# 🎮 AJOUTER LE SUPPORT GENESIS/MEGADRIVE

**Date:** 2025-10-19  
**Console:** Sega Genesis / Mega Drive  
**Méthode:** LibretroDroid (chargement direct filesystem)

---

## 🎯 OBJECTIF

Ajouter le support de la Sega Genesis/Mega Drive en utilisant **la même méthode que PSX/PSP** : LibretroDroid avec chargement direct depuis le filesystem.

---

## 📊 STATUS ACTUEL

| Aspect | Status |
|--------|--------|
| **Core disponible** | ❌ **ABSENT** |
| **Code préparé** | ✅ **PRÊT** (supprimé temporairement) |
| **Structure fichiers** | ✅ **PRÊTE** |
| **Méthode chargement** | ✅ **COMPATIBLE** |

**Le seul élément manquant : Le fichier `.so` du core !**

---

## 🔧 ÉTAPES D'INSTALLATION

### ÉTAPE 1 : Obtenir le core Libretro

**Deux options pour le core Genesis :**

#### OPTION A : genesis_plus_gx (Recommandé - Précision)
- **Nom:** `genesis_plus_gx_libretro_android.so`
- **Avantages:** Très précis, excellent son
- **Taille:** ~500 KB

#### OPTION B : picodrive (Recommandé - Performance)
- **Nom:** `picodrive_libretro_android.so`
- **Avantages:** Plus rapide, supporte aussi 32X et Sega CD
- **Taille:** ~800 KB

---

### ÉTAPE 2 : Télécharger le core

**Source officielle : LibretroDroid Buildbot**

```powershell
# URL Buildbot LibretroDroid
https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/

# Cores disponibles :
# - genesis_plus_gx_libretro_android.so.zip
# - picodrive_libretro_android.so.zip
```

**Téléchargement manuel :**

1. Aller sur https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/
2. Chercher `genesis_plus_gx_libretro_android.so.zip` ou `picodrive_libretro_android.so.zip`
3. Télécharger le fichier
4. Extraire le fichier `.so`

---

### ÉTAPE 3 : Placer le core dans le projet

```powershell
# Destination
C:\androidProject\ChatAI-Android-beta\ChatAI-Android\app\src\main\jniLibs\arm64-v8a\

# Copier le core
Copy-Item "genesis_plus_gx_libretro_android.so" "app\src\main\jniLibs\arm64-v8a\"

# OU (si PicoDrive)
Copy-Item "picodrive_libretro_android.so" "app\src\main\jniLibs\arm64-v8a\"
```

**Vérification :**
```powershell
Get-ChildItem "app\src\main\jniLibs\arm64-v8a\" | Where-Object {$_.Name -like "*genesis*" -or $_.Name -like "*pico*"}
```

---

### ÉTAPE 4 : Ajouter Genesis dans le code

**Fichier:** `app/src/main/java/com/chatai/NativeComposeEmulatorActivity.kt`  
**Ligne:** 189-201

#### Pour genesis_plus_gx

```kotlin
private fun getCorePath(console: String): String {
    return when (console.lowercase()) {
        "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
        "psp" -> "ppsspp_libretro_android.so"
        "n64" -> "parallel_n64_libretro_android.so"
        "snes" -> "snes9x_libretro_android.so"
        "nes" -> "fceumm_libretro_android.so"
        "gba" -> "libmgba_libretro_android.so"
        "gb", "gbc" -> "gambatte_libretro_android.so"
        "lynx" -> "handy_libretro_android.so"
        "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"  // ✅ AJOUTER
        else -> "fceumm_libretro_android.so"
    }
}
```

#### Pour picodrive

```kotlin
private fun getCorePath(console: String): String {
    return when (console.lowercase()) {
        "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
        "psp" -> "ppsspp_libretro_android.so"
        "n64" -> "parallel_n64_libretro_android.so"
        "snes" -> "snes9x_libretro_android.so"
        "nes" -> "fceumm_libretro_android.so"
        "gba" -> "libmgba_libretro_android.so"
        "gb", "gbc" -> "gambatte_libretro_android.so"
        "lynx" -> "handy_libretro_android.so"
        "genesis", "megadrive", "md", "32x", "scd" -> "picodrive_libretro_android.so"  // ✅ AJOUTER
        else -> "fceumm_libretro_android.so"
    }
}
```

---

### ÉTAPE 5 : Compiler et installer

```powershell
# Nettoyer
.\gradlew clean

# Compiler et installer
.\gradlew installDebug
```

---

## 📁 STRUCTURE DES FICHIERS GENESIS

### ROMs Genesis

```
/storage/emulated/0/GameLibrary-Data/
└── genesis/
    ├── Sonic the Hedgehog (USA, Europe).bin
    ├── Streets of Rage 2 (USA).bin
    └── Mortal Kombat (USA).bin
```

**Formats supportés :**
- `.bin` - Binaire brut (le plus courant)
- `.smd` - Super Magic Drive
- `.md` - Mega Drive (identique à .bin)

**Taille moyenne :** 512 KB - 4 MB

---

### Sauvegardes

```
/storage/emulated/0/GameLibrary-Data/
└── saves/genesis/
    ├── slot1/
    │   └── Sonic the Hedgehog (USA, Europe).state
    └── slot2/
        └── Streets of Rage 2 (USA).state
```

---

### Cheats

```
/storage/emulated/0/GameLibrary-Data/
└── cheats/
    ├── retroarch/genesis/
    │   └── Sonic the Hedgehog (USA, Europe).cht
    └── user/genesis/
        └── Sonic the Hedgehog (USA, Europe).cht
```

---

## 🎮 CHARGEMENT DES ROMS (Même méthode que PSX/PSP)

### Flux complet

```
User clique "PLAY" sur jeu Genesis
       ↓
GameDetailsActivity.launchGameNative()
       ↓
romPath = /storage/emulated/0/GameLibrary-Data/genesis/Sonic.bin
       ↓
Intent → NativeComposeEmulatorActivity
       ↓
GLRetroViewData.gameFilePath = romPath
       ↓
GLRetroView charge genesis_plus_gx_libretro_android.so
       ↓
Core lit DIRECTEMENT Sonic.bin depuis filesystem
       ↓
Jeu démarre
```

### Code de chargement

```kotlin
// IDENTIQUE à PSX/PSP !
val data = GLRetroViewData(this).apply {
    gameFilePath = romPath  // ✅ CHARGEMENT DIRECT
    systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"  // Pas de BIOS pour Genesis
    savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/genesis"
}

retroView = GLRetroView(this, data)
```

**C'est EXACTEMENT la même méthode que PSX/PSP !**

---

## 📊 COMPARAISON DES CORES

| Aspect | genesis_plus_gx | picodrive |
|--------|-----------------|-----------|
| **Précision** | ✅ Excellente | 🟡 Très bonne |
| **Performance** | 🟡 Bonne | ✅ Excellente |
| **Son** | ✅ Excellent | 🟡 Bon |
| **32X** | ❌ Non | ✅ Oui |
| **Sega CD** | ❌ Non | ✅ Oui |
| **Taille** | ~500 KB | ~800 KB |
| **Recommandé pour** | Précision | Performance + 32X/CD |

**Recommandation :** `genesis_plus_gx` pour la plupart des jeux Genesis/Mega Drive standard.

---

## ⚙️ CONFIGURATION GENESIS

### BIOS

**Genesis/Mega Drive :**
- ❌ **BIOS NON REQUIS**
- Les cores Genesis n'ont pas besoin de BIOS

**Sega CD (si picodrive) :**
- ✅ **BIOS REQUIS**
- Fichiers : `bios_CD_U.bin`, `bios_CD_E.bin`, `bios_CD_J.bin`
- Emplacement : `/storage/emulated/0/GameLibrary-Data/data/bios/`

---

## 🎯 AVANTAGES DE LA MÉTHODE LIBRETRODROID

### Comme PSX/PSP

- ✅ **Chargement DIRECT** depuis filesystem
- ✅ **Pas de serveur HTTP**
- ✅ **Performance maximale**
- ✅ **Support cheats intégré**
- ✅ **Sauvegardes natives**
- ✅ **TouchScreen natif**

### Spécifique Genesis

- ✅ **ROMs légères** (512 KB - 4 MB)
- ✅ **Pas de BIOS requis**
- ✅ **Chargement instantané**
- ✅ **Format simple** (.bin)

---

## 📝 EXEMPLE COMPLET

### Genesis - Sonic the Hedgehog

```kotlin
// 1. GameDetailsActivity construit le chemin
romPath = "/storage/emulated/0/GameLibrary-Data/genesis/Sonic the Hedgehog (USA, Europe).bin"

// 2. Intent vers NativeComposeEmulatorActivity
intent.putExtra("romPath", romPath)
intent.putExtra("gameName", "Sonic the Hedgehog (USA, Europe)")
intent.putExtra("console", "genesis")

// 3. GLRetroViewData
data.gameFilePath = "/storage/emulated/0/GameLibrary-Data/genesis/Sonic the Hedgehog (USA, Europe).bin"
data.systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"  // Non utilisé
data.savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/genesis"

// 4. GLRetroView charge genesis_plus_gx_libretro_android.so
// 5. Core lit la ROM directement
// 6. Le jeu démarre instantanément
```

---

## 🚀 SCRIPT D'INSTALLATION AUTOMATIQUE

```powershell
# add_genesis_support.ps1

$coreName = "genesis_plus_gx_libretro_android.so"
$destPath = "app\src\main\jniLibs\arm64-v8a\$coreName"

Write-Host "`n=== AJOUT DU SUPPORT GENESIS ===" -ForegroundColor Cyan

# Vérifier si le core existe déjà
if (Test-Path $destPath) {
    Write-Host "✓ Core Genesis déjà présent" -ForegroundColor Green
    exit 0
}

Write-Host "`n[1/3] Téléchargement du core..." -ForegroundColor Yellow
Write-Host "URL: https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/$coreName.zip" -ForegroundColor Gray

# L'utilisateur doit télécharger manuellement
Write-Host "`nVeuillez télécharger le core manuellement:" -ForegroundColor Yellow
Write-Host "  1. Aller sur https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/" -ForegroundColor White
Write-Host "  2. Télécharger $coreName.zip" -ForegroundColor White
Write-Host "  3. Extraire le fichier .so" -ForegroundColor White
Write-Host "  4. Placer dans: $destPath" -ForegroundColor White

Write-Host "`n[2/3] Modification du code..." -ForegroundColor Yellow
Write-Host "Ajouter dans NativeComposeEmulatorActivity.kt:" -ForegroundColor Gray
Write-Host '  "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"' -ForegroundColor White

Write-Host "`n[3/3] Compilation..." -ForegroundColor Yellow
Write-Host "  .\gradlew clean" -ForegroundColor Gray
Write-Host "  .\gradlew installDebug" -ForegroundColor Gray

Write-Host "`n✅ Suivez ces étapes pour activer Genesis !`n" -ForegroundColor Green
```

---

## ✅ RÉSULTAT FINAL

### Après ajout du core

| Console | Core | Status |
|---------|------|--------|
| PSX | `pcsx_rearmed` | ✅ Fonctionnel |
| PSP | `ppsspp` | ✅ Fonctionnel |
| N64 | `parallel_n64` | ✅ Fonctionnel |
| SNES | `snes9x` | ✅ Fonctionnel |
| NES | `fceumm` | ✅ Fonctionnel |
| GBA | `libmgba` | ✅ Fonctionnel |
| GB/GBC | `gambatte` | ✅ Fonctionnel |
| Lynx | `handy` | ✅ Fonctionnel |
| **Genesis** | `genesis_plus_gx` | ✅ **FONCTIONNEL** |

**Consoles fonctionnelles : 9/9 (100%)**

---

## 🎯 CONCLUSION

**Genesis utilisera EXACTEMENT la même méthode que PSX/PSP :**

1. ✅ Chargement DIRECT filesystem (pas de HTTP)
2. ✅ LibretroDroid avec core `.so` natif
3. ✅ Performance maximale
4. ✅ Support cheats intégré
5. ✅ Sauvegardes natives

**La seule chose à faire : Ajouter le fichier `.so` du core !**

---

**Une fois le core ajouté, Genesis sera 100% compatible avec la même architecture que PSX/PSP.** 🎮


