# PoLiTAI Master Grade - Quick Reference

## Critical Files to Replace

### 1. MainActivity.kt (GPU Optimization)
**Path:** `app/src/main/java/com/example/politai/MainActivity.kt`

**Key Changes:**
- Adreno 618 GPU optimization
- Staged initialization sequence
- Source citations display
- Memory management

### 2. RAGEngine.kt (Source Citations)
**Path:** `app/src/main/java/com/example/politai/RAGEngine.kt`

**Key Changes:**
- `RAGContextResult` data class with sources
- `loadRAGContextWithSources()` method
- Database info tracking

### 3. SettingsActivity.kt (Internet Sync)
**Path:** `app/src/main/java/com/example/politai/SettingsActivity.kt`

**Key Changes:**
- `performManualSync()` method
- JSON download with validation
- Progress tracking
- Update log

### 4. SystemPrompts.kt (Duplicate Fix)
**Path:** `app/src/main/java/com/example/politai/SystemPrompts.kt`

**Action:** Replace both `SystemPrompt.kt` and `SystemPrompts.kt` with this single file

## New Files to Add

### Application Class
**Path:** `app/src/main/java/com/example/politai/PoLiTAIApplication.kt`

**Manifest Update Required:**
```xml
<application
    android:name=".PoLiTAIApplication"
    ... >
```

### Download Receiver
**Path:** `app/src/main/java/com/example/politai/DownloadCompleteReceiver.kt`

**Manifest Update Required:**
```xml
<receiver
    android:name=".DownloadCompleteReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.DOWNLOAD_COMPLETE" />
    </intent-filter>
</receiver>
```

## Gradle Updates

### app/build.gradle
```gradle
android {
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libOpenCL.so'
        jniLibs {
            useLegacyPackaging true
        }
    }
}

dependencies {
    implementation 'com.google.mediapipe:tasks-genai:0.10.32'
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'androidx.multidex:multidex:2.0.1'
}
```

## GPU Optimization Code Reference

```kotlin
// In MainActivity.kt

// Adreno 618 constants
private const val MAX_TOKENS_GPU = 1024
private const val MAX_TOKENS_CPU = 512

// GPU initialization with fallback
private suspend fun loadModelOptimized() {
    // Stage 1: Try GPU
    val gpuSuccess = tryInitializeGPU(modelFile)
    if (gpuSuccess) {
        showStatus("PoLiTAI GPU Ready (Adreno 618)")
        return
    }
    
    // Stage 2: Fallback to CPU
    val cpuSuccess = tryInitializeCPU(modelFile)
    if (cpuSuccess) {
        showStatus("PoLiTAI CPU Ready")
        return
    }
}
```

## Source Citations Code Reference

```kotlin
// In RAGEngine.kt
data class RAGContextResult(
    val context: String,
    val sources: List<String>,  // ["Government Schemes", "Politician Database"]
    val results: List<RAGResult>
)

// Usage in MainActivity.kt
val ragResult = ragEngine?.loadRAGContextWithSources(fullQuery, context)
lastUsedSources = ragResult?.sources ?: emptyList()
displaySources(lastUsedSources)
```

## Internet Sync Code Reference

```kotlin
// In SettingsActivity.kt
private fun performManualSync() {
    val filesToSync = listOf(
        "governance_meetings.json",
        "india_government_schemes.json",
        // ... more files
    )
    
    filesToSync.forEach { filename ->
        val fileUrl = "$baseUrl$filename"
        downloadJsonFile(fileUrl, filename)
    }
}
```

## Troubleshooting

### Issue: Duplicate class SystemPrompts
**Fix:** Delete `SystemPrompt.kt`, keep only `SystemPrompts.kt`

### Issue: OpenCL not found
**Fix:** App auto-falls back to CPU mode

### Issue: Crash on initialization
**Fix:** Check logcat, app now has staged initialization

### Issue: Sync not working
**Fix:** Verify URL ends with `/` and JSON files are accessible

## Testing Checklist

- [ ] App launches without crash
- [ ] GPU mode activates on Redmi Note 10 Pro
- [ ] CPU fallback works on other devices
- [ ] Source citations appear below AI responses
- [ ] Manual sync downloads JSON files
- [ ] Chat history saves to Room DB
- [ ] Voice input works
- [ ] File attachment works

## Performance Expectations

| Device | Backend | Tokens/sec |
|--------|---------|------------|
| Redmi Note 10 Pro | GPU | 15-20 |
| Moto G64 5G | CPU | 8-12 |
| Emulator | CPU | 5-8 |
