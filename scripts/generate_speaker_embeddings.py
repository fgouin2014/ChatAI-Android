#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script pour générer default_speaker_embeddings.json
Utilise le modèle SpeechT5 pour générer les embeddings par défaut
"""

import sys
import json
from pathlib import Path

# Fix encodage Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

def check_dependencies():
    """Vérifier que les dépendances sont installées"""
    try:
        import torch
        from transformers import SpeechT5ForTextToSpeech
        print("[OK] torch installé")
        print("[OK] transformers installé")
        return True
    except ImportError as e:
        print(f"[MISSING] Dépendance manquante: {e}")
        print("[INFO] Installer avec: pip install torch transformers")
        return False

def generate_speaker_embeddings(output_path="E:/ChatAI-Models/tts/default_speaker_embeddings.json"):
    """Générer default_speaker_embeddings.json"""
    
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    print(f"\n=== GÉNÉRATION SPEAKER EMBEDDINGS ===")
    print(f"Modèle source: microsoft/speecht5_tts")
    print(f"Fichier sortie: {output_path}\n")
    
    try:
        from transformers import SpeechT5ForTextToSpeech
        import torch
        
        print("[1/2] Téléchargement modèle SpeechT5...")
        model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
        model.eval()
        print("[OK] Modèle chargé")
        
        print("[2/2] Génération speaker embeddings par défaut...")
        # Générer un speaker embedding par défaut (vecteur de 512 floats)
        # Speaker ID 0 = voix par défaut du modèle
        with torch.no_grad():
            default_speaker = model.speaker_embeddings(torch.tensor([0]))
        
        # Convertir en liste Python
        embeddings_list = default_speaker.squeeze().cpu().tolist()
        
        # Créer structure JSON
        speaker_data = {
            "embeddings": embeddings_list,
            "dimensions": len(embeddings_list),
            "description": "Speaker embeddings par défaut (microsoft/speecht5_tts)",
            "speaker_id": 0,
            "model": "microsoft/speecht5_tts"
        }
        
        # Sauvegarder
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(speaker_data, f, indent=2, ensure_ascii=False)
        
        file_size_kb = output_file.stat().st_size / 1024
        print(f"[OK] Speaker embeddings sauvegardé: {output_file}")
        print(f"  Taille: {file_size_kb:.2f} KB")
        print(f"  Dimensions: {len(embeddings_list)}")
        
        print(f"\n[SUCCESS] Génération réussie!")
        print(f"\n[INFO] Fichier pour Android:")
        print(f"  - {output_file.name} -> /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json")
        print(f"\n[COMMANDE] Transférer vers device:")
        print(f"  adb push \"{output_file}\" /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json")
        
        return True
        
    except Exception as e:
        print(f"[ERROR] Erreur génération: {e}")
        import traceback
        try:
            traceback.print_exc()
        except UnicodeEncodeError:
            print("  (Détails d'erreur non affichables - problème encodage)")
        return False

if __name__ == "__main__":
    if not check_dependencies():
        sys.exit(1)
    
    # Chemin de sortie (peut être passé en argument)
    output_path = sys.argv[1] if len(sys.argv) > 1 else "E:/ChatAI-Models/tts/default_speaker_embeddings.json"
    
    if generate_speaker_embeddings(output_path):
        print("\n[SUCCESS] Génération réussie!")
    else:
        print("\n[FAILED] Génération échouée")
        sys.exit(1)


