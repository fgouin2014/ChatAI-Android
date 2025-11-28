# 🚀 Commandes Rapides - Conversion ONNX TTS

## 📍 IMPORTANT: Répertoire

**Le script est dans**: `ChatAI-Android/scripts/`

**PAS dans**: `ChatAI-Android-beta/scripts/`

---

## ✅ COMMANDE CORRECTE

### Depuis ChatAI-Android-beta:
```bash
cd ChatAI-Android
python scripts/convert_speecht5_to_onnx_complete.py
```

### Ou avec chemin complet:
```bash
python ChatAI-Android/scripts/convert_speecht5_to_onnx_complete.py
```

---

## ⚠️ COMMANDE INCORRECTE

```bash
# ❌ NE FONCTIONNE PAS
python scripts/convert_speecht5_to_onnx_complete.py
# (depuis ChatAI-Android-beta)
```

---

## 📋 AUTRES COMMANDES

### Vérifier fichiers générés:
```bash
Get-ChildItem E:\ChatAI-Models\tts\onnx\
```

### Transférer vers device:
```powershell
cd ChatAI-Android
.\scripts\transfer_tts_onnx_to_device.ps1
```

---

**Toujours utiliser le chemin complet ou se placer dans ChatAI-Android !** ✅


