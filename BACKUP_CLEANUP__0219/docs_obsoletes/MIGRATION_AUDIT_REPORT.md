# RAPPORT D'AUDIT - Migration vers Lemuroid-TouchInput (Compose)

## 📊 État Actuel du Projet ChatAI

### Versions et Dépendances
- **Kotlin**: 1.9.10
- **Android Gradle Plugin**: 8.1.4
- **Compile SDK**: 34
- **Target SDK**: 34
- **Min SDK**: 24

### Gamepad Actuel
- **Bibliothèque**: `radialgamepad:08d1dd95`
- **Type**: View classique (Canvas)
- **Architecture**: XML Layouts + Kotlin
- **Police**: Système par défaut (ne respecte pas les thèmes)
- **Problème**: Symboles PlayStation non-uniformes

### Fichiers Concernés
1. `NativeEmulatorActivity.kt` (876 lignes)
   - `initializeVirtualGamePad()` - ligne 284-285
   - `handleEvent()` - gestion des événements RadialGamePad
   - `onConfigurationChanged()` - gestion orientation

2. `GamePadConfigManager.kt`
   - Gestion des configs par console
   - SharedPreferences pour settings

3. Configs gamepad (11 fichiers dans `gamepad/configs/`)
   - PSXGamePadConfig.kt
   - PSPGamePadConfig.kt
   - N64GamePadConfig.kt, SNESGamePadConfig.kt, etc.

---

## 🎯 Cible: Lemuroid-TouchInput

### Versions et Dépendances
- **Kotlin**: 2.0.21 ⚠️ (Écart de 3 versions majeures)
- **Compose BOM**: 2024.02.02
- **Kotlin Compiler Extension**: 1.4.6
- **Architecture**: Jetpack Compose
- **Build Tools**: 34.0.0

### Dépendances Compose Requises
```kotlin
// BOM
implementation(platform("androidx.compose:compose-bom:2024.02.02"))

// Core Compose
implementation("androidx.compose.ui:ui-geometry")
implementation("androidx.compose.runtime:runtime")
implementation("androidx.compose.material3:material3")

// Activity Compose
implementation("androidx.activity:activity-compose:1.7.2")

// Lifecycle Compose
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")

// Autres
implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
implementation("io.github.swordfish90:padkit:1.0.0-beta1")
```

### Plugins Gradle Requis
```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.6"
    }
}
```

---

## ⚠️ RISQUES MAJEURS IDENTIFIÉS

### 1. Migration Kotlin 1.9.10 → 2.0.21
- **Impact**: TRÈS ÉLEVÉ
- **Risques**:
  - Breaking changes dans K2 compiler
  - Modifications API stdlib
  - Changements dans coroutines
  - Problèmes avec autres dépendances

### 2. Introduction de Jetpack Compose
- **Impact**: ÉLEVÉ
- **Risques**:
  - Taille APK augmente de ~5-7 MB
  - Nouvelle courbe d'apprentissage
  - Nécessite refactoring majeur de NativeEmulatorActivity
  - Peut créer conflits avec Views existantes

### 3. Architecture Compose vs Views
- **Impact**: ÉLEVÉ
- **Changements**:
  - XML Layouts → Composables
  - FrameLayout → ComposeView
  - View.addView() → setContent { }
  - Événements différents

### 4. Dépendances Additionnelles
- **padkit**: Bibliothèque de gestion gamepad
- **collections-immutable**: Pour états Compose
- **lifecycle-viewmodel-compose**: ViewModel Compose
- **Augmentation totale APK**: ~8-10 MB

### 5. Compatibilité avec LibretroDroid
- **LibretroDroid**: 0.13.0 (reste compatible)
- **GLRetroView**: View classique (pas Compose)
- **Risque**: Mélange View + Compose peut causer bugs

---

## 📋 PLAN DE MIGRATION DÉTAILLÉ

### PHASE 1: Préparation (2-3 heures)
✅ **Backup complet**
- [ ] Commit git de l'état actuel
- [ ] Tag release "pre-compose-migration"
- [ ] Backup des fichiers critiques

✅ **Mise à jour Kotlin**
- [ ] Kotlin 1.9.10 → 2.0.21
- [ ] Android Gradle Plugin 8.1.4 → 8.4.0
- [ ] Vérifier compilation de base
- [ ] Tester l'app existante

### PHASE 2: Ajout Compose (1-2 heures)
- [ ] Ajouter Compose BOM
- [ ] Ajouter dépendances Compose core
- [ ] Configurer composeOptions
- [ ] Ajouter plugin compose
- [ ] Build test sans modification code

### PHASE 3: Migration NativeEmulatorActivity (3-4 heures)
- [ ] Créer branch "compose-gamepad"
- [ ] Convertir layout XML en Composable
- [ ] Remplacer FrameLayout par ComposeView
- [ ] Migrer logique gamepad vers Compose
- [ ] Adapter gestion événements

### PHASE 4: Integration Lemuroid-TouchInput (2-3 heures)
- [ ] Copier module lemuroid-touchinput
- [ ] Adapter configs gamepad pour nouveau API
- [ ] Intégrer PromptFont avec LocalFontFamily
- [ ] Tester affichage symboles

### PHASE 5: Tests et Validation (2-3 heures)
- [ ] Test PSX (DualShock + Basic)
- [ ] Test PSP
- [ ] Test N64, SNES, NES, GBA
- [ ] Test Genesis (6-button + 3-button)
- [ ] Test rotation écran
- [ ] Test settings gamepad

### PHASE 6: Rollback Plan
- [ ] Créer script de rollback automatique
- [ ] Documenter procédure manuelle
- [ ] Garder anciennes dépendances commentées

---

## 🔴 POINTS DE DÉCISION CRITIQUES

### Option A: Migration Complète (Recommandée pour long terme)
**Avantages**:
- ✅ PromptFont fonctionnera parfaitement
- ✅ Architecture moderne
- ✅ Meilleure maintenabilité
- ✅ Support futur assuré

**Inconvénients**:
- ❌ Temps: 10-15 heures
- ❌ Risque élevé de bugs
- ❌ APK +8-10 MB
- ❌ Nécessite tests exhaustifs

### Option B: Garder Symboles Unicode (Recommandée pour maintenant)
**Avantages**:
- ✅ Fonctionne immédiatement
- ✅ Zéro risque
- ✅ Pas de changement architecture
- ✅ Symboles reconnaissables

**Inconvénients**:
- ⚠️ Légèrement non-uniformes
- ⚠️ Pas de PromptFont

### Option C: Fork RadialGamePad avec PromptFont (Compromis)
**Avantages**:
- ✅ PromptFont sans Compose
- ✅ Architecture actuelle préservée
- ✅ Modification ciblée

**Inconvénients**:
- ⚠️ Maintenance du fork
- ⚠️ Temps: 4-6 heures
- ⚠️ Pas de mises à jour upstream

---

## 💡 RECOMMANDATION FINALE

### ⚠️ **NE PAS MIGRER MAINTENANT**

**Raisons**:
1. **Trop risqué** pour bénéfice cosmétique
2. **Écart Kotlin trop important** (1.9.10 → 2.0.21)
3. **Symboles actuels fonctionnent** (○ ✕ △ ■)
4. **Temps requis** vs valeur ajoutée disproportionné

### ✅ **Actions Recommandées**:
1. **Accepter les symboles Unicode actuels**
2. **Documenter pour future migration** (ce rapport)
3. **Planifier migration majeure** quand Kotlin 2.x devient standard
4. **Considérer fork RadialGamePad** si vraiment nécessaire

---

## 📝 NOTES TECHNIQUES

### Différence API: RadialGamePad vs Lemuroid-TouchInput

**RadialGamePad (View)**:
```kotlin
leftPad = RadialGamePad(settings.leftConfig, settings.scale, this)
leftPad.events().collect { event -> handleEvent(event) }
```

**Lemuroid-TouchInput (Compose)**:
```kotlin
@Composable
fun GamePadScreen() {
    LemuroidTouchOverlay(
        config = settings.leftConfig,
        onEvent = { event -> handleEvent(event) }
    )
}
```

### PromptFont dans Compose
```kotlin
val promptFont = FontFamily(Font(R.font.promptfont))

CompositionLocalProvider(LocalFontFamily provides promptFont) {
    LemuroidTouchOverlay(...)
}
```

---

## ✅ CONCLUSION

**Migration vers Lemuroid-TouchInput est FAISABLE mais NON RECOMMANDÉE dans contexte actuel.**

Le rapport coût/bénéfice est défavorable. Les symboles Unicode actuels (○ ✕ △ ■) sont une solution pragmatique et fonctionnelle.

**Si migration future souhaitée**: Attendre Kotlin 2.x stabilisé, puis suivre ce plan en 6 phases.

