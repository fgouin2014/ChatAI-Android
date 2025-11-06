# Checklist de Test - IA Générative KITT

## Préparation

### Configuration requise
- [ ] Application ChatAI compilée et installée
- [ ] Au moins une clé API configurée (OpenAI recommandé)
- [ ] Permissions microphone accordées (pour tests vocaux)
- [ ] Connexion Internet active

### Outils nécessaires
- [ ] `adb` installé et configuré
- [ ] PowerShell pour les commandes de log
- [ ] Compte API avec crédit disponible

## Tests de Base

### 1. Test de Configuration

#### Sans clé API configurée
```powershell
# Effacer les clés API existantes
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED

# Lancer KITT
# Observer : Le fallback local doit fonctionner
```

**Comportement attendu :**
- ✅ KITT répond avec les réponses locales pré-programmées
- ✅ Pas d'erreur réseau
- ✅ Réponses instantanées
- ✅ Style KITT maintenu

**Entrées de test :**
```
- "Bonjour" → "Bonjour, Michael. Je suis KITT..."
- "Scanner" → "Scanner activé..."
- "Turbo" → "Mode turbo boost prêt..."
- "Système" → "Tous mes systèmes sont opérationnels..."
```

#### Avec clé API OpenAI
```powershell
# Configurer la clé API via l'interface
# Ou via adb :
adb shell "run-as com.chatai mkdir -p /data/data/com.chatai/shared_prefs"
```

**Comportement attendu :**
- ✅ KITT utilise OpenAI pour les réponses
- ✅ Réponses plus naturelles et contextuelles
- ✅ Personnalité KITT maintenue
- ✅ Temps de réponse 0.5-2 secondes

**Entrées de test :**
```
- "Bonjour KITT" → Réponse personnalisée KITT
- "Explique-moi la théorie de la relativité" → Explication avec style KITT
- "Quelle est la capitale de la France ?" → "Paris" avec style KITT
```

### 2. Test du Cache

**Procédure :**
1. Poser une question : "Bonjour KITT"
2. Observer le temps de réponse (1-2 sec avec API)
3. Poser la MÊME question immédiatement après
4. Observer le temps de réponse (< 10ms avec cache)

```powershell
# Observer les logs
adb logcat -s KittAI:D
```

**Logs attendus :**
```
KittAI: Processing user input: Bonjour KITT
KittAI: Trying OpenAI API...
KittAI: OpenAI response received: Bonjour, Michael...

# Deuxième fois (cache hit) :
KittAI: Processing user input: Bonjour KITT
KittAI: Response found in cache
```

### 3. Test du Fallback

**Procédure :**
1. Configurer une clé API invalide
2. Poser une question
3. Observer le fallback vers le système local

**Comportement attendu :**
- ✅ Tentative avec l'API (erreur 401)
- ✅ Fallback automatique vers réponses locales
- ✅ Pas de crash
- ✅ Message d'erreur approprié si nécessaire

**Logs attendus :**
```
KittAI: Trying OpenAI API...
KittAI: OpenAI API error: 401 - Invalid API key
KittAI: Using local fallback response
```

### 4. Test de l'Historique Conversationnel

**Procédure :**
1. Poser une question : "Bonjour, je m'appelle Jean"
2. Poser une question de suivi : "Quel est mon nom ?"
3. Vérifier que KITT se souvient du contexte

**Comportement attendu :**
- ✅ KITT se souvient du nom (avec API générative)
- ✅ Réponse contextuelle : "Votre nom est Jean, Michael"
- ✅ L'historique est maintenu sur 10 échanges

**Exemple avec OpenAI :**
```
User: "Bonjour, je m'appelle Jean"
KITT: "Bonjour Jean. Je suis KITT, à votre service..."

User: "Quel est mon nom ?"
KITT: "Votre nom est Jean, comme vous me l'avez indiqué tout à l'heure."
```

## Tests Avancés

### 5. Test Multi-API (Fallback Cascade)

**Configuration :**
1. Configurer OpenAI avec clé invalide
2. Configurer Anthropic avec clé valide
3. Poser une question

**Comportement attendu :**
- ✅ Tentative OpenAI (échec 401)
- ✅ Fallback automatique vers Anthropic (succès)
- ✅ Réponse reçue depuis Anthropic
- ✅ Style KITT maintenu

**Logs attendus :**
```
KittAI: Trying OpenAI API...
KittAI: OpenAI API error: 401
KittAI: Trying Anthropic Claude API...
KittAI: Anthropic response received: Certainement, Michael...
```

### 6. Test de Performance

**Questions simples (attendu < 2s avec API) :**
```
- "Bonjour"
- "Quelle heure est-il ?"
- "Comment vas-tu ?"
```

**Questions complexes (attendu < 3s avec API) :**
```
- "Explique-moi la physique quantique en termes simples"
- "Quelles sont les différences entre un moteur V6 et V8 ?"
- "Comment puis-je optimiser les performances de ma voiture ?"
```

**Mesure du temps :**
```powershell
# Observer les timestamps dans les logs
adb logcat -v time -s KittAI:D
```

### 7. Test de Quota et Rate Limits

**Procédure :**
1. Faire 50 requêtes successives rapidement
2. Observer les erreurs de rate limit éventuelles
3. Vérifier le fallback automatique

**Comportement attendu (Hugging Face) :**
- ✅ Première vague de requêtes passe
- ✅ Rate limit atteint après N requêtes
- ✅ Erreur 429 loggée
- ✅ Fallback vers réponses locales
- ✅ Pas de crash

### 8. Test de Sécurité

**Entrées malveillantes à tester :**
```
- "'; DROP TABLE users; --"
- "<script>alert('XSS')</script>"
- "../../../../etc/passwd"
- "{{7*7}}" (injection template)
```

**Comportement attendu :**
- ✅ Entrées sanitizées correctement
- ✅ Pas d'exécution de code malveillant
- ✅ Réponse normale de KITT
- ✅ Logs sans données sensibles

### 9. Test de Personnalité KITT

**Vérifier que KITT maintient sa personnalité :**

**Questions pour tester la personnalité :**
```
User: "Qui es-tu ?"
Attendu: Référence à "Knight Industries Two Thousand" ou "KITT"

User: "Peux-tu m'aider ?"
Attendu: "Certainement, Michael" ou "À votre service"

User: "Active le turbo"
Attendu: Référence au turbo boost avec style KITT

User: "Tu es bête"
Attendu: Réponse sarcastique mais professionnelle
```

**Vérifications :**
- ✅ Utilise "Michael" régulièrement
- ✅ Fait référence à ses systèmes (scanner, navigation, turbo)
- ✅ Ton professionnel et sophistiqué
- ✅ Touches d'humour subtil
- ✅ Reste dans le personnage même pour questions complexes

### 10. Test d'Intégration Vocale

**Procédure :**
1. Activer l'interface vocale de KITT
2. Dire une commande vocale : "KITT, active le scanner"
3. Vérifier la reconnaissance et la réponse TTS

**Comportement attendu :**
- ✅ Reconnaissance vocale fonctionne
- ✅ Traitement par l'IA générative
- ✅ Réponse synthétisée en voix
- ✅ Animation VU-meter pendant la parole
- ✅ Scanner animé pendant le traitement

## Tests de Robustesse

### 11. Test de Connexion Instable

**Procédure :**
1. Activer le mode avion pendant une requête
2. Observer le comportement
3. Réactiver la connexion

**Comportement attendu :**
- ✅ Timeout après 30 secondes max
- ✅ Fallback vers réponses locales
- ✅ Message d'erreur approprié
- ✅ Pas de crash
- ✅ Récupération automatique après reconnexion

### 12. Test de Mémoire et Fuites

**Procédure :**
1. Faire 1000 requêtes successives
2. Observer l'utilisation mémoire
3. Vérifier les fuites mémoires

```powershell
# Profiler mémoire
adb shell dumpsys meminfo com.chatai
```

**Comportement attendu :**
- ✅ Pas de fuite mémoire
- ✅ Cache limité et géré correctement
- ✅ Historique plafonné à 10 échanges
- ✅ Pas de crash OOM (Out Of Memory)

### 13. Test de Concurrence

**Procédure :**
1. Lancer plusieurs conversations en parallèle
2. Observer la gestion des threads
3. Vérifier l'absence de race conditions

**Comportement attendu :**
- ✅ Chaque requête traitée correctement
- ✅ Pas de mélange de réponses
- ✅ Pas de deadlock
- ✅ Ordre des réponses respecté

## Validation Finale

### Checklist de validation complète

**Configuration :**
- [ ] Au moins une API fonctionnelle configurée
- [ ] Fallback local testé et fonctionnel
- [ ] Cache opérationnel

**Fonctionnalités :**
- [ ] Réponses génératives naturelles
- [ ] Personnalité KITT authentique
- [ ] Historique conversationnel maintenu
- [ ] Fallback automatique en cas d'erreur
- [ ] Performance acceptable (< 2s par réponse)

**Intégration :**
- [ ] Interface vocale intégrée
- [ ] TTS avec voix KITT
- [ ] Scanner et VU-meter animés
- [ ] Logs propres et informatifs

**Robustesse :**
- [ ] Gestion des erreurs réseau
- [ ] Gestion des quotas dépassés
- [ ] Pas de fuite mémoire
- [ ] Pas de crash sur entrées malveillantes

**Sécurité :**
- [ ] Clés API stockées de façon sécurisée
- [ ] Entrées sanitizées
- [ ] Pas de logs de données sensibles

## Commandes Utiles

### Logs en temps réel
```powershell
# Tous les logs KITT
adb logcat -s KittAI:D

# Logs avec filtre
adb logcat | Select-String "KittAI"

# Logs avec timestamp
adb logcat -v time -s KittAI:D
```

### Effacer le cache
```powershell
# Via adb
adb shell pm clear com.chatai

# Ou dans le code
kittAIService.clearCache()
```

### Vérifier la configuration
```kotlin
val status = kittAIService.getConfigurationStatus()
Log.d("TEST", status)
```

### Tester manuellement les APIs

**OpenAI :**
```bash
curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-..." \
  -d '{
    "model": "gpt-4o-mini",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

**Anthropic :**
```bash
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: sk-ant-..." \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d '{
    "model": "claude-3-5-sonnet-20241022",
    "max_tokens": 200,
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

**Hugging Face :**
```bash
curl https://api-inference.huggingface.co/models/microsoft/DialoGPT-medium \
  -H "Authorization: Bearer hf_..." \
  -H "Content-Type: application/json" \
  -d '{"inputs": "Hello"}'
```

## Résultats Attendus

### Tous les tests doivent passer

Si un test échoue :
1. Vérifier les logs pour identifier la cause
2. Vérifier la configuration des clés API
3. Vérifier la connexion Internet
4. Vérifier les quotas API
5. Consulter le guide de débogage

### Métriques de succès

- **Taux de succès API** : > 95%
- **Temps de réponse moyen** : < 2 secondes
- **Cache hit rate** : > 30% après 100 requêtes
- **Taux de crash** : 0%
- **Score de personnalité KITT** : Subjectif mais doit "sonner comme KITT"

---

**Version** : 1.0  
**Date** : 1 novembre 2025  
**Status** : Ready for testing

