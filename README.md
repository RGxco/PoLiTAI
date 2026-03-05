# 🏛️ PoLiTAI
**Senior Administrative Intelligence Terminal**

PoLiTAI is a professional-grade governance assistant and RAG-powered AI tool designed for administrative insights, budget analysis, and policy research. It runs a local LLM (Gemma 2B) to ensure 100% data privacy and offline capability.

---

## 🚀 Getting Started

### 1. Prerequisites
- Android Device (API 26+)
- Processor minimum 732g (testing) would run smooth on 8 gen processors ( works only on snapdragon processors for now ) 
- 6GB+ RAM (8GB recommended for Gemma 2B)

### 2. Installation
1. Download the latest APK from the [Releases](https://github.com/RGxco/PoLiTAI/releases) page.
2. Install the APK on your device.

### 3. Model Setup (Important)
Due to file size limits, the 1.3GB AI model is hosted externally.
1. Download `gemma-2b-it-gpu-int4.bin` from the [Official Google Drive Folder](https://drive.google.com/drive/folders/1s4_55-mwK29ti1I21NVLLoYVtH_uJuuE?usp=sharing).
2. For developers: Place the file in `app/src/main/assets/` before building.
3. For users: The app will look for the model in internal storage on first run.

---

## ✨ Features
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
