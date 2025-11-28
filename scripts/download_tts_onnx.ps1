# Script de telechargement TTS ONNX (SpeechT5)
# Modele pre-converti: NeuML/txtai-speecht5-onnx
# Destination: E:\ChatAI-Models\tts\

param(
    [string]$Destination = "E:\ChatAI-Models\tts"
)

$ErrorActionPreference = "Stop"

# Creer le repertoire de destination
if (-not (Test-Path $Destination)) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Write-Host "[INFO] Repertoire cree: $Destination" -ForegroundColor Green
}

# URLs du modele ONNX SpeechT5
$models = @(
    @{
        Name = "speecht5_onnx.onnx"
        Url = "https://huggingface.co/NeuML/txtai-speecht5-onnx/resolve/main/model.onnx"
        Size = "~80-150 MB"
        Description = "SpeechT5 TTS (ONNX pre-converti)"
    }
)

Write-Host "`n=== TELECHARGEMENT TTS ONNX ===" -ForegroundColor Cyan
Write-Host "Destination: $Destination`n" -ForegroundColor Gray

foreach ($model in $models) {
    $outputPath = Join-Path $Destination $model.Name
    
    # Verifier si deja telecharge
    if (Test-Path $outputPath) {
        $existingSize = (Get-Item $outputPath).Length / 1MB
        Write-Host "[SKIP] $($model.Name) deja present ($([math]::Round($existingSize, 2)) MB)" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "[DOWNLOAD] $($model.Name)..." -ForegroundColor Cyan
    Write-Host "  URL: $($model.Url)" -ForegroundColor Gray
    Write-Host "  Taille estimee: $($model.Size)" -ForegroundColor Gray
    
    try {
        # Telecharger avec barre de progression
        $ProgressPreference = 'Continue'
        Invoke-WebRequest -Uri $model.Url -OutFile $outputPath -UseBasicParsing
        
        $downloadedSize = (Get-Item $outputPath).Length / 1MB
        Write-Host "[OK] $($model.Name) telecharge ($([math]::Round($downloadedSize, 2)) MB)" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Echec telechargement $($model.Name): $_" -ForegroundColor Red
        Write-Host "  URL alternative: Verifier https://huggingface.co/NeuML/txtai-speecht5-onnx" -ForegroundColor Yellow
    }
}

Write-Host "`n=== VERIFICATION ===" -ForegroundColor Cyan
Get-ChildItem -Path $Destination -File | Format-Table Name, @{Label="Size(MB)";Expression={[math]::Round($_.Length/1MB,2)}} -AutoSize

Write-Host "`n[INFO] Pour transferer vers le device:" -ForegroundColor Gray
Write-Host "  adb push `"$Destination\speecht5_onnx.onnx`" /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx" -ForegroundColor Cyan


