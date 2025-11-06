# Script pour scanner les répertoires de ROMs vides ou quasi-vides
# Usage: .\scan_empty_rom_dirs.ps1

Write-Host "`n=== SCAN DES RÉPERTOIRES ROM ===" -ForegroundColor Cyan
Write-Host "Recherche des répertoires vides ou ne contenant que console.json/media...`n" -ForegroundColor Yellow

# Liste des répertoires à vérifier
$consoles = adb shell "ls /storage/emulated/0/GameLibrary-Data/" | Where-Object { $_ -notmatch "^(data|cheats|saves|media|.cache)$" }

$emptyDirs = @()
$jsonOnlyDirs = @()
$withRoms = @()

foreach ($console in $consoles) {
    $console = $console.Trim()
    if ([string]::IsNullOrWhiteSpace($console)) { continue }
    
    # Compter les fichiers (exclure console.json et media/)
    $files = adb shell "ls /storage/emulated/0/GameLibrary-Data/$console/ 2>&1" | Where-Object { 
        $_ -notmatch "console.json" -and 
        $_ -notmatch "^media$" -and
        $_ -notmatch "gamelist.json" -and
        $_ -ne ""
    }
    
    $fileCount = ($files | Measure-Object).Count
    
    if ($fileCount -eq 0) {
        $emptyDirs += $console
        Write-Host "[VIDE] $console" -ForegroundColor Red -NoNewline
        Write-Host " (0 ROM)" -ForegroundColor Gray
    } elseif ($fileCount -le 2) {
        $jsonOnlyDirs += $console
        Write-Host "[QUASI-VIDE] $console" -ForegroundColor Yellow -NoNewline
        Write-Host " ($fileCount fichiers)" -ForegroundColor Gray
        $files | ForEach-Object { Write-Host "  - $_" -ForegroundColor DarkGray }
    } else {
        $withRoms += $console
        Write-Host "[ROMs] $console" -ForegroundColor Green -NoNewline
        Write-Host " ($fileCount fichiers)" -ForegroundColor Gray
    }
}

Write-Host "`n=== RÉSUMÉ ===" -ForegroundColor Cyan
Write-Host "Répertoires VIDES (0 ROM): $($emptyDirs.Count)" -ForegroundColor Red
$emptyDirs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }

Write-Host "`nRépertoires QUASI-VIDES (1-2 fichiers config): $($jsonOnlyDirs.Count)" -ForegroundColor Yellow
$jsonOnlyDirs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }

Write-Host "`nRépertoires AVEC ROMs: $($withRoms.Count)" -ForegroundColor Green
$withRoms | ForEach-Object { Write-Host "  - $_" -ForegroundColor Green }

Write-Host "`n=== RECOMMANDATIONS ===" -ForegroundColor Cyan

if ($emptyDirs.Count -gt 0 -or $jsonOnlyDirs.Count -gt 0) {
    Write-Host "`nRépertoires à SUPPRIMER (vides/config seulement):" -ForegroundColor Yellow
    ($emptyDirs + $jsonOnlyDirs) | ForEach-Object { 
        Write-Host "  adb shell rm -rf /storage/emulated/0/GameLibrary-Data/$_" -ForegroundColor White
    }
    
    Write-Host "`nOu supprimer tous d'un coup:" -ForegroundColor Yellow
    Write-Host "  adb shell `"cd /storage/emulated/0/GameLibrary-Data && rm -rf " -NoNewline -ForegroundColor White
    Write-Host (($emptyDirs + $jsonOnlyDirs) -join " ") -NoNewline -ForegroundColor White
    Write-Host "`"" -ForegroundColor White
} else {
    Write-Host "Aucun répertoire vide trouvé ! Tout est bien organisé." -ForegroundColor Green
}

Write-Host ""

