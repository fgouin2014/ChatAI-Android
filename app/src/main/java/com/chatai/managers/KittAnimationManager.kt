package com.chatai.managers

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.chatai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

/**
 * 🎬 KITT ANIMATION MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUTES les animations de KITT:
 * - Scanner KITT (24 LEDs avec dégradé 5 segments)
 * - VU-meter (60 LEDs, modes ORIGINAL/DUAL, amplification)
 * - Thinking Animation (BSY/NET clignotent)
 * - Button Animations (activation, scan, glow)
 * 
 * RESPONSABILITÉS:
 * 1. Créer les LEDs scanner et VU-meter
 * 2. Animer le scanner avec effet balayage
 * 3. Animer le VU-meter selon le niveau audio
 * 4. Animer les indicateurs BSY/NET pendant thinking
 * 5. Animer les boutons lors de l'activation
 * 
 * RÈGLES ABSOLUES:
 * - TOUT le code est copié de V1 sans modification
 * - Les 3 ondes sinusoïdales sont ESSENTIELLES
 * - L'amplification × 1.8 est CRITIQUE
 * - Les colonnes à 70% sont NÉCESSAIRES
 * - Les couleurs par position DOIVENT être respectées
 */

/**
 * Modes du VU-meter (COPIÉ DE V1)
 */
enum class VUMeterMode {
    OFF,        // VU-meter éteint
    VOICE,      // Mode voix TTS
    AMBIENT     // Mode sons ambiants
}

/**
 * Modes d'animation du VU-meter (COPIÉ DE V1)
 */
enum class VUAnimationMode {
    ORIGINAL,   // Animation originale : du milieu vers les extrémités
    DUAL        // Animation dual : des extrémités vers le centre
}

class KittAnimationManager(
    private val context: Context,
    private val resources: Resources
) {
    
    companion object {
        private const val TAG = "KittAnimationManager"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES SCANNER KITT (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private val kittSegments = mutableListOf<ImageView>()
    private var kittPosition = 0
    private var kittDirection = 1
    private var scannerAnimation: Runnable? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES VU-METER (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private val vuLeds = mutableListOf<ImageView>()
    private var vuMeterAnimation: Runnable? = null
    var vuMeterMode = VUMeterMode.VOICE  // Mode par défaut
    var vuAnimationMode = VUAnimationMode.ORIGINAL  // Mode d'animation par défaut
    var currentMicrophoneLevel = -30f  // Niveau RMS microphone
    var isTTSSpeaking = false  // État TTS (mis à jour par TTS Manager)
    
    // Volume système pour animation TTS
    var currentVolume = 0f
    var maxVolume = 0f
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES THINKING ANIMATION (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private var thinkingAnimationBSY: Runnable? = null
    private var thinkingAnimationNET: Runnable? = null
    private var statusBarIndicatorBSY: MaterialTextView? = null
    private var statusBarIndicatorRDY: MaterialTextView? = null
    private var statusBarIndicatorNET: MaterialTextView? = null
    
    // Animation volume système
    private var systemVolumeAnimation: Runnable? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION SCANNER (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Créer 24 segments pour le scanner KITT
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setupScanner(scannerRow: LinearLayout) {
        kittSegments.clear()
        
        // Créer 24 segments pour le scanner KITT
        for (i in 0 until 24) {
            val segment = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.kitt_segment_width),
                    resources.getDimensionPixelSize(R.dimen.kitt_segment_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.kitt_segment_margin)
                }
                setImageResource(R.drawable.kitt_scanner_segment_off)
            }
            kittSegments.add(segment)
            scannerRow.addView(segment)
        }
        
        android.util.Log.d(TAG, "✅ Scanner created: ${kittSegments.size}/24 segments")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION VU-METER (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Créer 20 LEDs pour chaque barre VU (60 LEDs total)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setupVuMeter(leftVuBar: LinearLayout, centerVuBar: LinearLayout, rightVuBar: LinearLayout) {
        vuLeds.clear()
        
        // Créer 20 LEDs pour chaque barre VU
        setupVuBar(leftVuBar)
        setupVuBar(centerVuBar)
        setupVuBar(rightVuBar)
        
        android.util.Log.d(TAG, "✅ VU-meter created: ${vuLeds.size}/60 LEDs")
    }
    
    /**
     * Créer une barre VU (20 LEDs)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    private fun setupVuBar(bar: LinearLayout) {
        for (i in 0 until 20) {
            val led = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.vu_led_width),
                    resources.getDimensionPixelSize(R.dimen.vu_led_height)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.vu_led_margin)
                }
                setImageResource(R.drawable.kitt_vu_led_off)
            }
            vuLeds.add(led)
            bar.addView(led)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION THINKING INDICATORS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialiser les indicateurs BSY/NET pour thinking animation
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setupThinkingIndicators(
        bsy: MaterialTextView,
        rdy: MaterialTextView,
        net: MaterialTextView
    ) {
        statusBarIndicatorBSY = bsy
        statusBarIndicatorRDY = rdy
        statusBarIndicatorNET = net
        android.util.Log.d(TAG, "✅ Thinking indicators initialized")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ANIMATION SCANNER KITT (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Démarrer l'animation du scanner KITT
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun startScannerAnimation(speed: Long) {
        stopScannerAnimation()
        
        scannerAnimation = object : Runnable {
            override fun run() {
                updateScanner()
                mainHandler.postDelayed(this, speed)
            }
        }
        mainHandler.post(scannerAnimation!!)
    }
    
    /**
     * Arrêter l'animation du scanner
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopScannerAnimation() {
        scannerAnimation?.let { mainHandler.removeCallbacks(it) }
        scannerAnimation = null
    }
    
    /**
     * ⭐⭐⭐ FONCTION CRITIQUE - Mettre à jour le scanner KITT
     * 
     * Créer l'effet de balayage avec dégradé de luminosité (5 segments):
     * Position -2: segment_medium (faible)
     * Position -1: segment_high   (haute)
     * Position  0: segment_max    (maximale) ← Centre
     * Position +1: segment_high   (haute)
     * Position +2: segment_medium (faible)
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS SIMPLIFIER ⚠️⚠️⚠️
     */
    private fun updateScanner() {
        // Éteindre tous les segments
        kittSegments.forEach { segment ->
            segment.setImageResource(R.drawable.kitt_scanner_segment_off)
        }
        
        // Créer l'effet de balayage avec dégradé de luminosité
        for (i in -2..2) {
            val index = kittPosition + i
            if (index in 0 until kittSegments.size) {
                val segment = kittSegments[index]
                when (i) {
                    0 -> segment.setImageResource(R.drawable.kitt_scanner_segment_max)
                    1, -1 -> segment.setImageResource(R.drawable.kitt_scanner_segment_high)
                    2, -2 -> segment.setImageResource(R.drawable.kitt_scanner_segment_medium)
                }
            }
        }
        
        // Mouvement avec rebond
        kittPosition += kittDirection
        
        if (kittPosition >= kittSegments.size - 1) {
            kittDirection = -1
        } else if (kittPosition <= 0) {
            kittDirection = 1
        }
    }
    
    /**
     * Reset scanner (segments centraux légèrement allumés)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun resetScanner() {
        stopScannerAnimation()
        kittSegments.forEachIndexed { index, segment ->
            segment.setImageResource(R.drawable.kitt_scanner_segment_off)
            // Segments centraux légèrement allumés par défaut
            if (index in 10..13) {
                segment.setImageResource(R.drawable.kitt_scanner_segment_low)
            }
        }
        kittPosition = 0
        kittDirection = 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ANIMATION VU-METER (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Démarrer l'animation du VU-meter
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun startVuMeterAnimation() {
        stopVuMeterAnimation()
        
        // Debug : Afficher le mode VU-meter
        android.util.Log.d("VUMeter", "startVuMeterAnimation called, mode: $vuMeterMode")
        
        // Si le mode est OFF, ne pas démarrer l'animation
        if (vuMeterMode == VUMeterMode.OFF) {
            android.util.Log.d("VUMeter", "Mode OFF, resetting VU-meter")
            resetVuMeter()
            return
        }
        
        // Si le mode est VOICE et que TTS ne parle pas, ne pas démarrer l'animation
        if (vuMeterMode == VUMeterMode.VOICE && !isTTSSpeaking) {
            android.util.Log.d("VUMeter", "Mode VOICE but TTS not speaking, resetting VU-meter")
            resetVuMeter()
            return
        }
        
        vuMeterAnimation = object : Runnable {
            override fun run() {
                when (vuMeterMode) {
                    VUMeterMode.OFF -> {
                        // Mode OFF - éteindre toutes les LEDs
                        resetVuMeter()
                        return // Ne pas programmer la prochaine exécution
                    }
                    VUMeterMode.VOICE -> {
                        if (isTTSSpeaking) {
                            // Animation TTS basée sur le volume système Android
                            updateVuMeterFromSystemVolume()
                            // Programmer la prochaine exécution seulement si TTS parle
                            mainHandler.postDelayed(this, 60)
                        } else {
                            // Mode VOICE : Éteindre complètement quand TTS ne parle pas
                            android.util.Log.d("VUMeter", "VOICE mode but TTS not speaking, resetting VU-meter")
                            resetVuMeter()
                            return // Arrêter l'animation complètement
                        }
                    }
                    VUMeterMode.AMBIENT -> {
                        // Mode AMBIENT : Utiliser le microphone pour les sons environnants
                        android.util.Log.d("VUMeter", "AMBIENT mode, microphone level: $currentMicrophoneLevel")
                        if (currentMicrophoneLevel > -20f) { // Seuil de sensibilité
                            val normalizedLevel = (currentMicrophoneLevel + 20f) / 20f
                            val clampedLevel = normalizedLevel.coerceIn(0f, 1f)
                            android.util.Log.d("VUMeter", "AMBIENT: normalized=$normalizedLevel, clamped=$clampedLevel")
                            updateVuMeter(clampedLevel)
                        } else {
                            // Niveau très faible si pas de son
                            android.util.Log.d("VUMeter", "AMBIENT: level too low, using 0.05f")
                            updateVuMeter(0.05f)
                        }
                    }
                }
                // Programmer la prochaine exécution seulement pour les modes qui en ont besoin
                when (vuMeterMode) {
                    VUMeterMode.AMBIENT -> {
                        mainHandler.postDelayed(this, 80) // Fréquence plus rapide pour plus de réactivité
                    }
                    VUMeterMode.VOICE -> {
                        // En mode VOICE, la prochaine exécution est gérée dans le bloc VOICE
                        // Ne pas programmer ici pour éviter les conflits
                    }
                    VUMeterMode.OFF -> {
                        // Mode OFF - pas de prochaine exécution
                    }
                }
            }
        }
        mainHandler.post(vuMeterAnimation!!)
    }
    
    /**
     * ⭐⭐⭐ FONCTION TRÈS CRITIQUE - Animer VU-meter basé sur volume système
     * 
     * Simulation réaliste du TTS avec variations temporelles.
     * Utilise 3 ONDES SINUSOÏDALES + variation aléatoire pour effet naturel.
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS MODIFIER LES 3 ONDES ⚠️⚠️⚠️
     */
    fun updateVuMeterFromSystemVolume() {
        // Simulation réaliste du TTS avec variations temporelles
        val normalizedVolume = if (maxVolume > 0f) currentVolume / maxVolume else 0f
        
        // Créer des variations plus réalistes basées sur le temps
        val time = System.currentTimeMillis() * 0.01
        val baseLevel = normalizedVolume * 0.5f
        
        // ⚠️ COMBINAISON DE 3 ONDES - NE PAS SIMPLIFIER
        val wave1 = (Math.sin(time) * 0.3f).toFloat()
        val wave2 = (Math.sin(time * 1.7) * 0.2f).toFloat()
        val wave3 = (Math.sin(time * 0.5) * 0.15f).toFloat()
        val randomVariation = (Math.random() * 0.2 - 0.1).toFloat()
        
        val ttsLevel = (baseLevel + wave1 + wave2 + wave3 + randomVariation).coerceIn(0.1f, 0.95f)
        
        updateVuMeter(ttsLevel)
    }
    
    /**
     * Reset VU-meter au niveau de base
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun resetVuMeterToBase() {
        // Remettre le VU-meter au niveau de base quand TTS s'arrête
        when (vuMeterMode) {
            VUMeterMode.OFF -> {
                resetVuMeter() // Éteindre complètement
            }
            VUMeterMode.VOICE -> {
                resetVuMeter() // Éteindre complètement en mode VOICE quand TTS s'arrête
            }
            VUMeterMode.AMBIENT -> {
                updateVuMeter(0.05f) // Niveau très faible pour AMBIENT
            }
        }
    }
    
    /**
     * Démarrer animation volume système
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun startSystemVolumeAnimation() {
        stopSystemVolumeAnimation()
        
        systemVolumeAnimation = object : Runnable {
            override fun run() {
                if (isTTSSpeaking && vuMeterMode == VUMeterMode.VOICE) {
                    updateVuMeterFromSystemVolume()
                    mainHandler.postDelayed(this, 60) // Animation très rapide pendant TTS
                } else {
                    // Si TTS ne parle pas ou mode changé, arrêter l'animation
                    android.util.Log.d("VUMeter", "Stopping system volume animation - TTS: $isTTSSpeaking, Mode: $vuMeterMode")
                }
            }
        }
        mainHandler.post(systemVolumeAnimation!!)
    }
    
    /**
     * Arrêter animation volume système
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopSystemVolumeAnimation() {
        systemVolumeAnimation?.let { mainHandler.removeCallbacks(it) }
        systemVolumeAnimation = null
    }
    
    /**
     * Arrêter l'animation du VU-meter
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopVuMeterAnimation() {
        vuMeterAnimation?.let { mainHandler.removeCallbacks(it) }
        vuMeterAnimation = null
    }
    
    /**
     * ⭐⭐⭐ FONCTION ULTRA-CRITIQUE - Mettre à jour le VU-meter
     * 
     * C'EST LA FONCTION LA PLUS COMPLEXE DE TOUT LE PROJET (160+ lignes).
     * 
     * LOGIQUE COMPLEXE:
     * 1. Validation niveau (< 0.05f = éteindre)
     * 2. Amplification signal: sqrt(level) × 1.8  ⚠️ NE PAS MODIFIER
     * 3. Éteindre toutes les LEDs
     * 4. Gérer 3 colonnes (60 LEDs = 3×20)
     * 5. Colonnes latérales: 70% du niveau central  ⚠️ NE PAS MODIFIER
     * 6. Mode ORIGINAL: Du milieu (9/10) vers haut ET bas
     * 7. Mode DUAL: Des extrémités vers le centre
     * 8. Couleurs par position:
     *    - Latérales: Ambre (0-5, 14-19), Rouge (6-13)
     *    - Centrale: Rouge partout
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS SIMPLIFIER ⚠️⚠️⚠️
     */
    fun updateVuMeter(level: Float = 0.3f) {
        // Debug : Afficher le niveau reçu
        android.util.Log.d("VUMeter", "updateVuMeter called with level: $level")
        
        // Debug : Vérifier les LEDs VU
        android.util.Log.d("VUMeter", "Total VU LEDs: ${vuLeds.size}")
        
        // Si le niveau est très faible, éteindre complètement
        if (level < 0.05f) {
            android.util.Log.d("VUMeter", "Level too low, turning off LEDs")
            vuLeds.forEach { led ->
                led.setImageResource(R.drawable.kitt_vu_led_off)
            }
            return
        }
        
        // ⚠️ Améliorer la sensibilité - amplification du signal
        val amplifiedLevel = kotlin.math.sqrt(level.toDouble()).toFloat() // Racine carrée pour plus de sensibilité
        val enhancedLevel = (amplifiedLevel * 1.8f).coerceIn(0f, 1f) // Amplification x1.8
        
        // COMMENCER AVEC TOUTES LES LEDs ÉTEINTES (NOIRES)
        vuLeds.forEach { led ->
            led.setImageResource(R.drawable.kitt_vu_led_off)
        }
        
        // Gérer les LEDs par colonnes verticales (3 colonnes)
        val totalColumns = 3
        val ledsPerColumn = vuLeds.size / totalColumns // 20 LEDs par colonne
        
        // ⚠️ Colonnes latérales synchronisées (même niveau)
        val leftRightLevel = enhancedLevel * 0.7f // Colonnes latérales à 70% du niveau central
        val centerLevel = enhancedLevel
        
        // Traiter chaque colonne verticale
        for (columnIndex in 0 until totalColumns) {
            // Utiliser le niveau approprié selon la colonne
            val adjustedLevel = when (columnIndex) {
                0, 2 -> leftRightLevel // Colonnes latérales synchronisées
                1 -> centerLevel       // Colonne centrale
                else -> enhancedLevel
            }
            
            // Calculer combien de LEDs allumer selon le niveau (de bas en haut)
            val ledsToTurnOn = (adjustedLevel * ledsPerColumn).toInt().coerceAtMost(ledsPerColumn)
            
            // Debug : Afficher le calcul
            android.util.Log.d("VUMeter", "Column $columnIndex: adjustedLevel=$adjustedLevel, ledsToTurnOn=$ledsToTurnOn")
            
            // Choisir le mode d'animation selon vuAnimationMode
            when (vuAnimationMode) {
                VUAnimationMode.ORIGINAL -> {
                    // ⚠️ Animation originale : du milieu (9) vers le bas (0) ET du milieu (10) vers le haut (19)
                    // Pour 20 LEDs (0-19), le milieu est entre 9 et 10
                    val bottomLeds = ledsToTurnOn / 2  // Moins de LEDs en bas
                    val topLeds = ledsToTurnOn - bottomLeds   // Plus de LEDs en haut pour couvrir 16,17
                    
                    // Allumer du milieu vers le bas (9,8,7,6,5,4,3,2,1,0)
                    for (i in 0 until bottomLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + (9 - i) // 9 vers 0
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = 9 - i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_warning // Ambre aux extrémités
                                        else -> R.drawable.kitt_vu_led_active // Rouge pour les autres positions
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - ORIGINAL BOTTOM")
                        }
                    }
                    
                    // Allumer du milieu vers le haut (10,11,12,13,14,15,16,17,18,19)
                    for (i in 0 until topLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + (10 + i) // 10 vers 19
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = 10 + i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_warning // Ambre aux extrémités
                                        else -> R.drawable.kitt_vu_led_active // Rouge pour les autres positions
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - ORIGINAL TOP")
                        }
                    }
                }
                VUAnimationMode.DUAL -> {
                    // ⚠️ Animation dual : en-haut et en-bas vers le centre
                    val halfLeds = maxOf(1, ledsToTurnOn / 2)  // Minimum 1, maximum la moitié
                    val remainingLeds = ledsToTurnOn - halfLeds  // Le reste au centre
                    
                    // Allumer de bas en haut (première moitié)
                    for (i in 0 until halfLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + i
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales - COULEURS INVERSÉES
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_active // Rouge aux extrémités
                                        else -> R.drawable.kitt_vu_led_warning // Ambre au centre
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - BOTTOM")
                        }
                    }
                    
                    // Allumer de haut en bas (deuxième moitié)
                    for (i in 0 until remainingLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + (ledsPerColumn - 1 - i)
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = ledsPerColumn - 1 - i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales - COULEURS INVERSÉES
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_active // Rouge aux extrémités
                                        else -> R.drawable.kitt_vu_led_warning // Ambre au centre
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - TOP")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Reset VU-meter (éteindre toutes les LEDs)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun resetVuMeter() {
        stopVuMeterAnimation()
        vuLeds.forEach { led ->
            led.setImageResource(R.drawable.kitt_vu_led_off)
        }
    }
    
    /**
     * Toggle VU-meter mode (VOICE → AMBIENT → OFF)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     * 
     * NOTE: Cette fonction doit être appelée par KittFragment
     * car elle nécessite accès au microphone (startMicrophoneListening/stopMicrophoneListening)
     */
    fun toggleVUMeterMode(): VUMeterMode {
        vuMeterMode = when (vuMeterMode) {
            VUMeterMode.VOICE -> VUMeterMode.AMBIENT
            VUMeterMode.AMBIENT -> VUMeterMode.OFF
            VUMeterMode.OFF -> VUMeterMode.VOICE
        }
        
        // Redémarrer l'animation selon le nouveau mode
        stopVuMeterAnimation()
        startVuMeterAnimation()
        
        return vuMeterMode
    }
    
    /**
     * Toggle VU animation mode (ORIGINAL ↔ DUAL)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun toggleVUAnimationMode(): VUAnimationMode {
        vuAnimationMode = when (vuAnimationMode) {
            VUAnimationMode.ORIGINAL -> VUAnimationMode.DUAL
            VUAnimationMode.DUAL -> VUAnimationMode.ORIGINAL
        }
        
        // Redémarrer l'animation selon le nouveau mode
        stopVuMeterAnimation()
        startVuMeterAnimation()
        
        return vuAnimationMode
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // THINKING ANIMATION (BSY/NET) (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐ FONCTION CRITIQUE - Animer BSY et NET pendant thinking
     * 
     * BSY clignote à 250ms (rapide)
     * NET clignote à 500ms (lent)
     * Vitesses différentes créent l'effet asynchrone
     * 
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER LES VITESSES
     */
    fun startThinkingAnimation() {
        // Arrêter les animations existantes si nécessaire
        stopThinkingAnimation()
        
        val bsy = statusBarIndicatorBSY ?: return
        val rdy = statusBarIndicatorRDY ?: return
        val net = statusBarIndicatorNET ?: return
        
        // Animation BSY (rapide - 250ms)
        var bsyState = false
        thinkingAnimationBSY = object : Runnable {
            override fun run() {
                bsyState = !bsyState
                if (bsyState) {
                    // Allumé
                    bsy.setBackgroundResource(R.drawable.kitt_status_background_active)
                    bsy.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
                } else {
                    // Semi-allumé (pour effet clignotant)
                    bsy.setBackgroundResource(R.drawable.kitt_status_background)
                    bsy.setTextColor(ContextCompat.getColor(context, R.color.kitt_red))
                }
                
                mainHandler.postDelayed(this, 250) // 250ms (rapide)
            }
        }
        mainHandler.post(thinkingAnimationBSY!!)
        
        // Animation NET (lent - 500ms)
        var netState = false
        thinkingAnimationNET = object : Runnable {
            override fun run() {
                netState = !netState
                if (netState) {
                    // Allumé
                    net.setBackgroundResource(R.drawable.kitt_status_background_active)
                    net.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
                } else {
                    // Semi-allumé (pour effet clignotant)
                    net.setBackgroundResource(R.drawable.kitt_status_background)
                    net.setTextColor(ContextCompat.getColor(context, R.color.kitt_red))
                }
                
                mainHandler.postDelayed(this, 500) // 500ms (lent)
            }
        }
        mainHandler.post(thinkingAnimationNET!!)
        
        // RDY s'assombrit pendant le thinking
        rdy.alpha = 0.3f
        
        android.util.Log.d(TAG, "🧠 Thinking animation started (BSY: 250ms, NET: 500ms)")
    }
    
    /**
     * Arrêter thinking animation
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopThinkingAnimation(updateStatusCallback: (() -> Unit)? = null) {
        // Arrêter les animations
        thinkingAnimationBSY?.let { mainHandler.removeCallbacks(it) }
        thinkingAnimationNET?.let { mainHandler.removeCallbacks(it) }
        thinkingAnimationBSY = null
        thinkingAnimationNET = null
        
        // Restaurer RDY
        statusBarIndicatorRDY?.alpha = 1.0f
        
        // Restaurer l'état normal des LEDs (callback vers KittFragment)
        updateStatusCallback?.invoke()
        
        android.util.Log.d(TAG, "🧠 Thinking animation stopped")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // BUTTON ANIMATIONS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Animation fluide des boutons (rouge foncé → ambre → rouge vif)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun startSmoothButtonAnimation(
        buttons: List<MaterialButton>,
        statusBarIndicators: List<MaterialTextView>
    ) {
        buttons.forEach { button ->
            // Animation fluide du texte (rouge foncé → ambre → rouge vif)
            val textAnimator = ValueAnimator.ofArgb(
                ContextCompat.getColor(context, R.color.kitt_red_dark),
                ContextCompat.getColor(context, R.color.amber_primary),
                ContextCompat.getColor(context, R.color.kitt_red)
            )
            textAnimator.duration = 600
            textAnimator.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                button.setTextColor(color)
            }
            textAnimator.start()
            
            // Animation fluide des contours (rouge foncé → ambre → rouge vif)
            val strokeAnimator = ValueAnimator.ofArgb(
                ContextCompat.getColor(context, R.color.kitt_red_dark),
                ContextCompat.getColor(context, R.color.amber_primary),
                ContextCompat.getColor(context, R.color.kitt_red)
            )
            strokeAnimator.duration = 600
            strokeAnimator.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                button.setStrokeColor(android.content.res.ColorStateList.valueOf(color))
            }
            strokeAnimator.start()
        }
        
        // Animation fluide des Status Bar Indicators (sans effet ambre)
        statusBarIndicators.forEach { textView ->
            // Animation fluide du texte (rouge foncé → noir)
            val textAnimator = ValueAnimator.ofArgb(
                ContextCompat.getColor(context, R.color.kitt_red_dark),
                ContextCompat.getColor(context, R.color.kitt_black)
            )
            textAnimator.duration = 600
            textAnimator.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                textView.setTextColor(color)
            }
            textAnimator.start()
            
            // Animation du fond : changer le drawable progressivement
            mainHandler.postDelayed({
                textView.setBackgroundResource(R.drawable.kitt_status_background_active)
            }, 300)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Arrêter toutes les animations
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopAll() {
        stopScannerAnimation()
        stopVuMeterAnimation()
        stopSystemVolumeAnimation()
        stopThinkingAnimation()
        android.util.Log.d(TAG, "⏹️ All animations stopped")
    }
    
    /**
     * Détruire le manager (libérer ressources)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        stopAll()
        mainHandler.removeCallbacksAndMessages(null)
        kittSegments.clear()
        vuLeds.clear()
        android.util.Log.d(TAG, "🛑 KittAnimationManager destroyed")
    }
}
