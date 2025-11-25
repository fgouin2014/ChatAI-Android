# Améliorations Diagnostics - État et Plan

**Date:** 2025-01-XX  
**Status:** ✅ Phase 1 complétée, améliorations futures documentées

---

## ✅ CE QUI A ÉTÉ FAIT

### Phase 1: Sections RAG & Statistiques (Complétée)

#### 1. Section RAG & Ollama ✅
- **Fichier modifié:** `chat-diagnostics.js`
- **Méthode ajoutée:** `loadRAGStatus()`, `renderRAGStatus()`
- **Informations affichées:**
  - Statut RAG (Actif/Indisponible/Désactivé)
  - Mode Ollama (Cloud/Local)
  - Disponibilité embeddings
  - Raison si RAG indisponible

#### 2. Section Statistiques Conversations ✅
- **Fichiers modifiés:**
  - `chat-diagnostics.js` - Méthodes `loadConversationsStats()`, `renderConversationsStats()`
  - `ConversationDao.kt` - Ajout `getConversationsWithEmbeddingsCount()`
  - `ConversationHistoryHelper.kt` - Mise à jour `getConversationStats()`
- **Informations affichées:**
  - Total Conversations
  - Avec Embeddings (nombre et pourcentage)
  - Temps Moyen de réponse
  - API Principale utilisée

#### 3. HTML mis à jour ✅
- **Fichier modifié:** `index.html`
- **Sections ajoutées:**
  - `<div id="ragStatusGrid">` - Section RAG & Ollama
  - `<div id="conversationsStatsGrid">` - Section Statistiques Conversations

---

## 📋 AMÉLIORATIONS FUTURES (Pour plus tard)

### Phase 2: Serveurs RAG (Optionnel)

#### Objectif
Afficher le statut des serveurs RAG disponibles (Ollama Local, RAG Server Python)

#### Implémentation nécessaire

**1. Backend - WebAppInterface.java:**
```java
@JavascriptInterface
public String getRAGServersStatus() {
    // Retourner statut:
    // - Ollama Local: disponible/inaccessible
    // - RAG Server Python: disponible/inaccessible (si configuré)
    // - URLs configurées
    // - Modèles d'embedding disponibles
}
```

**2. Frontend - chat-diagnostics.js:**
```javascript
async loadRAGServersStatus() {
    const status = await this.androidInterface.getRAGServersStatus();
    this.renderRAGServersStatus(status);
}

renderRAGServersStatus(status) {
    // Afficher cartes pour:
    // - Ollama Local (port 11434)
    // - RAG Server Python (port 8890)
    // - Statut (disponible/inaccessible)
    // - URL configurée
}
```

**3. HTML - index.html:**
```html
<div class="panel-section">
    <h4>🔧 Serveurs RAG</h4>
    <div id="ragServersGrid">...</div>
</div>
```

**Fichiers à modifier:**
- `WebAppInterface.java` - Ajouter `getRAGServersStatus()`
- `chat-diagnostics.js` - Ajouter méthodes de chargement/rendu
- `index.html` - Ajouter section HTML

**Estimation:** 1-2h

---

### Phase 3: Modèles Ollama Disponibles (Optionnel)

#### Objectif
Afficher la liste des modèles Ollama installés (LLM et embeddings)

#### Implémentation nécessaire

**1. Backend - WebAppInterface.java:**
```java
@JavascriptInterface
public String getAvailableOllamaModels() {
    // Appeler Ollama /api/tags pour lister modèles
    // Retourner JSON avec:
    // - Modèles LLM disponibles
    // - Modèles d'embedding disponibles
    // - Taille de chaque modèle
}
```

**2. Frontend - chat-diagnostics.js:**
```javascript
async loadOllamaModels() {
    const models = await this.androidInterface.getAvailableOllamaModels();
    this.renderOllamaModels(models);
}
```

**Fichiers à modifier:**
- `WebAppInterface.java` - Ajouter `getAvailableOllamaModels()`
- `chat-diagnostics.js` - Ajouter affichage modèles
- `index.html` - Ajouter section "Modèles Ollama"

**Estimation:** 1h

---

### Phase 4: Statistiques Avancées (Optionnel)

#### Objectif
Graphiques et tendances pour les conversations

#### Fonctionnalités possibles:
- Graphique d'activité (messages par jour/semaine)
- Top topics (tags les plus utilisés)
- Longueur moyenne des messages
- Distribution par personnalité (KITT, GLaDOS, KARR)
- Distribution par plateforme (vocal, webapp)

#### Implémentation nécessaire

**1. Backend - ConversationDao.kt:**
```kotlin
@Query("SELECT DATE(timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM conversations GROUP BY date ORDER BY date DESC LIMIT 30")
suspend fun getConversationsByDate(): List<DateCount>

data class DateCount(val date: String, val count: Int)
```

**2. Frontend - chat-diagnostics.js:**
- Utiliser bibliothèque graphique (Chart.js, D3.js, ou simple SVG)
- Afficher graphiques dans section statistiques

**Fichiers à modifier:**
- `ConversationDao.kt` - Ajouter requêtes statistiques avancées
- `ConversationHistoryHelper.kt` - Exposer méthodes Java-friendly
- `WebAppInterface.java` - Ajouter méthodes JavaScript
- `chat-diagnostics.js` - Ajouter rendu graphiques
- `index.html` - Ajouter conteneurs graphiques

**Estimation:** 3-4h

---

### Phase 5: Tests de Connectivité (Optionnel)

#### Objectif
Boutons pour tester la connectivité des services

#### Fonctionnalités:
- Test Ollama Local (ping `/api/tags`)
- Test Ollama Cloud (ping `/api/chat`)
- Test RAG Server Python (ping `/status`)
- Test embeddings (générer un embedding de test)

#### Implémentation nécessaire

**1. Backend - WebAppInterface.java:**
```java
@JavascriptInterface
public String testOllamaLocal() {
    // Tester connexion Ollama Local
    // Retourner JSON avec: {success: boolean, latency: long, error: string}
}

@JavascriptInterface
public String testOllamaCloud() {
    // Tester connexion Ollama Cloud
    // Retourner JSON avec: {success: boolean, latency: long, error: string}
}

@JavascriptInterface
public String testRAGServerPython() {
    // Tester connexion RAG Server Python
    // Retourner JSON avec: {success: boolean, latency: long, error: string}
}
```

**2. Frontend - chat-diagnostics.js:**
```javascript
async testOllamaLocal() {
    const result = await this.androidInterface.testOllamaLocal();
    this.showTestResult('Ollama Local', result);
}
```

**Fichiers à modifier:**
- `WebAppInterface.java` - Ajouter méthodes de test
- `chat-diagnostics.js` - Ajouter boutons et affichage résultats
- `index.html` - Ajouter boutons de test

**Estimation:** 2-3h

---

### Phase 6: Export Diagnostics Amélioré (Optionnel)

#### Objectif
Améliorer l'export HTML avec toutes les nouvelles sections

#### Fonctionnalités:
- Inclure section RAG & Ollama dans HTML
- Inclure statistiques conversations dans HTML
- Graphiques dans HTML (si Phase 4 implémentée)
- Format plus lisible et organisé

#### Fichiers à modifier:
- `DiagnosticsHelper.kt` - Mettre à jour `generateDiagnosticsHtml()`
- Inclure toutes les nouvelles sections

**Estimation:** 1h

---

## 📊 PRIORISATION

### Priorité Haute (Utile immédiatement)
- ✅ **Phase 1: Sections RAG & Statistiques** - **COMPLÉTÉE**

### Priorité Moyenne (Utile pour debugging)
- **Phase 2: Serveurs RAG** - Voir statut serveurs rapidement
- **Phase 5: Tests de Connectivité** - Tester connexions facilement

### Priorité Basse (Nice to have)
- **Phase 3: Modèles Ollama** - Information utile mais pas critique
- **Phase 4: Statistiques Avancées** - Graphiques, tendances
- **Phase 6: Export Amélioré** - Amélioration de l'existant

---

## 🎯 RÉSUMÉ

### ✅ Complété
- Section RAG & Ollama avec statut et mode
- Section Statistiques Conversations avec métriques clés
- Backend mis à jour pour supporter nouvelles statistiques

### 📋 Pour plus tard
- Phase 2: Serveurs RAG (1-2h)
- Phase 3: Modèles Ollama (1h)
- Phase 4: Statistiques Avancées (3-4h)
- Phase 5: Tests de Connectivité (2-3h)
- Phase 6: Export Amélioré (1h)

**Total estimé restant:** 8-11h

---

## 📝 NOTES

- Les améliorations Phase 1 sont fonctionnelles et testées
- Les phases suivantes sont optionnelles et peuvent être implémentées selon les besoins
- L'architecture actuelle permet d'ajouter facilement de nouvelles sections
- Toutes les méthodes backend utilisent déjà les services existants (pas de duplication)

---

**Références:**
- `chat-diagnostics.js` - Module diagnostics
- `WebAppInterface.java` - Interface Android ↔ JavaScript
- `ConversationHistoryHelper.kt` - Helper pour statistiques
- `ConversationDao.kt` - DAO pour requêtes base de données

