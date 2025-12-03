# Script de diagnostic pour la compilation llama.cpp
# Usage: .\scripts\diagnose_llama_build.ps1

Write-Host "🔍 Diagnostic compilation llama.cpp" -ForegroundColor Cyan
Write-Host ""

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$llamaCppDir = "$projectRoot\llama.cpp"
$buildDir = "$llamaCppDir\build-android"

# Vérifier NDK
Write-Host "1️⃣ Vérification NDK..." -ForegroundColor Yellow
$ndkPath = $env:ANDROID_NDK_HOME
if (-not $ndkPath) {
    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) {
        $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }
    if (Test-Path "$sdkPath\ndk") {
        $ndkVersions = Get-ChildItem "$sdkPath\ndk" | Sort-Object Name -Descending
        if ($ndkVersions) {
            $ndkPath = $ndkVersions[0].FullName
        }
    }
}

if ($ndkPath -and (Test-Path $ndkPath)) {
    Write-Host "   ✅ NDK: $ndkPath" -ForegroundColor Green
    
    # Vérifier toolchain
    $toolchain = "$ndkPath\build\cmake\android.toolchain.cmake"
    if (Test-Path $toolchain) {
        Write-Host "   ✅ Toolchain: $toolchain" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Toolchain manquant: $toolchain" -ForegroundColor Red
    }
    
    # Vérifier compilateur
    $clang = Get-ChildItem -Path "$ndkPath\toolchains\llvm\prebuilt" -Recurse -Filter "clang.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($clang) {
        Write-Host "   ✅ Compilateur clang trouvé" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Compilateur clang non trouvé" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ❌ NDK non trouvé" -ForegroundColor Red
}

Write-Host ""

# Vérifier CMake
Write-Host "2️⃣ Vérification CMake..." -ForegroundColor Yellow
$cmakePath = Get-Command cmake -ErrorAction SilentlyContinue
if ($cmakePath) {
    Write-Host "   ✅ CMake: $($cmakePath.Source)" -ForegroundColor Green
    $cmakeVersion = & cmake --version | Select-Object -First 1
    Write-Host "   Version: $cmakeVersion" -ForegroundColor Gray
} else {
    Write-Host "   ❌ CMake non trouvé" -ForegroundColor Red
}

Write-Host ""

# Vérifier Ninja
Write-Host "3️⃣ Vérification Ninja..." -ForegroundColor Yellow
$ninjaPath = Get-Command ninja -ErrorAction SilentlyContinue
if ($ninjaPath) {
    Write-Host "   ✅ Ninja: $($ninjaPath.Source)" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Ninja non trouvé (Make sera utilisé)" -ForegroundColor Yellow
}

Write-Host ""

# Vérifier llama.cpp
Write-Host "4️⃣ Vérification llama.cpp..." -ForegroundColor Yellow
if (Test-Path $llamaCppDir) {
    Write-Host "   ✅ llama.cpp présent: $llamaCppDir" -ForegroundColor Green
    
    # Vérifier CMakeLists.txt
    if (Test-Path "$llamaCppDir\CMakeLists.txt") {
        Write-Host "   ✅ CMakeLists.txt présent" -ForegroundColor Green
    } else {
        Write-Host "   ❌ CMakeLists.txt manquant" -ForegroundColor Red
    }
} else {
    Write-Host "   ❌ llama.cpp non trouvé: $llamaCppDir" -ForegroundColor Red
}

Write-Host ""

# Vérifier build directory
Write-Host "5️⃣ Vérification répertoire build..." -ForegroundColor Yellow
if (Test-Path $buildDir) {
    Write-Host "   ✅ Répertoire build: $buildDir" -ForegroundColor Green
    
    # Vérifier fichiers de build
    $buildFiles = Get-ChildItem -Path $buildDir -File -ErrorAction SilentlyContinue
    if ($buildFiles) {
        Write-Host "   📁 Fichiers de build: $($buildFiles.Count)" -ForegroundColor Gray
    }
    
    # Chercher logs d'erreur
    $errorLogs = Get-ChildItem -Path $buildDir -Recurse -Filter "*.log" -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*error*" -or $_.Name -like "*fail*" }
    if ($errorLogs) {
        Write-Host "   ⚠️  Logs d'erreur trouvés:" -ForegroundColor Yellow
        $errorLogs | ForEach-Object { Write-Host "      - $($_.FullName)" -ForegroundColor Gray }
    }
} else {
    Write-Host "   ⚠️  Répertoire build n'existe pas encore" -ForegroundColor Yellow
}

Write-Host ""

# Vérifier erreurs de compilation récentes
Write-Host "6️⃣ Analyse des erreurs possibles..." -ForegroundColor Yellow

if (Test-Path "$buildDir\CMakeCache.txt") {
    Write-Host "   ✅ CMakeCache.txt présent (configuration réussie)" -ForegroundColor Green
    
    # Lire le cache pour voir les variables importantes
    $cache = Get-Content "$buildDir\CMakeCache.txt" | Select-String -Pattern "CMAKE_C_COMPILER|CMAKE_CXX_COMPILER|ANDROID_"
    if ($cache) {
        Write-Host "   📋 Variables importantes:" -ForegroundColor Gray
        $cache | Select-Object -First 5 | ForEach-Object { Write-Host "      $_" -ForegroundColor Gray }
    }
} else {
    Write-Host "   ⚠️  CMakeCache.txt absent (configuration non réussie)" -ForegroundColor Yellow
}

Write-Host ""

# Suggestions
Write-Host "💡 Suggestions:" -ForegroundColor Cyan
Write-Host "   1. Si erreur de compilation, vérifier les logs ci-dessus" -ForegroundColor Yellow
Write-Host "   2. Essayer de nettoyer et reconfigurer:" -ForegroundColor Yellow
Write-Host "      Remove-Item -Recurse -Force '$buildDir'" -ForegroundColor Gray
Write-Host "      Puis relancer compile_llama_cpp.ps1" -ForegroundColor Gray
Write-Host "   3. Vérifier que le NDK est complet (réinstaller si nécessaire)" -ForegroundColor Yellow
Write-Host "   4. Vérifier les logs de compilation dans le terminal" -ForegroundColor Yellow
Write-Host ""

