# ✅ Fix Erreur SentencePiece

**Date**: 2025-11-27  
**Problème**: `SpeechT5Tokenizer requires the SentencePiece library`

---

## 🔧 SOLUTION

### Installation SentencePiece

```bash
pip install sentencepiece
```

**Vérification**:
```bash
python -c "import sentencepiece; print('OK')"
```

---

## 📋 DÉPENDANCES COMPLÈTES

Pour la conversion SpeechT5 → ONNX, installer:

```bash
pip install optimum[onnxruntime] transformers torch onnxruntime sentencepiece
```

**Dépendances**:
- `optimum[onnxruntime]` - Export ONNX
- `transformers` - Modèles Hugging Face
- `torch` - PyTorch
- `onnxruntime` - Runtime ONNX
- `sentencepiece` - ⭐ **REQUIS pour SpeechT5Tokenizer**

---

## ✅ STATUT

- ✅ SentencePiece ajouté dans la vérification des dépendances
- ✅ Script mis à jour
- ✅ Conversion relancée

---

**Le script devrait maintenant fonctionner correctement !** ✅


