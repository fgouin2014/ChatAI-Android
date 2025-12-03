# Script de compilation llama.cpp pour Android ARM64
# Usage: .\scripts\compile_llama_cpp.ps1

Write-Host "🚀 Compilation llama.cpp pour Android ARM64" -ForegroundColor Cyan
Write-Host ""

# Vérifier que nous sommes dans le bon répertoire
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path "$projectRoot\ChatAI-Android")) {
    Write-Host "❌ Erreur: Ce script doit être exécuté depuis le répertoire du projet" -ForegroundColor Red
    exit 1
}

# Variables
$llamaCppDir = "$projectRoot\llama.cpp"
$buildDir = "$llamaCppDir\build-android"
$jniLibsDir = "$projectRoot\ChatAI-Android\app\src\main\jniLibs\arm64-v8a"

# Vérifier NDK Android
$ndkPath = $env:ANDROID_NDK_HOME
if (-not $ndkPath) {
    $ndkPath = $env:ANDROID_NDK_ROOT
}
if (-not $ndkPath) {
    Write-Host "⚠️  ANDROID_NDK_HOME non défini, tentative de détection automatique..." -ForegroundColor Yellow
    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) {
        $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }
    if (Test-Path "$sdkPath\ndk") {
        $ndkVersions = Get-ChildItem "$sdkPath\ndk" | Sort-Object Name -Descending
        if ($ndkVersions) {
            $ndkPath = $ndkVersions[0].FullName
            Write-Host "✅ NDK trouvé: $ndkPath" -ForegroundColor Green
        }
    }
}

if (-not $ndkPath -or -not (Test-Path $ndkPath)) {
    Write-Host "❌ Erreur: NDK Android non trouvé" -ForegroundColor Red
    Write-Host "   Définissez ANDROID_NDK_HOME ou installez le NDK via Android Studio" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ NDK Android: $ndkPath" -ForegroundColor Green

# Vérifier CMake
$cmakePath = Get-Command cmake -ErrorAction SilentlyContinue
if (-not $cmakePath) {
    Write-Host "⚠️  CMake non trouvé dans PATH, tentative de détection automatique..." -ForegroundColor Yellow
    
    # Chercher CMake dans Android SDK
    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) {
        $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }
    
    if (Test-Path "$sdkPath\cmake") {
        $cmakeVersions = Get-ChildItem "$sdkPath\cmake" | Sort-Object Name -Descending
        if ($cmakeVersions) {
            $cmakeBin = "$($cmakeVersions[0].FullName)\bin\cmake.exe"
            if (Test-Path $cmakeBin) {
                $env:PATH = "$($cmakeVersions[0].FullName)\bin;$env:PATH"
                $cmakePath = Get-Command cmake -ErrorAction SilentlyContinue
                Write-Host "✅ CMake trouvé dans Android SDK: $cmakeBin" -ForegroundColor Green
            }
        }
    }
    
    # Si toujours pas trouvé, chercher dans Program Files
    if (-not $cmakePath) {
        $programFilesCmakes = @(
            "${env:ProgramFiles}\CMake\bin\cmake.exe",
            "${env:ProgramFiles(x86)}\CMake\bin\cmake.exe"
        )
        
        foreach ($cmakeExe in $programFilesCmakes) {
            if (Test-Path $cmakeExe) {
                $env:PATH = "$(Split-Path $cmakeExe);$env:PATH"
                $cmakePath = Get-Command cmake -ErrorAction SilentlyContinue
                Write-Host "✅ CMake trouvé: $cmakeExe" -ForegroundColor Green
                break
            }
        }
    }
    
    if (-not $cmakePath) {
        Write-Host "❌ Erreur: CMake non trouvé" -ForegroundColor Red
        Write-Host "" -ForegroundColor Yellow
        Write-Host "   Solutions:" -ForegroundColor Yellow
        Write-Host "   1. Installer CMake via Android Studio:" -ForegroundColor Yellow
        Write-Host "      Tools → SDK Manager → SDK Tools → CMake" -ForegroundColor Yellow
        Write-Host "   2. Ou télécharger depuis: https://cmake.org/download/" -ForegroundColor Yellow
        Write-Host "   3. Ou ajouter CMake au PATH système" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "✅ CMake: $($cmakePath.Source)" -ForegroundColor Green
Write-Host ""

# Cloner llama.cpp si nécessaire
if (-not (Test-Path $llamaCppDir)) {
    Write-Host "📥 Clonage llama.cpp..." -ForegroundColor Cyan
    Push-Location $projectRoot
    git clone https://github.com/ggerganov/llama.cpp.git
    Pop-Location
    if (-not (Test-Path $llamaCppDir)) {
        Write-Host "❌ Erreur: Échec clonage llama.cpp" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ llama.cpp cloné" -ForegroundColor Green
} else {
    Write-Host "✅ llama.cpp déjà présent" -ForegroundColor Green
}

# Créer répertoire de build
if (Test-Path $buildDir) {
    Write-Host "🧹 Nettoyage ancien build..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

# Configurer CMake
Write-Host ""
Write-Host "⚙️  Configuration CMake..." -ForegroundColor Cyan
Push-Location $buildDir

$cmakeToolchain = "$ndkPath\build\cmake\android.toolchain.cmake"
if (-not (Test-Path $cmakeToolchain)) {
    Write-Host "❌ Erreur: android.toolchain.cmake non trouvé dans $cmakeToolchain" -ForegroundColor Red
    Pop-Location
    exit 1
}

# ⭐ FIX: Forcer le générateur Unix Makefiles pour éviter MSBuild/Visual Studio
# Vérifier si Ninja est disponible (plus rapide)
$ninjaPath = Get-Command ninja -ErrorAction SilentlyContinue
if ($ninjaPath) {
    $generator = "Ninja"
    Write-Host "✅ Utilisation de Ninja (plus rapide)" -ForegroundColor Green
} else {
    $generator = "Unix Makefiles"
    Write-Host "⚠️  Ninja non trouvé, utilisation de Make" -ForegroundColor Yellow
    Write-Host "   Pour installer Ninja: choco install ninja (ou via Android Studio)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "📋 Configuration CMake avec générateur: $generator" -ForegroundColor Cyan

$cmakeArgs = @(
    "-G", $generator,
    "-DCMAKE_TOOLCHAIN_FILE=$cmakeToolchain",
    "-DCMAKE_SYSTEM_NAME=Android",
    "-DCMAKE_ANDROID_NDK=$ndkPath",
    "-DANDROID_ABI=arm64-v8a",
    "-DANDROID_PLATFORM=android-24",
    "-DCMAKE_C_FLAGS=-march=armv8.4a+dotprod",
    "-DCMAKE_CXX_FLAGS=-march=armv8.4a+dotprod",
    "-DLLAMA_ANDROID=ON",
    "-DLLAMA_CURL=OFF",  # ⭐ Désactiver CURL (non nécessaire pour Android)
    "-DLLAMA_HTTP=OFF",  # ⭐ Désactiver HTTP (non nécessaire pour Android)
    "-DLLAMA_SERVER=OFF",  # ⭐ Désactiver serveur (utilise posix_spawn non disponible sur Android)
    "-DBUILD_SHARED_LIBS=ON",
    "-DCMAKE_BUILD_TYPE=Release",
    ".."
)

Write-Host "Commande: cmake $($cmakeArgs -join ' ')" -ForegroundColor Gray
$cmakeResult = & cmake $cmakeArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erreur: Échec configuration CMake" -ForegroundColor Red
    Pop-Location
    exit 1
}

Write-Host "✅ Configuration CMake réussie" -ForegroundColor Green

# Compiler
Write-Host ""
Write-Host "🔨 Compilation llama.cpp..." -ForegroundColor Cyan
Write-Host "   (Cela peut prendre 10-30 minutes selon votre CPU)" -ForegroundColor Gray
Write-Host ""

if ($generator -eq "Ninja") {
    # Ninja utilise -j automatiquement
    # Capturer la sortie pour afficher les erreurs
    $buildOutput = & cmake --build . --config Release 2>&1
    $buildResult = $buildOutput
    $buildOutput | ForEach-Object { Write-Host $_ }
} else {
    # Make nécessite -j explicite
    $buildOutput = & cmake --build . --config Release -- -j 4 2>&1
    $buildResult = $buildOutput
    $buildOutput | ForEach-Object { Write-Host $_ }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Erreur: Échec compilation" -ForegroundColor Red
    Write-Host ""
    Write-Host "📋 Dernières lignes d'erreur:" -ForegroundColor Yellow
    $buildOutput | Select-Object -Last 20 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    Write-Host ""
    Write-Host "💡 Solutions possibles:" -ForegroundColor Cyan
    Write-Host "   1. Vérifier que le NDK est complet (réinstaller si nécessaire)" -ForegroundColor Yellow
    Write-Host "   2. Vérifier les logs complets ci-dessus" -ForegroundColor Yellow
    Write-Host "   3. Essayer avec moins de threads: modifier le script pour utiliser -j 2" -ForegroundColor Yellow
    Pop-Location
    exit 1
}

Write-Host "✅ Compilation réussie" -ForegroundColor Green
Pop-Location

# Trouver la bibliothèque compilée
$libllama = Get-ChildItem -Path $buildDir -Recurse -Filter "libllama.so" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $libllama) {
    Write-Host "⚠️  libllama.so non trouvé, recherche alternative..." -ForegroundColor Yellow
    $libllama = Get-ChildItem -Path $buildDir -Recurse -Filter "*.so" -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*llama*" } | Select-Object -First 1
}

if (-not $libllama) {
    Write-Host "❌ Erreur: Bibliothèque llama non trouvée après compilation" -ForegroundColor Red
    Write-Host "   Vérifiez les logs de compilation ci-dessus" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "📦 Bibliothèque trouvée: $($libllama.FullName)" -ForegroundColor Green
Write-Host "   Taille: $([math]::Round($libllama.Length / 1MB, 2)) MB" -ForegroundColor Gray

# Copier vers jniLibs
Write-Host ""
Write-Host "📋 Copie vers jniLibs..." -ForegroundColor Cyan

if (-not (Test-Path $jniLibsDir)) {
    New-Item -ItemType Directory -Path $jniLibsDir -Force | Out-Null
}

$targetLib = "$jniLibsDir\libllama.so"
Copy-Item -Path $libllama.FullName -Destination $targetLib -Force

if (Test-Path $targetLib) {
    Write-Host "✅ Bibliothèque copiée: $targetLib" -ForegroundColor Green
} else {
    Write-Host "❌ Erreur: Échec copie bibliothèque" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Compilation llama.cpp terminée avec succès!" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Prochaines étapes:" -ForegroundColor Cyan
Write-Host "   1. Mettre à jour llama_jni.cpp avec les vraies fonctions llama.cpp" -ForegroundColor Yellow
Write-Host "   2. Rebuild le projet Android: .\gradlew assembleDebug" -ForegroundColor Yellow
Write-Host "   3. Tester avec gemma3-270m.gguf" -ForegroundColor Yellow
Write-Host ""

