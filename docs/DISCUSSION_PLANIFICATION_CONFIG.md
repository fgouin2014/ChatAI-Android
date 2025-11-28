# 💬 Discussion & Planification - Refonte Configuration

**Date**: 2025-11-27  
**Objectif**: Discuter et planifier les corrections après audit complet

---

## 📋 RÉSUMÉ DE LA DISCUSSION JUSQU'ICI

### ✅ Points Clarifiés

1. **Stockage des données**:
   - ✅ **100% LOCAL** : Toutes les conversations dans Room DB (SQLite)
   - ✅ **Aucun stockage cloud** : Ollama Cloud et Hugging Face ne stockent pas
   - ⚠️ **Transit cloud** : Données envoyées pour traitement seulement

2. **Local PC - Utilité limitée**:
   - Créé avant l'intégration Cloud
   - Ajoute confusion dans le code
   - Seulement utilisable à la maison (pas portable)
   - Cloud fait la même chose et plus (partout, simple, performant)

3. **Local = Device par défaut**:
   - "Local" dans l'onglet Général = Device (modèles sur Android)
   - Local PC et Local Device ne vont pas ensemble
   - Local PC n'est peut-être pas à garder

---

## 🎯 DÉCISIONS À PRENDRE

### Décision 1: Local PC - Garder ou Retirer ?

**Option A: RETIRER** ❌
- ✅ Code plus simple
- ✅ Moins de confusion
- ✅ Cloud couvre les mêmes besoins (et plus)
- ✅ Focus sur Cloud + Local Device
- ❌ Perd privacy (données cloud)
- ❌ Perd utilisation offline avec hotspot

**Option B: DÉPRÉCIER** ⚠️
- ✅ Compatibilité pour utilisateurs existants
- ✅ Garde fonctionnalité pour cas spéciaux (privacy stricte)
- ✅ Mais clairement marqué comme "Legacy/Avancé"
- ⚠️ Toujours ajoute de la complexité

**Recommandation basée sur vos feedback**:
- Vous avez dit: "je ne suis même pas sur de le garder"
- Vous avez dit: "ajoute de la confusion dans les codes"
- Vous avez dit: "c'est quoi son utilité si je ne peux juste l'utiliser a la maison"

→ **Option A (RETIRER)** semble correspondre à votre vision

**Question pour vous**: Privacy est-elle critique pour vous ? Si OUI → Option B, si NON → Option A

---

### Décision 2: Organisation Onglet AV

**Situation actuelle**:
- Onglet "🎤 AV" contient 3 sections non liées:
  1. 🔍 Web Search & Pensée
  2. 👁️ Vision
  3. 🎤 Audio (STT)

**Problème**: Nom ambigu, mélange de fonctionnalités

**Options**:

**Option A: Séparer en onglets distincts**
- Nouvel onglet "🔍 Web Search" (Web Search + Thinking)
- Onglet "👁️ Vision" (déjà existe ?)
- Onglet "🎤 Audio" → Renommer "🎤 Audio & TTS" (STT + TTS ensemble)

**Option B: Garder onglet unique mais réorganiser**
- Renommer "🎤 AV" → "📱 Multimédia" ou "🔊 Audio & Vision"
- Sections bien séparées visuellement
- Chaque section avec titre clair

**Recommandation**: **Option B** (plus simple, moins de changement)

**Question pour vous**: Préférez-vous onglets séparés ou onglet unique avec sections claires ?

---

### Décision 3: Compléter Onglet TTS

**Problèmes identifiés**:
1. Error 500 serveur indisponible (modèles ONNX manquants/erreur)
2. Inputs sans aide ("Mode", "Voix")
3. Diagnostic serveur insuffisant

**Actions nécessaires**:
1. Fix Error 500 (diagnostic + messages d'erreur clairs)
2. Ajouter aides contextuelles pour tous les inputs
3. Améliorer vérification statut serveur (fichiers ONNX présents)

**Question pour vous**: Priorité fix Error 500 maintenant ou continuer discussion d'abord ?

---

### Décision 4: Onglet Général - Clarifier "Local"

**Situation actuelle**:
- Select "Mode actif": "☁️ Cloud" ou "💻 Local"
- "Local" est ambigu (PC ou Device ?)

**Si on RETIRE Local PC**:
- "Local" = Device uniquement
- Aide claire: "Local = Modèles sur Device Android"

**Si on GARDE Local PC**:
- Séparer: "Local Device" et "Local PC" (ou marquer PC comme avancé)
- Clarifier dans l'aide

**Question pour vous**: Selon décision Local PC (Décision 1), comment organiser l'onglet Général ?

---

## 🎯 PLAN D'ACTION PROPOSÉ

### Phase 1: Décisions (Maintenant)

1. **Décision Local PC**: Retirer (A) ou Déprécier (B) ?
2. **Décision Onglet AV**: Séparer (A) ou Réorganiser (B) ?
3. **Décision Priorité TTS**: Fix Error 500 maintenant ou après discussion ?

### Phase 2: Implémentation (Cette semaine)

Selon décisions Phase 1:

#### Si Retirer Local PC:
- Retirer code `local_server_url` / Ollama PC
- Supprimer section "Serveur Ollama (PC)" de l'onglet Local
- Clarifier "Local" = Device dans onglet Général
- Nettoyer code legacy

#### Si Déprécier Local PC:
- Marquer comme "Legacy/Avancé"
- Ajouter avertissement
- Cacher dans section "Expert"
- Garder fonctionnalité

#### Réorganisation Onglet AV:
- Renommer et réorganiser sections
- Ou séparer en onglets distincts
- Ajouter aides contextuelles

#### Fix Onglet TTS:
- Diagnostic Error 500
- Vérification fichiers ONNX
- Messages d'erreur clairs
- Ajouter aides contextuelles

### Phase 3: Améliorations UX

- Ajouter aides contextuelles pour tous les inputs
- Améliorer messages d'aide
- Tests complets

---

## ❓ QUESTIONS POUR CONTINUER

### Question 1: Privacy
**Votre privacy est-elle critique ?**
- Si OUI → Garder Local PC (déprécié) pour cas spéciaux
- Si NON → Retirer Local PC (simplifier)

### Question 2: Organisation AV
**Préférez-vous**:
- Onglets séparés (plus clair mais plus d'onglets)
- Onglet unique réorganisé (plus simple, moins de changement)

### Question 3: Priorité
**Quelle est votre priorité ?**
1. Fix TTS Error 500 (urgent)
2. Clarifier Local PC/Device (important)
3. Réorganiser onglets (amélioration UX)

### Question 4: Utilisation Local PC
**Utilisez-vous actuellement Local PC ?**
- Si OUI → À garder (au moins déprécié)
- Si NON → On peut retirer

---

## 💡 RECOMMANDATIONS BASÉES SUR VOS FEEDBACK

### Recommandation 1: Retirer Local PC ✅

**Basé sur**:
- "je ne suis même pas sur de le garder"
- "ajoute de la confusion dans les codes"
- "utilité limitée (seulement à la maison)"
- "Cloud fait la même chose et plus"

**Exception**: Si privacy est critique → Déprécier au lieu de retirer

### Recommandation 2: Réorganiser Onglet AV (pas séparer) ✅

**Basé sur**:
- Moins de changement
- Plus simple à implémenter
- Suffisant si bien organisé

**Action**: Renommer "AV" → "Multimédia", sections claires

### Recommandation 3: Fix TTS Error 500 en priorité ⚠️

**Basé sur**:
- Problème critique (ne fonctionne pas)
- Bloque utilisation TTS
- Fix relativement rapide (diagnostic + messages)

---

## 🎯 PROCHAINE ÉTAPE SUGGÉRÉE

### Option A: Décisions d'abord (Recommandé)
1. Répondre aux 4 questions ci-dessus
2. Prendre décisions sur Local PC, AV, TTS
3. Planifier implémentation détaillée
4. Implémenter selon plan

### Option B: Fix TTS d'abord
1. Fix Error 500 TTS (diagnostic, messages d'erreur)
2. Continuer discussion après
3. Implémenter corrections configuration

**Quelle approche préférez-vous ?**

---

## 📊 RÉSUMÉ DES PROBLÈMES IDENTIFIÉS

### 🔴 Critiques (À fixer rapidement)
1. ❌ TTS Error 500 - Serveur indisponible
2. ❌ Confusion "Local" PC vs Device dans onglet Général

### 🟡 Majeurs (À corriger)
3. ⚠️ Onglet AV mal organisé (3 sections non liées)
4. ⚠️ Onglet TTS incomplet (inputs sans aide, diagnostic insuffisant)
5. ⚠️ Onglet Avancé (prompts/tokens sans contexte)

### 🟢 Mineurs (Améliorations UX)
6. ⚠️ Nombreux inputs sans aide contextuelle
7. ⚠️ Messages d'aide parfois vagues

---

**Document créé**: 2025-11-27  
**Dernière mise à jour**: 2025-11-27  
**Statut**: ✅ Discussion en cours, prêt pour décisions


