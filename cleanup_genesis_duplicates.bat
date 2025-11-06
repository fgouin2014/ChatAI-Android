@echo off
echo ========================================
echo    NETTOYAGE DUPLICATIONS GENESIS
echo ========================================
echo.
echo [INFO] Systeme de cache maintenant actif
echo [INFO] Les .bin dans megadrive/ ne sont plus necessaires
echo.

set GENESIS_DIR=/storage/emulated/0/GameLibrary-Data/megadrive
set CACHE_DIR=/storage/emulated/0/GameLibrary-Data/.cache/genesis

echo [1/3] Analyse de l'espace...
echo.
echo Espace actuel:
for /f "tokens=1" %%s in ('adb shell "du -sh '%GENESIS_DIR%' 2^>^/dev^/null ^| cut -f1"') do (
    echo   megadrive/: %%s
)
for /f "tokens=1" %%s in ('adb shell "du -sh '%CACHE_DIR%' 2^>^/dev^/null ^| cut -f1"') do (
    echo   cache/:     %%s
)
echo.

echo [2/3] Comptage des fichiers...
for /f %%i in ('adb shell "ls '%GENESIS_DIR%'/*.zip 2^>^/dev^/null | wc -l"') do set zipCount=%%i
for /f %%i in ('adb shell "ls '%GENESIS_DIR%'/*.bin 2^>^/dev^/null | wc -l"') do set binCount=%%i
echo   .zip (compresses): %zipCount%
echo   .bin (dupliques): %binCount%
echo.

echo [3/3] Options de nettoyage:
echo.
echo Avec le systeme de cache:
echo   - Garder: .zip (700 MB compresses)
echo   - Supprimer: .bin (1.4 GB dupliques)
echo   - Cache: genere automatiquement au besoin
echo.
echo Gain estime: ~1.3 GB
echo.
choice /C YN /M "Supprimer les .bin dupliques"

if %ERRORLEVEL% equ 1 (
    echo.
    echo [DELETE] Suppression des .bin en cours...
    adb shell "cd '%GENESIS_DIR%' && rm *.bin 2>/dev/null && echo 'Deleted' || echo 'No files'"
    echo [OK] .bin dupliques supprimes
    echo.
    echo Nouvel espace:
    for /f "tokens=1" %%s in ('adb shell "du -sh '%GENESIS_DIR%' 2^>^/dev^/null ^| cut -f1"') do (
        echo   megadrive/: %%s (seulement .zip maintenant)
    )
    echo.
    echo [INFO] Les .bin seront extraits dans le cache au besoin
) else (
    echo.
    echo [SKIP] .bin conserves
)

echo.
echo ========================================
echo    TERMINÉ
echo ========================================
echo.
pause


