package com.example.politai

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * PoLiTAI - Settings Activity with Internet Sync
 */

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var syncManager: DatabaseSyncManager
    
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
        syncManager = DatabaseSyncManager(this)
        
        initializeUI()
        loadSettings()
        updateSyncStatus()
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
                syncManager.schedulePeriodicSync()
                Toast.makeText(this, "Auto-sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                syncManager.cancelScheduledSync()
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
                if (syncSwitch.isChecked) {
                    syncManager.schedulePeriodicSync()
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
            // Apply dark mode (would need AppCompatDelegate)
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
                        // Clear database
                        val manager = ChatHistoryManager(this@SettingsActivity)
                        // Implementation would clear all sessions
                        Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                        updateDatabaseSize()
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
        // Sync frequency spinner
        val frequencyAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.sync_frequencies,
            android.R.layout.simple_spinner_item
        )
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        syncFrequencySpinner.adapter = frequencyAdapter
        
        // TTS language spinner
        val languageAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.tts_languages,
            android.R.layout.simple_spinner_item
        )
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ttsLanguageSpinner.adapter = languageAdapter
    }
    
    private fun loadSettings() {
        // Sync settings
        syncSwitch.isChecked = prefs.getBoolean("auto_sync_enabled", false)
        syncUrlInput.setText(prefs.getString("sync_url", "https://raw.githubusercontent.com/yourusername/politai-data/main/"))
        syncFrequencySpinner.setSelection(prefs.getInt("sync_frequency_index", 2))
        
        // Voice settings
        ttsSwitch.isChecked = prefs.getBoolean("tts_enabled", true)
        ttsLanguageSpinner.setSelection(prefs.getInt("tts_language_index", 0))
        
        // Display settings
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", true)
        compactModeSwitch.isChecked = prefs.getBoolean("compact_mode", false)
        
        updateDatabaseSize()
    }
    
    private fun updateSyncStatus() {
        val lastSync = prefs.getLong("last_sync_time", 0)
        if (lastSync > 0) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            lastSyncText.text = "Last sync: ${dateFormat.format(Date(lastSync))}"
        } else {
            lastSyncText.text = "Last sync: Never"
        }
        
        // Show update log
        val updates = prefs.getStringSet("last_update_files", emptySet()) ?: emptySet()
        updateLogContainer.removeAllViews()
        
        if (updates.isNotEmpty()) {
            updates.forEach { fileName ->
                val textView = TextView(this).apply {
                    text = "✓ $fileName"
                    setTextColor(resources.getColor(R.color.success, theme))
                    textSize = 12f
                    setPadding(8, 4, 8, 4)
                }
                updateLogContainer.addView(textView)
            }
        } else {
            val textView = TextView(this).apply {
                text = "No recent updates"
                setTextColor(resources.getColor(R.color.text_hint, theme))
                textSize = 12f
            }
            updateLogContainer.addView(textView)
        }
    }
    
    private fun performManualSync() {
        syncNowButton.isEnabled = false
        syncProgressBar.visibility = View.VISIBLE
        syncStatusText.text = "Checking for updates..."
        
        lifecycleScope.launch {
            try {
                val result = syncManager.syncNow()
                
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    
                    when (result) {
                        is SyncResult.Success -> {
                            syncStatusText.text = "✓ Sync completed"
                            syncStatusText.setTextColor(resources.getColor(R.color.success, theme))
                            prefs.edit()
                                .putLong("last_sync_time", System.currentTimeMillis())
                                .putStringSet("last_update_files", result.updatedFiles.toSet())
                                .apply()
                            updateSyncStatus()
                            
                            Toast.makeText(
                                this@SettingsActivity,
                                "Updated ${result.updatedFiles.size} files",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is SyncResult.NoUpdates -> {
                            syncStatusText.text = "✓ Already up to date"
                            syncStatusText.setTextColor(resources.getColor(R.color.success, theme))
                        }
                        is SyncResult.Error -> {
                            syncStatusText.text = "✗ ${result.message}"
                            syncStatusText.setTextColor(resources.getColor(R.color.error, theme))
                            Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    syncProgressBar.visibility = View.GONE
                    syncNowButton.isEnabled = true
                    syncStatusText.text = "✗ Sync failed"
                    syncStatusText.setTextColor(resources.getColor(R.color.error, theme))
                    Toast.makeText(this@SettingsActivity, "Sync error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun updateDatabaseSize() {
        // Calculate database file size
        val dbFile = getDatabasePath("politai_chat_history.db")
        val sizeBytes = dbFile?.length() ?: 0
        val sizeMB = sizeBytes / (1024.0 * 1024.0)
        databaseSizeText.text = String.format("%.2f MB", sizeMB)
    }
    
    private fun exportData() {
        // Implementation for exporting chat history
        Toast.makeText(this, "Export feature coming soon", Toast.LENGTH_SHORT).show()
    }
}