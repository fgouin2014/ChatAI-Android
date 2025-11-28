#!/usr/bin/env python3
"""
Script de conversion SpeechT5 PyTorch -> ONNX
Utilise optimum-cli pour exporter le modele en ONNX
"""

import subprocess
import sys
import os
from pathlib import Path

def check_dependencies():
    """Verifier que les dependances sont installees"""
    try:
        import optimum
        import transformers
        import torch
        print("[OK] Dependances installees")
        return True
    except ImportError as e:
        print(f"[ERROR] Dependance manquante: {e}")
        print("[INFO] Installer avec: pip install optimum[onnxruntime] transformers torch")
        return False

def convert_speecht5_to_onnx(output_dir="E:/ChatAI-Models/tts/onnx"):
    """Convertir SpeechT5 PyTorch en ONNX"""
    
    # Creer le repertoire de sortie
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\n=== CONVERSION SpeechT5 -> ONNX ===")
    print(f"Modele source: microsoft/speecht5_tts")
    print(f"Repertoire sortie: {output_dir}\n")
    
    # Commande optimum-cli
    cmd = [
        "optimum-cli",
        "export",
        "onnx",
        "--model", "microsoft/speecht5_tts",
        str(output_path),
        "--model-kwargs", '{"vocoder": "microsoft/speecht5_hifigan"}'
    ]
    
    print(f"[EXEC] {' '.join(cmd)}\n")
    
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
        print("[OK] Conversion reussie!")
        print(result.stdout)
        
        # Lister les fichiers generes
        print(f"\n=== FICHIERS GENERES ===")
        for file in output_path.rglob("*"):
            if file.is_file():
                size_mb = file.stat().st_size / (1024 * 1024)
                print(f"  {file.name}: {size_mb:.2f} MB")
        
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Echec conversion:")
        print(e.stderr)
        return False
    except FileNotFoundError:
        print("[ERROR] optimum-cli non trouve")
        print("[INFO] Installer avec: pip install optimum[onnxruntime]")
        return False

if __name__ == "__main__":
    if not check_dependencies():
        sys.exit(1)
    
    # Repertoire de sortie
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "E:/ChatAI-Models/tts/onnx"
    
    if convert_speecht5_to_onnx(output_dir):
        print("\n[SUCCESS] Modele ONNX pret!")
        print(f"[INFO] Transférer vers device:")
        print(f"  adb push {output_dir}/model.onnx /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx")
    else:
        print("\n[FAILED] Conversion echouee")
        sys.exit(1)


