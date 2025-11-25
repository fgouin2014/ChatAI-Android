# Tests Recommandés - Éléments Manquants

## Tests à effectuer après implémentation

### Phase 1: Synchronisation Webapp ↔ Room DB

#### Test 1.1: Sauvegarde messages webapp
**Objectif**: Vérifier que les messages webapp sont sauvegardés dans Room DB

**Étapes**:
1. Ouvrir ChatAI webapp (port 8080)
2. Envoyer un message: "Test sauvegarde webapp"
3. Attendre la réponse de l'IA
4. Ouvrir **Historique des conversations** dans l'app Android
5. Vérifier que la conversation apparaît avec `platform="webapp"`

**Résultat attendu**:
- Conversation visible dans l'historique
- Platform = "webapp"
- User message et AI response présents

**Logs à vérifier**:
```bash
adb logcat -s ConversationHistoryHelper | Select-String "saveWebappConversation"
```

#### Test 1.2: Chargement historique au démarrage
**Objectif**: Vérifier que l'historique Room DB est chargé au démarrage webapp

**Étapes**:
1. Créer plusieurs conversations dans la webapp
2. Fermer complètement l'app (force stop)
3. Rouvrir l'app et la webapp
4. Vérifier que les conversations précédentes sont visibles

**Résultat attendu**:
- Conversations précédentes affichées dans le chat
- Pas de doublons
- Ordre chronologique correct

**Logs à vérifier**:
```bash
adb logcat -s ChatMessaging | Select-String "Historique chargé"
```

#### Test 1.3: Détection doublons
**Objectif**: Vérifier que les doublons sont détectés et évités

**Étapes**:
1. Envoyer un message dans la webapp
2. Attendre la réponse
3. Recharger la page webapp (F5 ou refresh)
4. Vérifier qu'il n'y a pas de doublons

**Résultat attendu**:
- Pas de messages dupliqués
- Fusion localStorage + Room DB sans doublons

---

### Phase 3: Recherche améliorée ConversationHistoryActivity

#### Test 3.1: Recherche textuelle
**Objectif**: Vérifier que la recherche fonctionne

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Taper "Python" dans la barre de recherche
3. Vérifier les résultats

**Résultat attendu**:
- Conversations contenant "Python" affichées
- Highlight des termes recherchés (rouge)
- Résultats en temps réel (debounce 300ms)

#### Test 3.2: Filtres personnalité
**Objectif**: Vérifier les filtres par personnalité

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Sélectionner "KITT" dans le spinner personnalité
3. Vérifier les résultats

**Résultat attendu**:
- Seulement conversations avec personality="KITT"
- Filtre appliqué immédiatement

#### Test 3.3: Filtres combinés
**Objectif**: Vérifier recherche + filtres combinés

**Étapes**:
1. Rechercher "Python"
2. Sélectionner "KITT" dans personnalité
3. Sélectionner "webapp" dans plateforme
4. Vérifier les résultats

**Résultat attendu**:
- Conversations avec "Python" + KITT + webapp uniquement
- Filtres combinés correctement

---

### Phase 4: Export HTML et statistiques

#### Test 4.1: Statistiques améliorées
**Objectif**: Vérifier l'affichage des statistiques

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Vérifier la section "Statistiques"

**Résultat attendu**:
- Total conversations
- Temps total (en secondes)
- Longueur moyenne (chars)
- Top 3 personalities
- Top 3 APIs
- Première et dernière date

#### Test 4.2: Export HTML
**Objectif**: Vérifier l'export HTML

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Appui long sur "EXPORT" → "Exporter vers fichier HTML"
3. Attendre la génération
4. Cliquer sur "Partager"
5. Vérifier le fichier HTML

**Résultat attendu**:
- Fichier HTML généré avec style KITT
- Toutes les conversations incluses
- Thinking traces présents si disponibles
- Partage via Intent fonctionne

**Fichier généré**:
```
/storage/emulated/0/Android/data/com.chatai/files/chatai_conversations_*.html
```

#### Test 4.3: Partage HTML
**Objectif**: Vérifier le partage via Intent

**Étapes**:
1. Exporter en HTML
2. Cliquer sur "Partager"
3. Choisir Gmail ou Messages
4. Vérifier que le fichier est attaché

**Résultat attendu**:
- Intent de partage s'ouvre
- Fichier HTML attaché
- Pas d'erreur FileProvider

---

### Phase 5: Migration embeddings

#### Test 5.1: Migration embeddings
**Objectif**: Vérifier la migration des embeddings

**Prérequis**:
- Ollama local accessible
- Modèle `nomic-embed-text` installé
- Conversations sans embeddings dans Room DB

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Cliquer sur "EMBEDDINGS"
3. Confirmer la migration
4. Observer la progress bar
5. Attendre la fin

**Résultat attendu**:
- Progress bar mise à jour en temps réel
- Embeddings générés pour chaque conversation
- Message de succès à la fin
- Embeddings visibles dans Room DB

**Logs à vérifier**:
```bash
adb logcat -s EmbeddingMigrationService | Select-String "migrée"
```

#### Test 5.2: Vérification embeddings
**Objectif**: Vérifier que les embeddings sont sauvegardés

**Étapes**:
1. Après migration, vérifier Room DB
2. Exporter une conversation en JSON
3. Vérifier le champ `embeddingsJson`

**Résultat attendu**:
- `embeddingsJson` non null
- Format JSON array valide
- 768 dimensions (pour nomic-embed-text)

---

## Tests de régression

### Test R1: Historique existant
**Objectif**: Vérifier que l'historique existant fonctionne toujours

**Étapes**:
1. Ouvrir **Historique des conversations**
2. Vérifier que les anciennes conversations s'affichent
3. Tester recherche, filtres, export

**Résultat attendu**:
- Anciennes conversations visibles
- Pas de régression

### Test R2: Webapp fonctionne toujours
**Objectif**: Vérifier que la webapp fonctionne après modifications

**Étapes**:
1. Ouvrir webapp ChatAI (chargée depuis assets)
2. Envoyer un message
3. Vérifier que la réponse arrive
4. Vérifier que l'historique se charge

**Résultat attendu**:
- Webapp fonctionne normalement
- Pas de régression

---

## Tests de performance

### Test P1: Chargement historique
**Objectif**: Vérifier le temps de chargement

**Étapes**:
1. Avoir 100+ conversations dans Room DB
2. Ouvrir **Historique des conversations**
3. Mesurer le temps de chargement

**Résultat attendu**:
- Chargement < 2 secondes
- Pas de freeze UI

### Test P2: Recherche performance
**Objectif**: Vérifier la performance de la recherche

**Étapes**:
1. Avoir 100+ conversations
2. Rechercher un terme
3. Mesurer le temps de recherche

**Résultat attendu**:
- Recherche < 500ms
- Pas de freeze UI

---

## Tests d'intégration

### Test I1: Synchronisation complète
**Objectif**: Vérifier la synchronisation end-to-end

**Étapes**:
1. Envoyer message webapp
2. Vérifier sauvegarde Room DB
3. Vérifier affichage dans historique
4. Vérifier recherche/filtres
5. Vérifier export

**Résultat attendu**:
- Toute la chaîne fonctionne
- Pas d'erreur à chaque étape

### Test I2: RAG + Historique
**Objectif**: Vérifier que RAG utilise l'historique correctement

**Prérequis**:
- Ollama local + modèle embedding
- Conversations avec embeddings

**Étapes**:
1. Poser une question similaire à une conversation passée
2. Vérifier que RAG trouve la conversation
3. Vérifier que la réponse utilise le contexte

**Résultat attendu**:
- RAG trouve conversations similaires
- Contexte injecté dans la réponse
- Réponse améliorée avec contexte

---

## Checklist de tests

### Avant chaque release

- [ ] Test 1.1: Sauvegarde messages webapp
- [ ] Test 1.2: Chargement historique
- [ ] Test 3.1: Recherche textuelle
- [ ] Test 3.2: Filtres personnalité
- [ ] Test 4.1: Statistiques améliorées
- [ ] Test 4.2: Export HTML
- [ ] Test R1: Historique existant
- [ ] Test R2: Webapp fonctionne

### Tests approfondis (mensuel)

- [ ] Test 1.3: Détection doublons
- [ ] Test 3.3: Filtres combinés
- [ ] Test 4.3: Partage HTML
- [ ] Test 5.1: Migration embeddings
- [ ] Test 5.2: Vérification embeddings
- [ ] Test P1: Chargement historique
- [ ] Test P2: Recherche performance
- [ ] Test I1: Synchronisation complète
- [ ] Test I2: RAG + Historique

---

## Commandes de test rapide

### Vérifier sauvegarde webapp
```bash
adb logcat -s ConversationHistoryHelper | Select-String "saveWebappConversation"
```

### Vérifier chargement historique
```bash
adb logcat -s ChatMessaging | Select-String "Historique chargé"
```

### Vérifier recherche
```bash
adb logcat -s ConversationHistory | Select-String "performSearch"
```

### Vérifier migration embeddings
```bash
adb logcat -s EmbeddingMigrationService | Select-String "migrée"
```

### Vérifier export HTML
```bash
adb logcat -s CONV_HTML_EXPORT
```

---

## Problèmes connus à tester

### Problème 1: Doublons
**Test**: Recharger la webapp plusieurs fois et vérifier qu'il n'y a pas de doublons

### Problème 2: FileProvider
**Test**: Partager HTML et vérifier que le fichier est accessible

### Problème 3: RAG timeout
**Test**: Migration embeddings avec beaucoup de conversations et vérifier qu'il n'y a pas de timeout

---

## Résultats attendus

### Succès
- ✅ Tous les tests passent
- ✅ Pas de régression
- ✅ Performance acceptable

### Échecs à investiguer
- ❌ Sauvegarde webapp échoue
- ❌ Recherche ne fonctionne pas
- ❌ Export HTML échoue
- ❌ Migration embeddings timeout
- ❌ Doublons dans l'historique

