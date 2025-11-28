# Script de transfert TTS ONNX vers device Android
# Transfere model.onnx, vocab.json et default_speaker_embeddings.json

param(
    [string]$SourceDir = "E:\ChatAI-Models\tts\onnx",
    [string]$DevicePath = "/storage/emulated/0/ChatAI-Files/models/tts"
)

$ErrorActionPreference = "Stop"

Write-Host "`n=== TRANSFERT TTS ONNX VERS DEVICE ===" -ForegroundColor Cyan
Write-Host "Source: $SourceDir" -ForegroundColor Gray
Write-Host "Destination: $DevicePath`n" -ForegroundColor Gray

# Verifier ADB
$adbCheck = adb devices 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] ADB non disponible" -ForegroundColor Red
    exit 1
}

$devices = adb devices | Select-String "device$"
if ($devices.Count -eq 0) {
    Write-Host "[ERROR] Aucun device Android connecte" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Device Android detecte" -ForegroundColor Green

# Fichiers a transferer
$files = @(
    @{ Name = "model.onnx"; DeviceName = "speecht5_onnx.onnx" },
    @{ Name = "vocab.json"; DeviceName = "vocab.json" },
    @{ Name = "default_speaker_embeddings.json"; DeviceName = "default_speaker_embeddings.json" }
)

# Creer repertoire sur device
Write-Host "`n[1/4] Creation repertoire sur device..." -ForegroundColor Cyan
adb shell "mkdir -p $DevicePath" | Out-Null
Write-Host "[OK] Repertoire cree" -ForegroundColor Green

# Verifier fichiers source
Write-Host "`n[2/4] Verification fichiers source..." -ForegroundColor Cyan
$missingFiles = @()
foreach ($file in $files) {
    $sourcePath = Join-Path $SourceDir $file.Name
    if (Test-Path $sourcePath) {
        $size = (Get-Item $sourcePath).Length / 1MB
        Write-Host "  [OK] $($file.Name): $([math]::Round($size, 2)) MB" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] $($file.Name)" -ForegroundColor Yellow
        $missingFiles += $file.Name
    }
}

if ($missingFiles.Count -gt 0) {
    Write-Host "`n[WARNING] Fichiers manquants:" -ForegroundColor Yellow
    foreach ($file in $missingFiles) {
        Write-Host "  - $file" -ForegroundColor Yellow
    }
    Write-Host "`n[INFO] Generer avec: python scripts/convert_speecht5_to_onnx_complete.py" -ForegroundColor Gray
    exit 1
}

# Transferer fichiers
Write-Host "`n[3/4] Transfert fichiers..." -ForegroundColor Cyan
foreach ($file in $files) {
    $sourcePath = Join-Path $SourceDir $file.Name
    $devicePath = "$DevicePath/$($file.DeviceName)"
    
    Write-Host "  Transfert: $($file.Name)..." -ForegroundColor Gray
    adb push $sourcePath $devicePath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    [OK] $($file.DeviceName)" -ForegroundColor Green
    } else {
        Write-Host "    [ERROR] Echec transfert $($file.Name)" -ForegroundColor Red
        exit 1
    }
}

# Verifier fichiers sur device
Write-Host "`n[4/4] Verification fichiers sur device..." -ForegroundColor Cyan
foreach ($file in $files) {
    $devicePath = "$DevicePath/$($file.DeviceName)"
    $check = adb shell "test -f $devicePath && echo 'EXISTS' || echo 'MISSING'"
    
    if ($check -match "EXISTS") {
        $size = adb shell "du -h $devicePath" | ForEach-Object { ($_ -split '\s+')[0] }
        Write-Host "  [OK] $($file.DeviceName): $size" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] $($file.DeviceName) non trouve sur device" -ForegroundColor Red
        exit 1
    }
}

Write-Host "`n[SUCCESS] Transfert complete!" -ForegroundColor Green
Write-Host "`n[INFO] Fichiers sur device:" -ForegroundColor Gray
Write-Host "  - $DevicePath/speecht5_onnx.onnx" -ForegroundColor Cyan
Write-Host "  - $DevicePath/vocab.json" -ForegroundColor Cyan
Write-Host "  - $DevicePath/default_speaker_embeddings.json" -ForegroundColor Cyan
Write-Host "`n[INFO] Redemarrer ChatAI pour charger ONNX TTS" -ForegroundColor Gray


