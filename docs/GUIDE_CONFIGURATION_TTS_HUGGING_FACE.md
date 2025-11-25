# Guide Configuration TTS / Hugging Face

**Date**: 2025-11-23  
**Onglet**: TTS/Hugging Face dans la webapp

---

## 📍 Accès à la configuration

1. Ouvrir la webapp ChatAI
2. Aller dans l'onglet **Configuration** (⚙️)
3. Cliquer sur l'onglet **🗣️ TTS/Hugging Face**

---

## 🎯 Sélecteur de mode

En haut de l'onglet, vous trouverez un **sélecteur de mode** avec deux options:

- **🗣️ TTS (Text-to-Speech)** - Configuration de la synthèse vocale
- **🤗 Hugging Face (Embeddings)** - Configuration des embeddings Hugging Face

**⚠️ IMPORTANT**: Changez le sélecteur pour voir les options correspondantes!

---

## 🗣️ Configuration TTS

### Moteur TTS

Deux options disponibles:

1. **Android TTS (natif)** - Moteur Android par défaut
   - Configuration simple (voix uniquement)
   - Fonctionne sans serveur externe

2. **Coqui TTS Server (local)** - Serveur Coqui TTS local
   - Configuration avancée (endpoint, modèle, langue, vitesse, émotion, clone voix)
   - Nécessite un serveur Coqui TTS en cours d'exécution

### Configuration Coqui TTS

Si vous sélectionnez "Coqui TTS Server", les options suivantes apparaissent:

- **Endpoint serveur**: URL du serveur (ex: `http://127.0.0.1:11401/process`)
- **Modèle**: Modèle Coqui TTS (XTTS-v2, VITS Français, ou personnalisé)
- **Langue**: Langue de synthèse (fr, en, es, de, it)
- **Vitesse**: Vitesse de synthèse (0.5-2.0)
- **Émotion**: Émotion pour XTTS-v2 (optionnel)
- **Fichier référence voix**: Chemin vers fichier WAV pour cloner une voix (optionnel)

### Configuration Android TTS

Si vous sélectionnez "Android TTS", les options suivantes apparaissent:

- **Voix**: Sélection de la voix (KITT, GLaDOS, KARR, Sarah, Jarvis, ou personnalisé)

### Options communes

- **Lecture automatique des réponses (TTS)**: Active la lecture vocale automatique des réponses de l'IA

---

## 🤗 Configuration Hugging Face

**Pour voir cette section, sélectionnez "🤗 Hugging Face (Embeddings)" dans le sélecteur de mode en haut.**

### Champs disponibles

1. **🔑 Clé API Hugging Face**
   - Obtenez votre clé sur: https://huggingface.co/settings/tokens
   - Format: `hf_xxxxxxxxxxxxxxxxxxxx`
   - La clé est masquée après sauvegarde (affiche des `*`)

2. **Modèle d'embedding**
   - `all-MiniLM-L6-v2` (384 dim, rapide) - Recommandé
   - `all-mpnet-base-v2` (768 dim, qualité)
   - `paraphrase-multilingual` (768 dim, multilingue)
   - Autre (personnalisé)

3. **Utiliser Hugging Face pour RAG (embeddings)**
   - Case à cocher pour activer l'utilisation de Hugging Face pour RAG
   - Permet d'utiliser RAG même avec Ollama Cloud (qui ne supporte pas `/api/embeddings`)

---

## 💾 Sauvegarde

Cliquez sur le bouton **💾 Sauvegarder** en bas de l'onglet pour enregistrer vos modifications.

---

## 🔍 Dépannage

### Je ne vois pas la section Hugging Face

1. Vérifiez que vous êtes dans l'onglet **TTS/Hugging Face**
2. **Changez le sélecteur de mode** en haut de l'onglet vers "🤗 Hugging Face (Embeddings)"
3. La section Hugging Face devrait apparaître

### La section ne s'affiche pas après changement

1. Ouvrez la console du navigateur (F12)
2. Vérifiez les logs: `updateTtsHfView - mode: huggingface`
3. Si vous voyez des erreurs, contactez le support

---

## 📝 Notes

- La configuration TTS et Hugging Face sont sauvegardées séparément
- La clé API Hugging Face est masquée après sauvegarde (sécurité)
- Pour utiliser Coqui TTS, vous devez avoir un serveur Coqui TTS en cours d'exécution
- Hugging Face est optionnel mais recommandé pour RAG avec Ollama Cloud

