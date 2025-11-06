# 🗑️ Nettoyage des Répertoires ROM Vides

**Date:** 20 octobre 2025  
**Analyse:** 31 répertoires scannés  

---

## 📊 Résultats du Scan

### ✅ Répertoires AVEC ROMs (15) - À GARDER

| Répertoire | ROMs | Description |
|------------|------|-------------|
| `nes` | 706 | ✅ Collection complète |
| `megadrive` | 765 | ✅ Collection complète |
| `atari2600` | 463 | ✅ Collection complète |
| `atari7800` | 58 | ✅ Testé et fonctionnel |
| `snes` | 14 | ✅ Testé et fonctionnel |
| `fbneo` | 9 | ✅ Testé et fonctionnel |
| `psx` | 9 | ✅ Testé et fonctionnel |
| `segacd` | 7 | ✅ Testé et fonctionnel |
| `gamegear` | 7 | ✅ Avec ROMs |
| `gba` | 7 | ✅ Testé et fonctionnel |
| `n64` | 6 | ✅ Testé et fonctionnel |
| `psp` | 5 | ✅ Testé et fonctionnel |
| `gb` | 4 | ✅ Testé et fonctionnel |
| `atarilynx` | 3 | ✅ Testé et fonctionnel |
| `gbc` | 3 | ✅ Testé et fonctionnel |

### ⚠️ Répertoires AVEC 1 ROM (2) - À GARDER

| Répertoire | Contenu | Recommandation |
|------------|---------|----------------|
| `mame` | `alien3.zip` | ✅ **GARDER** (testé et fonctionne) |
| `pce` | `Splatterhouse.zip` | ✅ **GARDER** (peut être testé) |

---

## 🗑️ Répertoires VIDES à Supprimer (14)

### Doublons (3)
| Répertoire | Doublon de | Raison |
|------------|------------|--------|
| **`lynx`** | `atarilynx` | Vide, ROMs dans atarilynx |
| **`virtualboy`** | `vb` | Les deux vides |
| **`vb`** | `virtualboy` | Les deux vides |

### Consoles Sans ROMs (11)
| Répertoire | Raison |
|------------|--------|
| **`32x`** | Pas de ROMs Sega 32X |
| **`3do`** | Pas de ROMs 3DO |
| **`arcade`** | Vide, ROMs dans mame/fbneo |
| **`atari5200`** | Pas de ROMs Atari 5200 |
| **`jaguar`** | Pas de ROMs Atari Jaguar |
| **`mastersystem`** | Vide, doublon de sms |
| **`sms`** | Pas de ROMs Master System |
| **`nds`** | Pas de ROMs Nintendo DS |
| **`ngp`** | Pas de ROMs Neo Geo Pocket |
| **`saturn`** | Pas de ROMs Saturn |
| **`ws`** | Pas de ROMs WonderSwan |

---

## 💾 Cache à Nettoyer (1 Doublon)

### Doublon Genesis dans Cache
| Répertoire Cache | Status | Action |
|------------------|--------|--------|
| **`.cache/genesis/`** | ❌ Obsolète | Supprimer (ancien mapping) |
| **`.cache/megadrive/`** | ✅ Actuel | Garder (mapping correct) |

**Raison :** Avant la correction, le cache allait dans `genesis/`. Maintenant il va dans `megadrive/`.

---

## 🧹 Plan de Nettoyage

### Étape 1 : Supprimer Répertoires ROM Vides (14)

```bash
adb shell "cd /storage/emulated/0/GameLibrary-Data && rm -rf 32x 3do arcade atari5200 jaguar lynx mastersystem nds ngp saturn sms vb virtualboy ws"
```

**Espace libéré estimé :** ~5-10 MB (fichiers config)

### Étape 2 : Supprimer Cache Obsolète (1)

```bash
adb shell rm -rf /storage/emulated/0/GameLibrary-Data/.cache/genesis
```

**Espace libéré estimé :** Variable (ROMs extraites obsolètes)

---

## 📋 Liste des Répertoires APRÈS Nettoyage

**Répertoires ROM (17) :**
```
✅ atarilynx/      (3 ROMs Lynx)
✅ atari2600/      (463 ROMs)
✅ atari7800/      (58 ROMs)
✅ megadrive/      (765 ROMs Genesis)
✅ segacd/         (7 ROMs SegaCD)
✅ gamegear/       (7 ROMs Game Gear)
✅ psx/            (9 ROMs PlayStation)
✅ psp/            (5 ROMs PSP)
✅ n64/            (6 ROMs N64)
✅ snes/           (14 ROMs SNES)
✅ nes/            (706 ROMs NES)
✅ gb/             (4 ROMs Game Boy)
✅ gbc/            (3 ROMs Game Boy Color)
✅ gba/            (7 ROMs Game Boy Advance)
✅ mame/           (1 ROM Arcade)
✅ fbneo/          (9 ROMs Arcade)
✅ pce/            (1 ROM PC Engine)
```

**Répertoires système (à garder) :**
```
data/              (EmulatorJS, BIOS)
cheats/            (Cheats RetroArch)
saves/             (Save states)
media/             (Médias)
.cache/            (Cache extraction)
```

---

## ⚠️ Attention

**NE PAS SUPPRIMER :**
- ❌ `data/` (EmulatorJS, BIOS essentiels)
- ❌ `cheats/` (Cheats RetroArch)
- ❌ `saves/` (Sauvegardes)
- ❌ `media/` (Images, screenshots)

---

**Voulez-vous que j'exécute le nettoyage maintenant ?**
