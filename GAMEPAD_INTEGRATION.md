# Integration RadialGamePad avec LibretroDroid

## Vue d'ensemble

Ce document explique comment intégrer RadialGamePad (contrôles tactiles virtuels) avec LibretroDroid (émulation Libretro native Android) pour créer une expérience d'émulation complète avec gamepads configurables.

## Architecture

```
NativeEmulatorActivity (Kotlin)
    ├── GLRetroView (LibretroDroid)
    │   └── Libretro Core (.so files)
    ├── RadialGamePad (Left)
    │   └── Events Flow → handleEvent()
    └── RadialGamePad (Right)
        └── Events Flow → handleEvent()
```

## Dépendances requises

### build.gradle (project level)
```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### app/build.gradle
```gradle
dependencies {
    // LibretroDroid pour émulation native
    implementation 'com.github.Swordfish90:LibretroDroid:0.13.0'
    
    // RadialGamePad pour contrôles tactiles
    implementation 'com.github.swordfish90:radialgamepad:08d1dd95'
    
    // Coroutines pour Flow events
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
}
```

## Structure des fichiers

```
app/src/main/
├── java/com/chatai/
│   ├── NativeEmulatorActivity.kt       ← Activité principale
│   └── gamepad/
│       └── GamePadConfigManager.kt     ← Configurations par console
├── res/
│   ├── layout/
│   │   ├── activity_native_emulator.xml           ← Portrait
│   │   └── dialog_gamepad_settings.xml            ← Menu config
│   └── layout-land/
│       └── activity_native_emulator.xml           ← Paysage
└── jniLibs/arm64-v8a/
    ├── pcsx_rearmed_libretro_android.so   ← Cores Libretro
    ├── parallel_n64_libretro_android.so
    └── ...
```

## Layouts

### Portrait (layout/activity_native_emulator.xml)

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    <!-- Écran d'émulation : 55% en haut -->
    <FrameLayout id="emulatorContainer"
        constraintHeight_percent="0.55"
        constraintTop_toTopOf="parent" />
    
    <!-- Gamepad gauche : 0% → 50% -->
    <FrameLayout id="leftGamePadContainer"
        constraintStart_toStartOf="parent"
        constraintEnd_toStartOf="guideline" />
    
    <!-- Gamepad droit : 50% → 100% -->
    <FrameLayout id="rightGamePadContainer"
        constraintStart_toEndOf="guideline"
        constraintEnd_toEndOf="parent" />
    
    <!-- Guideline vertical à 50% -->
    <Guideline id="guideline"
        constraintGuide_percent="0.5" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

**Important** : Le guideline à 50% évite la superposition des zones tactiles.

### Paysage (layout-land/activity_native_emulator.xml)

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    <!-- Écran d'émulation : Fullscreen -->
    <FrameLayout id="emulatorContainer"
        constraintTop/Bottom/Left/Right_toParentOf="parent" />
    
    <!-- Gamepad gauche : Extrémité gauche, max 25% width -->
    <FrameLayout id="leftGamePadContainer"
        constraintWidth_percent="0.25"
        constraintWidth_max="250dp"
        constraintStart_toStartOf="parent" />
    
    <!-- Gamepad droit : Extrémité droite, max 25% width -->
    <FrameLayout id="rightGamePadContainer"
        constraintWidth_percent="0.25"
        constraintWidth_max="250dp"
        constraintEnd_toEndOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

**Résultat** : Les gamepads sont poussés aux extrémités, l'écran est visible au centre.

## Code d'intégration

### 1. Initialisation dans `onCreate()`

```kotlin
class NativeEmulatorActivity : AppCompatActivity() {
    private lateinit var retroView: GLRetroView
    private var leftPad: RadialGamePad? = null
    private var rightPad: RadialGamePad? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_native_emulator)
        
        val console = intent.getStringExtra("console") ?: "nes"
        
        // 1. Initialiser l'émulateur Libretro
        initializeEmulator(romPath, console, gameName)
        
        // 2. Initialiser les gamepads
        initializeVirtualGamePad()
        
        // 3. Setup menu button
        findViewById<FloatingActionButton>(R.id.fabGamePadSettings)?.setOnClickListener {
            showGamePadSettings()
        }
    }
}
```

### 2. Initialisation des GamePads

```kotlin
private fun initializeVirtualGamePad() {
    try {
        val console = intent.getStringExtra("console") ?: "generic"
        
        // Récupérer la configuration spécifique à la console
        val settings = GamePadConfigManager.getConfigForConsole(this, console)
        
        // Créer les gamepads avec la configuration
        leftPad = RadialGamePad(settings.leftConfig, settings.scale, this)
        rightPad = RadialGamePad(settings.rightConfig, settings.scale, this)
        
        // Positionner (ancrage en bas)
        leftPad?.apply {
            gravityX = -1f  // Gauche
            gravityY = 1f   // Bas
            alpha = settings.alpha
        }
        
        rightPad?.apply {
            gravityX = 1f   // Droite
            gravityY = 1f   // Bas
            alpha = settings.alpha
        }
        
        // Ajouter aux conteneurs
        findViewById<FrameLayout>(R.id.leftGamePadContainer)?.addView(leftPad)
        findViewById<FrameLayout>(R.id.rightGamePadContainer)?.addView(rightPad)
        
        // Collecter les événements SÉPARÉMENT pour chaque pad
        lifecycleScope.launch {
            leftPad!!.events()
                .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
                .collect { event -> handleEvent(event) }
        }
        
        lifecycleScope.launch {
            rightPad!!.events()
                .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
                .collect { event -> handleEvent(event) }
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error initializing virtual gamepad", e)
    }
}
```

**Important** : 
- Ne PAS utiliser `merge()` pour combiner les flows au début
- Tester avec des flows séparés pour déboguer
- Les conteneurs DOIVENT être séparés (ConstraintLayout + Guideline)

### 3. Gestion des événements

```kotlin
private fun handleEvent(event: Event) {
    if (!::retroView.isInitialized) return
    
    when (event) {
        is Event.Button -> {
            // Tous les boutons → sendKeyEvent
            retroView.sendKeyEvent(event.action, event.id)
        }
        is Event.Direction -> {
            // Mapping des directions vers sources Libretro
            val source = when (event.id) {
                0 -> GLRetroView.MOTION_SOURCE_DPAD           // Cross (D-pad)
                1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT    // Left Stick
                2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT   // Right Stick
                else -> GLRetroView.MOTION_SOURCE_DPAD
            }
            retroView.sendMotionEvent(source, event.xAxis, event.yAxis)
        }
    }
}
```

**Mapping des IDs** :
- `Event.Direction.id = 0` → D-pad (Cross)
- `Event.Direction.id = 1` → Stick gauche
- `Event.Direction.id = 2` → Stick droit

## Configuration par console

### GamePadConfigManager.kt

```kotlin
object GamePadConfigManager {
    
    enum class Variant {
        DEFAULT,
        PSX_DUALSHOCK,    // PSX avec sticks
        PSX_BASIC,        // PSX sans sticks
        GENESIS_3,        // Genesis 3 boutons
        GENESIS_6         // Genesis 6 boutons
    }
    
    fun getConfigForConsole(context: Context, console: String): GamePadSettings {
        // Récupérer settings (scale, alpha, variant)
        // Retourner la config adaptée
    }
    
    // Exemple PSX DualShock
    private val PSX_DUALSHOCK_LEFT = RadialGamePadConfig(
        sockets = 12,
        primaryDial = PrimaryDialConfig.Cross(CrossConfig(0)),
        secondaryDials = listOf(
            SecondaryDialConfig.SingleButton(3, 1f, 0f, 
                ButtonConfig(id = KeyEvent.KEYCODE_BUTTON_L1, label = "L1")),
            SecondaryDialConfig.SingleButton(4, 1f, 0f,
                ButtonConfig(id = KeyEvent.KEYCODE_BUTTON_L2, label = "L2")),
            SecondaryDialConfig.SingleButton(2, 1f, 0f,
                ButtonConfig(id = KeyEvent.KEYCODE_BUTTON_SELECT, label = "SELECT")),
            SecondaryDialConfig.Stick(9, 2, 2.0f, 0.1f, 1,
                KeyEvent.KEYCODE_BUTTON_THUMBL, contentDescription = "L3")
        )
    )
}
```

### Paramètres RadialGamePadConfig

- **sockets** : Nombre d'emplacements circulaires (12 recommandé)
- **primaryDial** : Contrôle central (Cross ou PrimaryButtons)
- **secondaryDials** : Liste de contrôles secondaires

### Types de contrôles secondaires

```kotlin
// Bouton simple
SecondaryDialConfig.SingleButton(
    index,          // Position (0-11)
    scale,          // Taille (1f = normal)
    distance,       // Distance du centre (0f = collé)
    ButtonConfig(id, label)
)

// Stick analogique
SecondaryDialConfig.Stick(
    index,          // Position
    spread,         // Nombre d'emplacements occupés
    scale,          // Taille
    distance,       // Distance
    id,             // ID du stick (1 ou 2)
    pressId,        // ID du bouton L3/R3
    contentDescription
)

// D-pad secondaire (N64 C-buttons)
SecondaryDialConfig.Cross(
    index,
    spread,
    scale,
    distance,
    CrossConfig(id)
)

// Espace vide (symétrie)
SecondaryDialConfig.Empty(
    index,
    spread,
    scale,
    distance
)
```

### Positions angulaires (index → degrés)

Pour `sockets = 12` :
```
Index  Angle   Position visuelle
  0     0°     Droite (3h)
  1    30°     
  2    60°     Haut-droite (2h)
  3    90°     Haut (12h)
  4   120°     Haut-gauche (10h)
  ...
  9   270°     Bas (6h)
```

**Positions typiques** :
- SELECT : 120° (haut-gauche)
- L1/R1 : 90° (haut)
- L2/R2 : 60° ou 120°
- START : 60° (haut-droite)
- Sticks : 270° (-80° en code Lemuroid)

## Consoles supportées

| Console | Variantes | Boutons principaux | Sticks | Notes |
|---------|-----------|-------------------|--------|-------|
| **PSX** | Basic / DualShock | ✕●■▲ | 0 ou 2 | DualShock par défaut |
| **N64** | - | A/B/Z | 1 | C-buttons = Cross secondaire |
| **SNES** | - | A/B/X/Y | 0 | L/R shoulder |
| **NES** | - | A/B | 0 | Simple |
| **GBA** | - | A/B | 0 | L/R shoulder |
| **GB/GBC** | - | A/B | 0 | Simple |
| **PSP** | - | ✕●■▲ | 1 | Stick gauche uniquement |
| **Genesis** | 3-button / 6-button | A/B/C (+X/Y/Z) | 0 | 6-button par défaut |

## Menu de configuration

### Accès
1. **FAB** : Bouton flottant en haut à droite
2. **Bouton MENU** : Touche physique (si disponible)

### Options
- **Layout** : Sélection de variante (PSX, Genesis)
- **Scale** : 4-16 (taille des gamepads, nécessite redémarrage)
- **Transparency** : 20%-100% (temps réel)

### Sauvegarde
- **SharedPreferences** : `gamepad_settings`
- **Clés** :
  - `gamepad_scale` : Float (4-16)
  - `gamepad_alpha` : Float (0.2-1.0)
  - `gamepad_variant_{console}` : String (nom du Variant)

## Problèmes courants et solutions

### 1. Gamepad gauche ne répond pas
**Cause** : Superposition des conteneurs (`match_parent` sur les deux)
**Solution** : Utiliser `ConstraintLayout` avec `Guideline` à 50%

```xml
<!-- MAUVAIS -->
<FrameLayout leftContainer match_parent />
<FrameLayout rightContainer match_parent />  ← Couvre le gauche !

<!-- BON -->
<ConstraintLayout>
    <FrameLayout left constraintEnd_toStartOf="guideline" />
    <FrameLayout right constraintStart_toEndOf="guideline" />
    <Guideline percent="0.5" />
</ConstraintLayout>
```

### 2. Directions (D-pad) ne fonctionnent pas
**Cause** : Mauvais mapping des événements `Direction`
**Solution** : Utiliser `sendMotionEvent()` avec la source correcte

```kotlin
// MAUVAIS : Essayer de convertir en KeyEvent
when (event.id) {
    KeyEvent.KEYCODE_DPAD_UP -> ...  // Ne marchera jamais !
}

// BON : Mapper l'ID vers la source motion
when (event.id) {
    0 -> GLRetroView.MOTION_SOURCE_DPAD
    1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
    2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
}
retroView.sendMotionEvent(source, event.xAxis, event.yAxis)
```

### 3. Sticks analogiques invisibles
**Cause** : `android:alpha="0"` dans le layout XML
**Solution** : Retirer l'alpha du XML, le gérer en code

```kotlin
leftPad?.alpha = 0.85f  // En code, pas en XML
```

### 4. Crash avec MaterialButton
**Cause** : Thème AppCompat incompatible avec Material3
**Solution** : Utiliser `Button` standard

```xml
<!-- MAUVAIS -->
<com.google.android.material.button.MaterialButton />

<!-- BON -->
<Button android:id="@+id/resetButton" />
```

### 5. Événements collectés mais pas envoyés
**Cause** : `merge()` des flows peut masquer des problèmes
**Solution** : Debug avec flows séparés

```kotlin
// Debug
lifecycleScope.launch {
    leftPad!!.events().collect { event ->
        Log.d(TAG, "LEFT PAD EVENT: ${event.javaClass.simpleName}")
        handleEvent(event)
    }
}

lifecycleScope.launch {
    rightPad!!.events().collect { event ->
        Log.d(TAG, "RIGHT PAD EVENT: ${event.javaClass.simpleName}")
        handleEvent(event)
    }
}
```

## API RadialGamePad

### Types d'événements

```kotlin
sealed class Event {
    // Boutons (A, B, X, Y, L1, R1, SELECT, START, etc.)
    data class Button(val id: Int, val action: Int) : Event()
    
    // Directions (D-pad, sticks)
    data class Direction(val id: Int, val xAxis: Float, val yAxis: Float) : Event()
    
    // Gestures (tap, double tap, triple tap)
    data class Gesture(val id: Int, val type: GestureType) : Event()
}
```

### Construction d'une config

```kotlin
val config = RadialGamePadConfig(
    sockets = 12,
    primaryDial = PrimaryDialConfig.Cross(CrossConfig(0)),
    secondaryDials = listOf(
        // Bouton à 90° (haut)
        SecondaryDialConfig.SingleButton(
            3,    // index (90° pour sockets=12)
            1f,   // scale
            0f,   // distance
            ButtonConfig(
                id = KeyEvent.KEYCODE_BUTTON_L1,
                label = "L1"
            )
        ),
        
        // Stick analogique à 270° (bas)
        SecondaryDialConfig.Stick(
            9,     // index (270°)
            2,     // spread (occupe 2 slots)
            2.0f,  // scale (double taille)
            0.1f,  // distance
            1,     // stick ID (1=gauche, 2=droite)
            KeyEvent.KEYCODE_BUTTON_THUMBL,  // L3 press
            contentDescription = "Left Stick"
        )
    )
)
```

### Création du gamepad

```kotlin
val pad = RadialGamePad(config, 12f, context)
pad.gravityX = -1f  // -1 = gauche, 1 = droite, 0 = centre
pad.gravityY = 1f   // -1 = haut, 1 = bas, 0 = centre
pad.alpha = 0.85f   // Transparence
```

## Variantes de layouts

### PSX (2 variantes)

**PSX Basic** : Contrôleur original sans sticks
- Utilisé pour : Jeux PS1 anciens (Crash Bandicoot, Spyro)
- Boutons : D-pad, ✕●■▲, L1/L2, R1/R2, SELECT, START

**PSX DualShock** : Contrôleur avec sticks analogiques
- Utilisé pour : Jeux PS1 modernes (Metal Gear Solid, Ape Escape)
- Boutons : Tout + 2 sticks (L3/R3)

### Genesis (2 variantes)

**Genesis 3-button** : Contrôleur original
- Utilisé pour : Jeux Genesis anciens (Sonic, Streets of Rage)
- Boutons : D-pad, A, B, C, START

**Genesis 6-button** : Contrôleur étendu
- Utilisé pour : Jeux de combat (Street Fighter II)
- Boutons : D-pad, A, B, C, X, Y, Z, START

## Optimisations Lemuroid

### Positions précises (angles en degrés)

D'après le code Lemuroid, positions optimales :

```kotlin
// Left pad
SecondaryButtonL1()          // 90° (haut)
SecondaryButtonL2()          // 120° (haut-gauche)
SecondaryButtonSelect(2)     // 120° - 30° * 2 = 60°
SecondaryAnalogLeft()        // -80° = 280° (bas-gauche)

// Right pad
SecondaryButtonR1()          // 90° (haut)
SecondaryButtonR2()          // 60° (haut-droite)
SecondaryButtonStart(2)      // 60° + 30° * 2 = 120°
SecondaryAnalogRight()       // +80° - 180° = -100° = 260° (bas-droite)
SecondaryButtonMenu()        // -60° + rotation = variable
```

### Scale recommandée

- **Portrait** : 12-14 (plus gros car moins d'espace horizontal)
- **Paysage** : 8-10 (plus petit, écran plus large)

## Intégration future

### Pour d'autres projets

1. **Copier les fichiers** :
   - `GamePadConfigManager.kt`
   - `activity_native_emulator.xml` (portrait + land)
   - `dialog_gamepad_settings.xml`

2. **Adapter le code** :
   - Remplacer `GLRetroView` par votre moteur d'émulation
   - Adapter `handleEvent()` selon votre API
   - Modifier les layouts selon vos consoles

3. **Ajouter les dépendances** :
   ```gradle
   implementation 'com.github.swordfish90:radialgamepad:08d1dd95'
   implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
   ```

### Pour Lemuroid complet (Jetpack Compose)

Si vous voulez utiliser les **vrais layouts Lemuroid** avec Compose :
- Nécessite upgrade vers **Kotlin 2.1.0+**
- Dépendances : PadKit, Compose BOM, kotlinx-serialization
- Complexité élevée (Dagger 2, Navigation Component, etc.)

**Alternative** : Utiliser RadialGamePad (ce qui est fait ici) avec des configs inspirées de Lemuroid.

## Tests

### Vérifier que tout fonctionne

```bash
# 1. Lancer l'app et un jeu PSX
adb logcat -s NativeEmulatorActivity:I NativeEmulatorActivity:D

# 2. Tester le D-pad gauche
# → Devrait voir "LEFT PAD EVENT: Direction"

# 3. Tester les boutons droits
# → Devrait voir "RIGHT PAD EVENT: Button"

# 4. Ouvrir le menu (FAB)
# → Dialogue avec sliders

# 5. Changer transparence
# → Gamepads deviennent plus/moins visibles en temps réel
```

## Références

- **RadialGamePad** : https://github.com/Swordfish90/RadialGamePad
- **LibretroDroid** : https://github.com/Swordfish90/LibretroDroid
- **Lemuroid** : https://github.com/Swordfish90/Lemuroid
- **Libretro Docs** : https://docs.libretro.com/

## Auteur & Date

Intégration réalisée : 16 octobre 2025
Projet : ChatAI-Android
Basé sur : Lemuroid (Swordfish90) layouts

