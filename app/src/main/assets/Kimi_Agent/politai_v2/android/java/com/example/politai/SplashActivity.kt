package com.example.politai

import android.animation.*
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/**
 * PoLiTAI - Splash Screen with Branding Animation
 * 
 * Features:
 * - Animated logo reveal
 * - Progress indicator
 * - "Developed by Rishit Rohan" watermark
 * - Smooth transition to MainActivity
 */

class SplashActivity : AppCompatActivity() {
    
    private lateinit var logoImage: ImageView
    private lateinit var appNameText: TextView
    private lateinit var taglineText: TextView
    private lateinit var developerText: TextView
    private lateinit var loadingText: TextView
    private lateinit var progressBar: View
    
    companion object {
        private const val SPLASH_DURATION = 3000L // 3 seconds
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        initializeViews()
        startAnimationSequence()
    }
    
    private fun initializeViews() {
        logoImage = findViewById(R.id.splashLogo)
        appNameText = findViewById(R.id.splashAppName)
        taglineText = findViewById(R.id.splashTagline)
        developerText = findViewById(R.id.splashDeveloper)
        loadingText = findViewById(R.id.splashLoading)
        progressBar = findViewById(R.id.splashProgress)
    }
    
    private fun startAnimationSequence() {
        // Initial state - all views invisible
        logoImage.alpha = 0f
        logoImage.scaleX = 0.5f
        logoImage.scaleY = 0.5f
        
        appNameText.alpha = 0f
        appNameText.translationY = 50f
        
        taglineText.alpha = 0f
        taglineText.translationY = 30f
        
        developerText.alpha = 0f
        
        loadingText.alpha = 0f
        progressBar.alpha = 0f
        progressBar.scaleX = 0f
        
        // Create animation sequence
        val animatorSet = AnimatorSet()
        
        // 1. Logo fade in and scale up (0-800ms)
        val logoFadeIn = ObjectAnimator.ofFloat(logoImage, View.ALPHA, 0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }
        
        val logoScaleX = ObjectAnimator.ofFloat(logoImage, View.SCALE_X, 0.5f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.5f)
        }
        
        val logoScaleY = ObjectAnimator.ofFloat(logoImage, View.SCALE_Y, 0.5f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.5f)
        }
        
        // 2. App name slide up and fade in (400-1000ms)
        val nameFadeIn = ObjectAnimator.ofFloat(appNameText, View.ALPHA, 0f, 1f).apply {
            duration = 500
            startDelay = 400
        }
        
        val nameSlideUp = ObjectAnimator.ofFloat(appNameText, View.TRANSLATION_Y, 50f, 0f).apply {
            duration = 600
            startDelay = 400
            interpolator = DecelerateInterpolator()
        }
        
        // 3. Tagline fade in (700-1200ms)
        val taglineFadeIn = ObjectAnimator.ofFloat(taglineText, View.ALPHA, 0f, 1f).apply {
            duration = 400
            startDelay = 700
        }
        
        val taglineSlideUp = ObjectAnimator.ofFloat(taglineText, View.TRANSLATION_Y, 30f, 0f).apply {
            duration = 500
            startDelay = 700
        }
        
        // 4. Developer watermark fade in (1000-1500ms)
        val developerFadeIn = ObjectAnimator.ofFloat(developerText, View.ALPHA, 0f, 0.7f).apply {
            duration = 500
            startDelay = 1000
        }
        
        // 5. Loading indicator (1200ms onwards)
        val loadingFadeIn = ObjectAnimator.ofFloat(loadingText, View.ALPHA, 0f, 1f).apply {
            duration = 300
            startDelay = 1200
        }
        
        val progressFadeIn = ObjectAnimator.ofFloat(progressBar, View.ALPHA, 0f, 1f).apply {
            duration = 300
            startDelay = 1200
        }
        
        val progressScale = ObjectAnimator.ofFloat(progressBar, View.SCALE_X, 0f, 1f).apply {
            duration = 1500
            startDelay = 1200
            interpolator = LinearInterpolator()
        }
        
        // Add pulsing animation to logo
        val pulseAnimation = ObjectAnimator.ofFloat(logoImage, View.ALPHA, 1f, 0.7f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            startDelay = 1500
        }
        
        // Combine all animations
        animatorSet.playTogether(
            logoFadeIn, logoScaleX, logoScaleY,
            nameFadeIn, nameSlideUp,
            taglineFadeIn, taglineSlideUp,
            developerFadeIn,
            loadingFadeIn, progressFadeIn, progressScale
        )
        
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Start pulsing animation
                pulseAnimation.start()
                
                // Simulate loading and transition
                lifecycleScope.launch {
                    delay(SPLASH_DURATION - 1500)
                    transitionToMainActivity()
                }
            }
        })
        
        animatorSet.start()
    }
    
    private fun transitionToMainActivity() {
        // Fade out all views
        val fadeOut = ObjectAnimator.ofFloat(
            findViewById<View>(android.R.id.content), 
            View.ALPHA, 
            1f, 
            0f
        ).apply {
            duration = 400
            interpolator = AccelerateInterpolator()
        }
        
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Start MainActivity
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        })
        
        fadeOut.start()
    }
}