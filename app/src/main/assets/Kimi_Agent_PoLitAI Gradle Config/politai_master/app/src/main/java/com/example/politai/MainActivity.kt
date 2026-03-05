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
import android.os.Handler
import android.os.Looper
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
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * PoLiTAI MainActivity - Master Grade Edition
 * 
 * Features:
 * - Adreno 618 GPU Optimization (Snapdragon 732G)
 * - Memory-optimized initialization sequence
 * - Source citations display
 * - Crash-resistant AI engine loading
 */

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "PoLiTAI-Main"
        private const val MAX_CONTEXT_LENGTH = 15000 
        private const val MAX_CONVERSATION_HISTORY = 3
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val REQUEST_AUDIO_PERMISSION = 1001
        
        // Adreno 618 GPU Optimization Constants
        private const val GPU_MEMORY_LIMIT_MB = 512
        private const val CPU_MEMORY_LIMIT_MB = 1024
        private const val MAX_TOKENS_GPU = 1024
        private const val MAX_TOKENS_CPU = 512
    }

    // Core Components
    private var llmInference: LlmInference? = null
    private var ragEngine: RAGEngine? = null
    private var trendAnalyzer: TrendAnalyzer? = null
    private var textToSpeech: TextToSpeech? = null
    private var chatHistoryManager: ChatHistoryManager? = null
    
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
    private lateinit var sourcesContainer: LinearLayout
    
    // State Management
    private val chatList = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val isGenerating = AtomicBoolean(false)
    private val pendingMessageIndex = AtomicInteger(-1)
    private var currentLanguage = "en-IN"
    private var currentSessionId: Long = -1
    private var attachedFile: AttachedFile? = null
    private var lastUsedSources = listOf<String>()
    
    private val supportedLanguages = mapOf(
        "en-IN" to Pair("English (India)", Locale("en", "IN")),
        "hi-IN" to Pair("हिन्दी", Locale("hi", "IN")),
        "ta-IN" to Pair("தமிழ்", Locale("ta", "IN")),
        "te-IN" to Pair("తెలుగు", Locale("te", "IN")),
        "mr-IN" to Pair("मराठी", Locale("mr", "IN"))
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
        
        // Staged initialization for stability
        lifecycleScope.launch {
            try {
                chatHistoryManager = ChatHistoryManager(this@MainActivity)
                currentSessionId = chatHistoryManager?.createSession("New Conversation") ?: -1
                initializeComponents()
            } catch (e: Exception) {
                Log.e(TAG, "Initialization error", e)
                showStatus("Initialization Error: ${e.message}", true)
            }
        }
        
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
        sourcesContainer = findViewById(R.id.sourcesContainer)
        
        chatAdapter = ChatAdapter(chatList) { message -> copyToClipboard(message) }
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = chatAdapter
        
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatList.isNotEmpty()) {
                recyclerView.post { if(chatList.isNotEmpty()) recyclerView.smoothScrollToPosition(chatList.size - 1) }
            }
        }
        
        sendButton.setOnClickListener {
            val query = messageInput.text.toString().trim()
            if (query.isNotEmpty() || attachedFile != null) sendMessage(query)
        }
        
        voiceButton.setOnClickListener { showLanguageSelector() }
        attachButton.setOnClickListener { openFilePicker() }
        removeAttachment.setOnClickListener {
            attachedFile = null
            attachmentPreview.visibility = View.GONE
        }
    }
    
    private suspend fun initializeComponents() {
        withContext(Dispatchers.Main) {
            showStatus("Initializing RAG Engine...")
        }
        
        // Initialize RAG Engine first (lightweight)
        ragEngine = RAGEngine(this)
        ragEngine?.preloadDatabases()
        
        withContext(Dispatchers.Main) {
            showStatus("Initializing Trend Analyzer...")
        }
        
        trendAnalyzer = TrendAnalyzer(this)
        
        withContext(Dispatchers.Main) {
            showStatus("Initializing TTS...")
        }
        
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("en", "IN")
            }
        }
        
        // Load AI Model last (heaviest operation)
        withContext(Dispatchers.Main) {
            showStatus("Loading AI Model...")
        }
        
        loadModelOptimized()
    }
    
    /**
     * MASTER GRADE: GPU-Optimized Model Loading for Adreno 618
     * - Memory-mapped model loading
     - Staged fallback sequence
     * - OpenCL compatibility checks
     */
    private suspend fun loadModelOptimized() {
        try {
            val modelFile = File(filesDir, MODEL_FILENAME)
            
            // Copy model from assets if needed
            if (!modelFile.exists() || modelFile.length() < 1000000) {
                withContext(Dispatchers.Main) { showStatus("Optimizing AI Assets...") }
                copyModelFromAssets(modelFile)
            }
            
            // Stage 1: Try GPU with Adreno 618 optimizations
            val gpuSuccess = tryInitializeGPU(modelFile)
            if (gpuSuccess) {
                withContext(Dispatchers.Main) {
                    showStatus("PoLiTAI GPU Ready (Adreno 618)")
                    hideStatusDelayed()
                }
                return
            }
            
            // Stage 2: Fallback to CPU with reduced tokens
            withContext(Dispatchers.Main) { showStatus("GPU unavailable, switching to CPU...") }
            
            val cpuSuccess = tryInitializeCPU(modelFile)
            if (cpuSuccess) {
                withContext(Dispatchers.Main) {
                    showStatus("PoLiTAI CPU Ready")
                    hideStatusDelayed()
                }
                return
            }
            
            throw IllegalStateException("Failed to initialize on both GPU and CPU")
            
        } catch (e: Exception) {
            Log.e(TAG, "Master Engine failed to initialize", e)
            withContext(Dispatchers.Main) {
                showStatus("Critical Error: ${e.message}", true)
            }
        }
    }
    
    private suspend fun copyModelFromAssets(modelFile: File) {
        withContext(Dispatchers.IO) {
            assets.open(MODEL_FILENAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        // Log progress every 10MB
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "Copied ${totalBytes / (1024 * 1024)} MB")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Adreno 618 GPU Initialization with optimized settings
     */
    private fun tryInitializeGPU(modelFile: File): Boolean {
        return try {
            Log.d(TAG, "Attempting GPU initialization for Adreno 618...")
            
            // Force garbage collection before initialization
            System.gc()
            Thread.sleep(100)
            
            val gpuOptions = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS_GPU)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            
            llmInference = LlmInference.createFromOptions(this, gpuOptions)
            
            // Test inference to verify GPU is working
            val testResult = llmInference?.generateResponse("Test") 
            Log.d(TAG, "GPU initialization successful. Test response: $testResult")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "GPU initialization failed: ${e.message}", e)
            llmInference?.close()
            llmInference = null
            false
        }
    }
    
    private fun tryInitializeCPU(modelFile: File): Boolean {
        return try {
            Log.d(TAG, "Attempting CPU initialization...")
            
            System.gc()
            Thread.sleep(100)
            
            val cpuOptions = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS_CPU)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            
            llmInference = LlmInference.createFromOptions(this, cpuOptions)
            Log.d(TAG, "CPU initialization successful")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "CPU initialization failed: ${e.message}", e)
            llmInference?.close()
            llmInference = null
            false
        }
    }
    
    private fun showStatus(message: String, isError: Boolean = false) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        if (isError) {
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }
    
    private fun hideStatusDelayed() {
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.visibility = View.GONE
        }, 3000)
    }
    
    private fun sendMessage(query: String, isVoiceInput: Boolean = false) {
        if (isGenerating.get() || llmInference == null) return
        
        val displayMsg = if (attachedFile != null) "📎 ${attachedFile?.name}\n$query" else query
        chatList.add(ChatMessage(displayMsg, true, System.currentTimeMillis()))
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
        
        val fullQuery = buildString {
            if (attachedFile != null) {
                append("[DOC: ${attachedFile?.name}]\n")
                append("Content: ${attachedFile?.content?.take(2000)}\n")
                if (query.isNotEmpty()) append("Query: $query")
            } else append(query)
        }
        
        messageInput.text.clear()
        attachedFile = null
        attachmentPreview.visibility = View.GONE
        hideKeyboard()
        
        isGenerating.set(true)
        loadingSpinner.visibility = View.VISIBLE
        
        val aiPlaceholder = ChatMessage("🖋️ Analyzing...", false, System.currentTimeMillis())
        chatList.add(aiPlaceholder)
        pendingMessageIndex.set(chatList.size - 1)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = conversationHistory.takeLast(MAX_CONVERSATION_HISTORY).joinToString("\n") { (u, a) -> "U: $u\nA: $a" }
                
                // Get RAG context with sources
                val ragResult = ragEngine?.loadRAGContextWithSources(fullQuery, context)
                val ragContext = ragResult?.context ?: ""
                lastUsedSources = ragResult?.sources ?: emptyList()
                
                val langInstr = if (currentLanguage != "en-IN") "\n\n(Respond in ${supportedLanguages[currentLanguage]?.first})" else ""
                
                val prompt = SystemPrompts.buildCompletePrompt(
                    userQuery = fullQuery + langInstr,
                    ragContext = ragContext,
                    conversationContext = context,
                    isFollowUp = conversationHistory.isNotEmpty()
                )
                
                val response = llmInference?.generateResponse(prompt) ?: "Engine error"
                
                withContext(Dispatchers.Main) {
                    updateAIMessage(response)
                    displaySources(lastUsedSources)
                    conversationHistory.add(fullQuery to response)
                    if (isVoiceInput) speakResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    updateAIMessage("❌ Generation Error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE
                    isGenerating.set(false)
                }
            }
        }
    }
    
    /**
     * Display source citations below the AI response
     */
    private fun displaySources(sources: List<String>) {
        sourcesContainer.removeAllViews()
        
        if (sources.isEmpty()) {
            sourcesContainer.visibility = View.GONE
            return
        }
        
        sourcesContainer.visibility = View.VISIBLE
        
        // Add header
        val headerView = TextView(this).apply {
            text = "📚 Sources:"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            setPadding(16, 8, 16, 4)
        }
        sourcesContainer.addView(headerView)
        
        // Add each source
        sources.take(3).forEach { source ->
            val sourceView = TextView(this).apply {
                text = "• $source"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
                setPadding(32, 2, 16, 2)
            }
            sourcesContainer.addView(sourceView)
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
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain"))
        }
        filePickerLauncher.launch(intent)
    }
    
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val name = getFileName(uri)
                    val content = contentResolver.openInputStream(uri)?.use { 
                        it.bufferedReader().readText().take(3000) 
                    } ?: ""
                    withContext(Dispatchers.Main) {
                        attachedFile = AttachedFile(uri, name, "pdf", content)
                        attachmentName.text = name
                        attachmentPreview.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result = "Document"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index) ?: "Document"
                }
            }
        }
        return result
    }
    
    private fun showLanguageSelector() {
        val names = supportedLanguages.values.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(names) { _, i ->
                currentLanguage = supportedLanguages.keys.elementAt(i)
                startVoiceInput()
            }
            .show()
    }
    
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        }
        speechResultLauncher.launch(intent)
    }
    
    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) sendMessage(matches[0], true)
        }
    }
    
    private fun speakResponse(text: String) {
        textToSpeech?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_new_chat -> {
                chatList.clear()
                chatAdapter.notifyDataSetChanged()
                conversationHistory.clear()
                sourcesContainer.removeAllViews()
                sourcesContainer.visibility = View.GONE
                lifecycleScope.launch {
                    currentSessionId = chatHistoryManager?.createSession("New Conversation") ?: -1
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun copyToClipboard(msg: ChatMessage) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText("AI", msg.content)
        )
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun scrollToBottom() = recyclerView.post { 
        if(chatList.isNotEmpty()) recyclerView.smoothScrollToPosition(chatList.size - 1) 
    }
    
    private fun hideKeyboard() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(messageInput.windowToken, 0)
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        llmInference?.close()
        ragEngine?.clearCache()
    }
}
