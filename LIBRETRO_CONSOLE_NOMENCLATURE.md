# 📂 Nomenclature Officielle Libretro - Répertoires ROM

**Source :** `C:\repos\libretro-database\cht\`  
**Date :** 20 octobre 2025

---

## 🎯 Nomenclature Officielle vs Répertoires Actuels

### Atari

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `Atari - 2600` | `atari2600` | ✅ `atari2600` | Garder |
| `Atari - 5200` | `atari5200` | ✅ `atari5200` | Garder |
| `Atari - 7800` | `atari7800` | ✅ `atari7800` | Garder |
| `Atari - Lynx` | `atarilynx` + `lynx` | ✅ `atarilynx` | **Supprimer `lynx`** |
| `Atari - Jaguar` | `jaguar` | ✅ `jaguar` | Garder |

**Décision Atari :**
- ✅ Garder : `atari2600`, `atari5200`, `atari7800`, `atarilynx`, `jaguar`
- ❌ Supprimer : `lynx` (doublon)

---

### Sega

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `Sega - 32X` | `32x` | ✅ `32x` | Garder |
| `Sega - Game Gear` | `gamegear` | ✅ `gamegear` | Garder |
| `Sega - Master System - Mark III` | `mastersystem` + `sms` | ✅ `mastersystem` | **Supprimer `sms`** |
| `Sega - Mega Drive - Genesis` | `megadrive` | ✅ `megadrive` | Garder |
| `Sega - Mega-CD - Sega CD` | `segacd` | ✅ `segacd` | Garder |
| `Sega - Dreamcast` | (aucun) | ➕ Créer `dreamcast` | Futur |
| `Sega - Saturn` | `saturn` | ✅ `saturn` | Garder |

**Décision Sega :**
- ✅ Garder : `32x`, `gamegear`, `mastersystem`, `megadrive`, `segacd`, `saturn`
- ❌ Supprimer : `sms` (doublon)
- ❌ Supprimer : `arcade` (doublon de mame/fbneo)

---

### Nintendo

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `Nintendo - Game Boy` | `gb` | ✅ `gb` | Garder |
| `Nintendo - Game Boy Color` | `gbc` | ✅ `gbc` | Garder |
| `Nintendo - Game Boy Advance` | `gba` | ✅ `gba` | Garder |
| `Nintendo - Nintendo 64` | `n64` | ✅ `n64` | Garder |
| `Nintendo - Nintendo Entertainment System` | `nes` | ✅ `nes` | Garder |
| `Nintendo - Super Nintendo Entertainment System` | `snes` | ✅ `snes` | Garder |
| `Nintendo - Nintendo DS` | `nds` | ✅ `nds` | Garder |

**Décision Nintendo :**
- ✅ Garder : `gb`, `gbc`, `gba`, `n64`, `nes`, `snes`, `nds`
- Pas de doublons

---

### Sony

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `Sony - PlayStation` | `psx` | ✅ `psx` | Garder |
| (PSP pas dans libretro-db) | `psp` | ✅ `psp` | Garder |

**Décision Sony :**
- ✅ Garder : `psx`, `psp`
- Pas de doublons

---

### Autres

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `NEC - PC Engine - TurboGrafx 16` | `pce` | ✅ `pce` | Garder |
| (Neo Geo Pocket) | `ngp` | ✅ `ngp` | Garder |
| (WonderSwan) | `ws` | ✅ `ws` | Garder |
| (Virtual Boy) | `virtualboy` + `vb` | ✅ `virtualboy` | **Supprimer `vb`** |
| (3DO) | `3do` | ✅ `3do` | Garder |

**Décision Autres :**
- ✅ Garder : `pce`, `ngp`, `ws`, `virtualboy`, `3do`
- ❌ Supprimer : `vb` (doublon)

---

### Arcade

| Libretro Officiel | Device Actuel | Recommandation | Action |
|-------------------|---------------|----------------|--------|
| `FBNeo - Arcade Games` | `fbneo` | ✅ `fbneo` | Garder |
| (MAME) | `mame` | ✅ `mame` | Garder |
| (Generic) | `arcade` | ❌ Doublon | **Supprimer `arcade`** |

**Décision Arcade :**
- ✅ Garder : `fbneo`, `mame`
- ❌ Supprimer : `arcade` (doublon inutile)

---

## 🗑️ Liste Finale de Suppression (5 Doublons)

**Basé sur la nomenclature libretro officielle :**

```bash
# Supprimer les 5 doublons seulement
adb shell "cd /storage/emulated/0/GameLibrary-Data && rm -rf lynx sms vb arcade"

# Supprimer cache obsolète
adb shell rm -rf /storage/emulated/0/GameLibrary-Data/.cache/genesis
```

---

## ✅ Répertoires Finaux (26)

### Avec ROMs (15)
- `atarilynx`, `atari2600`, `atari7800`
- `megadrive`, `segacd`, `gamegear`
- `psx`, `psp`
- `n64`, `snes`, `nes`, `gb`, `gbc`, `gba`
- `mame`, `fbneo`, `pce`

### Vides MAIS Cores Installés (5)
- `32x`, `atari5200`, `ngp`, `ws`, `mastersystem`

### Vides MAIS Potentiel Futur (6)
- `3do`, `jaguar`, `saturn`, `nds`, `virtualboy`

---

## 📝 Mapping Code à Mettre à Jour

**Après suppression, mettre à jour le mapping :**

```java
// AVANT (avec doublons)
case "lynx": return "atarilynx";  // lynx/ sera supprimé
case "sms": return "sms";          // sms/ sera supprimé
case "vb": return "virtualboy";    // vb/ sera supprimé

// APRÈS (simplifié)
case "lynx":
case "atarilynx":
    return "atarilynx";  // Seul répertoire restant
    
case "sms":
case "mastersystem":
    return "mastersystem";  // Seul répertoire restant
    
case "vb":
case "virtualboy":
    return "virtualboy";  // Seul répertoire restant
```

---

**Voulez-vous que j'exécute le nettoyage des 5 doublons maintenant ?**

