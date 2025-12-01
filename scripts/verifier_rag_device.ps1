# Script de vérification RAG sur device Android
# Usage: .\verifier_rag_device.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "VERIFICATION RAG ChatAI-Android" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Vérifier présence modèle ONNX
Write-Host "[1/6] Verification modele ONNX..." -ForegroundColor Yellow
$onnxPath = "/storage/emulated/0/ChatAI-Files/models/embeddings/model.onnx"
$onnxCheck = adb shell "test -f $onnxPath && echo 'OK' || echo 'MANQUANT'"
if ($onnxCheck -match "OK") {
    Write-Host "  ✅ Modele ONNX present: $onnxPath" -ForegroundColor Green
    $onnxSize = adb shell "ls -lh $onnxPath | awk '{print \$5}'"
    Write-Host "  📦 Taille: $onnxSize" -ForegroundColor Gray
} else {
    Write-Host "  ❌ Modele ONNX MANQUANT: $onnxPath" -ForegroundColor Red
    Write-Host "  ⚠️  RAG utilisera fallback Ollama/HuggingFace" -ForegroundColor Yellow
}
Write-Host ""

# 2. Vérifier tokenizer BERT
Write-Host "[2/6] Verification tokenizer BERT..." -ForegroundColor Yellow
$tokenizerPath = "/storage/emulated/0/ChatAI-Files/models/embeddings/tokenizer.json"
$tokenizerCheck = adb shell "test -f $tokenizerPath && echo 'OK' || echo 'MANQUANT'"
if ($tokenizerCheck -match "OK") {
    Write-Host "  ✅ Tokenizer BERT present: $tokenizerPath" -ForegroundColor Green
} else {
    Write-Host "  ⚠️  Tokenizer BERT manquant (peut fonctionner quand meme)" -ForegroundColor Yellow
}
Write-Host ""

# 3. Vérifier configuration SharedPreferences
Write-Host "[3/6] Verification configuration RAG..." -ForegroundColor Yellow
$configCheck = adb shell "run-as com.chatai cat /data/data/com.chatai/shared_prefs/chatai_ai_config.xml 2>/dev/null | grep -E 'rag_enabled|rag_use_huggingface|use_ollama_cloud'"
if ($configCheck) {
    Write-Host "  Configuration trouvee:" -ForegroundColor Green
    $configCheck -split "`n" | ForEach-Object {
        if ($_ -match 'rag_enabled.*true') {
            Write-Host "    ✅ RAG active" -ForegroundColor Green
        } elseif ($_ -match 'rag_enabled.*false') {
            Write-Host "    ❌ RAG desactive" -ForegroundColor Red
        }
        if ($_ -match 'use_ollama_cloud.*true') {
            Write-Host "    ⚠️  Mode Cloud active" -ForegroundColor Yellow
        } elseif ($_ -match 'use_ollama_cloud.*false') {
            Write-Host "    ✅ Mode Local active" -ForegroundColor Green
        }
    }
} else {
    Write-Host "  ⚠️  Configuration non trouvee (valeurs par defaut)" -ForegroundColor Yellow
}
Write-Host ""

# 4. Vérifier base de données (conversations avec embeddings)
Write-Host "[4/6] Verification base de donnees..." -ForegroundColor Yellow
$dbPath = "/data/data/com.chatai/databases/chatai_database.db"
$dbCheck = adb shell "run-as com.chatai sqlite3 $dbPath 'SELECT COUNT(*) FROM conversations WHERE embeddingsJson IS NOT NULL;' 2>/dev/null"
if ($dbCheck -match '^\d+$') {
    $count = [int]$dbCheck
    if ($count -gt 0) {
        Write-Host "  ✅ $count conversations avec embeddings" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Aucune conversation avec embeddings" -ForegroundColor Yellow
        Write-Host "     → Verifier que RAG est active et que des conversations ont ete sauvegardees" -ForegroundColor Gray
    }
} else {
    Write-Host "  ⚠️  Impossible de lire la base de donnees" -ForegroundColor Yellow
}
Write-Host ""

# 5. Vérifier logs initialisation ONNX
Write-Host "[5/6] Verification logs ONNX..." -ForegroundColor Yellow
$onnxLogs = adb logcat -d | Select-String "OnnxEmbeddingManager" | Select-Object -Last 5
if ($onnxLogs) {
    $initialized = $onnxLogs | Select-String "initialise|prêt|ready"
    if ($initialized) {
        Write-Host "  ✅ ONNX Embeddings initialise" -ForegroundColor Green
        $initialized | ForEach-Object {
            Write-Host "    $_" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ⚠️  ONNX Embeddings non initialise (verifier logs)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ⚠️  Aucun log ONNX trouve" -ForegroundColor Yellow
}
Write-Host ""

# 6. Vérifier logs génération embeddings
Write-Host "[6/6] Verification logs generation embeddings..." -ForegroundColor Yellow
$embeddingLogs = adb logcat -d | Select-String "EmbeddingService|Embedding generated" | Select-Object -Last 5
if ($embeddingLogs) {
    $generated = $embeddingLogs | Select-String "generé|generated|saved"
    if ($generated) {
        Write-Host "  ✅ Embeddings generes recemment" -ForegroundColor Green
        $generated | ForEach-Object {
            Write-Host "    $_" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ⚠️  Aucun embedding genere recemment" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ⚠️  Aucun log embedding trouve" -ForegroundColor Yellow
}
Write-Host ""

# Résumé
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RESUME" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$allOk = $true

if ($onnxCheck -match "OK") {
    Write-Host "✅ Modele ONNX: PRESENT" -ForegroundColor Green
} else {
    Write-Host "❌ Modele ONNX: MANQUANT" -ForegroundColor Red
    $allOk = $false
}

if ($configCheck -match 'rag_enabled.*true') {
    Write-Host "✅ RAG: ACTIVE" -ForegroundColor Green
} else {
    Write-Host "❌ RAG: DESACTIVE" -ForegroundColor Red
    $allOk = $false
}

if ($dbCheck -match '^\d+' -and [int]$dbCheck -gt 0) {
    Write-Host "✅ Base de donnees: $dbCheck conversations avec embeddings" -ForegroundColor Green
} else {
    Write-Host "⚠️  Base de donnees: Aucune conversation avec embeddings" -ForegroundColor Yellow
}

Write-Host ""
if ($allOk) {
    Write-Host "✅ RAG est configure correctement!" -ForegroundColor Green
} else {
    Write-Host "⚠️  RAG necessite des corrections (voir details ci-dessus)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Pour voir les logs en temps reel:" -ForegroundColor Gray
Write-Host "  adb logcat | Select-String 'RAGService|EmbeddingService|OnnxEmbeddingManager'" -ForegroundColor Gray

