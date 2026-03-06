package com.example.politai

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * PoLiTAI - Settings Activity v2
 * 
 * Changes:
 * - Real database stats (count files & records from assets)
 * - "Sync Now" actually re-counts databases and clears RAG cache
 * - Clear History actually clears the Room database
 */

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PoLiTAI-Settings"
    }
    
    private lateinit var prefs: SharedPreferences
    
    // UI Components
    private lateinit var syncSwitch: SwitchCompat
    private lateinit var syncUrlInput: EditText
    private lateinit var syncFrequencySpinner: Spinner
    private lateinit var syncNowButton: Button
    private lateinit var syncProgressBar: ProgressBar
    private lateinit var syncStatusText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var updateLogContainer: LinearLayout
    
    // Voice Settings
    private lateinit var ttsSwitch: SwitchCompat
    private lateinit var ttsLanguageSpinner: Spinner
    
    // Display Settings
    private lateinit var darkModeSwitch: SwitchCompat
    private lateinit var compactModeSwitch: SwitchCompat
    
    // Data Settings
    private lateinit var clearHistoryButton: Button
    private lateinit var exportDataButton: Button
    private lateinit var databaseSizeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefs = getSharedPreferences("PoLiTAI_Prefs", Context.MODE_PRIVATE)
        
        initializeUI()
        loadSettings()
        updateSyncStatus()
        loadDatabaseStats()
    }
    
    private fun initializeUI() {
        // Sync Section
        syncSwitch = findViewById(R.id.syncSwitch)
        syncUrlInput = findViewById(R.id.syncUrlInput)
        syncFrequencySpinner = findViewById(R.id.syncFrequencySpinner)
        syncNowButton = findViewById(R.id.syncNowButton)
        syncProgressBar = findViewById(R.id.syncProgressBar)
        syncStatusText = findViewById(R.id.syncStatusText)
        lastSyncText = findViewById(R.id.lastSyncText)
        updateLogContainer = findViewById(R.id.updateLogContainer)
        
        // Voice Section
        ttsSwitch = findViewById(R.id.ttsSwitch)
        ttsLanguageSpinner = findViewById(R.id.ttsLanguageSpinner)
        
        // Display Section
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        compactModeSwitch = findViewById(R.id.compactModeSwitch)
        
        // Data Section
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        exportDataButton = findViewById(R.id.exportDataButton)
        databaseSizeText = findViewById(R.id.databaseSizeText)
        
        // Setup listeners
        setupSyncListeners()
        setupVoiceListeners()
        setupDisplayListeners()
        setupDataListeners()
        
        // Setup spinners
        setupSpinners()
    }
    
    private fun setupSyncListeners() {
        // Auto-sync toggle
        syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_sync_enabled", isChecked).apply()
            syncUrlInput.isEnabled = isChecked
            syncFrequencySpinner.isEnabled = isChecked
            
            if (isChecked) {
                Toast.makeText(this, "Auto-sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Auto-sync disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Sync URL change
        syncUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = syncUrlInput.text.toString()
                prefs.edit().putString("sync_url", url).apply()
            }
        }
        
        // Sync frequency change
        syncFrequencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val frequencies = listOf(1, 6, 12, 24) // hours
                prefs.edit().putInt("sync_frequency_hours", frequencies[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Manual sync button — NOW DOES REAL WORK
        syncNowButton.setOnClickListener {
            performManualSync()
        }
    }
    
    private fun setupVoiceListeners() {
        ttsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tts_enabled", isChecked).apply()
        }
        
        ttsLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val languages = listOf("en-IN", "hi-IN", "ta-IN", "te-IN", "mr-IN")
                prefs.edit().putString("tts_language", languages[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupDisplayListeners() {
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }
        
        compactModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("compact_mode", isChecked).apply()
        }
    }
    
    private fun setupDataListeners() {
        clearHistoryButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("This will delete all saved chat sessions. This action cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val manager = ChatHistoryManager(this@SettingsActivity)
                            manager.clearAllHistory()
                            Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@SettingsActivity, "Failed to clear history", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        exportDataButton.setOnClickListener {
            exportData()
        }
    }
    
    private fun setupSpinners() {
        // Frequency options
        val frequencies = arrayOf("Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")
        val frequencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        syncFrequencySpinner.adapter = frequencyAdapter
        
        // Language options
        val languages = arrayOf("English (India)", "Hindi", "Tamil", "Telugu", "Marathi")
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ttsLanguageSpinner.adapter = languageAdapter
    }
    
    private fun loadSettings() {
        // Sync settings
        syncSwitch.isChecked = prefs.getBoolean("auto_sync_enabled", false)
        syncUrlInput.setText(prefs.getString("sync_url", "https://raw.githubusercontent.com/yourusername/politai-data/main/"))
        syncFrequencySpinner.setSelection(1) // Default to 6 hours
        
        // Voice settings
        ttsSwitch.isChecked = prefs.getBoolean("tts_enabled", true)
        ttsLanguageSpinner.setSelection(0) // Default to English
        
        // Display settings
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", true)
        compactModeSwitch.isChecked = prefs.getBoolean("compact_mode", false)
    }
    
    private fun updateSyncStatus() {
        val lastSync = prefs.getLong("last_sync_time", 0)
        if (lastSync > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            lastSyncText.text = "Last sync: ${dateFormat.format(Date(lastSync))}"
        } else {
            lastSyncText.text = "Last sync: Never"
        }
    }
    
    /**
     * Load real database stats from assets directory.
     */
    private fun loadDatabaseStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ragEngine = RAGEngine(this@SettingsActivity)
                val (dbCount, recordCount) = ragEngine.getDatabaseStats()
                
                withContext(Dispatchers.Main) {
                    databaseSizeText.text = "$dbCount databases • $recordCount records loaded"
                }
                
                // Save stats to prefs
                prefs.edit()
                    .putInt("db_count", dbCount)
                    .putInt("record_count", recordCount)
                    .apply()
                    
                Log.d(TAG, "Database stats: $dbCount DBs, $recordCount records")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load database stats", e)
                withContext(Dispatchers.Main) {
                    databaseSizeText.text = "Could not load database info"
                }
            }
        }
    }
    
    /**
     * Real sync: Download from URL, strip HTML, save as JSON, then re-scan databases.
     */
    private fun performManualSync() {
        val syncUrlStr = prefs.getString("sync_url", "") ?: ""
        if (syncUrlStr.isBlank()) {
            Toast.makeText(this, "Please enter a valid Sync URL", Toast.LENGTH_SHORT).show()
            return
        }

        syncNowButton.isEnabled = false
        syncProgressBar.visibility = View.VISIBLE
        syncStatusText.text = "Downloading data from URL..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Download data from URL
                val url = URL(syncUrlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP Error: ${connection.responseCode}")
                }
                
                val rawHtml = connection.inputStream.bufferedReader().use { it.readText() }
                
                // 2. Extract text with better HTML stripping (focused on paragraphs)
                val cleanText = stripHtmlTags(rawHtml).trim().take(15000) // increase cap
                
                if (cleanText.isEmpty() || cleanText.length < 100) {
                    throw Exception("Could not extract meaningful text from URL. Make sure it's a valid news article.")
                }

                // 3. Save as JSON format expected by RAGEngine
                val jsonContent = """
                    [
                      {
                        "source": "Internet Sync",
                        "title": "Latest News & Updates",
                        "text": "${cleanText.replace("\"", "\\\"").replace("\n", "  ")}"
                      }
                    ]
                """.trimIndent()
                
                val syncFile = java.io.File(filesDir, "synced_news.json")
                syncFile.writeText(jsonContent)
                
                // 4. Update stats
                val ragEngine = RAGEngine(this@SettingsActivity)
                ragEngine.clearCache()  // Force fresh reload
                val (dbCount, recordCount) = ragEngine.getDatabaseStats()
                
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    syncStatusText.text = "✓ Synced successfully! $dbCount DBs ready"
                    databaseSizeText.text = "$dbCount databases • $recordCount records loaded"
                    
                    prefs.edit()
                        .putLong("last_sync_time", System.currentTimeMillis())
                        .putInt("db_count", dbCount)
                        .putInt("record_count", recordCount)
                        .apply()
                    updateSyncStatus()
                    
                    // Show update log with preview
                    updateLogContainer.removeAllViews()
                    val previewSnippet = if (cleanText.length > 5000) cleanText.take(5000) + "..." else cleanText
                    val logText = TextView(this@SettingsActivity).apply {
                        text = "Downloaded news from internet.\nScanned $dbCount JSON databases\n$recordCount total records available\nRAG cache cleared and rebuilt\n\nPreview:\n\"$previewSnippet\""
                        setTextColor(resources.getColor(R.color.white_alpha_70, null))
                        textSize = 12f
                        setPadding(0, 8, 0, 0)
                        
                        // Make it scrollable internally
                        maxLines = 15
                        movementMethod = android.text.method.ScrollingMovementMethod()
                        setOnTouchListener { v, event ->
                            v.parent.requestDisallowInterceptTouchEvent(true)
                            when (event.action and android.view.MotionEvent.ACTION_MASK) {
                                android.view.MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                    }
                    updateLogContainer.addView(logText)
                }
                
                Log.d(TAG, "Manual sync complete: $dbCount DBs, $recordCount records")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    syncStatusText.text = "✗ Sync failed: ${e.message}"
                }
            }
        }
    }
    
    private fun stripHtmlTags(html: String): String {
        // 1. Remove standard non-text tags
        var temp = html.replace(Regex("<(style|script|noscript|header|footer|nav)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        
        // 2. Extract paragraph texts explicitly (where most news lives)
        val paragraphRegex = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val paragraphs = paragraphRegex.findAll(temp)
        
        val extractedText = if (paragraphs.any()) {
            paragraphs.joinToString("\n\n") { it.groupValues[1] }
        } else {
            temp // Fallback if no <p> tags found
        }
        
        // 3. Strip all remaining HTML tags
        temp = extractedText.replace(Regex("<[^>]*>"), " ")
        
        // 4. Clean up whitespace and HTML entities
        return temp.replace(Regex("&nbsp;|&amp;|&lt;|&gt;|&quot;|&apos;"), " ")
                   .replace(Regex("\\s+"), " ")
                   .trim()
    }
    
    private fun exportData() {
        Toast.makeText(this, "Export feature coming soon", Toast.LENGTH_SHORT).show()
    }
}
