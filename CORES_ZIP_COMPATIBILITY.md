# 📦 Compatibilité ZIP des Cores Libretro

**Date:** 20 octobre 2025  
**Version:** ChatAI-Android-beta

---

## 🎯 Objectif

Déterminer quels cores Libretro supportent **nativement** les fichiers `.zip` et lesquels nécessitent l'extraction.

---

## 📊 ROMs sur Device (Format Actuel)

| Console | ROMs sur Device | Format |
|---------|----------------|--------|
| **PSX** | ✅ Testées | `.PBP` (compressé natif, pas .zip) |
| **NES** | ✅ Testées | `.zip` (avec `.nes` interne) |
| **SNES** | ✅ Testées | `.zip` (avec `.sfc`/`.smc` interne) |
| **Genesis** | ✅ Testées | `.zip` (avec `.bin`/`.smd` interne) |
| **Lynx** | ✅ Testées | `.zip` (avec `.lnx` interne) |
| **Atari 2600** | ✅ Testées | `.zip` (avec `.a26` interne) |

---

## 🔍 Test de Compatibilité ZIP Native

### ✅ Cores Supportant .zip Nativement (Théorique)

Selon la documentation Libretro, certains cores **peuvent** lire les `.zip` :

| Core | Console | Support .zip Natif ? | Testé |
|------|---------|---------------------|-------|
| **FCEUmm** | NES | ✅ Oui (théoriquement) | ❌ Ne fonctionne pas |
| **Snes9x** | SNES | ✅ Oui (théoriquement) | ❌ À tester |
| **Genesis Plus GX** | Genesis/SMS/GG | ✅ Oui (théoriquement) | ❌ Ne fonctionne pas |
| **Gambatte** | GB/GBC | ✅ Oui (théoriquement) | ❌ À tester |
| **mGBA** | GBA | ✅ Oui (théoriquement) | ❌ À tester |

### ❌ Cores Ne Supportant PAS .zip

| Core | Console | Raison |
|------|---------|--------|
| **Beetle Lynx** | Lynx | Core Mednafen strict, pas de support archive |
| **Stella2014** | Atari 2600 | Pas de support .zip intégré |
| **PCSX ReARMed** | PSX | Supporte `.PBP` mais pas `.zip` |
| **PPSSPP** | PSP | Supporte `.CSO` mais pas `.zip` |
| **Parallel N64** | N64 | Pas de support .zip |

---

## 🐛 Problème Observé

### Ce que nous avons constaté :

1. **EmulatorJS** (WASM) : Tous les `.zip` fonctionnent ✅
2. **Lemuroid** (LibretroDroid + extraction) : Tous les `.zip` fonctionnent ✅
3. **ChatAI avec LibretroDroid brut** : Les `.zip` ne fonctionnent PAS ❌

### Pourquoi ?

**LibretroDroid passe le chemin brut au core :**
```
/storage/emulated/0/GameLibrary-Data/lynx/Desert Strike.zip
                                                          ↓
                                              Core Beetle Lynx
                                                          ↓
                                          ❌ Core ne peut pas lire .zip
                                                          ↓
                                              "Insert Game"
```

**Même si certains cores **déclarent** supporter les `.zip`, en pratique sur LibretroDroid Android ça ne fonctionne pas toujours.**

---

## ✅ Notre Solution : Extraction Universelle

Au lieu de deviner quels cores supportent les `.zip`, **nous extrayons TOUS les `.zip` automatiquement** :

```
/storage/emulated/0/GameLibrary-Data/lynx/Desert Strike.zip
                                                          ↓
                                      extractToCacheAsync()
                                                          ↓
                        /GameLibrary-Data/.cache/atarilynx/Desert Strike.lnx
                                                          ↓
                                              Core Beetle Lynx
                                                          ↓
                                              ✅ Jeu fonctionne
```

### Avantages

1. **Universel** : Fonctionne pour tous les cores
2. **Fiable** : Pas de dépendance aux capacités du core
3. **Rapide** : Cache persistant (extraction 1 fois seulement)
4. **Compatible** : Comme Lemuroid

---

## 🎮 Exemple Comparatif : PSX vs Autres

### PSX (Pas de Cache Nécessaire)

```java
// PSX utilise .PBP (format compressé natif)
romPath = "/storage/emulated/0/GameLibrary-Data/psx/GTA2.PBP"
         ↓
LibretroDroid
         ↓
PCSX ReARMed supporte .PBP nativement
         ↓
✅ Jeu fonctionne sans extraction
```

### Lynx (Cache Requis)

```java
// Lynx utilise .zip avec .lnx interne
romPath = "/storage/emulated/0/GameLibrary-Data/atarilynx/Desert Strike.zip"
         ↓
Détection .zip
         ↓
extractToCacheAsync()
         ↓
cachedRomPath = "/GameLibrary-Data/.cache/atarilynx/Desert Strike.lnx"
         ↓
LibretroDroid
         ↓
Beetle Lynx reçoit .lnx extrait
         ↓
✅ Jeu fonctionne
```

### NES (Cache Maintenant Activé par Défaut)

```java
// NES utilise .zip avec .nes interne
romPath = "/storage/emulated/0/GameLibrary-Data/nes/1942.zip"
         ↓
Détection .zip
         ↓
extractToCacheAsync()
         ↓
cachedRomPath = "/GameLibrary-Data/.cache/nes/1942.nes"
         ↓
LibretroDroid
         ↓
FCEUmm reçoit .nes extrait
         ↓
✅ Jeu fonctionne (même si le core "supporte" .zip théoriquement)
```

---

## 🔧 Amélioration : Support Formats Multiples

### PSX Supporte Plusieurs Formats

```kotlin
// PSX dans GLRetroViewData
gameFilePath = "/path/to/game.bin"   // ✅ Avec .cue
gameFilePath = "/path/to/game.chd"   // ✅ Compressé
gameFilePath = "/path/to/game.pbp"   // ✅ PSP format
```

**Le core PCSX ReARMed est **très flexible** car il supporte nativement plusieurs formats compressés.**

### Autres Consoles Moins Flexibles

**Genesis, Lynx, Atari, etc.** :
- Cores **moins flexibles**
- Acceptent seulement leur format natif
- `.zip` **pas supporté** même s'ils le déclarent

---

## 💡 Amélioration Proposée : Support .pbp, .chd, etc.

**Ajoutons le support des formats compressés natifs comme PSX :**

```java
// Ne PAS extraire si format compressé natif supporté
if (fileName.endsWith(".pbp") || fileName.endsWith(".chd") || 
    fileName.endsWith(".cso") || fileName.endsWith(".daa")) {
    // Charger directement (core supporte ces formats)
    launchDirectly(romPath, slot);
    return;
}

// Sinon, si .zip, extraire
if (fileName.endsWith(".zip")) {
    extractToCacheAsync(romPath, fileName, slot, console);
    return;
}
```

**Voulez-vous que j'ajoute cette logique pour détecter les formats compressés natifs supportés (comme `.pbp`, `.chd`, `.cso`) ?**
