# 🔄 Solution Alternative: Serveur Python TTS

**Date**: 2025-11-27  
**Raison**: Conversion ONNX complexe, serveur Python plus simple

---

## 🎯 POURQUOI SERVEUR PYTHON ?

### Problèmes conversion ONNX
- ❌ `torch.onnx.export` échoue avec SpeechT5 (attention masks)
- ❌ `optimum-cli` peut aussi avoir des problèmes
- ❌ Complexité de conversion

### Avantages serveur Python
- ✅ Modèle PyTorch déjà téléchargé (585 MB)
- ✅ Architecture connue (comme Whisper)
- ✅ Pas de conversion nécessaire
- ✅ Plus simple à maintenir

---

## 📋 ARCHITECTURE

```
Android App → HTTP POST → Python Server (Termux/PC) → SpeechT5 PyTorch → Audio WAV → Réponse
```

**Similaire à Whisper Server**:
- Port: 11401 (TTS) vs 11400 (Whisper)
- Format: JSON avec texte → Réponse audio WAV/MP3
- Serveur Flask/FastAPI

---

## 🚀 IMPLÉMENTATION

### 1. Serveur Python TTS

**Fichier**: `scripts/tts_server.py`

```python
from flask import Flask, request, jsonify, send_file
from transformers import SpeechT5Processor, SpeechT5ForTextToSpeech
import torch
import io
import soundfile as sf

app = Flask(__name__)
processor = None
model = None

def load_model():
    global processor, model
    processor = SpeechT5Processor.from_pretrained("microsoft/speecht5_tts")
    model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
    model.eval()

@app.route('/synthesize', methods=['POST'])
def synthesize():
    data = request.json
    text = data.get('text', '')
    
    # Tokeniser
    inputs = processor(text=text, return_tensors="pt")
    
    # Générer audio
    with torch.no_grad():
        speech = model.generate_speech(
            inputs["input_ids"],
            model.speaker_embeddings(torch.tensor([0]))
        )
    
    # Convertir en WAV
    audio_bytes = io.BytesIO()
    sf.write(audio_bytes, speech.numpy(), samplerate=16000, format='WAV')
    audio_bytes.seek(0)
    
    return send_file(audio_bytes, mimetype='audio/wav')

if __name__ == '__main__':
    load_model()
    app.run(host='127.0.0.1', port=11401)
```

---

### 2. Intégration Android

**Modifier `OnnxTTSManager.kt`** pour utiliser HTTP au lieu d'ONNX:

```kotlin
private fun synthesizeWithServer(text: String): ByteArray? {
    val url = "http://127.0.0.1:11401/synthesize"
    val requestBody = JSONObject().apply {
        put("text", text)
    }
    
    val response = httpClient.post(url, requestBody.toString())
    return response.body?.bytes()
}
```

---

## 📊 COMPARAISON

| Aspect | ONNX | Serveur Python |
|--------|------|----------------|
| **Qualité** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Latence** | 100-300ms | 50-200ms |
| **Complexité** | Élevée | Faible |
| **Maintenance** | Moyenne | Simple |
| **Conversion** | Nécessaire | Non nécessaire |
| **Modèle** | ONNX (~80-150 MB) | PyTorch (585 MB) |

---

## 🎯 RECOMMANDATION

**Pour l'instant**: Utiliser serveur Python (plus simple, fonctionnel rapidement)

**Plus tard**: Si conversion ONNX réussit, migrer vers ONNX (meilleure performance native)

---

**Le serveur Python est plus simple et utilise le modèle déjà téléchargé !** ✅


