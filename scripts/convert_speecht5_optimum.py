#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script de conversion SpeechT5 -> ONNX via optimum-cli
Methode recommandee pour les modeles Hugging Face
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
        'sentencepiece': 'sentencepiece'
    }
    
    missing = []
    for module, package in required.items():
        try:
            __import__(module)
            print(f"[OK] {module} installe")
        except ImportError:
            print(f"[MISSING] {module} manquant")
            missing.append(package)
        except Exception:
            print(f"[MISSING] {module} manquant")
            missing.append(package)
    
    if missing:
        print(f"\n[INFO] Installer avec: pip install {' '.join(missing)}")
        return False
    return True

def convert_with_optimum_cli(output_dir="E:/ChatAI-Models/tts/onnx"):
    """Convertir SpeechT5 via optimum-cli (methode recommandee)"""
    
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\n=== CONVERSION SpeechT5 -> ONNX (optimum-cli) ===")
    print(f"Modele source: microsoft/speecht5_tts")
    print(f"Repertoire sortie: {output_dir}\n")
    
    # Commande optimum-cli (methode recommandee)
    cmd = [
        "optimum-cli",
        "export",
        "onnx",
        "--model", "microsoft/speecht5_tts",
        str(output_path),
        "--task", "text-to-speech"
    ]
    
    print(f"[EXEC] {' '.join(cmd)}\n")
    
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True, encoding='utf-8', errors='replace')
        print("[OK] Conversion reussie!")
        if result.stdout:
            print(result.stdout)
        
        # Generer vocabulaire et speaker embeddings depuis le tokenizer
        print("\n[EXTRA] Generation vocabulaire et embeddings...")
        try:
            from transformers import SpeechT5Processor, SpeechT5ForTextToSpeech
            import torch
            
            processor = SpeechT5Processor.from_pretrained("microsoft/speecht5_tts")
            model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
            
            # Sauvegarder vocabulaire
            vocab = processor.tokenizer.get_vocab()
            vocab_path = output_path / "vocab.json"
            with open(vocab_path, 'w', encoding='utf-8') as f:
                json.dump(vocab, f, ensure_ascii=False, indent=2)
            print(f"  Vocabulaire sauvegarde: {vocab_path} ({len(vocab)} tokens)")
            
            # Generer speaker embeddings par defaut
            default_speaker = model.speaker_embeddings(torch.tensor([0]))
            speaker_path = output_path / "default_speaker_embeddings.json"
            speaker_data = {
                "embeddings": default_speaker.squeeze().tolist(),
                "dimensions": 512,
                "description": "Speaker embeddings par defaut (microsoft/speecht5_tts)"
            }
            with open(speaker_path, 'w', encoding='utf-8') as f:
                json.dump(speaker_data, f, indent=2, ensure_ascii=False)
            print(f"  Speaker embeddings sauvegarde: {speaker_path}")
            
        except Exception as e:
            print(f"  [WARNING] Impossible de generer vocab/embeddings: {e}")
            print("  [INFO] Vous pouvez les generer manuellement plus tard")
        
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
        
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Echec conversion:")
        if e.stderr:
            print(e.stderr)
        if e.stdout:
            print(e.stdout)
        return False
    except FileNotFoundError:
        print("[ERROR] optimum-cli non trouve")
        print("[INFO] Installer avec: pip install optimum[onnxruntime]")
        return False
    except Exception as e:
        print(f"[ERROR] Erreur inattendue: {e}")
        import traceback
        try:
            traceback.print_exc()
        except UnicodeEncodeError:
            print("  (Details d'erreur non affichables - probleme encodage)")
        return False

if __name__ == "__main__":
    if not check_dependencies():
        sys.exit(1)
    
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "E:/ChatAI-Models/tts/onnx"
    
    if convert_with_optimum_cli(output_dir):
        print("\n[SUCCESS] Conversion reussie!")
        print("\n[PROCHAINES ETAPES]")
        print("1. Transférer model.onnx vers device")
        print("2. Transférer vocab.json vers device")
        print("3. Transférer default_speaker_embeddings.json vers device")
    else:
        print("\n[FAILED] Conversion echouee")
        sys.exit(1)


