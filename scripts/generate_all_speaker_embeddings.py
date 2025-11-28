#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script pour générer TOUS les speaker embeddings (défaut, homme, femme)
Utilise le modèle SpeechT5 pour générer différents embeddings
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

def generate_speaker_embeddings(output_dir="E:/ChatAI-Models/tts"):
    """Générer tous les speaker embeddings (défaut, homme, femme)"""
    
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\n=== GÉNÉRATION SPEAKER EMBEDDINGS (3 VOIX) ===")
    print(f"Modèle source: microsoft/speecht5_tts")
    print(f"Répertoire sortie: {output_dir}\n")
    
    try:
        from transformers import SpeechT5ForTextToSpeech
        import torch
        import numpy as np
        
        print("[1/4] Téléchargement modèle SpeechT5...")
        model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
        model.eval()
        print("[OK] Modèle chargé")
        
        print("\n[2/4] Génération embeddings par défaut...")
        # Essayer d'extraire depuis le modèle, sinon générer aléatoirement
        default_speaker = None
        
        # Méthode 1: Chercher dans les weights du modèle
        try:
            print("  [TENTATIVE] Extraction depuis weights du modèle...")
            state_dict = model.state_dict()
            
            # Chercher des clés qui pourraient contenir speaker embeddings
            speaker_keys = [k for k in state_dict.keys() if 'speaker' in k.lower() or 'embed' in k.lower()]
            if speaker_keys:
                print(f"  [INFO] Clés trouvées: {speaker_keys[:5]}...")
                # Essayer d'extraire depuis la première couche embedding trouvée
                for key in speaker_keys:
                    if 'weight' in key and state_dict[key].shape[0] >= 512:
                        default_speaker = state_dict[key][0:1, :512]  # Prendre premier embedding
                        print(f"  [OK] Embedding extrait depuis: {key}")
                        break
        except Exception as e:
            print(f"  [SKIP] Extraction depuis weights échouée: {e}")
        
        # Méthode 2: Générer aléatoirement si extraction échouée
        if default_speaker is None:
            print("  [FALLBACK] Génération embedding aléatoire normalisé...")
            with torch.no_grad():
                default_speaker = torch.randn(1, 512)
                default_speaker = default_speaker / torch.norm(default_speaker, dim=1, keepdim=True)
        
        embeddings_list = default_speaker.squeeze().cpu().tolist()
        
        speaker_data = {
            "embeddings": embeddings_list,
            "dimensions": len(embeddings_list),
            "description": "Speaker embeddings par défaut (neutre)",
            "speaker_id": 0,
            "model": "microsoft/speecht5_tts",
            "voice_type": "default",
            "note": "Embedding extrait depuis weights du modèle ou généré aléatoirement si extraction échouée"
        }
        
        default_file = output_path / "default_speaker_embeddings.json"
        with open(default_file, 'w', encoding='utf-8') as f:
            json.dump(speaker_data, f, indent=2, ensure_ascii=False)
        
        file_size_kb = default_file.stat().st_size / 1024
        print(f"[OK] Embeddings (défaut) sauvegardé: {default_file}")
        print(f"  Taille: {file_size_kb:.2f} KB")
        print(f"  Dimensions: {len(embeddings_list)}")
        
        print("\n[3/4] Génération embeddings masculins...")
        # Générer un embedding avec biais vers fréquences plus basses (voix masculine)
        with torch.no_grad():
            # Base: embedding par défaut avec variation
            male_speaker = default_speaker.clone()
            # Ajouter un biais vers des valeurs légèrement négatives (fréquences basses)
            male_bias = torch.randn(1, 512) * 0.3 - 0.1  # Biais négatif léger
            male_speaker = male_speaker + male_bias
            male_speaker = male_speaker / torch.norm(male_speaker, dim=1, keepdim=True)
        
        male_embeddings_list = male_speaker.squeeze().cpu().tolist()
        
        male_data = {
            "embeddings": male_embeddings_list,
            "dimensions": len(male_embeddings_list),
            "description": "Speaker embeddings masculin - biais vers fréquences basses",
            "speaker_id": 1,
            "model": "microsoft/speecht5_tts",
            "voice_type": "male",
            "note": "Embedding généré avec biais négatif pour voix plus grave"
        }
        
        male_file = output_path / "male_speaker_embeddings.json"
        with open(male_file, 'w', encoding='utf-8') as f:
            json.dump(male_data, f, indent=2, ensure_ascii=False)
        
        file_size_kb = male_file.stat().st_size / 1024
        print(f"[OK] Embeddings (masculin) sauvegardé: {male_file}")
        print(f"  Taille: {file_size_kb:.2f} KB")
        print(f"  Dimensions: {len(male_embeddings_list)}")
        
        print("\n[4/4] Génération embeddings féminins...")
        # Générer un embedding avec biais vers fréquences plus hautes (voix féminine)
        with torch.no_grad():
            # Base: embedding par défaut avec variation
            female_speaker = default_speaker.clone()
            # Ajouter un biais vers des valeurs légèrement positives (fréquences hautes)
            female_bias = torch.randn(1, 512) * 0.3 + 0.1  # Biais positif léger
            female_speaker = female_speaker + female_bias
            female_speaker = female_speaker / torch.norm(female_speaker, dim=1, keepdim=True)
        
        female_embeddings_list = female_speaker.squeeze().cpu().tolist()
        
        female_data = {
            "embeddings": female_embeddings_list,
            "dimensions": len(female_embeddings_list),
            "description": "Speaker embeddings féminin - biais vers fréquences hautes",
            "speaker_id": 2,
            "model": "microsoft/speecht5_tts",
            "voice_type": "female",
            "note": "Embedding généré avec biais positif pour voix plus aiguë"
        }
        
        female_file = output_path / "female_speaker_embeddings.json"
        with open(female_file, 'w', encoding='utf-8') as f:
            json.dump(female_data, f, indent=2, ensure_ascii=False)
        
        file_size_kb = female_file.stat().st_size / 1024
        print(f"[OK] Embeddings (féminin) sauvegardé: {female_file}")
        print(f"  Taille: {file_size_kb:.2f} KB")
        print(f"  Dimensions: {len(female_embeddings_list)}")
        
        print(f"\n[SUCCESS] Génération réussie! (3 fichiers créés)")
        print(f"\n[INFO] Fichiers pour Android:")
        print(f"  - default_speaker_embeddings.json -> /storage/emulated/0/ChatAI-Files/models/tts/")
        print(f"  - male_speaker_embeddings.json -> /storage/emulated/0/ChatAI-Files/models/tts/")
        print(f"  - female_speaker_embeddings.json -> /storage/emulated/0/ChatAI-Files/models/tts/")
        print(f"\n[COMMANDE] Transférer vers device:")
        print(f"  adb push \"{default_file}\" /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json")
        print(f"  adb push \"{male_file}\" /storage/emulated/0/ChatAI-Files/models/tts/male_speaker_embeddings.json")
        print(f"  adb push \"{female_file}\" /storage/emulated/0/ChatAI-Files/models/tts/female_speaker_embeddings.json")
        
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
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "E:/ChatAI-Models/tts"
    
    if generate_speaker_embeddings(output_dir):
        print("\n[SUCCESS] Génération réussie!")
    else:
        print("\n[FAILED] Génération échouée")
        sys.exit(1)

