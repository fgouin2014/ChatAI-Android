# 🎮 Plan de Création - RetroPlay-Android

**Date:** 20 octobre 2025  
**Objectif:** Créer une app standalone d'émulation basée sur ChatAI  
**Nom:** RetroPlay-Android  
**Type:** Émulateur multi-console (WASM + Native)

---

## 🎯 Vision du Projet

**RetroPlay-Android** sera un émulateur Android complet avec :
- ✅ Émulation native (LibretroDroid - 19 cores)
- ✅ Émulation web (EmulatorJS)
- ✅ Double système WASM/NATIVE
- ✅ 17 consoles avec cheats
- ✅ Support `.zip`, `.7z`
- ✅ Interface moderne (Jetpack Compose)
- ❌ **PAS** de fonctionnalités ChatAI (Ask Gemini, etc.)

---

## 📋 Plan d'Action Détaillé

### Phase 1 : Structure du Projet (30 min)

#### 1.1 Créer Structure de Base
```
C:\androidProject\ChatAI-Android-beta\
└── RetroPlay-Android\
    ├── app\
    │   ├── src\
    │   │   └── main\
    │   │       ├── java\com\retroplay\
    │   │       ├── jniLibs\arm64-v8a\
    │   │       ├── res\
    │   │       └── AndroidManifest.xml
    │   └── build.gradle
    ├── lemuroid-touchinput\
    ├── retrograde-util\
    ├── gradle\
    ├── build.gradle
    ├── settings.gradle
    └── gradle.properties
```

#### 1.2 Configuration Gradle
- Package name: `com.retroplay`
- Application ID: `com.retroplay`
- Version: `1.0.0`
- Min SDK: 24
- Target SDK: 35

---

### Phase 2 : Copie Sélective des Fichiers (1h)

#### 2.1 Activités à Copier (Émulation)

**Source :** `ChatAI-Android/app/src/main/java/com/chatai/`

| Fichier | Destination | Modifications |
|---------|-------------|---------------|
| `NativeComposeEmulatorActivity.kt` | `com.retroplay` | ✅ Renommer package |
| `GameDetailsActivity.java` | `com.retroplay` | ✅ Renommer package |
| `GameListActivity.java` | `com.retroplay` | ✅ Retirer Ask Gemini |
| `WebServer.java` | `com.retroplay` | ✅ Renommer package |
| `Game.java` | `com.retroplay.model` | ✅ Renommer package |

#### 2.2 Activités à NE PAS Copier (ChatAI)

**À exclure :**
- ❌ `MainActivity.java` (interface ChatAI)
- ❌ `ChatActivity.java`
- ❌ `GeminiApiService.java`
- ❌ Toutes les activités de chat
- ❌ Fonctionnalités Ask Gemini

#### 2.3 Packages à Copier Intégralement

| Package | Nombre de Fichiers | Action |
|---------|-------------------|--------|
| `cheat/` | ~10 fichiers | ✅ Copier tout |
| `gamepad/` | ~15 fichiers | ✅ Copier tout |
| `TouchControllerSettingsManager.kt` | 1 fichier | ✅ Copier |
| `GamePadLayoutManager.kt` | 1 fichier | ✅ Copier |
| `ConsoleConfigActivity.java` | 1 fichier | ✅ Copier |

#### 2.4 Ressources (res/)

**À copier :**
- ✅ `layout/activity_game_list.xml`
- ✅ `layout/activity_game_details_modern.xml`
- ✅ `layout/activity_native_compose_emulator.xml`
- ✅ `values/colors.xml` (couleurs KITT)
- ✅ `values/strings.xml` (filtrer)
- ✅ `drawable/` (icônes émulation)

**À exclure :**
- ❌ Layouts ChatAI
- ❌ Icônes ChatAI
- ❌ Ressources Ask Gemini

---

### Phase 3 : Cores et Assets (15 min)

#### 3.1 Cores Natifs (jniLibs/arm64-v8a/)

**Copier tous les cores :**
```
✅ 19 cores actifs (115 MB)
❌ handy_libretro_android.so (déjà supprimé)
```

#### 3.2 Modules Lemuroid

**Copier les modules :**
- ✅ `lemuroid-touchinput/` (gamepads Compose)
- ✅ `retrograde-util/` (utilitaires)

---

### Phase 4 : Configuration Android (30 min)

#### 4.1 AndroidManifest.xml

**Modifications nécessaires :**

```xml
<!-- AVANT (ChatAI) -->
<manifest package="com.chatai">
    <application android:label="ChatAI">
        <activity android:name=".MainActivity" />
        <activity android:name=".ChatActivity" />
        <activity android:name=".GameListActivity" />
        ...
    </application>
</manifest>

<!-- APRÈS (RetroPlay) -->
<manifest package="com.retroplay">
    <application android:label="RetroPlay">
        <activity android:name=".GameListActivity" 
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".GameDetailsActivity" />
        <activity android:name=".NativeComposeEmulatorActivity" />
        ...
    </application>
</manifest>
```

**Changements :**
- ❌ Retirer MainActivity (ChatAI)
- ✅ GameListActivity devient le launcher
- ✅ Retirer toutes les activités ChatAI
- ✅ Garder seulement les activités émulation

#### 4.2 Permissions

**À garder :**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

**À retirer :**
```xml
<!-- Permissions ChatAI spécifiques si présentes -->
```

---

### Phase 5 : Refactoring Code (1h)

#### 5.1 Renommage Package

**Rechercher/Remplacer dans tous les fichiers copiés :**
```
package com.chatai → package com.retroplay
import com.chatai → import com.retroplay
```

#### 5.2 Nettoyage GameListActivity

**Retirer :**
- ❌ Bouton "Ask Gemini"
- ❌ Intégration ChatAI
- ❌ Fonctionnalités non-émulation

**Garder :**
- ✅ Liste des consoles
- ✅ Scan automatique ROMs
- ✅ Interface moderne
- ✅ WebServer (pour WASM)

#### 5.3 Simplifier BackgroundService

**Retirer :**
- ❌ Services ChatAI
- ❌ Serveurs non nécessaires

**Garder :**
- ✅ WebServer (port 8888 pour WASM)
- ✅ Gestion ROMs

---

### Phase 6 : Ressources et Thème (30 min)

#### 6.1 Icône Application

**Créer nouvelle icône :**
- Nom: RetroPlay
- Style: Manette de jeu rétro
- Couleurs: Conserver thème KITT (rouge/noir) ou nouveau ?

#### 6.2 Strings

**Fichier `res/values/strings.xml` :**
```xml
<string name="app_name">RetroPlay</string>
<string name="game_library">Game Library</string>
<string name="play_native">Play Native</string>
<string name="play_wasm">Play WASM</string>
<!-- Retirer toutes les strings ChatAI -->
```

#### 6.3 Thème

**Garder ou modifier ?**
- Option A: Garder thème KITT (rouge/noir)
- Option B: Nouveau thème gaming (bleu/orange ?)

---

### Phase 7 : Configuration Répertoires (15 min)

#### 7.1 Répertoires ROM

**Changement de chemin :**

```java
// AVANT (ChatAI)
/storage/emulated/0/ChatAI-Files/roms/
/storage/emulated/0/GameLibrary-Data/

// APRÈS (RetroPlay) - Options:
Option A: /storage/emulated/0/RetroPlay-Data/
Option B: Garder GameLibrary-Data/ (compatibilité)
```

**Recommandation :** Option B (compatibilité avec ROMs existantes)

#### 7.2 WebServer

**Modifier ports ? Ou garder 8888 ?**
- Option A: Garder 8888 (compatibilité)
- Option B: Nouveau port (ex: 9000)

---

### Phase 8 : Build et Test (30 min)

#### 8.1 Gradle Sync
```bash
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
.\gradlew clean build
```

#### 8.2 Compilation
```bash
.\gradlew installDebug
```

#### 8.3 Tests Basiques
- ✅ App se lance
- ✅ Liste des consoles visible
- ✅ Jeu se lance en NATIVE
- ✅ Jeu se lance en WASM
- ✅ Cheats fonctionnent
- ✅ Save states fonctionnent

---

## 📊 Estimation Temps

| Phase | Tâche | Temps Estimé |
|-------|-------|--------------|
| 1 | Structure projet | 30 min |
| 2 | Copie fichiers | 1h |
| 3 | Cores et assets | 15 min |
| 4 | Configuration Android | 30 min |
| 5 | Refactoring code | 1h |
| 6 | Ressources et thème | 30 min |
| 7 | Configuration répertoires | 15 min |
| 8 | Build et test | 30 min |

**Total estimé :** 4h30

---

## 🎯 Fichiers Critiques à Copier

### Java/Kotlin (Émulation Core)

```
✅ NativeComposeEmulatorActivity.kt (émulateur natif)
✅ GameDetailsActivity.java (détails jeu + lancement)
✅ GameListActivity.java (bibliothèque)
✅ WebServer.java (serveur WASM)
✅ Game.java (modèle de données)
✅ ConsoleConfigActivity.java (config consoles)

Package cheat/:
✅ CheatActivity.kt
✅ CheatManager.kt
✅ CheatApplier.kt
✅ CheatSelectionDialog.kt
✅ AddCustomCheatDialog.kt

Package gamepad/:
✅ Tous les fichiers (15 fichiers)
✅ TouchControllerSettingsManager.kt
✅ GamePadLayoutManager.kt
```

### Cores (jniLibs/)

```
✅ Copier tous les 19 cores (115 MB)
```

### Ressources (res/)

```
✅ layouts/ (filtrer émulation seulement)
✅ drawable/ (icônes jeu)
✅ values/colors.xml (KITT theme)
✅ values/strings.xml (filtrer)
```

---

## ❌ Fichiers à NE PAS Copier (ChatAI)

### Activités ChatAI

```
❌ MainActivity.java
❌ ChatActivity.java
❌ AskGeminiActivity.java
❌ GeminiApiService.java
❌ Toutes activités non-émulation
```

### Services ChatAI

```
❌ GeminiChatService.java
❌ Intégrations API ChatAI
```

### Ressources ChatAI

```
❌ Layouts chat
❌ Drawables chat
❌ Strings ChatAI
```

---

## 🔧 Modifications Requises

### 1. Package Name

```
Rechercher: com.chatai
Remplacer: com.retroplay
```

**Fichiers concernés :** ~50 fichiers Java/Kotlin

### 2. Application Name

```xml
<!-- strings.xml -->
<string name="app_name">RetroPlay</string>
```

### 3. Launcher Activity

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".GameListActivity"
          android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

### 4. Suppression Références ChatAI

**Dans GameListActivity.java :**
- ❌ Retirer boutons Ask Gemini
- ❌ Retirer intégrations ChatAI
- ✅ Garder liste consoles
- ✅ Garder WebServer

---

## 📂 Structure Finale RetroPlay-Android

```
RetroPlay-Android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/retroplay/
│   │       │   ├── GameListActivity.java
│   │       │   ├── GameDetailsActivity.java
│   │       │   ├── NativeComposeEmulatorActivity.kt
│   │       │   ├── WebServer.java
│   │       │   ├── BackgroundService.java
│   │       │   ├── ConsoleConfigActivity.java
│   │       │   ├── model/
│   │       │   │   └── Game.java
│   │       │   ├── cheat/
│   │       │   │   ├── CheatActivity.kt
│   │       │   │   ├── CheatManager.kt
│   │       │   │   ├── CheatApplier.kt
│   │       │   │   └── ...
│   │       │   └── gamepad/
│   │       │       ├── TouchControllerSettingsManager.kt
│   │       │       ├── GamePadLayoutManager.kt
│   │       │       └── ...
│   │       ├── jniLibs/arm64-v8a/
│   │       │   ├── (19 cores .so)
│   │       │   └── ...
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_game_list.xml
│   │       │   │   ├── activity_game_details_modern.xml
│   │       │   │   └── ...
│   │       │   ├── values/
│   │       │   │   ├── colors.xml
│   │       │   │   └── strings.xml
│   │       │   └── drawable/
│   │       │       └── (icônes jeu)
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── lemuroid-touchinput/
├── retrograde-util/
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🎨 Identité Visuelle

### Logo/Icône

**Concept :**
- Manette rétro stylisée
- Couleurs : Rouge/Noir (KITT) ou Bleu/Orange (Gaming)
- Style : Moderne, flat design

### Thème de Couleurs

**Option A - Garder KITT (Rouge/Noir) :**
```
kitt_red: #FF0033
kitt_black: #010102
kitt_dark_red: #330000
```

**Option B - Nouveau Gaming (Bleu/Orange) :**
```
retro_blue: #0066FF
retro_orange: #FF6600
retro_dark: #001122
```

**Recommandation :** Option A (garder KITT, déjà bien implémenté)

---

## 🔧 Configuration Répertoires ROM

### Option A : Nouveau Répertoire RetroPlay

**Avantages :**
- ✅ Séparation complète de ChatAI
- ✅ Pas de conflit
- ✅ App standalone pure

**Inconvénients :**
- ❌ Dupliquer toutes les ROMs (~10+ GB)
- ❌ User doit tout reconfigurer

### Option B : Réutiliser GameLibrary-Data

**Avantages :**
- ✅ Pas de duplication
- ✅ ROMs déjà présentes
- ✅ BIOS déjà présents
- ✅ Cheats déjà présents

**Inconvénients :**
- ⚠️ Partagé entre ChatAI et RetroPlay

**Recommandation :** **Option B** (réutiliser GameLibrary-Data)

**Configuration :**
```java
// Dans WebServer.java et activités
private static final String ROM_BASE_PATH = "/storage/emulated/0/GameLibrary-Data/";
```

---

## 📋 Checklist Création

### Avant de Commencer

- [ ] Vérifier GameLibrary-Android comme référence
- [ ] Décider nom package final
- [ ] Décider thème couleurs
- [ ] Décider répertoire ROMs

### Phase 1 - Structure

- [ ] Créer répertoire RetroPlay-Android
- [ ] Copier structure Gradle de ChatAI
- [ ] Modifier settings.gradle
- [ ] Modifier package name dans build.gradle

### Phase 2 - Code

- [ ] Copier activités émulation
- [ ] Copier packages cheat/ et gamepad/
- [ ] Copier WebServer.java
- [ ] Renommer tous les packages
- [ ] Retirer références ChatAI
- [ ] Vérifier imports

### Phase 3 - Assets

- [ ] Copier 19 cores dans jniLibs/
- [ ] Copier modules lemuroid
- [ ] Copier layouts émulation
- [ ] Copier drawables
- [ ] Copier colors.xml

### Phase 4 - Configuration

- [ ] Configurer AndroidManifest.xml
- [ ] Définir launcher activity
- [ ] Configurer permissions
- [ ] Modifier strings.xml

### Phase 5 - Build

- [ ] Gradle sync
- [ ] Résoudre erreurs compilation
- [ ] Build APK
- [ ] Installer sur device
- [ ] Tester fonctionnalités

### Phase 6 - Validation

- [ ] Test 5 consoles natives
- [ ] Test WASM
- [ ] Test cheats
- [ ] Test save states
- [ ] Test extraction .zip/.7z

---

## ⚠️ Points d'Attention

### 1. Dépendances Gradle

**S'assurer d'avoir :**
```gradle
implementation 'com.github.Swordfish90:LibretroDroid:0.13.0'
implementation 'org.apache.commons:commons-compress:1.25.0'
implementation 'org.tukaani:xz:1.9'
implementation platform('androidx.compose:compose-bom:2024.02.02')
// ... toutes les dépendances nécessaires
```

### 2. Modules Lemuroid

**Inclure dans settings.gradle :**
```gradle
include ':app'
include ':lemuroid-touchinput'
include ':retrograde-util'
```

### 3. WebServer

**Vérifier les chemins :**
- EmulatorJS assets
- GameLibrary-Data access
- Ports (8888)

---

## 📊 Comparaison Apps

| Fonctionnalité | ChatAI | RetroPlay | GameLibrary |
|----------------|--------|-----------|-------------|
| **Émulation Native** | ✅ 19 cores | ✅ 19 cores | ✅ Même cores |
| **Émulation WASM** | ✅ | ✅ | ✅ |
| **Double boutons** | ✅ | ✅ | ✅ |
| **Cheats** | ✅ | ✅ | ✅ |
| **Chat/Gemini** | ✅ | ❌ | ❌ |
| **Focus** | Chat + Jeux | **Jeux uniquement** | Jeux uniquement |

---

## 🚀 Prochaines Étapes

**Une fois le plan validé :**

1. **Créer structure** RetroPlay-Android
2. **Copier fichiers** sélectivement
3. **Refactorer** package names
4. **Nettoyer** références ChatAI
5. **Compiler** et tester
6. **Documenter** RetroPlay

**Temps estimé total :** 4-5 heures

---

## ❓ Décisions à Prendre

**Avant de commencer, confirmez :**

1. **Package name :** `com.retroplay` ? ✅
2. **Thème :** Garder KITT (rouge/noir) ? ❓
3. **Répertoire ROMs :** Réutiliser GameLibrary-Data ? ❓
4. **Port WebServer :** Garder 8888 ? ❓
5. **Launcher :** GameListActivity directement ? ❓

---

**Validez ces décisions et je commence la création ! 🚀**

---

*Plan créé le 20 octobre 2025*  
*RetroPlay-Android - Standalone Emulator*  
*Basé sur ChatAI-Android émulation system*

