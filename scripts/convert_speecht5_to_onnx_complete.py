#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script COMPLET de conversion SpeechT5 PyTorch -> ONNX
Inclut tokenizer et speaker embeddings
"""

import subprocess
import sys
import os
from pathlib import Path
import json

# Fix encodage Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

def check_dependencies():
    """Verifier que les dependances sont installees"""
    required = {
        'optimum': 'optimum[onnxruntime]',
        'transformers': 'transformers',
        'torch': 'torch',
        'onnxruntime': 'onnxruntime',
        'sentencepiece': 'sentencepiece'  # ⭐ REQUIS pour SpeechT5Tokenizer
    }
    
    missing = []
    for module, package in required.items():
        try:
            __import__(module)
            print(f"[OK] {module} installe")
        except ImportError:
            print(f"[MISSING] {module} manquant")
            missing.append(package)
    
    if missing:
        print(f"\n[INFO] Installer avec: pip install {' '.join(missing)}")
        return False
    return True

def convert_speecht5_to_onnx(output_dir="E:/ChatAI-Models/tts/onnx"):
    """Convertir SpeechT5 PyTorch en ONNX avec tokenizer"""
    
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\n=== CONVERSION SpeechT5 -> ONNX ===")
    print(f"Modele source: microsoft/speecht5_tts")
    print(f"Repertoire sortie: {output_dir}\n")
    
    try:
        from transformers import SpeechT5Processor, SpeechT5ForTextToSpeech
        import torch
        
        print("[1/4] Telechargement modele et processor...")
        processor = SpeechT5Processor.from_pretrained("microsoft/speecht5_tts")
        model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
        model.eval()
        
        print("[2/4] Sauvegarde tokenizer et config...")
        # Sauvegarder le tokenizer pour utilisation Android
        processor.save_pretrained(str(output_path / "tokenizer"))
        
        # Sauvegarder la config du vocabulaire
        vocab = processor.tokenizer.get_vocab()
        vocab_path = output_path / "vocab.json"
        with open(vocab_path, 'w', encoding='utf-8') as f:
            json.dump(vocab, f, ensure_ascii=False, indent=2)
        print(f"  Vocabulaire sauvegarde: {vocab_path} ({len(vocab)} tokens)")
        
        print("[3/4] Export ONNX...")
        # Exporter le modele en ONNX
        dummy_input_ids = torch.randint(0, len(vocab), (1, 10))  # Exemple: 10 tokens
        dummy_speaker_embeddings = torch.randn(1, 512)  # Speaker embeddings
        
        onnx_path = output_path / "model.onnx"
        
        torch.onnx.export(
            model,
            (dummy_input_ids, dummy_speaker_embeddings),
            str(onnx_path),
            input_names=["input_ids", "speaker_embeddings"],
            output_names=["waveform"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "sequence"},
                "speaker_embeddings": {0: "batch"},
                "waveform": {0: "batch", 1: "time"}
            },
            opset_version=14,
            do_constant_folding=True
        )
        
        print(f"[OK] Modele ONNX exporte: {onnx_path}")
        
        print("[4/4] Generation speaker embeddings par defaut...")
        # Generer un speaker embedding par defaut (vecteur de 512 floats)
        default_speaker = model.speaker_embeddings(torch.tensor([0]))  # Speaker ID 0
        speaker_path = output_path / "default_speaker_embeddings.json"
        speaker_data = {
            "embeddings": default_speaker.squeeze().tolist(),
            "dimensions": 512,
            "description": "Speaker embeddings par defaut (microsoft/speecht5_tts)"
        }
        with open(speaker_path, 'w', encoding='utf-8') as f:
            json.dump(speaker_data, f, indent=2, ensure_ascii=False)
        print(f"  Speaker embeddings sauvegarde: {speaker_path}")
        
        # Lister les fichiers generes
        print(f"\n=== FICHIERS GENERES ===")
        total_size = 0
        for file in output_path.rglob("*"):
            if file.is_file():
                size_mb = file.stat().st_size / (1024 * 1024)
                total_size += size_mb
                print(f"  {file.relative_to(output_path)}: {size_mb:.2f} MB")
        
        print(f"\n[SUCCESS] Conversion complete!")
        print(f"  Total: {total_size:.2f} MB")
        print(f"\n[INFO] Fichiers pour Android:")
        print(f"  - model.onnx -> /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx")
        print(f"  - vocab.json -> /storage/emulated/0/ChatAI-Files/models/tts/vocab.json")
        print(f"  - default_speaker_embeddings.json -> /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json")
        
        return True
        
    except Exception as e:
        print(f"[ERROR] Erreur conversion: {e}")
        import traceback
        try:
            traceback.print_exc()
        except UnicodeEncodeError:
            # Fallback si traceback contient des caracteres non-ASCII
            print("  (Details d'erreur non affichables - probleme encodage)")
        return False

if __name__ == "__main__":
    if not check_dependencies():
        sys.exit(1)
    
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "E:/ChatAI-Models/tts/onnx"
    
    if convert_speecht5_to_onnx(output_dir):
        print("\n[SUCCESS] Conversion reussie!")
        print("\n[PROCHAINES ETAPES]")
        print("1. Transférer model.onnx vers device")
        print("2. Transférer vocab.json vers device")
        print("3. Transférer default_speaker_embeddings.json vers device")
        print("4. Implémenter tokenizer dans OnnxTTSManager.kt")
    else:
        print("\n[FAILED] Conversion echouee")
        sys.exit(1)

