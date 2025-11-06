# ========================================
#    EXTRACTION DES ROMS GENESIS
# ========================================
# Genesis Plus GX ne supporte pas les .zip
# Ce script extrait tous les .zip en .bin (comme PSX utilise .PBP)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   EXTRACTION DES ROMS GENESIS" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

$genesisDir = "/storage/emulated/0/GameLibrary-Data/megadrive"
$tempDir = "temp_genesis_extract"

Write-Host "[INFO] Genesis Plus GX ne supporte pas les .zip" -ForegroundColor Yellow
Write-Host "[INFO] Extraction de tous les .zip en .bin (comme PSX avec .PBP)`n" -ForegroundColor Yellow

# Compter les fichiers .zip
Write-Host "[1/4] Comptage des fichiers..." -ForegroundColor Cyan
$zipList = adb shell "ls '$genesisDir'/*.zip 2>/dev/null"
$zipFiles = $zipList -split "`n" | Where-Object { $_ -match ".zip" }
$totalZips = $zipFiles.Count

Write-Host "[OK] $totalZips fichiers .zip trouves`n" -ForegroundColor Green

if ($totalZips -eq 0) {
    Write-Host "[INFO] Aucun fichier .zip a extraire !`n" -ForegroundColor Yellow
    exit 0
}

# Creer repertoire temporaire local
Write-Host "[2/4] Preparation..." -ForegroundColor Cyan
if (Test-Path $tempDir) {
    Remove-Item $tempDir -Recurse -Force
}
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
Write-Host "[OK] Repertoire temporaire cree`n" -ForegroundColor Green

# Extraire chaque .zip
Write-Host "[3/4] Extraction des ROMs..." -ForegroundColor Cyan
$extracted = 0
$skipped = 0
$failed = 0

foreach ($zipPath in $zipFiles) {
    $zipPath = $zipPath.Trim()
    if ([string]::IsNullOrEmpty($zipPath)) { continue }
    
    $zipName = Split-Path $zipPath -Leaf
    $binName = $zipName -replace "\.zip$", ".bin"
    $binPath = "$genesisDir/$binName"
    
    # Verifier si .bin existe deja
    $exists = adb shell "test -f '$binPath' && echo 'exists' || echo 'notfound'" 2>$null
    if ($exists -match "exists") {
        Write-Host "[SKIP] $zipName - .bin deja present" -ForegroundColor Gray
        $skipped++
        continue
    }
    
    Write-Host "[EXTRACT] $zipName" -ForegroundColor Yellow
    
    try {
        # Telecharger le .zip depuis le device
        $localZip = "$tempDir\$zipName"
        adb pull "$zipPath" "$localZip" 2>&1 | Out-Null
        
        if (Test-Path $localZip) {
            # Extraire le .bin localement
            Expand-Archive -Path $localZip -DestinationPath $tempDir -Force
            
            # Trouver le fichier .bin extrait
            $extractedBin = Get-ChildItem -Path $tempDir -Filter "*.bin" | Select-Object -First 1
            
            if ($extractedBin) {
                # Renommer pour matcher le nom du .zip
                $targetBin = "$tempDir\$binName"
                if ($extractedBin.FullName -ne $targetBin) {
                    Move-Item $extractedBin.FullName $targetBin -Force
                }
                
                # Envoyer le .bin sur le device
                adb push "$targetBin" "$binPath" 2>&1 | Out-Null
                
                Write-Host "[OK] $binName cree ($(([math]::Round((Get-Item $targetBin).Length / 1MB, 2))) MB)" -ForegroundColor Green
                $extracted++
                
                # Nettoyer les fichiers temporaires
                Remove-Item "$tempDir\*" -Force
            } else {
                Write-Host "[ERROR] Aucun .bin trouve dans $zipName" -ForegroundColor Red
                $failed++
            }
        } else {
            Write-Host "[ERROR] Echec telechargement $zipName" -ForegroundColor Red
            $failed++
        }
    } catch {
        Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
        $failed++
    }
}

# Nettoyer
Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`n[4/4] Nettoyage des .zip (optionnel)..." -ForegroundColor Cyan
Write-Host "Les .zip ne sont plus necessaires une fois les .bin extraits" -ForegroundColor Yellow
Write-Host "Gain espace: ~50% (les .zip sont comprimes)`n" -ForegroundColor Yellow

$response = Read-Host "Supprimer les .zip? (Y/N)"
if ($response -eq "Y" -or $response -eq "y") {
    adb shell "rm '$genesisDir'/*.zip"
    Write-Host "[OK] .zip supprimes`n" -ForegroundColor Green
} else {
    Write-Host "[SKIP] .zip conserves`n" -ForegroundColor Gray
}

# Rapport final
Write-Host "========================================" -ForegroundColor Green
Write-Host "   EXTRACTION TERMINEE" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Green

Write-Host "Total fichiers .zip: $totalZips" -ForegroundColor White
Write-Host "Extraits: $extracted" -ForegroundColor Green
Write-Host "Deja presents: $skipped" -ForegroundColor Gray
Write-Host "Echecs: $failed" -ForegroundColor Red
Write-Host "`nLes jeux Genesis peuvent maintenant etre lances !`n" -ForegroundColor Cyan

Write-Host "📊 VERIFICATION:" -ForegroundColor Cyan
adb shell "ls -lh '$genesisDir'/*.bin | wc -l" 2>$null | ForEach-Object {
    Write-Host "  Fichiers .bin disponibles: $_" -ForegroundColor White
}
Write-Host ""


