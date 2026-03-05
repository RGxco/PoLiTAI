# PoLiTAI v2.0 - Master Grade Edition

**AI Co-Pilot for Indian Governance**  
*Developed by Rishit Rohan*

---

## 5 Pillars Delivered

### Pillar 1: Model Fine-Tuning (The Brain)

**⚠️ Feasibility Assessment for RTX 5060 (8GB VRAM):**

| Aspect | Status | Notes |
|--------|--------|-------|
| QLoRA 4-bit Training | ✅ Possible | Batch size = 1 mandatory |
| Sequence Length | ⚠️ Limited | Max 512-1024 tokens |
| LoRA Rank | ⚠️ Limited | r=8-16 recommended |
| Training Time | ⏱️ 2-4 hours | For 1000 examples |
| **Recommendation** | 💡 Skip for hackathon | Base model + RAG is sufficient |

**Files Provided:**
- `training/json_to_jsonl_converter.py` - Converts JSON databases to training format
- `training/train_gemma_rtx5060.py` - Training script optimized for 8GB VRAM
- `training/convert_to_mediapipe.py` - Explains conversion limitations

**Key Finding:** Fine-tuning Gemma 2B provides marginal gains. Your excellent RAG system + sophisticated prompting will give better results for hackathon/demo purposes.

---

### Pillar 2: Database Expansion (The Knowledge)

**Python Scraper for Indian Government Sources:**

```bash
# Generate 500-1000 mock databases for testing
python scraper/government_data_scraper.py --output ./databases --count 500 --mock

# Generated databases:
# - politician_database.json (100+ politicians)
# - government_schemes.json (50+ schemes)
# - governance_meetings.json (50+ meetings)
# - constituency_complaints.json (100+ complaints)
# - india_major_bills.json (30+ bills)
```

**Live Scraping Support (when APIs available):**
- PIB (Press Information Bureau)
- PRS India (Legislative Research)
- Union Budget Portal
- Election Commission
- RBI Data
- Data.gov.in

---

### Pillar 3: Advanced Chat History (The UX)

**Navigation Drawer with Hamburger Menu:**

```kotlin
// Features implemented:
✅ Hamburger menu button opens Navigation Drawer
✅ Chat history organized by DATE (Today, Yesterday, This Week, etc.)
✅ Chat history organized by TOPIC (Politics, Schemes, Budget, etc.)
✅ Search chat history
✅ Pin important chats
✅ Edit chat titles
✅ New Chat button
```

**Database Schema (Room):**
```kotlin
@Entity(tableName = "chat_sessions")
data class ChatSession(
    val id: Long,
    val title: String,
    val topic: String,  // Auto-detected
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val isPinned: Boolean
)
```

---

### Pillar 4: Internet Sync & Auto-Update (The Ecosystem)

**Settings Screen with Sync Controls:**

```kotlin
// Features implemented:
✅ "Internet Sync" toggle in Settings
✅ Progress bar showing download/update status
✅ Update log showing what files were updated
✅ Manual "Sync Now" button
✅ Configurable sync frequency (1h, 6h, 12h, 24h)
✅ Background sync using WorkManager
```

**Sync Flow:**
1. App checks remote manifest.json
2. Compares local vs remote file versions
3. Downloads only changed files
4. Saves to internal storage
5. Shows update notification
6. Updates sync log in Settings

**Remote URL Format:**
```
https://raw.githubusercontent.com/yourusername/politai-data/main/
├── manifest.json
├── politician_database.json
├── government_schemes.json
└── ... (other databases)
```

---

### Pillar 5: Branding & Animation (The Identity)

**Splash Screen Features:**

```kotlin
// Animation sequence:
1. Logo fades in + scales up (0-800ms)
2. App name slides up + fades in (400-1000ms)
3. Tagline fades in (700-1200ms)
4. "Developed by Rishit Rohan" watermark fades in (1000-1500ms)
5. Loading indicator with progress bar (1200ms+)
6. Logo pulsing animation during loading
7. Fade out + transition to MainActivity
```

**Watermark:** "Developed by Rishit Rohan" displayed at bottom of splash screen.

---

## Bug Fixes Implemented

### PDF Reading Fix
```kotlin
// BEFORE: PDF auto-sent without message
sendMessage("Summarize this: $pdfText")

// AFTER: User can type message with PDF attached
// Shows attachment preview with filename
// User can remove attachment
// Full query includes both message + PDF content
```

### Multilanguage Fix
```kotlin
// Added language instruction to prompt for non-English
val languageInstruction = if (currentLanguage != "en-IN") {
    "\n\nIMPORTANT: Respond in ${supportedLanguages[currentLanguage]?.first} language."
} else ""

// TTS language updates with selected language
textToSpeech?.language = supportedLanguages[currentLanguage]?.second
```

---

## File Structure

```
politai_v2/
├── training/
│   ├── json_to_jsonl_converter.py    # Dataset converter
│   ├── train_gemma_rtx5060.py        # Training script
│   └── convert_to_mediapipe.py       # Conversion utility
│
├── scraper/
│   └── government_data_scraper.py    # Data scraper
│
└── android/
    ├── java/com/example/politai/
    │   ├── SplashActivity.kt          # Splash screen
    │   ├── MainActivity.kt            # Main with drawer
    │   ├── SettingsActivity.kt        # Settings with sync
    │   ├── ChatSession.kt             # Room entities
    │   ├── ChatHistoryManager.kt      # History management
    │   ├── DatabaseSyncManager.kt     # Sync logic
    │   ├── ChatAdapter.kt             # Chat UI
    │   ├── RAGEngine.kt               # RAG system
    │   ├── SystemPrompt.kt            # Prompts
    │   └── TrendAnalyzer.kt           # Analytics
    │
    ├── res/
    │   ├── layout/
    │   │   ├── activity_splash.xml
    │   │   ├── activity_main_drawer.xml
    │   │   ├── activity_settings.xml
    │   │   ├── nav_header.xml
    │   │   └── item_chat_message.xml
    │   │
    │   ├── menu/
    │   │   └── nav_drawer_menu.xml
    │   │
    │   ├── drawable/
    │   │   ├── ic_politai_logo.xml
    │   │   ├── ic_menu.xml
    │   │   ├── ic_chat_new.xml
    │   │   ├── ic_calendar.xml
    │   │   ├── ic_topic.xml
    │   │   ├── ic_search.xml
    │   │   ├── ic_settings.xml
    │   │   ├── ic_help.xml
    │   │   ├── ic_info.xml
    │   │   ├── ic_close.xml
    │   │   ├── glass_card.xml
    │   │   ├── glass_drawer_background.xml
    │   │   ├── button_primary.xml
    │   │   ├── button_secondary.xml
    │   │   ├── button_danger.xml
    │   │   ├── progress_gradient.xml
    │   │   ├── badge_background.xml
    │   │   ├── glow_circle.xml
    │   │   └── nav_item_background.xml
    │   │
    │   └── values/
    │       ├── arrays.xml
    │       └── strings.xml
    │
    └── AndroidManifest.xml
```

---

## Setup Instructions

### 1. Add Dependencies (build.gradle)

```gradle
dependencies {
    // Core
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Navigation Drawer
    implementation 'androidx.drawerlayout:drawerlayout:1.2.0'
    
    // Room Database
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    
    // WorkManager (Background Sync)
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    
    // MediaPipe
    implementation 'com.google.mediapipe:tasks-genai:0.10.8'
    
    // Gson
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 2. Add Model File

Download `gemma-2b-it-gpu-int4.bin` from MediaPipe and place in:
```
app/src/main/assets/gemma-2b-it-gpu-int4.bin
```

### 3. Copy JSON Databases

Place your 26+ JSON files in:
```
app/src/main/assets/
├── politician_database.json
├── government_schemes.json
├── governance_meetings.json
└── ... (all databases)
```

### 4. Update AndroidManifest.xml

Add SplashActivity as LAUNCHER, MainActivity as standard.

---

## Usage

### Navigation Drawer
- Tap ☰ hamburger button to open drawer
- View chat history by Date or Topic
- Search previous conversations
- Start new chat
- Access Settings

### File Attachment
- Tap 📎 attach button
- Select PDF or text file
- File appears as attachment preview
- Type your question about the file
- Tap send

### Voice Input
- Tap 🎙️ mic button
- Select language (10 Indian languages)
- Speak your query
- AI responds in same language

### Settings Sync
- Go to Settings → Internet Sync
- Enable "Auto Sync"
- Set sync frequency
- Tap "Sync Now" for manual sync
- View update log for recent changes

---

## Key Features Summary

| Feature | Status |
|---------|--------|
| Weighted RAG Search | ✅ |
| Navigation Drawer | ✅ |
| Chat History (Date/Topic) | ✅ |
| File Attachment with Preview | ✅ |
| Voice Input (10 Languages) | ✅ |
| Internet Sync | ✅ |
| Background Auto-Update | ✅ |
| Splash Screen Animation | ✅ |
| "Developed by Rishit Rohan" | ✅ |
| Settings Screen | ✅ |
| Multilanguage AI Responses | ✅ |

---

## Next Steps

1. **Test the app** on your device
2. **Add your 26+ JSON databases** to assets
3. **Customize the sync URL** in Settings
4. **Train the model** (optional - see feasibility note)
5. **Deploy for hackathon!**

---

**PoLiTAI v2.0 - Master Grade Edition**  
*AI Co-Pilot for Indian Governance*  
**Developed by Rishit Rohan**