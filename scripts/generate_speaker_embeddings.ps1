# Script PowerShell pour générer default_speaker_embeddings.json
# Utilise le script Python generate_speaker_embeddings.py

$ErrorActionPreference = "Stop"

Write-Host "=== GÉNÉRATION default_speaker_embeddings.json ===" -ForegroundColor Cyan
Write-Host ""

# Chemin de sortie par défaut
$OutputPath = "E:\ChatAI-Models\tts\default_speaker_embeddings.json"

# Vérifier si Python est disponible
Write-Host "[1/3] Vérification Python..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    Write-Host "  $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] Python non trouvé!" -ForegroundColor Red
    Write-Host "  [INFO] Installer Python depuis python.org" -ForegroundColor Yellow
    exit 1
}

# Vérifier si le script Python existe
$ScriptPath = Join-Path $PSScriptRoot "generate_speaker_embeddings.py"
if (-not (Test-Path $ScriptPath)) {
    Write-Host "[ERROR] Script Python non trouvé: $ScriptPath" -ForegroundColor Red
    exit 1
}

# Exécuter le script Python
Write-Host "[2/3] Génération speaker embeddings..." -ForegroundColor Yellow
Write-Host "  Script: $ScriptPath" -ForegroundColor Gray
Write-Host "  Sortie: $OutputPath" -ForegroundColor Gray
Write-Host ""

try {
    python $ScriptPath $OutputPath
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Erreur lors de la génération" -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
    Write-Host "[OK] Génération réussie!" -ForegroundColor Green
    
} catch {
    Write-Host "[ERROR] Erreur: $_" -ForegroundColor Red
    exit 1
}

# Vérifier que le fichier a été créé
Write-Host "[3/3] Vérification fichier..." -ForegroundColor Yellow
if (Test-Path $OutputPath) {
    $fileInfo = Get-Item $OutputPath
    $sizeKB = [math]::Round($fileInfo.Length / 1024, 2)
    Write-Host "  Fichier créé: $OutputPath" -ForegroundColor Green
    Write-Host "  Taille: $sizeKB KB" -ForegroundColor Green
} else {
    Write-Host "  [ERROR] Fichier non créé!" -ForegroundColor Red
    exit 1
}

# Proposer transfert vers device
Write-Host ""
Write-Host "=== TRANSFERT VERS DEVICE ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Pour transférer vers le device Android:" -ForegroundColor Yellow
Write-Host "  adb push `"$OutputPath`" /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json" -ForegroundColor Cyan
Write-Host ""

$transfer = Read-Host "Voulez-vous transférer maintenant? (O/N)"
if ($transfer -eq "O" -or $transfer -eq "o") {
    Write-Host "Transfert en cours..." -ForegroundColor Yellow
    
    # Vérifier ADB
    try {
        $adbVersion = adb version 2>&1 | Select-Object -First 1
        Write-Host "  $adbVersion" -ForegroundColor Gray
    } catch {
        Write-Host "  [ERROR] ADB non trouvé!" -ForegroundColor Red
        Write-Host "  [INFO] Installer Android SDK Platform Tools" -ForegroundColor Yellow
        exit 1
    }
    
    # Vérifier device connecté
    $devices = adb devices
    if ($devices -match "device$") {
        Write-Host "  Device connecté" -ForegroundColor Green
        
        # Créer répertoire si nécessaire
        adb shell mkdir -p /storage/emulated/0/ChatAI-Files/models/tts
        
        # Transférer
        adb push $OutputPath /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "[SUCCESS] Fichier transféré avec succès!" -ForegroundColor Green
        } else {
            Write-Host ""
            Write-Host "[ERROR] Erreur lors du transfert" -ForegroundColor Red
        }
    } else {
        Write-Host "  [ERROR] Aucun device Android connecté!" -ForegroundColor Red
        Write-Host "  [INFO] Connectez un device et activez le débogage USB" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "[SUCCESS] Terminé!" -ForegroundColor Green


