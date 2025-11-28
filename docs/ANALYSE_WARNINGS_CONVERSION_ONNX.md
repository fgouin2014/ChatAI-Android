# 🔍 Analyse des Warnings - Conversion ONNX

**Date**: 2025-11-27  
**Contexte**: Warnings lors de la conversion Translation (MarianMT)

---

## 📋 CLASSIFICATION DES WARNINGS

### ✅ **INFORMATIFS** (Peuvent être ignorés)

### ⚠️ **ATTENTION** (Vérifier mais généralement OK)

### ❌ **ERREURS** (Bloquants - nécessitent correction)

---

## 1. ✅ TensorFlow oneDNN (INFORMATIF - Ignorable)

```
2025-11-27 15:56:36.100384: I tensorflow/core/util/port.cc:153] oneDNN custom operations are on...
```

**Type**: Informations TensorFlow  
**Cause**: TensorFlow s'initialise même si on utilise PyTorch  
**Impact**: **Aucun** - Juste une initialisation  
**Action**: Ignorer ou désactiver avec `TF_ENABLE_ONEDNN_OPTS=0`

---

## 2. ✅ FutureWarning functools.partial (INFORMATIF)

```
FutureWarning: functools.partial will be a method descriptor in future Python versions
```

**Type**: Warning de compatibilité future Python  
**Cause**: Changement dans Python 3.13+  
**Impact**: **Aucun** - Fonctionne parfaitement actuellement  
**Action**: Ignorer (sera corrigé dans futures versions de `optimum`)

---

## 3. ✅ Generation Config (INFORMATIF)

```
Moving the following attributes in the config to the generation config: {'max_length': 512, 'num_beams': 4, 'bad_words_ids': [[59513]]}
```

**Type**: Information de migration de configuration  
**Cause**: Paramètres de génération déplacés dans `generation_config`  
**Impact**: **Aucun** - Migration automatique effectuée  
**Action**: Ignorer

---

## 4. ✅ TracerWarnings (NORMAL pour ONNX)

```
TracerWarning: Converting a tensor to a Python boolean might cause the trace to be incorrect...
TracerWarning: torch.tensor results are registered as constants in the trace...
```

**Type**: Warnings normaux de traçage PyTorch → ONNX  
**Cause**: PyTorch convertit certaines valeurs dynamiques en constantes pour ONNX  
**Impact**: **Aucun** - C'est le comportement attendu  
**Action**: Ignorer (normal pour les modèles de traduction)

**Explication** :
- PyTorch doit "figer" certaines valeurs lors de la conversion
- Ces valeurs seront constantes dans ONNX (OK pour la plupart des cas d'usage)
- C'est la façon normale de convertir PyTorch → ONNX

---

## 5. ⚠️ ONNX Initializer (ATTENTION - Mais généralement OK)

```
Could not find ONNX initializer for torch parameter model.decoder.embed_tokens.weight...
Could not find ONNX initializer for torch parameter model.encoder.embed_tokens.weight...
Found different candidate ONNX initializers (likely duplicate) for the tied weights...
```

**Type**: Warnings sur les poids partagés (tied weights)  
**Cause**: MarianMT a des poids partagés entre encoder/decoder  
**Impact**: **Minimal** - Les poids partagés sont gérés correctement  
**Action**: Vérifier que la conversion se termine avec succès

**Explication détaillée** :
- Les modèles de traduction (MarianMT) ont souvent des poids partagés
- L'encoder et le decoder partagent certains embeddings
- `optimum-cli` gère correctement cette situation
- Le message indique que les poids sont traités séparément (normal)

**Ce qui se passe** :
1. `model.encoder.embed_tokens.weight` et `model.decoder.embed_tokens.weight` partagent les mêmes valeurs
2. Dans ONNX, ces poids sont fusionnés dans `model.shared.weight`
3. C'est le comportement attendu et correct

---

## 📊 RÉSUMÉ PAR NIVEAU DE PRIORITÉ

| Warning | Type | Priorité | Action |
|---------|------|----------|--------|
| TensorFlow oneDNN | Info | ✅ Basse | Ignorer |
| FutureWarning functools | Info | ✅ Basse | Ignorer |
| Generation Config | Info | ✅ Basse | Ignorer |
| TracerWarnings | Normal | ✅ Basse | Ignorer (normal) |
| ONNX Initializer | Attention | ⚠️ Moyenne | Vérifier succès conversion |

---

## ✅ VÉRIFICATION

**Indicateurs que tout va bien** :
- ✅ Pas d'erreur `Traceback`
- ✅ Message `[OK] Conversion réussie!`
- ✅ Fichiers générés dans le répertoire `onnx/`
- ✅ Taille du fichier `model.onnx` raisonnable (> 0 MB)

**Indicateurs de problème** :
- ❌ `Traceback (most recent call last)`
- ❌ `Error:` ou `Exception:`
- ❌ `[FAILED]` ou `Conversion échouée`
- ❌ Aucun fichier généré

---

## 🎯 CONCLUSION

**Tous ces warnings sont normaux** pour la conversion ONNX d'un modèle de traduction (MarianMT). Ils indiquent que :

1. ✅ La conversion gère correctement les poids partagés
2. ✅ Les warnings de traçage sont attendus (PyTorch → ONNX)
3. ✅ Les informations TensorFlow/Python sont cosmétiques

**Si vous voyez `[OK] Conversion réussie!` et des fichiers générés, tout est OK !**

---

## 🔗 RÉFÉRENCES

- [PyTorch ONNX Export Warnings](https://pytorch.org/docs/stable/onnx.html#common-errors)
- [Hugging Face Optimum Export](https://huggingface.co/docs/optimum/onnxruntime/usage_guides/export)


