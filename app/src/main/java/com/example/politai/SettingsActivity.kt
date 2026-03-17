package com.example.politai

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PoLiTAI-Settings"
        private val SYNC_FREQUENCIES = listOf(1, 6, 12, 24)
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var syncSwitch: SwitchCompat
    private lateinit var syncUrlInput: EditText
    private lateinit var syncFrequencySpinner: Spinner
    private lateinit var syncNowButton: Button
    private lateinit var syncProgressBar: ProgressBar
    private lateinit var syncStatusText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var updateLogContainer: LinearLayout

    private lateinit var ttsSwitch: SwitchCompat
    private lateinit var ttsLanguageSpinner: Spinner

    private lateinit var darkModeSwitch: SwitchCompat
    private lateinit var compactModeSwitch: SwitchCompat

    private lateinit var clearHistoryButton: Button
    private lateinit var exportDataButton: Button
    private lateinit var databaseSizeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("PoLiTAI_Prefs", Context.MODE_PRIVATE)

        initializeUI()
        setupSpinners()
        loadSettings()
        setupSyncListeners()
        setupVoiceListeners()
        setupDisplayListeners()
        setupDataListeners()
        updateSyncStatus()
        loadDatabaseStats()
    }

    private fun initializeUI() {
        syncSwitch = findViewById(R.id.syncSwitch)
        syncUrlInput = findViewById(R.id.syncUrlInput)
        syncFrequencySpinner = findViewById(R.id.syncFrequencySpinner)
        syncNowButton = findViewById(R.id.syncNowButton)
        syncProgressBar = findViewById(R.id.syncProgressBar)
        syncStatusText = findViewById(R.id.syncStatusText)
        lastSyncText = findViewById(R.id.lastSyncText)
        updateLogContainer = findViewById(R.id.updateLogContainer)

        ttsSwitch = findViewById(R.id.ttsSwitch)
        ttsLanguageSpinner = findViewById(R.id.ttsLanguageSpinner)

        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        compactModeSwitch = findViewById(R.id.compactModeSwitch)

        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        exportDataButton = findViewById(R.id.exportDataButton)
        databaseSizeText = findViewById(R.id.databaseSizeText)
    }

    private fun setupSpinners() {
        val frequencyAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")
        )
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        syncFrequencySpinner.adapter = frequencyAdapter

        val languageAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            AppLanguages.displayNames()
        )
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ttsLanguageSpinner.adapter = languageAdapter
    }

    private fun loadSettings() {
        syncSwitch.isChecked = prefs.getBoolean("auto_sync_enabled", false)
        syncUrlInput.setText(prefs.getString("sync_url", ""))

        val savedFrequency = prefs.getInt("sync_frequency_hours", 6)
        syncFrequencySpinner.setSelection(SYNC_FREQUENCIES.indexOf(savedFrequency).coerceAtLeast(0))

        ttsSwitch.isChecked = prefs.getBoolean("tts_enabled", true)
        val savedLanguageCode = prefs.getString("tts_language", "en-IN")
        val languageIndex = AppLanguages.codes().indexOf(savedLanguageCode).coerceAtLeast(0)
        ttsLanguageSpinner.setSelection(languageIndex)

        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", true)
        compactModeSwitch.isChecked = prefs.getBoolean("compact_mode", false)

        syncUrlInput.hint = "One URL per line"
        syncUrlInput.isEnabled = syncSwitch.isChecked
        syncFrequencySpinner.isEnabled = syncSwitch.isChecked
    }

    private fun setupSyncListeners() {
        syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_sync_enabled", isChecked).apply()
            syncUrlInput.isEnabled = isChecked
            syncFrequencySpinner.isEnabled = isChecked
            Toast.makeText(this, if (isChecked) "Auto-sync enabled" else "Auto-sync disabled", Toast.LENGTH_SHORT).show()
        }

        syncUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                prefs.edit().putString("sync_url", syncUrlInput.text.toString().trim()).apply()
            }
        }

        syncFrequencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("sync_frequency_hours", SYNC_FREQUENCIES[position]).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

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
                prefs.edit().putString("tts_language", AppLanguages.codes()[position]).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
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
                        runCatching {
                            ChatHistoryManager(this@SettingsActivity).clearAllHistory()
                        }.onSuccess {
                            Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                        }.onFailure {
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

    private fun performManualSync() {
        val rawUrls = syncUrlInput.text.toString().trim()
        if (rawUrls.isBlank()) {
            Toast.makeText(this, "Please enter at least one valid Sync URL", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putString("sync_url", rawUrls).apply()
        syncNowButton.isEnabled = false
        syncProgressBar.visibility = View.VISIBLE
        syncStatusText.text = "Syncing sources..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val summary = SyncRepository.syncUrls(this@SettingsActivity, rawUrls)
                val ragEngine = RAGEngine(this@SettingsActivity)
                ragEngine.clearCache()
                val (dbCount, recordCount) = ragEngine.getDatabaseStats()

                prefs.edit()
                    .putLong("last_sync_time", System.currentTimeMillis())
                    .putInt("db_count", dbCount)
                    .putInt("record_count", recordCount)
                    .apply()

                withContext(Dispatchers.Main) {
                    syncNowButton.isEnabled = true
                    syncProgressBar.visibility = View.GONE
                    syncStatusText.text = "Synced ${summary.sourceCount} source(s) into ${summary.updatedFiles.size} file(s)"
                    databaseSizeText.text = "$dbCount databases • $recordCount records loaded"
                    updateSyncStatus()
                    showUpdateLog(summary.details)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Sync failed", error)
                withContext(Dispatchers.Main) {
                    syncNowButton.isEnabled = true
                    syncProgressBar.visibility = View.GONE
                    syncStatusText.text = "Sync failed: ${error.message}"
                    showUpdateLog(listOf("Sync failed", error.message ?: "Unknown error"))
                }
            }
        }
    }

    private fun updateSyncStatus() {
        val lastSync = prefs.getLong("last_sync_time", 0L)
        lastSyncText.text = if (lastSync > 0L) {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            "Last sync: ${formatter.format(Date(lastSync))}"
        } else {
            "Last sync: Never"
        }
    }

    private fun loadDatabaseStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                RAGEngine(this@SettingsActivity).getDatabaseStats()
            }.onSuccess { (dbCount, recordCount) ->
                prefs.edit()
                    .putInt("db_count", dbCount)
                    .putInt("record_count", recordCount)
                    .apply()

                withContext(Dispatchers.Main) {
                    databaseSizeText.text = "$dbCount databases • $recordCount records loaded"
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to load database stats", error)
                withContext(Dispatchers.Main) {
                    databaseSizeText.text = "Could not load database info"
                }
            }
        }
    }

    private fun showUpdateLog(lines: List<String>) {
        updateLogContainer.removeAllViews()

        lines.ifEmpty { listOf("No recent updates") }.forEach { entry ->
            val logLine = TextView(this).apply {
                text = entry
                setTextColor(resources.getColor(R.color.white_alpha_70, null))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            updateLogContainer.addView(logLine)
        }
    }

    private fun exportData() {
        Toast.makeText(this, "Export feature coming soon", Toast.LENGTH_SHORT).show()
    }
}
