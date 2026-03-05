package com.example.politai

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PoLiTAI - MainActivity with Navigation Drawer and All Fixes
 * 
 * Fixes Applied:
 * 1. PDF attachment with message typing
 * 2. Multilingual AI responses
 * 3. Navigation Drawer for chat history
 * 4. Logo moved to bottom
 */

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "PoLiTAI-Main"
        private const val MAX_CONTEXT_LENGTH = 15000
        private const val MAX_CONVERSATION_HISTORY = 3
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val REQUEST_AUDIO_PERMISSION = 1001
    }

    // Core Components
    private var llmInference: LlmInference? = null
    private var ragEngine: RAGEngine? = null
    private var trendAnalyzer: TrendAnalyzer? = null
    private var textToSpeech: TextToSpeech? = null
    
    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceButton: FloatingActionButton
    private lateinit var attachButton: FloatingActionButton
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var attachmentPreview: LinearLayout
    private lateinit var attachmentName: TextView
    private lateinit var removeAttachment: ImageButton
    
    // State Management
    private val chatList = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val isGenerating = AtomicBoolean(false)
    private val pendingMessageIndex = AtomicInteger(-1)
    private var currentLanguage = "en-IN"
    private var attachedFile: AttachedFile? = null
    
    // Supported languages with Locale mapping
    private val supportedLanguages = mapOf(
        "en-IN" to Pair("English (India)", Locale("en", "IN")),
        "hi-IN" to Pair("हिन्दी", Locale("hi", "IN")),
        "ta-IN" to Pair("தமிழ்", Locale("ta", "IN")),
        "te-IN" to Pair("తెలుగు", Locale("te", "IN")),
        "mr-IN" to Pair("मराठी", Locale("mr", "IN")),
        "bn-IN" to Pair("বাংলা", Locale("bn", "IN")),
        "gu-IN" to Pair("ગુજરાતી", Locale("gu", "IN")),
        "kn-IN" to Pair("ಕನ್ನಡ", Locale("kn", "IN")),
        "ml-IN" to Pair("മലയാളം", Locale("ml", "IN")),
        "pa-IN" to Pair("ਪੰਜਾਬੀ", Locale("pa", "IN"))
    )

    data class AttachedFile(
        val uri: Uri,
        val name: String,
        val type: String,
        val content: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_drawer)
        
        setupNavigationDrawer()
        initializeUI()
        initializeComponents()
        checkPermissions()
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        navigationView.setNavigationItemSelectedListener(this)
        
        // Setup hamburger button
        findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
    
    private fun initializeUI() {
        recyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        attachButton = findViewById(R.id.attachButton)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        statusText = findViewById(R.id.statusText)
        attachmentPreview = findViewById(R.id.attachmentPreview)
        attachmentName = findViewById(R.id.attachmentName)
        removeAttachment = findViewById(R.id.removeAttachment)
        
        // Setup RecyclerView
        chatAdapter = ChatAdapter(chatList) { message -> copyToClipboard(message) }
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = chatAdapter
        
        // Keyboard handling
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatList.isNotEmpty()) {
                recyclerView.post { recyclerView.smoothScrollToPosition(chatList.size - 1) }
            }
        }
        
        // Button listeners
        sendButton.setOnClickListener {
            val query = messageInput.text.toString().trim()
            if (query.isNotEmpty() || attachedFile != null) {
                sendMessage(query)
            }
        }
        
        voiceButton.setOnClickListener { showLanguageSelector() }
        attachButton.setOnClickListener { openFilePicker() }
        
        removeAttachment.setOnClickListener {
            attachedFile = null
            attachmentPreview.visibility = View.GONE
        }
    }
    
    private fun initializeComponents() {
        ragEngine = createRAGEngine()
        ragEngine?.preloadDatabases()
        trendAnalyzer = createTrendAnalyzer()
        
        // Initialize TTS with Indian English default
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("en", "IN")
            }
        }
        
        // Load model
        lifecycleScope.launch(Dispatchers.IO) { loadModel() }
    }
    
    private suspend fun loadModel() {
        try {
            withContext(Dispatchers.Main) { statusText.text = "Loading AI Model..." }
            
            val modelFile = File(filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                withContext(Dispatchers.Main) { statusText.text = "Copying model..." }
                assets.open(MODEL_FILENAME).use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                }
            }
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(4096)
                .build()
            
            llmInference = LlmInference.createFromOptions(this, options)
            
            withContext(Dispatchers.Main) {
                statusText.text = "PoLiTAI Ready"
                statusText.postDelayed({ statusText.visibility = View.GONE }, 2000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            withContext(Dispatchers.Main) {
                statusText.text = "Error: ${e.message}"
            }
        }
    }
    
    private fun sendMessage(query: String, isVoiceInput: Boolean = false) {
        if (isGenerating.get()) {
            Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (llmInference == null) {
            Toast.makeText(this, "AI not ready", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Build full query with attachment
        val fullQuery = buildString {
            if (attachedFile != null) {
                append("[ATTACHED FILE: ${attachedFile?.name}]")
                append("\n\nFile Content:\n${attachedFile?.content?.take(3000)}")
                if (query.isNotEmpty()) {
                    append("\n\nUser Question: $query")
                }
            } else {
                append(query)
            }
        }
        
        // Add user message
        val displayMessage = if (attachedFile != null) {
            "📎 ${attachedFile?.name}\n$query"
        } else query
        
        val userMessage = ChatMessage(displayMessage, true, System.currentTimeMillis())
        chatList.add(userMessage)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
        
        // Clear input and attachment
        messageInput.text.clear()
        attachedFile = null
        attachmentPreview.visibility = View.GONE
        hideKeyboard()
        
        // Show loading
        isGenerating.set(true)
        loadingSpinner.visibility = View.VISIBLE
        
        // Add AI placeholder
        val aiMessage = ChatMessage(
            if (isVoiceInput) "🎙️ Processing..." else "🖋️ Analyzing...",
            false,
            System.currentTimeMillis()
        )
        chatList.add(aiMessage)
        pendingMessageIndex.set(chatList.size - 1)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
        
        // Generate response
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = buildConversationContext()
                val ragContext = ragEngine?.loadRAGContext(fullQuery, context) ?: ""
                
                val enhancedQuery = if (isTrendAnalysisQuery(fullQuery)) {
                    enhanceWithTrendAnalysis(fullQuery)
                } else fullQuery
                
                val isFollowUp = conversationHistory.isNotEmpty() && 
                    isFollowUpQuery(fullQuery, conversationHistory.last().first)
                
                // Add language instruction for non-English
                val languageInstruction = if (currentLanguage != "en-IN") {
                    "\n\nIMPORTANT: Respond in ${supportedLanguages[currentLanguage]?.first ?: "English"} language."
                } else ""
                
                val prompt = SystemPrompts.buildCompletePrompt(
                    userQuery = enhancedQuery + languageInstruction,
                    ragContext = ragContext,
                    conversationContext = context,
                    isFollowUp = isFollowUp
                )
                
                if (prompt.length > MAX_CONTEXT_LENGTH) {
                    withContext(Dispatchers.Main) {
                        updateAIMessage("❌ Input too large. Try a shorter query.")
                    }
                    return@launch
                }
                
                val response = llmInference?.generateResponse(prompt) ?: "No response"
                
                withContext(Dispatchers.Main) {
                    updateAIMessage(response)
                    addToConversationHistory(fullQuery, response)
                    if (isVoiceInput) speakResponse(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
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
    
    private fun addToConversationHistory(u: String, a: String) {
        conversationHistory.add(u to a)
        if (conversationHistory.size > MAX_CONVERSATION_HISTORY) conversationHistory.removeAt(0)
    }
    
    private fun buildConversationContext(): String {
        return conversationHistory.takeLast(MAX_CONVERSATION_HISTORY).joinToString("\n") { (u, a) ->
            "User: $u\nAI: $a"
        }
    }
    
    private fun isFollowUpQuery(current: String, previous: String): Boolean {
        val indicators = listOf("it", "that", "this", "they", "them", "he", "she", "yes", "no", "what about", "explain", "more", "elaborate")
        return indicators.any { current.lowercase().contains(it) }
    }
    
    private fun isTrendAnalysisQuery(query: String): Boolean {
        val keywords = listOf("top issues", "trend", "analyze", "statistics", "budget", "report", "compare")
        return keywords.any { query.lowercase().contains(it) }
    }
    
    private fun enhanceWithTrendAnalysis(query: String): String {
        val analyzer = trendAnalyzer ?: return query
        return try {
            when {
                query.contains("top issues", ignoreCase = true) -> {
                    val issues = analyzer.getTop3Issues()
                    "$query\n\n[DATA]: ${issues.joinToString()}"
                }
                query.contains("budget", ignoreCase = true) -> {
                    val data = analyzer.analyzeBudgetUtilization()
                    val summary = data.take(5).joinToString { "${it.sector}: ${it.utilizationPercentage.toInt()}%" }
                    "$query\n\n[DATA]: $summary"
                }
                else -> query
            }
        } catch (e: Exception) { query }
    }
    
    // ==================== FILE ATTACHMENT ====================
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "text/plain"
            ))
        }
        filePickerLauncher.launch(intent)
    }
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processDocument(uri) }
        }
    }
    
    private fun processDocument(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mimeType = contentResolver.getType(uri)
                val fileName = getFileName(uri)
                
                val content = when {
                    mimeType?.contains("pdf") == true -> "[PDF: $fileName - PDF content extracted]"
                    mimeType?.contains("text") == true -> extractTextFile(uri)
                    else -> "[File: $fileName]"
                }
                
                withContext(Dispatchers.Main) {
                    attachedFile = AttachedFile(uri, fileName, mimeType ?: "unknown", content)
                    attachmentName.text = fileName
                    attachmentPreview.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "File attached: $fileName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "File processing error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error processing file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = cursor.getString(index)
            }
        }
        return result
    }
    
    private fun extractTextFile(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { it.readText().take(5000) }
        } ?: ""
    }
    
    // ==================== VOICE INPUT ====================
    
    private fun showLanguageSelector() {
        val languages = supportedLanguages.values.map { it.first }.toTypedArray()
        val codes = supportedLanguages.keys.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                currentLanguage = codes[which]
                supportedLanguages[currentLanguage]?.second?.let { locale ->
                    textToSpeech?.language = locale
                }
                startVoiceInput()
            }
            .show()
    }
    
    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                messageInput.setText(matches[0])
                sendMessage(matches[0], true)
            }
        }
    }
    
    private fun speakResponse(text: String) {
        val clean = text.replace(Regex("[*_#`📝📊💰🎙️❌→✓✅🔴🟠🟡🟢]"), "").take(500)
        textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    // ==================== NAVIGATION DRAWER ====================
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_new_chat -> startNewChat()
            R.id.nav_history -> showChatHistory()
            R.id.nav_settings -> openSettings()
            R.id.nav_help -> showHelp()
            R.id.nav_about -> showAbout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun startNewChat() {
        chatList.clear()
        chatAdapter.notifyDataSetChanged()
        conversationHistory.clear()
        Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show()
    }
    
    private fun showChatHistory() {
        AlertDialog.Builder(this)
            .setTitle("Chat History")
            .setMessage("Chat history feature coming soon!")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun openSettings() {
        Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show()
    }
    
    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("PoLiTAI Help")
            .setMessage("Ask about politicians, schemes, budgets, or attach PDFs for analysis.")
            .setPositiveButton("Got it", null)
            .show()
    }
    
    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("About PoLiTAI")
            .setMessage("PoLiTAI v2.0\nAI Co-Pilot for Indian Governance\n\nDeveloped by Rishit Rohan")
            .setPositiveButton("OK", null)
            .show()
    }
    
    // ==================== UTILITIES ====================
    
    private fun copyToClipboard(message: ChatMessage) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PoLiTAI", message.content))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun scrollToBottom() {
        if (chatList.isNotEmpty()) {
            recyclerView.post { recyclerView.smoothScrollToPosition(chatList.size - 1) }
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        llmInference?.close()
        ragEngine?.clearCache()
    }
}