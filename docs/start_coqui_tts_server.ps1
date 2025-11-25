# Script pour démarrer le serveur Coqui TTS (PowerShell)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "DÉMARRAGE SERVEUR COQUI TTS" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$MODEL_NAME = "tts_models/multilingual/multi-dataset/xtts_v2"
$PORT = 11401

Write-Host "Modèle: $MODEL_NAME" -ForegroundColor Yellow
Write-Host "Port: $PORT" -ForegroundColor Yellow
Write-Host ""

# Vérifier Python
try {
    $pythonVersion = python --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Python non trouvé"
    }
    Write-Host "✅ Python trouvé: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Python non trouvé" -ForegroundColor Red
    exit 1
}

# Vérifier version Python (3.9-3.11 requis)
$versionMatch = $pythonVersion -match "Python (\d+)\.(\d+)"
if ($versionMatch) {
    $major = [int]$matches[1]
    $minor = [int]$matches[2]
    if ($major -lt 3 -or ($major -eq 3 -and $minor -lt 9) -or ($major -eq 3 -and $minor -gt 11) -or $major -gt 3) {
        Write-Host "❌ Python $major.$minor non compatible" -ForegroundColor Red
        Write-Host "Coqui TTS nécessite Python 3.9-3.11" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "✅ Python $major.$minor compatible" -ForegroundColor Green
}

# Vérifier installation TTS
$ttsInstalled = python -c "import TTS" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Coqui TTS non installé" -ForegroundColor Yellow
    Write-Host "Installation de Coqui TTS..." -ForegroundColor Yellow
    pip install TTS
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Échec installation Coqui TTS" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Démarrage du serveur Coqui TTS..." -ForegroundColor Yellow
Write-Host "Le modèle sera téléchargé au premier démarrage (peut prendre plusieurs minutes)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Serveur accessible sur: http://127.0.0.1:$PORT" -ForegroundColor Green
Write-Host "Appuyez sur Ctrl+C pour arrêter" -ForegroundColor Yellow
Write-Host ""

# Démarrer serveur
$ttsServer = Get-Command tts-server -ErrorAction SilentlyContinue
if ($ttsServer) {
    & tts-server --model_name $MODEL_NAME --port $PORT
} else {
    # Fallback: utiliser Python module
    python -m TTS.server.server --model_name $MODEL_NAME --port $PORT
}

