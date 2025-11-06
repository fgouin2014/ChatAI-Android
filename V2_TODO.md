# 🚧 KITT V2 - TODO Liste

## ✅ TERMINÉ

- Architecture modulaire (6 managers séparés)
- `KittVoiceManager` : Reconnaissance vocale
- `KittTTSManager` : Text-to-Speech
- `KittAnimationManager` : Gestion des animations
- `KittAudioManager` : Musique de fond
- `KittCommandProcessor` : Traitement des commandes
- Fix thread UI (TTS callbacks)
- Integration avec MainActivity
- Quick Settings Tile support
- Commandes vocales (redémarre-toi, ouvre KITT, ouvre ChatAI)
- Menu drawer fonctionnel

## ❌ EN COURS / À FAIRE

### 1. Animations visuelles (LEDs)

**Problème :** Les LEDs scanner et VU-meter ne sont pas créées dynamiquement.

**Solution requise :**
```kotlin
private fun setupScanner() {
    val scannerRow = view.findViewById<LinearLayout>(R.id.scannerRow)
    
    // Créer 24 segments pour le scanner KITT
    for (i in 0 until 24) {
        val segment = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.kitt_segment_width),
                resources.getDimensionPixelSize(R.dimen.kitt_segment_height)
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.kitt_segment_margin)
            }
            setImageResource(R.drawable.kitt_scanner_segment_off)
        }
        scannerLeds.add(segment)
        scannerRow.addView(segment)
    }
}

private fun setupVuMeter() {
    val leftVuBar = view.findViewById<LinearLayout>(R.id.leftVuBarMenu)
    val centerVuBar = view.findViewById<LinearLayout>(R.id.centerVuBarMenu)
    val rightVuBar = view.findViewById<LinearLayout>(R.id.rightVuBarMenu)
    
    setupVuBar(leftVuBar)
    setupVuBar(centerVuBar)
    setupVuBar(rightVuBar)
}

private fun setupVuBar(bar: LinearLayout) {
    for (i in 0 until 20) {
        val led = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.vu_led_width),
                resources.getDimensionPixelSize(R.dimen.vu_led_height)
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.vu_led_margin)
            }
            setImageResource(R.drawable.vu_led_off)
        }
        vuLeds.add(led)
        bar.addView(led)
    }
}
```

Appeler ces méthodes dans `initializeViews()` AVANT d'initialiser `KittAnimationManager`.

### 2. Drawables manquants

Vérifier que ces drawables existent :
- `R.drawable.kitt_scanner_segment_off`
- `R.drawable.kitt_scanner_segment_max`
- `R.drawable.kitt_scanner_segment_high`
- `R.drawable.kitt_scanner_segment_medium`
- `R.drawable.vu_led_off`
- `R.drawable.vu_led_on`

### 3. Dimensions manquantes

Vérifier dans `res/values/dimens.xml` :
- `kitt_segment_width`
- `kitt_segment_height`
- `kitt_segment_margin`
- `vu_led_width`
- `vu_led_height`
- `vu_led_margin`

## 📊 ÉTAT ACTUEL

**V2 fonctionne SANS les animations visuelles** :
- ✅ Reconnaissance vocale : OK
- ✅ TTS : OK  
- ✅ Commandes IA : OK
- ✅ Menu drawer : OK
- ✅ Quick Settings Tile : OK
- ❌ Scanner LED : Pas visible (0/24 LEDs)
- ❌ VU-meter LED : Pas visible (0/60 LEDs)

## 🔄 Prochaines étapes

1. **Option A** : Implémenter la création dynamique des LEDs (1-2h de travail)
2. **Option B** : Garder V1 comme version principale, V2 comme alternative sans animations
3. **Option C** : Créer un layout séparé pour V2 avec les LEDs en XML

## 💡 Recommandation

**Continuer avec V1 pour l'instant** car il est fonctionnel à 100%.

V2 peut être finalisé plus tard quand les animations seront implémentées.

---

**Version:** 4.0.4  
**Date:** 2025-11-04  
**Status:** V2 partiellement fonctionnel (pas d'animations visuelles)

