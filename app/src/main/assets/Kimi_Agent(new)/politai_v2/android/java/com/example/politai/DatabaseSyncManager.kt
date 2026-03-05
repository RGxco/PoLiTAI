package com.example.politai

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * PoLiTAI - Database Sync Manager
 * Handles background syncing of JSON databases from remote sources
 */

sealed class SyncResult {
    data class Success(val updatedFiles: List<String>) : SyncResult()
    data class NoUpdates(val message: String = "No updates available") : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class DatabaseSyncManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-Sync"
        private const val SYNC_WORK_NAME = "politai_database_sync"
        private const val MANIFEST_FILE = "manifest.json"
        
        // Default remote URL - can be customized in settings
        const val DEFAULT_SYNC_URL = "https://raw.githubusercontent.com/yourusername/politai-data/main/"
        
        // List of databases to sync
        val SYNC_DATABASES = listOf(
            "politician_database.json",
            "government_schemes.json",
            "governance_meetings.json",
            "india_major_bills.json",
            "constituency_complaints.json",
            "budget_allocations.json",
            "parliamentary_debates.json",
            "rbi_repo_rate_history.json",
            "india_cpi_monthly.json",
            "india_gdp_quarterly.json"
        )
    }
    
    private val prefs = context.getSharedPreferences("PoLiTAI_Prefs", Context.MODE_PRIVATE)
    
    /**
     * Schedule periodic background sync using WorkManager
     */
    fun schedulePeriodicSync() {
        val hours = prefs.getInt("sync_frequency_hours", 12)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncWork = PeriodicWorkRequestBuilder<DatabaseSyncWorker>(
            hours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(SYNC_WORK_NAME)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWork
        )
        
        Log.d(TAG, "Scheduled periodic sync every $hours hours")
    }
    
    /**
     * Cancel scheduled sync
     */
    fun cancelScheduledSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        Log.d(TAG, "Cancelled scheduled sync")
    }
    
    /**
     * Perform immediate sync
     */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = prefs.getString("sync_url", DEFAULT_SYNC_URL) ?: DEFAULT_SYNC_URL
            val updatedFiles = mutableListOf<String>()
            
            // First, fetch manifest to check versions
            val manifestResult = fetchManifest(baseUrl)
            if (manifestResult == null) {
                return@withContext SyncResult.Error("Could not fetch manifest file")
            }
            
            // Parse manifest
            val manifest = try {
                JSONObject(manifestResult)
            } catch (e: Exception) {
                return@withContext SyncResult.Error("Invalid manifest format")
            }
            
            // Check each database
            for (dbName in SYNC_DATABASES) {
                try {
                    val remoteVersion = manifest.optJSONObject("files")?.optString(dbName, "")
                    val localVersion = prefs.getString("db_version_$dbName", "")
                    
                    // Skip if versions match (and not forced)
                    if (remoteVersion == localVersion && remoteVersion.isNotEmpty()) {
                        Log.d(TAG, "$dbName is up to date")
                        continue
                    }
                    
                    // Download updated file
                    val fileUrl = "$baseUrl$dbName"
                    val content = downloadFile(fileUrl)
                    
                    if (content != null) {
                        // Save to internal storage
                        saveToInternalStorage(dbName, content)
                        
                        // Update version in prefs
                        prefs.edit().putString("db_version_$dbName", remoteVersion).apply()
                        
                        updatedFiles.add(dbName)
                        Log.d(TAG, "Updated $dbName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing $dbName: ${e.message}")
                }
            }
            
            return@withContext if (updatedFiles.isEmpty()) {
                SyncResult.NoUpdates()
            } else {
                SyncResult.Success(updatedFiles)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            return@withContext SyncResult.Error("Sync failed: ${e.message}")
        }
    }
    
    /**
     * Fetch manifest file from remote
     */
    private fun fetchManifest(baseUrl: String): String? {
        val manifestUrl = "$baseUrl$MANIFEST_FILE"
        return downloadFile(manifestUrl)
    }
    
    /**
     * Download file from URL
     */
    private fun downloadFile(fileUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(fileUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "HTTP $responseCode for $fileUrl")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Save downloaded content to internal storage
     */
    private fun saveToInternalStorage(filename: String, content: String) {
        try {
            // Save to app's files directory
            val file = File(context.filesDir, filename)
            file.writeText(content)
            Log.d(TAG, "Saved $filename (${content.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $filename: ${e.message}")
            throw e
        }
    }
    
    /**
     * Get local database file (from internal storage or assets fallback)
     */
    fun getLocalDatabaseFile(filename: String): File {
        // First check internal storage (synced files)
        val internalFile = File(context.filesDir, filename)
        if (internalFile.exists()) {
            return internalFile
        }
        
        // Fall back to assets (bundled files)
        // Note: Assets are read-only, so we copy to internal storage
        val assetFile = File(context.filesDir, "assets_$filename")
        if (!assetFile.exists()) {
            try {
                context.assets.open(filename).use { input ->
                    assetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not copy asset file: $filename")
            }
        }
        return assetFile
    }
    
    /**
     * Check if a database needs update
     */
    fun needsUpdate(filename: String): Boolean {
        val localVersion = prefs.getString("db_version_$filename", "")
        return localVersion.isEmpty()
    }
}

/**
 * WorkManager Worker for background sync
 */
class DatabaseSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val syncManager = DatabaseSyncManager(applicationContext)
        
        return try {
            when (val result = syncManager.syncNow()) {
                is SyncResult.Success -> {
                    // Show notification if updates were found
                    if (result.updatedFiles.isNotEmpty()) {
                        showUpdateNotification(result.updatedFiles)
                    }
                    Result.success()
                }
                is SyncResult.NoUpdates -> Result.success()
                is SyncResult.Error -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e("DatabaseSyncWorker", "Sync failed: ${e.message}")
            Result.retry()
        }
    }
    
    private fun showUpdateNotification(updatedFiles: List<String>) {
        // Implementation for showing notification
        // Would use NotificationManager
        Log.d("DatabaseSyncWorker", "Updates available: $updatedFiles")
    }
}