# 🔍 AUDIT COMPLET - ONGLET CONFIGURATION

**Date:** 2025-11-27  
**Objectif:** Identifier toutes les incohérences, confusions et éléments manquants dans l'onglet Configuration

---

## 📋 RÉSUMÉ EXÉCUTIF

L'audit révèle **plusieurs problèmes critiques** de clarté, d'organisation et de cohérence dans l'onglet Configuration:

1. **Confusion Local PC vs Device** dans l'onglet Général
2. **Organisation incohérente** de l'onglet AV (mélange de fonctionnalités)
3. **Configuration legacy mélangée** dans l'onglet TTS
4. **Aides contextuelles manquantes** pour de nombreux champs
5. **Incohérences de terminologie** entre les onglets

---

## 🔴 PROBLÈMES CRITIQUES

### 1. **Onglet Général - Confusion "Local"**

**Ligne 116-121:**
```html
<label>Mode actif
    <select id="configModeSelect">
        <option value="cloud">☁️ Cloud</option>
        <option value="local">💻 Local</option>
    </select>
    <small>Choisir le mode à utiliser (Cloud ou Local)</small>
</label>
```

**Problème:**
- Le label "Local" est ambigu: fait-il référence à "Local PC" (Ollama sur PC) ou "Local Device" (modèles sur Android)?
- L'utilisateur ne sait pas quel "Local" est sélectionné
- Le commentaire ligne 136 mentionne "Ignoré en mode Local (gemma fixé)" - mais quel Local?

**Impact:** Confusion majeure pour l'utilisateur

**Recommandation:**
- Clarifier: "Local" = "Local Device" par défaut (modèles sur Android)
- OU renommer en "Local Device" explicitement
- OU séparer en deux modes: "Cloud", "Local PC", "Local Device"

---

### 2. **Onglet AV - Organisation incohérente**

**Lignes 403-530:**
- **Section 1:** Web Search & Thinking (lignes 406-429)
- **Section 2:** Vision (lignes 432-456)
- **Section 3:** Audio/STT (lignes 458-530)

**Problèmes:**
1. **Web Search n'est PAS de l'audio/vidéo** - pourquoi dans l'onglet "AV"?
2. **Thinking trace n'est PAS de l'audio/vidéo** - pourquoi dans l'onglet "AV"?
3. **Vision** est correctement placée
4. **STT (Audio)** est correctement placée
5. **Pas de section claire** pour chaque fonctionnalité

**Impact:** L'utilisateur ne sait pas où chercher quoi

**Recommandation:**
- **Option A:** Renommer l'onglet "AV" en "Multimédia & Recherche"
- **Option B:** Séparer en deux onglets:
  - "🎤 Audio/STT" (STT uniquement)
  - "👁️ Vision" (Vision uniquement)
  - Déplacer "Web Search & Thinking" dans "Avancé" ou créer un onglet "🔍 Recherche"

---

### 3. **Onglet TTS - Configuration legacy mélangée**

**Lignes 642-728:**
- **Section 1:** ONNX TTS Server (lignes 646-678) ✅ Moderne
- **Section 2:** Test TTS (lignes 680-688) ✅ Moderne
- **Section 3:** Configuration legacy Android TTS (lignes 690-717) ⚠️ Legacy

**Problèmes:**
1. **Configuration legacy Android TTS** (lignes 691-717) est toujours présente mais non utilisée
2. **Champs "Mode" et "Voix"** (lignes 692-706) ne sont pas clairs - sont-ils pour ONNX ou Android TTS?
3. **Pas de séparation visuelle** entre ONNX et Android TTS

**Impact:** Confusion sur quelle configuration est active

**Recommandation:**
- **Option A:** Supprimer complètement la section legacy Android TTS (lignes 690-717)
- **Option B:** Déplacer Android TTS dans une section "Legacy (non recommandé)" avec un avertissement
- **Option C:** Clarifier que "Mode" et "Voix" sont pour Android TTS uniquement (fallback)

---

## 🟡 PROBLÈMES MAJEURS

### 4. **Inputs sans aide contextuelle**

**Champs problématiques identifiés:**

#### a) **Onglet Cloud:**
- `configCloudApiKey` (ligne 170): Aide présente ✅
- `configCloudModel` (ligne 175): Aide présente ✅

#### b) **Onglet Local:**
- `configLocalUrl` (ligne 283): Aide présente ✅
- `configLocalModel` (ligne 303): Aide présente ✅

#### c) **Onglet AV:**
- `configWebSearchProvider` (ligne 411): **Aide partielle** - "laisser vide pour désactiver" mais pas d'exemple
- `configAudioEndpoint` (ligne 488): **Aucune aide** - l'utilisateur ne sait pas quoi mettre
- `configAudioTimeout` (ligne 492): **Aucune aide** - l'utilisateur ne sait pas quelle valeur
- `configAudioSilenceDb` (ligne 496): **Aucune aide** - l'utilisateur ne sait pas quelle valeur
- `configAudioSilenceMs` (ligne 500): **Aucune aide** - l'utilisateur ne sait pas quelle valeur
- `configAudioDelayAfterHotword` (ligne 504): Aide présente ✅

#### d) **Onglet TTS:**
- `configTtsMode` (ligne 693): **Aucune aide** - l'utilisateur ne sait pas quoi mettre
- `configTtsVoice` (ligne 696): **Aucune aide** - l'utilisateur ne sait pas quelle voix choisir

#### e) **Onglet Avancé:**
- `configPromptKitt` (ligne 736): **Aucune aide** - l'utilisateur ne sait pas quoi mettre
- `configPromptGlados` (ligne 739): **Aucune aide**
- `configPromptKarr` (ligne 742): **Aucune aide**
- `configMaxContext` (ligne 745): **Aucune aide** - l'utilisateur ne sait pas quelle valeur
- `configMaxResponse` (ligne 748): **Aucune aide** - l'utilisateur ne sait pas quelle valeur

**Impact:** L'utilisateur ne sait pas comment remplir ces champs

**Recommandation:**
- Ajouter des `<small>` avec exemples et valeurs recommandées pour TOUS les champs

---

### 5. **Incohérences de terminologie**

**Problèmes identifiés:**

1. **"Local"** utilisé de manière inconsistante:
   - Onglet Général: "Local" (ambigu)
   - Onglet Local: "Serveur Ollama (PC)" et "Modèles Locaux (Device)" (clair)
   - Onglet TTS: "Mode local" (ligne 693) - quel local?

2. **"Mode"** utilisé de manière inconsistante:
   - Onglet Général: "Mode actif" (Cloud/Local)
   - Onglet TTS: "Mode" (ligne 693) - quel mode?

3. **"Provider"** vs **"Moteur"**:
   - Onglet Cloud: "Provider"
   - Onglet AV: "Moteur STT"
   - Onglet TTS: "Moteur TTS"
   - Onglet Hotword: "Moteur"

**Impact:** Confusion sur la terminologie

**Recommandation:**
- Standardiser la terminologie:
  - **"Provider"** = Service cloud (Ollama, Hugging Face, etc.)
  - **"Moteur"** = Implémentation technique (ONNX, Android, Whisper, etc.)
  - **"Local"** = Toujours préciser "Local PC" ou "Local Device"

---

## 🟢 PROBLÈMES MINEURS

### 6. **Placement des boutons de sauvegarde**

**Problème:**
- Chaque section a son propre bouton "Sauvegarder"
- L'utilisateur ne sait pas s'il doit sauvegarder chaque section séparément ou tout en une fois

**Recommandation:**
- Ajouter un bouton "💾 Sauvegarder toute la configuration" en haut de l'onglet Configuration
- Garder les boutons individuels pour des sauvegardes partielles

---

### 7. **Manque de feedback visuel**

**Problème:**
- Pas de confirmation après sauvegarde
- Pas d'indication si une valeur a changé depuis la dernière sauvegarde

**Recommandation:**
- Ajouter des toasts/notifications après sauvegarde
- Ajouter des indicateurs visuels pour les champs modifiés

---

## 📊 TABLEAU RÉCAPITULATIF

| Onglet | Problème | Priorité | Ligne(s) |
|--------|----------|----------|----------|
| Général | Confusion "Local" PC vs Device | 🔴 Critique | 116-121 |
| AV | Web Search & Thinking mal placés | 🔴 Critique | 406-429 |
| AV | Inputs sans aide (Endpoint, Timeout, etc.) | 🟡 Majeur | 488, 492, 496, 500 |
| TTS | Configuration legacy mélangée | 🔴 Critique | 690-717 |
| TTS | Inputs sans aide (Mode, Voix) | 🟡 Majeur | 693, 696 |
| Avancé | Inputs sans aide (Prompts, Tokens) | 🟡 Majeur | 736, 739, 742, 745, 748 |
| Tous | Incohérences terminologie | 🟡 Majeur | Multiple |

---

## ✅ RECOMMANDATIONS PRIORITAIRES

### Phase 1 - Corrections critiques (immédiat)
1. ✅ Clarifier "Local" dans l'onglet Général
2. ✅ Réorganiser l'onglet AV (séparer Web Search/Thinking)
3. ✅ Nettoyer l'onglet TTS (supprimer ou clarifier legacy)

### Phase 2 - Améliorations majeures (court terme)
4. ✅ Ajouter aides contextuelles pour TOUS les inputs
5. ✅ Standardiser la terminologie

### Phase 3 - Améliorations mineures (moyen terme)
6. ✅ Améliorer feedback visuel
7. ✅ Optimiser placement des boutons de sauvegarde

---

## 🎯 PROCHAINES ÉTAPES

1. **Discussion avec l'utilisateur** sur les recommandations
2. **Planification** des corrections
3. **Implémentation** par phases

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


