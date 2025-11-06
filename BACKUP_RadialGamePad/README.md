# BACKUP RadialGamePad - Configs Personnalisées

**Date de sauvegarde** : 18 Octobre 2025

Ces fichiers sont les **configurations RadialGamePad originales** avant migration vers Lemuroid-TouchInput Compose.

## 📦 Contenu

- `GamePadConfigManager.kt` - Gestionnaire principal de configs
- `configs/` - Configurations par console
  - `SharedGamePadButtons.kt` - Boutons partagés
  - `PSXGamePadConfig.kt` - PlayStation 1 (DualShock + Basic)
  - `PSPGamePadConfig.kt` - PlayStation Portable
  - `N64GamePadConfig.kt` - Nintendo 64
  - `SNESGamePadConfig.kt` - Super Nintendo
  - `NESGamePadConfig.kt` - Nintendo Entertainment System
  - `GBAGamePadConfig.kt` - Game Boy Advance
  - `GBGamePadConfig.kt` - Game Boy / Game Boy Color
  - `GenesisGamePadConfig.kt` - Sega Genesis (3-button + 6-button)
  - `GenericGamePadConfig.kt` - Configuration générique

## 🔧 Utilisation

Ces fichiers fonctionnent avec :
- `RadialGamePad` version `08d1dd95`
- Android View (Canvas)
- Architecture XML + Kotlin

## 📋 Dépendances

```gradle
implementation 'com.github.swordfish90:radialgamepad:08d1dd95'
```

## ⚠️ Note

Ces configs ont été **remplacées par Lemuroid-TouchInput** (Compose) dans ChatAI pour avoir des symboles PlayStation uniformes.

Conservez ce backup pour **futurs projets** ou **référence**.

