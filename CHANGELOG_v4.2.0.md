# Changelog v4.2.0 - Ollama Web Search + Serveur RAG séparé

**Date:** 5 novembre 2025  
**Type:** Feature majeure + Refactoring architecture

---

## 🌐 Ollama Web Search

### Nouvelle fonctionnalité

ChatAI/KITT peut maintenant effectuer des recherches web en temps réel via Ollama Web Search API.

#### Fonctionnement automatique

Le système détecte automatiquement si une question nécessite une recherche web basée sur :

- **Mots-clés déclencheurs :**
  - `recherche`, `search`, `trouve`, `cherche`
  - `actualité`, `news`, `dernière`, `dernier`
  - `météo`, `weather`, `température`
  - `prix`, `price`, `coûte`, `cost`
  - `où acheter`, `where to buy`
  - `résultat`, `score`, `match`
  - `bourse`, `stock`, `action`, `bitcoin`, `crypto`

- **Questions factuelles :**
  - Questions commençant par "Quel", "Combien", "Qui", "What", "How much", "Who"
  - (Sauf les questions sur l'heure, déjà gérées par Function Calling)

#### Intégration

- Web Search activé automatiquement dans Ollama Cloud si détecté
- Citations web extraites et ajoutées au thinking trace
- Format des citations :
  ```
  📚 Sources:
    1. Titre de la source - URL
    2. Titre de la source - URL
  ```

#### Code modifié

- `KittAIService.kt` :
  - `needsWebSearch()` - Détecte si web search nécessaire
  - `tryOllamaCloud()` - Ajout paramètre `tools: [{"type": "web_search"}]`
  - Extraction des citations dans la réponse JSON

#### Exemples d'utilisation

```
User: "Quel est le prix du Bitcoin aujourd'hui ?"
KITT: [Recherche web automatique] "D'après mes sources..."
      📚 Sources:
        1. CoinMarketCap - https://coinmarketcap.com/...
        2. CoinGecko - https://www.coingecko.com/...
```

```
User: "Quelle est la météo à Paris ?"
KITT: [Recherche web automatique] "D'après les dernières données..."
```

---

## 🧠 Serveur RAG - Projet séparé

### Refactoring architecture

Le serveur RAG Python a été déplacé dans un projet séparé pour améliorer la maintenabilité.

#### Nouveau projet

**Emplacement :** `C:\androidProject\ChatAI-RAG-Server\`

**Structure :**
```
ChatAI-RAG-Server/
├── rag_server.py          # Serveur Flask principal
├── requirements.txt       # Dépendances Python
├── start_rag_server.ps1   # Script de démarrage Windows
├── README.md              # Documentation complète
└── .gitignore            # Ignore cache/models
```

#### Améliorations

- Variables d'environnement configurables (`RAG_PORT`, `EMBEDDING_MODEL`)
- Gestion d'erreurs améliorée
- Documentation complète
- Script de démarrage avec vérifications automatiques
- Structure prête pour Git (projet indépendant)

#### Migration

- `setup_chatai_pc_server.ps1` mis à jour pour pointer vers le nouveau projet
- Ancien `chatai_rag_server.py` renommé en `.old` (backup)
- Fichier `RAG_SERVER_MOVED.txt` créé pour guider les utilisateurs

#### Utilisation

```powershell
cd C:\androidProject\ChatAI-RAG-Server
.\start_rag_server.ps1
```

Ou via setup complet :
```powershell
cd C:\androidProject\ChatAI-Android-beta
.\setup_chatai_pc_server.ps1
```

---

## 📝 Modifications des fichiers

### Modifiés

- `ChatAI-Android/app/src/main/java/com/chatai/services/KittAIService.kt`
  - Ajout `needsWebSearch()` (36 lignes)
  - Modification `tryOllamaCloud()` (ajout tools + parsing citations)
  - Version mise à jour : `4.2.0`

- `setup_chatai_pc_server.ps1`
  - Pointe vers `ChatAI-RAG-Server/start_rag_server.ps1`
  - Fallback vers ancien script si présent

### Créés

- `C:\androidProject\ChatAI-RAG-Server\` (projet complet)
  - `rag_server.py` (330 lignes)
  - `requirements.txt`
  - `start_rag_server.ps1` (90 lignes)
  - `README.md` (200+ lignes)
  - `.gitignore`

- `RAG_SERVER_MOVED.txt` (note de migration)

### Renommés

- `chatai_rag_server.py` → `chatai_rag_server.py.old` (backup)

---

## 🧪 Tests requis

### Web Search

1. Tester avec question nécessitant recherche web
   ```
   "Quel est le prix du Bitcoin ?"
   "Quelle est la météo à Montréal ?"
   "Qui a gagné le match hier ?"
   ```

2. Vérifier que les citations apparaissent dans le thinking trace
   - Menu → Historique → Voir le raisonnement

3. Vérifier que ça ne s'active PAS pour les questions normales
   ```
   "Quelle heure est-il ?"  → Function Calling (pas web search)
   "Comment vas-tu ?"       → Réponse normale (pas web search)
   ```

### Serveur RAG

1. Lancer le nouveau serveur RAG séparé
   ```powershell
   cd C:\androidProject\ChatAI-RAG-Server
   .\start_rag_server.ps1
   ```

2. Vérifier qu'il démarre correctement
3. Tester l'endpoint `/status` depuis le PC
   ```bash
   curl http://localhost:8890/status
   ```

---

## 📊 Statistiques

- **Lignes de code ajoutées :** ~700 (dont 330 pour RAG Server)
- **Fichiers modifiés :** 2
- **Fichiers créés :** 6 (nouveau projet)
- **Compilation :** ✅ Réussie (93 tâches, 21s)
- **Linter :** ✅ Aucune erreur

---

## 🔗 Références

- **Ollama Web Search Docs :** https://docs.ollama.com/capabilities/web-search
- **ChatAI RAG Server :** `C:\androidProject\ChatAI-RAG-Server\README.md`

---

## ⏭️ Prochaines étapes

- Tester Web Search avec le device
- Intégrer RAG Server dans l'app Android (appel HTTP)
- Ajouter interface pour activer/désactiver Web Search
- Ajouter caching des résultats web

---

**Version précédente :** v4.1.0 (V2 unique + Power ON by default)  
**Version actuelle :** v4.2.0 (Ollama Web Search + RAG Server séparé)

