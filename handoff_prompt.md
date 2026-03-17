# Handoff Report: PoLiTAI Project

## 🎯 Project Overview
**PoLiTAI** is a high-performance, offline-first Android application designed as a governance assistant for India. It uses local LLMs and a massive vector-less RAG (Retrieval-Augmented Generation) system to provide factual answers about Indian laws, politicians, schemes, and news without requiring an internet connection.

## 🛠️ Tech Stack
- **AI Core:** MediaPipe Tasks GenAI (LiteRT).
- **Current Model:** Gemma 2B IT (Int4 Quantized), ~2.9GB.
- **RAG Engine:** Custom keyword-based router + weighted scoring system against 55+ JSON datasets.
- **Database:** Room (SQLite) for chat history.
- **UI:** Material Design 3 with custom "Glassmorphism" aesthetics and specialized Chat Adapters.
- **Regional Support:** Real-time STT (Voice) and TTS for English, Hindi, Tamil, Telugu, and Marathi.

## 🚀 What We've Accomplished
1.  **Large Model Bundling:** Solved the "4GB APK limit" and "AAPT2 Memory" issues by:
    - Slicing the 2.9GB model into **30 x 100MB chunks** (`gemma_3n_2b.part1`...`30`).
    - Disabling compression for these parts in `build.gradle` to speed up builds.
    - Implementing a robust `assembleModelChunks()` method in `MainActivity.kt` that glues these pieces back into internal storage on first launch.
2.  **Advanced RAG Logic:**
    - Implemented a "multi-priority" routing system in `RAGEngine.kt`.
    - Created 55+ distinct datasets covering everything from parliamentary bills to RBI Repo rates.
    - Added "Response Quality" selectors (Quick, Normal, Deep) which dynamically adjust RAG context window size and LLM token limits.
3.  **Governance Features:**
    - **OCR/PDF Support:** Users can attach documents; the app extracts text for context.
    - **Live Feedback:** A "Send Feedback" feature that serializes chat history and automatically opens a GitHub Issue on the developer's repo.
    - **Crash Protection:** Uncaught exceptions are logged to `crash_log.txt` and attached to the next feedback report.

## ⚠️ Critical Architecture Notes (Read Before Touching)
- **Model Storage:** The AI model **MUST** be stored in `context.filesDir` (Internal Storage) to avoid EACCES (Permission Denied) errors on Android 11+. Stay away from `getExternalFilesDir()`.
- **Packaging:** The `noCompress` rule in `app/build.gradle` is vital. If you add more parts, update the `noCompress` list or the build will fail with "Java Heap Space".
- **RAG Thresholds:** We use a `similarityScore` threshold of **1.5** in `RAGEngine.kt`. Lowering this leads to more hallucinations; raising it makes the AI too silent.

## 📋 Pending Roadmap
1.  **Verification:** Confirm the 2B model assembly completes successfully on a mid-range physical device (it might take ~45 seconds).
2.  **Performance Tuning:** Optimize the JSON parsing in `RAGEngine.kt`. Currently, it reads the whole JSON file for every query; switching to a local indexed DB (like Room FTS) would be faster for 100+ datasets.
3.  **UI Refinement:** The quality chips are functional but could use smoother micro-animations.

---

## 🤖 Proposed "Agent Resume" Prompt
> "I am working on **PoLiTAI**, an offline Android AI assistant. The project is currently at Phase 16. We just successfully transitioned from a 4B model to a 2B model to fit under the 4GB APK limit. 
> 
> **Your Task:** Resume verification of the model unpacking logic.
> 1. Check `MainActivity.kt`'s `loadModel()` and `assembleModelChunks()` methods.
> 2. Ensure `app/build.gradle` is not compressing the `.partX` files.
> 3. Verify that the RAG routing in `RAGEngine.kt` correctly maps to the 55+ JSON files in `assets/`.
> 4. Keep an eye on `statusText` UI updates to ensure the user sees 'Unpacking: X/30' progress.
> 
> Please analyze the current `task.md` and `implementation_plan.md` in the brain folder to understand the specific data patches we added for Indian governance (e.g., Manoj Sinha, BNS laws, GDP quarterly data)."
