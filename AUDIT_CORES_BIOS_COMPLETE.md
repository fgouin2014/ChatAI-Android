# 🔍 Audit Complet - Cores, BIOS & Fichiers Requis

**Date:** 20 octobre 2025  
**Cores installés:** 20  
**BIOS disponibles:** 40+

---

## ✅ Cores Installés (20)

| # | Fichier Core | Console(s) | BIOS Requis | Status BIOS |
|---|--------------|------------|-------------|-------------|
| 1 | `fceumm_libretro_android.so` | **NES** | ❌ Aucun | ✅ OK |
| 2 | `snes9x_libretro_android.so` | **SNES** | ❌ Aucun | ✅ OK |
| 3 | `parallel_n64_libretro_android.so` | **N64** | ❌ Aucun | ✅ OK |
| 4 | `gambatte_libretro_android.so` | **GB/GBC** | ⚙️ Optionnel | ✅ `gb_bios.bin`, `gbc_bios.bin` |
| 5 | `libmgba_libretro_android.so` | **GBA** | ⚙️ Optionnel | ✅ `gba_bios.bin` |
| 6 | `pcsx_rearmed_libretro_android.so` | **PSX** | ✅ Requis | ✅ `scph5501.bin` (+ autres) |
| 7 | `ppsspp_libretro_android.so` | **PSP** | ❌ Aucun | ✅ OK |
| 8 | `genesis_plus_gx_libretro_android.so` | **Genesis, SegaCD, SMS, Game Gear** | ⚙️ SegaCD requis | ✅ `bios_CD_U/E/J.bin` |
| 9 | `mednafen_lynx_libretro_android.so` | **Lynx** | ✅ Requis | ✅ `lynxboot.img` |
| 10 | `stella2014_libretro_android.so` | **Atari 2600** | ❌ Aucun | ✅ OK |
| 11 | `prosystem_libretro_android.so` | **Atari 7800** | ⚙️ Optionnel | ✅ OK |
| 12 | `a5200_libretro_android.so` | **Atari 5200** | ⚙️ Optionnel | ✅ `5200.rom` |
| 13 | `mame2003_plus_libretro_android.so` | **Arcade (MAME)** | ⚙️ Par ROM | ✅ `mame2003-plus/` |
| 14 | `fbneo_libretro_android.so` | **Arcade (FBNeo)** | ⚙️ Par ROM | ✅ `neogeo.zip` |
| 15 | `picodrive_libretro_android.so` | **32X** | ❌ Aucun | ✅ OK |
| 16 | `mednafen_ngp_libretro_android.so` | **Neo Geo Pocket** | ❌ Aucun | ✅ OK |
| 17 | `mednafen_wswan_libretro_android.so` | **WonderSwan** | ❌ Aucun | ✅ OK |
| 18 | `mednafen_pce_libretro_android.so` | **PC Engine** | ⚙️ Optionnel | ✅ `syscard3.pce` |
| 19 | `handy_libretro_android.so` | **Lynx (ancien)** | ✅ Requis | ⚠️ **NON UTILISÉ** (remplacé par Beetle Lynx) |
| 20 | `libparallel.so` | (Dépendance N64) | - | ✅ OK |

---

## 📊 Récapitulatif par Console

### ✅ Consoles COMPLÈTES (Core + BIOS OK)

| Console | Core | BIOS | Fichiers Additionnels | Status |
|---------|------|------|----------------------|--------|
| **NES** | FCEUmm | ❌ | - | ✅ Complet |
| **SNES** | Snes9x | ❌ | - | ✅ Complet |
| **N64** | Parallel N64 | ❌ | `libparallel.so` | ✅ Complet |
| **GB/GBC** | Gambatte | ✅ (opt) | `gb_bios.bin`, `gbc_bios.bin` | ✅ Complet |
| **GBA** | mGBA | ✅ (opt) | `gba_bios.bin` | ✅ Complet |
| **PSX** | PCSX ReARMed | ✅ | `scph5501.bin` | ✅ Complet |
| **PSP** | PPSSPP | ❌ | - | ✅ Complet |
| **Genesis** | Genesis Plus GX | ❌ | - | ✅ Complet |
| **SegaCD** | Genesis Plus GX | ✅ | `bios_CD_U.bin` | ✅ Complet |
| **Master System** | Genesis Plus GX | ❌ | - | ✅ Complet |
| **Game Gear** | Genesis Plus GX | ❌ | - | ✅ Complet |
| **Lynx** | Beetle Lynx | ✅ | `lynxboot.img` | ✅ Complet |
| **Atari 2600** | Stella2014 | ❌ | - | ✅ Complet |
| **Atari 7800** | ProSystem | ❌ | - | ✅ Complet |
| **Atari 5200** | A5200 | ⚙️ (opt) | `5200.rom` | ✅ Complet |
| **32X** | PicoDrive | ❌ | - | ✅ Complet |
| **Neo Geo Pocket** | Mednafen NGP | ❌ | - | ✅ Complet |
| **WonderSwan** | Mednafen WSwan | ❌ | - | ✅ Complet |
| **PC Engine** | Mednafen PCE | ⚙️ (opt) | `syscard3.pce` | ✅ Complet |
| **MAME** | MAME 2003 Plus | ⚙️ | `mame2003-plus/` | ✅ Complet |
| **FBNeo** | FBNeo | ⚙️ | `neogeo.zip` | ✅ Complet |

---

## 🎯 Consoles Potentielles (BIOS Présents, Core Manquant)

| Console | BIOS Disponible | Core Manquant | Action Requise |
|---------|-----------------|---------------|----------------|
| **Nintendo DS** | ✅ `bios7.bin`, `bios9.bin`, `firmware.bin` | `melonds_libretro_android.so` | Télécharger core |
| **Saturn** | ✅ `saturn_bios.bin` | `mednafen_saturn_libretro_android.so` | Télécharger core |
| **Dreamcast** | ✅ `dc_boot.bin`, `dc_flash.bin` | `flycast_libretro_android.so` | Télécharger core |
| **3DO** | ✅ `panafz1.bin`, `panafz10.bin` | `opera_libretro_android.so` | Télécharger core |

**Ces consoles pourraient être ajoutées facilement car les BIOS sont déjà présents !**

---

## ⚠️ BIOS Inutiles (Sans Core Correspondant)

| BIOS | Console | Core Disponible | Status |
|------|---------|-----------------|--------|
| `ATARIBAS.ROM`, `ATARIOSA.ROM`, etc. | Atari 8-bit | ❌ Non | ⚠️ Inutile |
| `MSX.ROM`, `MSX2.ROM`, etc. | MSX | ❌ Non | ⚠️ Inutile |
| `mpr-17933.bin` | Saturn BIOS alt | ❌ Non (pas de core Saturn) | ⚠️ Inutile |
| `sega_101.bin` | Saturn BIOS alt | ❌ Non | ⚠️ Inutile |
| `sgb_bios.bin`, `sgb_boot.bin` | Super Game Boy | ⚙️ Optionnel (Gambatte) | ✅ OK |
| `scd_E/J/U.brm` | SegaCD RAM | ✅ Genesis Plus GX | ✅ OK |

---

## 🗑️ Core Inutile à Supprimer

| Core | Raison | Action |
|------|--------|--------|
| `handy_libretro_android.so` | Remplacé par `mednafen_lynx_libretro_android.so` | **SUPPRIMER** |

**Commande :**
```bash
Remove-Item app\src\main\jniLibs\arm64-v8a\handy_libretro_android.so
```

---

## 📋 Checklist Complète

### ✅ Cores Actifs (19)

1. ✅ **FCEUmm** → NES
2. ✅ **Snes9x** → SNES
3. ✅ **Parallel N64** → N64 (+ libparallel.so)
4. ✅ **Gambatte** → GB/GBC
5. ✅ **mGBA** → GBA
6. ✅ **PCSX ReARMed** → PSX
7. ✅ **PPSSPP** → PSP
8. ✅ **Genesis Plus GX** → Genesis/SegaCD/SMS/Game Gear
9. ✅ **Beetle Lynx** → Lynx
10. ✅ **Stella2014** → Atari 2600
11. ✅ **ProSystem** → Atari 7800
12. ✅ **A5200** → Atari 5200
13. ✅ **MAME 2003 Plus** → Arcade MAME
14. ✅ **FBNeo** → Arcade FBNeo
15. ✅ **PicoDrive** → 32X
16. ✅ **Mednafen NGP** → Neo Geo Pocket
17. ✅ **Mednafen WSwan** → WonderSwan
18. ✅ **Mednafen PCE** → PC Engine

### ⚠️ Cores à Supprimer (1)

19. ❌ **Handy** → Obsolète (remplacé)

---

## 🎮 BIOS Requis vs Disponibles

### ✅ BIOS Requis (100% Complets)

| Console | BIOS Requis | Fichier | Status |
|---------|-------------|---------|--------|
| **PSX** | Oui | `scph5501.bin` (USA) | ✅ Présent |
| | | `scph5500.bin` (JP) | ✅ Présent |
| | | `scph5502.bin` (EU) | ✅ Présent |
| **Lynx** | Oui | `lynxboot.img` | ✅ Présent |
| **SegaCD** | Oui | `bios_CD_U.bin` (USA) | ✅ Présent |
| | | `bios_CD_E.bin` (EU) | ✅ Présent |
| | | `bios_CD_J.bin` (JP) | ✅ Présent |

### ✅ BIOS Optionnels (Tous Présents)

| Console | BIOS Optionnel | Fichier | Status |
|---------|----------------|---------|--------|
| **GB/GBC** | Améliore boot | `gb_bios.bin`, `gbc_bios.bin` | ✅ Présent |
| **GBA** | Améliore compat | `gba_bios.bin` | ✅ Présent |
| **Atari 5200** | Améliore compat | `5200.rom` | ✅ Présent |
| **PC Engine** | CD support | `syscard3.pce` | ✅ Présent |
| **Neo Geo (FBNeo)** | ROM set | `neogeo.zip` | ✅ Présent |

### ✅ BIOS Futurs (Consoles Non Actives)

| Console | BIOS | Core Manquant | Status |
|---------|------|---------------|--------|
| **Nintendo DS** | `bios7.bin`, `bios9.bin`, `firmware.bin` | melonDS | ⏳ BIOS OK, core manquant |
| **Saturn** | `saturn_bios.bin` | Beetle Saturn | ⏳ BIOS OK, core manquant |
| **Dreamcast** | `dc_boot.bin`, `dc_flash.bin` | Flycast | ⏳ BIOS OK, core manquant |
| **3DO** | `panafz1.bin`, `panafz10.bin` | Opera | ⏳ BIOS OK, core manquant |

---

## 🔧 Fichiers Spéciaux

### MAME 2003 Plus

**Répertoire :** `/storage/emulated/0/GameLibrary-Data/data/bios/mame2003-plus/`

<function_calls>
<invoke name="run_terminal_cmd">
<parameter name="command">adb shell "ls /storage/emulated/0/GameLibrary-Data/data/bios/mame2003-plus/ 2>&1 | wc -l"
