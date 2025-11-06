# Documentation ChatAI - Guide d'utilisation

**Ce dossier contient toute la documentation technique et méthodologique du projet ChatAI.**

---

## 📚 DOCUMENTS DISPONIBLES

### **Méthodologie:**
- **`METHODOLOGIE_NOS_RULES.md`** → Méthodologie complète de développement
  - Histoire de "Nos Rules"
  - Application à ChatAI
  - Protection contre erreurs
  - Règles pour code et Cursor

### **Références Cursor (AI Assistant):**
- **`CURSOR_RULES.md`** → Règles à copier dans Cursor Settings → Rules
  - Git workflow
  - Architecture V3
  - Drawer KITT
  - Tests et documentation

- **`CURSOR_MEMORIES.md`** → Memories à ajouter dans Cursor → Memories
  - Vision du projet
  - Architecture
  - Méthodologie
  - Citations utilisateur importantes

- **`CURSOR_COMMANDS.md`** → Commandes utiles pour Cursor
  - Git commands
  - Build commands
  - Test commands
  - Documentation commands

### **Git Workflow:**
- **`GIT_WORKFLOW.md`** → Guide technique complet Git
  - Stratégie de branches
  - Convention de commits
  - Workflow de développement
  - Gestion des versions
  - Résolution de conflits
  - Commandes utiles

---

## 🚀 UTILISATION RAPIDE

### **Pour Cursor (AI Assistant):**

#### **1. Ajouter Rules:**
1. Ouvrir Cursor Settings → Rules
2. Copier contenu de `CURSOR_RULES.md`
3. Coller dans Rules
4. Sauvegarder

#### **2. Ajouter Memories:**
1. Ouvrir Cursor → Memories
2. Pour chaque memory dans `CURSOR_MEMORIES.md`:
   - Créer nouvelle memory
   - Copier "Memory Title" comme titre
   - Copier "Content" comme contenu
   - Sauvegarder

#### **3. Créer Commands (optionnel):**
1. Ouvrir Cursor → Commands
2. Pour chaque command utile dans `CURSOR_COMMANDS.md`:
   - Créer nouvelle command
   - Nom: alias suggéré (ex: `/check-git`)
   - Commande: contenu de la commande
   - Sauvegarder

### **Pour le projet (code):**

#### **1. Consulter méthodologie:**
- Lire `METHODOLOGIE_NOS_RULES.md` avant chaque nouvelle feature
- Appliquer les 3 étapes: Recherche → Implémentation → Documentation

#### **2. Consulter Git workflow:**
- Lire `GIT_WORKFLOW.md` pour workflow Git
- Suivre conventions de commits
- Utiliser commandes utiles

#### **3. Référence rapide:**
- `CURSOR_RULES.md` → Règles obligatoires
- `CURSOR_MEMORIES.md` → Contexte important
- `CURSOR_COMMANDS.md` → Commandes fréquentes

---

## 📋 CHECKLIST NOUVELLE FEATURE

**Avant de commencer:**
- [ ] Lire `METHODOLOGIE_NOS_RULES.md` (étapes 1-3)
- [ ] Vérifier `CURSOR_RULES.md` (règles à respecter)
- [ ] Consulter `GIT_WORKFLOW.md` (workflow Git)

**Pendant le développement:**
- [ ] Suivre méthodologie "Nos Rules"
- [ ] Respecter architecture V3
- [ ] Commits fréquents et bien nommés
- [ ] Tester sur device réel

**Après développement:**
- [ ] Documentation complète
- [ ] Changelog créé
- [ ] Tests validés
- [ ] Push vers GitHub

---

## 🎯 DOCUMENTS PRINCIPAUX (racine du projet)

Ces documents sont dans la racine du projet ChatAI-Android:

- **`HISTOIRE_ET_VISION_CHATAI.md`** → Vision complète et histoire du projet
- **`CONTRIBUTING.md`** → Guide contributeurs (pour nouveaux développeurs)
- **`PLAN_REFONTE_DRAWER.md`** → Plan drawer refactoring (exécuté)
- **`AUDIT_DRAWER_KITT.md`** → Audit 29 boutons (réalisé)
- **`CHANGELOG_v*.md`** → Changelogs par version

---

## 🔄 MAINTENANCE

### **Mettre à jour ces documents:**
- Quand nouvelle méthodologie adoptée
- Quand nouvelle règle importante
- Quand nouveau workflow Git
- Quand nouvelle memory importante

### **Versioning:**
- Ces documents font partie du projet
- Modifications trackées via Git
- Historique complet disponible

---

## 💡 RAPPEL

**"Nos Rules" = Recherche approfondie → Implémentation exacte → Documentation complète**

**Résultat:**
- Code maintenable
- 0 bugs après refactoring
- Protection contre perte de travail
- Documentation complète

---

**Dernière mise à jour:** 2025-11-06  
**Version:** 1.0.0

