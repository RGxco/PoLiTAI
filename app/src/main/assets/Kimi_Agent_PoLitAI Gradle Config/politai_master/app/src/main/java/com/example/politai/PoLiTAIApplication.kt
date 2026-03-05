package com.example.politai

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

/**
 * PoLiTAI Application Class
 * Master Grade Edition
 * 
 * Handles:
 * - MultiDex support for large apps
 * - Global application context
 * - Memory management
 */

class PoLiTAIApplication : Application() {
    
    companion object {
        lateinit var instance: PoLiTAIApplication
            private set
    }
    
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize any global components here
        initializeApp()
    }
    
    private fun initializeApp() {
        // Set default preferences
        val prefs = getSharedPreferences("PoLiTAI_Prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("first_run")) {
            prefs.edit().apply {
                putBoolean("first_run", false)
                putBoolean("tts_enabled", true)
                putBoolean("dark_mode", true)
                putBoolean("auto_sync_enabled", false)
                putString("sync_url", "https://raw.githubusercontent.com/yourusername/politai-data/main/")
                putInt("sync_frequency_hours", 6)
                apply()
            }
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        // Clear caches when low on memory
        System.gc()
    }
}
