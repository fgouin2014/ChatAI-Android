# 🎮 CONSOLES NATIVES - CONFIGURATION FINALE

**Date:** 2025-10-19  
**Système:** LibretroDroid (Émulation native Android)

---

## ✅ CONSOLES NATIVES ACTIVÉES (10 consoles)

### Liste complète

| Console | Aliases | Core | Format ROM | Extraction .zip |
|---------|---------|------|------------|-----------------|
| **PSX** | ps1, playstation | pcsx_rearmed | `.PBP`, `.bin/.cue` | ❌ Non |
| **PSP** | - | ppsspp | `.ISO`, `.CSO` | ❌ Non |
| **N64** | - | parallel_n64 | `.z64`, `.n64` | ❌ Non |
| **SNES** | - | snes9x | `.sfc`, `.smc` | ❌ Non |
| **NES** | - | fceumm | `.nes` | ❌ Non |
| **GBA** | - | libmgba | `.gba` | ❌ Non |
| **GB/GBC** | gb, gbc | gambatte | `.gb`, `.gbc` | ❌ Non |
| **Lynx** | - | handy | `.lnx` | ❌ Non |
| **Genesis** | megadrive, md | genesis_plus_gx | `.bin` | ✅ **OUI** |
| **SegaCD** | scd | genesis_plus_gx | `.bin`, `.iso` | ✅ **OUI** |

**Total : 10 consoles natives (LibretroDroid)**

---

## 🔧 SYSTÈME D'EXTRACTION .ZIP

### Consoles avec cache asynchrone

**Genesis et SegaCD** nécessitent extraction .zip → .bin

**Pourquoi ?**
- Ces consoles utilisent des ROMs .zip (compressées)
- Le core Genesis Plus GX ne peut pas lire .zip directement
- Extraction nécessaire dans un cache

---

### Fonctionnement du cache

**Fichier :** `GameDetailsActivity.java`  
**Fonction :** `extractToCacheAsync()`

```java
// Consoles avec extraction automatique
if ((console.equals("genesis") || console.equals("megadrive") || console.equals("md") || 
     console.equals("scd") || console.equals("segacd")) && fileName.endsWith(".zip")) {
    extractToCacheAsync(zipPath, fileName, slot, console);
    return;  // Extraction en arrière-plan
}
```

**Flux :**
```
User lance jeu .zip
       ↓
Vérifier cache .cache/{console}/
       ↓
Si en cache → Lancer immédiatement
       ↓
Si pas en cache → Afficher ProgressDialog
       ↓
Thread arrière-plan extrait .zip → .bin
       ↓
Cache le .bin dans .cache/{console}/
       ↓
Lancer NativeComposeEmulatorActivity avec .bin
```

**Avantages :**
- ✅ Pas d'ANR (extraction en arrière-plan)
- ✅ ProgressDialog (feedback utilisateur)
- ✅ Cache réutilisé (instantané après 1ère fois)
- ✅ .zip conservés (compressés, backup)

---

### Structure du cache

```
/storage/emulated/0/GameLibrary-Data/
├── .cache/
│   ├── genesis/
│   │   ├── Sonic the Hedgehog.bin (2 MB)
│   │   ├── Streets of Rage 2.bin (3 MB)
│   │   └── ... (seulement jeux joués)
│   └── scd/
│       ├── Sonic CD.bin (450 MB)
│       └── ... (seulement jeux joués)
├── megadrive/
│   ├── Sonic the Hedgehog.zip (500 KB) ✅ CONSERVÉ
│   └── ... (758 .zip)
└── scd/
    └── Sonic CD.zip
```

**Taille typique du cache :**
- Genesis : 50-100 MB (10-20 jeux)
- SegaCD : 200-500 MB (2-5 jeux)
- Total : ~300 MB max

**Gain d'espace vs duplication complète :**
- Avant : .zip (700 MB) + .bin (1.4 GB) = 2.1 GB
- Après : .zip (700 MB) + cache (100 MB) = 800 MB
- **Gain : ~1.3 GB**

---

## 📊 CONSOLES PAR FORMAT

### Formats natifs (pas d'extraction)

**Ces consoles lisent directement leurs ROMs :**

| Console | Format | Exemple |
|---------|--------|---------|
| PSX | `.PBP` | 007.PBP |
| PSP | `.ISO`, `.CSO` | God of War.iso |
| N64 | `.z64`, `.n64` | Mario 64.z64 |
| SNES | `.sfc`, `.smc` | Super Mario World.sfc |
| NES | `.nes` | Super Mario Bros.nes |
| GBA | `.gba` | Pokemon.gba |
| GB/GBC | `.gb`, `.gbc` | Tetris.gb |
| Lynx | `.lnx` | California Games.lnx |

**Pas d'extraction, chargement direct !**

---

### Formats compressés (extraction requise)

**Ces consoles ont besoin d'extraction .zip → cache :**

| Console | Format stocké | Format requis | Cache |
|---------|---------------|---------------|-------|
| Genesis | `.zip` | `.bin` | `.cache/genesis/` |
| SegaCD | `.zip` | `.bin`, `.iso` | `.cache/scd/` |

**Extraction automatique en arrière-plan !**

---

## 🎯 CONFIGURATION UI

### Boutons natifs visibles pour

**Fichier :** `GameDetailsActivity.java`  
**Ligne :** 168-174

```java
if (console.equals("psx") || console.equals("n64") || console.equals("psp") || 
    console.equals("ps1") || console.equals("playstation") || console.equals("snes") ||
    console.equals("nes") || console.equals("gba") || console.equals("gb") || 
    console.equals("gbc") || console.equals("genesis") || console.equals("megadrive") || 
    console.equals("md") || console.equals("lynx") || console.equals("scd") || 
    console.equals("segacd")) {
    nativeButtonsContainer.setVisibility(View.VISIBLE);
}
```

**Boutons affichés :**
- ▶️ **PLAY NATIVE** - Lance avec LibretroDroid
- 💾 **LOAD SAVE** - Charge une sauvegarde (slots 1-5)
- 🎮 **CODES** - Ouvre le menu des cheats

---

## 🔄 SYSTÈME DE CACHE

### Extraction asynchrone

**Pour éviter ANR (Application Not Responding) :**

1. **Vérification rapide** (UI thread) :
   - Le .bin est en cache ? → Lancer immédiatement

2. **Extraction lente** (background thread) :
   - Afficher ProgressDialog
   - Extraire .zip → .bin en arrière-plan
   - Cacher le .bin dans `.cache/{console}/`
   - Lancer l'émulateur

**Temps :**
- 1ère fois : 2-3 secondes (avec dialogue)
- Fois suivantes : Instantané (cache)

---

### Nettoyage du cache

**Le cache peut être nettoyé manuellement :**

```bash
# Supprimer tout le cache
adb shell "rm -rf '/storage/emulated/0/GameLibrary-Data/.cache/'"

# Supprimer cache Genesis seulement
adb shell "rm -rf '/storage/emulated/0/GameLibrary-Data/.cache/genesis/'"

# Supprimer cache SegaCD seulement
adb shell "rm -rf '/storage/emulated/0/GameLibrary-Data/.cache/scd/'"
```

**Le cache sera recréé automatiquement au besoin.**

---

## 📝 CORES UTILISÉS

### Cores LibretroDroid (10 fichiers .so)

```
app/src/main/jniLibs/arm64-v8a/
├── fceumm_libretro_android.so           (NES)
├── gambatte_libretro_android.so         (GB/GBC)
├── handy_libretro_android.so            (Lynx)
├── libmgba_libretro_android.so          (GBA)
├── libparallel.so                       (Support N64)
├── parallel_n64_libretro_android.so     (N64)
├── pcsx_rearmed_libretro_android.so     (PSX)
├── ppsspp_libretro_android.so           (PSP)
├── snes9x_libretro_android.so           (SNES)
└── genesis_plus_gx_libretro_android.so  (Genesis + SegaCD)
```

**Total : 10 cores, 52.8 MB**

**Note :** Genesis Plus GX supporte à la fois Genesis/MegaDrive ET SegaCD.

---

## ✅ RÉSUMÉ FINAL

### Consoles natives : 10

1. ✅ PSX (PlayStation 1)
2. ✅ PSP (PlayStation Portable)
3. ✅ N64 (Nintendo 64)
4. ✅ SNES (Super Nintendo)
5. ✅ NES (Nintendo Entertainment System)
6. ✅ GBA (Game Boy Advance)
7. ✅ GB/GBC (Game Boy / Game Boy Color)
8. ✅ Lynx (Atari Lynx)
9. ✅ Genesis/MegaDrive (Sega Genesis)
10. ✅ SegaCD (Sega CD / Mega CD)

### Système d'extraction

- **Genesis :** ✅ Cache asynchrone
- **SegaCD :** ✅ Cache asynchrone
- **Autres :** ❌ Pas nécessaire (formats natifs)

### Performance

- **Chargement direct :** 8 consoles (instantané)
- **Extraction 1ère fois :** 2 consoles (2-3 sec avec dialogue)
- **Extraction fois suivantes :** Cache (instantané)

---

**L'application ChatAI-Android supporte 10 consoles avec émulation native LibretroDroid !** 🎮


