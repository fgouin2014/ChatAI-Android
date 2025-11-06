# Guide de Contribution - ChatAI

Merci de votre intérêt pour contribuer à ChatAI! Ce document décrit les conventions et le workflow du projet.

## 📋 Table des matières

- [Workflow Git](#workflow-git)
- [Convention des commits](#convention-des-commits)
- [Versioning](#versioning)
- [Architecture du code](#architecture-du-code)
- [Tests](#tests)
- [Documentation](#documentation)

---

## 🌿 Workflow Git

### Branches principales

- **`main`** → Version stable, toujours fonctionnelle et déployable
- **`dev/*`** → Branches de développement pour nouvelles features
- **`hotfix/*`** → Corrections urgentes de bugs critiques
- **`backup-*`** → Sauvegardes avant opérations majeures (automatiques)

### Convention de nommage des branches

```
dev/drawer-refactoring
dev/real-assistant-system
dev/karr-personality
hotfix/crash-on-startup
hotfix/ollama-cloud-quota
```

**Format:** `type/description-kebab-case`

### Workflow de développement

1. **Créer une branche depuis `main`:**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b dev/my-feature
   ```

2. **Développer et commiter régulièrement:**
   ```bash
   git add .
   git commit -m "feat: Add new feature"
   ```

3. **Tester localement:**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Merger dans `main` quand stable:**
   ```bash
   git checkout main
   git merge dev/my-feature
   git push origin main
   ```

5. **Nettoyer la branche (optionnel):**
   ```bash
   git branch -d dev/my-feature
   ```

Pour plus de détails techniques, voir [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md).

---

## 📝 Convention des commits

Nous suivons la convention **[Conventional Commits](https://www.conventionalcommits.org/)**.

### Format

```
<type>: <description courte>

[corps optionnel]

[footer optionnel]
```

### Types de commits

| Type | Description | Exemple |
|------|-------------|---------|
| `feat` | Nouvelle fonctionnalité | `feat: Add KARR personality with TTS voice` |
| `fix` | Correction de bug | `fix: Fix Ollama Cloud quota detection (HTTP 429)` |
| `chore` | Maintenance (build, deps) | `chore: Bump version to 4.5.0` |
| `docs` | Documentation uniquement | `docs: Update README with new features` |
| `refactor` | Refactorisation sans changement de fonctionnalité | `refactor: Extract KittAnimationManager from KittFragment` |
| `perf` | Amélioration de performance | `perf: Optimize ROM loading for PSX games` |
| `test` | Ajout/modification de tests | `test: Add API connection tests` |
| `style` | Formatage, indentation | `style: Fix code formatting in KittAIService` |

### Exemples réels du projet

✅ **BON:**
```
feat: v4.5.0 - Real AI Assistant System + Complete Architecture Overhaul

MAJOR CHANGES:
1. SYSTEM PROMPT REVOLUTION (KittAIService.kt)
2. CONVERSATION SYSTEM (ConversationHistoryActivity.kt)
3. API DIAGNOSTICS (AIConfigurationActivity.kt)
4. ARCHITECTURE V3 - MODULAR (7 Managers)
```

```
fix: Correct Ollama Cloud URL and model name handling

- Changed URL from api.ollama.ai to ollama.com
- Removed -cloud suffix from model names
- Added quota detection for HTTP 429/502/503
```

```
chore: Add obb/ to gitignore
```

❌ **MAUVAIS:**
```
fixed stuff
update
wip
test
```

---

## 🔢 Versioning

Nous utilisons **[Semantic Versioning 2.0.0](https://semver.org/)**.

### Format: MAJOR.MINOR.PATCH

- **MAJOR** (4.x.x): Changements d'architecture, breaking changes
- **MINOR** (x.5.x): Nouvelles fonctionnalités (compatibles)
- **PATCH** (x.x.1): Corrections de bugs uniquement

### Suffixes

- `-beta`: Version de test pré-release
- `-alpha`: Version très instable en développement précoce
- `-rc1`: Release candidate

### Exemples

```
4.5.0       → Version stable avec Real Assistant System
4.5.1       → Bug fixes pour 4.5.0
4.6.0-beta  → Test de la refonte du drawer
5.0.0       → Changement d'architecture majeur (breaking)
```

### Workflow de release

1. **Mettre à jour les versions:**
   ```gradle
   // app/build.gradle
   versionCode 12
   versionName "4.5.0"
   ```

   ```kotlin
   // KittAIService.kt
   private const val VERSION = "4.5.0"
   ```

2. **Créer un tag:**
   ```bash
   git tag v4.5.0
   git push origin v4.5.0
   ```

3. **Créer le changelog:**
   - Créer `CHANGELOG_v4.5.0.md` avec les changements

---

## 🏗️ Architecture du code

### Structure V3 - Modular (7 Managers)

Le projet utilise une architecture modulaire avec des managers spécialisés:

```
KittFragment (Coordinator)
├── KittAnimationManager    → VU-meter, Scanner, Thinking animations
├── KittTTSManager          → Text-to-Speech (KITT, GLaDOS, KARR)
├── KittVoiceManager        → Speech Recognition
├── KittMessageQueueManager → Priority queue, Marquee display
├── KittMusicManager        → MediaPlayer for theme music
├── KittStateManager        → 6 system states (OFF, STANDBY, READY, etc.)
└── KittDrawerManager       → Menu drawer management
```

### Principes de développement

1. **Séparation des responsabilités:** Chaque manager a une fonction claire et unique
2. **Lifecycle-aware:** Les managers suivent le lifecycle d'Android
3. **Thread-safe:** Callbacks et états gérés de façon synchronisée
4. **Testable:** Managers indépendants et mockables

### Fichiers critiques

| Fichier | Rôle | Ne PAS modifier sans tester |
|---------|------|----------------------------|
| `KittAIService.kt` | Service IA principal (prompts, API calls) | ⚠️ Critique |
| `KittFragment.kt` | Coordinateur principal | ⚠️ Critique |
| `AIConfigurationActivity.kt` | Configuration API | ✅ Safe |
| `ConversationHistoryActivity.kt` | Historique conversations | ✅ Safe |

Pour plus de détails, voir `ARCHITECTURE_V3_FINAL.md`.

---

## 🧪 Tests

### Tests avant commit

```bash
# Compiler le projet
cd ChatAI-Android
./gradlew assembleDebug

# Installer sur device
adb install app/build/outputs/apk/debug/app-debug.apk

# Tester les fonctionnalités principales
# 1. KITT voice interaction
# 2. API connections (OpenAI, Anthropic, Ollama)
# 3. Conversation history
# 4. Personality switching (KITT, GLaDOS, KARR)
```

### Tests de diagnostic

Utiliser le bouton "Test Connexions API" dans `AIConfigurationActivity`:
- Vérifie toutes les API configurées
- Génère un log détaillé dans `/storage/emulated/0/ChatAI-Files/logs/`
- Détecte les quotas Ollama Cloud (HTTP 429)

### Logs

```bash
# Logs en temps réel
adb logcat | Select-String "KITT|API_TEST|Ollama"

# Filtrer par tag
adb logcat -s KittAIService
adb logcat -s API_TEST_EXPORT
```

---

## 📚 Documentation

### Quoi documenter?

✅ **À DOCUMENTER:**
- Nouvelles fonctionnalités (CHANGELOG)
- Changements d'architecture (ARCHITECTURE.md)
- API publiques et interfaces (API_REFERENCE.md)
- Workflow Git et contribution (ce fichier)
- Décisions de design importantes

❌ **PAS BESOIN:**
- Commits Git individuels
- Notes de session de développement
- Bugs fixes mineurs (sauf si critique)
- Workflow personnel temporaire

### Structure de documentation

```
ChatAI-Android/
├── README.md                          # Présentation publique
├── CONTRIBUTING.md                    # Ce fichier (contribution)
├── CHANGELOG_v4.5.0.md               # Changelogs par version
├── ARCHITECTURE_V3_FINAL.md          # Architecture technique
├── docs/
│   ├── GIT_WORKFLOW.md               # Workflow Git détaillé
│   ├── API_REFERENCE.md              # API documentation
│   └── SETUP.md                      # Installation et setup
└── dev-notes/                        # Notes internes (gitignored)
    ├── SESSION_*.md
    └── BUGFIX_*.md
```

### Créer un changelog

Pour chaque version majeure/mineure, créer `CHANGELOG_vX.Y.Z.md`:

```markdown
# Changelog v4.5.0 - Real AI Assistant System

**Date:** 2025-11-06
**Type:** MAJOR UPDATE

## Nouvelles fonctionnalités

- Real AI Assistant System (transparent, factual responses)
- Complete API diagnostics with file logging
- Conversation export/import with UUID tracking

## Corrections de bugs

- Fixed Ollama Cloud URL (ollama.com not api.ollama.ai)
- Fixed model names (removed -cloud suffix)

## Changements techniques

- Architecture V3 with 7 specialized managers
- Thread-safe callback system
- Lifecycle-aware managers

## Breaking changes

- System prompt changed from roleplay to real assistant
- Database schema updated (conversationId UUID added)
```

---

## 🚀 Checklist avant commit

- [ ] Code compilé sans erreurs: `./gradlew assembleDebug`
- [ ] Testé sur device physique (pas juste émulateur)
- [ ] Logs vérifiés (pas d'erreurs critiques)
- [ ] Commit message suit la convention
- [ ] Documentation mise à jour si nécessaire
- [ ] Version bumped si feature majeure

---

## 📞 Contact

Pour toute question sur la contribution:
- Créer une issue sur GitHub
- Consulter les documents dans `/docs/`
- Vérifier les changelogs récents

---

**Merci de contribuer à ChatAI! 🎉**

