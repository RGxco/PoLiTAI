# 🏛️ PoLiTAI
**Senior Administrative Intelligence Terminal**

PoLiTAI is a professional-grade governance assistant and RAG-powered AI tool designed for administrative insights, budget analysis, and policy research. It runs a local LLM (Gemma 2B) to ensure 100% data privacy and offline capability.

---

## 🚀 Getting Started

### 1. Prerequisites
- Android Device (API 26+)
- Processor minimum 732g (testing) would run smooth on 8 gen processors ( works only on snapdragon & exynos processors for now ) 
- 6GB+ RAM (8GB recommended for Gemma 2B)

### 2. Installation
1. Download the latest APK from the [Releases](https://github.com/RGxco/PoLiTAI/releases) page.
2. Install the APK on your device.


## ✨ Features
### 🔥 New in Version 3.0
- **Massive Database Expansion**: Added over 55+ new local JSON datasets covering historical elections, bills, budgets, demographics, and supreme court rulings.
- **Response Quality Matrix**: Added Quick ⚡, Normal 📝, and Deep 📊 chip selectors. Control whether Gemma answers in 1 sentence or a comprehensive 3-paragraph analysis.
- **Scrollable Sync Previews**: Web URL syncing now aggressively strips HTML clutter, guarantees minimum text density, and provides a scrollable 5000-character preview of the indexed text.
- **Smart Chat Management**: Added long-press imitation via dialogue boxes in the Navigation Drawer to easily delete individual chat histories from the Room database.
- **Strict Anti-Hallucination Guardrails**: Gemma is now strictly prohibited from mixing Hindi, English, and Chinese characters. It adheres exclusively to the user's input language and never falls back to generative refusal phrases.

### Core Features
- **Retrieval-Augmented Generation (RAG)**: Queries real-time JSON databases including budget allocations, government schemes, and citizen grievances.
- **Multilingual Core**: Support for English, Hindi, Tamil, Telugu, and Marathi.
- **Master-Grade Logic**: Optimized system prompts to provide direct, factual data without conversational filler.
- **Privacy First**: No internet required for AI inference.

## 🛠️ Tech Stack
- **Language**: Kotlin
- **AI Engine**: Google MediaPipe LLM Inference API
- **Model**: Google Gemma 2B IT (Quantized)
- **Database**: JSON-based RAG architecture

## ⚖️ License
This project is for educational and administrative research purposes.
