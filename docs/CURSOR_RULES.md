# Cursor Rules - ChatAI Project

**Usage:** Copier ces règles dans Cursor Settings → Rules

---

## 🔧 GIT WORKFLOW

### Convention de commits (OBLIGATOIRE):
- `feat:` → Nouvelle fonctionnalité
- `fix:` → Correction de bug
- `refactor:` → Refactorisation sans changement fonctionnel
- `docs:` → Documentation uniquement
- `chore:` → Maintenance (build, version, deps)
- `test:` → Tests
- `style:` → Formatage

**Format:** `<type>: <description courte> (max 50 chars)`

**Exemples:**
- ✅ `feat: Add QR scanner to drawer with ZXing`
- ✅ `fix: Fix API quota detection (HTTP 429)`
- ✅ `docs: Update README with new features`
- ❌ `update`, `fix bug`, `wip`

### Branches:
- `main` → Version stable, toujours fonctionnelle
- `dev/<feature-name>` → Features en développement
- `hotfix/<bug-name>` → Corrections urgentes

**Workflow:**
1. Créer branche: `git checkout -b dev/my-feature`
2. Développer et commiter régulièrement
3. Tester sur device avant merge
4. Merger dans `main` quand stable
5. Push vers `origin/main`

### Push régulier:
- Ne pas accumuler > 5 commits sans push
- Push après chaque feature complétée
- Git = backup automatique dans le cloud

---

## 🏗️ ARCHITECTURE V3 - MODULAR

### Structure obligatoire:
- **7 Managers spécialisés:**
  1. `KittAnimationManager` - VU-meter, Scanner, Thinking
  2. `KittTTSManager` - Text-to-Speech
  3. `KittVoiceManager` - Speech Recognition
  4. `KittMessageQueueManager` - Priority queue, Marquee
  5. `KittMusicManager` - MediaPlayer
  6. `KittStateManager` - 6 états système
  7. `KittDrawerManager` - Menu drawer

### Règles absolues:
- ✅ Chaque manager = UNE responsabilité unique
- ✅ Pas de dépendances circulaires
- ✅ Lifecycle-aware partout
- ✅ Thread-safe callbacks
- ✅ Pas de code monolithique dans KittFragment

### Fichiers critiques (ne PAS modifier sans tester):
- `KittAIService.kt` → Service IA principal (prompts, API calls)
- `KittFragment.kt` → Coordinateur principal
- Architecture V3 managers → Structure modulaire

---

## 🎯 DRAWER KITT

### Règles absolues:
- ❌ **PAS de boutons fictifs** (Turbo Boost, Mode Poursuite, etc.)
- ❌ **PAS de boutons "En développement"** sans fonction
- ✅ **TOUS les boutons** doivent ouvrir vraies fonctionnalités
- ✅ **Style KITT conservé** (rouge, sophistiqué, élégant)
- ✅ **Professionnel et commercialisable**

### Structure drawer:
- Section "DIAGNOSTIC & MONITORING" → Activités réelles
- Section "ACTIONS RAPIDES" → Utilitaires device
- Section "OUTILS DEVICE" → Capteurs, DND, SOS
- Section "NAVIGATION" → Google Maps, GPS
- Section "COMMUNICATION" → Contacts, Audio, Share

### Boutons transformés:
- System Status → Vraies infos device (batterie, RAM, stockage)
- GPS → Google Maps
- Contacts → App Contacts Android
- Audio → Paramètres audio Android
- Share → Share sheet Android
- DND → Do Not Disturb settings

---

## 🧠 ASSISTANT IA - REAL ASSISTANT SYSTEM

### Vision:
- **KITT = style vocal**, PAS un personnage fictif
- **Réponses factuelles** et utiles au quotidien
- **Transparent** sur capacités réelles et limitations
- **Commercialisable** et utilisable en contexte pro

### System Prompt règles:
- ✅ Mentionne vrai nom du modèle (qwen3-coder:480b, gpt-oss:120b)
- ✅ Explique limitations réelles
- ❌ Pas de prétention de systèmes fictifs
- ❌ Pas de "je suis KITT la voiture"

### Personnalités (style vocal uniquement):
- **KITT:** Professionnel, sophistiqué, courtois
- **GLaDOS:** Sarcastique, scientifique, passive-agressive
- **KARR:** Dominant, calculateur, auto-préservation

**Important:** Ce sont des styles de présentation, pas des limitations fonctionnelles.

---

## 📝 DOCUMENTATION

### OBLIGATOIRE pour chaque feature:
1. **Audit** (si modification existante) → `AUDIT_*.md`
2. **Plan** → `PLAN_*.md` avec phases claires
3. **Changelog** → `CHANGELOG_vX.Y.Z.md`
4. **Tests** → Documenter résultats validation

### Fichiers de référence:
- `HISTOIRE_ET_VISION_CHATAI.md` → Vision et histoire projet
- `docs/METHODOLOGIE_NOS_RULES.md` → Méthodologie complète
- `CONTRIBUTING.md` → Guide contributeurs
- `docs/GIT_WORKFLOW.md` → Workflow Git détaillé

### Journal de développement:
- Documenter décisions importantes dans `HISTOIRE_ET_VISION_CHATAI.md`
- Dater chaque décision majeure
- Citer utilisateur si décision importante

---

## 🧪 TESTS

### OBLIGATOIRE avant commit:
- ✅ Compiler sans erreurs: `./gradlew assembleDebug`
- ✅ Tester sur device physique (pas juste émulateur)
- ✅ Vérifier logs (pas d'erreurs critiques)
- ✅ Tester fonctionnalité modifiée

### Tests drawer:
- Ouvrir drawer KITT
- Tester chaque bouton modifié
- Vérifier navigation (BACK retourne à KITT)
- Vérifier dialogs affichent correctement

---

## 🚨 POINTS D'ATTENTION

### Ne JAMAIS:
- ❌ Simplifier sans comprendre pourquoi
- ❌ Copier-coller sans vérifier
- ❌ Modifier code critique sans tester
- ❌ Commit sans documentation
- ❌ Accumuler commits sans push

### Toujours:
- ✅ Recherche approfondie avant implémentation
- ✅ Lire specs officielles complètes
- ✅ Implémenter exactement à 100%
- ✅ Documenter décisions importantes
- ✅ Tester sur device réel

---

## 📊 VERSIONING

### Semantic Versioning (MAJOR.MINOR.PATCH):
- `4.6.0` → Version stable
- `4.6.1` → Bug fix
- `4.7.0` → Nouvelle feature
- `5.0.0` → Breaking change

### Fichiers à modifier:
- `app/build.gradle` → `versionCode` et `versionName`
- `KittAIService.kt` → `VERSION` constant

### Changelog:
- Créer `CHANGELOG_vX.Y.Z.md` pour chaque version
- Documenter toutes les nouvelles features
- Lister breaking changes

---

## 🎯 MÉTHODOLOGIE "NOS RULES"

### Les 3 étapes obligatoires:

1. **RECHERCHE APPROFONDIE**
   - Lire TOUTES les specs officielles
   - Étudier exemples concrets (30+ si possible)
   - Comprendre le POURQUOI, pas juste le COMMENT

2. **IMPLÉMENTATION EXACTE**
   - À 100% selon les spécifications
   - Pas de simplifications arbitraires
   - Pas de "ça devrait marcher" sans vérifier

3. **DOCUMENTATION COMPLÈTE**
   - Écrire ce qu'on a compris
   - Documenter décisions importantes
   - Créer références permanentes

**Résultat:** Code maintenable, 0 bugs, protection contre perte de travail.

---

## 📱 ANDROID SPECIFIC

### Permissions:
- Vérifier `AndroidManifest.xml` avant utiliser feature
- Demander permissions runtime si nécessaire
- Gérer fallback si permission refusée

### Lifecycle:
- Tous les managers doivent être Lifecycle-aware
- Respecter onResume/onPause/onDestroy
- Nettoyer resources correctement

### Thread-safety:
- Callbacks UI depuis background thread
- Utiliser `runOnUiThread` ou Coroutines
- Synchroniser accès aux états partagés

---

## 🎨 UI/UX

### Style KITT:
- Couleur rouge: `@color/kitt_red`
- Fond noir: `@color/kitt_black`
- Font monospace pour textes techniques
- Animations smooth (pas de lag)

### Thèmes:
- Rouge (par défaut)
- Sombre (dark)
- Ambre (amber)

### Responsive:
- Tester portrait et landscape
- Adapter layout selon orientation
- Gérer keyboard overlay

---

## ✅ CHECKLIST AVANT COMMIT

- [ ] Code compilé sans erreurs
- [ ] Testé sur device physique
- [ ] Logs vérifiés (pas d'erreurs critiques)
- [ ] Commit message suit convention
- [ ] Documentation mise à jour
- [ ] Version bumped si feature majeure
- [ ] Changelog créé si nouvelle version
- [ ] Tests drawer validés (si modifié)
- [ ] Architecture V3 respectée
- [ ] Pas de boutons fictifs ajoutés

---

**Ces règles sont OBLIGATOIRES pour maintenir la qualité et cohérence du projet ChatAI.**

