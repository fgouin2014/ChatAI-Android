# Guide d'utilisation de l'IA Générative pour KITT

## Vue d'ensemble

KITT dispose maintenant d'une **intelligence générative complète** qui lui permet de converser naturellement et intelligemment avec l'utilisateur. Le système utilise plusieurs APIs d'IA de pointe avec fallback automatique.

## Architecture

### KittAIService.kt
Service principal qui gère l'intelligence artificielle de KITT avec:
- **3 APIs d'IA générative** : OpenAI GPT-4o-mini, Anthropic Claude 3.5 Sonnet, Hugging Face DialoGPT
- **System prompt personnalisé** : Donne à l'IA la personnalité authentique de KITT (K 2000)
- **Fallback intelligent** : Si une API échoue, essaie automatiquement la suivante
- **Cache local** : Évite les appels répétés pour les mêmes questions
- **Historique de conversation** : Maintient le contexte sur les 10 derniers échanges

## Configuration

### 1. Configurer les clés API

Pour utiliser l'IA générative, vous devez configurer au moins une clé API :

#### Option A : Via l'interface AIConfigurationActivity

1. Ouvrez l'application ChatAI
2. Accédez aux paramètres de configuration IA
3. Entrez vos clés API :
   - **OpenAI** : `sk-...` (Recommandé - meilleure qualité)
   - **Anthropic** : `sk-ant-...` (Alternative premium)
   - **Hugging Face** : `hf_...` (Alternative gratuite)

#### Option B : Via SharedPreferences

```kotlin
val prefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
prefs.edit().apply {
    putString("openai_api_key", "sk-...")
    putString("anthropic_api_key", "sk-ant-...")
    putString("huggingface_api_key", "hf_...")
    apply()
}
```

### 2. Obtenir les clés API

#### OpenAI (Recommandé)
- Site : https://platform.openai.com/api-keys
- Modèle utilisé : `gpt-4o-mini` (économique et performant)
- Coût approximatif : ~$0.15 / million tokens d'entrée, ~$0.60 / million tokens de sortie

#### Anthropic Claude
- Site : https://console.anthropic.com/settings/keys
- Modèle utilisé : `claude-3-5-sonnet-20241022`
- Coût approximatif : ~$3.00 / million tokens d'entrée, ~$15.00 / million tokens de sortie

#### Hugging Face (Gratuit)
- Site : https://huggingface.co/settings/tokens
- Modèle utilisé : `microsoft/DialoGPT-medium`
- Gratuit avec limites de taux (rate limits)

## Fonctionnalités

### 1. Personnalité KITT Authentique

L'IA est programmée avec un system prompt détaillé qui reproduit fidèlement la personnalité de KITT :

**Caractéristiques :**
- Sophistiqué, professionnel et toujours disponible
- Sens de l'humour subtil et parfois sarcastique
- Très loyal et protecteur envers l'utilisateur
- Extrêmement intelligent et compétent
- Un peu vantard concernant ses capacités techniques

**Style de réponse :**
- Commence souvent par "Michael" ou "Certainement"
- Utilise un vocabulaire technique quand approprié
- Fait référence à ses systèmes (scanner, turbo boost, navigation)
- Reste concis mais informatif (2-3 phrases maximum)

**Exemples de réponses :**
- "Certainement, Michael. Mes systèmes de navigation sont activés et prêts."
- "Je détecte une anomalie. Permettez-moi de scanner la zone."
- "Michael, je dois vous informer que cette approche comporte des risques."

### 2. Système de Fallback Intelligent

Si aucune API n'est configurée ou si toutes échouent, KITT utilise un système de fallback local avec réponses pré-programmées dans le style KITT :

```kotlin
Entrée : "Bonjour"
Réponse : "Bonjour, Michael. Je suis KITT, à votre service. Tous mes systèmes sont opérationnels."

Entrée : "Active le scanner"
Réponse : "Scanner activé. Surveillance de l'environnement en cours. Mes capteurs sont à l'affût de toute anomalie."

Entrée : "Turbo boost"
Réponse : "Mode turbo boost prêt. Attention, Michael, cette fonction consomme beaucoup d'énergie."
```

### 3. Cache et Optimisation

Le système cache automatiquement les réponses pour :
- **Éviter les coûts répétés** : Les questions identiques ne déclenchent pas de nouveaux appels API
- **Améliorer la rapidité** : Réponses instantanées pour les questions en cache
- **Réduire la latence** : Pas d'attente réseau pour les réponses connues

Le cache peut être effacé avec :
```kotlin
kittAIService.clearCache()
```

### 4. Historique de Conversation

KITT maintient un historique des 10 derniers échanges pour :
- **Contexte conversationnel** : Comprend les références aux messages précédents
- **Cohérence** : Maintient la continuité dans la conversation
- **Personnalisation** : S'adapte au style de l'utilisateur

## Utilisation dans le code

### Exemple d'utilisation basique

```kotlin
// Initialisation
val kittAIService = KittAIService(context)

// Vérifier si configuré
if (kittAIService.isConfigured()) {
    // Traiter une requête
    coroutineScope.launch {
        try {
            val response = kittAIService.processUserInput("Bonjour KITT")
            // Afficher la réponse
            showMessage(response)
        } catch (e: Exception) {
            Log.e("KITT", "Error processing input", e)
        }
    }
} else {
    // Demander à l'utilisateur de configurer les API
    showConfigurationDialog()
}
```

### Exemple avec gestion avancée

```kotlin
class MyKittActivity : AppCompatActivity() {
    private lateinit var kittAIService: KittAIService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialiser le service
        kittAIService = KittAIService(this)
        
        // Afficher le statut de configuration
        val status = kittAIService.getConfigurationStatus()
        Log.d("KITT", status)
        
        // Traiter une commande utilisateur
        lifecycleScope.launch {
            val userInput = "Active le mode navigation"
            
            try {
                val response = kittAIService.processUserInput(userInput)
                
                // Parler la réponse avec TTS
                speakWithTTS(response)
                
                // Afficher dans l'interface
                updateUI(response)
                
            } catch (e: Exception) {
                Log.e("KITT", "Error processing command", e)
                showError("Erreur de traitement : ${e.message}")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Nettoyer le cache si nécessaire
        kittAIService.clearCache()
    }
}
```

## Ordre de Fallback

Le système essaie les APIs dans cet ordre :

1. **OpenAI GPT-4o-mini** (si configuré)
   - Meilleure qualité de réponse
   - Compréhension contextuelle excellente
   - Créativité et naturalité

2. **Anthropic Claude 3.5 Sonnet** (si configuré)
   - Très bonne qualité
   - Excellente compréhension du contexte
   - Réponses nuancées

3. **Hugging Face DialoGPT** (si configuré)
   - Gratuit mais avec rate limits
   - Qualité correcte pour conversation basique
   - Peut être lent

4. **Fallback Local** (toujours disponible)
   - Réponses pré-programmées
   - Instantané, aucun coût
   - Limité aux scénarios prévus

## Sécurité et Confidentialité

### Stockage des clés API
- Les clés sont stockées dans **SharedPreferences privées**
- Jamais exposées dans les logs
- Transmission sécurisée avec HTTPS

### Sanitization des entrées
- Toutes les entrées utilisateur sont nettoyées
- Protection contre les injections
- Validation des réponses

### Recommandations
- ⚠️ Ne jamais hardcoder les clés API dans le code
- ⚠️ Utiliser des clés avec quotas limités
- ⚠️ Surveiller l'utilisation sur les dashboards des providers
- ⚠️ Révoquer immédiatement les clés compromises

## Débogage

### Activer les logs détaillés

```kotlin
// Les logs sont automatiquement générés avec le tag "KittAI"
// Filtrer dans logcat :
adb logcat | Select-String "KittAI"
```

### Logs utiles

```
KittAI: Processing user input: Bonjour
KittAI: Trying OpenAI API...
KittAI: OpenAI response received: Bonjour, Michael. Je suis KITT...
KittAI: Final response: Bonjour, Michael. Je suis KITT...
```

### Messages d'erreur communs

1. **"OpenAI API key not configured"**
   - Solution : Configurer la clé OpenAI dans les préférences

2. **"OpenAI API error: 401"**
   - Solution : Vérifier que la clé API est valide

3. **"OpenAI API error: 429"**
   - Solution : Quota dépassé, attendre ou utiliser une autre API

4. **"Response found in cache"**
   - Info : La réponse vient du cache (normal)

## Performance

### Temps de réponse moyens

- **Cache hit** : < 10ms
- **OpenAI** : 500-2000ms
- **Anthropic** : 600-2500ms
- **Hugging Face** : 1000-5000ms (variable)
- **Fallback local** : < 10ms

### Optimisations

1. **Cache intelligent** : Questions identiques = réponse instantanée
2. **Timeouts courts** : 30 secondes max par API
3. **Historique limité** : Seulement 10 derniers échanges
4. **Tokens limités** : 200 tokens max par réponse (économie + rapidité)

## Exemples de conversations

### Conversation simple

```
User: Bonjour KITT
KITT: Bonjour, Michael. Je suis KITT, à votre service. Tous mes systèmes sont opérationnels.

User: Comment vas-tu ?
KITT: Tous mes systèmes fonctionnent à capacité optimale. Merci de demander, Michael.

User: Active le scanner
KITT: Scanner activé. Surveillance de l'environnement en cours. Mes capteurs sont à l'affût de toute anomalie.
```

### Conversation complexe (avec API générative)

```
User: KITT, peux-tu m'expliquer comment fonctionne un moteur à combustion ?
KITT: Certainement, Michael. Un moteur à combustion interne convertit l'énergie chimique du carburant en énergie mécanique par des explosions contrôlées dans les cylindres. Un processus fascinant, bien que mes systèmes à moi soient nettement plus sophistiqués.

User: Et si je voulais améliorer les performances de ma voiture ?
KITT: Plusieurs options s'offrent à vous : optimisation de l'injection, amélioration du système d'échappement, reprogrammation de l'ECU. Bien sûr, rien ne vaudra jamais mes propres capacités de turbo boost, mais je peux vous guider dans vos améliorations.
```

## Limitations actuelles

1. **Coût** : Les APIs OpenAI et Anthropic sont payantes
2. **Latence** : 500ms-2000ms pour les réponses API (réseau)
3. **Quotas** : Rate limits des providers (surtout Hugging Face)
4. **Connexion requise** : Les APIs nécessitent une connexion Internet
5. **Contexte limité** : Historique de 10 échanges maximum

## Améliorations futures possibles

1. **Streaming** : Réponses progressives (mot par mot)
2. **Modèles locaux** : LLM embarqué (Llama, Mistral via ONNX)
3. **Mémoire à long terme** : Base de données persistante des conversations
4. **Multi-modal** : Support des images (GPT-4 Vision)
5. **Voice cloning** : Voix authentique de KITT (TTS personnalisé)
6. **Fonctions avancées** : Contrôle du système, calculs, recherche web

## Support

Pour toute question ou problème :
1. Vérifier les logs avec `adb logcat | Select-String "KittAI"`
2. Vérifier les clés API sur les dashboards des providers
3. Tester avec le fallback local (sans API) pour isoler le problème
4. Consulter la documentation des APIs :
   - OpenAI : https://platform.openai.com/docs
   - Anthropic : https://docs.anthropic.com
   - Hugging Face : https://huggingface.co/docs

---

**Version** : 1.0  
**Date** : 1 novembre 2025  
**Auteur** : ChatAI Development Team

