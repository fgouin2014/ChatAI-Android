# Notes de Développement - Serveur RAG Python

**Date:** 2025-01-XX  
**Contexte:** Configuration et amélioration du serveur RAG Python pour ChatAI Android  
**Thread:** Configuration serveur RAG + environnement virtuel

---

## 🎯 OBJECTIF

Améliorer la configuration du serveur RAG Python et documenter l'état actuel des systèmes RAG dans ChatAI.

---

## 📋 MODIFICATIONS EFFECTUÉES

### 1. Script de démarrage amélioré (`ChatAI-RAG-Server/start_rag_server.ps1`)

**Problème identifié:**
- Le script n'utilisait pas l'environnement virtuel `venv-rag`
- Vérification des dépendances avec Python global au lieu du venv

**Solution implémentée:**
- ✅ Détection automatique de l'environnement virtuel `C:\androidProject\ChatAI-Android-beta\venv-rag`
- ✅ Utilisation automatique du venv si disponible
- ✅ Fallback sur Python global si venv non trouvé
- ✅ Option `-UseVenv:$false` pour forcer Python global
- ✅ Vérification des dépendances avec le bon Python (venv ou global)
- ✅ Correction syntaxe PowerShell pour affichage

**Fichier modifié:**
- `ChatAI-RAG-Server/start_rag_server.ps1`

**Lignes clés:**
```powershell
# Chemin vers l'environnement virtuel
$venvPath = "C:\androidProject\ChatAI-Android-beta\venv-rag"
$venvPython = Join-Path $venvPath "Scripts\python.exe"

# Détection automatique
if ($UseVenv -and (Test-Path $venvPython)) {
    $pythonCmd = $venvPython
    $pipCmd = $venvPip
}
```

---

### 2. Document de configuration créé (`ChatAI-RAG-Server/CONFIGURATION_RAG.md`)

**Contenu:**
- Vue d'ensemble des deux systèmes RAG (Ollama vs Python)
- Configuration de l'environnement virtuel
- Instructions de démarrage (3 options)
- Comparaison des deux systèmes
- État d'intégration dans l'app Android
- Tests et recommandations

**Fichier créé:**
- `ChatAI-RAG-Server/CONFIGURATION_RAG.md`

---

## 🔍 ÉTAT ACTUEL DES SYSTÈMES RAG

### Système 1: RAG via Ollama Local ✅

**Statut:** Fonctionnel et intégré dans l'app Android

**Composants:**
- `RAGService.kt` - Service de recherche sémantique
- `EmbeddingService.kt` - Génération embeddings via Ollama
- Modèle: `nomic-embed-text` (768 dimensions)
- Stockage: Room Database (colonne `embeddingsJson`)

**Fonctionnement:**
- L'app Android génère des embeddings via Ollama `/api/embeddings`
- Recherche sémantique avec similarité cosine
- Contexte injecté dans les prompts Ollama

**Documentation:**
- `ChatAI-Android/docs/GUIDE_RAG.md`

---

### Système 2: RAG Server Python ⚠️

**Statut:** Serveur fonctionnel mais **NON intégré** dans l'app Android

**Composants:**
- `ChatAI-RAG-Server/rag_server.py` - Serveur Flask (port 8890)
- Modèle: `all-MiniLM-L6-v2` (sentence-transformers, 384 dimensions)
- Environnement virtuel: `ChatAI-Android-beta/venv-rag/`

**Endpoints disponibles:**
- `GET /status` - Statut du serveur
- `POST /embed` - Générer embedding
- `POST /search` - Recherche sémantique
- `POST /detect_correction` - Détecter corrections
- `POST /analyze` - Analyser conversations

**Fonctionnement:**
- Serveur Flask accessible via HTTP sur réseau local
- Utilise sentence-transformers (plus léger que Ollama)
- Cache des embeddings en mémoire

**Documentation:**
- `ChatAI-RAG-Server/README.md`
- `ChatAI-RAG-Server/CONFIGURATION_RAG.md` (nouveau)

---

## 🛠️ ENVIRONNEMENT VIRTUEL

**Emplacement:** `C:\androidProject\ChatAI-Android-beta\venv-rag\`

**Configuration:**
- Python: 3.13.5
- Chemin: `C:\Users\Quentin\AppData\Local\Programs\Python\Python313`

**Packages installés:**
- flask
- sentence-transformers
- numpy
- torch
- transformers

**Utilisation:**
Le script `start_rag_server.ps1` utilise maintenant automatiquement cet environnement virtuel.

---

## 🚀 DÉMARRAGE DU SERVEUR RAG PYTHON

### Option 1: Script PowerShell (recommandé)

```powershell
cd C:\androidProject\ChatAI-RAG-Server
.\start_rag_server.ps1
```

**Fonctionnalités:**
- ✅ Détecte et utilise automatiquement `venv-rag`
- ✅ Vérifie les dépendances
- ✅ Installe les packages manquants si nécessaire
- ✅ Détecte l'IP du PC automatiquement
- ✅ Affiche l'URL de configuration pour ChatAI

### Option 2: Manuel avec venv

```powershell
# Activer l'environnement virtuel
C:\androidProject\ChatAI-Android-beta\venv-rag\Scripts\Activate.ps1

# Aller dans le dossier du serveur
cd C:\androidProject\ChatAI-RAG-Server

# Démarrer le serveur
python rag_server.py
```

### Option 3: Sans venv (Python global)

```powershell
cd C:\androidProject\ChatAI-RAG-Server
.\start_rag_server.ps1 -UseVenv:$false
```

---

## 📊 COMPARAISON DES DEUX SYSTÈMES

| Caractéristique | RAG Ollama Local | RAG Server Python |
|----------------|------------------|-------------------|
| **Modèle** | nomic-embed-text | all-MiniLM-L6-v2 |
| **Dimensions** | 768 | 384 |
| **Taille modèle** | ~140 MB | ~80 MB |
| **Dépendances** | Ollama installé | Python + sentence-transformers |
| **Performance** | Moyenne (via Ollama) | Rapide (local) |
| **Intégration Android** | ✅ Complète | ❌ Non intégré |
| **Réseau requis** | Local (Ollama) | Local (HTTP) |
| **Cache** | Non | Oui (en mémoire) |

---

## 🔧 INTÉGRATION FUTURE (À FAIRE)

### Pour intégrer le serveur RAG Python dans l'app Android:

1. **Ajouter SharedPreferences:**
   ```kotlin
   sharedPreferences.edit()
       .putString("rag_server_url", "http://192.168.x.x:8890")
       .putBoolean("rag_server_enabled", true)
       .apply()
   ```

2. **Créer RagServerClient.kt:**
   - Classe pour appels HTTP vers le serveur RAG
   - Méthodes: `embed()`, `search()`, `detectCorrection()`, `analyze()`
   - Gestion des erreurs et timeouts

3. **Intégrer dans KittAIService.kt:**
   - Détecter si le serveur RAG est disponible
   - Utiliser le serveur RAG au lieu d'Ollama pour les embeddings (optionnel)
   - Ou utiliser les deux en parallèle (hybride)

**Référence:**
- `ChatAI-Android/docs/RAG_SERVER_ARCHITECTURE.md` (plan d'intégration)

---

## 🧪 TESTS

### Test 1: Vérifier le statut

```powershell
Invoke-WebRequest -Uri "http://localhost:8890/status" | Select-Object -ExpandProperty Content
```

**Réponse attendue:**
```json
{
  "status": "online",
  "model": "all-MiniLM-L6-v2",
  "embedding_dimension": 384,
  "cache_size": 0,
  "conversations_loaded": 0,
  "port": 8890,
  "timestamp": "2025-01-XX..."
}
```

### Test 2: Tester l'embedding

```powershell
$body = @{
    text = "Quelle heure à Tokyo?"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8890/embed" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body | Select-Object -ExpandProperty Content
```

### Test 3: Page web

Ouvrir dans le navigateur: `http://localhost:8890/`

---

## 📁 FICHIERS MODIFIÉS/CRÉÉS

### Modifiés:
- ✅ `ChatAI-RAG-Server/start_rag_server.ps1` - Support venv automatique

### Créés:
- ✅ `ChatAI-RAG-Server/CONFIGURATION_RAG.md` - Documentation complète
- ✅ `ChatAI-Android/docs/NOTES_DEVELOPPEMENT_RAG.md` - Ce document

---

## 📝 NOTES TECHNIQUES

- Le serveur RAG Python écoute sur `0.0.0.0:8890` (accessible depuis le réseau local)
- L'environnement virtuel utilise Python 3.13.5
- Le script `start_rag_server.ps1` détecte automatiquement l'IP du PC
- Les embeddings sont mis en cache en mémoire (pas de persistance)

---

## 🎯 PROCHAINES ÉTAPES (Pour un autre thread)

1. **Décider de l'intégration:**
   - Option A: Remplacer RAG Ollama par RAG Server Python
   - Option B: Utiliser les deux en parallèle (hybride)
   - Option C: Garder uniquement RAG Ollama

2. **Si intégration choisie:**
   - Créer `RagServerClient.kt` dans l'app Android
   - Ajouter configuration UI dans `AIConfigurationActivity`
   - Implémenter détection automatique du serveur
   - Ajouter fallback si serveur indisponible
   - Tester avec l'app Android sur le réseau local

3. **Documentation:**
   - Mettre à jour `GUIDE_RAG.md` si intégration
   - Documenter l'utilisation hybride si les deux systèmes coexistent

---

## 🔗 RÉFÉRENCES

- **Serveur RAG Python:** `ChatAI-RAG-Server/README.md`
- **Configuration RAG:** `ChatAI-RAG-Server/CONFIGURATION_RAG.md`
- **RAG Android (Ollama):** `ChatAI-Android/docs/GUIDE_RAG.md`
- **Architecture RAG:** `ChatAI-Android/docs/RAG_SERVER_ARCHITECTURE.md`
- **Guide serveur:** `ChatAI-Android/docs/RAG_SERVER_GUIDE.md`

---

**Fin des notes de développement RAG**

