# Script de test pour Coqui TTS Server (PowerShell)
# Teste l'installation et le démarrage du serveur

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "TEST COQUI TTS SERVER" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier Python
Write-Host "[1/5] Vérification Python..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Python non trouvé"
    }
    Write-Host "✅ Python trouvé: $pythonVersion" -ForegroundColor Green
    
    # Vérifier version Python (Coqui TTS nécessite 3.9-3.11)
    $versionMatch = $pythonVersion -match "Python (\d+)\.(\d+)"
    if ($versionMatch) {
        $major = [int]$matches[1]
        $minor = [int]$matches[2]
        if ($major -lt 3 -or ($major -eq 3 -and $minor -lt 9) -or ($major -eq 3 -and $minor -gt 11) -or $major -gt 3) {
            Write-Host "⚠️  ATTENTION: Coqui TTS nécessite Python 3.9-3.11" -ForegroundColor Yellow
            Write-Host "   Version détectée: Python $major.$minor" -ForegroundColor Yellow
            Write-Host "   L'installation peut échouer. Utilisez Python 3.9, 3.10 ou 3.11" -ForegroundColor Yellow
        } else {
            Write-Host "✅ Version Python compatible (3.9-3.11)" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "❌ Python non trouvé" -ForegroundColor Red
    exit 1
}

# Vérifier pip
Write-Host "[2/5] Vérification pip..." -ForegroundColor Yellow
try {
    $pipVersion = pip --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "pip non trouvé"
    }
    Write-Host "✅ pip trouvé: $pipVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ pip non trouvé" -ForegroundColor Red
    exit 1
}

# Vérifier installation TTS
Write-Host "[3/5] Vérification installation Coqui TTS..." -ForegroundColor Yellow
$ttsInstalled = python -c "import TTS" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Coqui TTS non installé" -ForegroundColor Yellow
    Write-Host "Installation de Coqui TTS..." -ForegroundColor Yellow
    pip install TTS
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Échec installation Coqui TTS" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Coqui TTS installé" -ForegroundColor Green
} else {
    Write-Host "✅ Coqui TTS déjà installé" -ForegroundColor Green
}

# Vérifier commande tts-server
Write-Host "[4/5] Vérification commande tts-server..." -ForegroundColor Yellow
$ttsServer = Get-Command tts-server -ErrorAction SilentlyContinue
if (-not $ttsServer) {
    Write-Host "⚠️  Commande tts-server non trouvée dans PATH" -ForegroundColor Yellow
    Write-Host "Installation complète de TTS avec serveur..." -ForegroundColor Yellow
    pip install TTS[server]
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Échec installation serveur TTS" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Serveur TTS disponible" -ForegroundColor Green
} else {
    Write-Host "✅ Commande tts-server trouvée" -ForegroundColor Green
}

# Test configuration serveur
Write-Host "[5/5] Test configuration serveur..." -ForegroundColor Yellow
$MODEL_NAME = "tts_models/multilingual/multi-dataset/xtts_v2"
$PORT = 11401

Write-Host "Modèle: $MODEL_NAME" -ForegroundColor Yellow
Write-Host "Port: $PORT" -ForegroundColor Yellow

# Vérifier si le modèle peut être chargé (test rapide)
Write-Host "Test chargement modèle (peut prendre du temps au premier lancement)..." -ForegroundColor Yellow
$testScript = @"
from TTS.api import TTS
import sys

try:
    print('Chargement modèle $MODEL_NAME...')
    tts = TTS('$MODEL_NAME')
    print('✅ Modèle chargé avec succès')
    if hasattr(tts, 'languages'):
        print(f'✅ Langues supportées: {tts.languages}')
    else:
        print('✅ Modèle chargé (langues N/A)')
    sys.exit(0)
except Exception as e:
    print(f'❌ Erreur chargement modèle: {e}')
    print('⚠️  Le modèle sera téléchargé au premier démarrage du serveur')
    sys.exit(0)  # Non bloquant
"@

$testScript | python
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Erreur lors du test (non bloquant)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "✅ TESTS TERMINÉS" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Pour démarrer le serveur Coqui TTS:" -ForegroundColor Yellow
Write-Host "  tts-server --model_name $MODEL_NAME --port $PORT" -ForegroundColor White
Write-Host ""
Write-Host "Ou avec Python:" -ForegroundColor Yellow
Write-Host "  python -m TTS.server.server --model_name $MODEL_NAME --port $PORT" -ForegroundColor White
Write-Host ""
Write-Host "Test API (une fois le serveur démarré):" -ForegroundColor Yellow
Write-Host "  curl `"http://127.0.0.1:$PORT/process?INPUT_TEXT=Bonjour&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE`" > test_output.wav" -ForegroundColor White
Write-Host ""

