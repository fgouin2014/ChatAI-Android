@echo off
echo ========================================
echo    EXTRACTION DES ROMS GENESIS
echo ========================================
echo.
echo [INFO] Genesis Plus GX ne supporte pas les .zip
echo [INFO] Extraction de tous les .zip en .bin
echo.

set GENESIS_DIR=/storage/emulated/0/GameLibrary-Data/megadrive
set /a total=0
set /a extracted=0
set /a skipped=0

echo [1/3] Comptage des fichiers .zip...
for /f %%i in ('adb shell "ls '%GENESIS_DIR%'/*.zip 2^>^/dev^/null | wc -l"') do set total=%%i
echo [OK] %total% fichiers .zip trouves
echo.

echo [2/3] Extraction des ROMs...
echo.

REM Liste tous les fichiers .zip
for /f "delims=" %%f in ('adb shell "ls '%GENESIS_DIR%'/*.zip 2>/dev/null"') do (
    set "zipFile=%%f"
    setlocal enabledelayedexpansion
    
    REM Enlever le .zip et ajouter .bin
    set "binFile=!zipFile:.zip=.bin!"
    
    REM Verifier si le .bin existe deja
    adb shell "test -f '!binFile!'" 2>nul
    if !ERRORLEVEL! equ 0 (
        echo [SKIP] !zipFile! - .bin deja present
        set /a skipped+=1
    ) else (
        echo [EXTRACT] !zipFile!
        
        REM Extraire le .zip sur le device directement
        adb shell "cd '%GENESIS_DIR%' && unzip -o -j '!zipFile!' '*.bin' 2>/dev/null"
        
        if !ERRORLEVEL! equ 0 (
            echo [OK] Extrait: !binFile!
            set /a extracted+=1
        ) else (
            echo [ERROR] Echec extraction: !zipFile!
        )
    )
    
    endlocal
)

echo.
echo [3/3] Nettoyage optionnel des .zip...
echo.
echo [QUESTION] Voulez-vous supprimer les .zip apres extraction?
echo   Les .zip ne sont plus necessaires une fois les .bin extraits
echo   Gain espace: ~50%% (les .zip sont comprimes)
echo.
choice /C YN /M "Supprimer les .zip"
if %ERRORLEVEL% equ 1 (
    echo [DELETE] Suppression des .zip...
    adb shell "rm '%GENESIS_DIR%'/*.zip"
    echo [OK] .zip supprimes
) else (
    echo [SKIP] .zip conserves
)

echo.
echo ========================================
echo    EXTRACTION TERMINEE
echo ========================================
echo.
echo Total fichiers .zip: %total%
echo Extraits: %extracted%
echo Deja presents: %skipped%
echo.
echo Les jeux Genesis peuvent maintenant etre lances !
echo.
pause


