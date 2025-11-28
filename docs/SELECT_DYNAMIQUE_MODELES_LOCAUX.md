# 🔄 Select Dynamique pour Modèles Locaux Ollama

**Date**: 2025-11-27  
**Statut**: ✅ **IMPLÉMENTÉ**

---

## 🎯 OBJECTIF

Rendre le select des modèles locaux **dynamique** pour afficher automatiquement les modèles installés sur le serveur Ollama PC, au lieu d'une liste hardcodée.

---

## ✨ FONCTIONNALITÉS IMPLÉMENTÉES

### 1. Récupération automatique des modèles

**Fonction**: `listLocalModels()`
- Interroge l'API Ollama locale: `{baseUrl}/api/tags`
- Extrait l'URL de base depuis le champ `configLocalUrl`
- Convertit automatiquement l'URL (ajoute `http://` si nécessaire)
- Retire `/v1/chat/completions` pour obtenir l'URL de base

### 2. Peuplement dynamique du select

**Fonction**: `populateLocalModelDropdown(models)`
- Vide le select actuel
- Ajoute "– Choisir –" en première option
- Ajoute tous les modèles trouvés
- Ajoute "Autre (personnalisé)" à la fin
- Restaure la valeur sélectionnée si elle existe encore

### 3. Cache localStorage

**Fonctions**: 
- `saveLocalModelsToStorage(models)` - Sauvegarde la liste
- `loadLocalModelsFromStorage()` - Charge depuis le cache

**Durée de validité**: 24 heures (1 jour)

### 4. Interface utilisateur

**Bouton**: "🔄 Récupérer les modèles"
- Couleur violette pour le distinguer
- Affiche un indicateur de chargement
- Désactivé pendant la récupération

**Zone de résultats**: `localModelsResult`
- Affiche le statut (⏳, ✅, ❌)
- Affiche la liste des modèles trouvés (cliquables)
- Messages d'erreur détaillés

### 5. Chargement automatique

- Au chargement de la page, charge depuis le cache si disponible
- Si URL configurée, charge automatiquement depuis le serveur après 1 seconde
- Permet un affichage rapide même si le serveur est inaccessible

---

## 🔧 FONCTIONNEMENT

### Flux de chargement

1. **Au chargement de la page**:
   - Vérifie le cache localStorage
   - Si cache valide (< 24h), charge les modèles depuis le cache
   - Peuple le select avec les modèles en cache

2. **Si URL configurée**:
   - Attend 1 seconde (pour que la page soit chargée)
   - Appelle automatiquement `listLocalModels()`
   - Met à jour le select avec les modèles réels

3. **Clic sur "🔄 Récupérer les modèles"**:
   - Force la récupération depuis le serveur
   - Met à jour le select
   - Met à jour le cache

### Gestion des erreurs

- **Timeout**: 5 secondes maximum
- **Erreurs réseau**: Messages explicites
- **404**: "API Ollama non trouvée"
- **Timeout**: "Le serveur ne répond pas"
- **URL invalide**: "URL du serveur manquante"

---

## 📝 CODE AJOUTÉ

### Fonctions JavaScript

1. `listLocalModels()` - Fonction principale
2. `populateLocalModelDropdown(models)` - Peuplement du select
3. `saveLocalModelsToStorage(models)` - Sauvegarde cache
4. `loadLocalModelsFromStorage()` - Chargement cache
5. `selectLocalModelFromList(modelName)` - Sélection depuis la liste cliquable

### Éléments HTML

1. Bouton: `<button id="listLocalModelsBtn">`
2. Zone résultats: `<div id="localModelsResult">`
3. Statut: `<div id="localModelsStatus">`
4. Liste: `<div id="localModelsList">`

### Event Listeners

- Clic sur `listLocalModelsBtn` → `listLocalModels()`
- Chargement page → Charge depuis cache + appelle serveur si URL configurée

---

## 🎨 INTERFACE UTILISATEUR

### Bouton "🔄 Récupérer les modèles"

- **Couleur**: Violet (linear-gradient)
- **Texte**: "🔄 Récupérer les modèles"
- **État chargement**: "⏳ Récupération..." (désactivé)

### Zone de résultats

- **Couleur de fond**: `rgba(139, 92, 246, 0.1)` (violet clair)
- **Bordure**: `rgba(139, 92, 246, 0.3)` (violet)
- **Affichage**: Seulement quand nécessaire (display: none/block)

### Liste des modèles

- **Format**: Tags cliquables
- **Style**: Fond violet clair, border-radius
- **Action**: Cliquer sur un modèle → Sélectionne dans le select

---

## 🔍 EXEMPLE D'UTILISATION

### URL configurée

```
http://10.43.62.249:11434/v1/chat/completions
```

### Extraction de l'URL de base

```
http://10.43.62.249:11434
```

### Appel API

```
GET http://10.43.62.249:11434/api/tags
```

### Réponse JSON

```json
{
  "models": [
    {"name": "gemma3:270m", ...},
    {"name": "llama3.2:3b", ...},
    {"name": "gemma3:1b", ...}
  ]
}
```

### Modèles dans le select

```
– Choisir –
gemma3:270m
gemma3:1b
llama3.2:3b
Autre (personnalisé)
```

---

## ✅ AVANTAGES

1. **Liste toujours à jour**: Affiche les modèles réellement installés
2. **Plus besoin de hardcoder**: Les modèles sont découverts automatiquement
3. **Cache intelligent**: Évite les appels API répétés
4. **Interface claire**: Bouton et résultats visibles
5. **Gestion erreurs**: Messages explicites pour l'utilisateur

---

## 🔗 RÉFÉRENCES

- `ChatAI-Android/app/src/main/assets/webapp/index.html` - Implémentation
- `ChatAI-Android/docs/ORIGINE_MODELES_LOCAUX_OLLAMA.md` - Origine des modèles

---

## 🚀 PROCHAINES AMÉLIORATIONS POSSIBLES

1. **Rafraîchissement auto**: Recharger la liste toutes les X minutes
2. **Indicateur de taille**: Afficher la taille de chaque modèle
3. **Installation depuis l'app**: Bouton pour installer un modèle (nécessite API Ollama)
4. **Filtres**: Filtrer par taille, type, etc.


