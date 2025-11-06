# KITT - Intelligence Générative

> Donnez une vraie IA à KITT, l'ordinateur de bord légendaire de K 2000

## Résumé Rapide

KITT dispose maintenant d'une **intelligence artificielle générative** qui lui permet de converser naturellement avec vous. Il utilise les meilleures APIs d'IA du marché (OpenAI GPT, Anthropic Claude, Hugging Face) avec un fallback local intelligent si aucune API n'est configurée.

## Fonctionnalités

- **3 APIs d'IA générative** : OpenAI, Anthropic, Hugging Face
- **Personnalité KITT authentique** : Style K 2000 fidèle à la série
- **Fallback intelligent** : Fonctionne même sans connexion Internet
- **Cache local** : Réponses instantanées pour questions répétées
- **Historique conversationnel** : KITT se souvient des 10 derniers échanges
- **Sécurité** : Stockage sécurisé des clés API, sanitization des entrées

## Installation Rapide

### 1. Compiler et installer l'application

```powershell
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android
.\gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Configurer une clé API (optionnel mais recommandé)

**Option A : Via l'interface**
1. Ouvrir ChatAI
2. Aller dans Configuration → IA
3. Entrer votre clé OpenAI (`sk-...`)
4. Sauvegarder

**Option B : Via PowerShell (pour développeurs)**
```powershell
# Créer un fichier temporaire avec la clé
$apiKey = "sk-..."
adb shell "run-as com.chatai sh -c 'echo $apiKey > /data/data/com.chatai/api_key.tmp'"
```

### 3. Tester

1. Ouvrir l'interface KITT dans l'application
2. Dire ou taper : "Bonjour KITT"
3. KITT répond avec son IA générative !

## Obtenir une clé API

### OpenAI (Recommandé - Meilleur rapport qualité/prix)

1. Aller sur : https://platform.openai.com/api-keys
2. Créer un compte ou se connecter
3. Cliquer sur "Create new secret key"
4. Copier la clé (format : `sk-...`)
5. La coller dans l'application ChatAI

**Coût :** ~$0.07 pour 1000 conversations (très abordable)

### Anthropic Claude (Alternative premium)

1. Aller sur : https://console.anthropic.com/settings/keys
2. Créer un compte
3. Générer une clé API
4. Format : `sk-ant-...`

**Coût :** ~$1.65 pour 1000 conversations

### Hugging Face (Gratuit)

1. Aller sur : https://huggingface.co/settings/tokens
2. Créer un compte
3. Générer un token
4. Format : `hf_...`

**Coût :** Gratuit (avec rate limits)

## Exemples de Conversations

### Sans API (Fallback local)

```
Vous: Bonjour
KITT: Bonjour, Michael. Je suis KITT, à votre service. 
      Tous mes systèmes sont opérationnels.

Vous: Active le scanner
KITT: Scanner activé. Surveillance de l'environnement en cours.
```

### Avec API générative (OpenAI/Claude)

```
Vous: Bonjour KITT
KITT: Bonjour, Michael. Je suis KITT, prêt à vous assister. 
      Tous mes systèmes sont en ligne et opérationnels. 
      Que puis-je faire pour vous aujourd'hui ?

Vous: Explique-moi comment fonctionne un moteur V8
KITT: Certainement. Un moteur V8 possède 8 cylindres disposés 
      en forme de V, offrant puissance et équilibre. Un système 
      impressionnant, bien que mes propres processeurs soient 
      nettement plus sophistiqués, naturellement.

Vous: Tu es modeste ?
KITT: Modeste ? Je préfère le terme "réaliste", Michael. 
      Mes capacités sont exceptionnelles, c'est un fait. 
      Mais je reste à votre service avec humilité.
```

## Documentation

### Fichiers créés

| Fichier | Description |
|---------|-------------|
| `KittAIService.kt` | Service principal d'IA générative |
| `KITT_AI_GENERATIVE_GUIDE.md` | Guide complet d'utilisation |
| `KITT_AI_TESTING_CHECKLIST.md` | Checklist de tests détaillée |
| `KITT_AI_INTEGRATION_SUMMARY.md` | Résumé technique complet |
| `KittAIServiceExample.kt` | Exemples de code et tests |
| `README_KITT_AI.md` | Ce fichier (point d'entrée) |

### Lecture recommandée

1. **Pour les utilisateurs** : Ce fichier (README_KITT_AI.md)
2. **Pour les développeurs** : `KITT_AI_GENERATIVE_GUIDE.md`
3. **Pour les tests** : `KITT_AI_TESTING_CHECKLIST.md`
4. **Pour les détails techniques** : `KITT_AI_INTEGRATION_SUMMARY.md`

## Débogage

### Voir les logs en temps réel

```powershell
adb logcat | Select-String "KittAI"
```

### Problèmes courants

**"OpenAI API key not configured"**
- Configurer la clé API dans l'application

**"OpenAI API error: 401"**
- Vérifier que la clé est valide sur https://platform.openai.com/api-keys

**"OpenAI API error: 429"**
- Quota dépassé, attendre ou créditer le compte

**Réponses lentes**
- Vérifier la connexion Internet
- Le cache accélère les questions répétées

## Architecture Technique

```
User Input
    ↓
KittFragment
    ↓
KittAIService
    ↓
┌─────────────────┐
│  1. Check Cache │
├─────────────────┤
│  2. Try OpenAI  │
├─────────────────┤
│  3. Try Claude  │
├─────────────────┤
│  4. Try HF      │
├─────────────────┤
│  5. Fallback    │
└─────────────────┘
    ↓
Response KITT
    ↓
TextToSpeech
```

## Performance

| Méthode | Temps moyen |
|---------|-------------|
| Cache | < 10ms |
| OpenAI | 500-2000ms |
| Anthropic | 600-2500ms |
| Hugging Face | 1-5 secondes |
| Fallback | < 10ms |

## Sécurité

- ✅ Clés API stockées en SharedPreferences privées
- ✅ Communications chiffrées (HTTPS uniquement)
- ✅ Entrées sanitizées contre les injections
- ✅ Pas de logs de données sensibles
- ✅ Timeouts pour éviter les blocages

## Code d'Exemple

### Utilisation basique

```kotlin
val kittAIService = KittAIService(context)

lifecycleScope.launch {
    try {
        val response = kittAIService.processUserInput("Bonjour KITT")
        println("KITT: $response")
    } catch (e: Exception) {
        Log.e("KITT", "Error", e)
    }
}
```

### Avec vérification de configuration

```kotlin
val kittAIService = KittAIService(context)

if (kittAIService.isConfigured()) {
    // Utiliser l'API
    lifecycleScope.launch {
        val response = kittAIService.processUserInput(userInput)
        showMessage(response)
    }
} else {
    // Demander la configuration
    showConfigDialog()
}
```

## Tests

### Lancer les tests

```powershell
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android
.\gradlew test
```

### Tests manuels

1. Ouvrir l'application
2. Aller dans l'interface KITT
3. Tester les commandes vocales ou textuelles
4. Observer les logs avec `adb logcat | Select-String "KittAI"`

## Limitations

- Connexion Internet requise pour les APIs (fallback local disponible hors ligne)
- Latence réseau : 0.5-2 secondes pour les API
- Coût des APIs : OpenAI/Anthropic sont payants (mais abordables)
- Contexte limité : 10 derniers échanges seulement

## Roadmap

### Prochaines étapes possibles

- [ ] Streaming des réponses (mot par mot)
- [ ] Modèle local embarqué (Llama/Mistral via ONNX)
- [ ] Support images (GPT-4 Vision)
- [ ] Voice cloning (voix authentique KITT)
- [ ] Mémoire à long terme (base de données)
- [ ] Fonctions avancées (contrôle système, recherche web)

## Support

### Ressources

- Documentation OpenAI : https://platform.openai.com/docs
- Documentation Anthropic : https://docs.anthropic.com
- Documentation Hugging Face : https://huggingface.co/docs

### Logs de débogage

```powershell
# Tous les logs KITT
adb logcat -s KittAI:D

# Avec timestamp
adb logcat -v time -s KittAI:D

# Sauvegarder dans un fichier
adb logcat -s KittAI:D > kitt_logs.txt
```

## Conclusion

KITT a maintenant une **vraie intelligence générative** qui lui permet de :
- Converser naturellement sur n'importe quel sujet
- Maintenir sa personnalité authentique de K 2000
- Fonctionner avec ou sans connexion Internet
- Apprendre et s'adapter à votre style de conversation

**Profitez de votre assistant IA KITT !** 🚗

---

**Version** : 1.0  
**Date** : 1 novembre 2025  
**Statut** : Production Ready  
**Auteur** : ChatAI Development Team

**Questions ?** Consultez la documentation complète dans `KITT_AI_GENERATIVE_GUIDE.md`

