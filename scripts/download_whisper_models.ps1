# Script de telechargement des modeles Whisper
# Verifie la validite des liens et telecharge en background
# Destination: E:\ChatAI-Models\

param(
    [string]$Destination = "E:\ChatAI-Models",
    [switch]$Background = $false
)

$ErrorActionPreference = "Stop"

# Creer le repertoire de destination
if (-not (Test-Path $Destination)) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Write-Host "[OK] Repertoire cree: $Destination" -ForegroundColor Green
}

# URLs des modeles a telecharger
$models = @(
    @{
        Name = "ggml-medium-q5_1.bin"
        Url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_1.bin"
        AltUrl = "https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-medium-q5_1.bin"
        Size = "~587 MB"
        Priority = "HIGH"
        Description = "Whisper Medium Q5_1 (Recommandé)"
    },
    @{
        Name = "ggml-medium-q5_0.bin"
        Url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin"
        AltUrl = ""
        Size = "~750 MB"
        Priority = "HIGH"
        Description = "Whisper Medium Q5_0 (Alternative)"
    },
    @{
        Name = "ggml-small-q6_k.bin"
        Url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q6_k.bin"
        AltUrl = ""
        Size = "~207 MB"
        Priority = "MEDIUM"
        Description = "Whisper Small Q6_K (Alternative)"
    },
    @{
        Name = "ggml-small-q8_0.bin"
        Url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin"
        Size = "~160 MB"
        Priority = "MEDIUM"
        Description = "Whisper Small Q8_0 (Alternative)"
    }
)

Write-Host ""
Write-Host "[VERIFICATION] Verification de la validite des liens..." -ForegroundColor Cyan
Write-Host ("=" * 70)

# Fonction pour verifier si un lien est valide
function Test-Url {
    param([string]$Url)
    try {
        $request = [System.Net.WebRequest]::Create($Url)
        $request.Method = "HEAD"
        $request.Timeout = 10000
        $response = $request.GetResponse()
        $statusCode = [int]$response.StatusCode
        $contentLength = $response.ContentLength
        $response.Close()
        return @{
            Valid = ($statusCode -eq 200)
            StatusCode = $statusCode
            Size = $contentLength
        }
    } catch {
        return @{
            Valid = $false
            StatusCode = 0
            Size = 0
            Error = $_.Exception.Message
        }
    }
}

# Verifier tous les liens
$validModels = @()
foreach ($model in $models) {
    Write-Host ""
    Write-Host "[CHECK] Verification: $($model.Name)" -ForegroundColor Yellow
    Write-Host "   URL: $($model.Url)" -ForegroundColor Gray
    Write-Host "   Taille: $($model.Size)" -ForegroundColor Gray
    
    $testResult = Test-Url -Url $model.Url
    
    # Si le lien principal ne fonctionne pas, essayer l'URL alternative
    if (-not $testResult.Valid -and $model.AltUrl) {
        Write-Host "   [RETRY] Essai avec URL alternative..." -ForegroundColor Yellow
        $testResult = Test-Url -Url $model.AltUrl
        if ($testResult.Valid) {
            $model.Url = $model.AltUrl
        }
    }
    
    if ($testResult.Valid) {
        Write-Host "   [OK] Lien valide (HTTP $($testResult.StatusCode))" -ForegroundColor Green
        if ($testResult.Size -gt 0) {
            $sizeMB = [math]::Round($testResult.Size / 1MB, 2)
            Write-Host "   [INFO] Taille reelle: $sizeMB MB" -ForegroundColor Cyan
        }
        $model | Add-Member -NotePropertyName "Valid" -NotePropertyValue $true
        $model | Add-Member -NotePropertyName "SizeBytes" -NotePropertyValue $testResult.Size
        $validModels += $model
    } else {
        Write-Host "   [ERREUR] Lien invalide" -ForegroundColor Red
        if ($testResult.Error) {
            Write-Host "   Erreur: $($testResult.Error)" -ForegroundColor Red
        }
        $model | Add-Member -NotePropertyName "Valid" -NotePropertyValue $false
    }
}

Write-Host ""
Write-Host ("=" * 70)
Write-Host "[RESUME] Resume de la verification:" -ForegroundColor Cyan
Write-Host "   Liens valides: $($validModels.Count) / $($models.Count)" -ForegroundColor $(if ($validModels.Count -eq $models.Count) { "Green" } else { "Yellow" })

if ($validModels.Count -eq 0) {
    Write-Host ""
    Write-Host "[ERREUR] Aucun lien valide trouve. Arret du script." -ForegroundColor Red
    exit 1
}

# Fonction de telechargement avec progression
function Download-File {
    param(
        [string]$Url,
        [string]$Destination,
        [string]$FileName
    )
    
    $filePath = Join-Path $Destination $FileName
    
    # Verifier si le fichier existe deja
    if (Test-Path $filePath) {
        Write-Host ""
        Write-Host "[SKIP] Fichier existe deja: $FileName" -ForegroundColor Yellow
        Write-Host "   Chemin: $filePath" -ForegroundColor Gray
        $existingSize = (Get-Item $filePath).Length
        Write-Host "   Taille: $([math]::Round($existingSize / 1MB, 2)) MB" -ForegroundColor Gray
        
        $overwrite = Read-Host "   Voulez-vous le telecharger a nouveau? (O/N)"
        if ($overwrite -ne "O" -and $overwrite -ne "o") {
            return $false
        }
        Remove-Item $filePath -Force
    }
    
    Write-Host ""
    Write-Host "[DOWNLOAD] Telechargement: $FileName" -ForegroundColor Cyan
    Write-Host "   Destination: $filePath" -ForegroundColor Gray
    
    try {
        $webClient = New-Object System.Net.WebClient
        
        # Evenement pour afficher la progression
        Register-ObjectEvent -InputObject $webClient -EventName "DownloadProgressChanged" -Action {
            $percent = $EventArgs.ProgressPercentage
            $downloaded = [math]::Round($EventArgs.BytesReceived / 1MB, 2)
            $total = [math]::Round($EventArgs.TotalBytesToReceive / 1MB, 2)
            Write-Progress -Activity "Telechargement" -Status "$downloaded MB / $total MB" -PercentComplete $percent
        } | Out-Null
        
        # Telecharger
        $webClient.DownloadFile($Url, $filePath)
        
        Write-Progress -Activity "Telechargement" -Completed
        
        # Verifier le fichier telecharge
        if (Test-Path $filePath) {
            $fileSize = (Get-Item $filePath).Length
            $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
            Write-Host "   [OK] Telechargement termine: $fileSizeMB MB" -ForegroundColor Green
            return $true
        } else {
            Write-Host "   [ERREUR] Fichier non trouve apres telechargement" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "   [ERREUR] Erreur de telechargement: $($_.Exception.Message)" -ForegroundColor Red
        if (Test-Path $filePath) {
            Remove-Item $filePath -Force -ErrorAction SilentlyContinue
        }
        return $false
    } finally {
        if ($webClient) {
            $webClient.Dispose()
        }
    }
}

# Telecharger les modeles valides
Write-Host ""
Write-Host "[START] Debut du telechargement..." -ForegroundColor Cyan
Write-Host ("=" * 70)

$downloadJobs = @()
foreach ($model in $validModels) {
    if ($Background) {
        # Telechargement en background (Job)
        Write-Host ""
        Write-Host "[BACKGROUND] Lancement en background: $($model.Name)" -ForegroundColor Yellow
        $job = Start-Job -ScriptBlock {
            param($url, $dest, $name)
            $ErrorActionPreference = "Stop"
            try {
                $webClient = New-Object System.Net.WebClient
                $filePath = Join-Path $dest $name
                $webClient.DownloadFile($url, $filePath)
                $webClient.Dispose()
                return @{ Success = $true; File = $filePath }
            } catch {
                return @{ Success = $false; Error = $_.Exception.Message }
            }
        } -ArgumentList $model.Url, $Destination, $model.Name
        
        $downloadJobs += @{
            Job = $job
            Model = $model
        }
        Write-Host "   [OK] Job lance (ID: $($job.Id))" -ForegroundColor Green
    } else {
        # Telechargement normal avec progression
        $success = Download-File -Url $model.Url -Destination $Destination -FileName $model.Name
        if ($success) {
            Write-Host "   [OK] $($model.Name) telecharge avec succes" -ForegroundColor Green
        } else {
            Write-Host "   [ERREUR] Echec du telechargement: $($model.Name)" -ForegroundColor Red
        }
    }
}

# Si telechargement en background, attendre et afficher les resultats
if ($Background -and $downloadJobs.Count -gt 0) {
    Write-Host ""
    Write-Host "[WAIT] Attente de la fin des telechargements en background..." -ForegroundColor Cyan
    
    $allJobs = $downloadJobs | ForEach-Object { $_.Job }
    $allJobs | Wait-Job | Out-Null
    
    Write-Host ""
    Write-Host "[RESULTS] Resultats des telechargements:" -ForegroundColor Cyan
    Write-Host ("=" * 70)
    
    foreach ($item in $downloadJobs) {
        $result = Receive-Job -Job $item.Job
        Remove-Job -Job $item.Job
        
        if ($result.Success) {
            $filePath = $result.File
            if (Test-Path $filePath) {
                $fileSize = (Get-Item $filePath).Length
                $fileSizeMB = [math]::Round($fileSize / 1MB, 2)
                Write-Host "[OK] $($item.Model.Name): $fileSizeMB MB" -ForegroundColor Green
                Write-Host "   Chemin: $filePath" -ForegroundColor Gray
            } else {
                Write-Host "[ERREUR] $($item.Model.Name): Fichier non trouve" -ForegroundColor Red
            }
        } else {
            Write-Host "[ERREUR] $($item.Model.Name): $($result.Error)" -ForegroundColor Red
        }
    }
}

# Resume final
Write-Host ""
Write-Host ("=" * 70)
Write-Host "[DONE] Script termine!" -ForegroundColor Green
Write-Host ""
Write-Host "[INFO] Fichiers telecharges dans: $Destination" -ForegroundColor Cyan

# Lister les fichiers telecharges
Write-Host ""
Write-Host "[LIST] Fichiers disponibles:" -ForegroundColor Cyan
Get-ChildItem -Path $Destination -Filter "*.bin" | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    Write-Host "   - $($_.Name): $sizeMB MB" -ForegroundColor Gray
}

Write-Host ""
Write-Host "[NEXT] Prochaines etapes:" -ForegroundColor Yellow
Write-Host "   1. Transférer les fichiers vers le device:" -ForegroundColor White
Write-Host "      adb push E:\ChatAI-Models\ggml-medium-q5_1.bin /storage/emulated/0/ChatAI-Files/models/whisper/" -ForegroundColor Gray
Write-Host "   2. Mettre a jour la configuration dans ChatAI" -ForegroundColor White
Write-Host "   3. Redemarrer le serveur Whisper" -ForegroundColor White
