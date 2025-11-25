# Audit Nos Rules - Interface Webapp Configuration

## Date: 2025-01-24

## Problèmes identifiés

### 1. Séparation incohérente General/Cloud
**Problème:** L'onglet "General" contient uniquement le sélecteur de mode (Cloud/Local) et un modèle par défaut. C'est le point d'entrée logique pour la configuration Cloud, mais il est séparé de l'onglet Cloud.

**Solution:** Fusionner l'onglet General avec Cloud. Le sélecteur de mode devient le point d'entrée principal de l'onglet Cloud.

### 2. Spinner Provider mélangé avec providers non intégrés
**Problème:** Le spinner `configCloudProvider` contient des providers non intégrés:
- OpenAI (non intégré)
- Groq (non intégré)
- Perplexity (non intégré)

**Providers réellement intégrés:**
- `ollama` (Ollama Cloud) - Provider principal pour mode Cloud
- `huggingface` - Utilisé uniquement pour embeddings RAG, pas comme provider principal

**Solution:** Le spinner provider doit être conditionnel selon le mode:
- **Mode "local"**: Pas de provider (ou afficher "Local" en lecture seule)
- **Mode "cloud"**: Provider = "ollama" uniquement (Hugging Face est une option d'embedding, pas un provider)

### 3. Structure logique à implémenter

```
Onglet Cloud (fusionné avec General):
├── Mode actif (Cloud/Local) ← Point d'entrée principal
├── Si Mode = "Cloud":
│   ├── Provider: Ollama (fixé, pas de choix)
│   ├── Clé API Ollama Cloud
│   ├── Modèle Cloud (chargé dynamiquement)
│   ├── Hugging Face (optionnel, pour embeddings RAG):
│   │   ├── Clé API Hugging Face
│   │   └── Modèle d'embedding Hugging Face
│   └── Tests de connexion
└── Si Mode = "Local":
    └── (Redirection vers onglet Local ou masquage)

Onglet Local:
├── URL serveur Ollama local
├── Modèle local (gemma3-270m.gguf, fixé)
└── Configuration RAG (Ollama Local)
```

## Architecture proposée

### Structure des onglets
1. **Cloud** (ancien General + Cloud fusionnés)
   - Mode actif (Cloud/Local)
   - Configuration Cloud conditionnelle
   - Hugging Face (embeddings RAG) conditionnel

2. **Local** (inchangé)
   - Serveur Ollama local
   - Modèle local fixé
   - RAG Ollama Local

3. **Audio, Hotword, TTS, Advanced** (inchangés)

### Logique conditionnelle

```javascript
// Mode Cloud
if (mode === "cloud") {
    show:
        - Provider: "ollama" (fixé, non modifiable)
        - Clé API Ollama Cloud
        - Modèle Cloud (dynamique)
        - Section Hugging Face (optionnel)
    hide:
        - Configuration Local
}

// Mode Local
if (mode === "local") {
    show:
        - Message: "Configuration Local dans onglet Local"
    hide:
        - Toute configuration Cloud
}
```

## Implémentation

### Étape 1: Fusion General/Cloud
- Supprimer l'onglet "General"
- Déplacer le sélecteur de mode en haut de l'onglet Cloud
- Adapter les listeners et la logique de sauvegarde

### Étape 2: Refondre spinner Provider
- Supprimer les providers non intégrés (OpenAI, Groq, Perplexity)
- Fixer provider à "ollama" en mode Cloud
- Masquer le spinner en mode Local

### Étape 3: Logique conditionnelle
- Afficher/masquer les sections selon le mode
- Gérer la sauvegarde conditionnelle
- Adapter les tests de connexion

## Tests à effectuer

1. ✅ Mode Cloud: Affichage correct de la configuration Ollama
2. ✅ Mode Local: Masquage de la configuration Cloud
3. ✅ Sauvegarde: Persistance correcte selon le mode
4. ✅ Hugging Face: Affichage conditionnel (uniquement si clé API configurée)
5. ✅ Tests de connexion: Fonctionnent selon le provider sélectionné

