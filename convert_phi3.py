import os
import subprocess
import sys

# Ensure required packages are installed
try:
    import huggingface_hub
    import mediapipe as mp
except ImportError:
    print("Installing required packages (mediapipe, huggingface_hub)...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "mediapipe", "huggingface_hub"])
    import huggingface_hub
    import mediapipe as mp

from mediapipe.tasks.python.genai import converter

# Settings
MODEL_ID = "microsoft/Phi-3-mini-4k-instruct"
DOWNLOAD_DIR = "phi3_raw_weights"
OUTPUT_FILE = os.path.abspath("phi3-mini-int4.task")

print(f"--- Step 1: Downloading {MODEL_ID} from HuggingFace ---")
print(f"This is a ~7GB download and may take a while depending on your internet speed.")
os.makedirs(DOWNLOAD_DIR, exist_ok=True)

# Download the model files
huggingface_hub.snapshot_download(
    repo_id=MODEL_ID,
    local_dir=DOWNLOAD_DIR,
    ignore_patterns=["*.msgpack", "*.h5", "coreml/*", "onnx/*"] 
)

print(f"\n--- Step 2: Converting to MediaPipe .task format (int4 quantization) ---")
print("This will compress the 7GB model into a ~2GB file optimized for your phone.")
print("This step requires significant RAM on your PC and may take 5-15 minutes...")

config = converter.ConversionConfig(
    input_ckpt=DOWNLOAD_DIR,
    ckpt_format="safetensors",
    model_type="PHI_2",
    backend="cpu",
    output_dir=".",
    combine_file_only=True,
    vocab_model_file=os.path.join(DOWNLOAD_DIR, "tokenizer.model"),
    output_tflite_file=OUTPUT_FILE,
)

converter.convert_checkpoint(config)

print(f"\n✅ SUCCESS! Conversion complete.")
print(f"Your mobile-ready file is here: {os.path.abspath(OUTPUT_FILE)}")
print("You can now push this file to your Android device via ADB.")
