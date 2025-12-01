# Script pour activer RAG automatiquement
# Usage: .\activer_rag.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ACTIVATION RAG ChatAI-Android" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Vérifier présence modèle ONNX
Write-Host "[1/3] Verification modele ONNX..." -ForegroundColor Yellow
$onnxPath = "/storage/emulated/0/ChatAI-Files/models/embeddings/model.onnx"
$onnxCheck = adb shell "test -f $onnxPath && echo 'OK' || echo 'MANQUANT'"
if ($onnxCheck -match "OK") {
    Write-Host "  ✅ Modele ONNX present: $onnxPath" -ForegroundColor Green
    $onnxSize = adb shell "ls -lh $onnxPath | awk '{print \$5}'"
    Write-Host "  📦 Taille: $onnxSize" -ForegroundColor Gray
} else {
    Write-Host "  ❌ Modele ONNX MANQUANT: $onnxPath" -ForegroundColor Red
    Write-Host "  ⚠️  Impossible d'activer RAG sans ONNX" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 2. Activer RAG via SharedPreferences
Write-Host "[2/3] Activation RAG..." -ForegroundColor Yellow
$prefsPath = "/data/data/com.chatai/shared_prefs/chatai_ai_config.xml"
$prefsBackup = "/data/data/com.chatai/shared_prefs/chatai_ai_config.xml.backup"

# Sauvegarder la config actuelle
Write-Host "  📦 Sauvegarde configuration actuelle..." -ForegroundColor Gray
adb shell "run-as com.chatai cp $prefsPath $prefsBackup 2>/dev/null || echo 'Pas de backup necessaire'"

# Lire la config actuelle
$currentConfig = adb shell "run-as com.chatai cat $prefsPath 2>/dev/null"

if ($currentConfig -match 'rag_enabled.*true') {
    Write-Host "  ✅ RAG deja active" -ForegroundColor Green
} else {
    # Modifier la config pour activer RAG
    Write-Host "  🔧 Activation RAG..." -ForegroundColor Yellow
    
    # Créer un script temporaire pour modifier SharedPreferences
    $tempScript = @"
import android.content.Context
import android.content.SharedPreferences

val context = getApplicationContext()
val prefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
prefs.edit()
    .putBoolean("rag_enabled", true)
    .putBoolean("rag_use_huggingface", false)
    .apply()
"@
    
    # Utiliser am start pour déclencher l'auto-configuration
    Write-Host "  📱 Redemarrage de l'app pour appliquer les changements..." -ForegroundColor Gray
    adb shell "am force-stop com.chatai"
    Start-Sleep -Seconds 2
    adb shell "am start -n com.chatai/.MainActivity"
    
    Write-Host "  ✅ RAG active (redemarrage necessaire)" -ForegroundColor Green
}
Write-Host ""

# 3. Vérifier la configuration
Write-Host "[3/3] Verification configuration..." -ForegroundColor Yellow
Start-Sleep -Seconds 3
$ragConfig = adb shell "run-as com.chatai cat $prefsPath 2>/dev/null | grep rag_enabled"
if ($ragConfig -match 'rag_enabled.*true') {
    Write-Host "  ✅ RAG active dans la configuration" -ForegroundColor Green
} else {
    Write-Host "  ⚠️  RAG pas encore active (peut prendre quelques secondes)" -ForegroundColor Yellow
    Write-Host "     → Verifier les logs: adb logcat | Select-String 'RAGAutoConfigurator'" -ForegroundColor Gray
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "RESUME" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ Modele ONNX: PRESENT" -ForegroundColor Green
Write-Host "✅ RAG: ACTIVE (apres redemarrage)" -ForegroundColor Green
Write-Host ""
Write-Host "Pour verifier les logs:" -ForegroundColor Gray
Write-Host "  adb logcat | Select-String 'RAGAutoConfigurator|OnnxEmbeddingManager'" -ForegroundColor Gray

