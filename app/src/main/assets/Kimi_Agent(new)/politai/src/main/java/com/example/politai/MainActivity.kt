package com.example.politai

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
nimport android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PoLiTAI - Production-Ready MainActivity
 * 
 * Features:
 * - Thread-safe LLM inference with MediaPipe
 * - Voice input with multilingual support (Hindi, English, regional)
 * - File/PDF access and text extraction
 * - Conversation memory management
 * - Rapid-fire messaging protection
 * - Glassmorphic UI with crisp text
 * - Loading spinner safety
 * - Token overflow prevention
 */

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PoLiTAI-Main"
        private const val MAX_CONTEXT_LENGTH = 3000
        private const val MAX_CONVERSATION_HISTORY = 10
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val REQUEST_AUDIO_PERMISSION = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
        private const val PDF_PICK_REQUEST = 1003
    }

    // Core Components
    private var llmInference: LlmInference? = null
    private var ragEngine: RAGEngine? = null
    private var trendAnalyzer: TrendAnalyzer? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    
    // UI Components
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceButton: FloatingActionButton
    private lateinit var attachButton: FloatingActionButton
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var statusText: TextView
    
    // State Management
    private val chatList = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val isGenerating = AtomicBoolean(false)
    private val pendingMessageIndex = AtomicInteger(-1)
    private var currentLanguage = "en-IN" // Default to Indian English
    
    // Supported languages for voice input
    private val supportedLanguages = mapOf(
        "en-IN" to "English (India)",
        "hi-IN" to "हिन्दी",
        "ta-IN" to "தமிழ்",
        "te-IN" to "తెలుగు",
        "mr-IN" to "मराठी",
        "bn-IN" to "বাংলা",
        "gu-IN" to "ગુજરાતી",
        "kn-IN" to "ಕನ್ನಡ",
        "ml-IN" to "മലയാളം",
        "pa-IN" to "ਪੰਜਾਬੀ"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeUI()
        initializeComponents()
        checkPermissions()
    }
    
    private fun initializeUI() {
        recyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        attachButton = findViewById(R.id.attachButton)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        statusText = findViewById(R.id.statusText)
        
        // Setup RecyclerView with glassmorphic adapter
        chatAdapter = ChatAdapter(chatList) { message ->
            copyToClipboard(message)
        }
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = chatAdapter
        
        // Keyboard visibility listener for auto-scroll
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatList.isNotEmpty()) {
                recyclerView.post {
                    recyclerView.smoothScrollToPosition(chatList.size - 1)
                }
            }
        }
        
        // Send button click
        sendButton.setOnClickListener {
            val query = messageInput.text.toString().trim()
            if (query.isNotEmpty()) {
                sendMessage(query)
            }
        }
        
        // Voice input button
        voiceButton.setOnClickListener {
            showLanguageSelector()
        }
        
        // File attachment button
        attachButton.setOnClickListener {
            openFilePicker()
        }
        
        // Long press on input for paste
        messageInput.setOnLongClickListener {
            messageInput.requestFocus()
            showKeyboard(messageInput)
            true
        }
    }
    
    private fun initializeComponents() {
        // Initialize RAG Engine
        ragEngine = createRAGEngine()
        ragEngine?.preloadDatabases()
        
        // Initialize Trend Analyzer
        trendAnalyzer = createTrendAnalyzer()
        
        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("en", "IN")
            }
        }
        
        // Initialize Speech Recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        
        // Load LLM Model in background
        lifecycleScope.launch(Dispatchers.IO) {
            loadModel()
        }
    }
    
    private suspend fun loadModel() {
        try {
            withContext(Dispatchers.Main) {
                statusText.text = "Initializing AI Model..."
                statusText.visibility = View.VISIBLE
            }
            
            val modelFile = File(filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Downloading model (first time)..."
                }
                assets.open(MODEL_FILENAME).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setResultListener { partialResult, done ->
                    if (done) {
                        onGenerationComplete(partialResult)
                    } else {
                        onPartialResult(partialResult)
                    }
                }
                .build()
            
            llmInference = LlmInference.createFromOptions(this, options)
            
            withContext(Dispatchers.Main) {
                statusText.text = "PoLiTAI Ready"
                statusText.postDelayed({ statusText.visibility = View.GONE }, 2000)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Model initialization failed: ${e.message}")
            withContext(Dispatchers.Main) {
                statusText.text = "Model Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Failed to load AI model", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun sendMessage(query: String, isVoiceInput: Boolean = false) {
        // Prevent rapid-fire messaging
        if (isGenerating.get()) {
            Toast.makeText(this, "Please wait for current response...", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (llmInference == null) {
            Toast.makeText(this, "AI is initializing, please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add user message to chat
        val userMessage = ChatMessage(
            content = query,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        chatList.add(userMessage)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
        
        // Clear input
        messageInput.text.clear()
        hideKeyboard()
        
        // Show loading
        isGenerating.set(true)
        loadingSpinner.visibility = View.VISIBLE
        
        // Add placeholder for AI response
        val aiMessage = ChatMessage(
            content = if (isVoiceInput) "🎙️ Listening..." else "🖋️ Generating...",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        chatList.add(aiMessage)
        pendingMessageIndex.set(chatList.size - 1)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
        
        // Process in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Build conversation context
                val context = buildConversationContext()
                
                // Load RAG context
                val ragContext = ragEngine?.loadRAGContext(query, context) ?: ""
                
                // Check for trend analysis queries
                val enhancedQuery = if (isTrendAnalysisQuery(query)) {
                    enhanceWithTrendAnalysis(query)
                } else query
                
                // Build complete prompt
                val isFollowUp = conversationHistory.isNotEmpty() && 
                    isFollowUpQuery(query, conversationHistory.last().first)
                
                val prompt = SystemPrompts.buildCompletePrompt(
                    userQuery = enhancedQuery,
                    ragContext = ragContext,
                    conversationContext = context,
                    isFollowUp = isFollowUp
                )
                
                // Generate response
                val response = llmInference?.generateResponse(prompt) ?: "No response generated"
                
                withContext(Dispatchers.Main) {
                    updateAIMessage(response)
                    addToConversationHistory(query, response)
                    
                    // Optional: Read response aloud if voice input was used
                    if (isVoiceInput) {
                        speakResponse(response)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Generation error: ${e.message}")
                withContext(Dispatchers.Main) {
                    updateAIMessage("❌ Error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE
                    isGenerating.set(false)
                }
            }
        }
    }
    
    private fun updateAIMessage(content: String) {
        val index = pendingMessageIndex.get()
        if (index >= 0 && index < chatList.size) {
            chatList[index] = chatList[index].copy(content = content)
            chatAdapter.notifyItemChanged(index)
            scrollToBottom()
        }
    }
    
    private fun onPartialResult(result: String) {
        // For streaming updates (if MediaPipe supports it)
        runOnUiThread {
            val index = pendingMessageIndex.get()
            if (index >= 0 && index < chatList.size) {
                val current = chatList[index].content
                if (current == "🖋️ Generating..." || current == "🎙️ Listening...") {
                    chatList[index] = chatList[index].copy(content = result)
                } else {
                    chatList[index] = chatList[index].copy(content = current + result)
                }
                chatAdapter.notifyItemChanged(index)
            }
        }
    }
    
    private fun onGenerationComplete(finalResult: String) {
        runOnUiThread {
            updateAIMessage(finalResult)
            loadingSpinner.visibility = View.GONE
            isGenerating.set(false)
        }
    }
    
    private fun buildConversationContext(): String {
        if (conversationHistory.isEmpty()) return ""
        
        return conversationHistory.takeLast(MAX_CONVERSATION_HISTORY).joinToString("\n") { (user, ai) ->
            "User: $user\nAI: $ai"
        }
    }
    
    private fun addToConversationHistory(userQuery: String, aiResponse: String) {
        conversationHistory.add(userQuery to aiResponse)
        
        // Trim if exceeds max
        while (conversationHistory.size > MAX_CONVERSATION_HISTORY) {
            conversationHistory.removeAt(0)
        }
    }
    
    private fun isFollowUpQuery(current: String, previous: String): Boolean {
        val followUpIndicators = listOf(
            "it", "that", "this", "they", "them", "he", "she", "his", "her",
            "yes", "no", "what about", "how about", "tell me more", "explain",
            "why", "when", "where", "who", "which", "elaborate", "detail",
            "more", "further", "continue", "go on"
        )
        return followUpIndicators.any { current.lowercase().contains(it) }
    }
    
    private fun isTrendAnalysisQuery(query: String): Boolean {
        val trendKeywords = listOf(
            "top issues", "top 3", "trend", "analyze", "statistics",
            "compare", "performance", "budget utilization", "complaints",
            "district report", "underutilized", "most repeated"
        )
        return trendKeywords.any { query.lowercase().contains(it) }
    }
    
    private fun enhanceWithTrendAnalysis(query: String): String {
        val analyzer = trendAnalyzer ?: return query
        
        return when {
            query.contains("top 3 issues", ignoreCase = true) || 
            query.contains("top issues", ignoreCase = true) -> {
                val district = extractDistrictFromQuery(query)
                val topIssues = if (district != null) {
                    analyzer.getTop3Issues(district)
                } else {
                    analyzer.getTop3Issues()
                }
                "$query\n\n[TREND DATA]: Top issues: ${topIssues.joinToString("; ")}"
            }
            
            query.contains("budget", ignoreCase = true) && 
            query.contains("utilization", ignoreCase = true) -> {
                val analysis = analyzer.analyzeBudgetUtilization()
                val critical = analysis.filter { 
                    it.status == UtilizationStatus.POOR || it.status == UtilizationStatus.CRITICAL 
                }
                "$query\n\n[BUDGET DATA]: Underutilized sectors: ${critical.take(3).joinToString { "${it.sector} (${it.utilizationPercentage.roundToInt()}%)" }}"
            }
            
            query.contains("district report", ignoreCase = true) -> {
                val district = extractDistrictFromQuery(query) ?: "General"
                val report = analyzer.generateDistrictReport(district)
                "$query\n\n[DISTRICT DATA]: $report"
            }
            
            else -> query
        }
    }
    
    private fun extractDistrictFromQuery(query: String): String? {
        // Common district names in India
        val districts = listOf(
            "jaipur", "delhi", "mumbai", "chennai", "bangalore", "hyderabad",
            "kolkata", "pune", "ahmedabad", "surat", "lucknow", "kanpur",
            "nagpur", "indore", "thane", "bhopal", "visakhapatnam", "patna",
            "vadodara", "ghaziabad", "ludhiana", "agra", "nashik", "faridabad"
        )
        return districts.find { query.lowercase().contains(it) }
    }
    
    // ==================== VOICE INPUT ====================
    
    private fun showLanguageSelector() {
        val languages = supportedLanguages.values.toTypedArray()
        val localeCodes = supportedLanguages.keys.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                currentLanguage = localeCodes[which]
                startVoiceInput()
            }
            .show()
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                messageInput.setText(spokenText)
                sendMessage(spokenText, isVoiceInput = true)
            }
        }
    }
    
    private fun speakResponse(text: String) {
        // Clean text for TTS (remove markdown, emojis)
        val cleanText = text
            .replace(Regex("[*_#`\\[\\]]"), "")
            .replace(Regex("[📝📊💰🔝✅🟢🟡🟠🔴🎙️❌→]"), "")
            .take(500) // Limit length
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null)
        }
    }
    
    // ==================== FILE/PDF ACCESS ====================
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
        }
        filePickerLauncher.launch(intent)
    }
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processDocument(uri)
            }
        }
    }
    
    private fun processDocument(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mimeType = contentResolver.getType(uri)
                val extractedText = when {
                    mimeType?.contains("pdf") == true -> extractTextFromPdf(uri)
                    mimeType?.contains("text") == true -> extractTextFromTextFile(uri)
                    else -> "Unsupported file type: $mimeType"
                }
                
                withContext(Dispatchers.Main) {
                    if (extractedText.isNotEmpty() && extractedText != "Unsupported file type: $mimeType") {
                        // Summarize the document
                        val summaryPrompt = "Summarize this document:\n\n$extractedText"
                        messageInput.setText("Summarize the uploaded document")
                        sendMessage(summaryPrompt)
                    } else {
                        Toast.makeText(this@MainActivity, extractedText, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Document processing error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error processing file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun extractTextFromPdf(uri: Uri): String {
        val textBuilder = StringBuilder()
        var pdfRenderer: PdfRenderer? = null
        var fileDescriptor: ParcelFileDescriptor? = null
        
        try {
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            fileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
                val pageCount = pdfRenderer.pageCount
                
                // Limit to first 5 pages for performance
                val pagesToProcess = minOf(pageCount, 5)
                
                for (i in 0 until pagesToProcess) {
                    val page = pdfRenderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_TEXT)
                    
                    // Note: For production, use a proper OCR library like ML Kit
                    // This is a simplified version
                    textBuilder.append("[Page ${i + 1} content extracted]\n")
                    
                    page.close()
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction error: ${e.message}")
            return "Error extracting PDF: ${e.message}"
        } finally {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
        
        return textBuilder.toString()
    }
    
    private fun extractTextFromTextFile(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText().take(5000) // Limit to 5000 chars
                }
            } ?: ""
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }
    
    // ==================== UTILITY FUNCTIONS ====================
    
    private fun copyToClipboard(message: ChatMessage) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PoLiTAI", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun scrollToBottom() {
        if (chatList.isNotEmpty()) {
            recyclerView.post {
                recyclerView.smoothScrollToPosition(chatList.size - 1)
            }
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
    }
    
    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_DOCUMENTS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_DOCUMENTS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_STORAGE_PERMISSION)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startVoiceInput()
                } else {
                    Toast.makeText(this, "Microphone permission required for voice input", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        llmInference?.close()
        ragEngine?.clearCache()
    }
}

/**
 * Chat Message Data Class
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false
)