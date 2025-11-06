# Git Workflow - ChatAI (Documentation technique)

Ce document décrit le workflow Git utilisé dans le projet ChatAI. Pour un guide général de contribution, voir [CONTRIBUTING.md](../CONTRIBUTING.md).

---

## 📋 Table des matières

- [Stratégie de branches](#stratégie-de-branches)
- [Convention des commits](#convention-des-commits)
- [Workflow de développement](#workflow-de-développement)
- [Gestion des versions](#gestion-des-versions)
- [Résolution de conflits](#résolution-de-conflits)
- [Commandes utiles](#commandes-utiles)

---

## 🌿 Stratégie de branches

### Vue d'ensemble

```
main (stable, déployable)
  │
  ├── dev/drawer-refactoring (feature en cours)
  ├── dev/real-assistant-system (feature terminée, mergée)
  ├── hotfix/crash-on-startup (correction urgente)
  └── backup-before-cleanup-20251105 (sauvegarde automatique)
```

### Types de branches

| Type | Nomenclature | Base | Merge vers | Durée de vie |
|------|--------------|------|-----------|--------------|
| **main** | `main` | - | - | Permanente |
| **Feature** | `dev/<description>` | `main` | `main` | Temporaire (jours/semaines) |
| **Hotfix** | `hotfix/<description>` | `main` | `main` | Très courte (heures) |
| **Backup** | `backup-<operation>-<date>` | `main` | - | Archive permanente |

### Règles

1. **`main` doit TOUJOURS compiler et fonctionner**
   - Jamais de commit non testé sur `main`
   - Toujours tester sur device avant de merger

2. **Branches `dev/*` pour toute nouvelle feature**
   - Une feature = une branche
   - Nom descriptif en kebab-case
   - Exemples: `dev/karr-personality`, `dev/drawer-refactoring`

3. **Branches `hotfix/*` pour bugs critiques**
   - Créées depuis `main`
   - Mergées immédiatement après fix
   - Supprimées après merge

4. **Branches `backup-*` créées avant opérations majeures**
   - Cleanup de code
   - Refactoring massif
   - Changement d'architecture
   - Format: `backup-<operation>-YYYYMMDD-HHMMSS`

---

## 📝 Convention des commits

### Format Conventional Commits

```
<type>: <description>

[corps détaillé optionnel]

[footers optionnels: BREAKING CHANGE, Fixes #123]
```

### Types autorisés

| Type | Quand l'utiliser | Exemple |
|------|------------------|---------|
| `feat` | Nouvelle fonctionnalité utilisateur | `feat: Add KARR personality` |
| `fix` | Correction de bug | `fix: Fix API quota detection` |
| `refactor` | Changement de code sans impact fonctionnel | `refactor: Extract managers from KittFragment` |
| `perf` | Amélioration de performance | `perf: Optimize ROM loading` |
| `docs` | Documentation uniquement | `docs: Add Git workflow guide` |
| `style` | Formatage, whitespace | `style: Fix indentation` |
| `test` | Ajout/modification de tests | `test: Add API connection tests` |
| `chore` | Build, dépendances, config | `chore: Bump version to 4.5.0` |
| `ci` | CI/CD configuration | `ci: Add GitHub Actions workflow` |
| `revert` | Annuler un commit précédent | `revert: Restore KITT roleplay` |

### Description (sujet)

- **Longueur:** 50 caractères maximum
- **Style:** Impératif présent ("Add feature" pas "Added" ou "Adds")
- **Capitalisation:** Première lettre majuscule
- **Pas de point final**

✅ **BON:**
```
feat: Add real-time thinking trace display
fix: Correct Ollama Cloud URL endpoint
refactor: Extract KittAnimationManager
```

❌ **MAUVAIS:**
```
Added new feature.
Fixed stuff
wip
Update
```

### Corps du commit (optionnel mais recommandé)

Pour commits complexes, ajouter un corps détaillé:

```
feat: v4.5.0 - Real AI Assistant System + Complete Architecture Overhaul

MAJOR CHANGES:

1. SYSTEM PROMPT REVOLUTION (KittAIService.kt):
   - Transformed from roleplay to real assistant
   - KITT is vocal STYLE, responses are FACTUAL
   - Transparent about technical capabilities

2. CONVERSATION SYSTEM (ConversationHistoryActivity.kt):
   - Full export/import with JSON
   - UUID tracking for all conversations

3. API DIAGNOSTICS (AIConfigurationActivity.kt):
   - Complete API testing with file logging
   - Ollama Cloud quota detection

VERSION: 4.5.0
```

**Format du corps:**
- Ligne vide après le sujet
- Lignes de 72 caractères maximum
- Utiliser listes à puces ou numérotées
- Expliquer **QUOI** et **POURQUOI**, pas le **COMMENT**

### Footers

```
BREAKING CHANGE: Database schema changed, conversationId UUID added
Fixes #42
Closes #123, #456
```

---

## 🚀 Workflow de développement

### 1. Nouvelle feature

```bash
# 1. S'assurer que main est à jour
git checkout main
git pull origin main

# 2. Créer une branche de feature
git checkout -b dev/my-new-feature

# 3. Développer et commiter régulièrement
git add app/src/main/java/com/chatai/MyNewFile.kt
git commit -m "feat: Add MyNewFile component"

# 4. Continuer le développement
git add .
git commit -m "feat: Complete MyNewFile integration"

# 5. Tester sur device
cd ChatAI-Android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# 6. Si tests OK, merger dans main
git checkout main
git merge dev/my-new-feature

# 7. Pusher vers remote
git push origin main

# 8. Optionnel: supprimer la branche locale
git branch -d dev/my-new-feature
```

### 2. Hotfix urgent

```bash
# 1. Créer branche depuis main
git checkout main
git checkout -b hotfix/crash-on-startup

# 2. Corriger le bug
# ... éditer les fichiers ...

# 3. Commiter le fix
git add app/src/main/java/com/chatai/BuggyFile.kt
git commit -m "fix: Prevent crash on startup when API not configured"

# 4. Tester IMMÉDIATEMENT
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# 5. Merger dans main
git checkout main
git merge hotfix/crash-on-startup
git push origin main

# 6. Supprimer la branche
git branch -d hotfix/crash-on-startup
```

### 3. Backup avant opération majeure

```bash
# Créer une branche de backup
git checkout -b backup-before-refactoring-$(date +%Y%m%d-%H%M%S)
git push origin backup-before-refactoring-$(date +%Y%m%d-%H%M%S)

# Retourner sur main pour continuer
git checkout main
```

**Exemple réel du projet:**
```bash
git checkout -b backup-before-cleanup-20251105-211316
```

### 4. Workflow avec plusieurs features en parallèle

```bash
# Feature 1
git checkout -b dev/drawer-refactoring
# ... travail ...
git commit -m "feat: Connect ServerActivity to drawer"

# Feature 2 (switcher)
git checkout main
git checkout -b dev/karr-improvements
# ... travail ...
git commit -m "feat: Add KARR voice pitch adjustment"

# Retour à feature 1
git checkout dev/drawer-refactoring
# ... continuer ...

# Merger feature 2 d'abord
git checkout main
git merge dev/karr-improvements
git push origin main

# Puis feature 1
git merge dev/drawer-refactoring
git push origin main
```

---

## 🔢 Gestion des versions

### Semantic Versioning

Format: **MAJOR.MINOR.PATCH[-PRERELEASE]**

```
4.5.0       → Stable release
4.5.1       → Bug fix patch
4.6.0-beta  → Pre-release pour tests
5.0.0       → Breaking change (architecture V4?)
```

### Workflow de release

#### 1. Préparation

```bash
# S'assurer que main est propre
git checkout main
git status
# -> "nothing to commit, working tree clean"
```

#### 2. Bump de version

**Fichiers à modifier:**

```gradle
// app/build.gradle
defaultConfig {
    versionCode 12         // Incrémenter de 1
    versionName "4.5.0"    // Nouvelle version
}
```

```kotlin
// app/src/main/java/com/chatai/services/KittAIService.kt
companion object {
    private const val VERSION = "4.5.0"
}
```

```bash
# Commiter le bump
git add app/build.gradle app/src/main/java/com/chatai/services/KittAIService.kt
git commit -m "chore: Bump version to 4.5.0"
```

#### 3. Créer le changelog

```bash
# Créer CHANGELOG_v4.5.0.md
# Voir exemple dans CONTRIBUTING.md
git add CHANGELOG_v4.5.0.md
git commit -m "docs: Add changelog for v4.5.0"
```

#### 4. Créer un tag Git

```bash
# Tag annoté (recommandé)
git tag -a v4.5.0 -m "Release v4.5.0 - Real AI Assistant System"

# Pusher le tag
git push origin v4.5.0

# Pusher tous les tags
git push --tags
```

#### 5. Vérifier

```bash
# Lister les tags
git tag -l

# Voir les détails d'un tag
git show v4.5.0

# Vérifier sur remote
git ls-remote --tags origin
```

### Nomenclature des versions

| Version actuelle | Type de changement | Nouvelle version |
|-----------------|-------------------|------------------|
| 4.5.0 | Bug fix critique | 4.5.1 |
| 4.5.0 | Nouvelle feature (drawer refactoring) | 4.6.0 |
| 4.5.0 | Breaking change (architecture V4) | 5.0.0 |
| 4.5.0 | Pre-release testing | 4.6.0-beta |

---

## ⚔️ Résolution de conflits

### Scénario: Conflit lors du merge

```bash
git checkout main
git merge dev/my-feature

# Output:
# Auto-merging app/src/main/java/com/chatai/KittFragment.kt
# CONFLICT (content): Merge conflict in app/src/main/java/com/chatai/KittFragment.kt
```

### Étapes de résolution

1. **Identifier les fichiers en conflit:**
   ```bash
   git status
   # Unmerged paths:
   #   both modified:   app/src/main/java/com/chatai/KittFragment.kt
   ```

2. **Ouvrir le fichier et trouver les markers:**
   ```kotlin
   <<<<<<< HEAD (main)
   // Version actuelle dans main
   private const val VERSION = "4.5.0"
   =======
   // Version dans dev/my-feature
   private const val VERSION = "4.6.0"
   >>>>>>> dev/my-feature
   ```

3. **Résoudre manuellement:**
   ```kotlin
   // Garder la version la plus récente
   private const val VERSION = "4.6.0"
   ```

4. **Marquer comme résolu et commiter:**
   ```bash
   git add app/src/main/java/com/chatai/KittFragment.kt
   git commit -m "Merge branch 'dev/my-feature' - resolve version conflict"
   ```

### Annuler un merge en cours

```bash
# Si vous voulez abandonner le merge
git merge --abort
```

### Stratégies de merge

```bash
# Merge avec commit de merge (par défaut)
git merge dev/my-feature

# Merge fast-forward si possible
git merge --ff-only dev/my-feature

# Toujours créer un commit de merge
git merge --no-ff dev/my-feature

# Squash tous les commits de la branche en un seul
git merge --squash dev/my-feature
```

**Recommandation:** Utiliser `--no-ff` pour les features importantes pour garder l'historique clair.

---

## 🛠️ Commandes utiles

### Inspection de l'historique

```bash
# Historique complet
git log

# Historique compact (1 ligne par commit)
git log --oneline

# 10 derniers commits
git log --oneline -10

# Historique avec graphe de branches
git log --oneline --graph --all

# Commits entre deux dates
git log --since="2025-11-01" --until="2025-11-06"

# Commits par auteur
git log --author="François"

# Chercher dans les commits
git log --grep="KITT"
```

### Inspection des branches

```bash
# Lister toutes les branches locales
git branch

# Lister avec derniers commits
git branch -v

# Lister toutes les branches (local + remote)
git branch -a

# Branches mergées dans main
git branch --merged main

# Branches non mergées
git branch --no-merged main
```

### Comparaison

```bash
# Différences entre working directory et staging
git diff

# Différences entre staging et dernier commit
git diff --staged

# Différences entre deux branches
git diff main..dev/my-feature

# Statistiques de changements
git diff --stat

# Fichiers modifiés seulement
git diff --name-only
```

### Nettoyage

```bash
# Supprimer branches locales mergées
git branch --merged main | grep -v "main" | xargs git branch -d

# Supprimer branche locale (force)
git branch -D dev/old-feature

# Supprimer branche remote
git push origin --delete dev/old-feature

# Nettoyer références aux branches remote supprimées
git fetch --prune

# Nettoyer fichiers non trackés
git clean -fd
```

### Stash (sauvegarder temporairement)

```bash
# Sauvegarder changements non commités
git stash

# Sauvegarder avec message
git stash save "WIP: drawer refactoring"

# Lister les stashs
git stash list

# Appliquer le dernier stash
git stash apply

# Appliquer et supprimer le stash
git stash pop

# Supprimer un stash
git stash drop stash@{0}
```

### Annuler des changements

```bash
# Annuler modifications d'un fichier (pas encore staged)
git restore app/src/main/java/com/chatai/MyFile.kt

# Annuler modifications de TOUS les fichiers
git restore .

# Unstage un fichier (garder les modifications)
git restore --staged app/src/main/java/com/chatai/MyFile.kt

# Annuler le dernier commit (garder les modifications)
git reset --soft HEAD~1

# Annuler le dernier commit (perdre les modifications)
git reset --hard HEAD~1

# Revenir à un commit spécifique
git reset --hard <commit-hash>
```

### Remote

```bash
# Voir les remotes configurés
git remote -v

# Ajouter un remote
git remote add origin https://github.com/user/chatai.git

# Changer l'URL d'un remote
git remote set-url origin https://github.com/user/chatai-new.git

# Fetch depuis remote (sans merger)
git fetch origin

# Pull (fetch + merge)
git pull origin main

# Push vers remote
git push origin main

# Push tous les tags
git push --tags
```

### Diagnostic

```bash
# Statut actuel
git status

# Voir quelle branche track quel remote
git branch -vv

# Voir le dernier commit
git show

# Voir un commit spécifique
git show <commit-hash>

# Qui a modifié chaque ligne d'un fichier
git blame app/src/main/java/com/chatai/KittFragment.kt

# Chercher un mot dans tout l'historique
git log -S "KittAIService" --source --all
```

---

## 🎯 Scénarios courants

### Scénario 1: "J'ai commité sur main par erreur"

```bash
# Créer une branche à partir de main actuel
git branch dev/accidental-commit

# Revenir main au commit précédent
git reset --hard HEAD~1

# Continuer le travail sur la branche
git checkout dev/accidental-commit
```

### Scénario 2: "Je veux séparer mes commits en plusieurs"

```bash
# Reset le dernier commit mais garder les modifications
git reset --soft HEAD~1

# Re-commiter en plusieurs fois
git add fichier1.kt
git commit -m "feat: Add feature part 1"

git add fichier2.kt
git commit -m "feat: Add feature part 2"
```

### Scénario 3: "Je veux changer le message du dernier commit"

```bash
# Modifier le message (avant push)
git commit --amend -m "feat: Correct commit message"

# Si déjà pushé (ATTENTION: réécrit l'historique)
git commit --amend -m "feat: Correct commit message"
git push --force-with-lease origin main
```

### Scénario 4: "Je veux synchroniser ma branche avec main"

```bash
# Option 1: Merge (crée un commit de merge)
git checkout dev/my-feature
git merge main

# Option 2: Rebase (historique linéaire, plus propre)
git checkout dev/my-feature
git rebase main

# Si conflits, résoudre puis:
git add <fichiers-résolus>
git rebase --continue
```

### Scénario 5: "Push bloqué par des modifications remote"

```bash
# Erreur:
# ! [rejected] main -> main (fetch first)

# Solution:
git pull --rebase origin main
# Résoudre conflits si nécessaire
git push origin main
```

---

## 📊 Exemple d'historique du projet ChatAI

```bash
$ git log --oneline --graph --all -15

* 123c125 (HEAD -> dev/drawer-refactoring, origin/main, main) chore: Add obb/ to gitignore
* b120f4a chore: Add temp_apk/ to gitignore
* 8bdf1cd feat: v4.5.0 - Real AI Assistant System + Complete Architecture Overhaul
* 3c295fb docs: Vision IA Consciente - Document de session complet
* f4ad35e Initial commit: ChatAI v2.6 - Intelligence Conversationnelle
```

**Analyse:**
- `dev/drawer-refactoring` est au même niveau que `main` (vient d'être créée)
- `main` et `origin/main` sont synchronisés (`123c125`)
- Historique linéaire (pas de merges complexes)
- Commits bien nommés avec types (`feat`, `docs`, `chore`)

---

## ✅ Checklist Git quotidienne

**Avant de commencer à travailler:**
- [ ] `git checkout main`
- [ ] `git pull origin main`
- [ ] `git checkout -b dev/my-feature` (si nouvelle feature)

**Pendant le développement:**
- [ ] `git status` (souvent pour voir l'état)
- [ ] `git add <fichiers>` (staging sélectif)
- [ ] `git commit -m "type: description"` (commits atomiques)

**Avant de merger:**
- [ ] `./gradlew assembleDebug` (compile OK?)
- [ ] Tester sur device physique
- [ ] `git log --oneline -5` (vérifier les commits)

**Après merge:**
- [ ] `git push origin main`
- [ ] `git branch -d dev/my-feature` (nettoyer)

---

**Document maintenu à jour:** 2025-11-06  
**Version du workflow:** 1.0.0  
**Prochaine révision:** Quand adoption de CI/CD ou GitHub Actions

