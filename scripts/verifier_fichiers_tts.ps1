# Script de vérification des fichiers TTS requis
# Usage: .\verifier_fichiers_tts.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "VERIFICATION FICHIERS TTS REQUIS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$TTS_DIR = "/storage/emulated/0/ChatAI-Files/models/tts"

# Fichiers obligatoires
$FICHIERS_OBLIGATOIRES = @(
    @{Nom="encoder_model.onnx"; TailleMinMB=300; TailleMaxMB=350},
    @{Nom="decoder_model.onnx"; TailleMinMB=200; TailleMaxMB=250},
    @{Nom="decoder_postnet_and_vocoder.onnx"; TailleMinMB=45; TailleMaxMB=60},
    @{Nom="vocab.json"; TailleMinMB=0.5; TailleMaxMB=3},
    @{Nom="default_speaker_embeddings.json"; TailleMinKB=2; TailleMaxKB=10}
)

# Fichiers optionnels
$FICHIERS_OPTIONNELS = @(
    @{Nom="male_speaker_embeddings.json"; TailleMinKB=2; TailleMaxKB=10},
    @{Nom="female_speaker_embeddings.json"; TailleMinKB=2; TailleMaxKB=10}
)

Write-Host "Verification des fichiers obligatoires..." -ForegroundColor Yellow
Write-Host ""

$tous_presents = $true
$total_taille = 0

foreach ($fichier in $FICHIERS_OBLIGATOIRES) {
    $nom = $fichier.Nom
    $chemin = "$TTS_DIR/$nom"
    
    Write-Host "  [$nom] " -NoNewline
    
    $result = adb shell "test -f `"$chemin`" && ls -lh `"$chemin`" || echo 'FICHIER_MANQUANT'"
    
    if ($result -match "FICHIER_MANQUANT") {
        Write-Host "❌ MANQUANT" -ForegroundColor Red
        $tous_presents = $false
    } else {
        # Extraire la taille
        if ($result -match "(\d+\.?\d*)([KM])") {
            $taille_str = $matches[0]
            $valeur = [double]$matches[1]
            $unite = $matches[2]
            
            if ($unite -eq "K") {
                $taille_mb = $valeur / 1024
            } elseif ($unite -eq "M") {
                $taille_mb = $valeur
            } else {
                $taille_mb = $valeur / (1024 * 1024)
            }
            
            $total_taille += $taille_mb
            
            # Vérifier si la taille est dans la plage attendue
            $taille_ok = $false
            if ($fichier.TailleMinMB) {
                $taille_ok = ($taille_mb -ge $fichier.TailleMinMB -and $taille_mb -le $fichier.TailleMaxMB)
            } elseif ($fichier.TailleMinKB) {
                $taille_kb = $taille_mb * 1024
                $taille_ok = ($taille_kb -ge $fichier.TailleMinKB -and $taille_kb -le $fichier.TailleMaxKB)
            }
            
            if ($taille_ok) {
                Write-Host "✅ PRESENT ($taille_str)" -ForegroundColor Green
            } else {
                Write-Host "⚠️ PRESENT mais taille suspecte ($taille_str)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "✅ PRESENT" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Verification des fichiers optionnels..." -ForegroundColor Yellow
Write-Host ""

foreach ($fichier in $FICHIERS_OPTIONNELS) {
    $nom = $fichier.Nom
    $chemin = "$TTS_DIR/$nom"
    
    Write-Host "  [$nom] " -NoNewline
    
    $result = adb shell "test -f `"$chemin`" && ls -lh `"$chemin`" || echo 'FICHIER_MANQUANT'"
    
    if ($result -match "FICHIER_MANQUANT") {
        Write-Host "⚠️ MANQUANT (utilisera default)" -ForegroundColor Yellow
    } else {
        Write-Host "✅ PRESENT" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($tous_presents) {
    Write-Host "✅ TOUS LES FICHIERS OBLIGATOIRES SONT PRESENTS" -ForegroundColor Green
    Write-Host "   Taille totale: $([math]::Round($total_taille, 2)) MB" -ForegroundColor Green
} else {
    Write-Host "❌ CERTAINS FICHIERS OBLIGATOIRES SONT MANQUANTS" -ForegroundColor Red
    Write-Host ""
    Write-Host "Actions requises:" -ForegroundColor Yellow
    Write-Host "  1. Télécharger les modèles ONNX (conversion PyTorch → ONNX)" -ForegroundColor Yellow
    Write-Host "  2. Télécharger vocab.json depuis Hugging Face" -ForegroundColor Yellow
    Write-Host "  3. Générer default_speaker_embeddings.json (script Python)" -ForegroundColor Yellow
    Write-Host "  4. Transférer tous les fichiers via ADB push" -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier aussi les logs d'initialisation
Write-Host "Verification des logs d'initialisation..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Recherche des logs OnnxTTSManager (derniers 50 lignes)..." -ForegroundColor Gray

$logs = adb logcat -d | Select-String "OnnxTTSManager" | Select-Object -Last 20

if ($logs) {
    Write-Host ""
    foreach ($log in $logs) {
        if ($log -match "✅") {
            Write-Host "  $log" -ForegroundColor Green
        } elseif ($log -match "❌|ERROR|Erreur") {
            Write-Host "  $log" -ForegroundColor Red
        } elseif ($log -match "⚠️|WARN|Warning") {
            Write-Host "  $log" -ForegroundColor Yellow
        } else {
            Write-Host "  $log" -ForegroundColor Gray
        }
    }
} else {
    Write-Host "  Aucun log OnnxTTSManager trouvé" -ForegroundColor Yellow
    Write-Host "  (L'app n'a peut-être pas encore été lancée)" -ForegroundColor Gray
}

Write-Host ""

