# 🔍 AUDIT COMPLET - Onglets Configuration

**Date**: 2025-11-27  
**Objectif**: Identifier TOUTES les incohérences, confusions et problèmes dans les onglets Configuration

---

## 🔴 PROBLÈMES CRITIQUES

### 1. ❌ TTS Error 500 - Serveur Indisponible

**Problème**:
- Statut serveur TTS affiche "Indisponible" avec erreur 500
- `OnnxTTSManager.initialize()` échoue (modèles ONNX manquants ou erreur de chargement)
- `TTSServer.java` retourne 500 car `isONNXReady()` = false

**Cause identifiée**:
- Les modèles ONNX TTS ne sont probablement pas présents aux chemins attendus
- Ou erreur lors du chargement ONNX Runtime
- Le serveur démarre mais `OnnxTTSManager` n'est pas prêt

**Fichiers concernés**:
- `TTSServer.java` ligne 226-228 : Retourne 503 si `isONNXReady()` = false
- `TTSServer.java` ligne 232-236 : Retourne 500 si synthèse échoue
- `OnnxTTSManager.kt` ligne 82-133 : Initialisation peut échouer silencieusement

**Solution**:
1. Vérifier présence des modèles ONNX sur device
2. Ajouter meilleure gestion d'erreurs avec messages clairs
3. Le serveur doit informer le webapp de l'état réel (ready/not ready)
4. Health check doit refléter l'état réel (`model_loaded` doit être dynamique)

---

### 2. ❌ Confusion "Local" PC vs Device dans onglet Général

**Problème**:
- L'onglet **Général** a un select "Mode actif" avec option "💻 Local"
- **AMBIGUÏTÉ** : Quel "Local" ?
  - Local = Ollama sur PC (comme défini dans onglet Local section 1) ?
  - Local = Modèles sur Device Android (comme défini dans onglet Local section 2) ?
  - Local = Les deux ?

**Aide actuelle** (ligne 121):
```
"Choisir le mode à utiliser (Cloud ou Local)"
```
❌ **Trop vague** - Ne précise pas quel "Local"

**Aide pour "Modèle Cloud par défaut"** (ligne 136):
```
"Modèle Cloud par défaut (utilisé si aucun modèle spécifié dans tab Cloud). Ignoré en mode Local (gemma fixé)."
```
⚠️ **Mentionne "gemma fixé"** mais ne précise pas si c'est PC ou Device

**Solution**:
1. Clarifier l'aide : "Local = Ollama sur PC (configuré dans onglet Local)"
2. Ou renommer : "Local (PC)" et "Local (Device)" séparément
3. Ajouter info-bulle explicative complète

---

## 🟡 PROBLÈMES MAJEURS

### 3. ⚠️ Onglet AV - Mélange de fonctionnalités non liées

**Problème**:
L'onglet **🎤 AV** contient **3 sections complètement différentes**:
1. **🔍 Web Search & Pensée** (lignes 405-429)
   - Provider Web Search (input texte)
   - Thinking trace (select)
2. **👁️ Vision** (lignes 431-456)
   - Modèle vision (select)
3. **🎤 Audio (STT)** (lignes 458-531)
   - Moteur STT, modèle audio, endpoint, timeout, etc.

**Pourquoi c'est problématique**:
- "AV" signifie Audio/Video, mais contient aussi Web Search et Vision
- Les utilisateurs ne savent pas où chercher quoi
- Logique de regroupement non claire
- Web Search & Pensée n'a rien à voir avec Audio/Video

**Solution**:
1. **Option A**: Séparer en 3 onglets distincts
   - 🎤 Audio (STT + TTS ?)
   - 👁️ Vision (déjà un onglet séparé ?)
   - 🔍 Web Search (nouvel onglet ou intégré ailleurs ?)
2. **Option B**: Renommer l'onglet "AV" → "Multimédia" et clarifier les sections
3. **Option C**: Réorganiser complètement la structure des onglets

---

### 4. ⚠️ Onglet TTS - Incomplet et manque d'aide

**Problèmes identifiés**:

#### 4.1 Input "Mode" sans aide (ligne 692-693)
```html
<label>Mode
    <input type="text" id="configTtsMode" placeholder="local">
</label>
```
❌ **Aucune aide contextuelle**:
- Qu'est-ce que "Mode" ?
- Que signifie "local" ?
- Quelles sont les valeurs possibles ?
- À quoi sert ce champ ?

#### 4.2 Select "Voix" sans aide (ligne 695-706)
```html
<label>Voix
    <select id="configTtsVoice">
        <option value="">– Choisir –</option>
        <option value="kitt">KITT</option>
        <option value="glados">GLaDOS</option>
        ...
    </select>
</label>
```
❌ **Aucune aide**:
- Ces voix sont-elles pour Android TTS ou ONNX TTS ?
- Comment sont-elles implémentées ?
- S'appliquent-elles seulement au fallback Android TTS ?

#### 4.3 Statut serveur TTS - Pas de diagnostic
- Affiche "Indisponible" mais ne dit pas **pourquoi**
- Pas de bouton pour voir les logs
- Pas de message d'erreur détaillé

#### 4.4 Configuration ONNX TTS - Statut modèles non vérifié
- Affiche "Encoder: -", "Decoder: -", "Vocoder: -"
- Mais ne vérifie pas réellement la présence des fichiers
- JavaScript `checkTtsServerStatus()` fait juste un health check, pas une vérification des fichiers

**Solution**:
1. Ajouter aides contextuelles pour tous les inputs
2. Améliorer diagnostic serveur TTS (erreurs détaillées)
3. Vérifier présence fichiers ONNX et afficher statut réel
4. Clarifier quand Android TTS vs ONNX TTS est utilisé

---

### 5. ⚠️ Onglet Avancé - Inputs sans contexte

#### 5.1 Textarea "Prompt KITT" (ligne 735-737)
```html
<label>Prompt KITT
    <textarea id="configPromptKitt" rows="2"></textarea>
</label>
```
❌ **Pas d'aide**:
- Qu'est-ce qu'un prompt système ?
- Format attendu ?
- Exemples ?

#### 5.2 Input "Max context tokens" (ligne 744-746)
```html
<label>Max context tokens
    <input type="number" id="configMaxContext" placeholder="8192">
</label>
```
❌ **Pas d'aide**:
- Qu'est-ce qu'un token ?
- Impact sur performance/mémoire ?
- Valeurs recommandées ?
- Différence avec "Max réponse tokens" ?

#### 5.3 Input "Max réponse tokens" (ligne 747-749)
```html
<label>Max réponse tokens
    <input type="number" id="configMaxResponse" placeholder="2048">
</label>
```
❌ **Mêmes problèmes que "Max context tokens"**

**Solution**:
1. Ajouter `<small>` avec aide contextuelle pour chaque champ
2. Ajouter liens vers documentation
3. Ajouter exemples de prompts

---

## 🟢 PROBLÈMES MINEURS

### 6. ⚠️ Inputs texte sans placeholder ni aide

#### Onglet Cloud:
- ✅ `configCloudApiKey` : Aide présente (ligne 171)
- ✅ `configCloudProvider` : OK (dropdown avec options)
- ⚠️ `configCloudProviderCustom` : Pas d'aide si "custom" sélectionné

#### Onglet Local:
- ✅ `configLocalUrl` : Aide présente (lignes 284-287)
- ✅ `configLocalModel` : Aide présente (lignes 311-313)

#### Onglet AV:
- ❌ `configWebSearchProvider` (ligne 411) : 
  - Placeholder: `"ollama"`
  - ❌ Pas d'aide : Qu'est-ce qu'un provider Web Search ? Format ? Exemples ?
- ❌ `configAudioEndpoint` (ligne 488) :
  - Placeholder: `"http://127.0.0.1:11400/inference"`
  - ❌ Pas d'aide : Pourquoi cet endpoint ? Doit-il être changé ? Quand ?
- ⚠️ `configAudioTimeout`, `configAudioSilenceDb`, etc. : Placeholders OK mais pas d'aide contextuelle

#### Onglet TTS:
- ❌ `configTtsMode` (ligne 693) : Placeholder `"local"` mais pas d'aide
- ✅ `configTtsVoice` : Select avec options, mais pas d'aide sur leur utilisation

#### Onglet Avancé:
- ❌ Tous les textarea prompts : Pas d'aide
- ❌ Tous les inputs tokens : Pas d'aide

---

### 7. ⚠️ Organisation incohérente des sections

#### Onglet Local:
- ✅ **Bien organisé** : 3 sections claires (PC, Device, RAG)
- ✅ Séparation visuelle avec borders
- ✅ Aides présentes

#### Onglet Cloud:
- ✅ **Bien organisé** : Tous les champs regroupés logiquement
- ✅ Zones de résultats séparées
- ✅ Actions groupées par type

#### Onglet AV:
- ❌ **Mal organisé** : 3 sections non liées mélangées
- ❌ Pas de séparation visuelle claire entre sections
- ❌ Nom d'onglet ambigu ("AV" pour Audio/Video mais contient Web Search)

#### Onglet TTS:
- ⚠️ **Organisation OK mais incomplète** :
  - Section ONNX TTS Server (OK)
  - Section Legacy Android TTS (OK)
  - Mais pas d'aide pour comprendre la différence

#### Onglet Hotword:
- ✅ **Bien organisé** : Configuration générale + Liste modèles

#### Onglet Avancé:
- ✅ **Organisation OK** : Prompts + Contraintes + JSON brut

---

## 📋 RÉSUMÉ DES PROBLÈMES PAR ONGLET

| Onglet | Problèmes Critiques | Problèmes Majeurs | Problèmes Mineurs |
|--------|-------------------|-------------------|-------------------|
| **⚙️ Général** | 1. Confusion Local PC/Device | - | - |
| **☁️ Cloud** | - | - | Provider custom sans aide |
| **💻 Local** | - | - | ✅ **BIEN FAIT** |
| **🎤 AV** | - | 1. Mélange 3 fonctionnalités non liées | Plusieurs inputs sans aide |
| **🔊 Hotword** | - | - | ✅ **BIEN FAIT** |
| **🗣️ TTS** | 1. Error 500 serveur indisponible | 1. Plusieurs inputs sans aide<br>2. Statut serveur non diagnostic | Mode/Voix sans contexte |
| **🔧 Avancé** | - | 1. Tous inputs prompts sans aide<br>2. Tokens sans contexte | - |

---

## 🎯 PLAN DE CORRECTION

### Phase 1: Fix Critiques (Priorité 1) ⚠️

#### 1.1 Fix TTS Error 500
- [ ] Vérifier présence modèles ONNX sur device
- [ ] Améliorer gestion erreurs `OnnxTTSManager`
- [ ] Health check dynamique avec statut réel
- [ ] Messages d'erreur détaillés dans webapp
- [ ] Diagnostic dans onglet TTS (bouton "Voir logs")

#### 1.2 Clarifier "Local" dans onglet Général
- [ ] Renommer aide : "Local (Ollama PC)" ou "Local (serveur PC Ollama)"
- [ ] Ajouter info-bulle explicative complète
- [ ] Lien vers onglet Local pour configurer

---

### Phase 2: Réorganisation (Priorité 2) 🔄

#### 2.1 Réorganiser onglet AV
- [ ] **Option recommandée**: Séparer en sections plus claires
  - Section 1: 🎤 Audio (STT)
  - Section 2: 🔍 Web Search & Pensée
  - Section 3: 👁️ Vision
- [ ] Ou renommer onglet "AV" → "Multimédia" avec sections séparées visuellement
- [ ] Ajouter séparateurs visuels entre sections

#### 2.2 Compléter onglet TTS
- [ ] Ajouter aide pour `configTtsMode` (expliquer valeurs possibles)
- [ ] Ajouter aide pour `configTtsVoice` (Android TTS vs ONNX)
- [ ] Améliorer diagnostic serveur (détails erreurs, logs)
- [ ] Vérifier fichiers ONNX et afficher statut réel

---

### Phase 3: Ajouter Aides Contextuelles (Priorité 3) 📝

#### 3.1 Onglet AV
- [ ] Aide `configWebSearchProvider` : Qu'est-ce que c'est, format, exemples
- [ ] Aide `configAudioEndpoint` : Quand modifier, format
- [ ] Aide `configAudioTimeout` : Impact, valeurs recommandées
- [ ] Aide `configAudioSilenceDb` : Ce que c'est, impact

#### 3.2 Onglet TTS
- [ ] Aide `configTtsMode` : Valeurs possibles, usage
- [ ] Aide `configTtsVoice` : Android TTS vs ONNX TTS, implémentation

#### 3.3 Onglet Avancé
- [ ] Aide textarea prompts : Format, exemples, utilisation
- [ ] Aide "Max context tokens" : Définition, impact, valeurs recommandées
- [ ] Aide "Max réponse tokens" : Définition, différence avec context tokens

#### 3.4 Onglet Cloud
- [ ] Aide `configCloudProviderCustom` : Format attendu si custom

---

## 📊 RÉSUMÉ DES ACTIONS

### Actions Immédiates (Aujourd'hui)
1. ✅ **Audit complet** (ce document)
2. ⏳ Fix TTS Error 500 (diagnostic et messages d'erreur)
3. ⏳ Clarifier "Local" dans onglet Général

### Actions Court Terme (Cette semaine)
4. ⏳ Réorganiser onglet AV (sections claires)
5. ⏳ Compléter onglet TTS (aides + diagnostic)
6. ⏳ Ajouter aides contextuelles (tous les inputs)

### Actions Moyen Terme (Semaine prochaine)
7. ⏳ Tests complets après corrections
8. ⏳ Documentation utilisateur mise à jour

---

## 🔍 QUESTIONS À RÉSOUDRE AVANT IMPLÉMENTATION

### Question 1: Structure onglet AV
**Question**: Faut-il séparer Web Search/Vision/Audio en onglets distincts ou garder un seul onglet avec sections ?

**Recommandation**: Garder onglet unique mais :
- Renommer "AV" → "Multimédia" ou "Audio & Vision"
- Sections bien séparées visuellement
- Chaque section avec son propre titre et aide

### Question 2: Onglet TTS - Mode et Voix
**Question**: À quoi servent `configTtsMode` et `configTtsVoice` exactement ?

**À clarifier**:
- `configTtsMode` : Est-ce utilisé ? Par qui ?
- `configTtsVoice` : Android TTS seulement ? Comment implémenté ?

### Question 3: Local PC vs Device
**Question**: Dans l'onglet Général, "Local" signifie-t-il :
- A) Ollama sur PC (comme onglet Local section 1) ?
- B) Modèles sur Device (comme onglet Local section 2) ?
- C) Les deux automatiquement ?

**Recommandation**: Clarifier = Ollama sur PC (A), car c'est ce qui est utilisé actuellement

---

## ✅ PROCHAINES ÉTAPES

1. **Discuter** avec l'utilisateur sur les questions ci-dessus
2. **Planifier** les corrections en détail
3. **Implémenter** les fixes Phase 1 (critiques)
4. **Tester** après chaque fix
5. **Continuer** avec Phase 2 et 3

---

**Document créé**: 2025-11-27  
**Dernière mise à jour**: 2025-11-27  
**Statut**: ✅ Audit complet terminé, prêt pour discussion et planification


