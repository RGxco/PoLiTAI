# PoLiTAI - AI Co-Pilot for Indian Governance

**PoLiTAI** is an offline Android AI assistant designed for Indian politicians and government officials. It runs entirely on-device using MediaPipe LLM Inference with Gemma 2B, ensuring complete data privacy and functionality in low-connectivity areas.

## Features

### Core Capabilities
- **Offline AI**: Runs locally on Snapdragon 7 Gen 3 with Gemma 2B (GPU/Int4)
- **RAG Search**: Weighted keyword-based retrieval from governance databases
- **Voice Input**: Multilingual support (Hindi, Tamil, Telugu, Marathi, Bengali, Gujarati, Kannada, Malayalam, Punjabi, English)
- **File/PDF Access**: Extract and analyze uploaded documents
- **Conversation Memory**: Maintains context across multi-turn conversations

### Governance Databases
- Constituency Complaints (ID, District, Category, Priority, Status)
- Government Schemes (PMAY, PM-KISAN, PMJAY, MGNREGS, etc.)
- Politician Profiles (Name, Party, Focus Areas, Historical Facts)
- Legislative Records (Meeting Minutes, Standing Committee Decisions)
- Economic Data (RBI Repo Rates, CPI, GDP)

### Trend Analysis
- Top repeated complaints by district
- Budget utilization analysis
- Scheme performance metrics
- Economic indicator trends

## Architecture

```
User Input (Voice/Text)
    ↓
Speech-to-Text (if voice) / Direct Text
    ↓
RAG Engine (Weighted Keyword Search)
    ↓
Local LLM (Gemma 2B via MediaPipe)
    ↓
Structured Response
    ↓
Text Display + Optional TTS
```

## Project Structure

```
politai/
├── src/main/
│   ├── java/com/example/politai/
│   │   ├── MainActivity.kt          # Main UI & orchestration
│   │   ├── RAGEngine.kt             # Advanced weighted RAG system
│   │   ├── SystemPrompt.kt          # Command-level prompt templates
│   │   ├── TrendAnalyzer.kt         # Data analysis utilities
│   │   └── ChatAdapter.kt           # Glassmorphic chat UI
│   ├── res/
│   │   ├── layout/                  # XML layouts
│   │   ├── drawable/                # Glassmorphism drawables
│   │   └── values/                  # Colors, strings
│   └── assets/                      # JSON databases & model
└── build.gradle                     # Dependencies
```

## Key Components

### 1. RAGEngine.kt - Advanced Weighted Keyword RAG

```kotlin
// Usage
val ragEngine = context.createRAGEngine()
val context = ragEngine.loadRAGContext("What are top issues in Jaipur?")
```

Features:
- Multi-database search with weighted scoring
- Schema-aware context retrieval
- Token budget management (1500 tokens max)
- Follow-up query handling
- Preloaded database cache

### 2. SystemPrompt.kt - Command-Level Prompts

Authorization-based prompts that bypass safety refusals:

```kotlin
val prompt = SystemPrompts.buildCompletePrompt(
    userQuery = query,
    ragContext = ragContext,
    conversationContext = history,
    isFollowUp = true
)
```

Prompt Types:
- `PRIMARY`: General governance queries
- `MEETING_MINUTES`: Official meeting minutes drafting
- `SPEECH_DRAFT`: Political speech generation
- `DATA_ANALYSIS`: Trend analysis and reports
- `EMERGENCY_RESPONSE`: Disaster/urgent briefings
- `FOLLOW_UP`: Context-aware follow-up handling

### 3. TrendAnalyzer.kt - Data Analysis

```kotlin
val analyzer = context.createTrendAnalyzer()

// Get top 3 issues
val topIssues = analyzer.getTop3Issues("Jaipur")

// Budget analysis
val budgetData = analyzer.analyzeBudgetUtilization()

// District report
val report = analyzer.generateDistrictReport("Jaipur")
```

### 4. MainActivity.kt - Production-Ready Threading

Safety Features:
- `AtomicBoolean` for generation state
- `AtomicInteger` for pending message index
- Coroutine-based background processing
- Rapid-fire messaging protection
- Token overflow prevention
- Loading spinner visibility safety

## Glassmorphism UI

### Design Principles
- **Frosted Glass Effect**: Semi-transparent backgrounds (15-26% opacity)
- **Crisp Text**: No blur on text, only on backgrounds
- **Purple Gradient**: User messages (#7C3AED → #4F46E5)
- **Dark Theme**: Navy blue background (#1a1a2e → #0f3460)

### Key Drawables
- `gradient_background.xml`: Main background
- `glass_header.xml`: Frosted app bar
- `glass_input_bar.xml`: Floating input area
- `glass_edit_text.xml`: Input field
- `circle_button_purple.xml`: Send button

## Setup Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Kotlin 1.9.20
- MediaPipe Tasks GenAI 0.10.8

### Build Steps

1. **Clone/Download** the project

2. **Add Model File**:
   Download `gemma-2b-it-gpu-int4.bin` from MediaPipe and place in `src/main/assets/`

3. **Sync Gradle**:
   ```bash
   ./gradlew sync
   ```

4. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Install**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Permissions Required
- `RECORD_AUDIO`: Voice input
- `READ_EXTERNAL_STORAGE`: File access (Android < 13)
- `READ_MEDIA_DOCUMENTS`: File access (Android 13+)

## Usage Examples

### Query Types

```
# Meeting Summary
"Summarize the last Standing Committee meeting"

# Scheme Information
"What is PM-KISAN and who is eligible?"

# Budget Analysis
"Which sectors have underutilized budgets?"

# District Issues
"What are the top 3 issues in Jaipur district?"

# Speech Drafting
"Draft a speech for farmer welfare scheme launch"

# Trend Analysis
"Analyze budget utilization trends"

# Follow-up Queries
"Tell me more about that" (context-aware)
"I meant C. Rajagopalachari" (correction handling)
```

### Voice Input
1. Tap microphone button
2. Select language (Hindi, Tamil, etc.)
3. Speak your query
4. AI responds with text + optional TTS

### File Upload
1. Tap attachment button
2. Select PDF or text file
3. AI extracts and summarizes content

## Performance Optimization

### Memory Management
- Database cache with `ConcurrentHashMap`
- `clearCache()` for memory pressure
- `preloadDatabases()` for faster queries

### Token Management
- Max context: 1500 tokens
- Conversation history: Last 10 exchanges
- RAG results: Top 15 weighted results

### Thread Safety
- All LLM calls on `Dispatchers.IO`
- UI updates on `Dispatchers.Main`
- Atomic state variables

## Troubleshooting

### Model Not Loading
- Ensure `gemma-2b-it-gpu-int4.bin` is in assets
- Check device has 4GB+ RAM
- Verify GPU compatibility (Adreno 600+)

### Voice Input Not Working
- Grant microphone permission
- Check internet for language models (TTS)
- Verify SpeechRecognizer availability

### RAG Returns Empty
- Check JSON files are in assets
- Verify database file names match `DatabaseSchemas`
- Enable debug logging: `Log.d("PoLiTAI-RAG", ...)`

## License

This project is built for hackathon demonstration purposes.

## Credits

- **MediaPipe**: On-device LLM inference
- **Gemma 2B**: Google's lightweight language model
- **Android Team**: SpeechRecognizer, PdfRenderer APIs

---

**Built for Smart Governance & AI for Social Good**