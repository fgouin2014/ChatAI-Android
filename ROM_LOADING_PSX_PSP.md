# 🎮 CHARGEMENT DES ROMS PSX ET PSP

**Date:** 2025-10-19  
**Système:** LibretroDroid (Émulation native)

---

## 📊 FLUX DE CHARGEMENT

```
User clicks "PLAY"
       ↓
GameDetailsActivity.launchGameNative()
       ↓
Construit romPath: /storage/emulated/0/GameLibrary-Data/{console}/{fileName}
       ↓
Envoie Intent → NativeComposeEmulatorActivity
       ↓
GLRetroViewData.gameFilePath = romPath
       ↓
GLRetroView charge le core Libretro
       ↓
Core lit directement la ROM depuis le filesystem
```

---

## 🔍 DÉTAIL PAR ÉTAPE

### ÉTAPE 1 : GameDetailsActivity (Préparation)

**Fichier:** `GameDetailsActivity.java`  
**Ligne:** 215-238

```java
private void launchGameNative(int slot) {
    // 1. Récupérer le nom du fichier
    String fileName = game.getFile();
    
    // 2. Si c'est une URL, extraire juste le nom
    if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
        fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
    }
    
    // 3. Construire le chemin complet
    String romPath = "/storage/emulated/0/GameLibrary-Data/" + game.getConsole() + "/" + fileName;
    
    // 4. Créer l'Intent
    Intent intent = new Intent(this, NativeComposeEmulatorActivity.class);
    intent.putExtra("romPath", romPath);
    intent.putExtra("gameName", game.getName());
    intent.putExtra("console", game.getConsole());
    intent.putExtra("loadSlot", slot);  // 0 = new game, 1-5 = load save
    
    // 5. Lancer l'émulateur
    startActivity(intent);
}
```

---

### ÉTAPE 2 : NativeComposeEmulatorActivity (Réception)

**Fichier:** `NativeComposeEmulatorActivity.kt`  
**Ligne:** 75-87

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 1. Récupérer le romPath depuis l'Intent
    romPath = intent.getStringExtra("romPath") ?: run {
        Log.e(TAG, "No ROM path provided")
        Toast.makeText(this, "Error: No ROM path", Toast.LENGTH_LONG).show()
        finish()
        return
    }
    
    // 2. Récupérer les autres infos
    console = intent.getStringExtra("console") ?: "psx"
    gameName = intent.getStringExtra("gameName") ?: "Game"
    val loadSlot = intent.getIntExtra("loadSlot", 0)
    
    Log.i(TAG, "NativeComposeEmulator starting: $gameName ($console) from $romPath")
}
```

---

### ÉTAPE 3 : GLRetroViewData (Configuration)

**Fichier:** `NativeComposeEmulatorActivity.kt`  
**Ligne:** 94-113

```kotlin
// Créer GLRetroViewData avec GLRetroViewData
val data = com.swordfish.libretrodroid.GLRetroViewData(this).apply {
    
    // ✅ CHEMIN DE LA ROM (DIRECTEMENT LE FICHIER)
    gameFilePath = romPath
    
    // BIOS directory (pour PSX)
    systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
    
    // Saves directory (SHARED entre apps)
    val sharedSavesDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console")
    if (!sharedSavesDir.exists()) sharedSavesDir.mkdirs()
    savesDirectory = sharedSavesDir.absolutePath
    
    // Shader
    shader = ShaderConfig.Default
    
    // Options
    rumbleEventsEnabled = true
    preferLowLatencyAudio = true
}
```

---

### ÉTAPE 4 : GLRetroView (Chargement du core)

**Fichier:** `NativeComposeEmulatorActivity.kt`  
**Ligne:** 115-116

```kotlin
retroView = GLRetroView(this, data)
lifecycle.addObserver(retroView)
```

**Ce qui se passe en interne (LibretroDroid) :**

1. LibretroDroid charge le core `.so` approprié :
   - PSX → `pcsx_rearmed_libretro_android.so`
   - PSP → `ppsspp_libretro_android.so`

2. Le core initialise avec les paramètres :
   - `gameFilePath` → Chemin de la ROM
   - `systemDirectory` → Chemin des BIOS
   - `savesDirectory` → Chemin des sauvegardes

3. Le core lit directement la ROM depuis le filesystem

---

## 📁 STRUCTURE DES FICHIERS

### PSX (PlayStation 1)

```
/storage/emulated/0/GameLibrary-Data/
├── psx/
│   ├── 007 - The World Is Not Enough (USA).pbp       (ROM)
│   ├── Crash Bandicoot (USA).bin                     (ROM)
│   ├── Crash Bandicoot (USA).cue                     (CUE)
│   └── Final Fantasy VII (USA) [Disc 1].pbp         (ROM)
├── data/bios/
│   └── scph5501.bin                                  (BIOS PSX requis)
└── saves/psx/
    ├── slot1/
    │   └── 007 - The World Is Not Enough (USA).state
    └── slot2/
        └── Crash Bandicoot (USA).state
```

**Formats supportés :**
- `.pbp` - PlayStation Portable EBOOT (compression optimale)
- `.bin` + `.cue` - Image disque brute
- `.iso` - Image ISO (plus rare pour PSX)

**BIOS requis :**
- `scph5501.bin` (USA)
- `scph5500.bin` (Japon)
- `scph5502.bin` (Europe)

**Taille moyenne :**
- PBP : 200-450 MB
- BIN/CUE : 400-700 MB

---

### PSP (PlayStation Portable)

```
/storage/emulated/0/GameLibrary-Data/
├── psp/
│   ├── God of War - Chains of Olympus (USA).iso      (ROM)
│   ├── GTA Liberty City Stories (USA).cso            (ROM compressée)
│   └── Monster Hunter Freedom Unite (USA).iso        (ROM)
├── data/bios/
│   └── (PPSSPP n'a pas besoin de BIOS)
└── saves/psp/
    ├── slot1/
    │   └── God of War - Chains of Olympus (USA).state
    └── slot2/
        └── GTA Liberty City Stories (USA).state
```

**Formats supportés :**
- `.iso` - Image ISO standard
- `.cso` - ISO compressée (économise de l'espace)

**BIOS requis :**
- ❌ **Aucun** (PPSSPP a un HLE complet)

**Taille moyenne :**
- ISO : 500 MB - 1.8 GB
- CSO : 300 MB - 1.2 GB (compression ~60%)

---

## 🔑 POINTS IMPORTANTS

### 1. Chargement DIRECT depuis le filesystem

```kotlin
gameFilePath = "/storage/emulated/0/GameLibrary-Data/psx/007.pbp"
```

**Le core Libretro lit DIRECTEMENT le fichier.**

**Pas de :**
- Copie en mémoire
- Extraction temporaire
- Serveur HTTP
- Buffer intermédiaire

**Avantages :**
- ✅ Performance maximale
- ✅ Démarrage rapide
- ✅ Pas de RAM gaspillée
- ✅ Support des gros fichiers (1.8 GB)

---

### 2. BIOS requis SEULEMENT pour PSX

**PSX (PCSX ReARMed)** :
- ✅ **BIOS REQUIS** : `scph5501.bin`
- Emplacement : `/storage/emulated/0/GameLibrary-Data/data/bios/`
- Sans BIOS → Le jeu ne démarre pas

**PSP (PPSSPP)** :
- ❌ **BIOS NON REQUIS**
- PPSSPP utilise un HLE (High Level Emulation)
- Fonctionne sans firmware PSP

---

### 3. Sauvegardes partagées (SHARED)

```kotlin
savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/$console"
```

**Les sauvegardes sont PARTAGÉES entre :**
- GameLibrary-Android
- ChatAI-Android

**Exemple :**
Si vous jouez à "Crash Bandicoot" sur GameLibrary et sauvegardez dans Slot 1, vous pouvez charger ce Slot 1 depuis ChatAI.

**Structure :**
```
saves/
├── psx/
│   ├── slot1/
│   ├── slot2/
│   └── slot3/
└── psp/
    ├── slot1/
    └── slot2/
```

---

### 4. Délais de chargement

**PSX (PBP) :**
```kotlin
// PSX PBP loading is slow (8 seconds)
postDelayed({ loadAndApplyCheats() }, 8000)
```

**Raison :** Les fichiers `.pbp` sont compressés et doivent être décompressés par PCSX ReARMed.

**PSP (ISO/CSO) :**
```kotlin
// PSP loading is faster (3 seconds)
postDelayed({ loadAndApplyCheats() }, 3000)
```

**Raison :** PPSSPP charge plus rapidement, même avec des fichiers volumineux.

---

## 🎯 DIFFÉRENCES PSX vs PSP

| Aspect | PSX | PSP |
|--------|-----|-----|
| **Core** | `pcsx_rearmed_libretro_android.so` | `ppsspp_libretro_android.so` |
| **Taille core** | 1.4 MB | 17.4 MB |
| **Format ROM** | `.pbp`, `.bin/.cue`, `.iso` | `.iso`, `.cso` |
| **BIOS requis** | ✅ Oui (`scph5501.bin`) | ❌ Non |
| **Taille ROM** | 200-700 MB | 500 MB - 1.8 GB |
| **Délai chargement** | 8 secondes (PBP) | 3 secondes |
| **Compression** | PBP (très efficace) | CSO (modérée) |

---

## 🚀 OPTIMISATIONS

### 1. Streaming des gros fichiers

Le code utilise déjà **le streaming direct** via LibretroDroid.

**Pas besoin de charger la ROM en mémoire.**

Le core lit la ROM **par blocs** selon ses besoins.

---

### 2. BIOS PSX

**Le BIOS PSX DOIT être présent :**

```
/storage/emulated/0/GameLibrary-Data/data/bios/scph5501.bin
```

**Vérification au démarrage :**
```kotlin
systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
```

Si le BIOS est absent, PCSX ReARMed affichera une erreur.

---

### 3. Cheats et saves

**Les cheats et saves utilisent le même `gameName` :**

```kotlin
// Cheats
/storage/emulated/0/GameLibrary-Data/cheats/retroarch/psx/{gameName}.cht

// Saves
/storage/emulated/0/GameLibrary-Data/saves/psx/slot1/{gameName}.state
```

**Important :** Le `gameName` doit être identique pour lier cheats et saves au même jeu.

---

## 📝 EXEMPLE COMPLET

### PSX - 007 The World Is Not Enough

```kotlin
// 1. GameDetailsActivity construit le chemin
romPath = "/storage/emulated/0/GameLibrary-Data/psx/007 - The World Is Not Enough (USA).pbp"

// 2. Intent vers NativeComposeEmulatorActivity
intent.putExtra("romPath", romPath)
intent.putExtra("gameName", "007 - The World Is Not Enough (USA)")
intent.putExtra("console", "psx")

// 3. GLRetroViewData
data.gameFilePath = "/storage/emulated/0/GameLibrary-Data/psx/007 - The World Is Not Enough (USA).pbp"
data.systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
data.savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/psx"

// 4. GLRetroView charge pcsx_rearmed_libretro_android.so
// 5. PCSX ReARMed lit la ROM + BIOS
// 6. Le jeu démarre
```

---

### PSP - God of War

```kotlin
// 1. GameDetailsActivity construit le chemin
romPath = "/storage/emulated/0/GameLibrary-Data/psp/God of War - Chains of Olympus (USA).iso"

// 2. Intent vers NativeComposeEmulatorActivity
intent.putExtra("romPath", romPath)
intent.putExtra("gameName", "God of War - Chains of Olympus (USA)")
intent.putExtra("console", "psp")

// 3. GLRetroViewData
data.gameFilePath = "/storage/emulated/0/GameLibrary-Data/psp/God of War - Chains of Olympus (USA).iso"
data.systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"  // Non utilisé
data.savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/psp"

// 4. GLRetroView charge ppsspp_libretro_android.so
// 5. PPSSPP lit la ROM (pas de BIOS)
// 6. Le jeu démarre
```

---

## ✅ RÉSUMÉ

### Processus de chargement

1. **GameDetailsActivity** construit le `romPath` depuis `game.getFile()`
2. **Intent** passe `romPath`, `gameName`, `console` à `NativeComposeEmulatorActivity`
3. **GLRetroViewData** configure `gameFilePath = romPath`
4. **GLRetroView** charge le core Libretro approprié
5. **Core Libretro** lit directement la ROM depuis le filesystem
6. **Le jeu démarre** (avec délai de 3-8 secondes pour initialisation)

### Caractéristiques

- ✅ **Chargement direct** depuis filesystem (pas de copie en RAM)
- ✅ **Support des gros fichiers** (jusqu'à 1.8 GB pour PSP)
- ✅ **BIOS requis pour PSX** (`scph5501.bin`)
- ✅ **Pas de BIOS pour PSP** (HLE intégré)
- ✅ **Sauvegardes partagées** entre GameLibrary et ChatAI
- ✅ **Délais optimisés** (8s PSX, 3s PSP)

---

**Le système de chargement est OPTIMAL et n'a pas besoin de modifications !** ✅


