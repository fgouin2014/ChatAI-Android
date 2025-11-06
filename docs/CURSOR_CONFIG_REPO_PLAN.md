# Plan: Repo Cursor-Config Universel

**Date:** 2025-11-06  
**Statut:** PLANIFIÉ (à créer plus tard)  
**Objectif:** Créer un repo GitHub séparé avec configuration Cursor réutilisable pour tous les projets

---

## 🎯 VISION

**Un repo GitHub universel pour configuration Cursor:**
- Réutilisable pour TOUS les projets (ChatAI, GameLibrary, RetroPlay, etc.)
- Rules génériques + spécifiques par projet
- Memories méthodologie "Nos Rules"
- Commands utiles universelles
- Facile à cloner et utiliser

**Citation utilisateur (2025-11-06):**
> "plus tard il me faudra un repo différent pour config cursor 'l'app'. pour d'autres projets."

---

## 📂 STRUCTURE PROPOSÉE

```
cursor-config/  (repo GitHub séparé)
│
├── README.md
│   └── Guide complet d'utilisation
│
├── .cursorrules  (fichier standard Cursor)
│   └── Règles Git universelles
│       - Conventional Commits
│       - Semantic Versioning
│       - Branch strategy (main, dev/*, hotfix/*)
│       - Documentation requirements
│
├── memories/  (memories génériques)
│   ├── nos-rules-methodology.md
│   │   └── Méthodologie "Nos Rules" complète
│   │
│   ├── git-workflow-success.md
│   │   └── Protection contre erreurs Git
│   │
│   ├── crash-recovery.md
│   │   └── Comment récupérer après crash
│   │
│   ├── architecture-modular.md
│   │   └── Importance architecture modulaire
│   │
│   └── testing-on-device.md
│       └── Toujours tester sur device réel
│
├── commands/
│   ├── git/
│   │   ├── check-status.sh
│   │   ├── create-feature.sh
│   │   ├── merge-main.sh
│   │   └── recover-work.sh
│   │
│   ├── build/
│   │   ├── build-debug.sh
│   │   ├── build-install.sh
│   │   └── clean-build.sh
│   │
│   ├── test/
│   │   ├── test-logs.sh
│   │   └── test-device.sh
│   │
│   └── docs/
│       ├── create-changelog.md
│       ├── create-audit.md
│       └── create-plan.md
│
└── projects/  (configs spécifiques)
    │
    ├── chatai/
    │   ├── .cursorrules  (règles spécifiques ChatAI)
    │   │   └── Architecture V3 (7 managers)
    │   │   └── Drawer KITT rules
    │   │   └── Real Assistant System
    │   │
    │   ├── memories.md  (memories ChatAI)
    │   │   └── Vision commerciale
    │   │   └── Citations utilisateur
    │   │   └── KITT style vocal
    │   │
    │   └── commands.sh  (commandes ChatAI)
    │       └── Build Android
    │       └── Test drawer
    │       └── Test API
    │
    ├── gamelibrary/
    │   ├── .cursorrules
    │   │   └── EmulatorJS rules
    │   │   └── WebServer rules (port 9999)
    │   │   └── ROMs management
    │   │
    │   ├── memories.md
    │   │   └── GameLibrary-Data structure
    │   │   └── Ne jamais modifier EmulatorJS
    │   │
    │   └── commands.sh
    │       └── Build GameLibrary
    │       └── Test emulators
    │       └── Deploy to device
    │
    └── retroplay/
        ├── .cursorrules
        │   └── RetroArch overlays
        │   └── LibRetro cores
        │   └── "Nos Rules" for overlays
        │
        └── memories.md
            └── Zapper zone discovery
            └── Overlay parser 100% compatible

```

---

## 📋 CONTENU DÉTAILLÉ

### 1. **README.md** (racine)

```markdown
# Cursor Configuration - Nos Rules

Configuration Cursor universelle pour tous les projets.

## Installation rapide:

### Pour nouveau projet:
1. Clone ce repo
2. Copier `.cursorrules` à la racine de votre projet
3. Ajouter memories depuis `memories/` dans Cursor
4. Copier commands depuis `commands/` dans Cursor

### Pour projet existant (ChatAI, GameLibrary, etc.):
1. Clone ce repo
2. Utiliser config spécifique: `projects/<nom-projet>/`
3. Copier `.cursorrules` du projet
4. Ajouter memories spécifiques
5. Ajouter commands spécifiques

## Méthodogie "Nos Rules":
1. Recherche approfondie
2. Implémentation exacte (100%)
3. Documentation complète

## Résultat:
- Protection contre perte de travail
- Git comme backup fiable
- Recovery après crash Cursor
- Code maintenable
```

---

### 2. **.cursorrules** (fichier standard Cursor)

**Règles universelles pour TOUS les projets:**

```markdown
# Nos Rules - Universal Development Rules

## Git Workflow
- Use Conventional Commits (feat, fix, docs, chore, etc.)
- Create branch for each feature: dev/feature-name
- Test before merge to main
- Push regularly (max 5 commits without push)
- Git = automatic cloud backup

## Methodology "Nos Rules"
1. DEEP RESEARCH before implementing
   - Read ALL official specs
   - Study concrete examples (30+ if possible)
   - Understand WHY, not just HOW

2. EXACT IMPLEMENTATION (100%)
   - According to official specifications
   - No arbitrary simplifications
   - Test on real device/environment

3. COMPLETE DOCUMENTATION
   - Write what you understood
   - Document important decisions
   - Create permanent references

## Documentation Required
- Audit before modifications (AUDIT_*.md)
- Detailed plan (PLAN_*.md with phases)
- Changelog for each version (CHANGELOG_vX.Y.Z.md)
- Test results documented

## Testing
- ALWAYS test on real device/environment
- Compile without errors
- Verify logs (no critical errors)
- Validate functionality

## Versioning
- Semantic Versioning (MAJOR.MINOR.PATCH)
- Update version in all relevant files
- Create changelog for each version
- Tag releases in Git
```

---

### 3. **memories/** (génériques)

#### **nos-rules-methodology.md:**
```
Méthodologie "Nos Rules" utilisée pour tous les projets:
1) Recherche approfondie (lire specs officielles complètes)
2) Implémentation exacte à 100% (pas de simplifications)
3) Documentation complète

Résultat: Code maintenable, 0 bugs, protection contre perte.

Exemple ChatAI: Architecture V3 (7 managers), drawer refactoring, crash Cursor recovery.
Exemple RetroArch: Parser overlays 100% compatible, 30+ packages officiels, maintenance zéro.
```

#### **git-workflow-success.md:**
```
Git workflow avec "Nos Rules":
- Commits fréquents et bien nommés (Conventional Commits)
- Branches pour features (dev/*)
- Push réguliers (backup automatique)
- Documentation complète

Protection contre erreurs:
- Crash Cursor → Récupération complète via git log/show
- Perte de travail → Impossible (tout dans Git)
- Confusion → git diff/git show pour comprendre

Citation: "Avant je détestais GitHub. Maintenant c'est exceptionnel!"
```

---

### 4. **projects/chatai/** (spécifiques)

**Memories ChatAI uniquement:**
- Vision commerciale (Google Watch, usage quotidien)
- Architecture V3 (7 managers)
- KITT style vocal (pas personnage)
- Drawer professionnel (pas roleplay)

**Commands ChatAI:**
- Build Android: `./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Test drawer: Checklist 16 boutons
- Test API: Diagnostic API + logs

---

## 🚀 AVANTAGES

### **1. Réutilisabilité:**
- Même config pour ChatAI, GameLibrary, RetroPlay
- Rules universelles partagées
- Memories méthodologie accessibles partout

### **2. Maintenance:**
- Une seule source de vérité
- Modifications propagées facilement
- Versioning de la config elle-même

### **3. Collaboration:**
- Nouveau développeur → Clone cursor-config
- Setup Cursor en 5 minutes
- Méthodologie comprise immédiatement

### **4. Protection:**
- Config Cursor aussi dans Git
- Perte config Cursor → Récupération facile
- Backup automatique

---

## 📅 TIMELINE PROPOSÉ

### **À faire maintenant (2025-11-06):**
- ✅ Créer plan (ce document)
- ✅ Documenter structure
- ✅ Commiter dans ChatAI

### **À faire plus tard (quand besoin):**

**1. Créer repo GitHub:**
```bash
cd c:\repos
mkdir cursor-config
cd cursor-config
git init
# Créer structure
git add .
git commit -m "feat: Initial Cursor config repo"
git remote add origin https://github.com/<user>/cursor-config.git
git push -u origin main
```

**2. Extraire configs depuis ChatAI:**
- Copier `docs/CURSOR_RULES.md` → `projects/chatai/.cursorrules`
- Copier `docs/CURSOR_MEMORIES.md` → `projects/chatai/memories.md`
- Copier `docs/CURSOR_COMMANDS.md` → `projects/chatai/commands.sh`

**3. Créer configs universelles:**
- Extraire règles communes de ChatAI
- Créer `.cursorrules` universel
- Créer memories génériques (Nos Rules, Git, etc.)

**4. Ajouter autres projets:**
- GameLibrary config
- RetroPlay config
- Futurs projets

---

## 📝 NOTES

**Ce repo cursor-config sera:**
- Votre "base de connaissances" Cursor
- Votre méthodologie formalisée
- Votre protection contre oubli
- Votre onboarding pour nouveaux projets

**Chaque nouveau projet:**
1. Clone cursor-config
2. Copie config universelle
3. Ajoute config spécifique projet
4. Setup Cursor en 5 minutes
5. Commence développement avec bonnes pratiques

---

**Document maintenu par:** François Gouin  
**Dernière mise à jour:** 2025-11-06  
**Statut:** PLANIFIÉ (à exécuter quand besoin)  
**Repo futur:** `https://github.com/<user>/cursor-config` (à créer)

