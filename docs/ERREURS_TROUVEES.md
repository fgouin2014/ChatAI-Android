# 🐛 ERREURS CRITIQUES TROUVÉES

## Date: 2025-11-29

---

## 1. ✅ CORRIGÉ: Tokenizer incorrect pour MarianMT

**Fichier:** `OnnxTranslationManager.kt` 

**Problème:** Utilisait `BertTokenizer` (WordPiece) au lieu de SentencePiece pour MarianMT.

**Solution appliquée:** ✅ Créé `SentencePieceTokenizer.kt`:
- Charge le vocabulaire réel MarianMT depuis `vocab.json`
- Détecte automatiquement les tokens spéciaux (BOS, EOS, PAD, UNK)
- Implémente encodage/décodage basé sur le vocabulaire
- Intégré dans `OnnxTranslationManager` à la place de `BertTokenizer`

**Statut:** ✅ **CORRIGÉ** - Le SentencePieceTokenizer est implémenté et utilisé. Note: Version simplifiée basée sur vocab.json. Pour une implémentation complète SentencePiece, il faudrait parser le fichier `.spm`.

---

## 2. ✅ CORRIGÉ: Tokens BOS/EOS incorrects

**Fichier:** `OnnxTranslationManager.kt`

**Problème:** `BOS_TOKEN_ID` et `EOS_TOKEN_ID` étaient hardcodés à des valeurs incorrectes.

**Solution appliquée:** ✅ Détection automatique depuis le vocabulaire:
- `SentencePieceTokenizer` détecte automatiquement les IDs des tokens spéciaux depuis `vocab.json`
- Les valeurs BOS/EOS/PAD sont maintenant dynamiques selon le vocabulaire réel
- Plus besoin de constantes hardcodées

**Statut:** ✅ **CORRIGÉ** - Les tokens spéciaux sont maintenant détectés automatiquement depuis le vocabulaire MarianMT.

---

## 3. ✅ CORRIGÉ: Décodage autoregressif non implémenté

**Fichier:** `OnnxTranslationManager.kt` 

**Problème:** La fonction `translate()` retournait un placeholder au lieu d'implémenter le vrai décodage.

**Solution appliquée:** ✅ Implémenté décodage autoregressif complet (inspiré de OnnxTTSManager mais adapté pour tokens texte):
- Boucle autoregressive générant un token à la fois
- Greedy decoding (sélection du token avec probabilité maximale)
- Détection EOS token pour arrêt automatique
- Limite MAX_LENGTH pour éviter boucles infinies

**Statut:** ✅ **CORRIGÉ** - Le décodage autoregressif est maintenant implémenté. La qualité sera limitée car on utilise encore BertTokenizer au lieu de SentencePiece.

---

## 4. ⚠️ RISQUE: Utilisation excessive de `!!` (non-null assertions)

**Fichiers:** Tous les managers ONNX

**Problème:** Utilisation de `!!` partout, ce qui peut causer des `KotlinNullPointerException` si une valeur est null.

```kotlin
// ❌ RISQUÉ:
encoderSession = ortEnv!!.createSession(...)
val inputs = encoderSession!!.inputNames
```

**Fichiers affectés:**
- `OnnxTranslationManager.kt` (lignes 98, 101, 104-107)
- `OnnxVisionManager.kt` (lignes 101, 104, 107-110)
- `OnnxTTSManager.kt` (lignes 110, 113, 116)
- `OnnxEmbeddingManager.kt` (lignes 82, 85-86)

**Impact:** Crashes potentiels si `ortEnv` ou les sessions sont null (ex: si l'initialisation échoue silencieusement).

**Solution:** Utiliser des vérifications null-safe avec `?.let` ou early returns.

---

## 5. ⚠️ À VÉRIFIER: Indexation imageData dans OnnxVisionManager

**Fichier:** `OnnxVisionManager.kt` lignes 191-193

**Problème:** Indexation potentiellement incorrecte pour les canaux RGB dans `imageData`.

```kotlin
val idx = y * IMAGE_SIZE + x
imageData[idx] = (r - mean[0]) / std[0] // R channel
imageData[idx + IMAGE_SIZE * IMAGE_SIZE] = (g - mean[1]) / std[1] // G channel
imageData[idx + 2 * IMAGE_SIZE * IMAGE_SIZE] = (b - mean[2]) / std[2] // B channel
```

**Impact:** Si l'indexation est incorrecte, les canaux RGB seront mélangés et l'encodage d'image sera incorrect.

**Solution:** Vérifier que l'indexation correspond bien au format attendu par CLIP (généralement CHW: channels, height, width).

---

## 6. ⚠️ PROBLÈME: Fermeture de tensors dans OnnxTranslationManager

**Fichier:** `OnnxTranslationManager.kt` lignes 215-217

**Problème:** Fermeture de tensors avant d'utiliser `encoderOutputTensor` pour le décodage.

```kotlin
// ❌ FERME TROP TÔT:
encoderOutputTensor.close()
inputTensor.close()
encoderResult.close()
```

**Impact:** Si le décodage autoregressif était implémenté, il ne pourrait pas utiliser `encoderOutputTensor` car il serait déjà fermé.

**Solution:** Ne fermer les tensors qu'après avoir terminé toutes les étapes (encoder + decoder).

---

## PRIORITÉS DE CORRECTION

1. **URGENT:** Corriger le tokenizer MarianMT (erreur #1)
2. **URGENT:** Corriger les tokens BOS/EOS (erreur #2)
3. **IMPORTANT:** Implémenter le décodage autoregressif (erreur #3)
4. **MOYEN:** Remplacer `!!` par des vérifications null-safe (erreur #4)
5. **FAIBLE:** Vérifier l'indexation imageData (erreur #5)

---

## NOTES

- Le problème du tokenizer est le plus critique car il rend la traduction complètement dysfonctionnelle.
- Les non-null assertions (`!!`) sont un risque mais moins critique si l'initialisation est bien gérée.
- Le décodage autoregressif peut être implémenté en s'inspirant de `OnnxTTSManager.kt`.

