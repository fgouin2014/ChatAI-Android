# Script pour vérifier/installer CMake via Android Studio
# Note: Ce script vérifie seulement, l'installation doit se faire manuellement via Android Studio

Write-Host "📋 Vérification CMake dans Android SDK" -ForegroundColor Cyan
Write-Host ""

$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) {
    $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
}

if (-not (Test-Path $sdkPath)) {
    Write-Host "❌ Android SDK non trouvé à: $sdkPath" -ForegroundColor Red
    Write-Host ""
    Write-Host "   Solutions:" -ForegroundColor Yellow
    Write-Host "   1. Définir ANDROID_HOME pointant vers votre SDK" -ForegroundColor Yellow
    Write-Host "   2. Ou installer Android Studio" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Android SDK trouvé: $sdkPath" -ForegroundColor Green
Write-Host ""

# Vérifier CMake
$cmakeDir = "$sdkPath\cmake"
if (Test-Path $cmakeDir) {
    $cmakeVersions = Get-ChildItem $cmakeDir | Sort-Object Name -Descending
    if ($cmakeVersions) {
        Write-Host "✅ CMake trouvé dans Android SDK:" -ForegroundColor Green
        foreach ($version in $cmakeVersions) {
            $cmakeExe = "$($version.FullName)\bin\cmake.exe"
            if (Test-Path $cmakeExe) {
                Write-Host "   - $($version.Name) → $cmakeExe" -ForegroundColor Gray
            }
        }
        Write-Host ""
        Write-Host "💡 Pour utiliser ce CMake, ajoutez-le au PATH:" -ForegroundColor Cyan
        Write-Host "   `$env:PATH = `"$($cmakeVersions[0].FullName)\bin;`$env:PATH`"" -ForegroundColor Yellow
    } else {
        Write-Host "⚠️  Répertoire cmake vide" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ CMake non trouvé dans Android SDK" -ForegroundColor Red
    Write-Host ""
    Write-Host "   Pour installer CMake:" -ForegroundColor Yellow
    Write-Host "   1. Ouvrir Android Studio" -ForegroundColor Yellow
    Write-Host "   2. Tools → SDK Manager" -ForegroundColor Yellow
    Write-Host "   3. Onglet 'SDK Tools'" -ForegroundColor Yellow
    Write-Host "   4. Cocher 'CMake'" -ForegroundColor Yellow
    Write-Host "   5. Cocher 'NDK (Side by side)' si pas déjà installé" -ForegroundColor Yellow
    Write-Host "   6. Cliquer 'Apply' et attendre l'installation" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "   Après installation, relancez ce script pour vérifier." -ForegroundColor Cyan
    exit 1
}

Write-Host ""
Write-Host "✅ CMake est disponible!" -ForegroundColor Green

