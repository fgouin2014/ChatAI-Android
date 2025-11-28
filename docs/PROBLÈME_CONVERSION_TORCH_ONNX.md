# ⚠️ Problème Conversion torch.onnx.export

**Date**: 2025-11-27  
**Problème**: `torch.onnx.export` échoue avec SpeechT5

---

## 🔴 ERREUR

```
ValueError: Attention mask should be of size (s43, 1, s70, s70), but is torch.Size([s43, 1, 512, 512])
TorchExportError: Failed to export the model with torch.export
```

**Cause**: `torch.onnx.export` a des problèmes avec les modèles complexes comme SpeechT5, notamment avec les attention masks dynamiques.

---

## ✅ SOLUTION

### Utiliser optimum-cli (Recommandé)

**Pourquoi**: `optimum-cli` est spécialement conçu pour convertir les modèles Hugging Face en ONNX et gère mieux les cas complexes.

**Script**: `scripts/convert_speecht5_optimum.py`

**Commande**:
```bash
python scripts/convert_speecht5_optimum.py
```

**Ou directement**:
```bash
optimum-cli export onnx --model microsoft/speecht5_tts output_dir --task text-to-speech
```

---

## 🔄 ALTERNATIVES

### Option 1: Utiliser modèle PyTorch avec serveur Python

**Avantages**:
- ✅ Pas de conversion nécessaire
- ✅ Modèle déjà téléchargé (585 MB)
- ✅ Architecture connue (comme Whisper)

**Inconvénients**:
- ❌ Nécessite serveur Python (Termux)
- ❌ Latence HTTP (50-200ms)

**Script**: Créer serveur Python similaire à Whisper

---

### Option 2: Rechercher modèle ONNX pré-converti

**Sources possibles**:
- Hugging Face Spaces avec modèles ONNX
- Communauté (GitHub, forums)
- Modèles alternatifs (Piper TTS, Coqui TTS)

---

### Option 3: Utiliser Android TTS (actuel)

**Avantages**:
- ✅ Déjà fonctionnel
- ✅ Pas de conversion nécessaire
- ✅ Latence minimale (~50ms)

**Inconvénients**:
- ❌ Qualité limitée (voix robotique)
- ❌ Personnalisation limitée

---

## 📊 COMPARAISON

| Méthode | Qualité | Latence | Complexité | Statut |
|---------|---------|---------|------------|--------|
| **ONNX (optimum-cli)** | ⭐⭐⭐⭐⭐ | 100-300ms | Moyenne | ⏳ En test |
| **Serveur Python** | ⭐⭐⭐⭐⭐ | 50-200ms | Faible | ✅ Possible |
| **Android TTS** | ⭐⭐⭐ | 50ms | Faible | ✅ Actuel |

---

## 🎯 RECOMMANDATION

**Essayer d'abord**: `optimum-cli` (script `convert_speecht5_optimum.py`)

**Si échec**: Créer serveur Python (plus simple, architecture connue)

**Fallback**: Garder Android TTS actuel

---

**Le script `convert_speecht5_optimum.py` est en cours d'exécution...** ⏳


