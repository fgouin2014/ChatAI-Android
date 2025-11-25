# Guide de Test - Améliorations Continuité Conversationnelle

## Tests à effectuer

### Test 1: Fenêtre historique augmentée (10 → 20)

**Objectif**: Vérifier que 20 conversations sont chargées au lieu de 10

**Étapes**:
1. Créer au moins 25 conversations dans ChatAI (via webapp ou KITT)
2. Redémarrer l'app complètement (force stop)
3. Ouvrir ChatAI et envoyer un nouveau message
4. Vérifier les logs: `adb logcat -s KittAIService | Select-String "Loaded.*conversations"`

**Résultat attendu**:
```
Loaded 20 conversations from database (sessionId: ...)
```

**Vérification manuelle**:
- Ouvrir **Historique des conversations** dans l'app
- Vérifier que les 20 dernières conversations sont visibles
- Vérifier que l'IA peut référencer des conversations plus anciennes

---

### Test 2: Persistance sessionId entre redémarrages

**Objectif**: Vérifier que le même sessionId est réutilisé après redémarrage

**Étapes**:
1. Envoyer un message dans ChatAI
2. Noter le sessionId dans les logs: `adb logcat -s KittAIService | Select-String "sessionId"`
3. Fermer complètement l'app (force stop)
4. Attendre 5 secondes (pas plus de 24h)
5. Rouvrir l'app
6. Envoyer un autre message
7. Vérifier que le même sessionId est réutilisé

**Résultat attendu**:
```
Réutilisation sessionId existant: <sessionId>
```

**Vérification**:
- Les deux messages doivent avoir le même `sessionId` dans Room DB
- L'historique doit charger les conversations de cette session

---

### Test 3: Nouveau sessionId après inactivité > 24h

**Objectif**: Vérifier qu'un nouveau sessionId est créé après 24h d'inactivité

**Étapes**:
1. Modifier manuellement `last_activity_time` dans SharedPreferences pour simuler inactivité > 24h:
   ```bash
   adb shell "run-as com.chatai cat /data/data/com.chatai/shared_prefs/chatai_ai_config.xml"
   ```
2. Modifier la valeur `last_activity_time` à une date > 24h dans le passé
3. Redémarrer l'app
4. Envoyer un message
5. Vérifier qu'un nouveau sessionId est créé

**Résultat attendu**:
```
Nouveau sessionId créé: <nouveau-sessionId>
```

**Alternative (plus simple)**:
- Attendre 24h+ entre deux utilisations (peu pratique)
- Ou modifier le code temporairement pour réduire le délai à 1 minute pour test

---

### Test 4: Historique contextuel par session

**Objectif**: Vérifier que l'historique charge prioritairement les conversations de la session actuelle

**Étapes**:
1. Créer 5 conversations dans session A (noter le sessionId)
2. Redémarrer l'app (nouveau sessionId = session B)
3. Créer 3 conversations dans session B
4. Redémarrer l'app à nouveau
5. Envoyer un message dans session B
6. Vérifier les logs: `adb logcat -s KittAIService | Select-String "Loaded.*conversations"`

**Résultat attendu**:
- Historique contient seulement les 3 conversations de session B
- Pas de mélange avec session A

**Vérification manuelle**:
- Ouvrir **Historique des conversations**
- Filtrer par sessionId (si possible) ou vérifier les timestamps
- Vérifier que seulement les conversations récentes de la session actuelle sont chargées

---

### Test 5: Continuité conversationnelle fonctionnelle

**Objectif**: Vérifier que l'IA peut référencer des conversations précédentes

**Étapes**:
1. Session 1:
   - Message 1: "Je m'appelle John"
   - Vérifier la réponse
2. Message 2: "Quel est mon nom?"
   - Vérifier que l'IA répond "John"
3. Redémarrer l'app (si sessionId persiste)
4. Message 3: "Comment je m'appelle?"
   - Vérifier que l'IA répond toujours "John"

**Résultat attendu**:
- L'IA se souvient du nom "John" même après redémarrage
- Références aux conversations précédentes fonctionnent

---

### Test 6: Performance avec 20 conversations

**Objectif**: Vérifier que la performance reste acceptable avec 20 conversations

**Étapes**:
1. Créer 20+ conversations
2. Envoyer un nouveau message
3. Mesurer le temps de réponse: `adb logcat -s KittAIService | Select-String "responseTime"`
4. Vérifier qu'il n'y a pas de freeze UI

**Résultat attendu**:
- Temps de réponse < 5 secondes (selon API)
- Pas de freeze UI
- Tokens envoyés: ~4000-10000 (au lieu de ~2000-5000)

**Vérification**:
- Logs montrent le nombre de tokens dans la requête
- Réponse arrive dans un délai raisonnable

---

## Checklist de tests

### Tests essentiels (avant release)
- [ ] Test 1: Fenêtre historique augmentée
- [ ] Test 2: Persistance sessionId
- [ ] Test 5: Continuité fonctionnelle

### Tests approfondis (optionnel)
- [ ] Test 3: Nouveau sessionId après inactivité
- [ ] Test 4: Historique contextuel par session
- [ ] Test 6: Performance

---

## Commandes utiles

### Vérifier sessionId actuel
```bash
adb logcat -s KittAIService | Select-String "sessionId"
```

### Vérifier nombre de conversations chargées
```bash
adb logcat -s KittAIService | Select-String "Loaded.*conversations"
```

### Vérifier temps de réponse
```bash
adb logcat -s KittAIService | Select-String "responseTime|Time:"
```

### Vérifier SharedPreferences
```bash
adb shell "run-as com.chatai cat /data/data/com.chatai/shared_prefs/chatai_ai_config.xml"
```

### Vérifier Room DB (sessionId)
```bash
adb shell "run-as com.chatai sqlite3 /data/data/com.chatai/databases/chatai_database 'SELECT sessionId, COUNT(*) FROM conversations GROUP BY sessionId;'"
```

---

## Problèmes connus à surveiller

### Problème 1: SessionId non persistant
**Symptôme**: Nouveau sessionId à chaque redémarrage
**Solution**: Vérifier que `SharedPreferences` est bien sauvegardé

### Problème 2: Historique vide après redémarrage
**Symptôme**: Aucune conversation chargée
**Solution**: Vérifier que `getConversationsBySession()` fonctionne

### Problème 3: Performance dégradée
**Symptôme**: Réponses très lentes avec 20 conversations
**Solution**: Réduire `CONTEXT_WINDOW_SIZE` à 15 si nécessaire

---

## Résultats attendus

### Succès
- ✅ 20 conversations chargées
- ✅ SessionId persistant < 24h
- ✅ Nouveau sessionId > 24h
- ✅ Historique contextuel par session
- ✅ Continuité fonctionnelle
- ✅ Performance acceptable

### Échecs à investiguer
- ❌ Seulement 10 conversations chargées
- ❌ Nouveau sessionId à chaque redémarrage
- ❌ Historique mélange sessions
- ❌ Performance dégradée

