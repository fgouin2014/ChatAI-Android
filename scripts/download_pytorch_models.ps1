# Script de telechargement COMPLET - Modeles PyTorch
# TTS, Embeddings, Vision, Classification, Traduction
# Destination: E:\ChatAI-Models\
# Lance en background pour telechargement pendant la nuit

param(
    [string]$Destination = "E:\ChatAI-Models"
)

$ErrorActionPreference = "Stop"

# Creer le repertoire de destination
if (-not (Test-Path $Destination)) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
}

# Creer sous-repertoires
$subdirs = @("tts", "embeddings", "vision", "classification", "translation")
foreach ($dir in $subdirs) {
    $fullPath = Join-Path $Destination $dir
    if (-not (Test-Path $fullPath)) {
        New-Item -ItemType Directory -Path $fullPath -Force | Out-Null
    }
}

# URLs des modeles PyTorch a telecharger
$models = @(
    # === TTS ===
    @{
        Name = "pytorch_model.bin"
        Url = "https://huggingface.co/microsoft/speecht5_tts/resolve/main/pytorch_model.bin"
        SubDir = "tts"
        Size = "~200 MB"
        Description = "SpeechT5 TTS (PyTorch)"
    },
    
    # === EMBEDDINGS ===
    @{
        Name = "pytorch_model.bin"
        Url = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/pytorch_model.bin"
        SubDir = "embeddings"
        Size = "~80 MB"
        Description = "all-MiniLM-L6-v2 Embeddings (PyTorch)"
    },
    
    # === VISION ===
    @{
        Name = "pytorch_model.bin"
        Url = "https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/pytorch_model.bin"
        SubDir = "vision"
        Size = "~150 MB"
        Description = "CLIP Vision (PyTorch)"
    },
    
    # === CLASSIFICATION ===
    @{
        Name = "pytorch_model.bin"
        Url = "https://huggingface.co/distilbert-base-uncased/resolve/main/pytorch_model.bin"
        SubDir = "classification"
        Size = "~67 MB"
        Description = "DistilBERT Classification (PyTorch)"
    },
    
    # === TRADUCTION ===
    @{
        Name = "pytorch_model.bin"
        Url = "https://huggingface.co/Helsinki-NLP/opus-mt-fr-en/resolve/main/pytorch_model.bin"
        SubDir = "translation"
        Size = "~50 MB"
        Description = "MarianMT Translation FR-EN (PyTorch)"
    }
)

Write-Host ""
Write-Host "[START] Debut du telechargement de tous les modeles PyTorch..." -ForegroundColor Cyan
Write-Host "Destination: $Destination" -ForegroundColor Gray
Write-Host ("=" * 70)

# Fonction de telechargement avec retry
function Download-FileWithRetry {
    param(
        [string]$Url,
        [string]$Destination,
        [string]$FileName,
        [int]$MaxRetries = 3
    )
    
    $filePath = Join-Path $Destination $FileName
    
    # Verifier si le fichier existe deja
    if (Test-Path $filePath) {
        $existingSize = (Get-Item $filePath).Length
        if ($existingSize -gt 0) {
            Write-Host "[SKIP] Fichier existe deja: $FileName ($([math]::Round($existingSize/1MB,2)) MB)" -ForegroundColor Yellow
            return $true
        }
    }
    
    $retryCount = 0
    while ($retryCount -lt $MaxRetries) {
        try {
            Write-Host "[DOWNLOAD] Telechargement: $FileName (Tentative $($retryCount + 1)/$MaxRetries)" -ForegroundColor Cyan
            
            $webClient = New-Object System.Net.WebClient
            $webClient.Headers.Add("User-Agent", "Mozilla/5.0")
            
            # Telecharger avec progression
            $webClient.DownloadFile($Url, $filePath)
            
            # Verifier le fichier
            if (Test-Path $filePath) {
                $fileSize = (Get-Item $filePath).Length
                if ($fileSize -gt 0) {
                    $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
                    Write-Host "[OK] Telecharge: $FileName ($fileSizeMB MB)" -ForegroundColor Green
                    $webClient.Dispose()
                    return $true
                }
            }
            
            $webClient.Dispose()
            $retryCount++
            
        } catch {
            Write-Host "[ERREUR] Tentative $($retryCount + 1) echouee: $($_.Exception.Message)" -ForegroundColor Red
            if (Test-Path $filePath) {
                Remove-Item $filePath -Force -ErrorAction SilentlyContinue
            }
            $retryCount++
            
            if ($retryCount -lt $MaxRetries) {
                $waitTime = [math]::Pow(2, $retryCount) # Backoff exponentiel
                Write-Host "[WAIT] Attente $waitTime secondes avant retry..." -ForegroundColor Yellow
                Start-Sleep -Seconds $waitTime
            }
        }
    }
    
    Write-Host "[ERREUR] Echec apres $MaxRetries tentatives: $FileName" -ForegroundColor Red
    return $false
}

# Telecharger tous les modeles
$successCount = 0
$failCount = 0
$totalSize = 0

foreach ($model in $models) {
    $targetDir = Join-Path $Destination $model.SubDir
    $targetPath = Join-Path $targetDir $model.Name
    
    Write-Host ""
    Write-Host "[INFO] $($model.Description)" -ForegroundColor Yellow
    Write-Host "   URL: $($model.Url)" -ForegroundColor Gray
    Write-Host "   Taille estimee: $($model.Size)" -ForegroundColor Gray
    
    $success = Download-FileWithRetry -Url $model.Url -Destination $targetDir -FileName $model.Name
    
    if ($success) {
        $successCount++
        if (Test-Path $targetPath) {
            $totalSize += (Get-Item $targetPath).Length
        }
    } else {
        $failCount++
    }
}

# Resume final
Write-Host ""
Write-Host ("=" * 70)
Write-Host "[DONE] Telechargement termine!" -ForegroundColor Green
Write-Host ""
Write-Host "[RESUME] Resultats:" -ForegroundColor Cyan
Write-Host "   Reussis: $successCount / $($models.Count)" -ForegroundColor $(if ($successCount -eq $models.Count) { "Green" } else { "Yellow" })
Write-Host "   Echoues: $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "   Espace total: $([math]::Round($totalSize/1MB,2)) MB" -ForegroundColor Cyan

# Lister les fichiers telecharges
Write-Host ""
Write-Host "[LIST] Fichiers disponibles:" -ForegroundColor Cyan
foreach ($dir in $subdirs) {
    $dirPath = Join-Path $Destination $dir
    if (Test-Path $dirPath) {
        $files = Get-ChildItem -Path $dirPath -Filter "*.bin" -ErrorAction SilentlyContinue
        if ($files.Count -gt 0) {
            Write-Host ""
            Write-Host "  [$dir]" -ForegroundColor Yellow
            $files | ForEach-Object {
                $sizeMB = [math]::Round($_.Length / 1MB, 2)
                Write-Host "    - $($_.Name): $sizeMB MB" -ForegroundColor Gray
            }
        }
    }
}

Write-Host ""
Write-Host "[INFO] Tous les modeles PyTorch ont ete telecharges dans: $Destination" -ForegroundColor Green
Write-Host "[INFO] Vous pouvez maintenant les utiliser ou les convertir en ONNX plus tard." -ForegroundColor Gray


