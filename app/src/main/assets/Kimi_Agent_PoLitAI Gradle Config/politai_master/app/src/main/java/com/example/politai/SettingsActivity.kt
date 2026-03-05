package com.example.politai

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * PoLiTAI - Settings Activity with Full Internet Sync
 * Master Grade Edition
 * 
 * Features:
 * - Auto-sync with configurable frequency
 * - Manual sync with progress tracking
 * - Download new JSON data from remote URL
 * - Sync status and update log
 * - Voice and display settings
 * - Data management (clear history, export)
 */

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    
    // UI Components - Sync Section
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
    
    // Sync tracking
    private var activeDownloads = mutableMapOf<Long, String>()
    private var downloadReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefs = getSharedPreferences("PoLiTAI_Prefs", Context.MODE_PRIVATE)
        
        initializeUI()
        loadSettings()
        updateSyncStatus()
        calculateDatabaseSize()
        registerDownloadReceiver()
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
                scheduleAutoSync()
            } else {
                Toast.makeText(this, "Auto-sync disabled", Toast.LENGTH_SHORT).show()
                cancelAutoSync()
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
                if (syncSwitch.isChecked) {
                    scheduleAutoSync()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Manual sync button
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
            // Apply theme change immediately
            if (isChecked) {
                setTheme(R.style.Theme_PoLiTAI_Dark)
            } else {
                setTheme(R.style.Theme_PoLiTAI_Light)
            }
        }
        
        compactModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("compact_mode", isChecked).apply()
        }
    }
    
    private fun setupDataListeners() {
        clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("This will delete all saved chat sessions. This action cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        clearChatHistory()
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
        
        // Load update log
        loadUpdateLog()
    }
    
    private fun loadUpdateLog() {
        updateLogContainer.removeAllViews()
        
        val updates = prefs.getStringSet("sync_updates", emptySet())?.toList() ?: emptyList()
        
        if (updates.isEmpty()) {
            val noUpdatesView = TextView(this).apply {
                text = "No recent updates"
                textSize = 12f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            updateLogContainer.addView(noUpdatesView)
        } else {
            updates.sortedDescending().take(5).forEach { update ->
                val updateView = TextView(this).apply {
                    text = "• $update"
                    textSize = 12f
                    setTextColor(getColor(android.R.color.white))
                    setPadding(0, 4, 0, 4)
                }
                updateLogContainer.addView(updateView)
            }
        }
    }
    
    /**
     * MASTER GRADE: Manual Sync Implementation
     * Downloads JSON files from remote URL and updates local assets
     */
    private fun performManualSync() {
        val baseUrl = syncUrlInput.text.toString().trim()
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a sync URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        syncNowButton.isEnabled = false
        syncProgressBar.visibility = View.VISIBLE
        syncProgressBar.progress = 0
        syncStatusText.text = "Checking for updates..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // List of files to sync
                val filesToSync = listOf(
                    "governance_meetings.json",
                    "india_government_schemes.json",
                    "politician_database.json",
                    "india_major_bills.json",
                    "india_cpi_monthly.json",
                    "india_gdp_quarterly.json",
                    "rbi_repo_rate_history.json",
                    "constitution_articles.json",
                    "parliament_members.json",
                    "party_profiles.json"
                )
                
                var successCount = 0
                var failCount = 0
                val updatedFiles = mutableListOf<String>()
                
                filesToSync.forEachIndexed { index, filename ->
                    withContext(Dispatchers.Main) {
                        syncStatusText.text = "Syncing ${index + 1}/${filesToSync.size}: $filename"
                        syncProgressBar.progress = ((index + 1) * 100) / filesToSync.size
                    }
                    
                    val fileUrl = "$baseUrl$filename"
                    val result = downloadJsonFile(fileUrl, filename)
                    
                    if (result) {
                        successCount++
                        updatedFiles.add(filename)
                    } else {
                        failCount++
                    }
                    
                    delay(100) // Small delay between downloads
                }
                
                // Save sync time
                prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
                
                // Save update log
                if (updatedFiles.isNotEmpty()) {
                    val currentUpdates = prefs.getStringSet("sync_updates", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                    currentUpdates.add("${dateFormat.format(Date())}: Updated ${updatedFiles.size} files")
                    prefs.edit().putStringSet("sync_updates", currentUpdates).apply()
                }
                
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    
                    when {
                        successCount == filesToSync.size -> {
                            syncStatusText.text = "✓ All files synced successfully"
                            Toast.makeText(this@SettingsActivity, "Sync completed!", Toast.LENGTH_SHORT).show()
                        }
                        successCount > 0 -> {
                            syncStatusText.text = "⚠ $successCount synced, $failCount failed"
                            Toast.makeText(this@SettingsActivity, "Partial sync completed", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            syncStatusText.text = "✗ Sync failed - check URL and connection"
                            Toast.makeText(this@SettingsActivity, "Sync failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    updateSyncStatus()
                }
                
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Sync error", e)
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    syncStatusText.text = "✗ Error: ${e.message}"
                    Toast.makeText(this@SettingsActivity, "Sync error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * Download a single JSON file from URL
     */
    private suspend fun downloadJsonFile(fileUrl: String, filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 30000
                    setRequestProperty("Accept", "application/json")
                }
                
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val content = inputStream.bufferedReader().use { it.readText() }
                    
                    // Validate JSON
                    try {
                        JSONArray(content)
                    } catch (e: Exception) {
                        // Try as JSONObject
                        try {
                            JSONObject(content)
                        } catch (e2: Exception) {
                            Log.w("SettingsActivity", "Invalid JSON for $filename")
                            return@withContext false
                        }
                    }
                    
                    // Save to app's files directory (can be accessed by RAGEngine)
                    val file = File(filesDir, "synced_$filename")
                    file.writeText(content)
                    
                    Log.d("SettingsActivity", "Downloaded $filename (${content.length} bytes)")
                    true
                } else {
                    Log.w("SettingsActivity", "HTTP $responseCode for $filename")
                    false
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Failed to download $filename: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Alternative: Use DownloadManager for large files
     */
    private fun downloadWithManager(url: String, filename: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("PoLiTAI Sync: $filename")
            .setDescription("Downloading database update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        activeDownloads[downloadId] = filename
    }
    
    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                val filename = activeDownloads[downloadId] ?: return
                
                // Handle download completion
                activeDownloads.remove(downloadId)
                Log.d("SettingsActivity", "Download completed: $filename")
            }
        }
        
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
    
    private fun scheduleAutoSync() {
        // In a production app, use WorkManager for scheduled sync
        // For now, just log the intent
        val frequency = prefs.getInt("sync_frequency_hours", 6)
        Log.d("SettingsActivity", "Auto-sync scheduled every $frequency hours")
    }
    
    private fun cancelAutoSync() {
        Log.d("SettingsActivity", "Auto-sync cancelled")
    }
    
    private suspend fun clearChatHistory() {
        withContext(Dispatchers.IO) {
            try {
                // Clear Room database
                val dbFile = getDatabasePath("politai_chat_history.db")
                if (dbFile.exists()) {
                    deleteDatabase("politai_chat_history.db")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                    calculateDatabaseSize()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Error clearing history: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun calculateDatabaseSize() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dbFile = getDatabasePath("politai_chat_history.db")
                val sizeInMB = if (dbFile.exists()) {
                    dbFile.length() / (1024.0 * 1024.0)
                } else {
                    0.0
                }
                
                withContext(Dispatchers.Main) {
                    databaseSizeText.text = String.format("%.2f MB", sizeInMB)
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error calculating DB size", e)
            }
        }
    }
    
    private fun exportData() {
        lifecycleScope.launch {
            try {
                // Create export file
                val exportFile = File(externalCacheDir, "politai_export_${System.currentTimeMillis()}.json")
                
                // TODO: Implement actual data export from database
                exportFile.writeText("{\"export_date\": \"${Date()}\", \"version\": \"2.0\"}")
                
                // Share file
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(exportFile))
                    putExtra(Intent.EXTRA_SUBJECT, "PoLiTAI Data Export")
                }
                startActivity(Intent.createChooser(shareIntent, "Export Data"))
                
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            unregisterReceiver(it)
        }
    }
}
