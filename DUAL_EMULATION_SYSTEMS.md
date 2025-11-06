# 🎮 DEUX SYSTÈMES D'ÉMULATION

**Date:** 2025-10-19  
**Important:** ChatAI-Android utilise **DEUX** systèmes d'émulation différents

---

## 📊 VUE D'ENSEMBLE

```
ChatAI-Android
     │
     ├─ LIBRETRODROID (Natif) ──────> NativeComposeEmulatorActivity
     │                                  ✅ PAS de serveur HTTP
     │                                  ✅ Lecture DIRECTE filesystem
     │
     └─ EMULATORJS (Web) ────────────> WebViewActivity / RelaxWebViewActivity
                                        🌐 UTILISE serveur HTTP (port 8888)
                                        🌐 Lecture via WebView
```

---

## 1️⃣ LIBRETRODROID (NATIF) - SYSTÈME PRINCIPAL

### Activité
`NativeComposeEmulatorActivity.kt`

### Caractéristiques
- ✅ **Émulation native Android** (cores `.so`)
- ✅ **PAS de serveur HTTP**
- ✅ **Chargement DIRECT filesystem**
- ✅ **Performance maximale**
- ✅ **Support cheats intégré**
- ✅ **TouchScreen natif**

### Comment ça marche

```kotlin
// 1. Chemin direct vers la ROM
val romPath = "/storage/emulated/0/GameLibrary-Data/psx/007.pbp"

// 2. Configuration GLRetroView
val data = GLRetroViewData(this).apply {
    gameFilePath = romPath  // ✅ LECTURE DIRECTE
    systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
    savesDirectory = "/storage/emulated/0/GameLibrary-Data/saves/psx"
}

// 3. Chargement du core
retroView = GLRetroView(this, data)

// 4. Le core lit DIRECTEMENT le fichier
// Pas de copie, pas de serveur, accès direct au filesystem
```

### Consoles supportées
- PSX (PlayStation 1)
- PSP (PlayStation Portable)
- N64 (Nintendo 64)
- SNES (Super Nintendo)
- NES (Nintendo Entertainment System)
- GBA (Game Boy Advance)
- GB/GBC (Game Boy / Game Boy Color)
- Lynx (Atari Lynx)

### Cores utilisés
```
app/src/main/jniLibs/arm64-v8a/
├── pcsx_rearmed_libretro_android.so       (PSX)
├── ppsspp_libretro_android.so             (PSP)
├── parallel_n64_libretro_android.so       (N64)
├── snes9x_libretro_android.so             (SNES)
├── fceumm_libretro_android.so             (NES)
├── libmgba_libretro_android.so            (GBA)
├── gambatte_libretro_android.so           (GB/GBC)
└── handy_libretro_android.so              (Lynx)
```

---

## 2️⃣ EMULATORJS (WEB) - SYSTÈME ALTERNATIF

### Activités
- `WebViewActivity.java`
- `RelaxWebViewActivity.kt`
- `GameLibraryWebViewActivity.kt`

### Caractéristiques
- 🌐 **Émulation web** (cores WASM)
- 🌐 **UTILISE serveur HTTP** (port 8888)
- 🌐 **Chargement via HTTP**
- 🌐 **WebView Android**
- 🌐 **Compatibilité universelle**

### Comment ça marche

```kotlin
// 1. Serveur HTTP démarre sur port 8888
WebServer(context).start()

// 2. Routes HTTP disponibles
http://localhost:8888/relax/index.html          // EmulatorJS UI
http://localhost:8888/gamedata/psx/007.pbp      // ROM via HTTP
http://localhost:8888/gamedata/data/cores/      // Cores WASM

// 3. WebView charge EmulatorJS
webView.loadUrl("http://localhost:8888/relax/index.html")

// 4. EmulatorJS télécharge la ROM via HTTP
fetch("http://localhost:8888/gamedata/psx/007.pbp")
```

### Serveur HTTP (WebServer.java)

**Port:** 8888

**Routes principales:**
- `/relax/` → Sert EmulatorJS depuis `assets/relax/`
- `/gamedata/` → Sert GameLibrary-Data depuis `/storage/emulated/0/GameLibrary-Data/`
- `/gamelibrary/` → Sert pages HTML externes

**Code serveur:**
```java
// WebServer.java:224-225
if (cleanPath.startsWith("/gamedata/")) {
    serveGameDataFile(outputStream, cleanPath, method, enableSharedArrayBuffer);
    return;
}

// WebServer.java:304-331
private void serveGameDataFile(...) {
    String filePath = path.substring(10); // Enlever "/gamedata/"
    String fullPath = "/storage/emulated/0/GameLibrary-Data/" + filePath;
    File file = new File(fullPath);
    
    // Streaming du fichier via HTTP
    // ...
}
```

### Cores WASM
```
app/src/main/assets/relax/data/cores/
├── fceumm-wasm.data              (NES)
├── mgba-wasm.data                (GBA)
├── nestopia-wasm.data            (NES)
├── parallel_n64-wasm.data        (N64)
└── snes9x-wasm.data              (SNES)
```

---

## 🔍 QUAND CHAQUE SYSTÈME EST UTILISÉ ?

### LibretroDroid (Natif) - PAR DÉFAUT

**Déclencheur:** Clic sur bouton "PLAY" dans `GameDetailsActivity`

```java
// GameDetailsActivity.java:215
private void launchGameNative(int slot) {
    Intent intent = new Intent(this, NativeComposeEmulatorActivity.class);
    intent.putExtra("romPath", romPath);  // Chemin DIRECT
    startActivity(intent);
}
```

**Utilisé pour :**
- ✅ PSX, PSP, N64, SNES, NES, GBA, GB/GBC, Lynx
- ✅ Toutes les consoles par défaut
- ✅ Performance maximale

---

### EmulatorJS (Web) - ALTERNATIVE

**Déclencheur:** Clic sur bouton "Jeux" dans le drawer KITT

```kotlin
// KittDrawerFragment.kt:234
val intent = Intent(requireContext(), RelaxWebViewActivity::class.java)
startActivity(intent)
```

**Utilisé pour :**
- 🌐 Interface web EmulatorJS
- 🌐 Émulation dans navigateur
- 🌐 Alternative à LibretroDroid
- 🌐 Tests et développement

---

## 📊 COMPARAISON

| Aspect | LibretroDroid (Natif) | EmulatorJS (Web) |
|--------|----------------------|------------------|
| **Serveur HTTP** | ❌ **NON** | ✅ **OUI** (port 8888) |
| **Chargement ROM** | Filesystem direct | HTTP stream |
| **Performance** | ✅ Maximale | 🟡 Bonne |
| **Cores** | `.so` natifs (9 cores) | `.wasm` web (5 cores) |
| **Taille cores** | 40 MB | Variable |
| **TouchScreen** | Natif (LibretroDroid) | HTML5 (EmulatorJS) |
| **Cheats** | ✅ Intégré | ❌ Non supporté |
| **Sauvegardes** | Native | LocalStorage |
| **Utilisation** | **Par défaut** | Alternative |

---

## 🎯 CLARIFICATION IMPORTANTE

### Pour PSX/PSP (Question originale)

**Quand vous jouez à PSX ou PSP en cliquant "PLAY" :**

```
User clique "PLAY"
       ↓
GameDetailsActivity.launchGameNative()
       ↓
NativeComposeEmulatorActivity (LibretroDroid)
       ↓
GLRetroViewData.gameFilePath = /storage/.../007.pbp
       ↓
✅ LECTURE DIRECTE FILESYSTEM (PAS DE SERVEUR HTTP)
       ↓
Core lit le fichier directement
```

**Le serveur HTTP (port 8888) N'EST PAS utilisé pour LibretroDroid.**

---

### Quand le serveur HTTP est utilisé

**UNIQUEMENT pour EmulatorJS (système web) :**

```
User clique "Jeux" (drawer KITT)
       ↓
RelaxWebViewActivity (EmulatorJS)
       ↓
WebView charge http://localhost:8888/relax/index.html
       ↓
🌐 SERVEUR HTTP (port 8888) ACTIF
       ↓
EmulatorJS télécharge ROM via HTTP
```

---

## 📁 STRUCTURE DES FICHIERS

### GameLibrary-Data (partagé)

```
/storage/emulated/0/GameLibrary-Data/
├── psx/                          # ROMs PSX
│   └── 007.pbp
├── psp/                          # ROMs PSP
│   └── GTA.iso
├── data/
│   ├── bios/                     # BIOS (pour LibretroDroid)
│   │   └── scph5501.bin
│   └── cores/                    # Cores EmulatorJS (pour web)
│       └── ...wasm.data
└── saves/                        # Sauvegardes (partagées)
    ├── psx/
    └── psp/
```

### Assets (dans l'APK)

```
app/src/main/assets/
└── relax/                        # EmulatorJS (système web)
    ├── index.html
    └── data/
        ├── cores/                # Cores WASM
        ├── emulator.min.js       # EmulatorJS
        └── loader.js
```

---

## 🔧 CONFIGURATION SERVEUR

### Démarrage automatique

Le serveur HTTP démarre au lancement de l'app :

```java
// MainActivity.java ou BackgroundService.java
WebServer webServer = new WebServer(context);
webServer.start();
Log.i(TAG, "WebServer started on port 8888");
```

### Routes configurées

```java
// WebServer.java
private void handleClient(Socket clientSocket) {
    String path = request.getPath();
    
    if (path.startsWith("/relax/")) {
        serveRelaxFile(...);        // Assets EmulatorJS
    }
    else if (path.startsWith("/gamedata/")) {
        serveGameDataFile(...);     // GameLibrary-Data
    }
    else if (path.startsWith("/gamelibrary/")) {
        serveGamelibrarySite(...);  // Pages HTML externes
    }
    else {
        serveStaticFile(...);       // ChatAI-Files/sites
    }
}
```

---

## ✅ RÉSUMÉ

### LibretroDroid (Natif) - POUR PSX/PSP

- ✅ **PAS de serveur HTTP**
- ✅ **Chargement DIRECT** : `gameFilePath = /storage/.../007.pbp`
- ✅ **Performance maximale**
- ✅ **Système par défaut** pour tous les jeux

### EmulatorJS (Web) - ALTERNATIF

- 🌐 **UTILISE serveur HTTP** (port 8888)
- 🌐 **Chargement HTTP** : `fetch("http://localhost:8888/gamedata/psx/007.pbp")`
- 🌐 **Alternative web**
- 🌐 **Système secondaire**

---

## 🎯 CONCLUSION

**Pour votre question sur PSX/PSP :**

Quand vous jouez à PSX ou PSP via le bouton "PLAY", le système utilise **LibretroDroid (natif)** qui charge les ROMs **DIRECTEMENT depuis le filesystem**, **SANS serveur HTTP**.

Le serveur HTTP (port 8888) existe dans le projet, mais il est utilisé **UNIQUEMENT pour EmulatorJS** (système web alternatif), **PAS pour LibretroDroid**.

**Correction du document précédent :**
- ❌ "Pas de serveur HTTP" → Vrai pour LibretroDroid
- ✅ "Serveur HTTP existe" → Vrai, mais pour EmulatorJS uniquement

---

**Les deux systèmes coexistent sans conflit et servent des usages différents !** ✅


