#!/usr/bin/env python3
"""
PoLiTAI - Gemma 2B Fine-Tuning Script for RTX 5060 (8GB VRAM)

⚠️  IMPORTANT FEASIBILITY NOTE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RTX 5060 has 8GB VRAM. Fine-tuning Gemma 2B with QLoRA is POSSIBLE but CHALLENGING:

✅ WHAT WORKS:
   • QLoRA 4-bit quantization (bitsandbytes)
   • Gradient checkpointing
   • Batch size = 1 (mandatory)
   • Max sequence length = 512-1024 tokens
   • LoRA rank = 8-16 (lower = less VRAM)

❌ WHAT WON'T WORK:
   • Full fine-tuning (needs 16GB+)
   • Batch size > 1
   • Sequence length > 1024
   • LoRA rank > 32

📊 EXPECTED RESULTS:
   • Training time: ~2-4 hours for 1000 examples
   • VRAM usage: ~6.5-7.5GB (leaving headroom)
   • Quality improvement: Moderate (2B model has limits)

💡 RECOMMENDATION:
   For hackathon/demo purposes, the base Gemma 2B + good RAG is often sufficient.
   Fine-tuning helps with style consistency but won't fix fundamental knowledge gaps.

Usage:
    # 1. Install dependencies:
    pip install torch transformers peft bitsandbytes accelerate datasets
    
    # 2. Run training:
    python train_gemma_rtx5060.py --dataset ./politai_training.jsonl --output ./politai_finetuned
    
    # 3. Convert to MediaPipe format (see conversion script)
"""

import os
import json
import torch
import argparse
from pathlib import Path
from datasets import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    DataCollatorForLanguageModeling,
    BitsAndBytesConfig
)
from peft import (
    LoraConfig,
    get_peft_model,
    prepare_model_for_kbit_training,
    TaskType
)
from trl import SFTTrainer

# Suppress warnings
import warnings
warnings.filterwarnings('ignore')


def load_jsonl_dataset(filepath: str) -> Dataset:
    """Load JSONL dataset and convert to HuggingFace Dataset"""
    data = []
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            data.append(json.loads(line))
    
    # Format for instruction tuning
    formatted_data = []
    for item in data:
        # Create the full prompt
        text = f"""<|system|>
{item['system']}

<|user|>
{item['instruction']}

Context: {item['context'][:500]}...

<|assistant|>
{item['response']}"""
        
        formatted_data.append({"text": text})
    
    return Dataset.from_list(formatted_data)


def setup_model_for_rtx5060(model_name: str = "google/gemma-2b-it"):
    """
    Setup Gemma 2B with 4-bit quantization for RTX 5060 (8GB VRAM)
    """
    print("🚀 Loading Gemma 2B with 4-bit quantization...")
    
    # 4-bit quantization config - CRITICAL for 8GB VRAM
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_use_double_quant=True,  # Nested quantization for more savings
        bnb_4bit_quant_type="nf4",       # Normal Float 4 (better than FP4)
        bnb_4bit_compute_dtype=torch.bfloat16,
    )
    
    # Load model
    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        quantization_config=bnb_config,
        device_map="auto",  # Automatically distribute layers
        trust_remote_code=True,
        torch_dtype=torch.bfloat16,
    )
    
    # Load tokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"
    
    print(f"✅ Model loaded. VRAM usage: ~{torch.cuda.memory_allocated() / 1024**3:.2f} GB")
    
    return model, tokenizer


def setup_lora_config():
    """
    LoRA configuration optimized for RTX 5060
    Lower rank = less VRAM but also less capacity to learn
    """
    lora_config = LoraConfig(
        r=8,                    # LoRA rank (8-16 is safe for 8GB)
        lora_alpha=16,          # Scaling factor (usually 2x rank)
        target_modules=[
            "q_proj",           # Query projection
            "k_proj",           # Key projection
            "v_proj",           # Value projection
            "o_proj",           # Output projection
            "gate_proj",        # Gate projection (MLP)
            "up_proj",          # Up projection (MLP)
            "down_proj",        # Down projection (MLP)
        ],
        lora_dropout=0.05,      # Dropout for regularization
        bias="none",
        task_type=TaskType.CAUSAL_LM,
    )
    
    return lora_config


def train_model(model, tokenizer, dataset, output_dir: str, num_epochs: int = 3):
    """
    Fine-tune the model with QLoRA
    """
    print("🔧 Preparing model for training...")
    
    # Prepare model for k-bit training
    model = prepare_model_for_kbit_training(model)
    
    # Apply LoRA
    lora_config = setup_lora_config()
    model = get_peft_model(model, lora_config)
    
    # Print trainable parameters
    model.print_trainable_parameters()
    
    # Training arguments - OPTIMIZED FOR RTX 5060
    training_args = TrainingArguments(
        output_dir=output_dir,
        num_train_epochs=num_epochs,
        per_device_train_batch_size=1,      # MUST be 1 for 8GB VRAM
        gradient_accumulation_steps=4,       # Effective batch size = 4
        optim="paged_adamw_8bit",           # 8-bit optimizer (saves VRAM)
        save_steps=100,
        logging_steps=10,
        learning_rate=2e-4,                  # Standard LoRA LR
        weight_decay=0.001,
        fp16=False,                          # Use bf16 instead
        bf16=True,                           # bfloat16 is more stable
        max_grad_norm=0.3,
        max_steps=-1,
        warmup_ratio=0.03,
        group_by_length=True,                # Efficiency optimization
        lr_scheduler_type="cosine",
        report_to="none",                    # Disable wandb/tensorboard
        gradient_checkpointing=True,         # CRITICAL: Saves VRAM
    )
    
    # Data collator
    data_collator = DataCollatorForLanguageModeling(
        tokenizer=tokenizer,
        mlm=False,  # We're doing causal LM, not masked LM
    )
    
    print("🏋️ Starting training...")
    print(f"   Epochs: {num_epochs}")
    print(f"   Batch size: 1 (effective: 4 with grad accum)")
    print(f"   Dataset size: {len(dataset)} examples")
    
    # Initialize trainer
    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        args=training_args,
        data_collator=data_collator,
        max_seq_length=512,  # CRITICAL: Keep this low for 8GB VRAM
    )
    
    # Train
    trainer.train()
    
    # Save model
    print(f"💾 Saving fine-tuned model to {output_dir}")
    trainer.save_model(output_dir)
    
    return model


def merge_and_save(model, output_dir: str):
    """
    Merge LoRA weights with base model for deployment
    """
    print("🔀 Merging LoRA weights with base model...")
    
    # Merge adapters
    merged_model = model.merge_and_unload()
    
    # Save merged model
    merged_path = Path(output_dir) / "merged"
    merged_model.save_pretrained(merged_path)
    
    print(f"✅ Merged model saved to {merged_path}")
    
    return merged_model


def main():
    parser = argparse.ArgumentParser(
        description='Fine-tune Gemma 2B on RTX 5060 (8GB VRAM)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Basic training (3 epochs)
  python train_gemma_rtx5060.py -d ./politai_training.jsonl -o ./output
  
  # More epochs for better quality
  python train_gemma_rtx5060.py -d ./politai_training.jsonl -o ./output --epochs 5
  
  # Skip merging (faster, but need to load adapters separately)
  python train_gemma_rtx5060.py -d ./politai_training.jsonl -o ./output --no-merge
        """
    )
    parser.add_argument('--dataset', '-d', required=True, help='Path to JSONL training dataset')
    parser.add_argument('--output', '-o', default='./politai_finetuned', help='Output directory')
    parser.add_argument('--epochs', '-e', type=int, default=3, help='Number of training epochs')
    parser.add_argument('--model', '-m', default='google/gemma-2b-it', help='Base model name')
    parser.add_argument('--no-merge', action='store_true', help='Skip merging LoRA weights')
    parser.add_argument('--check-vram', action='store_true', help='Check VRAM and exit')
    
    args = parser.parse_args()
    
    # Check CUDA availability
    if not torch.cuda.is_available():
        print("❌ CUDA not available! This script requires an NVIDIA GPU.")
        return
    
    gpu_name = torch.cuda.get_device_name(0)
    vram_gb = torch.cuda.get_device_properties(0).total_memory / 1024**3
    
    print(f"🖥️ GPU: {gpu_name}")
    print(f"💾 Total VRAM: {vram_gb:.2f} GB")
    
    if args.check_vram:
        if vram_gb < 7.5:
            print("⚠️ WARNING: Less than 8GB VRAM detected. Training may fail.")
        else:
            print("✅ VRAM looks sufficient for QLoRA training.")
        return
    
    if vram_gb < 7.5:
        print("\n⚠️ WARNING: Your GPU has less than 8GB VRAM.")
        print("   Training may crash. Consider:")
        print("   • Reducing max_seq_length to 256")
        print("   • Using LoRA rank r=4")
        print("   • Closing all other applications")
        response = input("\nContinue anyway? (y/n): ")
        if response.lower() != 'y':
            return
    
    # Load dataset
    print(f"\n📚 Loading dataset from {args.dataset}...")
    dataset = load_jsonl_dataset(args.dataset)
    print(f"✅ Loaded {len(dataset)} training examples")
    
    # Setup model
    model, tokenizer = setup_model_for_rtx5060(args.model)
    
    # Train
    model = train_model(model, tokenizer, dataset, args.output, args.epochs)
    
    # Merge if requested
    if not args.no_merge:
        merge_and_save(model, args.output)
    
    print("\n🎉 Training complete!")
    print(f"   Model saved to: {args.output}")
    print("\nNext steps:")
    print("   1. Test the model with inference")
    print("   2. Convert to MediaPipe format using convert_to_mediapipe.py")


if __name__ == '__main__':
    main()