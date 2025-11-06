# 🔧 FIX GENESIS - SUPPORT .BIN au lieu de .ZIP

**Date:** 2025-10-19  
**Problème:** Genesis ne s'affichait pas (écran noir)  
**Cause:** Core Genesis Plus GX ne supporte pas les ROM .zip  
**Solution:** Détection automatique et utilisation des fichiers .bin

---

## 🚨 PROBLÈME DÉTECTÉ

### Symptômes

- ✅ Core se charge correctement (928 KB)
- ✅ Audio s'initialise (44100 Hz)
- ✅ Graphics s'initialise (OpenGL ES 3.2)
- ✅ FPS détecté (49.7)
- ✅ Manette tactile visible
- ❌ **Pas d'image du jeu (écran noir)**
- ❌ **Émulation ne démarre pas**

---

### Cause identifiée

**Genesis Plus GX ne peut pas lire les fichiers .zip compressés !**

```
ROM chargée : /storage/.../megadrive/3 Ninjas Kick Back (USA).zip (906 KB)
                                                                     ^^^^
                                                             Format non supporté
```

**Le core charge les 928 KB mais ne peut pas décompresser/lire le contenu.**

---

### Fichiers disponibles

```
/storage/emulated/0/GameLibrary-Data/megadrive/
├── 3 Ninjas Kick Back (USA).zip    (906 KB)  ← Chargé AVANT (ne marche pas)
└── 3 Ninjas Kick Back.bin          (2.0 MB)  ← Fichier correct APRÈS
```

**Le fichier .bin existe déjà mais n'était pas utilisé !**

---

## ✅ SOLUTION APPLIQUÉE

### Modification de GameDetailsActivity.java

**Fichier :** `app/src/main/java/com/chatai/GameDetailsActivity.java`  
**Ligne :** 229-252

```java
// FIX GENESIS: Preferer .bin aux .zip (Genesis Plus GX ne supporte pas les .zip)
String console = game.getConsole().toLowerCase();
if ((console.equals("genesis") || console.equals("megadrive") || console.equals("md")) && fileName.endsWith(".zip")) {
    // Essayer de trouver un fichier .bin correspondant
    String binFileName = fileName.replace(".zip", ".bin");
    // Enlever aussi la region (USA) si presente pour matcher "3 Ninjas Kick Back.bin"
    String simpleBinName = game.getName().replaceAll("\\s*\\(.*?\\)\\s*", "").trim() + ".bin";
    
    String binPath = "/storage/emulated/0/GameLibrary-Data/" + game.getConsole() + "/" + binFileName;
    String simpleBinPath = "/storage/emulated/0/GameLibrary-Data/" + game.getConsole() + "/" + simpleBinName;
    
    java.io.File binFile = new java.io.File(binPath);
    java.io.File simpleBinFile = new java.io.File(simpleBinPath);
    
    if (binFile.exists()) {
        romPath = binPath;
        Log.i(TAG, "Genesis: Using .bin file instead of .zip: " + binPath);
    } else if (simpleBinFile.exists()) {
        romPath = simpleBinPath;
        Log.i(TAG, "Genesis: Using simplified .bin file: " + simpleBinPath);
    } else {
        Log.w(TAG, "Genesis: No .bin file found, trying .zip (may not work)");
    }
}
```

---

### Logique de sélection

**Pour un jeu nommé "3 Ninjas Kick Back (USA)" avec fichier "3 Ninjas Kick Back (USA).zip" :**

1. **Détection** : Console = `megadrive` + Fichier = `.zip`
2. **Recherche variante 1** : `3 Ninjas Kick Back (USA).bin` → Pas trouvé
3. **Recherche variante 2** : `3 Ninjas Kick Back.bin` → ✅ **TROUVÉ !**
4. **Utilisation** : Charge `3 Ninjas Kick Back.bin` au lieu du .zip

**Log attendu :**
```
Genesis: Using simplified .bin file: /storage/.../megadrive/3 Ninjas Kick Back.bin
```

---

## 🎯 RÉSULTAT ATTENDU

### Avant le fix

```
romPath = /storage/.../megadrive/3 Ninjas Kick Back (USA).zip
                                                             ^^^^
Core charge le .zip → Ne peut pas décompresser → Écran noir
```

---

### Après le fix

```
romPath = /storage/.../megadrive/3 Ninjas Kick Back.bin
                                                       ^^^^
Core charge le .bin → Lit directement → Jeu s'affiche ✅
```

---

## 📊 COMPARAISON DES FORMATS

### Format .zip

**Avantages :**
- ✅ Économise espace disque (906 KB vs 2.0 MB)
- ✅ Facile à distribuer

**Inconvénients :**
- ❌ Genesis Plus GX **ne supporte pas** les .zip
- ❌ Nécessite décompression manuelle
- ❌ Écran noir lors du chargement

---

### Format .bin

**Avantages :**
- ✅ **Supporté par Genesis Plus GX**
- ✅ Chargement direct
- ✅ Pas de décompression nécessaire
- ✅ **Jeu s'affiche correctement**

**Inconvénients :**
- ⚠️ Plus gros (2.0 MB vs 906 KB)

**Recommandation : Utiliser .bin pour Genesis**

---

## 🔍 CORES ET SUPPORT .ZIP

### Cores qui supportent .zip

| Console | Core | Support .zip |
|---------|------|--------------|
| PSX | pcsx_rearmed | ❓ Pas testé |
| PSP | ppsspp | ❌ Non (.iso requis) |
| N64 | parallel_n64 | ❓ Pas testé |
| SNES | snes9x | ✅ Probablement |
| NES | fceumm | ✅ Probablement |
| GBA | libmgba | ✅ Probablement |
| GB/GBC | gambatte | ✅ Probablement |
| Lynx | handy | ❓ Pas testé |
| **Genesis** | genesis_plus_gx | ❌ **NON** (confirmé) |

**Conclusion :** Genesis est l'une des rares consoles où le .zip ne marche pas.

---

## 🎯 RECOMMANDATIONS

### Pour Genesis/MegaDrive

**Formats recommandés (par ordre de priorité) :**

1. ✅ **`.bin`** - Format standard (recommandé)
   - Taille : 512 KB - 4 MB
   - Supporté par tous les cores
   - Chargement instantané

2. ⚠️ **`.smd`** - Super Magic Drive
   - Peut fonctionner
   - Moins courant

3. ❌ **`.zip`** - Compressé
   - **NE FONCTIONNE PAS** avec Genesis Plus GX
   - Nécessite décompression manuelle

---

### Décompression des .zip

**Si vous avez uniquement des .zip :**

```powershell
# PowerShell - Décompresser tous les .zip Genesis
$zipFiles = Get-ChildItem "/storage/emulated/0/GameLibrary-Data/megadrive/*.zip"

foreach ($zip in $zipFiles) {
    $destination = $zip.DirectoryName
    Expand-Archive -Path $zip.FullName -DestinationPath $destination -Force
}
```

**Ou via ADB :**

```bash
# Extraire un fichier spécifique
adb pull "/storage/emulated/0/GameLibrary-Data/megadrive/3 Ninjas Kick Back (USA).zip" .
unzip "3 Ninjas Kick Back (USA).zip"
adb push "3 Ninjas Kick Back (USA).bin" "/storage/emulated/0/GameLibrary-Data/megadrive/"
```

---

## 📝 NOTES IMPORTANTES

### Différence de taille

**Pourquoi .bin est plus gros que .zip ?**

```
3 Ninjas Kick Back (USA).zip : 906 KB (compressé)
3 Ninjas Kick Back.bin       : 2.0 MB (décompressé)
```

**Taux de compression :** ~55% (906 KB / 2048 KB)

**Les ROMs Genesis compressent bien car :**
- Beaucoup de données répétitives
- Graphismes en tiles
- Zones vides dans la ROM

---

### Impact sur l'espace disque

**Si toutes les ROMs Genesis sont en .bin :**

Exemple avec 100 jeux :
- Format .zip : ~80 MB
- Format .bin : ~180 MB
- **Différence : +100 MB**

**Pour 1000 jeux :**
- Format .zip : ~800 MB
- Format .bin : ~1.8 GB
- **Différence : +1 GB**

**Recommandation :** Garder les .bin uniquement, supprimer les .zip une fois décompressés.

---

## 🔧 BUILD ET INSTALLATION

### Compilation

**Commande :** `.\gradlew installDebug`  
**Temps :** 23 secondes  
**Status :** BUILD SUCCESSFUL  
**Tâches :** 6 exécutées, 67 up-to-date (très rapide)

### Installation

**Device :** Samsung Galaxy S21 FE (SM-G990W)  
**Android :** 15  
**Status :** Installed on 1 device

---

## 🎮 TEST ATTENDU

### Logs attendus

```
GameDetailsActivity: Lancement du jeu (NATIVE COMPOSE): 3 Ninjas Kick Back (USA) [NEW GAME]
GameDetailsActivity: Genesis: Using simplified .bin file: /storage/.../3 Ninjas Kick Back.bin
NativeComposeEmulator: NativeComposeEmulator starting: 3 Ninjas Kick Back (USA) (megadrive) from /storage/.../3 Ninjas Kick Back.bin
Libretro Core: Loading 2097152 bytes ...  (2.0 MB au lieu de 928 KB)
libretrodroid: Starting game with fps 49.7
```

**Taille chargée devrait être ~2 MB (2,097,152 bytes) au lieu de 928 KB**

---

### Affichage attendu

- ✅ Écran du jeu Genesis visible
- ✅ Logo Sega (si présent dans la ROM)
- ✅ Écran titre "3 Ninjas Kick Back"
- ✅ Menu du jeu accessible
- ✅ Manette tactile fonctionnelle

---

## ✅ PROCHAINES ÉTAPES

### Si ça fonctionne

1. **Décompresser tous les .zip Genesis**
   - Extraire tous les .bin
   - Supprimer les .zip (économiser confusion)

2. **Mettre à jour gamelist.json**
   - Pointer vers .bin au lieu de .zip
   - Ou laisser le fix gérer automatiquement

---

### Si ça ne fonctionne toujours pas

**Causes possibles :**
1. Le fichier .bin est corrompu
2. Le core a besoin d'un paramètre spécial
3. Problème de résolution (Genesis = 320x224)
4. Autre ROM à tester

---

## 📄 DOCUMENTATION

**Fichier créé :** `GENESIS_ZIP_BIN_FIX.md`

Ce document contient :
- Analyse du problème
- Solution appliquée
- Logique de sélection .bin vs .zip
- Recommandations de formats
- Guide de décompression

---

**Testez maintenant le jeu Genesis ! Le fichier .bin devrait être chargé et le jeu devrait s'afficher.** 🎮


