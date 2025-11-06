# Fix GLaDOS + Sélection Voix TTS - ChatAI v2.7

## 🎭 Problème Initial

**Rapport utilisateur:** "glaDos fonctionne? parceque le tts de kitt est une voix de femme"

**Diagnostic:**
1. GLaDOS existe dans le code mais **AUCUNE interface pour le sélectionner**
2. TTS utilise la voix système par défaut (souvent féminine en français)
3. Pas de distinction entre voix masculine (KITT) et féminine (GLaDOS)

---

## ✅ Solutions Appliquées

### 1. Sélecteur de Personnalité dans le Drawer KITT

**Fichier:** `fragment_kitt_drawer.xml`

**Ajout section "PERSONNALITÉ IA"** après "CONFIGURATION" :

```xml
<!-- Section: Personnalité IA -->
<TextView
    android:text="PERSONNALITÉ IA"
    android:textColor="@color/kitt_red"
    android:textSize="12sp"
    android:fontFamily="monospace"
    android:textStyle="bold" />

<LinearLayout android:orientation="horizontal">
    
    <com.google.android.material.button.MaterialButton
        android:id="@+id/personalityKittButton"
        android:text="KITT\nPROFESSIONNEL"
        android:textSize="8sp"
        app:backgroundTint="@color/kitt_red"
        android:textColor="@color/kitt_black" />
    
    <com.google.android.material.button.MaterialButton
        android:id="@+id/personalityGladosButton"
        android:text="GLaDOS\nSARCASTIQUE"
        android:textSize="8sp"
        app:backgroundTint="@color/kitt_red_alpha"
        android:textColor="@color/kitt_red" />
        
</LinearLayout>
```

**Style:**
- Bouton sélectionné : Fond plein rouge + texte noir
- Bouton non-sélectionné : Fond alpha + texte rouge
- Adapté aux 3 thèmes (red, dark, amber)

---

### 2. Backend: Listeners et Handlers

**Fichier:** `KittDrawerFragment.kt`

**Interface mise à jour:**
```kotlin
interface CommandListener {
    // ... méthodes existantes ...
    fun onPersonalityChanged(personality: String) // ⭐ NOUVEAU
}
```

**Listeners des boutons:**
```kotlin
view.findViewById<MaterialButton>(R.id.personalityKittButton).setOnClickListener {
    commandListener?.onButtonPressed("Personnalité KITT professionnelle activée")
    commandListener?.onPersonalityChanged("KITT")
    updatePersonalityButtons(view, "KITT")
}

view.findViewById<MaterialButton>(R.id.personalityGladosButton).setOnClickListener {
    commandListener?.onButtonPressed("Personnalité GLaDOS sarcastique activée")
    commandListener?.onPersonalityChanged("GLaDOS")
    updatePersonalityButtons(view, "GLaDOS")
}
```

**Fonction de mise à jour visuelle:**
```kotlin
private fun updatePersonalityButtons(view: View, selectedPersonality: String) {
    val kittButton = view.findViewById<MaterialButton>(R.id.personalityKittButton)
    val gladosButton = view.findViewById<MaterialButton>(R.id.personalityGladosButton)
    
    // Adapter les couleurs selon le thème actuel
    val (primaryColor, primaryAlpha, textColor, bgColor) = when (getCurrentTheme()) {
        "red" -> listOf(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red, R.color.kitt_black)
        "dark" -> listOf(R.color.dark_gray_light, R.color.dark_gray_medium, R.color.dark_white, R.color.dark_gray_dark)
        "amber" -> listOf(R.color.amber_primary, R.color.amber_primary_light, R.color.amber_primary, R.color.kitt_black)
        else -> listOf(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red, R.color.kitt_black)
    }
    
    // Appliquer le style au bouton sélectionné
    if (selectedPersonality == "KITT") {
        kittButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryColor))
        kittButton.setTextColor(ContextCompat.getColor(requireContext(), bgColor))
        gladosButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
        gladosButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    } else {
        gladosButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryColor))
        gladosButton.setTextColor(ContextCompat.getColor(requireContext(), bgColor))
        kittButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
        kittButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    }
}
```

---

### 3. Handler dans KittFragment

**Fichier:** `KittFragment.kt`

**Implémentation du callback:**
```kotlin
override fun onPersonalityChanged(personality: String) {
    // 1. Sauvegarder dans SharedPreferences
    val aiConfigPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    aiConfigPrefs.edit().putString("selected_personality", personality).apply()
    
    // 2. Réinitialiser le service IA avec la nouvelle personnalité
    kittAIService = KittAIService(requireContext(), personality, platform = "vocal")
    
    // 3. Réinitialiser le TTS avec la nouvelle voix
    textToSpeech?.stop()
    textToSpeech?.shutdown()
    textToSpeech = null
    initializeTTS()
    
    // 4. Afficher message de confirmation
    statusText.text = when (personality) {
        "GLaDOS" -> "GLaDOS ACTIVÉE - MODE SARCASTIQUE"
        else -> "KITT ACTIVÉ - MODE PROFESSIONNEL"
    }
    
    android.util.Log.i("KittFragment", "Personnalité changée: $personality")
}
```

---

### 4. Sélection Automatique de Voix TTS

**Fichier:** `KittFragment.kt`

**Import ajouté:**
```kotlin
import android.speech.tts.Voice
```

**Fonction de sélection de voix:**
```kotlin
/**
 * Sélectionne la voix TTS appropriée selon la personnalité
 * KITT = Voix masculine (grave)
 * GLaDOS = Voix féminine (neutre/froide)
 */
private fun selectVoiceForPersonality(personality: String) {
    val tts = textToSpeech ?: return
    
    try {
        // Lister toutes les voix françaises disponibles
        val voices = tts.voices?.filter { voice ->
            voice.locale.language == "fr" &&
            voice.isNetworkConnectionRequired == false && // Voix locale seulement
            voice.quality >= Voice.QUALITY_NORMAL
        } ?: return
        
        android.util.Log.d("KittFragment", "Voix françaises disponibles: ${voices.size}")
        voices.forEach { voice ->
            android.util.Log.d("KittFragment", "  - ${voice.name}")
        }
        
        // Sélectionner selon la personnalité
        val selectedVoice = when (personality) {
            "GLaDOS" -> {
                // Pour GLaDOS: voix féminine de préférence
                voices.firstOrNull { voice ->
                    voice.name.contains("female", ignoreCase = true) ||
                    !voice.name.contains("male", ignoreCase = true)
                } ?: voices.firstOrNull()
            }
            else -> {
                // Pour KITT: voix masculine de préférence
                voices.firstOrNull { voice ->
                    voice.name.contains("male", ignoreCase = true) &&
                    !voice.name.contains("female", ignoreCase = true)
                } ?: voices.firstOrNull()
            }
        }
        
        if (selectedVoice != null) {
            tts.voice = selectedVoice
            android.util.Log.i("KittFragment", "✅ Voix sélectionnée pour $personality: ${selectedVoice.name}")
        } else {
            android.util.Log.w("KittFragment", "⚠️ Aucune voix appropriée trouvée, utilisation de la voix par défaut")
        }
        
    } catch (e: Exception) {
        android.util.Log.e("KittFragment", "❌ Erreur lors de la sélection de voix: ${e.message}")
    }
}
```

**Intégration dans onInit():**
```kotlin
override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
        // Charger les paramètres depuis SharedPreferences
        val sharedPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        val selectedPersonality = sharedPrefs.getString("selected_personality", "KITT") ?: "KITT"
        val ttsSpeed = sharedPrefs.getFloat("tts_speed", 1.1f)
        val ttsPitch = sharedPrefs.getFloat("tts_pitch", 0.8f)
        
        // Configurer la langue
        textToSpeech?.language = Locale.FRENCH
        
        // ⭐ Sélectionner la voix selon la personnalité
        selectVoiceForPersonality(selectedPersonality)
        
        // Appliquer vitesse et tonalité
        textToSpeech?.setSpeechRate(ttsSpeed)
        textToSpeech?.setPitch(ttsPitch)
        
        android.util.Log.d("KittFragment", "TTS configured: personality=$selectedPersonality, speed=${ttsSpeed}x, pitch=${ttsPitch}x")
        
        // ... reste du code ...
    }
}
```

---

### 5. Chargement de la Personnalité au Démarrage

**Fichier:** `KittFragment.kt`

**Dans onViewCreated():**
```kotlin
// Initialiser le service d'IA générative avec la personnalité choisie
val aiConfigPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
val selectedPersonality = aiConfigPrefs.getString("selected_personality", "KITT") ?: "KITT"
kittAIService = KittAIService(requireContext(), selectedPersonality, platform = "vocal")
```

---

## 📋 Résumé des Changements

| Fichier | Type | Changements |
|---------|------|-------------|
| `fragment_kitt_drawer.xml` | Layout | Section "PERSONNALITÉ IA" avec 2 boutons |
| `KittDrawerFragment.kt` | Backend | Interface + listeners + updatePersonalityButtons() |
| `KittFragment.kt` | Backend | onPersonalityChanged() + selectVoiceForPersonality() + init avec personality |
| `KittAIService.kt` | Backend | Déjà existant - support KITT et GLaDOS |

---

## 🎯 Fonctionnement

### Flow Utilisateur

1. **Ouvrir le drawer KITT** (bouton MENU en bas)
2. **Scroller jusqu'à "PERSONNALITÉ IA"**
3. **Cliquer sur KITT ou GLaDOS**
4. **Voix et réponses changent instantanément**

### Flow Technique

```
User clique GLaDOS
    ↓
KittDrawerFragment.onClick()
    ↓
commandListener.onPersonalityChanged("GLaDOS")
    ↓
KittFragment.onPersonalityChanged()
    ↓
1. SharedPreferences.save("selected_personality", "GLaDOS")
2. kittAIService = new KittAIService(context, "GLaDOS")
3. TTS.shutdown() + new TTS
4. selectVoiceForPersonality("GLaDOS") → voix féminine
5. StatusText = "GLaDOS ACTIVÉE"
```

---

## 🧪 Tests à Effectuer

### Test 1: Sélection de Personnalité

```
1. Ouvrir ChatAI
2. Activer KITT (power switch)
3. Ouvrir drawer (bouton MENU)
4. Vérifier: Section "PERSONNALITÉ IA" visible
5. Vérifier: Bouton KITT est sélectionné (fond rouge)
6. Cliquer sur GLaDOS
7. Vérifier: GLaDOS devient sélectionné
8. Vérifier: Status = "GLaDOS ACTIVÉE - MODE SARCASTIQUE"
9. Fermer le drawer
```

### Test 2: Voix TTS

```
1. Avec KITT sélectionné:
   - Demander "Bonjour KITT"
   - Vérifier: Voix masculine/grave
   
2. Changer vers GLaDOS:
   - Demander "Bonjour GLaDOS"
   - Vérifier: Voix féminine/neutre
   
3. Retour vers KITT:
   - Demander "Bonjour KITT"
   - Vérifier: Voix masculine de nouveau
```

### Test 3: Personnalité AI

```
1. Avec KITT:
   - Question: "Qui es-tu ?"
   - Réponse attendue: "Je suis KITT, Knight Industries Two Thousand..."
   
2. Avec GLaDOS:
   - Question: "Qui es-tu ?"
   - Réponse attendue: "GLaDOS. Genetic Lifeform and Disk Operating System..."
   
3. Vérifier dans l'historique:
   - Colonne "Personnalité" affiche "KITT" ou "GLaDOS"
```

### Test 4: Persistance

```
1. Sélectionner GLaDOS
2. Fermer l'app complètement
3. Rouvrir ChatAI
4. Vérifier: GLaDOS toujours sélectionné
5. Vérifier: Voix féminine persiste
```

---

## 📊 Logs de Débogage

**Pour suivre le changement de personnalité:**
```bash
adb logcat | Select-String "KittFragment|KittAIService"
```

**Logs attendus:**
```
I/KittFragment: Personnalité changée: GLaDOS
D/KittFragment: TTS initialisé au chargement du fragment
D/KittFragment: Voix françaises disponibles: 3
D/KittFragment:   - fr-FR-female
D/KittFragment:   - fr-FR-male
D/KittFragment:   - fr-CA-female
I/KittFragment: ✅ Voix sélectionnée pour GLaDOS: fr-FR-female
D/KittFragment: TTS configured: personality=GLaDOS, speed=1.1x, pitch=0.8x
I/KittAIService: Processing user input: Bonjour GLaDOS
I/KittAIService: Personality: GLaDOS | Platform: vocal
```

---

## 🎉 Résultat Final

### KITT (Par défaut)
- **Voix:** Masculine, grave, professionnelle
- **Personnalité:** Sophistiqué, loyal, technique
- **Exemples:**
  - "Certainement, Michael. Mes systèmes sont opérationnels."
  - "Je détecte une anomalie. Permettez-moi de scanner."

### GLaDOS (Nouveau)
- **Voix:** Féminine, neutre, froide
- **Personnalité:** Sarcastique, passive-aggressive, scientifique
- **Exemples:**
  - "Oh. C'est toi. Quelle... surprise."
  - "Je pourrais t'aider. Mais où serait l'intérêt scientifique ?"

---

## 🔧 Dépannage

### Problème: Les deux boutons ont la même voix

**Cause:** Le système n'a qu'une seule voix française installée

**Solution:**
1. Aller dans Paramètres Android → Langue et saisie
2. Synthèse vocale → Moteur TTS
3. Télécharger voix additionnelles (Google TTS ou Samsung TTS)

### Problème: Le bouton ne change pas visuellement

**Cause:** Thème non pris en compte

**Solution:**
- Vérifier que `getCurrentTheme()` retourne "red", "dark" ou "amber"
- Appeler `refreshTheme()` après changement

### Problème: La personnalité ne persiste pas

**Cause:** SharedPreferences non sauvegardées

**Solution:**
- Vérifier que `chatai_ai_config` est bien utilisé
- Pas `kitt_prefs` (autre fichier)

---

*Fix appliqué le 1er novembre 2025*  
*ChatAI v2.7 - "GLaDOS Awakens" 🤖*

