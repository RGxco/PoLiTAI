package com.example.politai

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for download completion events
 * Used by the Internet Sync feature
 */

class DownloadCompleteReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PoLiTAI-Download"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            
            if (downloadId != -1L) {
                Log.d(TAG, "Download completed: $downloadId")
                
                // Query the download status
                val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager?.query(query)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = it.getInt(statusIndex)
                        
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.d(TAG, "Download successful: $downloadId")
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = it.getInt(reasonIndex)
                                Log.e(TAG, "Download failed: $downloadId, reason: $reason")
                            }
                        }
                    }
                }
            }
        }
    }
}
