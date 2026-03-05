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

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "PoLiTAI-Main"
        private const val MAX_CONTEXT_LENGTH = 15000 
        private const val MAX_CONVERSATION_HISTORY = 3
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val REQUEST_AUDIO_PERMISSION = 1001
        private const val MAX_TOKENS = 4096 
    }

    private var llmInference: LlmInference? = null
    private var ragEngine: RAGEngine? = null
    private var trendAnalyzer: TrendAnalyzer? = null
    private var textToSpeech: TextToSpeech? = null
    private var chatHistoryManager: ChatHistoryManager? = null
    
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
    
    private val chatList = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val isGenerating = AtomicBoolean(false)
    private val pendingMessageIndex = AtomicInteger(-1)
    private var currentLanguage = "en-IN"
    private var currentSessionId: Long = -1
    private var attachedFile: AttachedFile? = null
    
    private val supportedLanguages = mapOf(
        "en-IN" to Pair("English (India)", Locale("en", "IN")),
        "hi-IN" to Pair("हिन्दी", Locale("hi", "IN")),
        "ta-IN" to Pair("தமிழ்", Locale("ta", "IN")),
        "te-IN" to Pair("తెలుగు", Locale("te", "IN")),
        "mr-IN" to Pair("मराठी", Locale("mr", "IN"))
    )

    data class AttachedFile(val uri: Uri, val name: String, val type: String, val content: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_drawer)
        setupNavigationDrawer()
        initializeUI()
        initializeComponents()
        checkPermissions()
        lifecycleScope.launch {
            chatHistoryManager = ChatHistoryManager(this@MainActivity)
            currentSessionId = chatHistoryManager?.createSession("New Conversation") ?: -1
        }
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
        findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
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
    
    private fun initializeComponents() {
        ragEngine = RAGEngine(this)
        ragEngine?.preloadDatabases()
        trendAnalyzer = TrendAnalyzer(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("en", "IN")
        }
        lifecycleScope.launch(Dispatchers.IO) { loadModel() }
    }
    
    private suspend fun loadModel() {
        try {
            withContext(Dispatchers.Main) { 
                statusText.text = "Initializing AI Brain..."
                statusText.visibility = View.VISIBLE
            }
            val modelFile = File(filesDir, MODEL_FILENAME)
            if (!modelFile.exists() || modelFile.length() < 1000000) {
                withContext(Dispatchers.Main) { statusText.text = "Optimizing AI Assets..." }
                assets.open(MODEL_FILENAME).use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                }
            }
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .build()
            llmInference = LlmInference.createFromOptions(this, options)
            withContext(Dispatchers.Main) {
                statusText.text = "PoLiTAI Ready"
                statusText.postDelayed({ statusText.visibility = View.GONE }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            withContext(Dispatchers.Main) { statusText.text = "AI Error: Model issue" }
        }
    }
    
    private fun sendMessage(query: String, isVoiceInput: Boolean = false) {
        if (isGenerating.get()) return

        // 1. QUICK GREETING INTERCEPT (Master Grade Speed)
        val lowerQuery = query.lowercase(Locale.ROOT).trim()
        if (lowerQuery == "hi" || lowerQuery == "hello") {
            addUserMessage(query)
            addAIPlaceholder()
            updateAIMessage("PoLiTAI system online. Awaiting governance query.")
            return
        }

        if (llmInference == null) {
            Toast.makeText(this, "AI is initializing...", Toast.LENGTH_SHORT).show()
            return
        }

        addUserMessage(query)
        
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
        
        addAIPlaceholder()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = conversationHistory.takeLast(MAX_CONVERSATION_HISTORY).joinToString("\n") { (u, a) -> "U: $u\nA: $a" }
                val ragContext = ragEngine?.loadRAGContext(fullQuery, context) ?: ""
                val langInstr = if (currentLanguage != "en-IN") "\n\n(Respond in ${supportedLanguages[currentLanguage]?.first})" else ""
                
                val prompt = SystemPrompts.buildCompletePrompt(
                    userQuery = fullQuery + langInstr,
                    ragContext = ragContext,
                    conversationContext = context,
                    isFollowUp = conversationHistory.isNotEmpty()
                )
                
                var response = llmInference?.generateResponse(prompt) ?: "No data found."
                
                // 2. CLEANUP: Remove any hallucinations of few-shot examples
                response = response.substringBefore("[USER QUERY]").substringBefore("User:").trim()

                withContext(Dispatchers.Main) {
                    updateAIMessage(response)
                    conversationHistory.add(fullQuery to response)
                    chatHistoryManager?.saveMessages(currentSessionId, chatList)
                    if (isVoiceInput) speakResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { updateAIMessage("❌ Generation Error: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) {
                    loadingSpinner.visibility = View.GONE
                    isGenerating.set(false)
                }
            }
        }
    }

    private fun addUserMessage(content: String) {
        val displayMsg = if (attachedFile != null) "📎 ${attachedFile?.name}\n$content" else content
        chatList.add(ChatMessage(displayMsg, true, System.currentTimeMillis()))
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
    }

    private fun addAIPlaceholder() {
        val aiPlaceholder = ChatMessage("🖋️ Analyzing...", false, System.currentTimeMillis())
        chatList.add(aiPlaceholder)
        pendingMessageIndex.set(chatList.size - 1)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
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
                    val content = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText().take(3000) } ?: ""
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
                if (index >= 0) result = cursor.getString(index) ?: "Document"
            }
        }
        return result
    }
    
    private fun showLanguageSelector() {
        val names = supportedLanguages.values.map { it.first }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select Language").setItems(names) { _, i ->
            currentLanguage = supportedLanguages.keys.elementAt(i)
            supportedLanguages[currentLanguage]?.second?.let { textToSpeech?.language = it }
            startVoiceInput()
        }.show()
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
        val clean = text.replace(Regex("[*_#`📝📊💰🎙️❌→✓✅🔴🟠🟡🟢]"), "").take(500)
        textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_new_chat -> {
                chatList.clear()
                chatAdapter.notifyDataSetChanged()
                conversationHistory.clear()
                lifecycleScope.launch {
                    currentSessionId = chatHistoryManager?.createSession("New Conversation") ?: -1
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun copyToClipboard(msg: ChatMessage) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI", msg.content))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
    
    private fun scrollToBottom() = recyclerView.post { if(chatList.isNotEmpty()) recyclerView.smoothScrollToPosition(chatList.size - 1) }
    private fun hideKeyboard() = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(messageInput.windowToken, 0)
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        llmInference?.close()
    }
}
