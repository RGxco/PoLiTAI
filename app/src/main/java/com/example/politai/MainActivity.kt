package com.example.politai

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import android.media.MediaPlayer
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "PoLiTAI-Main"
        private const val MAX_CONTEXT_LENGTH = 15000 
        private const val MAX_CONVERSATION_HISTORY = 3
        private const val MAX_HISTORY_CHARS = 600
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        private const val REQUEST_AUDIO_PERMISSION = 1001
        private const val MAX_TOKENS = 4096 
        private const val FALLBACK_RESPONSE = "Information not available in the local governance database."
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

    // Quality selector buttons
    private lateinit var btnQuick: TextView
    private lateinit var btnNormal: TextView
    private lateinit var btnDeep: TextView
    private lateinit var btnAuto: TextView
    
    private val chatList = mutableListOf<ChatMessage>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val isGenerating = AtomicBoolean(false)
    private val pendingMessageIndex = AtomicInteger(-1)
    private var currentLanguage = "en-IN"
    private var currentSessionId: Long = -1
    private var sessionReady = CompletableDeferred<Long>()  // FIX: guarantees session is ready
    private var attachedFile: AttachedFile? = null
    
    // Response quality: null = auto-detect
    private var userComplexityOverride: QueryComplexity? = null
    
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
        setupBackPressInterceptor()
        setupCrashReporter()
        sendPendingCrashReport()
        
        // FIX: Create session and signal when ready
        lifecycleScope.launch {
            chatHistoryManager = ChatHistoryManager(this@MainActivity)
            val id = chatHistoryManager?.createSession("New Conversation") ?: -1
            currentSessionId = id
            sessionReady.complete(id)
            Log.d(TAG, "Session created: $id")
        }
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
        findViewById<ImageButton>(R.id.menuButton)?.setOnClickListener { 
            loadHistoryIntoDrawer()
            drawerLayout.openDrawer(GravityCompat.START) 
        }
    }

    private fun loadHistoryIntoDrawer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val historyMap = chatHistoryManager?.getSessionsByDate() ?: return@launch
            withContext(Dispatchers.Main) {
                val menu = navigationView.menu
                
                // For simplicity: clear the whole menu and re-inflate, then append history
                menu.clear()
                menuInflater.inflate(R.menu.nav_drawer_menu, menu)
                
                // Now append history under a new group
                val recentGroup = menu.addSubMenu("Recent Chats")
                
                var count = 0
                for ((_, sessions) in historyMap) {
                    if (count >= 10) break // Show only top 10 recent
                    
                    // Add date header as an uncheckable item
                    // recentGroup.add(Menu.NONE, Menu.NONE, Menu.NONE, dateLabel).setEnabled(false)
                    
                    for (session in sessions.sortedByDescending { it.updatedAt }) {
                        if (count >= 10) break
                        
                        val menuItem = recentGroup.add(R.id.group_history, session.id.toInt(), count, session.title)
                        menuItem.setIcon(R.drawable.ic_topic)
                        
                        count++
                    }
                }
            }
        }
    }
    
    private fun setupBackPressInterceptor() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                // If chat is empty, just exit
                if (chatList.isEmpty()) {
                    finish()
                    return
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exit PoLiTAI")
                    .setMessage("Do you want to save this chat session?")
                    .setPositiveButton("Save & Exit") { _, _ ->
                        finish() // Session is already saved incrementally in DB
                    }
                    .setNegativeButton("Discard") { _, _ ->
                        // Delete the current session so it doesn't clutter history
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (currentSessionId != -1L) {
                                chatHistoryManager?.deleteSession(currentSessionId)
                            }
                            withContext(Dispatchers.Main) { finish() }
                        }
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
            }
        })
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

        // Quality selector buttons
        btnQuick = findViewById(R.id.btnQuick)
        btnNormal = findViewById(R.id.btnNormal)
        btnDeep = findViewById(R.id.btnDeep)
        btnAuto = findViewById(R.id.btnAuto)

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

        // Quality selector listeners
        setupQualitySelector()
    }

    /**
     * Setup the response quality chips.
     * Default: Auto (system auto-detects complexity per query)
     */
    private fun setupQualitySelector() {
        // Default state: Auto is selected
        selectQualityButton(null)

        btnQuick.setOnClickListener { selectQualityButton(QueryComplexity.SHORT) }
        btnNormal.setOnClickListener { selectQualityButton(QueryComplexity.MEDIUM) }
        btnDeep.setOnClickListener { selectQualityButton(QueryComplexity.LONG) }
        btnAuto.setOnClickListener { selectQualityButton(null) }
    }

    private fun selectQualityButton(complexity: QueryComplexity?) {
        userComplexityOverride = complexity

        // Reset all to inactive style (pill shape)
        val inactiveButtons = listOf(btnQuick, btnNormal, btnDeep, btnAuto)
        inactiveButtons.forEach { btn ->
            btn.setBackgroundResource(R.drawable.chip_inactive)
            btn.setTextColor(ContextCompat.getColor(this, R.color.white_alpha_70))
            btn.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        // Set active button
        val activeBtn = when (complexity) {
            QueryComplexity.SHORT -> btnQuick
            QueryComplexity.MEDIUM -> btnNormal
            QueryComplexity.LONG -> btnDeep
            null -> btnAuto
        }
        activeBtn.setBackgroundResource(R.drawable.chip_active)
        activeBtn.setTextColor(ContextCompat.getColor(this, R.color.white))
        activeBtn.setTypeface(null, android.graphics.Typeface.BOLD)
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

        // 1. QUICK GREETING INTERCEPT
        val lowerQuery = query.lowercase(Locale.ROOT).trim()
        if (lowerQuery == "hi" || lowerQuery == "hello" || lowerQuery == "namaste" || lowerQuery == "namaskar") {
            addUserMessage(query)
            addAIPlaceholder()
            val greeting = if (currentLanguage == "hi-IN") {
                "नमस्ते, मैं आपकी कैसे सहायता कर सकती हूँ?"
            } else {
                "PoLiTAI system online. Awaiting governance query."
            }
            updateAIMessage(greeting)
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
                // FIX: Wait for session to be ready before saving
                val safeSessionId = sessionReady.await()

                val convContext = conversationHistory.takeLast(MAX_CONVERSATION_HISTORY)
                    .joinToString("\n") { (u, a) -> "U: $u\nA: $a" }
                    .takeLast(MAX_HISTORY_CHARS)

                // RAG retrieval with complexity support
                val (ragContext, detectedComplexity) = ragEngine?.loadRAGContext(
                    fullQuery, convContext, userComplexityOverride
                ) ?: ("" to QueryComplexity.MEDIUM)

                Log.d(TAG, "RAG context length: ${ragContext.length}, complexity: ${detectedComplexity.label}")
                Log.d(TAG, "RAG context preview: ${ragContext.take(200)}")

                val langInstr = if (currentLanguage != "en-IN") "\n\n(Respond in ${supportedLanguages[currentLanguage]?.first})" else ""
                
                val prompt = SystemPrompts.buildCompletePrompt(
                    userQuery = fullQuery + langInstr,
                    ragContext = ragContext,
                    conversationContext = convContext,
                    isFollowUp = conversationHistory.isNotEmpty(),
                    complexity = detectedComplexity
                )
                
                var response = llmInference?.generateResponse(prompt) ?: FALLBACK_RESPONSE
                
                // CLEANUP: Remove any hallucinated prompt leakage
                response = response
                    .substringBefore("[USER QUERY]")
                    .substringBefore("User:")
                    .substringBefore("[GOVERNANCE RECORDS]")
                    .substringBefore("ANSWER:")
                    .trim()

                // POST-VALIDATION: ONLY trust the LLM if RAG actually returned data.
                // If RAG context is empty, the LLM has ZERO factual knowledge about this local offline question, 
                // so anything it said is a hallucination (even if it's well-formatted Hindi).
                if (ragContext.isBlank()) {
                    response = FALLBACK_RESPONSE
                }

                // Remove "no real-time access" hallucination if model still produces it
                if (response.contains("real-time", ignoreCase = true) ||
                    response.contains("do not have access", ignoreCase = true) ||
                    response.contains("don't have access", ignoreCase = true)) {
                    if (ragContext.isNotBlank()) {
                        // We DO have data — re-generate focusing on the data
                        // Just strip the refusal prefix and use whatever follows
                        val cleaned = response
                            .replace(Regex("(?i)I (do not|don't) have access to real-time information[.,]?\\s*"), "")
                            .replace(Regex("(?i)therefore[,]?\\s*"), "")
                            .replace(Regex("(?i)I cannot provide[^.]*\\.\\s*"), "")
                            .trim()
                        response = if (cleaned.length > 20) cleaned else FALLBACK_RESPONSE
                    } else {
                        response = FALLBACK_RESPONSE
                    }
                }

                // Remove conversational filler at the end of responses
                response = response
                    .replace(Regex("(?i)Please provide (additional|more) context.*"), "")
                    .replace(Regex("(?i)Let me know if you need .*"), "")
                    .replace(Regex("(?i)Is there anything else .*"), "")
                    .replace(Regex("(?i)(If|Please let me know if) you have any (further|more) questions.*"), "")
                    .trim()

                // Ensure response isn't empty
                if (response.isBlank()) response = FALLBACK_RESPONSE

                withContext(Dispatchers.Main) {
                    updateAIMessage(response)
                    conversationHistory.add(fullQuery to response)
                    
                    // Save messages with guaranteed valid session
                    try {
                        chatHistoryManager?.saveMessage(
                            safeSessionId,
                            ChatMessage(fullQuery, true, System.currentTimeMillis())
                        )
                        chatHistoryManager?.saveMessage(
                            safeSessionId,
                            ChatMessage(response, false, System.currentTimeMillis())
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save chat message", e)
                    }
                    
                    if (isVoiceInput) speakResponse(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
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
        val clean = text.replace(Regex("[*_#`📝📊💰🎙️❌→✓✅🔴🟠🟡🟢⚡🤖🖋️]"), "").take(500)
        textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_new_chat -> {
                chatList.clear()
                chatAdapter.notifyDataSetChanged()
                conversationHistory.clear()
                // FIX: Reset session deferred for new chat
                sessionReady = CompletableDeferred()
                lifecycleScope.launch {
                    val id = chatHistoryManager?.createSession("New Conversation") ?: -1
                    currentSessionId = id
                    sessionReady.complete(id)
                }
            }
            R.id.nav_help, R.id.nav_about -> {
                // Ignore for now
            }
            R.id.nav_send_feedback -> {
                sendChatFeedback()
            }
            else -> {
                // History item clicked
                if (item.groupId == R.id.group_history) {
                    val sessionIdToLoad = item.itemId.toLong()
                    val sessionTitle = item.title.toString()
                    
                    // Show a dialog to Open or Delete (since NavigationView lacks native long-press)
                    AlertDialog.Builder(this)
                        .setTitle("Chat Options")
                        .setMessage("What would you like to do with '$sessionTitle'?")
                        .setPositiveButton("Open Chat") { _, _ -> loadChatSession(sessionIdToLoad) }
                        .setNegativeButton("Delete Chat") { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                chatHistoryManager?.deleteSession(sessionIdToLoad)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Chat deleted", Toast.LENGTH_SHORT).show()
                                    // Refresh the drawer history
                                    loadHistoryIntoDrawer()
                                }
                            }
                        }
                        .setNeutralButton("Cancel", null)
                        .show()
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun loadChatSession(sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = chatHistoryManager?.loadSessionMessages(sessionId) ?: emptyList()
            withContext(Dispatchers.Main) {
                chatList.clear()
                conversationHistory.clear()
                
                messages.forEach { msg ->
                    chatList.add(ChatMessage(msg.content, msg.isUser, msg.timestamp))
                }
                
                // Rebuild conversation history for RAG Context
                for (i in 0 until messages.size step 2) {
                    if (i + 1 < messages.size) {
                        conversationHistory.add(messages[i].content to messages[i+1].content)
                    }
                }
                
                chatAdapter.notifyDataSetChanged()
                scrollToBottom()
                
                // Update current session
                currentSessionId = sessionId
                sessionReady = CompletableDeferred()
                sessionReady.complete(sessionId)
                
                Toast.makeText(this@MainActivity, "Chat Loaded", Toast.LENGTH_SHORT).show()
            }
        }
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
    
    // ── Send Feedback: Serialize chat → GitHub Issue with user comment + crash log ──
    private fun sendChatFeedback() {
        if (chatList.isEmpty()) {
            Toast.makeText(this, "No chat to send", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Build dialog with an EditText for user comments
        val commentInput = EditText(this).apply {
            hint = "Describe the issue (optional)"
            setPadding(48, 24, 48, 24)
            minLines = 2
            maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        
        AlertDialog.Builder(this)
            .setTitle("Send Feedback")
            .setMessage("Send chat transcript to the developer. Add a comment below if you'd like:")
            .setView(commentInput)
            .setPositiveButton("Send") { _, _ ->
                val userComment = commentInput.text.toString().trim()
                Toast.makeText(this, "Sending feedback...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val chatTranscript = chatList.joinToString("\n\n") { msg ->
                            val role = if (msg.isUser) "**User**" else "**PoLiTAI**"
                            "$role:\n${msg.content}"
                        }
                        
                        val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nApp Version: 3.1.0"
                        
                        // Build body with optional comment and optional crash log
                        val sb = StringBuilder("## Chat Feedback\n\n$deviceInfo\n\n")
                        
                        if (userComment.isNotEmpty()) {
                            sb.append("### User Comment\n> $userComment\n\n")
                        }
                        
                        // Attach crash log if one exists
                        val crashFile = File(filesDir, "crash_log.txt")
                        if (crashFile.exists()) {
                            val crashContent = crashFile.readText().take(3000)
                            sb.append("### Crash Log\n```\n$crashContent\n```\n\n")
                        }
                        
                        sb.append("---\n\n$chatTranscript")
                        
                        val success = createGitHubIssue(
                            title = "[Feedback] Chat from ${Build.MODEL} - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
                            body = sb.toString(),
                            labels = listOf("feedback", "user-chat")
                        )
                        
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(this@MainActivity, "✓ Feedback sent to developer!", Toast.LENGTH_LONG).show()
                                // Clear crash log after successful send
                                if (crashFile.exists()) crashFile.delete()
                            } else {
                                Toast.makeText(this@MainActivity, "✗ Failed to send. Check internet.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send feedback", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "✗ Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // ── Crash Reporter: Log uncaught exceptions → send on next launch ──
    private fun setupCrashReporter() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "crash_log.txt")
                val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nApp Version: 3.0.0\nTime: ${Date()}\nThread: ${thread.name}"
                val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                crashFile.writeText("$deviceInfo\n\n$stackTrace")
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun sendPendingCrashReport() {
        val crashFile = File(filesDir, "crash_log.txt")
        if (!crashFile.exists()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val crashContent = crashFile.readText()
                val success = createGitHubIssue(
                    title = "[Crash] ${Build.MODEL} - ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}",
                    body = "## Crash Report\n\n```\n$crashContent\n```",
                    labels = listOf("bug", "crash-report")
                )
                if (success) {
                    crashFile.delete()
                    Log.d(TAG, "Crash report sent and cleared")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send crash report", e)
            }
        }
    }
    
    // ── GitHub Issues API ──
    private fun createGitHubIssue(title: String, body: String, labels: List<String>): Boolean {
        val token = BuildConfig.GITHUB_TOKEN
        val url = URL("https://api.github.com/repos/RGxco/PoLiTAI/issues")
        val labelsJson = labels.joinToString(",") { "\"$it\"" }
        val escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
        val json = """{"title":"$title","body":"$escapedBody","labels":[$labelsJson]}"""
        
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        
        connection.outputStream.use { it.write(json.toByteArray()) }
        
        val responseCode = connection.responseCode
        Log.d(TAG, "GitHub API response: $responseCode")
        return responseCode == 201
    }
}
