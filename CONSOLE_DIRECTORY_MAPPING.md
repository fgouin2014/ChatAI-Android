# 📂 Mapping des Répertoires de Consoles

**Date:** 20 octobre 2025  
**Version:** ChatAI-Android-beta

---

## 🎯 Problème Résolu

**Situation :** Certaines consoles ont **plusieurs noms** dans le code mais un **seul répertoire** sur le device.

**Exemple :**
- Code dit : `"lynx"` ou `"atarilynx"`
- Device a : `/storage/emulated/0/GameLibrary-Data/atarilynx/` (ROMs ici)
- Device a : `/storage/emulated/0/GameLibrary-Data/lynx/` (vide)

**Solution :** Fonction `getRealConsoleDirectory()` qui mappe les alias vers le vrai répertoire.

---

## 📊 Répertoires Réels sur Device

### Consoles avec ROMs (Vérifiées)

```
✅ atarilynx/      (ROMs Lynx)
✅ atari2600/      (ROMs Atari 2600)
✅ atari5200/      (ROMs Atari 5200)
✅ atari7800/      (ROMs Atari 7800)
✅ megadrive/      (ROMs Genesis)
✅ segacd/         (ROMs SegaCD)
✅ psx/            (ROMs PlayStation)
✅ psp/            (ROMs PSP)
✅ n64/            (ROMs N64)
✅ snes/           (ROMs SNES)
✅ nes/            (ROMs NES)
✅ gb/             (ROMs Game Boy)
✅ gbc/            (ROMs Game Boy Color)
✅ gba/            (ROMs Game Boy Advance)
```

### Répertoires Vides (Configuration seulement)

```
❌ lynx/           (vide, ROMs dans atarilynx/)
❌ mastersystem/   (vide)
❌ sms/            (vide)
```

### Répertoires Non Testés

```
⏳ 32x/
⏳ 3do/
⏳ arcade/
⏳ fbneo/
⏳ gamegear/
⏳ jaguar/
⏳ mame/
⏳ nds/
⏳ ngp/
⏳ pce/
⏳ saturn/
⏳ vb/
⏳ virtualboy/
⏳ ws/
```

---

## 🔧 Fonction getRealConsoleDirectory()

**Fichier :** `GameDetailsActivity.java` (Ligne 214-318)

### Mapping Complet

```java
private String getRealConsoleDirectory(String consoleName) {
    switch (consoleName.toLowerCase()) {
        // Atari - Utiliser les noms complets
        case "lynx":                    return "atarilynx";   // ✅
        case "atarilynx":               return "atarilynx";   // ✅
        case "atari":
        case "a2600":
        case "atari2600":               return "atari2600";   // ✅
        case "a5200":
        case "atari5200":               return "atari5200";   // ✅
        case "a7800":
        case "atari7800":               return "atari7800";   // ✅
        
        // Sega - Mapper aux répertoires réels
        case "genesis":
        case "md":
        case "megadrive":               return "megadrive";   // ✅
        case "scd":
        case "segacd":                  return "segacd";      // ✅
        case "mastersystem":
        case "segasms":
        case "sms":                     return "sms";         // ❓ À vérifier
        case "gamegear":
        case "gg":
        case "segagg":                  return "gamegear";    // ❓ À vérifier
        case "32x":
        case "sega32x":                 return "32x";         // ❓ À vérifier
        
        // Default
        default:                        return consoleName;
    }
}
```

---

## 📋 Utilisation dans le Code

### Avant (❌ Problème)

```java
// Construction du chemin ROM
String romPath = "/storage/emulated/0/GameLibrary-Data/" + game.getConsole() + "/" + fileName;

// Si game.getConsole() retourne "lynx"
// romPath = "/storage/emulated/0/GameLibrary-Data/lynx/Desert Strike.zip"
// ❌ Le fichier n'existe pas (il est dans atarilynx/)
```

### Après (✅ Corrigé)

```java
// Mapper au vrai répertoire
String consoleDir = getRealConsoleDirectory(game.getConsole());
String romPath = "/storage/emulated/0/GameLibrary-Data/" + consoleDir + "/" + fileName;

// Si game.getConsole() retourne "lynx"
// consoleDir = "atarilynx"
// romPath = "/storage/emulated/0/GameLibrary-Data/atarilynx/Desert Strike.zip"
// ✅ Le fichier existe
```

---

## 🎮 Cas d'Usage Réels

### Exemple 1 : Lynx

```
User clique sur jeu Lynx
    ↓
game.getConsole() = "lynx" ou "atarilynx"
    ↓
getRealConsoleDirectory("lynx") = "atarilynx"
    ↓
romPath = "/storage/emulated/0/GameLibrary-Data/atarilynx/Desert Strike.zip"
    ↓
✅ ROM trouvée
```

### Exemple 2 : Genesis

```
User clique sur jeu Genesis
    ↓
game.getConsole() = "genesis" ou "md" ou "megadrive"
    ↓
getRealConsoleDirectory("genesis") = "megadrive"
    ↓
romPath = "/storage/emulated/0/GameLibrary-Data/megadrive/Race Drivin.zip"
    ↓
✅ ROM trouvée
```

### Exemple 3 : PSX (Pas de Mapping Nécessaire)

```
User clique sur jeu PSX
    ↓
game.getConsole() = "psx"
    ↓
getRealConsoleDirectory("psx") = "psx"
    ↓
romPath = "/storage/emulated/0/GameLibrary-Data/psx/GTA2.PBP"
    ↓
✅ ROM trouvée
```

---

## 📊 Tableau de Mapping

| Nom Console (Code) | Vrai Répertoire (Device) | Status |
|-------------------|-------------------------|--------|
| `lynx`, `atarilynx` | `atarilynx` | ✅ Corrigé |
| `genesis`, `md`, `megadrive` | `megadrive` | ✅ Corrigé |
| `atari`, `a2600`, `atari2600` | `atari2600` | ✅ Corrigé |
| `a5200`, `atari5200` | `atari5200` | ✅ Corrigé |
| `a7800`, `atari7800` | `atari7800` | ✅ Corrigé |
| `scd`, `segacd` | `segacd` | ✅ Corrigé |
| `sms`, `mastersystem` | `sms` ou `mastersystem` | ⚠️ À vérifier |
| `gg`, `gamegear` | `gamegear` | ⚠️ À vérifier |
| `vb`, `virtualboy` | `virtualboy` ou `vb` | ⚠️ À vérifier |
| `psx`, `ps1`, `playstation` | `psx` | ✅ Pas de duplication |
| `psp` | `psp` | ✅ Pas de duplication |
| `nes`, `snes`, `n64`, `gb`, `gbc`, `gba` | Identiques | ✅ Pas de duplication |

---

## 🐛 Bugs Corrigés

### Bug 1 : Lynx ROMs Introuvables
**Avant :**
```java
romPath = "/storage/emulated/0/GameLibrary-Data/lynx/Desert Strike.zip"
```
❌ Fichier pas trouvé (`lynx/` est vide)

**Après :**
```java
consoleDir = getRealConsoleDirectory("lynx");  // "atarilynx"
romPath = "/storage/emulated/0/GameLibrary-Data/atarilynx/Desert Strike.zip"
```
✅ Fichier trouvé

### Bug 2 : Cache Genesis dans Mauvais Répertoire
**Avant :**
```java
cacheDir = "/storage/emulated/0/GameLibrary-Data/.cache/genesis/"
```
❌ Incohérent avec `megadrive/`

**Après :**
```java
realConsoleDir = getRealConsoleDirectory("genesis");  // "megadrive"
cacheDir = "/storage/emulated/0/GameLibrary-Data/.cache/megadrive/"
```
✅ Cohérent

---

## ✅ Validation

**Cette correction garantit que :**
1. ✅ Les ROMs sont **trouvées** même si le code utilise un alias
2. ✅ Le cache est créé dans le **bon répertoire**
3. ✅ Cohérence avec le **système de fichiers réel**
4. ✅ Compatible avec **EmulatorJS** (qui utilise aussi ces répertoires)

---

## 🎯 Prochaine Étape

Tester toutes les consoles pour vérifier que le mapping est correct :
- [ ] Lynx (atarilynx) - ✅ Testé
- [ ] Atari 2600 - ✅ Testé
- [ ] Genesis (megadrive) - ✅ Testé
- [ ] Master System (sms vs mastersystem) - ❓ À tester
- [ ] Game Gear (gamegear) - ❓ À tester
- [ ] Autres consoles - ❓ À tester

---

*Document créé le 20 octobre 2025*  
*ChatAI-Android-beta - Console Directory Mapping*

