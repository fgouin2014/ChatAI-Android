# 📚 LIBRETRODROID API REFERENCE - CHEAT CODES

## ✅ API PUBLIQUE DISPONIBLE

**LibretroDroid 0.13.0** (via JitPack) expose **déjà** l'API de codes de triche !

**Aucun fork nécessaire** - L'API est prête à l'emploi ✨

---

## 🎯 API CHEATS DISPONIBLES

### `setCheat(index, enable, code)`

**Signature** :
```kotlin
fun setCheat(index : Int, enable : Boolean, code : String)
```

**Paramètres** :
- `index` : Index du code (0-99)
- `enable` : true = activer, false = désactiver
- `code` : Code au format Libretro (ex: "8009C6E4 03E7")

**Exemple** :
```kotlin
// Activer un code
retroView.setCheat(0, true, "8009C6E4 03E7")

// Désactiver un code
retroView.setCheat(0, false, "")
```

**Source** : `GLRetroView.kt` ligne 161

---

## 🔧 UTILISATION DANS CHATAI

### CheatApplier.kt

```kotlin
class CheatApplier(private val retroView: GLRetroView) {
    
    // Appliquer un code
    private fun applyCheatCode(index: Int, description: String, code: String) {
        retroView.setCheat(index, true, code)
        Log.i(TAG, "✅ Applied cheat #$index: $description = $code")
    }
    
    // Désactiver un code
    fun toggleCheat(index: Int, enabled: Boolean, cheat: Cheat) {
        if (enabled) {
            retroView.setCheat(index, true, convertedCode)
        } else {
            retroView.setCheat(index, false, "")
        }
    }
    
    // Supprimer tous les codes
    fun clearAllCheats() {
        for (i in 0 until 20) {
            retroView.setCheat(i, false, "")
        }
    }
}
```

---

## 📝 FORMAT DES CODES

### Format Libretro (attendu par setCheat)

**GameShark PSX** :
```
Format entrée : 8009C6E4 03E7
Format LibretroDroid : 8009C6E4 03E7  (identique)
```

**RetroArch** :
```
Format entrée : 8009C6E4+03E7
Conversion : 8009C6E4 03E7  (remplacer + par espace)
```

**Game Genie** :
```
Format entrée : SXIOPO
Format LibretroDroid : SXIOPO  (identique)
Note : Le core fait la conversion hex
```

---

## 🎮 CODES NATIVES LIBRETRO (C++)

### JNI Implementation

**Fichier** : `libretrodroid/src/main/cpp/libretrodroidjni.cpp`

```cpp
// Ligne 213
JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_LibretroDroid_setCheat(
    JNIEnv* env,
    jclass obj,
    jint index,
    jboolean enabled,
    jstring code
) {
    ScopedJStringChars codeString(env, code);
    try {
        LibretroDroid::getInstance().setCheat(index, enabled, codeString.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in setCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}
```

**Appelle** : `core->retro_cheat_set(index, enabled, code)`

---

## 🔍 CORE LIBRETRO

### API Libretro Standard

Chaque core Libretro expose :
```cpp
void retro_cheat_reset(void);
void retro_cheat_set(unsigned index, bool enabled, const char *code);
```

**Cores supportant les cheats** :
- ✅ **PCSX ReARMed** (PSX) - GameShark
- ✅ **Mupen64Plus** (N64) - GameShark
- ✅ **SNES9x** (SNES) - Game Genie / Pro Action Replay
- ✅ **FCEUmm** (NES) - Game Genie
- ✅ **Genesis Plus GX** (Genesis) - Game Genie / Action Replay
- ✅ **mGBA** (GBA) - GameShark / CodeBreaker
- ✅ **Gambatte** (GB/GBC) - Game Genie

---

## 📊 WORKFLOW D'APPLICATION

### 1. Au démarrage du jeu
```kotlin
// NativeComposeEmulatorActivity.kt (ligne 122)
android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
    loadAndApplyCheats()  // Charge et applique les codes activés
}, 1500)  // Après initialisation du core
```

### 2. Depuis le menu
```kotlin
// Ligne 516
onCheatsChanged = { updatedCheats ->
    cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
    cheatApplier.applyCheatsList(updatedCheats)  // Application immédiate
}
```

### 3. Application au core
```kotlin
// CheatApplier.kt
fun applyCheatsList(cheats: List<Cheat>): Boolean {
    clearAllCheats()  // Reset
    
    cheats.filter { it.enabled }.forEachIndexed { index, cheat ->
        val converted = convertCheatCode(cheat)
        retroView.setCheat(index, true, converted)  // ← API publique !
    }
    
    return true
}
```

---

## ⚙️ CONVERSION DES FORMATS

### CheatApplier.convertCheatCode()

```kotlin
private fun convertCheatCode(cheat: Cheat): String? {
    return when (cheat.type) {
        CheatType.RETROARCH -> {
            // 8009C6E4+03E7 → 8009C6E4 03E7
            cheat.code.replace("+", " ")
        }
        
        CheatType.GAMESHARK -> {
            // 8009C6E4 03E7 (déjà au bon format)
            cheat.code
        }
        
        CheatType.GAME_GENIE -> {
            // SXIOPO (le core fait la conversion)
            cheat.code
        }
        
        CheatType.ACTION_REPLAY -> {
            // 12345678 ABCDEF01 (format natif)
            cheat.code
        }
        
        CheatType.CUSTOM -> {
            cheat.code
        }
    }
}
```

---

## 🧪 TESTS

### Logs attendus

**Au démarrage** :
```
I NativeComposeEmulator: [PSX] Loading 2 active cheat(s) for 007
I CheatApplier: Applying 2 cheat(s)
I CheatApplier: 🧹 Clearing all active cheats
I CheatApplier: ✅ All cheats cleared
I CheatApplier: ✅ Applied cheat #0: Infinite Health = 8009C6E4 03E7
I CheatApplier: ✅ Applied cheat #1: Infinite Ammo = 300A1234 00FF
Toast: [PSX] 2 cheat(s) active
```

**Toggle depuis menu** :
```
I CheatApplier: ✅ Enabling cheat #2: Max Money
I CheatApplier: ✅ Applied cheat #2: Max Money = 8009C8A0 FFFF
I NativeComposeEmulator: [PSX] Applied 3 active cheat(s)
```

---

## 🎯 POURQUOI AUCUN FORK N'EST NÉCESSAIRE

### GLRetroView.setCheat() est PUBLIQUE

**Preuve** : `GLRetroView.kt` ligne 161
```kotlin
fun setCheat(index : Int, enable : Boolean, code : String) = runOnGLThread {
    LibretroDroid.setCheat(index, enable, code)
}
```

**Accessibilité** :
- ✅ `fun` (pas `private fun`)
- ✅ Accessible depuis n'importe quelle classe
- ✅ Fonctionne avec LibretroDroid 0.13.0 standard

**Conclusion** :
- ❌ Pas besoin de fork
- ❌ Pas besoin de .aar custom
- ❌ Pas besoin de JNI
- ✅ **Utiliser directement l'API publique**

---

## 📚 RESSOURCES

### LibretroDroid GitHub
- **Repo** : https://github.com/Swordfish90/LibretroDroid
- **Version** : 0.13.0
- **Fichier** : `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`
- **Ligne** : 161

### Libretro API Docs
- **Cheat API** : https://docs.libretro.com/development/cores/developing-cores/#cheat-support
- **Format** : https://docs.libretro.com/guides/cheat-codes/

---

## ✅ STATUT : PRODUCTION READY

**Système complet et fonctionnel** avec :
- ✅ API publique de LibretroDroid
- ✅ Application réelle des codes
- ✅ Aucune modification externe requise
- ✅ 100% compatible avec JitPack

**🎮 Prêt à tricher dans vos jeux ! ✨🚀**
