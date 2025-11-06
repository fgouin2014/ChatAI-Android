@echo off
echo ========================================
echo    BACKUP AVANT NETTOYAGE
echo ========================================
echo.

REM Creer le repertoire de backup avec timestamp
for /f "tokens=2-4 delims=/ " %%a in ('date /t') do (set mydate=%%c%%a%%b)
for /f "tokens=1-2 delims=/: " %%a in ('time /t') do (set mytime=%%a%%b)
set BACKUP_DIR=BACKUP_CLEANUP_%mydate%_%mytime%

echo [INFO] Creation du repertoire: %BACKUP_DIR%
mkdir "%BACKUP_DIR%" 2>nul
mkdir "%BACKUP_DIR%\lemuroid-touchinput-DUPLICATE" 2>nul
mkdir "%BACKUP_DIR%\assets_bak" 2>nul
mkdir "%BACKUP_DIR%\cores_test" 2>nul
mkdir "%BACKUP_DIR%\temp_files" 2>nul
mkdir "%BACKUP_DIR%\logs" 2>nul
mkdir "%BACKUP_DIR%\docs_obsoletes" 2>nul
mkdir "%BACKUP_DIR%\scripts_obsoletes" 2>nul
echo.

echo [1/8] Backup module duplique...
if exist "ChatAI-Android\lemuroid-touchinput" (
    xcopy "ChatAI-Android\lemuroid-touchinput" "%BACKUP_DIR%\lemuroid-touchinput-DUPLICATE\" /E /I /Q /Y >nul
    echo [OK] Module duplique sauvegarde
) else (
    echo [SKIP] Module introuvable
)
echo.

echo [2/8] Backup fichiers .bak assets...
if exist "app\src\main\assets\webapp\chat.js.bak" (
    copy "app\src\main\assets\webapp\chat.js.bak" "%BACKUP_DIR%\assets_bak\" >nul
    echo [OK] chat.js.bak sauvegarde
)
if exist "app\src\main\assets\webapp\index.html.bak" (
    copy "app\src\main\assets\webapp\index.html.bak" "%BACKUP_DIR%\assets_bak\" >nul
    echo [OK] index.html.bak sauvegarde
)
if exist "app\src\main\assets\webapp\kitt-originale.html" (
    copy "app\src\main\assets\webapp\kitt-originale.html" "%BACKUP_DIR%\assets_bak\" >nul
    echo [OK] kitt-originale.html sauvegarde
)
echo.

echo [3/8] Backup core test-gl.so...
if exist "app\src\main\jniLibs\arm64-v8a\libretro-test-gl.so" (
    copy "app\src\main\jniLibs\arm64-v8a\libretro-test-gl.so" "%BACKUP_DIR%\cores_test\" >nul
    echo [OK] libretro-test-gl.so sauvegarde
) else (
    echo [SKIP] Core test introuvable
)
echo.

echo [4/8] Backup fichiers temporaires...
if exist "browse_test.html" copy "browse_test.html" "%BACKUP_DIR%\temp_files\" >nul
if exist "temp_loader_psx.js" copy "temp_loader_psx.js" "%BACKUP_DIR%\temp_files\" >nul
if exist "gradle-temp.zip" copy "gradle-temp.zip" "%BACKUP_DIR%\temp_files\" >nul
if exist "cool_spot_check" xcopy "cool_spot_check" "%BACKUP_DIR%\temp_files\cool_spot_check\" /E /I /Q /Y >nul
echo [OK] Fichiers temporaires sauvegardes
echo.

echo [5/8] Backup logs de compilation...
copy "*.log" "%BACKUP_DIR%\logs\" >nul 2>&1
echo [OK] Logs sauvegardes
echo.

echo [6/8] Backup documentations obsoletes...
copy "CHEAT_SYSTEM.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "CHEAT_SYSTEM_STATUS.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "CHEAT_EXAMPLES.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "SESSION_2025-10-18_RECAP.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "LIBRETRODROID_API_REFERENCE.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "RETROARCH_CHEATS_INSTALLED.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "MIGRATION_COMPLETE.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "COMPOSE_INTEGRATION_SUCCESS.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "MIGRATION_AUDIT_REPORT.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "FONCTIONNALITES_COMPLETES.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "FONCTIONNALITES_RESTAUREES.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "CORRECTIONS_APPLIQUEES.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "ARCHITECTURE_MULTI_ACTIVITES.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "GUIDE_INTERFACE.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "CORRECTIONS_MATERIAL3.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "MATERIAL3_CONFIGURATION.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "INTEGRATION_KITT.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "ACCES_KITT_SIMPLE.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "GUIDE_ACCES_KITT.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "AUDIT_COMPLET.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "AUDIT_OKHTTP.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "AUDIT_SERVEURS_FICHIERS.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "ENDPOINTS_DOCUMENTATION.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "css_card.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
copy "csshtmljs.md" "%BACKUP_DIR%\docs_obsoletes\" >nul 2>&1
echo [OK] Documentations obsoletes sauvegardees
echo.

echo [7/8] Backup scripts obsoletes...
copy "debug_music.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "debug_music_filtered.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "debug_msq.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "debug_storage_config.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "debug_service_restart.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "launch_kitt.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "launch_kitt_direct.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "force_config_direct.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "force_fix_final.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "force_save_config.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "force_storage_config.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "fix_storage_permanent.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_directory_debug.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_directory_listing.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_external_access.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_file_server.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_music.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_sites_simple.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_storage_path.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_user_sites.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "test_web_interface.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "setup_chatai_files.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
copy "restart_service.bat" "%BACKUP_DIR%\scripts_obsoletes\" >nul 2>&1
echo [OK] Scripts obsoletes sauvegardes
echo.

echo [8/8] Creation du manifest...
(
echo BACKUP AVANT NETTOYAGE - %date% %time%
echo ========================================
echo.
echo FICHIERS SAUVEGARDES:
echo.
echo 1. MODULE DUPLIQUE:
echo    - ChatAI-Android/lemuroid-touchinput/
echo.
echo 2. FICHIERS .BAK ASSETS:
echo    - app/src/main/assets/webapp/chat.js.bak ^(43.8 KB^)
echo    - app/src/main/assets/webapp/index.html.bak ^(15.6 KB^)
echo    - app/src/main/assets/webapp/kitt-originale.html
echo.
echo 3. CORE DE TEST:
echo    - app/src/main/jniLibs/arm64-v8a/libretro-test-gl.so
echo.
echo 4. FICHIERS TEMPORAIRES:
echo    - browse_test.html
echo    - temp_loader_psx.js
echo    - gradle-temp.zip
echo    - cool_spot_check/
echo.
echo 5. LOGS DE COMPILATION:
echo    - compile.log
echo    - compile_error.log
echo    - compile_error2.log
echo    - compile_error3.log
echo    - compile_error_full.log
echo.
echo 6. DOCUMENTATIONS OBSOLETES ^(24 fichiers .md^)
echo.
echo 7. SCRIPTS OBSOLETES ^(23 fichiers .bat^)
echo.
echo ========================================
echo POUR RESTAURER UN FICHIER:
echo    copy "%BACKUP_DIR%\[categorie]\[fichier]" "[destination]"
echo ========================================
) > "%BACKUP_DIR%\MANIFEST.txt"

echo [OK] Manifest cree
echo.

echo ========================================
echo    BACKUP TERMINE !
echo ========================================
echo.
echo Repertoire: %BACKUP_DIR%
echo.
echo Pour voir le contenu:
echo    dir "%BACKUP_DIR%" /S
echo.
echo Pour restaurer tout:
echo    xcopy "%BACKUP_DIR%\*" . /E /Y
echo.
pause


