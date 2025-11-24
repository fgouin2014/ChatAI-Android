# 🔍 AUDIT TTS COQUI - NOS RULES

**Date**: 2025-11-23  
**Méthodologie**: "Nos Rules" - Recherche approfondie, specs officielles, implémentation exacte à 100%  
**Objectif**: Intégrer Coqui TTS local pour améliorer la voix de KITT

---

## 📋 PHASE 1: AUDIT ARCHITECTURE ACTUELLE

### 1.1 TTS Actuel (Android Natif)

**Fichier**: `ChatAI-Android/app/src/main/java/com/chatai/managers/KittTTSManager.kt`

**Implémentation**:
- ✅ Android `TextToSpeech` natif
- ✅ Langue: `Locale.CANADA_FRENCH`
- ✅ Sélection voix selon personnalité (KITT/GLaDOS/KARR)
- ✅ Pitch/Speed configurables
- ✅ Callbacks: `onTTSReady()`, `onTTSStart()`, `onTTSDone()`, `onTTSError()`
- ✅ Nettoyage Markdown avant TTS
- ✅ Synchronisation VU-meter avec TTS

**Limitations identifiées**:
- ⚠️ Qualité voix limitée (Android TTS natif)
- ⚠️ Pas de clone de voix possible
- ⚠️ Contrôle émotionnel limité
- ⚠️ Voix moins naturelles que Coqui TTS

**Interface actuelle**:
```kotlin
interface TTSListener {
    fun onTTSReady()
    fun onTTSStart(utteranceId: String?)
    fun onTTSDone(utteranceId: String?)
    fun onTTSError(utteranceId: String?)
}
```

**Fonctions publiques**:
- `initialize()` - Initialiser TTS
- `selectVoiceForPersonality(personality: String)` - Sélectionner voix
- `speak(text: String, utteranceId: String)` - Parler un texte
- `stop()` - Arrêter la parole
- `setSpeechRate(rate: Float)` - Vitesse
- `setPitch(pitch: Float)` - Hauteur

---

### 1.2 Pattern STT (Whisper Server) - À SUIVRE

**Fichier**: `ChatAI-Android/app/src/main/java/com/chatai/audio/WhisperServerRecognizer.kt`

**Architecture Whisper Server**:
```
Android App → HTTP POST (Multipart) → Whisper Server (Python) → Transcription JSON
```

**Détails techniques**:
- **Endpoint**: `http://127.0.0.1:11400/inference` (configurable)
- **Format**: Multipart POST avec fichier WAV
- **Paramètres**: `language`, `task`, `model`, `speed_up`, `temperature`, `beam_size`, `best_of`, `threads`
- **Réponse**: JSON `{"text": "transcription"}` ou `{"transcription": "..."}`
- **Timeouts**: connect=15s, read=120s, write=60s, call=150s
- **Warm-up**: Envoi de 200ms d'audio silencieux pour initialiser le modèle

**Code clé**:
```kotlin
// Upload audio WAV
val bodyBuilder = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("file", "capture.wav", wavData.toRequestBody(mediaType))
    .addFormDataPart("language", config.language)
    .addFormDataPart("task", "transcribe")
    .addFormDataPart("model", config.preferredModel)

val request = Request.Builder()
    .url(config.endpoint)
    .post(body)
    .build()

// Réponse JSON
val json = JSONObject(responseBody)
val text = json.optString("text", json.optString("transcription", ""))
```

**Configuration**:
```kotlin
data class AudioEngineConfig(
    val engine: String,
    val endpoint: String,
    val preferredModel: String,
    val language: String,
    // ... autres paramètres
)
```

**Pattern à suivre pour Coqui TTS**:
1. ✅ Serveur Python HTTP local (comme Whisper)
2. ✅ Endpoint configurable (port différent, ex: 11401)
3. ✅ Multipart POST avec texte
4. ✅ Réponse audio WAV/MP3
5. ✅ Configuration via `AudioEngineConfig` (ou nouveau `TTSConfig`)
6. ✅ Fallback vers Android TTS si serveur indisponible

---

## 📚 PHASE 2: RECHERCHE DOCUMENTATION OFFICIELLE COQUI TTS

### 2.1 Sources à consulter (Nos Rules)

**Repos officiels à vérifier**:
- ❌ `c:\repos\` - Pas de Coqui TTS (projet différent)
- ✅ GitHub: `https://github.com/coqui-ai/TTS`
- ✅ Documentation: `https://tts.readthedocs.io/`
- ✅ Hugging Face: `https://huggingface.co/coqui`

**Spécifications à lire**:
1. API Python officielle (`TTS.api`)
2. Modèles disponibles (XTTS-v2, etc.)
3. Serveur HTTP/Flask/FastAPI (si existe)
4. Paramètres de synthèse (speed, emotion, etc.)
5. Clone de voix (voice cloning)

---

### 2.2 Modèles Coqui TTS disponibles

**XTTS-v2** (recommandé):
- **Nom complet**: `tts_models/multilingual/multi-dataset/xtts_v2`
- **Avantages**:
  - Multilingue (17 langues)
  - Clone de voix possible
  - Qualité très élevée
  - Contrôle émotionnel
- **Taille**: ~1.7 GB
- **GPU recommandé**: Oui (mais fonctionne CPU)

**Autres modèles**:
- `tts_models/fr/css10/vits` - Français uniquement
- `tts_models/en/ljspeech/tacotron2-DDC` - Anglais
- `tts_models/multilingual/multi-dataset/your_tts` - Multilingue (plus léger)

---

### 2.3 API Python Coqui TTS

**Usage basique** (selon documentation officielle):
```python
from TTS.api import TTS

# Initialiser modèle
tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2")

# Synthèse simple
tts.tts_to_file(
    text="Bonjour, je suis KITT.",
    file_path="output.wav",
    speaker_wav="reference_voice.wav",  # Clone voix (optionnel)
    language="fr"
)
```

**Paramètres disponibles**:
- `text`: Texte à synthétiser
- `speaker_wav`: Fichier audio référence pour clone voix
- `language`: Langue (fr, en, es, etc.)
- `emotion`: Émotion (happy, sad, angry, etc.) - XTTS-v2
- `speed`: Vitesse (0.5-2.0)
- `file_path`: Chemin sortie audio

---

### 2.4 Serveur HTTP Coqui TTS (OFFICIEL - Mary-TTS API)

**⭐ DÉCOUVERTE IMPORTANTE**: Coqui TTS a un serveur HTTP intégré avec compatibilité Mary-TTS API!

**Commande officielle**:
```bash
tts-server --model_name tts_models/multilingual/multi-dataset/xtts_v2
```

**Port par défaut**: `5002` (à configurer pour `11401`)

**API Mary-TTS compatible**:
```bash
# Synthèse vocale
curl "http://localhost:5002/process?INPUT_TEXT=Bonjour&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE" > output.wav
```

**Paramètres API Mary-TTS**:
- `INPUT_TEXT`: Texte à synthétiser
- `INPUT_TYPE`: `TEXT` (ou `SSML`)
- `OUTPUT_TYPE`: `AUDIO`
- `AUDIO`: `WAVE_FILE` (format WAV)

**Option A: Utiliser serveur intégré Coqui** (recommandé)
- ✅ Serveur officiel, maintenu
- ✅ Compatible Mary-TTS (standard)
- ⚠️ Paramètres limités (pas de speed/emotion via API Mary-TTS)

**Option B: Serveur Flask/FastAPI custom** (plus flexible)
- ✅ Contrôle total (speed, emotion, clone voix)
- ✅ Endpoints personnalisés
- ⚠️ Maintenance supplémentaire

**Recommandation**: Commencer avec serveur intégré, puis migrer vers custom si besoin de features avancées.

**Serveur custom (si nécessaire)**:
```python
from flask import Flask, request, send_file
from TTS.api import TTS
import tempfile

app = Flask(__name__)
tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2")

@app.route('/synthesize', methods=['POST'])
def synthesize():
    text = request.form.get('text', '')
    language = request.form.get('language', 'fr')
    speaker_wav = request.files.get('speaker_wav')  # Optionnel
    emotion = request.form.get('emotion', None)  # Optionnel
    speed = float(request.form.get('speed', 1.0))
    
    output_path = tempfile.mktemp(suffix='.wav')
    tts.tts_to_file(
        text=text,
        file_path=output_path,
        speaker_wav=speaker_wav if speaker_wav else None,
        language=language,
        emotion=emotion,
        speed=speed
    )
    
    return send_file(output_path, mimetype='audio/wav')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=11401)
```

**Endpoints proposés**:
- `POST /synthesize` - Synthèse TTS (custom)
- `GET /process` - API Mary-TTS (serveur intégré)
- `GET /ping` - Vérifier disponibilité
- `GET /models` - Lister modèles disponibles

---

## 🏗️ PHASE 3: ARCHITECTURE PROPOSÉE

### 3.1 Architecture complète

```
┌─────────────────────────────────────────────────────────┐
│  ANDROID APP (ChatAI)                                   │
│  ├── KittTTSManager.kt (existant)                       │
│  │   └── Fallback: Android TTS natif                    │
│  │                                                       │
│  └── CoquiTTSClient.kt (NOUVEAU) ⭐                     │
│      ├── HTTP POST → Coqui Server                       │
│      ├── Réception audio WAV                            │
│      ├── Lecture via MediaPlayer                        │
│      └── Callbacks: onStart/onDone/onError              │
└─────────────────────────────────────────────────────────┘
                    ↓ HTTP (port 11401)
┌─────────────────────────────────────────────────────────┐
│  COQUI TTS SERVER (Python)                              │
│  ├── Flask/FastAPI (port 11401)                         │
│  ├── TTS.api (XTTS-v2)                                  │
│  ├── Endpoint: /synthesize                              │
│  └── Retour: WAV audio                                  │
└─────────────────────────────────────────────────────────┘
```

---

### 3.2 Nouveau fichier: `CoquiTTSClient.kt`

**Emplacement**: `ChatAI-Android/app/src/main/java/com/chatai/audio/CoquiTTSClient.kt`

**Interface** (identique à `KittTTSManager` pour compatibilité):
```kotlin
interface CoquiTTSListener {
    fun onTTSReady()
    fun onTTSStart(utteranceId: String?)
    fun onTTSDone(utteranceId: String?)
    fun onTTSError(utteranceId: String?)
}

class CoquiTTSClient(
    private val config: TTSConfig,
    private val listener: CoquiTTSListener,
    private val httpClient: OkHttpClient
) {
    fun initialize() // Vérifier serveur disponible
    fun speak(text: String, utteranceId: String = "coqui_speech")
    fun stop()
    fun isReady(): Boolean
    fun isSpeaking(): Boolean
}
```

**Fonctionnalités**:
1. **HTTP POST** vers serveur Coqui
2. **Réception WAV** en mémoire
3. **Lecture audio** via `MediaPlayer` ou `AudioTrack`
4. **Callbacks** synchronisés avec lecture
5. **Fallback** vers Android TTS si serveur indisponible

---

### 3.3 Configuration TTS

**Nouveau fichier**: `ChatAI-Android/app/src/main/java/com/chatai/audio/TTSConfig.kt`

```kotlin
data class TTSConfig(
    val engine: String,  // "coqui_server" ou "android_tts"
    val endpoint: String,  // "http://127.0.0.1:11401/synthesize"
    val model: String,  // "xtts_v2" ou autre
    val language: String,  // "fr", "en", etc.
    val speakerWavPath: String?,  // Chemin fichier référence (clone voix)
    val emotion: String?,  // "happy", "sad", etc. (optionnel)
    val speed: Float,  // 0.5-2.0
    val apiKey: String?  // Pour authentification (optionnel)
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:11401/synthesize"
        const val DEFAULT_ENGINE = "coqui_server"
        const val DEFAULT_MODEL = "xtts_v2"
        const val DEFAULT_LANGUAGE = "fr"
        const val DEFAULT_SPEED = 1.0f
    }
}
```

---

### 3.4 Intégration dans `KittTTSManager`

**Option A: Wrapper Coqui dans KittTTSManager** (recommandé)
```kotlin
class KittTTSManager(
    private val context: Context,
    private val listener: TTSListener
) {
    private var androidTTS: TextToSpeech? = null
    private var coquiClient: CoquiTTSClient? = null
    private var useCoqui = false
    
    fun initialize() {
        // Vérifier si Coqui est configuré et disponible
        val ttsConfig = TTSConfig.fromContext(context)
        if (ttsConfig.engine == "coqui_server") {
            coquiClient = CoquiTTSClient(ttsConfig, object : CoquiTTSListener {
                override fun onTTSReady() { listener.onTTSReady() }
                override fun onTTSStart(id: String?) { listener.onTTSStart(id) }
                override fun onTTSDone(id: String?) { listener.onTTSDone(id) }
                override fun onTTSError(id: String?) { listener.onTTSError(id) }
            })
            coquiClient?.initialize()
            useCoqui = true
        } else {
            // Fallback Android TTS
            androidTTS = TextToSpeech(context, this)
        }
    }
    
    fun speak(text: String, utteranceId: String = "kitt_speech") {
        if (useCoqui && coquiClient?.isReady() == true) {
            coquiClient?.speak(text, utteranceId)
        } else {
            androidTTS?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }
}
```

**Avantages**:
- ✅ Compatibilité totale avec code existant
- ✅ Fallback automatique Android TTS
- ✅ Pas de modification des callers existants

---

## 📝 PHASE 4: PLAN D'IMPLÉMENTATION

### Étape 1: Serveur Python Coqui TTS (Jour 1-2)

**Option A: Serveur intégré Coqui** (recommandé pour début)
```bash
# Installation
pip install TTS

# Démarrer serveur (port 5002 par défaut)
tts-server --model_name tts_models/multilingual/multi-dataset/xtts_v2 --port 11401

# Test API Mary-TTS
curl "http://127.0.0.1:11401/process?INPUT_TEXT=Bonjour&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE" > output.wav
```

**Option B: Serveur Flask custom** (si besoin features avancées)
**Fichier**: `ChatAI-Coqui-TTS-Server/coqui_tts_server.py`

**Tâches**:
1. Créer serveur Flask/FastAPI
2. Initialiser modèle XTTS-v2 via `TTS.api`
3. Endpoint `/synthesize` avec paramètres (text, language, speed, emotion, speaker_wav)
4. Endpoint `/ping` pour vérification
5. Gestion erreurs et logging
6. Support clone voix (optionnel)

**Tests**:
```bash
# Test serveur custom
curl -X POST http://127.0.0.1:11401/synthesize \
  -F "text=Bonjour, je suis KITT." \
  -F "language=fr" \
  -F "speed=1.0" \
  -o output.wav

# Test avec clone voix
curl -X POST http://127.0.0.1:11401/synthesize \
  -F "text=Bonjour, je suis KITT." \
  -F "language=fr" \
  -F "speaker_wav=@reference_voice.wav" \
  -o output.wav
```

**Recommandation**: Commencer avec serveur intégré (Option A), puis migrer vers custom si besoin de speed/emotion/clone voix.

---

### Étape 2: Client Android CoquiTTSClient (Jour 3-4)

**Fichier**: `CoquiTTSClient.kt`

**Tâches**:
1. Classe `CoquiTTSClient` avec interface identique à `KittTTSManager`
2. **Option A**: HTTP GET vers `/process` (API Mary-TTS)
3. **Option B**: HTTP POST vers `/synthesize` (API custom)
4. Réception WAV en mémoire (ResponseBody → ByteArray)
5. Lecture via `MediaPlayer` ou `AudioTrack`
6. Callbacks synchronisés (`onStart`, `onDone`, `onError`)
7. Gestion erreurs et fallback vers Android TTS

**Code clé (API Mary-TTS)**:
```kotlin
// GET request vers /process
val url = "${config.endpoint}/process?INPUT_TEXT=${URLEncoder.encode(text, "UTF-8")}&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE"
val request = Request.Builder().url(url).get().build()
val response = httpClient.newCall(request).execute()
val wavBytes = response.body?.bytes() ?: throw IllegalStateException("Réponse vide")
```

**Code clé (API custom)**:
```kotlin
// POST request vers /synthesize
val bodyBuilder = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("text", text)
    .addFormDataPart("language", config.language)
    .addFormDataPart("speed", config.speed.toString())
    
config.speakerWavPath?.let {
    val wavFile = File(it)
    bodyBuilder.addFormDataPart("speaker_wav", wavFile.name, 
        wavFile.readBytes().toRequestBody("audio/wav".toMediaType()))
}

val request = Request.Builder()
    .url("${config.endpoint}/synthesize")
    .post(bodyBuilder.build())
    .build()
    
val response = httpClient.newCall(request).execute()
val wavBytes = response.body?.bytes() ?: throw IllegalStateException("Réponse vide")
```

**Lecture audio**:
```kotlin
// Option 1: MediaPlayer (plus simple)
val tempFile = File.createTempFile("coqui_tts", ".wav", context.cacheDir)
tempFile.writeBytes(wavBytes)
mediaPlayer.setDataSource(tempFile.absolutePath)
mediaPlayer.prepare()
mediaPlayer.setOnCompletionListener { listener.onTTSDone(utteranceId) }
mediaPlayer.start()
listener.onTTSStart(utteranceId)

// Option 2: AudioTrack (plus contrôle, streaming possible)
// À implémenter si besoin de streaming
```

**Tests**:
- Test avec serveur disponible (API Mary-TTS)
- Test avec serveur disponible (API custom)
- Test avec serveur indisponible (fallback Android TTS)
- Test avec différents textes
- Test avec paramètres (speed, emotion, clone voix)
- Test performance (latence)

---

### Étape 3: Configuration et intégration (Jour 5)

**Fichiers**:
- `TTSConfig.kt` - Configuration
- `KittTTSManager.kt` - Intégration wrapper
- `AiConfigManager.java` - Sauvegarde config
- `chat-config.js` - UI configuration webapp

**Tâches**:
1. Créer `TTSConfig.fromContext()`
2. Modifier `KittTTSManager` pour wrapper Coqui
3. Ajouter config dans `AiConfigManager`
4. Ajouter UI dans webapp (sélection moteur TTS)

---

### Étape 4: Tests et optimisation (Jour 6-7)

**Tests**:
1. Test intégration complète
2. Test fallback Android TTS
3. Test performance (latence)
4. Test qualité audio
5. Test clone voix (si implémenté)

**Optimisations**:
- Cache audio pour textes répétés
- Préchargement modèle (warm-up)
- Streaming audio (si supporté)

---

## ⚠️ POINTS D'ATTENTION (NOS RULES)

### 1. Compatibilité 100% avec code existant
- ✅ Interface `TTSListener` identique
- ✅ Callbacks identiques
- ✅ Fallback automatique Android TTS
- ✅ Pas de breaking changes

### 2. Gestion erreurs robuste
- ✅ Timeout HTTP configurable
- ✅ Retry automatique (optionnel)
- ✅ Fallback Android TTS si erreur
- ✅ Logging détaillé

### 3. Performance
- ✅ Latence minimale (cache si possible)
- ✅ Warm-up serveur (comme Whisper)
- ✅ Streaming audio (si supporté)

### 4. Configuration flexible
- ✅ Endpoint configurable
- ✅ Modèle configurable
- ✅ Paramètres ajustables (speed, emotion)
- ✅ Clone voix optionnel

---

## 📚 RESSOURCES OFFICIELLES CONSULTÉES

### Documentation Coqui TTS (OFFICIELLE)
- **GitHub**: https://github.com/coqui-ai/TTS
- **Documentation**: https://docs.coqui.ai/
- **Installation**: https://docs.coqui.ai/en/stable/installation.html
- **Mary-TTS API**: https://coqui-tts.readthedocs.io/en/latest/marytts.html
- **API Reference**: https://tts.readthedocs.io/en/latest/api.html

### Modèles disponibles
- **XTTS-v2**: `tts_models/multilingual/multi-dataset/xtts_v2`
- **Liste modèles**: `tts --list_models`
- **Info modèle**: `tts --model_info_by_name "tts_models/..."`
- **Hugging Face**: https://huggingface.co/coqui

### Serveur HTTP intégré
- **Commande**: `tts-server --model_name <model> --port <port>`
- **API Mary-TTS**: `/process?INPUT_TEXT=...&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE`
- **Port par défaut**: `5002`
- **Documentation**: https://coqui-tts.readthedocs.io/en/latest/marytts.html

### Usage Python API
```python
from TTS.api import TTS

# Initialiser
tts = TTS("tts_models/multilingual/multi-dataset/xtts_v2")

# Synthèse simple
tts.tts_to_file(text="Bonjour", file_path="output.wav", language="fr")

# Clone voix
tts.tts_to_file(
    text="Bonjour",
    file_path="output.wav",
    speaker_wav="reference.wav",  # Clone voix
    language="fr"
)
```

---

## ✅ VALIDATION NOS RULES

### Checklist avant implémentation

- [x] Documentation officielle Coqui TTS lue complètement
- [x] Serveur HTTP intégré Coqui découvert (Mary-TTS API)
- [x] Architecture Whisper Server comprise à 100%
- [x] Interface `KittTTSManager` analysée complètement
- [x] Pattern fallback défini
- [x] Configuration définie
- [x] Tests planifiés
- [x] Points d'attention identifiés
- [x] API Python `TTS.api` documentée
- [x] Options serveur (intégré vs custom) évaluées

### Découvertes importantes

1. **Serveur HTTP intégré**: Coqui TTS a un serveur HTTP officiel avec API Mary-TTS
   - Commande: `tts-server --model_name <model> --port <port>`
   - API: `/process?INPUT_TEXT=...&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE`
   - Avantage: Pas besoin de créer serveur custom (sauf pour features avancées)

2. **API Python**: `TTS.api` pour usage programmatique
   - `TTS(model_name)` - Initialiser modèle
   - `tts.tts_to_file(text, file_path, language, speaker_wav, emotion, speed)` - Synthèse

3. **Modèles**: XTTS-v2 recommandé pour multilingue + clone voix
   - Taille: ~1.7 GB
   - Langues: 17 langues supportées
   - Features: Clone voix, contrôle émotion, multilingue

---

## 🎯 PROCHAINES ÉTAPES

1. ✅ **Documentation officielle Coqui TTS lue** (API, modèles, serveur intégré)
2. ✅ **Serveur HTTP intégré découvert** (Mary-TTS API)
3. **Décider: Serveur intégré vs Custom**
   - **Recommandation**: Commencer avec serveur intégré (simple)
   - Migrer vers custom si besoin speed/emotion/clone voix
4. **Installer et tester serveur Coqui** (local)
   ```bash
   pip install TTS
   tts-server --model_name tts_models/multilingual/multi-dataset/xtts_v2 --port 11401
   ```
5. **Implémenter client Android** (`CoquiTTSClient.kt`)
   - Support API Mary-TTS (GET `/process`)
   - Support API custom (POST `/synthesize`) si serveur custom
6. **Intégrer dans KittTTSManager** (wrapper avec fallback)
7. **Tester et optimiser**

---

**Status**: ✅ AUDIT COMPLET  
**Décision**: Utiliser serveur intégré Coqui (Mary-TTS API) pour début, puis évaluer serveur custom si besoin features avancées

