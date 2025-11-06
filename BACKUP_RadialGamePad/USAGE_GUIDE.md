# Guide d'Utilisation - RadialGamePad Configs

Ce backup contient les configurations RadialGamePad personnalisées créées pour ChatAI avant la migration vers Lemuroid-TouchInput.

## 📦 Pour Utiliser dans un Nouveau Projet

### 1. Ajouter la dépendance

Dans `app/build.gradle` :
```gradle
dependencies {
    // RadialGamePad
    implementation 'com.github.swordfish90:radialgamepad:08d1dd95'
    
    // LibretroDroid (si émulation)
    implementation 'com.github.Swordfish90:LibretroDroid:0.13.0'
}
```

### 2. Copier les fichiers de config

Copiez le dossier `configs/` vers votre projet :
```
src/main/java/com/votrepackage/gamepad/
├── GamePadConfigManager.kt
└── configs/
    ├── SharedGamePadButtons.kt
    ├── PSXGamePadConfig.kt
    ├── PSPGamePadConfig.kt
    ├── N64GamePadConfig.kt
    ├── SNESGamePadConfig.kt
    ├── NESGamePadConfig.kt
    ├── GBAGamePadConfig.kt
    ├── GBGamePadConfig.kt
    ├── GenesisGamePadConfig.kt
    └── GenericGamePadConfig.kt
```

### 3. Utilisation dans une Activity

```kotlin
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.event.Event
import com.votrepackage.gamepad.GamePadConfigManager

class EmulatorActivity : AppCompatActivity() {
    private var leftPad: RadialGamePad? = null
    private var rightPad: RadialGamePad? = null
    
    private fun initializeVirtualGamePad() {
        val console = "psx"  // ou autre console
        
        // Récupérer la config
        val settings = GamePadConfigManager.getConfigForConsole(this, console)
        
        // Créer les gamepads
        leftPad = RadialGamePad(settings.leftConfig, settings.scale, this)
        rightPad = RadialGamePad(settings.rightConfig, settings.scale, this)
        
        // Configurer apparence
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
        findViewById<FrameLayout>(R.id.leftGamePadContainer).addView(leftPad)
        findViewById<FrameLayout>(R.id.rightGamePadContainer).addView(rightPad)
        
        // Écouter les événements
        lifecycleScope.launch {
            leftPad!!.events().collect { event -> handleEvent(event) }
        }
        
        lifecycleScope.launch {
            rightPad!!.events().collect { event -> handleEvent(event) }
        }
    }
    
    private fun handleEvent(event: Event) {
        when (event) {
            is Event.Button -> {
                // Boutons : A, B, X, Y, L1, R1, etc.
                if (event.id == KeyEvent.KEYCODE_BUTTON_MODE) {
                    showGamePadSettings()
                } else {
                    retroView.sendKeyEvent(event.action, event.id)
                }
            }
            is Event.Direction -> {
                // Directions : D-Pad, Analog sticks
                val source = when (event.id) {
                    0 -> GLRetroView.MOTION_SOURCE_DPAD
                    1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                    2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                    else -> GLRetroView.MOTION_SOURCE_DPAD
                }
                retroView.sendMotionEvent(source, event.xAxis, event.yAxis)
            }
        }
    }
}
```

### 4. Layout XML

```xml
<ConstraintLayout>
    <!-- Emulator Container -->
    <FrameLayout
        android:id="@+id/emulatorContainer"
        android:layout_width="0dp"
        android:layout_height="0dp" />
    
    <!-- Left GamePad Container -->
    <FrameLayout
        android:id="@+id/leftGamePadContainer"
        android:layout_width="0dp"
        android:layout_height="0dp" />
    
    <!-- Right GamePad Container -->
    <FrameLayout
        android:id="@+id/rightGamePadContainer"
        android:layout_width="0dp"
        android:layout_height="0dp" />
</ConstraintLayout>
```

## 🎮 Consoles Supportées

| Console | Config File | Variants |
|---------|-------------|----------|
| PlayStation 1 | PSXGamePadConfig.kt | DualShock, Basic |
| PlayStation Portable | PSPGamePadConfig.kt | Standard |
| Nintendo 64 | N64GamePadConfig.kt | Standard |
| Super Nintendo | SNESGamePadConfig.kt | Standard |
| NES | NESGamePadConfig.kt | Standard |
| Game Boy Advance | GBAGamePadConfig.kt | Standard |
| Game Boy / GBC | GBGamePadConfig.kt | Standard |
| Sega Genesis | GenesisGamePadConfig.kt | 3-button, 6-button |
| Générique | GenericGamePadConfig.kt | Fallback |

## ⚙️ Features

- ✅ Configs par console
- ✅ Variants multiples (ex: Genesis 3/6 boutons)
- ✅ Boutons partagés réutilisables
- ✅ Menu settings intégré (bouton ⚙)
- ✅ Placeholder pour symétrie
- ✅ Support Scale, Alpha, Margins
- ✅ Persistance SharedPreferences

## 🔄 Migration vers Lemuroid-TouchInput

Ces configs ont été remplacées par **Lemuroid-TouchInput (Compose)** pour bénéficier de :
- Symboles PlayStation uniformes (VectorDrawables)
- Architecture Compose moderne
- Live preview des settings
- Support rotation gamepad
- Meilleure intégration Lemuroid

## 📚 Référence

Voir aussi :
- `GAMEPAD_INTEGRATION.md` - Documentation intégration RadialGamePad
- `COMPOSE_INTEGRATION_SUCCESS.md` - Migration vers Lemuroid-TouchInput

