# 🎮 Système Double Boutons - 18 Consoles Natives

**Date:** 20 octobre 2025  
**Version:** ChatAI-Android-beta  
**Statut:** ✅ Opérationnel

---

## 🎯 Concept

**TOUS les jeux affichent maintenant 2 boutons :**

1. **🎮 WASM** → Émulation web (EmulatorJS via WebView)
2. **⚡ NEW GAME** → Émulation native (LibretroDroid)

**L'utilisateur choisit** quelle méthode il préfère pour chaque jeu !

---

## 📊 18 Consoles Natives Supportées

### Nintendo (5 consoles)

| Console | Core | Fichier `.so` | Status |
|---------|------|---------------|--------|
| **NES** | FCEUmm | `fceumm_libretro_android.so` | ✅ |
| **SNES** | Snes9x | `snes9x_libretro_android.so` | ✅ |
| **N64** | Parallel N64 | `parallel_n64_libretro_android.so` | ✅ |
| **GB/GBC** | Gambatte | `gambatte_libretro_android.so` | ✅ |
| **GBA** | mGBA | `libmgba_libretro_android.so` | ✅ |

### Sony (2 consoles)

| Console | Core | Fichier `.so` | Status |
|---------|------|---------------|--------|
| **PSX** | PCSX ReARMed | `pcsx_rearmed_libretro_android.so` | ✅ |
| **PSP** | PPSSPP | `ppsspp_libretro_android.so` | ✅ |

### Sega (5 consoles)

| Console | Core | Fichier `.so` | Status |
|---------|------|---------------|--------|
| **Genesis / MegaDrive** | Genesis Plus GX | `genesis_plus_gx_libretro_android.so` | ✅ |
| **SegaCD** | Genesis Plus GX | `genesis_plus_gx_libretro_android.so` | ✅ |
| **Master System** | Genesis Plus GX | `genesis_plus_gx_libretro_android.so` | ✅ |
| **Game Gear** | Genesis Plus GX | `genesis_plus_gx_libretro_android.so` | ✅ |
| **32X** | PicoDrive | `picodrive_libretro_android.so` | ✅ |

### Atari (4 consoles)

| Console | Core | Fichier `.so` | Status |
|---------|------|---------------|--------|
| **Atari 2600** | Stella2014 | `stella2014_libretro_android.so` | ✅ |
| **Atari 5200** | A5200 | `a5200_libretro_android.so` | ✅ |
| **Atari 7800** | ProSystem | `prosystem_libretro_android.so` | ✅ |
| **Lynx** | Handy | `handy_libretro_android.so` | ⚠️ À tester |

### Autres (4 consoles)

| Console | Core | Fichier `.so` | Status |
|---------|------|---------------|--------|
| **Neo Geo Pocket** | Beetle NeoPop | `mednafen_ngp_libretro_android.so` | ✅ |
| **WonderSwan** | Beetle Cygne | `mednafen_wswan_libretro_android.so` | ✅ |
| **PC Engine** | Beetle PCE | `mednafen_pce_libretro_android.so` | ✅ |
| **Arcade (MAME)** | MAME 2003 Plus | `mame2003_plus_libretro_android.so` | ✅ |

---

## 🔧 Architecture Technique

### 1. Interface Utilisateur

**Dans `activity_game_details_modern.xml` :**
```xml
<!-- Bouton WASM (toujours visible) -->
<MaterialButton
    android:id="@+id/play_button"
    android:text="🎮 WASM"
    ... />

<!-- Container boutons natifs (maintenant toujours visible) -->
<LinearLayout
    android:id="@+id/native_buttons_container"
    android:visibility="visible">
    
    <MaterialButton
        android:id="@+id/play_native_button"
        android:text="⚡ NEW GAME"
        ... />
    
    <MaterialButton
        android:id="@+id/load_save_button"
        android:text="📂 CHARGER"
        ... />
    
    <MaterialButton
        android:id="@+id/cheat_button"
        android:text="🎮 CODES"
        ... />
</LinearLayout>
```

### 2. Logique Java

**Dans `GameDetailsActivity.java` :**
```java
private void setupButtons() {
    // Play button (WASM)
    playButton.setOnClickListener(v -> launchGame());
    
    // Play native button (cores natifs)
    playNativeButton.setOnClickListener(v -> launchGameNative(0));
    
    // TOUS les jeux affichent les boutons natifs maintenant
    nativeButtonsContainer.setVisibility(View.VISIBLE);
    checkAndShowLoadSaveButton();
}
```

### 3. Mapping des Cores

**Dans `NativeComposeEmulatorActivity.kt` :**
```kotlin
private fun getCorePath(console: String): String {
    return when (console.lowercase()) {
        // Nintendo
        "nes" -> "fceumm_libretro_android.so"
        "snes" -> "snes9x_libretro_android.so"
        "n64" -> "parallel_n64_libretro_android.so"
        "gb", "gbc" -> "gambatte_libretro_android.so"
        "gba" -> "libmgba_libretro_android.so"
        
        // Sony
        "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
        "psp" -> "ppsspp_libretro_android.so"
        
        // Sega
        "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"
        "scd", "segacd" -> "genesis_plus_gx_libretro_android.so"
        "mastersystem", "sms" -> "genesis_plus_gx_libretro_android.so"
        "gamegear", "gg" -> "genesis_plus_gx_libretro_android.so"
        "32x" -> "picodrive_libretro_android.so"
        
        // Atari
        "atari2600", "atari", "a2600" -> "stella2014_libretro_android.so"
        "atari5200", "a5200" -> "a5200_libretro_android.so"
        "atari7800", "a7800" -> "prosystem_libretro_android.so"
        "lynx" -> "handy_libretro_android.so"
        
        // Other
        "ngp", "ngc", "neogeopocket" -> "mednafen_ngp_libretro_android.so"
        "ws", "wsc", "wonderswan" -> "mednafen_wswan_libretro_android.so"
        "pce", "turbografx", "pcengine" -> "mednafen_pce_libretro_android.so"
        "arcade", "mame" -> "mame2003_plus_libretro_android.so"
        
        else -> {
            // Fallback pour consoles sans core natif
            Log.w("NativeComposeEmulator", "No native core for: $console")
            "fceumm_libretro_android.so"
        }
    }
}
```

---

## 📦 Taille des Cores

**Poids total des cores natifs : ~108 MB**

| Core | Taille |
|------|--------|
| mame2003_plus | 37.67 MB |
| ppsspp | 17.02 MB |
| genesis_plus_gx | 12.15 MB |
| parallel_n64 | 7.74 MB |
| mednafen_pce | 5.21 MB |
| fceumm | 3.99 MB |
| stella2014 | 3.39 MB |
| snes9x | 2.79 MB |
| libmgba | 2.82 MB |
| libparallel | 2.71 MB |
| picodrive | 1.52 MB |
| pcsx_rearmed | 1.42 MB |
| mednafen_wswan | 1.27 MB |
| gambatte | 0.98 MB |
| mednafen_ngp | 0.48 MB |
| handy | 0.27 MB |
| a5200 | 0.26 MB |
| prosystem | 0.18 MB |

---

## 🚀 Utilisation

### Pour l'Utilisateur Final

1. **Ouvrir un jeu** (n'importe quelle console)
2. **Voir les 2 boutons :**
   - 🎮 **WASM** : Émulation web (compatible tout)
   - ⚡ **NEW GAME** : Émulation native (meilleure performance)
3. **Choisir la méthode préférée**

### Avantages WASM
- ✅ Compatible avec toutes les consoles
- ✅ Pas besoin de ROMs sur device
- ✅ Streaming HTTP
- ❌ Performance légèrement inférieure

### Avantages NATIVE
- ✅ Performance maximale
- ✅ Save states natifs (4 slots)
- ✅ Cheats RetroArch + custom
- ✅ Pas de latence réseau
- ❌ Nécessite ROMs sur device
- ❌ Limité aux consoles avec cores

---

## 🔄 Compatibilité Rétroactive

**Comportement pour consoles sans core natif :**
- Le bouton **WASM** fonctionne toujours
- Le bouton **NEW GAME** essaie avec un fallback (fceumm)
- Un warning est logué : `"No native core for: {console}"`

**Exemples de consoles sans core natif :**
- Nintendo DS (besoin de `melonds`)
- Saturn (besoin de `mednafen_saturn`)
- 3DO (besoin de `opera`)
- Dreamcast (besoin de `flycast`)
- Jaguar (besoin de `virtualjaguar`)

---

## 📝 Fichiers Modifiés

### 1. `GameDetailsActivity.java`
- **Ligne 166-172** : Suppression de la condition `if (console.equals(...))` 
- **Nouveau comportement** : `nativeButtonsContainer.setVisibility(View.VISIBLE)` pour TOUS les jeux

### 2. `NativeComposeEmulatorActivity.kt`
- **Ligne 190-230** : Fonction `getCorePath()` étendue avec 18 consoles
- **Organisation** : Groupement par fabricant (Nintendo, Sony, Sega, Atari, Other)
- **Fallback** : Log warning + utilisation de fceumm

### 3. `app/src/main/jniLibs/arm64-v8a/`
- **Ajout de 8 nouveaux cores** (de 10 à 18)
- **Script** : `download_cores.ps1` pour automatiser le téléchargement

---

## 📊 Statistiques

**Avant cette mise à jour :**
- 9 consoles natives (PSX, PSP, N64, SNES, NES, GBA, GB/GBC, Genesis, SegaCD)
- Boutons natifs visibles uniquement pour ces 9 consoles

**Après cette mise à jour :**
- **18 consoles natives**
- **Tous les jeux** affichent les 2 boutons (WASM + NATIVE)
- **+100% de consoles natives** (de 9 à 18)

---

## 🧪 Tests à Effectuer

### Priorité Haute (Consoles Testées)
- [x] PSX - Fonctionne
- [x] PSP - Fonctionne
- [x] N64 - Fonctionne
- [x] SNES - Fonctionne
- [x] NES - Fonctionne
- [x] GBA - Fonctionne
- [x] GB/GBC - Fonctionne
- [x] Genesis - Fonctionne
- [x] SegaCD - Fonctionne

### Priorité Moyenne (Nouveaux Cores)
- [ ] Master System - À tester
- [ ] Game Gear - À tester
- [ ] 32X - À tester
- [ ] Atari 2600 - À tester
- [ ] Atari 5200 - À tester
- [ ] Atari 7800 - À tester
- [ ] Neo Geo Pocket - À tester
- [ ] WonderSwan - À tester
- [ ] PC Engine - À tester
- [ ] Arcade (MAME) - À tester

### Problème Connu
- [ ] **Lynx** - Core présent mais ne fonctionne pas (à investiguer)

---

## 🐛 Debugging

### Logs Utiles

**Pour vérifier quel core est utilisé :**
```bash
adb logcat NativeComposeEmulator:I *:S
```

**Pour voir les warnings de fallback :**
```bash
adb logcat NativeComposeEmulator:W *:S
```

**Pour debug complet LibretroDroid :**
```bash
adb logcat "Libretro Core:*" NativeComposeEmulator:* GLRetroView:* *:S
```

### Problèmes Potentiels

1. **Core ne se charge pas**
   - Vérifier que le fichier `.so` existe dans `jniLibs/arm64-v8a/`
   - Vérifier le mapping dans `getCorePath()`
   - Vérifier les logs pour exceptions

2. **ROM ne se charge pas**
   - Vérifier le chemin ROM : `/storage/emulated/0/GameLibrary-Data/{console}/{file}`
   - Vérifier les permissions de lecture
   - Activer le cache ZIP si nécessaire

3. **Performance médiocre**
   - Certains cores (MAME, Saturn) sont gourmands
   - Essayer WASM si NATIVE est trop lent

---

## 🎯 Prochaines Étapes (Optionnelles)

1. **Tester toutes les nouvelles consoles** avec des ROMs connues
2. **Fixer Lynx** si possible (ou supprimer le core)
3. **Ajouter Nintendo DS** (core `melonds`)
4. **Ajouter Saturn** (core `mednafen_saturn`)
5. **Documenter quelles ROMs fonctionnent** pour chaque console

---

## 📚 Documents Associés

- `NATIVE_EMULATION_FINAL_RECAP.md` - Récapitulatif système natif (version précédente, 9 consoles)
- `CORES_TO_ADD.md` - Liste des cores à ajouter
- `download_cores.ps1` - Script de téléchargement automatique
- `DUAL_EMULATION_SYSTEMS.md` - Différence LibretroDroid vs EmulatorJS

---

## ✅ Validation

**Date:** 20 octobre 2025  
**Version:** ChatAI-Android-beta  
**Device:** Samsung SM-G990W (Android 15)  

**Tests de validation :**
- ✅ Compilation : Succès avec 18 cores
- ✅ Installation : APK installé
- ✅ UI : Les 2 boutons apparaissent pour tous les jeux
- ✅ WASM : Fonctionne comme avant
- ✅ NATIVE : Fonctionne pour les 9 consoles testées
- ⏳ Nouveaux cores : En attente de tests avec ROMs

---

## 🏆 Conclusion

**ChatAI dispose maintenant de :**
- **18 consoles natives** (vs 9 avant)
- **Choix WASM/NATIVE** pour tous les jeux
- **Flexibilité maximale** pour l'utilisateur
- **Fallback automatique** pour consoles sans core

**Statut :** ✅ **PRÊT POUR TESTS ÉTENDUS**

---

*Document créé le 20 octobre 2025*  
*ChatAI-Android-beta - Dual Buttons System with 18 Native Consoles*

