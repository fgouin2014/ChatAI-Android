# Script PowerShell simple pour logs ChatAI uniquement
# Usage: .\scripts\logcat-simple.ps1

Write-Host "🔍 Logs ChatAI seulement" -ForegroundColor Green
Write-Host "Appuyez sur Ctrl+C pour arrêter" -ForegroundColor Yellow
Write-Host ""

# Effacer les logs précédents
adb logcat -c

# Filtrer uniquement nos tags ChatAI
adb logcat -v time | Select-String -Pattern "ChatAI|Onnx|Vision|Translation|Embedding|TTS|RAG|Kitt" | Where-Object { 
    $_ -notmatch "setRequestedFrameRate" -and 
    $_ -notmatch "ViewPostIme" -and 
    $_ -notmatch "setFrameRateCategory" -and
    $_ -notmatch "VRI\[" -and
    $_ -notmatch "chromium"
}

