# 💾 Backup/Restore Complet - Implémentation

**Date**: 2025-11-27  
**Objectif**: Système complet de backup/restore pour conversations + configuration + clés API

---

## 📋 BESOIN

L'utilisateur veut:
- ✅ **Exporter** toutes les données (conversations + config + clés API)
- ✅ **Restaurer** toutes les données depuis un backup
- ✅ **Pas juste garder le texte pour la postérité** - restaurer fonctionnellement

---

## 🎯 SOLUTION PROPOSÉE

### Format de Backup Unique

Un seul fichier JSON/ZIP contenant:
1. **Conversations** (toutes, format Room DB)
2. **Configuration** (SharedPreferences + SecureConfig)
3. **Clés API** (KeyringManager, optionnel, chiffré)

**Format**:
```json
{
  "version": "1.0",
  "timestamp": 1234567890,
  "app_version": "3.0",
  "conversations": [...],
  "configuration": {
    "shared_preferences": {...},
    "secure_config": {...}
  },
  "api_keys": {
    "encrypted": true,
    "keys": {...}
  }
}
```

---

## 📁 EMPLACEMENT DES BACKUPS

### Option A: Storage Public (Recommandé) ✅

**Chemin**: `/storage/emulated/0/ChatAI-Files/backups/`

**Avantages**:
- ✅ Accessible via explorateur de fichiers Android standard
- ✅ Facile à copier vers PC/USB
- ✅ Facile à partager
- ✅ Visible dans l'app pour restaurer

**Implémentation**:
- Créer répertoire `backups/` au démarrage
- Sauvegarder backups avec timestamp: `chatai_backup_20251127_143025.json`
- Afficher liste des backups disponibles pour restore

---

### Option B: Android/data (Actuel) ⚠️

**Chemin**: `/storage/emulated/0/Android/data/com.chatai/files/backups/`

**Avantages**:
- ✅ Déjà utilisé actuellement
- ✅ Effacé avec désinstallation app

**Inconvénients**:
- ⚠️ Pas accessible facilement sans ADB/root
- ⚠️ Difficile pour récupération manuelle

---

## 🔧 IMPLÉMENTATION

### 1. Export Complet

#### 1.1 Conversations
- Utiliser `conversationDao.getAllConversationsForExport()`
- Format existant OK (JSON array)

#### 1.2 Configuration
- **SharedPreferences**: Tous les fichiers `*.xml` dans `shared_prefs/`
- **SecureConfig**: Utiliser `SecureConfig` pour lire toutes les valeurs
- Convertir en JSON

#### 1.3 Clés API
- Utiliser `KeyringManager.exportKeys(encrypted: true)`
- Option utilisateur: Inclure ou pas (privacy)

#### 1.4 Fichier Unique
- Combiner tout dans un JSON structuré
- Ou créer ZIP avec:
  - `conversations.json`
  - `configuration.json`
  - `api_keys.json` (optionnel)
  - `backup_manifest.json`

---

### 2. Restore Complet

#### 2.1 Parser Fichier Backup
- Lire JSON/ZIP
- Vérifier version (compatibilité)

#### 2.2 Restaurer Conversations
- Utiliser `conversationDao.insertAll()`
- Gérer conflits (UUID déjà existants? Remplacer ou ignorer?)

#### 2.3 Restaurer Configuration
- Écrire dans SharedPreferences
- Utiliser `SecureConfig.saveSetting()` pour SecureConfig
- Option: Fusionner ou remplacer complètement?

#### 2.4 Restaurer Clés API
- Utiliser `KeyringManager.importKeys()`
- Vérifier chiffrement

#### 2.5 Option: Mode Fusion vs Remplacer
- **Remplacer**: Vider DB/config, restaurer tout
- **Fusionner**: Ajouter conversations, fusionner config
- **Sélectif**: Choisir ce qui est restauré

---

### 3. UI/UX

#### 3.1 Export
1. Menu Historique → "💾 Backup Complet"
2. Dialog: 
   - Inclure clés API? (checkbox)
   - Emplacement: Storage public ou Android/data
3. Progress bar pendant export
4. Confirmation avec chemin fichier
5. Option "Partager" directement

#### 3.2 Restore
1. Menu Historique → "📥 Restaurer Backup"
2. Sélecteur de fichier (choisir n'importe quel `.json` ou `.zip`)
   - Lister backups dans `ChatAI-Files/backups/`
   - Ou choisir fichier depuis explorateur
3. Dialog prévisualisation:
   - Nombre de conversations
   - Date backup
   - Options: Fusionner ou Remplacer
4. Progress bar pendant restore
5. Confirmation succès/erreurs

---

## 🔐 SÉCURITÉ

### Clés API
- ✅ Toujours chiffrées dans backup
- ✅ Option utilisateur pour inclure/exclure
- ✅ Avertissement si non chiffrées

### Validation
- ✅ Vérifier format/version backup
- ✅ Valider JSON avant restore
- ✅ Backup de sécurité avant restore (backup actuel)

---

## 📝 PLAN D'IMPLÉMENTATION

### Phase 1: Export Complet (2-3h)

1. **Créer BackupManager.kt**
   - Méthode `exportFullBackup(includeApiKeys: Boolean, usePublicStorage: Boolean)`
   - Collecter conversations (existant)
   - Collecter configuration (nouveau)
   - Collecter clés API (KeyringManager existant)
   - Écrire fichier JSON unique

2. **Modifier ConversationHistoryActivity.kt**
   - Ajouter option "💾 Backup Complet" dans menu export
   - Dialog avec options (clés API, emplacement)
   - Appeler BackupManager
   - Afficher résultat + partage

3. **Tester export complet**

---

### Phase 2: Restore Complet (2-3h)

1. **BackupManager.kt**
   - Méthode `restoreFullBackup(file: File, mode: RestoreMode)`
   - Parser JSON backup
   - Restaurer conversations (existant, améliorer)
   - Restaurer configuration (nouveau)
   - Restaurer clés API (KeyringManager existant)

2. **ConversationHistoryActivity.kt**
   - Ajouter option "📥 Restaurer Backup"
   - Sélecteur de fichier amélioré
   - Dialog prévisualisation + options
   - Appeler BackupManager
   - Afficher résultat

3. **Tester restore complet**

---

### Phase 3: Améliorations (1-2h)

1. **Backup de sécurité avant restore**
   - Créer backup automatique avant restore
   - Permet rollback si problème

2. **Liste backups disponibles**
   - Afficher backups dans `ChatAI-Files/backups/`
   - Afficher date, taille, nombre conversations

3. **Validation et erreurs**
   - Vérifier version backup
   - Messages d'erreur clairs
   - Logs détaillés

---

## 🎯 FORMAT BACKUP DÉTAILLÉ

```json
{
  "backup_version": "1.0",
  "app_version": "3.0",
  "timestamp": 1735315200000,
  "timestamp_readable": "2025-11-27 14:00:00",
  
  "metadata": {
    "conversations_count": 1250,
    "has_configuration": true,
    "has_api_keys": true,
    "api_keys_encrypted": true
  },
  
  "conversations": [
    {
      "conversationId": "uuid-...",
      "timestamp": 1234567890,
      "userMessage": "...",
      "aiResponse": "...",
      "thinkingTrace": "...",
      "personality": "KITT",
      "apiUsed": "ollama",
      "responseTimeMs": 500,
      "platform": "webapp",
      "sessionId": "..."
    }
  ],
  
  "configuration": {
    "shared_preferences": {
      "chatai_ai_config": {
        "current_mode": "cloud",
        "local_model_name": "gemma3:270m",
        "current_session_id": "...",
        ...
      },
      "chatai_settings": {
        ...
      }
    },
    "secure_config": {
      "local_server_url": "http://192.168.1.100:11434",
      ...
    }
  },
  
  "api_keys": {
    "encrypted": true,
    "keys": {
      "openai_api_key": "encrypted...",
      "ollama_cloud_api_key": "encrypted...",
      ...
    }
  }
}
```

---

## ✅ AVANTAGES

1. **Backup complet**: Tout en un fichier
2. **Restore fonctionnel**: Restaure vraiment tout (pas juste texte)
3. **Accessible**: Storage public, facile à récupérer
4. **Sécurisé**: Clés API chiffrées, option utilisateur
5. **Flexible**: Mode fusion ou remplacement

---

## ⚠️ CONSIDÉRATIONS

### Fusion vs Remplacer
- **Fusion**: Peut créer doublons (conversations)
- **Remplacer**: Perd données actuelles
- **Solution**: Option utilisateur + preview avant restore

### Conflits UUID
- Si conversation UUID existe déjà → Remplacer ou Ignorer?
- **Solution**: Option utilisateur + timestamp pour décider

### Version Backups
- Si format change → Compatibilité?
- **Solution**: Vérifier version, convertir si possible

---

## 🚀 PROCHAINES ÉTAPES

1. **Créer BackupManager.kt** (nouvelle classe)
2. **Implémenter export complet**
3. **Ajouter UI pour export**
4. **Tester export**
5. **Implémenter restore complet**
6. **Ajouter UI pour restore**
7. **Tester restore**
8. **Améliorations (backup de sécurité, liste backups, etc.)**

---

**Document créé**: 2025-11-27  
**Dernière mise à jour**: 2025-11-27  
**Statut**: ✅ Plan complet, prêt pour implémentation


