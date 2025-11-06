# Cursor Commands - ChatAI Project

**Usage:** Créer ces commandes dans Cursor → Commands (ou utiliser comme référence)

---

## 🔍 GIT COMMANDS

### Check Git Status
```bash
# Voir état actuel + 5 derniers commits
git status && git log --oneline -5
```

**Alias suggéré:** `/check-git`

---

### Check Git Branches
```bash
# Lister toutes les branches avec dernier commit
git branch -v
```

**Alias suggéré:** `/check-branches`

---

### Compare Branches
```bash
# Comparer main avec branche actuelle
git diff main..HEAD --stat
```

**Alias suggéré:** `/compare-main`

---

### Show Commit Content
```bash
# Voir contenu d'un commit spécifique
git show <commit-hash>:<file-path>
```

**Exemple:** `git show 540549b:CHANGELOG_v4.6.0.md`

**Alias suggéré:** `/show-commit`

---

### Recover File from Git
```bash
# Récupérer fichier depuis un commit
git checkout <commit-hash> -- <file-path>
```

**Alias suggéré:** `/recover-file`

---

## 🏗️ BUILD COMMANDS

### Build Debug APK
```bash
cd ChatAI-Android && ./gradlew assembleDebug
```

**Alias suggéré:** `/build-debug`

---

### Build and Install
```bash
cd ChatAI-Android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Alias suggéré:** `/build-install`

---

### Clean Build
```bash
cd ChatAI-Android && ./gradlew clean && ./gradlew assembleDebug
```

**Alias suggéré:** `/clean-build`

---

### Build Release
```bash
cd ChatAI-Android && ./gradlew assembleRelease
```

**Alias suggéré:** `/build-release`

---

## 🧪 TEST COMMANDS

### Test Drawer Buttons
```markdown
# Checklist complète des boutons drawer:

## Section "DIAGNOSTIC & MONITORING"
- [ ] 🔍 DIAGNOSTIC API → AIConfigurationActivity
- [ ] 📊 MONITORING SERVEURS → ServerActivity
- [ ] ⚙️ CONFIG SERVEURS → ServerConfigurationActivity
- [ ] 📋 ENDPOINTS API → EndpointsListActivity
- [ ] 💬 HISTORIQUE → ConversationHistoryActivity

## Section "ACTIONS RAPIDES"
- [ ] ACTIVER VOCAL → VoiceListenerActivity
- [ ] INFOS SYSTÈME → Dialog (batterie, RAM, stockage)
- [ ] SCANNER QR → Info dialog (ZXing prévu)

## Section "OUTILS DEVICE"
- [ ] CAPTEURS → Dialog SensorManager
- [ ] NE PAS DÉRANGER → DND settings
- [ ] CONTACTS SOS → App Contacts

## Section "NAVIGATION"
- [ ] OUVRIR MAPS → Google Maps
- [ ] PARTAGER GPS → Share sheet
- [ ] NAVIGATION → Recherche Maps

## Section "COMMUNICATION"
- [ ] CONTACTS → App Contacts
- [ ] AUDIO → Paramètres audio
- [ ] PARTAGER → Share sheet

## Vérifications
- [ ] Boutons fictifs invisibles (Turbo Boost, Pursuit Mode)
- [ ] Navigation propre (BACK retourne à KITT)
- [ ] Aucun crash
- [ ] Dialogs affichent correctement
```

**Alias suggéré:** `/test-drawer`

---

### Test API Connections
```bash
# Ouvrir app et tester via bouton "Diagnostic API"
# Vérifier logs dans /storage/emulated/0/ChatAI-Files/logs/
adb logcat | Select-String "API_TEST"
```

**Alias suggéré:** `/test-api`

---

### Test Logs
```bash
# Filtrer logs ChatAI
adb logcat | Select-String "ChatAI|KITT|API_TEST"
```

**Alias suggéré:** `/test-logs`

---

## 📝 DOCUMENTATION COMMANDS

### Create Changelog Template
```markdown
# Changelog vX.Y.Z - [Feature Name]

**Date:** $(date +%Y-%m-%d)
**Type:** MAJOR FEATURE UPDATE / MINOR UPDATE / PATCH
**Thème:** [Description]

---

## 🎯 RÉSUMÉ

[Description courte des changements]

---

## ✨ NOUVELLES FONCTIONNALITÉS

### [Feature Name]
- [Description détaillée]
- [Fichiers modifiés]
- [Impact utilisateur]

---

## 🔧 CORRECTIONS DE BUGS

- [Bug description]
- [Fix description]

---

## 🏗️ CHANGEMENTS TECHNIQUES

- [Technical change]
- [Architecture modification]

---

## 📊 STATISTIQUES

- X fichiers modifiés
- +Y lignes / -Z lignes
- X commits

---

## 🧪 TESTS VALIDÉS

- [Test description]
- [User feedback]
```

**Alias suggéré:** `/create-changelog`

---

### Create Audit Template
```markdown
# AUDIT - [Feature Name]

**Date:** $(date +%Y-%m-%d)
**Objectif:** [Objectif de l'audit]

---

## 📋 ÉTAT ACTUEL

### [Section 1]
- [Item 1] → État / Problème
- [Item 2] → État / Problème

---

## 🎯 PROBLÈMES IDENTIFIÉS

1. [Problème 1]
   - Impact: [Description]
   - Priorité: HAUTE / MOYENNE / BASSE

---

## 💡 SOLUTIONS PROPOSÉES

1. [Solution 1]
   - Action: [Description]
   - Effort: [Estimation]
   - Priorité: HAUTE / MOYENNE / BASSE

---

## 📊 STATISTIQUES

- X items analysés
- Y problèmes identifiés
- Z solutions proposées
```

**Alias suggéré:** `/create-audit`

---

### Create Plan Template
```markdown
# PLAN - [Feature Name]

**Date:** $(date +%Y-%m-%d)
**Objectif:** [Objectif du plan]

---

## 🎯 VISION FINALE

[Description de l'objectif final]

---

## 📋 PHASE 1: [Phase Name]

### Objectif:
[Description]

### Actions:
1. [Action 1]
2. [Action 2]

### Fichiers à modifier:
- [File 1]
- [File 2]

### Tests:
- [Test 1]
- [Test 2]

---

## 📋 PHASE 2: [Phase Name]

[Repéter structure Phase 1]

---

## 🧪 PLAN DE TEST

### Test Phase 1:
1. [Test step]
2. [Test step]

---

## 📊 IMPACT ATTENDU

**Avant:**
- [État actuel]

**Après:**
- [État souhaité]

**Bénéfices:**
- [Bénéfice 1]
- [Bénéfice 2]
```

**Alias suggéré:** `/create-plan`

---

## 🔧 UTILITY COMMANDS

### Check Version
```bash
# Voir version dans build.gradle
grep "versionName\|versionCode" ChatAI-Android/app/build.gradle

# Voir version dans KittAIService
grep "VERSION" ChatAI-Android/app/src/main/java/com/chatai/services/KittAIService.kt
```

**Alias suggéré:** `/check-version`

---

### Bump Version
```bash
# Bump version (exemple: 4.6.0 → 4.6.1)
# 1. Modifier app/build.gradle (versionCode + versionName)
# 2. Modifier KittAIService.kt (VERSION constant)
# 3. Créer CHANGELOG_vX.Y.Z.md
# 4. Commit: "chore: Bump version to X.Y.Z"
```

**Alias suggéré:** `/bump-version`

---

### Find TODOs
```bash
# Trouver tous les TODO dans le code
grep -r "TODO\|FIXME\|XXX" ChatAI-Android/app/src --include="*.kt" --include="*.java"
```

**Alias suggéré:** `/find-todos`

---

### Check Linter Errors
```bash
# Voir erreurs linter pour un fichier
# (À adapter selon linter utilisé)
```

**Alias suggéré:** `/check-lint`

---

## 📁 FILE OPERATIONS

### Backup File
```bash
# Créer backup avant modification
cp <file> <file>.backup.$(date +%Y%m%d_%H%M%S)
```

**Alias suggéré:** `/backup-file`

---

### Restore File from Backup
```bash
# Restaurer depuis backup
cp <file>.backup.* <file>
```

**Alias suggéré:** `/restore-backup`

---

## 🎯 QUICK ACTIONS

### Create Feature Branch
```bash
# Créer branche feature depuis main
git checkout main
git pull origin main
git checkout -b dev/<feature-name>
```

**Alias suggéré:** `/new-feature`

---

### Commit Feature
```bash
# Commit avec message conventionnel
git add .
git commit -m "feat: <description>"
```

**Alias suggéré:** `/commit-feat`

---

### Merge to Main
```bash
# Merger feature dans main
git checkout main
git merge dev/<feature-name> --no-ff -m "Merge branch 'dev/<feature-name>'"
git push origin main
```

**Alias suggéré:** `/merge-main`

---

## 📊 STATISTICS

### Code Stats
```bash
# Lignes de code par type
find ChatAI-Android/app/src -name "*.kt" | xargs wc -l
find ChatAI-Android/app/src -name "*.java" | xargs wc -l
```

**Alias suggéré:** `/code-stats`

---

### File Count
```bash
# Compter fichiers par type
find ChatAI-Android/app/src -name "*.kt" | wc -l
find ChatAI-Android/app/src -name "*.java" | wc -l
find ChatAI-Android/app/src -name "*.xml" | wc -l
```

**Alias suggéré:** `/file-count`

---

## 🚨 RECOVERY COMMANDS

### Recover from Stash
```bash
# Lister stashs
git stash list

# Appliquer dernier stash
git stash pop

# Appliquer stash spécifique
git stash apply stash@{0}
```

**Alias suggéré:** `/recover-stash`

---

### Undo Last Commit (Keep Changes)
```bash
# Annuler dernier commit mais garder modifications
git reset --soft HEAD~1
```

**Alias suggéré:** `/undo-commit`

---

### Revert File Changes
```bash
# Annuler modifications d'un fichier
git restore <file-path>
```

**Alias suggéré:** `/revert-file`

---

## 📚 DOCUMENTATION QUICK ACCESS

### Open Documentation
```bash
# Ouvrir docs principaux (Windows)
start ChatAI-Android\HISTOIRE_ET_VISION_CHATAI.md
start ChatAI-Android\docs\METHODOLOGIE_NOS_RULES.md
start ChatAI-Android\CONTRIBUTING.md
start ChatAI-Android\docs\GIT_WORKFLOW.md
```

**Alias suggéré:** `/open-docs`

---

### List All MD Files
```bash
# Lister tous les fichiers .md
find ChatAI-Android -name "*.md" -type f
```

**Alias suggéré:** `/list-docs`

---

## 🎯 WORKFLOW COMPLETE

### Complete Feature Workflow
```bash
# Workflow complet pour une feature
# 1. Créer branche
git checkout -b dev/<feature-name>

# 2. Développer...
# (modifications)

# 3. Build et test
cd ChatAI-Android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Commit
git add .
git commit -m "feat: <description>"

# 5. Push
git push origin dev/<feature-name>

# 6. Merge (après tests)
git checkout main
git merge dev/<feature-name> --no-ff
git push origin main
```

**Alias suggéré:** `/feature-workflow`

---

**Ces commandes peuvent être créées dans Cursor → Commands pour usage rapide.**

**Note:** Adapter les chemins selon votre environnement (Windows PowerShell vs Linux/Mac).

