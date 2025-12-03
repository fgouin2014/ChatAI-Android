# Problèmes identifiés dans les fichiers JavaScript de la webapp

## 🔴 Problèmes critiques

### 1. Double/Triple scan automatique
**Localisation:** `index.html` lignes 1773-1778, 1889-1895, `chat-config.js` lignes 145-152

**Problème:**
- `scanLocalGGUFModels()` est appelé dans `updateModelSectionVisibility()` (ligne 1777)
- `scanLocalGGUFModels()` est appelé dans `renderConfigForms()` avec setTimeout (ligne 149)
- `scanLocalGGUFModels()` est appelé au chargement si mode='local_gguf' (ligne 1893)

**Impact:** 3 scans simultanés peuvent se déclencher, causant:
- Requêtes HTTP multiples inutiles
- Conflits de mise à jour du DOM
- Performance dégradée

**Solution:** Utiliser un flag pour éviter les scans multiples simultanés.

---

### 2. Bouton manquant dans le DOM
**Localisation:** `index.html` ligne 1899

**Problème:**
```javascript
const refreshLocalDeviceModelsBtnGeneral = document.getElementById('refreshLocalDeviceModelsBtn');
```
Ce bouton n'existe pas dans le HTML. Seul `refreshDeviceModelsBtn` existe (onglet Local).

**Impact:** Event listener ajouté sur un élément null (silencieux mais inutile).

**Solution:** Supprimer cette référence ou créer le bouton dans le HTML.

---

### 3. Gestion d'erreurs incomplète
**Localisation:** `index.html` lignes 1788-1873, 3817-3919

**Problèmes:**
- Pas de vérification si `HttpServer` est démarré avant d'appeler l'API
- Pas de retry en cas d'échec réseau
- Erreurs silencieuses si `getHttpServerUrl()` n'est pas disponible

**Impact:** L'utilisateur ne sait pas pourquoi le scan échoue.

**Solution:** Ajouter vérification du serveur et messages d'erreur clairs.

---

### 4. Timing issues (race conditions)
**Localisation:** `index.html` lignes 1775, 1891, `chat-config.js` lignes 142, 146

**Problème:**
- Plusieurs `setTimeout` avec délais différents (150ms, 300ms)
- Pas de coordination entre les appels
- `updateModelSectionVisibility()` peut être appelé avant que le DOM soit prêt

**Impact:** Comportement imprévisible, scans qui échouent silencieusement.

**Solution:** Utiliser un système de queue ou un flag de "scan en cours".

---

## ⚠️ Problèmes mineurs

### 5. Code dupliqué
- `scanLocalGGUFModels()` et `loadLocalDeviceModels()` font essentiellement la même chose
- `loadLocalDeviceModels()` est plus complet (affichage formaté)
- `scanLocalGGUFModels()` remplit juste un `<select>`

**Solution:** Unifier les deux fonctions ou clarifier leurs rôles distincts.

---

### 6. Logs excessifs
- Trop de `console.log()` dans les fonctions de scan
- Peut ralentir en mode debug

**Solution:** Utiliser un système de log conditionnel (dev/prod).

---

## ✅ Recommandations

1. **Créer un système de scan unique:**
   ```javascript
   let isScanning = false;
   async function scanGGUFModelsOnce() {
       if (isScanning) return;
       isScanning = true;
       try {
           // ... scan logic
       } finally {
           isScanning = false;
       }
   }
   ```

2. **Vérifier le serveur avant de scanner:**
   ```javascript
   async function checkServerStatus() {
       try {
           const response = await fetch('/api/status');
           return response.ok;
       } catch {
           return false;
       }
   }
   ```

3. **Unifier les fonctions de scan:**
   - Garder `loadLocalDeviceModels()` pour l'onglet Local (affichage détaillé)
   - Utiliser `scanLocalGGUFModels()` pour le select (onglet Général)
   - Partager la logique commune

4. **Supprimer les références aux boutons inexistants:**
   - Retirer `refreshLocalDeviceModelsBtnGeneral` ou créer le bouton

