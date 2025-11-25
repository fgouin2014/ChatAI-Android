# Guide RAG (Retrieval Augmented Generation)

## Qu'est-ce que RAG?

RAG (Retrieval Augmented Generation) est une architecture qui améliore les réponses de l'IA en récupérant des informations pertinentes depuis l'historique des conversations avant de générer une réponse.

### Comment ça marche?

1. **Embeddings** : Chaque conversation est convertie en un vecteur numérique (embedding) qui capture le sens sémantique
2. **Recherche** : Quand vous posez une question, le système cherche les conversations similaires dans l'historique
3. **Contexte** : Les conversations trouvées sont ajoutées au prompt de l'IA comme contexte
4. **Réponse améliorée** : L'IA génère une réponse en utilisant ce contexte, ce qui améliore la pertinence et la cohérence

## Prérequis

### Ollama Local

RAG nécessite Ollama local car les embeddings sont générés via l'API `/api/embeddings` qui n'est pas encore disponible sur Ollama Cloud.

1. **Installer Ollama** sur votre PC ou device Android
2. **Télécharger le modèle d'embedding** :
   ```bash
   ollama pull nomic-embed-text
   ```
   Ou un autre modèle comme `mxbai-embed-large`

3. **Démarrer Ollama** :
   ```bash
   ollama serve
   ```

### Configuration dans ChatAI

1. Ouvrir la webapp ChatAI (port 8080)
2. Aller dans l'onglet **Configuration**
3. Dans **Local**, vérifier que l'URL du serveur Ollama est correcte (par défaut: `http://localhost:11434`)
4. Dans **RAG**, activer l'option **RAG activé**
5. Sélectionner le **Modèle d'embedding** (`nomic-embed-text` ou autre)

## Comment activer RAG

### Via la Webapp

1. Ouvrir ChatAI
2. Aller dans **Configuration** → **Local**
3. Activer **RAG activé** (switch)
4. Sélectionner le **Modèle d'embedding**
5. Cliquer sur **Sauvegarder Configuration**

### Vérification

Une fois activé, chaque nouvelle conversation générera automatiquement un embedding en arrière-plan. Vous pouvez vérifier dans les logs:

```
[Embedding] Generated and saved - 768 dimensions
```

## Exemples d'utilisation

### Exemple 1 : Contexte conversationnel

**Sans RAG:**
- Vous: "Quelle était ma question sur Python?"
- IA: "Je ne peux pas accéder à l'historique de nos conversations précédentes."

**Avec RAG:**
- Vous: "Quelle était ma question sur Python?"
- IA: "Vous aviez demandé comment créer une liste en Python. Je vous avais expliqué la syntaxe `[1, 2, 3]`..."

### Exemple 2 : Continuité thématique

**Sans RAG:**
- Vous: "Peux-tu me rappeler ce que j'ai dit sur les bases de données?"
- IA: "Je n'ai pas accès à l'historique."

**Avec RAG:**
- Vous: "Peux-tu me rappeler ce que j'ai dit sur les bases de données?"
- IA: "Vous aviez mentionné que vous utilisiez PostgreSQL et que vous vouliez migrer vers MongoDB..."

## Troubleshooting

### Erreur: "Embedding service not available"

**Cause** : Ollama local n'est pas accessible ou le modèle d'embedding n'est pas installé.

**Solution** :
1. Vérifier que Ollama est démarré: `ollama serve`
2. Vérifier que le modèle est installé: `ollama list`
3. Installer le modèle si nécessaire: `ollama pull nomic-embed-text`
4. Vérifier l'URL dans la configuration (par défaut: `http://localhost:11434`)

### Erreur: "Ollama Cloud ne supporte pas embeddings"

**Cause** : RAG nécessite Ollama local, pas Cloud.

**Solution** :
1. Désactiver "Ollama Cloud" dans la configuration
2. Configurer Ollama local
3. Réactiver RAG

### Les embeddings ne se génèrent pas

**Cause** : Plusieurs raisons possibles:
- Ollama local non accessible
- Modèle d'embedding non installé
- Timeout réseau
- RAG désactivé

**Solution** :
1. Vérifier les logs: `adb logcat -s EmbeddingService`
2. Tester la connexion Ollama: `curl http://localhost:11434/api/tags`
3. Vérifier la configuration RAG dans la webapp
4. Essayer de régénérer les embeddings manuellement (voir section Migration)

## Migration embeddings anciennes conversations

Si vous avez des conversations existantes sans embeddings, vous pouvez les migrer:

1. Ouvrir **Historique des conversations** dans ChatAI
2. Cliquer sur **EMBEDDINGS**
3. Confirmer la migration
4. Attendre la fin (peut prendre plusieurs minutes selon le nombre de conversations)

La migration traite les conversations par batches de 10 pour éviter les timeouts.

## Modèles d'embeddings supportés

### nomic-embed-text (par défaut)
- Dimensions: 768
- Taille: ~140 MB
- Qualité: Excellente pour la plupart des cas
- Langue: Multilingue

### mxbai-embed-large
- Dimensions: 1024
- Taille: ~334 MB
- Qualité: Très bonne pour les textes longs
- Langue: Multilingue

Pour utiliser un autre modèle:
```bash
ollama pull mxbai-embed-large
```

Puis sélectionner ce modèle dans la configuration ChatAI.

## Performance

### Génération d'embedding
- Temps moyen: 200-500ms par conversation
- Dépend de: modèle utilisé, longueur du texte, puissance CPU

### Recherche sémantique
- Temps moyen: 10-50ms pour 100 conversations
- Optimisé avec cosine similarity

### Impact sur réponses IA
- Temps supplémentaire: 300-800ms (génération embedding + recherche)
- Amélioration qualité: Significative pour contextes conversationnels

## Limitations

1. **Ollama Cloud** : RAG ne fonctionne pas actuellement avec Ollama Cloud (pas encore d'endpoint `/api/embeddings`)
   - ⭐ **Future-proof** : Si Ollama ajoute `/api/embeddings` à Cloud, RAG sera automatiquement disponible sans modification de code
   - Le système détecte automatiquement si Cloud supporte les embeddings
2. **Stockage** : Chaque embedding prend ~3KB (768 dimensions × 4 bytes)
3. **Temps de traitement** : La première génération d'embedding peut prendre plus de temps
4. **Similarité minimale** : Score de similarité minimum de 0.5 pour inclure une conversation dans le contexte

## Questions fréquentes

**Q: RAG ralentit-il les réponses?**
R: Oui, légèrement (300-800ms), mais la qualité des réponses s'améliore significativement.

**Q: Peut-on désactiver RAG temporairement?**
R: Oui, dans Configuration → Local → RAG activé (désactiver).

**Q: Les anciennes conversations ont-elles besoin d'embeddings?**
R: Oui, pour être trouvées par la recherche sémantique. Utilisez la migration automatique.

**Q: Combien d'espace prennent les embeddings?**
R: Environ 3KB par conversation (768 dimensions). Pour 1000 conversations: ~3MB.

## Ressources

- [Documentation Ollama Embeddings](https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings)
- [nomic-embed-text sur Hugging Face](https://huggingface.co/nomic-ai/nomic-embed-text-v1)
- [Architecture RAG dans ChatAI](ARCHITECTURE_RAG.md)

