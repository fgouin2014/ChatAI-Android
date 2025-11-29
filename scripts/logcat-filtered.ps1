# Script PowerShell pour filtrer les logs ChatAI et réduire le bruit
# Usage: .\scripts\logcat-filtered.ps1

Write-Host "🔍 Logcat filtré pour ChatAI (sans bruit système)" -ForegroundColor Green
Write-Host "Filtrage des logs système verbeux (WebView, View, VRI, etc.)" -ForegroundColor Gray
Write-Host "Appuyez sur Ctrl+C pour arrêter" -ForegroundColor Yellow
Write-Host ""

# Tags à inclure (notre application)
$includeTags = @(
    "ChatAI",
    "OnnxEmbeddingManager",
    "OnnxVisionManager", 
    "OnnxTranslationManager",
    "OnnxTTSManager",
    "VisionService",
    "TranslationService",
    "EmbeddingService",
    "WebAppInterface",
    "MainActivity",
    "BackgroundService",
    "KittTTSManager",
    "RAGService",
    "KittAIService",
    "BidirectionalBridge"
)

# Tags à exclure (bruit système)
$excludePatterns = @(
    "setRequestedFrameRate",
    "ViewPostIme",
    "setFrameRateCategory",
    "VRI\[",
    "WV\.",
    "chromium",
    "WebViewChromium",
    "onDraw",
    "call setFrameRateCategory"
)

# Construire la commande logcat
$includeFilter = $includeTags -join "|"
$excludeFilter = "(" + ($excludePatterns -join "|") + ")"

# Utiliser logcat avec filtres
$logcatCmd = "adb logcat -v time | Select-String -Pattern '$includeFilter' | Where-Object { `$_ -notmatch '$excludeFilter' }"

Write-Host "Filtres actifs:" -ForegroundColor Cyan
Write-Host "  ✅ Inclure: $includeFilter" -ForegroundColor Green
Write-Host "  ❌ Exclure: $excludeFilter" -ForegroundColor Red
Write-Host ""

# Exécuter la commande
Invoke-Expression $logcatCmd

