# Guide Historique des Conversations

## Vue d'ensemble

L'historique des conversations dans ChatAI permet de consulter, rechercher et exporter toutes vos interactions avec KITT et GLaDOS.

## Accès à l'historique

### Depuis ChatAI

1. Ouvrir ChatAI
2. Aller dans **Historique** (onglet navigation)
3. Afficher les conversations, statistiques et options d'export

### Depuis l'application Android

1. Ouvrir ChatAI
2. Menu principal → **Historique des conversations**
3. Ou via **Historique** dans la webapp

## Fonctionnalités

### 1. Recherche

#### Recherche textuelle

- Utiliser la barre de recherche en haut de l'historique
- Recherche en temps réel (debounce 300ms)
- Recherche dans: messages utilisateur, réponses IA, métadonnées
- Highlight automatique des termes recherchés

**Exemples**:
- Rechercher "Python" → Affiche toutes les conversations mentionnant Python
- Rechercher "erreur" → Affiche les conversations avec des erreurs

#### Filtres

**Par personnalité**:
- Toutes
- KITT
- GLaDOS
- casual
- friendly
- professional
- creative
- funny

**Par plateforme**:
- Toutes
- vocal (KITT interface)
- webapp (ChatAI web)
- web (importé)

**Filtres combinés**:
- Recherche + Personnalité + Plateforme
- Résultats filtrés en temps réel

### 2. Statistiques

Les statistiques affichent:

- **Total conversations** : Nombre total de conversations
- **KITT / GLaDOS** : Répartition par personnalité
- **Temps moyen** : Temps de réponse moyen (ms)
- **Temps total** : Somme de tous les temps de réponse
- **Longueur moyenne** : Nombre moyen de caractères par conversation
- **API principale** : API la plus utilisée
- **Top Personalities** : Top 3 personnalités les plus utilisées
- **Top APIs** : Top 3 APIs les plus utilisées
- **Première / Dernière** : Dates de première et dernière conversation

### 3. Export

#### Export Logcat

- Exporter toutes les conversations dans logcat
- Commande: `adb logcat -s CONV_DEBUG_EXPORT`
- Ou sauvegarder: `adb logcat -s CONV_DEBUG_EXPORT > export.txt`
- Format: Logcat text avec structure lisible

#### Export JSON

- Exporter toutes les conversations en JSON
- Fichier sauvegardé dans: `/storage/emulated/0/Android/data/com.chatai/files/`
- Commande pour récupérer: `adb pull /path/to/file.json .`
- Format: JSON array avec tous les champs (id, timestamp, messages, thinking, etc.)

#### Export HTML

- Exporter toutes les conversations en HTML
- Style KITT (thème sombre, couleurs cohérentes)
- Partageable via Intent (email, messages, etc.)
- Inclut: statistiques, conversations, thinking traces
- Fichier sauvegardé dans: `/storage/emulated/0/Android/data/com.chatai/files/`

**Pour partager**:
1. Cliquer sur **Export HTML**
2. Attendre la génération
3. Cliquer sur **Partager**
4. Choisir l'application (Gmail, Messages, etc.)

#### Export par ID

- Exporter une conversation spécifique par UUID ou DB row ID
- Format: Logcat détaillé avec structure lisible
- Utile pour debugging ou partage de conversation précise

### 4. Import

#### Import JSON

- Importer des conversations depuis un fichier JSON
- Format: JSON array avec champs ConversationEntity
- Fichier doit être dans: `/storage/emulated/0/Android/data/com.chatai/files/`
- UUIDs générés automatiquement si absents

**Format JSON attendu**:
```json
[
  {
    "conversationId": "uuid-optional",
    "timestamp": 1234567890,
    "userMessage": "Question?",
    "aiResponse": "Réponse",
    "thinkingTrace": "Raisonnement...",
    "personality": "KITT",
    "apiUsed": "ollama",
    "responseTimeMs": 500,
    "platform": "webapp"
  }
]
```

### 5. Migration Embeddings

- Générer des embeddings pour les anciennes conversations
- Nécessite Ollama local et modèle d'embedding installé
- Traitement par batches de 10 conversations
- Progress bar en temps réel
- Voir [GUIDE_RAG.md](GUIDE_RAG.md) pour plus de détails

## Synchronisation Webapp ↔ Room DB

### Comment ça marche?

1. **Sauvegarde automatique** : Chaque message webapp est sauvegardé dans Room DB
2. **Chargement automatique** : Au démarrage, fusion localStorage + Room DB
3. **Détection doublons** : Évite les duplications (timestamp ±5s)

### Détails techniques

- **Webapp → Room DB** : Sauvegarde immédiate après chaque réponse AI
- **Room DB → Webapp** : Chargement au démarrage + fusion avec localStorage
- **Plateforme** : Les messages webapp sont marqués `platform="webapp"`

## Utilisation avancée

### Recherche avec regex

La recherche est une recherche textuelle simple (pas de regex). Pour des recherches complexes, utilisez l'export JSON et un outil externe.

### Export personnalisé

1. Exporter en JSON
2. Utiliser un script Python/Node.js pour filtrer/transformer
3. Réimporter si nécessaire

**Exemple Python**:
```python
import json

with open('export.json', 'r') as f:
    conversations = json.load(f)

# Filtrer par date
filtered = [c for c in conversations if c['timestamp'] > 1234567890]

# Sauvegarder
with open('filtered.json', 'w') as f:
    json.dump(filtered, f, indent=2)
```

### Statistiques personnalisées

1. Exporter en JSON
2. Analyser avec un outil de votre choix (Excel, Python, etc.)

**Exemple Python**:
```python
import json
from collections import Counter

with open('export.json', 'r') as f:
    conversations = json.load(f)

# Top personnalités
personalities = Counter(c['personality'] for c in conversations)
print(personalities.most_common(5))

# Temps total
total_time = sum(c['responseTimeMs'] for c in conversations)
print(f"Temps total: {total_time/1000}s")
```

## Troubleshooting

### L'historique ne charge pas

**Solution**:
1. Vérifier les logs: `adb logcat -s ConversationHistory`
2. Vérifier que Room DB est accessible
3. Redémarrer l'application

### Recherche ne fonctionne pas

**Solution**:
1. Vérifier que les conversations existent dans Room DB
2. Essayer de rafraîchir (pull to refresh ou bouton refresh)
3. Vérifier les logs: `adb logcat -s ConversationHistory`

### Export échoue

**Solution**:
1. Vérifier les permissions de stockage
2. Vérifier l'espace disponible
3. Vérifier les logs: `adb logcat -s CONV_JSON_EXPORT` ou `CONV_HTML_EXPORT`

### Doublons dans l'historique

**Solution**:
1. Les doublons sont normalement évités par la détection (timestamp ±5s)
2. Si problème persiste, vérifier les logs: `adb logcat -s ConversationHistory`
3. Réimporter depuis export JSON propre

## Questions fréquentes

**Q: Où sont stockées les conversations?**
R: Dans Room DB (SQLite) sur le device Android. Localisation: `/data/data/com.chatai/databases/chat_ai_database`

**Q: Peut-on sauvegarder l'historique hors du device?**
R: Oui, via export JSON/HTML. Les fichiers sont dans `/storage/emulated/0/Android/data/com.chatai/files/`

**Q: L'historique est-il synchronisé entre devices?**
R: Non, chaque device a son propre historique. Utilisez export/import pour transférer.

**Q: Peut-on supprimer des conversations spécifiques?**
R: Actuellement, seulement "Tout effacer". Pour supprimer spécifique, export → filtrer → réimporter.

**Q: Les conversations sont-elles cryptées?**
R: Non, stockées en clair dans Room DB. Pour sécurité, utilisez chiffrement au niveau Android.

## Ressources

- [Architecture Room DB dans ChatAI](ARCHITECTURE_RAG.md)
- [Guide RAG](GUIDE_RAG.md)
- [Documentation Room](https://developer.android.com/training/data-storage/room)

