@echo off
echo ========================================
echo    NETTOYAGE CACHE GENESIS
echo ========================================
echo.

set CACHE_DIR=/storage/emulated/0/GameLibrary-Data/.cache/genesis
set GENESIS_DIR=/storage/emulated/0/GameLibrary-Data/megadrive

echo [1/3] Verification du cache...
adb shell "ls -lh '%CACHE_DIR%' 2>/dev/null | head -10"
echo.

echo [2/3] Taille du cache...
for /f "tokens=1" %%s in ('adb shell "du -sh '%CACHE_DIR%' 2^>^/dev^/null ^| cut -f1"') do (
    echo Cache actuel: %%s
)
echo.

echo [3/3] Options de nettoyage:
echo   A) Supprimer le cache (liberer espace, sera recree au besoin)
echo   B) Supprimer les .bin dupliques dans megadrive/ (garder .zip + cache)
echo   C) Tout garder
echo.
choice /C ABC /M "Votre choix"

if %ERRORLEVEL% equ 1 (
    echo.
    echo [DELETE] Suppression du cache...
    adb shell "rm -rf '%CACHE_DIR%'"
    echo [OK] Cache supprime
    echo [INFO] Il sera recree automatiquement au prochain lancement
)

if %ERRORLEVEL% equ 2 (
    echo.
    echo [DELETE] Suppression des .bin dupliques dans megadrive/...
    adb shell "cd '%GENESIS_DIR%' && rm *.bin 2>/dev/null"
    echo [OK] .bin dupliques supprimes
    echo [INFO] Seuls les .zip sont gardes, cache sera utilise
)

if %ERRORLEVEL% equ 3 (
    echo.
    echo [SKIP] Rien supprime
)

echo.
echo ========================================
echo    TERMINÉ
echo ========================================
echo.
pause


