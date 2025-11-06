# ✅ GENESIS - APPROCHE SIMPLE

**Date:** 2025-10-19  
**Status:** ✅ Code simplifié, tests requis

---

## 🎯 DÉCISION

**Approche simple adoptée : Laisser le core gérer les .zip**

- ❌ Pas de système de cache (causait ANR)
- ❌ Pas d'extraction automatique (bloquait UI)
- ✅ Code simple : Passer ROM directement au core
- ✅ Le core décide s'il peut lire .zip ou non

---

## 🔍 CE QUE NOUS SAVONS

### Tests effectués

| Format | Test | Résultat |
|--------|------|----------|
| `.bin` | 3 Ninjas Kick Back.bin | ✅ **FONCTIONNE** |
| `.zip` | Test initial (avant extraction) | ❌ Écran noir |
| `.zip` | Après corrections | ❓ **À TESTER** |

### Hypothèses

**Hypothèse 1 :** Genesis Plus GX ne supporte PAS les .zip
- Les .bin sont nécessaires
- Garder les 430 .bin extraits

**Hypothèse 2 :** Genesis Plus GX SUPPORTE les .zip
- Le problème initial était un bug (cache, compilation, etc.)
- Peut supprimer les .bin, garder seulement .zip

**Lemuroid fonctionne avec .zip → Suggère hypothèse 2**

---

## 🧪 TEST À EFFECTUER

### Pour confirmer quelle hypothèse est vraie

**Testez un jeu qui a SEULEMENT le .zip (pas de .bin) :**

Jeux disponibles sans .bin :
```bash
# Vérifier quels jeux n'ont PAS de .bin
adb shell "cd '/storage/emulated/0/GameLibrary-Data/megadrive' && \
  for f in *.zip; do \
    binName=\${f%.zip}.bin; \
    if [ ! -f \"\$binName\" ]; then \
      echo \"$f (PAS de .bin)\"; \
    fi; \
  done | head -10"
```

Exemples possibles :
- AAAHH!!! Real Monsters (USA).zip
- ATP Tour Championship Tennis (USA).zip
- AWS Pro Moves Soccer (USA).zip

---

### Résultats attendus

#### Si le jeu .zip fonctionne (image + émulation)

**→ Genesis Plus GX SUPPORTE les .zip !**

Actions :
- ✅ Supprimer les 430 .bin (libérer 1.3 GB)
- ✅ Garder seulement les .zip
- ✅ Code actuel parfait

---

#### Si le jeu .zip NE fonctionne PAS (écran noir)

**→ Genesis Plus GX ne supporte PAS les .zip**

Actions :
- ✅ Garder les .bin extraits
- ✅ Supprimer les .zip (optionnel, économiser 700 MB)
- ❓ Ou garder les deux (backup)

---

## 📊 COMPARAISON DES OPTIONS

### OPTION A : .zip fonctionnent

```
megadrive/
├── Sonic.zip (500 KB) ✅
├── Streets of Rage 2.zip (800 KB) ✅
└── ...

Total: 700 MB (.zip seulement)
```

**Avantages :**
- ✅ Gain espace : 700 MB vs 2.1 GB
- ✅ Fichiers compressés
- ✅ Simple à gérer

---

### OPTION B : .bin nécessaires

```
megadrive/
├── Sonic.bin (2 MB) ✅
├── Streets of Rage 2.bin (3 MB) ✅
└── ...

Total: 1.4 GB (.bin seulement)
```

**Avantages :**
- ✅ Accès direct (pas de compression)
- ✅ Chargement rapide
- ✅ Compatible 100%

---

### OPTION C : Garder les deux

```
megadrive/
├── Sonic.zip (500 KB) ✅ Backup
├── Sonic.bin (2 MB) ✅ Utilisé
└── ...

Total: 2.1 GB (.zip + .bin)
```

**Avantages :**
- ✅ Backup compressé (.zip)
- ✅ Accès rapide (.bin)

**Inconvénients :**
- ❌ Duplication
- ❌ 2.1 GB au lieu de 700 MB ou 1.4 GB

---

## 💾 SITUATION ACTUELLE

### Fichiers présents

```
megadrive/
├── 758 fichiers .zip (700 MB)
├── 430 fichiers .bin (1.4 GB)
└── Total: 2.1 GB
```

**Décision à prendre :**
- Si .zip fonctionnent → Supprimer les .bin
- Si .zip ne fonctionnent pas → Supprimer les .zip

---

## 🎯 RECOMMANDATION

**Testez un jeu .zip SANS .bin pour savoir :**

1. Supprimez le .bin d'un jeu (ex: 3 Ninjas Kick Back.bin)
2. Gardez seulement le .zip
3. Testez le jeu
4. Observez :
   - ✅ Fonctionne → .zip OK pour tous
   - ❌ Écran noir → .bin nécessaires

---

## 📝 CODE ACTUEL

### GameDetailsActivity.launchGameNative()

```java
private void launchGameNative(int slot) {
    String fileName = game.getFile();
    String romPath = "/storage/emulated/0/GameLibrary-Data/" + game.getConsole() + "/" + fileName;
    
    // Passer directement au core (pas d'extraction)
    Intent intent = new Intent(this, NativeComposeEmulatorActivity.class);
    intent.putExtra("romPath", romPath);  // Peut être .zip ou .bin
    intent.putExtra("gameName", game.getName());
    intent.putExtra("console", game.getConsole());
    
    startActivity(intent);
}
```

**Simple et direct !**

---

## ✅ PROCHAINE ÉTAPE

**TESTEZ UN JEU .ZIP (sans .bin) pour déterminer la meilleure approche.**

Une fois confirmé, nous saurons quelle option adopter (A, B, ou C).

---

**En attente de votre test...** 🧪


