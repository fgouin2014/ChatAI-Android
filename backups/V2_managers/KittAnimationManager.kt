package com.chatai.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import com.chatai.R

/**
 * Modes du VU-meter
 */
enum class VUMeterMode {
    OFF,        // VU-meter éteint
    VOICE,      // Mode voix TTS
    AMBIENT,    // Mode sons ambiants
    SYSTEM      // Mode volume système
}

/**
 * Modes d'animation du VU-meter
 */
enum class VUAnimationMode {
    ORIGINAL,   // Animation originale : de bas en haut seulement
    DUAL        // Animation dual : en-haut et en-bas vers le centre
}

/**
 * 🎬 Gestionnaire d'Animations pour KITT
 * 
 * Responsabilités:
 * - Animation Scanner KITT (LEDs rouges)
 * - Animation VU-meter (LEDs vertes)
 * - Animation Thinking (LEDs BSY/NET)
 * - Gestion des handlers et cleanup
 */
class KittAnimationManager {
    
    companion object {
        private const val TAG = "KittAnimationManager"
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Scanner KITT
    private var scannerAnimation: Runnable? = null
    private var scannerPosition = 0
    private var scannerDirection = 1
    private var scannerLeds = listOf<ImageView>()
    
    // VU-meter
    private var vuMeterAnimation: Runnable? = null
    private var vuLeds = listOf<ImageView>()
    private var vuMeterMode = VUMeterMode.VOICE
    private var vuAnimationMode = VUAnimationMode.ORIGINAL
    
    // Thinking animation
    private var thinkingAnimationBSY: Runnable? = null
    private var thinkingAnimationNET: Runnable? = null
    private var bsyIndicator: android.widget.TextView? = null
    private var netIndicator: android.widget.TextView? = null
    
    /**
     * Initialiser le scanner KITT
     */
    fun initializeScanner(leds: List<ImageView>) {
        scannerLeds = leds
        Log.i(TAG, "✅ Scanner initialized with ${leds.size} LEDs")
    }
    
    /**
     * Initialiser le VU-meter
     */
    fun initializeVuMeter(leds: List<ImageView>) {
        vuLeds = leds
        Log.i(TAG, "✅ VU-meter initialized with ${leds.size} LEDs")
    }
    
    /**
     * Initialiser les indicateurs de thinking
     */
    fun initializeThinkingIndicators(bsy: android.widget.TextView, net: android.widget.TextView) {
        bsyIndicator = bsy
        netIndicator = net
        Log.i(TAG, "✅ Thinking indicators initialized")
    }
    
    /**
     * Démarrer l'animation du scanner KITT (comme V1 original)
     */
    fun startScanner(speedMs: Int = 60) {
        stopScanner()
        
        scannerPosition = 0
        scannerDirection = 1
        
        scannerAnimation = object : Runnable {
            override fun run() {
                // Éteindre tous les segments
                scannerLeds.forEach { it.setImageResource(R.drawable.kitt_scanner_segment_off) }
                
                // Créer l'effet de balayage avec dégradé de luminosité (comme V1 original)
                for (i in -2..2) {
                    val index = scannerPosition + i
                    if (index in 0 until scannerLeds.size) {
                        val segment = scannerLeds[index]
                        when (i) {
                            0 -> segment.setImageResource(R.drawable.kitt_scanner_segment_max)    // Centre (max)
                            1, -1 -> segment.setImageResource(R.drawable.kitt_scanner_segment_high)   // Voisins (high)
                            2, -2 -> segment.setImageResource(R.drawable.kitt_scanner_segment_medium) // Extrêmes (medium)
                        }
                    }
                }
                
                // Mouvement avec rebond
                scannerPosition += scannerDirection
                
                if (scannerPosition >= scannerLeds.size - 1) {
                    scannerDirection = -1
                } else if (scannerPosition <= 0) {
                    scannerDirection = 1
                }
                
                handler.postDelayed(this, speedMs.toLong())
            }
        }
        
        handler.post(scannerAnimation!!)
        Log.i(TAG, "▶️ Scanner animation started (${scannerLeds.size} LEDs)")
    }
    
    /**
     * Arrêter l'animation du scanner
     */
    fun stopScanner() {
        scannerAnimation?.let { handler.removeCallbacks(it) }
        scannerAnimation = null
        
        // Reset scanner (comme V1 original)
        scannerLeds.forEachIndexed { index, segment ->
            segment.setImageResource(R.drawable.kitt_scanner_segment_off)
            // Segments centraux légèrement allumés par défaut
            if (index in 10..13) {
                segment.setImageResource(R.drawable.kitt_scanner_segment_low)
            }
        }
        scannerPosition = 0
        scannerDirection = 1
        
        Log.i(TAG, "⏹️ Scanner animation stopped")
    }
    
    /**
     * Démarrer l'animation du VU-meter
     */
    fun startVuMeter(level: Float = 0.5f) {
        stopVuMeter()
        
        var frame = 0
        
        vuMeterAnimation = object : Runnable {
            override fun run() {
                // Éteindre toutes les LEDs
                vuLeds.forEach { it.setImageResource(R.drawable.kitt_vu_led_off) }
                
                // Pattern vague (bas→haut→bas) - Ajusté pour 60 LEDs
                val ledsPerBar = 20
                val pattern = listOf(
                    listOf(0, 20, 40),                                  // Bas uniquement (3 barres)
                    listOf(0, 1, 2, 20, 21, 22, 40, 41, 42),           // 3 premières de chaque barre
                    listOf(0, 1, 2, 3, 4, 5, 20, 21, 22, 23, 24, 25, 40, 41, 42, 43, 44, 45), // 6 premières
                    listOf(0, 1, 2, 20, 21, 22, 40, 41, 42),           // Retour à 3
                    listOf(0, 20, 40)                                   // Bas uniquement
                )
                
                val currentPattern = pattern[frame % pattern.size]
                currentPattern.forEach { index ->
                    vuLeds.getOrNull(index)?.setImageResource(R.drawable.kitt_vu_led_active)
                }
                
                frame++
                handler.postDelayed(this, 150)
            }
        }
        
        handler.post(vuMeterAnimation!!)
        Log.i(TAG, "▶️ VU-meter animation started")
    }
    
    /**
     * Arrêter l'animation du VU-meter
     */
    fun stopVuMeter() {
        vuMeterAnimation?.let { handler.removeCallbacks(it) }
        vuMeterAnimation = null
        
        // Éteindre toutes les LEDs
        vuLeds.forEach { it.setImageResource(R.drawable.kitt_vu_led_off) }
        
        Log.i(TAG, "⏹️ VU-meter animation stopped")
    }
    
    /**
     * Mettre à jour le VU-meter avec un niveau spécifique (0.0-1.0)
     * COPIE EXACTE DE V1 ORIGINAL
     */
    fun updateVuMeter(level: Float = 0.3f) {
        // Debug : Afficher le niveau reçu
        Log.d("VUMeter", "updateVuMeter called with level: $level")
        
        // Debug : Vérifier les LEDs VU
        Log.d("VUMeter", "Total VU LEDs: ${vuLeds.size}")
        
        // Si le niveau est très faible, éteindre complètement
        if (level < 0.05f) {
            Log.d("VUMeter", "Level too low, turning off LEDs")
            vuLeds.forEach { led ->
                led.setImageResource(R.drawable.kitt_vu_led_off)
            }
            return
        }
        
        // Améliorer la sensibilité - amplification du signal
        val amplifiedLevel = kotlin.math.sqrt(level.toDouble()).toFloat() // Racine carrée pour plus de sensibilité
        val enhancedLevel = (amplifiedLevel * 1.8f).coerceIn(0f, 1f) // Amplification x1.8
        
        // COMMENCER AVEC TOUTES LES LEDs ÉTEINTES (NOIRES)
        vuLeds.forEach { led ->
            led.setImageResource(R.drawable.kitt_vu_led_off)
        }
        
        // Gérer les LEDs par colonnes verticales (3 colonnes)
        val totalColumns = 3
        val ledsPerColumn = vuLeds.size / totalColumns // 20 LEDs par colonne
        
        // Colonnes latérales synchronisées (même niveau)
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
            Log.d("VUMeter", "Column $columnIndex: adjustedLevel=$adjustedLevel, ledsToTurnOn=$ledsToTurnOn")
            
            // Choisir le mode d'animation selon vuAnimationMode
            when (vuAnimationMode) {
                VUAnimationMode.ORIGINAL -> {
                    // Animation originale : du milieu (9) vers le bas (0) ET du milieu (10) vers le haut (19)
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
                        }
                    }
                }
                VUAnimationMode.DUAL -> {
                    // Animation dual : en-haut et en-bas vers le centre
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
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Changer le mode du VU-meter
     */
    fun setVuMeterMode(mode: VUMeterMode) {
        vuMeterMode = mode
        Log.i(TAG, "VU-meter mode set to: $mode")
    }
    
    /**
     * Changer le mode d'animation du VU-meter
     */
    fun setVuAnimationMode(mode: VUAnimationMode) {
        vuAnimationMode = mode
        Log.i(TAG, "VU animation mode set to: $mode")
    }
    
    /**
     * Obtenir le mode actuel
     */
    fun getVuMeterMode(): VUMeterMode = vuMeterMode
    
    /**
     * Obtenir le mode d'animation actuel
     */
    fun getVuAnimationMode(): VUAnimationMode = vuAnimationMode
    
    /**
     * Démarrer l'animation Thinking (BSY/NET clignotent)
     */
    fun startThinking() {
        stopThinking()
        
        var bsyState = true
        var netState = false
        
        thinkingAnimationBSY = object : Runnable {
            override fun run() {
                bsyState = !bsyState
                bsyIndicator?.setBackgroundColor(
                    if (bsyState) 0xFFFF0000.toInt() // Rouge
                    else 0xFF330000.toInt() // Rouge sombre
                )
                handler.postDelayed(this, 500)
            }
        }
        
        thinkingAnimationNET = object : Runnable {
            override fun run() {
                netState = !netState
                netIndicator?.setBackgroundColor(
                    if (netState) 0xFFFF0000.toInt() // Rouge
                    else 0xFF330000.toInt() // Rouge sombre
                )
                handler.postDelayed(this, 700) // Vitesse différente pour effet async
            }
        }
        
        handler.post(thinkingAnimationBSY!!)
        handler.post(thinkingAnimationNET!!)
        
        Log.i(TAG, "▶️ Thinking animation started")
    }
    
    /**
     * Arrêter l'animation Thinking
     */
    fun stopThinking() {
        thinkingAnimationBSY?.let { handler.removeCallbacks(it) }
        thinkingAnimationNET?.let { handler.removeCallbacks(it) }
        thinkingAnimationBSY = null
        thinkingAnimationNET = null
        
        // Remettre en état inactif
        bsyIndicator?.setBackgroundColor(0xFF330000.toInt()) // Rouge sombre
        netIndicator?.setBackgroundColor(0xFF330000.toInt()) // Rouge sombre
        
        Log.i(TAG, "⏹️ Thinking animation stopped")
    }
    
    /**
     * Arrêter toutes les animations
     */
    fun stopAll() {
        stopScanner()
        stopVuMeter()
        stopThinking()
        Log.i(TAG, "⏹️ All animations stopped")
    }
    
    /**
     * Détruire le manager
     */
    fun destroy() {
        stopAll()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "🛑 KittAnimationManager destroyed")
    }
}

