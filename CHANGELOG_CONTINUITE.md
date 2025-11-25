# Changelog - Amélioration Continuité Conversationnelle

## Version - 2025-01-XX

### Améliorations implémentées

#### 1. Fenêtre d'historique augmentée (10 → 20)

**Avant**:
- 10 dernières conversations chargées
- Environ 5 aller-retours de contexte

**Après**:
- 20 dernières conversations chargées
- Environ 10 aller-retours de contexte
- Meilleure continuité dans les sessions longues

**Fichiers modifiés**:
- `KittAIService.kt`:
  - `CONTEXT_WINDOW_SIZE`: 10 → 20
  - Chargement historique: `limit = 10` → `limit = CONTEXT_WINDOW_SIZE`
  - Limite historique: `size > 10` → `size > CONTEXT_WINDOW_SIZE`

**Impact**:
- ✅ Plus de contexte immédiat (2x)
- ⚠️ Plus de tokens envoyés à l'IA (~4000-10000 tokens au lieu de ~2000-5000)
- ⚠️ Légèrement plus lent (mais négligeable)

#### 2. Persistance sessionId entre redémarrages

**Avant**:
- Nouveau `sessionId` à chaque redémarrage de l'app
- Conversations non groupées entre sessions

**Après**:
- `sessionId` persistant dans `SharedPreferences`
- Réutilisation si inactivité < 24h
- Nouveau `sessionId` si inactivité > 24h

**Fichiers modifiés**:
- `KittAIService.kt`:
  - Initialisation `sessionId` avec logique de persistance
  - Mise à jour `last_activity_time` après chaque message
  - Réutilisation sessionId existant si < 24h

**Impact**:
- ✅ Continuité entre redémarrages
- ✅ Meilleur groupement des conversations
- ✅ Historique contextuel par session

#### 3. Historique contextuel par session

**Avant**:
- Chargement des 10 dernières conversations globales
- Mélange de conversations de différentes sessions

**Après**:
- Priorité aux conversations de la session actuelle
- Fallback vers conversations globales si session vide
- Historique plus pertinent et cohérent

**Fichiers modifiés**:
- `KittAIService.kt`:
  - Chargement prioritaire: `getConversationsBySession(sessionId)`
  - Fallback: `getLastConversations()` si session vide

**Impact**:
- ✅ Historique plus pertinent (même session)
- ✅ Moins de bruit des sessions précédentes
- ✅ Meilleure continuité conversationnelle

## Détails techniques

### sessionId persistant

**Logique**:
```kotlin
// Récupérer sessionId existant ou en créer un nouveau
val savedSessionId = sharedPreferences.getString("current_session_id", null)
val lastActivityTime = sharedPreferences.getLong("last_activity_time", 0L)
val currentTime = System.currentTimeMillis()

// Si sessionId existe et inactivité < 24h, réutiliser
if (savedSessionId != null && (currentTime - lastActivityTime) < 24 * 3600 * 1000) {
    savedSessionId
} else {
    // Nouveau sessionId si inexistant ou inactivité > 24h
    UUID.randomUUID().toString()
}
```

**Mise à jour activité**:
```kotlin
// Après chaque message
sharedPreferences.edit()
    .putLong("last_activity_time", System.currentTimeMillis())
    .apply()
```

### Historique contextuel

**Logique**:
```kotlin
// Prioriser conversations de la session actuelle
val sessionConversations = conversationDao.getConversationsBySession(sessionId)
val recentConversations = if (sessionConversations.isNotEmpty()) {
    sessionConversations.sortedByDescending { it.timestamp }.take(CONTEXT_WINDOW_SIZE)
} else {
    // Fallback vers conversations globales
    conversationDao.getLastConversations(limit = CONTEXT_WINDOW_SIZE)
}
```

## Tests recommandés

### Test 1: Fenêtre historique augmentée

**Étapes**:
1. Créer 25 conversations dans une session
2. Envoyer un message
3. Vérifier les logs: `adb logcat -s KittAIService | Select-String "Loaded.*conversations"`
4. Vérifier que 20 conversations sont chargées

**Résultat attendu**:
- 20 conversations chargées (pas 10)
- Historique plus long disponible

### Test 2: Persistance sessionId

**Étapes**:
1. Envoyer un message dans ChatAI
2. Noter le sessionId dans les logs: `adb logcat -s KittAIService | Select-String "sessionId"`
3. Fermer complètement l'app (force stop)
4. Rouvrir l'app
5. Envoyer un autre message
6. Vérifier que le même sessionId est réutilisé

**Résultat attendu**:
- Même sessionId réutilisé
- Log: "Réutilisation sessionId existant"

### Test 3: Nouveau sessionId après inactivité

**Étapes**:
1. Modifier `last_activity_time` dans SharedPreferences (simuler inactivité > 24h)
2. Redémarrer l'app
3. Vérifier qu'un nouveau sessionId est créé

**Résultat attendu**:
- Nouveau sessionId créé
- Log: "Nouveau sessionId créé"

### Test 4: Historique contextuel par session

**Étapes**:
1. Créer 5 conversations dans session A
2. Redémarrer l'app (nouveau sessionId)
3. Créer 3 conversations dans session B
4. Vérifier que l'historique charge seulement les 3 conversations de session B

**Résultat attendu**:
- Historique contient seulement conversations session B
- Pas de mélange avec session A

## Impact sur performance

### Tokens envoyés

**Avant** (10 conversations):
- ~2000-5000 tokens par requête

**Après** (20 conversations):
- ~4000-10000 tokens par requête

**Impact**:
- ⚠️ 2x plus de tokens (coût si API payante)
- ✅ Meilleure continuité
- ⚠️ Légèrement plus lent (négligeable, ~50-100ms)

### Mémoire

**Avant**:
- 10 conversations en mémoire = ~50-100 KB

**Après**:
- 20 conversations en mémoire = ~100-200 KB

**Impact**:
- ✅ Négligeable (200 KB est très faible)

## Recommandations futures

### Option 1: Fenêtre dynamique

Adapter la fenêtre selon le modèle:
- Petits modèles (qwen3): 20 conversations
- Grands modèles (llama3.2): 30 conversations

### Option 2: Historique intelligent

Charger seulement les conversations pertinentes (via RAG) au lieu de toujours les 20 dernières.

### Option 3: Compression historique

Compresser l'historique ancien pour garder plus de contexte sans augmenter les tokens.

## Notes

- La fenêtre de 20 conversations est un bon compromis entre continuité et performance
- Le sessionId persistant améliore significativement la continuité entre redémarrages
- L'historique contextuel par session réduit le bruit des sessions précédentes

