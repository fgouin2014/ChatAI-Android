# ⚠️ ERREUR - Export CLIP Text Encoder

## Date: 2025-11-29

---

## 🔍 PROBLÈME

L'export du Text Encoder CLIP échoue avec l'erreur:

```
TypeError: 'SymbolicTensor' object is not iterable
Error when calling function 'TracedOnnxFunction(<function aten_unbind>)'
```

---

## 🔍 CAUSE

Le wrapper du Text Encoder utilise une logique de pooling dynamique qui n'est pas compatible avec l'export ONNX:

```python
# Code problématique
last_idx = (mask == 1).nonzero(as_tuple=True)[0][-1].item()
```

Cette logique utilise `nonzero()` et `unbind()` qui créent des problèmes avec les `SymbolicTensor` d'ONNX.

---

## ✅ SOLUTION

Simplifier le Text Encoder pour:
1. Retourner seulement `last_hidden_state` (sans pooling)
2. Faire le pooling côté Android si nécessaire
3. Utiliser `opset_version=18` au lieu de 14
4. Pas de `dynamic_axes` pour éviter les problèmes

---

## 🔧 CORRECTIONS APPLIQUÉES

### 1. Version ONNX

- ✅ Changé `opset_version=14` → `opset_version=18`
- Compatible avec PyTorch moderne

### 2. Text Encoder simplifié

**Avant:**
- Wrapper complexe avec pooling dynamique
- Utilise `nonzero()` et logique conditionnelle

**Après:**
- Retourne seulement `last_hidden_state`
- Pooling fait côté Android si nécessaire

### 3. Dynamic axes

- ✅ Retiré `dynamic_axes` pour le Text Encoder
- Les séquences CLIP sont toujours de longueur 77 (fixe)

---

## 📝 STATUT

✅ **Vision Encoder:** Exporté avec succès (1.22 MB)  
⚠️ **Text Encoder:** Simplifié, à tester

---

## 🔄 PROCHAINES ÉTAPES

1. ✅ Corriger le script avec la version simplifiée
2. ⏳ Tester l'export du Text Encoder
3. ⏳ Adapter le code Android si nécessaire pour le pooling

---

**Note:** Le Vision Encoder fonctionne correctement. Le Text Encoder nécessite une approche simplifiée.

