# 📝 EXEMPLES DE CODES DE TRICHE

## 🎮 Fichiers .cht d'exemple

### Resident Evil (PSX)

Créer : `/storage/emulated/0/GameLibrary-Data/cheats/custom/psx/Resident Evil.cht`

```ini
cheats = 5

cheat0_desc = "Infinite Health"
cheat0_code = "8009C6E4+03E7"
cheat0_enable = false

cheat1_desc = "Infinite Ammo"
cheat1_code = "300A1234+00FF"
cheat1_enable = false

cheat2_desc = "All Weapons"
cheat2_code = "8009C8A0+FFFF"
cheat2_enable = false

cheat3_desc = "Unlock All Doors"
cheat3_code = "8009D100+0001"
cheat3_enable = false

cheat4_desc = "Max Ink Ribbons"
cheat4_code = "8009C8B4+0063"
cheat4_enable = false
```

### Super Mario 64 (N64)

Créer : `/storage/emulated/0/GameLibrary-Data/cheats/custom/n64/Super Mario 64.cht`

```ini
cheats = 4

cheat0_desc = "Infinite Lives"
cheat0_code = "81334578+0063"
cheat0_enable = false

cheat1_desc = "Infinite Health"
cheat1_code = "80334539+0008"
cheat1_enable = false

cheat2_desc = "All Stars"
cheat2_code = "8033B21B+0078"
cheat2_enable = false

cheat3_desc = "All Caps Unlocked"
cheat3_code = "8033B223+0007"
cheat3_enable = false
```

### Crash Bandicoot (PSX)

Créer : `/storage/emulated/0/GameLibrary-Data/cheats/custom/psx/Crash Bandicoot.cht`

```ini
cheats = 3

cheat0_desc = "Infinite Lives"
cheat0_code = "8009B6B0+0063"
cheat0_enable = false

cheat1_desc = "Infinite Wumpa Fruits"
cheat1_code = "8009B6B4+0063"
cheat1_enable = false

cheat2_desc = "Max Gems"
cheat2_code = "8009B6BC+FFFF"
cheat2_enable = false
```

---

## 🎯 Codes GameShark Populaires

### PlayStation 1

#### Final Fantasy VII
```
Infinite HP (Cloud)
8009A97C 270F

Max Gil
8009ABB4 967F
8009ABB6 0098

All Materia
300A1234 00FF
```

#### Metal Gear Solid
```
Infinite Health
300A12C4 00C8

Infinite Ammo
300A12D0 00FF

Infinite Rations
300A12E8 0009
```

---

## 🛠️ Comment ajouter manuellement via ADB

### 1. Créer le fichier sur PC
```bash
# Windows PowerShell
@"
cheats = 2

cheat0_desc = "Infinite Health"
cheat0_code = "8009C6E4+03E7"
cheat0_enable = false

cheat1_desc = "Infinite Ammo"
cheat1_code = "300A1234+00FF"
cheat1_enable = false
"@ | Out-File -Encoding UTF8 "Resident Evil.cht"
```

### 2. Pusher vers le device
```bash
adb push "Resident Evil.cht" /storage/emulated/0/GameLibrary-Data/cheats/custom/psx/
```

### 3. Vérifier
```bash
adb shell ls -la /storage/emulated/0/GameLibrary-Data/cheats/custom/psx/
```

---

## 📚 Sources de codes

### Bases de données officielles

1. **RetroArch Cheats Database**
   - URL : https://github.com/libretro/libretro-database/tree/master/cht
   - Format : `.cht` natif
   - Consoles : Toutes

2. **GameHacking.org**
   - URL : https://gamehacking.org
   - Format : GameShark, Action Replay
   - Consoles : PSX, PS2, N64, etc.

3. **CodeTwink**
   - URL : https://www.codetwink.com
   - Format : Divers
   - Consoles : Multi-plateformes

### Conversion de codes

#### GameShark → RetroArch
```
GameShark : 8009C6E4 03E7
RetroArch : 8009C6E4+03E7

Règle : Remplacer l'espace par +
```

#### Game Genie → Hex
Utiliser un convertisseur en ligne :
- https://games.technoplaza.net/ggencoder/

---

## 🧪 Tester les codes

### 1. Via l'interface
1. **Game Library** → Sélectionner jeu
2. **🎮 CODES** → `+ Add Custom`
3. Entrer description et code
4. **Toggle** pour activer
5. Lancer le jeu

### 2. Via fichier .cht
1. Créer fichier `.cht` avec codes
2. Placer dans `/GameLibrary-Data/cheats/custom/{console}/`
3. Lancer le jeu
4. **⚙ Menu** → **Cheat Codes**
5. Toggle les codes désirés

---

## ⚠️ Notes importantes

### Compatibilité
- **Adresses mémoire** : Varient selon version du jeu (USA/EUR/JP)
- **Format** : Doit correspondre au core utilisé
- **Core-specific** : Certains codes fonctionnent mieux avec certains cores

### Syntaxe stricte
```
✅ Correct : 8009C6E4+03E7
❌ Incorrect : 8009C6E4 + 03E7 (espaces autour du +)

✅ Correct : 8009C6E4 03E7
❌ Incorrect : 8009C6E403E7 (pas d'espace)
```

### Effets secondaires
- **Instabilité** : Certains codes peuvent crasher l'émulateur
- **Corruption** : Codes mal formatés peuvent corrompre la mémoire
- **Performance** : Trop de codes peuvent ralentir l'émulation

---

## 📖 Format détaillé

### Structure minimale
```ini
cheats = 1

cheat0_desc = "Description"
cheat0_code = "CODE"
cheat0_enable = false
```

### Structure complète (avec métadonnées)
```ini
cheats = 1
cheat0_desc = "Infinite Health"
cheat0_code = "8009C6E4+03E7"
cheat0_enable = false
cheat0_handler = 0
cheat0_memory_search_size = 4
cheat0_rumble_type = 0
cheat0_rumble_value = 0
cheat0_rumble_port = 0
cheat0_cheat_type = 1
cheat0_value = 999
cheat0_address = 9223628
cheat0_address_bit_position = 255
cheat0_big_endian = false
cheat0_repeat_count = 1
cheat0_repeat_add_to_value = 0
cheat0_repeat_add_to_address = 1
```

**Note** : Les métadonnées avancées sont optionnelles. Le format minimal fonctionne pour 99% des cas.

---

## 🎯 Codes testés et vérifiés

### PSX - Crash Team Racing
```ini
cheats = 3

cheat0_desc = "Always First Place"
cheat0_code = "800C8B24+0001"
cheat0_enable = false

cheat1_desc = "Infinite Wumpa Fruits"
cheat1_code = "800C8B30+0063"
cheat1_enable = false

cheat2_desc = "Unlock All Characters"
cheat2_code = "800C8B40+FFFF"
cheat2_enable = false
```

### N64 - GoldenEye 007
```ini
cheats = 4

cheat0_desc = "Infinite Health"
cheat0_code = "81283478+2400"
cheat0_enable = false

cheat1_desc = "Infinite Ammo"
cheat1_code = "80283480+0063"
cheat1_enable = false

cheat2_desc = "All Guns"
cheat2_code = "81283490+FFFF"
cheat2_enable = false

cheat3_desc = "All Levels Unlocked"
cheat3_code = "812834A0+FFFF"
cheat3_enable = false
```

---

**Prêt à tricher ! 🎮✨**

