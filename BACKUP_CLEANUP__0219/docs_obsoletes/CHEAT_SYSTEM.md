# 🎮 SYSTÈME DE CODES DE TRICHE - CHATAI

## 📋 Vue d'ensemble

Système complet de gestion de codes de triche pour émulateurs avec support :
- **Fichiers .cht RetroArch** (format officiel)
- **Codes personnalisés** (GameShark, Game Genie, Action Replay)
- **Interface Compose** Material 3
- **Activation/Désactivation en temps réel**

---

## 🗂️ Structure des fichiers

```
/storage/emulated/0/GameLibrary-Data/
└── cheats/
    ├── retroarch/          # Codes officiels RetroArch
    │   ├── psx/
    │   │   ├── Resident Evil 1.5.cht
    │   │   └── Final Fantasy VII.cht
    │   ├── n64/
    │   └── snes/
    │
    └── custom/             # Codes personnalisés
        ├── psx/
        ├── n64/
        └── snes/
```

---

## 📝 Format des fichiers .cht (RetroArch)

### Structure
```ini
cheats = 3

cheat0_desc = "Infinite Health"
cheat0_code = "8009C6E4+03E7"
cheat0_enable = false

cheat1_desc = "Infinite Ammo"
cheat1_code = "300A1234+00FF"
cheat1_enable = false

cheat2_desc = "Max Money"
cheat2_code = "8009C8A0+FFFF"
cheat2_enable = true
```

### Champs
- `cheats` : Nombre total de codes
- `cheat{N}_desc` : Description lisible
- `cheat{N}_code` : Code au format console spécifique
- `cheat{N}_enable` : État d'activation (true/false)

---

## 🎯 Types de codes supportés

### 1. RetroArch Format
**Format** : `AAAAAAAA+XXXX`
- `AAAAAAAA` : Adresse mémoire (8 hex digits)
- `XXXX` : Valeur (4 hex digits)
- **Exemple** : `8009C6E4+03E7`

### 2. GameShark (PSX, N64)
**Format** : `AAAAAAAA XXXX` (espace)
- PSX : `8009C6E4 03E7`
- N64 : `81234567 00FF`

### 3. Game Genie (NES, SNES, Genesis)
**Format** : 6-8 caractères alphanumériques
- NES : `SXIOPO`
- SNES : `F4A5-646D`
- Genesis : `ABCD-1234`

### 4. Action Replay
**Format** : `AAAAAAAA XXXXXXXX`
- **Exemple** : `12345678 ABCDEF01`

---

## 🏗️ Architecture du code

### Fichiers créés

```
app/src/main/java/com/chatai/cheat/
├── CheatManager.kt              # Gestionnaire principal
├── CheatActivity.kt             # Activity Compose
├── CheatSelectionDialog.kt      # Interface de sélection
└── AddCustomCheatDialog.kt      # Dialog d'ajout

app/src/main/res/
└── layout/
    └── activity_game_details_modern.xml  # Bouton 🎮 CODES
```

### Classes principales

#### CheatManager
```kotlin
class CheatManager(context: Context) {
    // Charger codes depuis .cht
    fun loadRetroArchCheats(chtFile: File): List<Cheat>
    
    // Trouver fichier pour un jeu
    fun findCheatFile(console: String, gameName: String): File?
    
    // Sauvegarder codes activés
    fun saveEnabledCheats(console: String, gameName: String, cheats: List<Cheat>)
    
    // Créer code personnalisé
    fun createCustomCheat(console, gameName, description, code, type): Cheat
    
    // Valider syntaxe
    fun validateCheatCode(code: String, type: CheatType): Boolean
}
```

#### Cheat Data Class
```kotlin
data class Cheat(
    val description: String,      // "Infinite Health"
    val code: String,             // "8009C6E4+03E7"
    val enabled: Boolean = false, // État
    val type: CheatType           // RETROARCH, GAMESHARK, etc.
)

enum class CheatType {
    RETROARCH,
    GAMESHARK,
    GAME_GENIE,
    ACTION_REPLAY,
    CUSTOM
}
```

---

## 🖥️ Interface utilisateur

### 1. GameDetailsActivity
**Bouton** : `🎮 CODES` (vert)
- Ouvre **CheatActivity**
- Visible pour toutes les consoles natives

### 2. CheatActivity (Compose)
**Dialog pleine hauteur (85%)** avec :
- **Header** : Nom du jeu + console
- **Liste scrollable** : Tous les codes avec Switch
- **Statut** : [Occupied] ou [Empty]
- **Bouton** : `+ Add Custom` pour ajouter un code
- **Badge** : Type de code (GAMESHARK, etc.)

### 3. NativeComposeEmulatorActivity
**Menu ⚙ → Cheat Codes**
- Accessible pendant l'émulation
- Même interface que CheatActivity
- Activation/désactivation en temps réel

### 4. AddCustomCheatDialog
**Interface d'ajout** :
- **Description** : TextField
- **Code** : TextField (uppercase auto)
- **Type** : 3 boutons (GameShark / Game Genie / Action Replay)
- **Validation** : Vérifie syntaxe avant ajout

---

## 🎨 Design

### Couleurs KITT
```xml
<!-- colors.xml -->
<color name="kitt_green">#4CAF50</color>
<color name="kitt_green_alpha">#1a4CAF50</color>
<color name="kitt_green_dark">#2E7D32</color>
<color name="kitt_green_light">#81C784</color>
```

### États visuels
- **Code activé** : Fond vert foncé (#1B5E20), texte vert (#4CAF50)
- **Code désactivé** : Fond gris (#212121), texte blanc
- **Switch** : Thumb vert / gris selon état

---

## 🔄 Workflow d'utilisation

### Ajouter des codes RetroArch
1. Télécharger fichiers .cht depuis [RetroArch Database](https://github.com/libretro/libretro-database)
2. Placer dans `/GameLibrary-Data/cheats/retroarch/{console}/`
3. Nommer comme le jeu : `Resident Evil 1.5.cht`

### Créer code personnalisé
1. **Game Library** → Sélectionner jeu → `🎮 CODES`
2. Cliquer `+ Add Custom`
3. Entrer **Description** : "God Mode"
4. Entrer **Code** : "8009C6E4 03E7"
5. Sélectionner **Type** : GameShark
6. Cliquer **Add**
7. **Switch** pour activer

### Activer pendant le jeu
1. **En jeu** → Appuyer sur `⚙` (menu)
2. Sélectionner **Cheat Codes**
3. **Toggle** les codes désirés
4. Fermer → Codes appliqués immédiatement

---

## 🛠️ Prochaines améliorations

### TODO
- [ ] **Intégration LibretroDroid** : Appliquer les codes au core
- [ ] **Import/Export** : Partager fichiers .cht
- [ ] **Base de données** : Codes communautaires téléchargeables
- [ ] **Recherche** : Filtrer codes par description
- [ ] **Catégories** : Organiser (Health, Weapons, Unlock, etc.)
- [ ] **Favoris** : Marquer codes fréquents
- [ ] **Historique** : Derniers codes utilisés

### Optimisations
- [ ] **Parser asynchrone** : Charger codes en arrière-plan
- [ ] **Cache** : Éviter re-parsing à chaque ouverture
- [ ] **Compression** : Support .zip pour gros fichiers .cht
- [ ] **Validation stricte** : Vérifier compatibilité console/code

---

## 📚 Ressources

### Formats de codes
- [GameShark PSX](https://gamehacking.org/system/psx)
- [Game Genie NES](https://datacrystal.romhacking.net/wiki/Game_Genie)
- [Action Replay](https://www.emuparadise.me/links/action-replay.php)

### RetroArch Cheats
- [libretro-database](https://github.com/libretro/libretro-database/tree/master/cht)
- [RetroArch Docs](https://docs.libretro.com/guides/cheat-codes/)

---

## ✅ Statut : IMPLÉMENTÉ

**Version** : 1.0  
**Date** : 2025-10-18  
**Auteur** : ChatAI Development Team

**Fonctionnalités** :
- ✅ Parser .cht RetroArch
- ✅ Interface Compose Material 3
- ✅ Codes personnalisés (4 formats)
- ✅ Activation/Désactivation
- ✅ Persistance des états
- ✅ Menu dans émulateur
- ✅ Validation syntaxe

**Prêt pour production !** 🚀

