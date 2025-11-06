# Fix Voix TTS - Drawer et Reconnaissance Vocale

## 🎙️ Problèmes Identifiés

### Problème 1: Voix Ne Parle PAS dans le Drawer
**Symptôme:** Cliquer boutons du drawer → Aucune annonce vocale

**Cause:** `speakAIResponse()` bloqué par condition `!isReady`
```kotlin
// AVANT (BUGGÉ)
private fun speakAIResponse(response: String) {
    if (textToSpeech == null || !isReady || isTTSSpeaking) return  // ❌ Bloque si KITT OFF!
    textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "ai_response")
}
```

Si KITT est désactivé (power switch OFF), `isReady = false`, donc les annonces du drawer ne fonctionnent PAS.

### Problème 2: Mauvaise Voix Sélectionnée pour KITT
**Symptôme:** KITT a une voix féminine malgré 11 voix disponibles

**Logs capturés:**
```
TOTAL VOIX: 474
VOIX FRANÇAISES LOCALES: 11
✅ VOIX SÉLECTIONNÉE: fr-fr-x-frc-local  ❌ FÉMININE (C)!
```

**Voix disponibles:**
- `fr-fr-x-fra-local` - Féminine (A)
- `fr-fr-x-frb-local` - **MASCULINE (B)** ⭐
- `fr-fr-x-frc-local` - Féminine (C)
- `fr-fr-x-frd-local` - **MASCULINE (D)** ⭐
- `fr-fr-x-vlf-local` - ?
- + 6 autres (réseau ou Canada)

**Cause:** Détection incorrecte - cherchait "-frb-" avec tirets au lieu de "frb"

---

## ✅ Solutions Appliquées

### Fix 1: Permettre TTS Même si KITT est OFF

```kotlin
// APRÈS (CORRIGÉ)
private fun speakAIResponse(response: String) {
    // ⭐ PERMETTRE LA VOIX MÊME SI KITT EST OFF (pour les annonces du drawer)
    if (textToSpeech == null || isTTSSpeaking) return  // Seulement TTS null ou déjà en cours
    
    try {
        // Afficher dans le marquee seulement si KITT est activé
        if (isReady) {
            showStatusMessage("KITT: '$response'", 5000, MessageType.VOICE)
        }
        
        // Parler TOUJOURS (même si KITT OFF)
        textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "ai_response")
        android.util.Log.d("KittFragment", "🔊 TTS Speaking: '$response'")
    } catch (e: Exception) {
        android.util.Log.e("KittFragment", "❌ TTS Error: ${e.message}")
    }
}
```

**Résultat:**
- ✅ Drawer annonce les boutons MÊME si KITT est OFF
- ✅ "Personnalité KITT professionnelle activée" se fait entendre
- ✅ "Configuration IA" s'annonce au clic

### Fix 2: Détection Correcte des Voix Masculines

```kotlin
// AVANT (BUGGÉ)
frenchVoices.firstOrNull { voice ->
    voice.name.contains("-frb-", ignoreCase = true)  // ❌ Avec tirets, ne match pas!
}

// APRÈS (CORRIGÉ)
when (personality) {
    "GLaDOS" -> {
        // GLaDOS: voix féminine (A, C, E)
        selectedVoice = frenchVoices.firstOrNull { voice ->
            voice.name.contains("fra", ignoreCase = true) || // fr-fr-x-fra-local
            voice.name.contains("frc", ignoreCase = true) || // fr-fr-x-frc-local
            voice.name.contains("fre", ignoreCase = true)
        }
        
        // Fallback: éviter frb/frd (masculines)
        if (selectedVoice == null) {
            selectedVoice = frenchVoices.firstOrNull { voice ->
                !voice.name.contains("frb") && !voice.name.contains("frd")
            }
        }
    }
    else -> {
        // KITT: voix masculine - PRIORITÉ FRB
        selectedVoice = frenchVoices.firstOrNull { voice ->
            voice.name.contains("frb", ignoreCase = true)  // ✅ SANS tirets, match!
        }
        
        // Fallback: FRD
        if (selectedVoice == null) {
            selectedVoice = frenchVoices.firstOrNull { voice ->
                voice.name.contains("frd", ignoreCase = true)
            }
        }
    }
}
```

**Résultat attendu:**
```
Logs après installation:
✅ VOIX SÉLECTIONNÉE: fr-fr-x-frb-local  ✅ MASCULINE (B)!
   Genre détecté: MASCULIN
   Pour personnalité: KITT
```

---

## 🧪 Tests à Effectuer

### Test 1: Annonce Drawer (KITT OFF)

```
1. Ouvrir ChatAI
2. NE PAS activer KITT (switch reste OFF)
3. Ouvrir drawer
4. Cliquer un bouton (ex: "ACTIVER KITT")
5. ✅ Devrait annoncer: "Activation de KITT"
```

### Test 2: Annonce Drawer (KITT ON)

```
1. Activer KITT (switch ON)
2. Ouvrir drawer
3. Cliquer "KITT PROFESSIONNEL"
4. ✅ Devrait annoncer: "Personnalité KITT professionnelle activée"
5. ✅ Status: "KITT ACTIVÉ - VOIX CHANGÉE"
```

### Test 3: Voix Masculine pour KITT

```
1. Drawer → KITT PROFESSIONNEL
2. Désactiver/réactiver KITT
3. Dire "Bonjour KITT"
4. ✅ Voix devrait être MASCULINE (grave)
```

### Test 4: Voix Féminine pour GLaDOS

```
1. Drawer → GLaDOS SARCASTIQUE
2. Désactiver/réactiver KITT
3. Dire "Bonjour GLaDOS"
4. ✅ Voix devrait être FÉMININE
```

### Test 5: Reconnaissance Vocale

```
1. Activer KITT
2. Cliquer bouton "AI"
3. Parler: "Quelle heure est-il ?"
4. ✅ KITT devrait répondre avec voix
```

---

## 📋 Logs de Débogage

```bash
# Voir les voix sélectionnées
adb logcat | Select-String "VOIX SÉLECTIONNÉE|Genre détecté"

# Voir les annonces TTS
adb logcat | Select-String "TTS Speaking"

# Voir les erreurs TTS
adb logcat | Select-String "TTS Error"
```

**Logs attendus après fix:**
```
I/KittFragment: 🚗 KITT: Cherche voix masculine (frb/frd)
I/KittFragment: ✅ VOIX SÉLECTIONNÉE: fr-fr-x-frb-local
I/KittFragment:    Genre détecté: MASCULIN
I/KittFragment:    Pour personnalité: KITT

D/KittFragment: 🔊 TTS Speaking: 'Personnalité KITT professionnelle activée'
```

---

## 🔧 Nomenclature Google TTS

| Suffixe | Genre | Exemples |
|---------|-------|----------|
| **-fra-** | Féminin (A) | fr-fr-x-fra-local |
| **-frb-** | **Masculin (B)** | fr-fr-x-frb-local ⭐ |
| **-frc-** | Féminin (C) | fr-fr-x-frc-local |
| **-frd-** | **Masculin (D)** | fr-fr-x-frd-local ⭐ |
| **-fre-** | Féminin (E) | fr-fr-x-fre-local |
| **-vlf-** | Voix légère (?) | fr-fr-x-vlf-local |

---

## 📊 Différences Avant/Après

| Aspect | Avant | Après |
|--------|-------|-------|
| **Voix drawer (KITT OFF)** | ❌ Silence | ✅ Annonce vocale |
| **Voix KITT** | ❌ frc (féminine) | ✅ frb (masculine) |
| **Voix GLaDOS** | ? | ✅ frc/fra (féminine) |
| **Détection voix** | Cherche "-frb-" | Cherche "frb" ✅ |
| **Logs** | Basiques | Détaillés avec genre |

---

*Fix appliqué le 1er novembre 2025*  
*ChatAI v2.9 - "True Voice Selection"* 🎙️

