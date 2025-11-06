@echo off
echo ========================================
echo    NETTOYAGE DU PROJET ChatAI-Android
echo ========================================
echo.
echo [INFO] Backup disponible dans: BACKUP_CLEANUP__0219
echo.

set /a count=0

echo [1/7] Suppression du module duplique...
if exist "ChatAI-Android\lemuroid-touchinput" (
    rmdir /S /Q "ChatAI-Android\lemuroid-touchinput"
    echo [OK] Module duplique supprime
    set /a count+=44
) else (
    echo [SKIP] Deja supprime
)
echo.

echo [2/7] Suppression des fichiers .bak dans assets...
if exist "app\src\main\assets\webapp\chat.js.bak" (
    del /Q "app\src\main\assets\webapp\chat.js.bak"
    echo [OK] chat.js.bak supprime (-43.8 KB)
    set /a count+=1
)
if exist "app\src\main\assets\webapp\index.html.bak" (
    del /Q "app\src\main\assets\webapp\index.html.bak"
    echo [OK] index.html.bak supprime (-15.6 KB)
    set /a count+=1
)
if exist "app\src\main\assets\webapp\kitt-originale.html" (
    del /Q "app\src\main\assets\webapp\kitt-originale.html"
    echo [OK] kitt-originale.html supprime
    set /a count+=1
)
echo.

echo [3/7] Suppression du core de test...
if exist "app\src\main\jniLibs\arm64-v8a\libretro-test-gl.so" (
    del /Q "app\src\main\jniLibs\arm64-v8a\libretro-test-gl.so"
    echo [OK] libretro-test-gl.so supprime (-~90 KB)
    set /a count+=1
) else (
    echo [SKIP] Deja supprime
)
echo.

echo [4/7] Suppression des fichiers temporaires...
if exist "browse_test.html" (
    del /Q "browse_test.html"
    set /a count+=1
)
if exist "temp_loader_psx.js" (
    del /Q "temp_loader_psx.js"
    set /a count+=1
)
if exist "gradle-temp.zip" (
    del /Q "gradle-temp.zip"
    set /a count+=1
)
if exist "cool_spot_check" (
    rmdir /S /Q "cool_spot_check"
    set /a count+=1
)
echo [OK] Fichiers temporaires supprimes
echo.

echo [5/7] Suppression des logs de compilation...
del /Q "*.log" >nul 2>&1
echo [OK] Logs supprimes (5 fichiers)
set /a count+=5
echo.

echo [6/7] Suppression des documentations obsoletes...
del /Q "CHEAT_SYSTEM.md" >nul 2>&1
del /Q "CHEAT_SYSTEM_STATUS.md" >nul 2>&1
del /Q "CHEAT_EXAMPLES.md" >nul 2>&1
del /Q "SESSION_2025-10-18_RECAP.md" >nul 2>&1
del /Q "LIBRETRODROID_API_REFERENCE.md" >nul 2>&1
del /Q "RETROARCH_CHEATS_INSTALLED.md" >nul 2>&1
del /Q "MIGRATION_COMPLETE.md" >nul 2>&1
del /Q "COMPOSE_INTEGRATION_SUCCESS.md" >nul 2>&1
del /Q "MIGRATION_AUDIT_REPORT.md" >nul 2>&1
del /Q "FONCTIONNALITES_COMPLETES.md" >nul 2>&1
del /Q "FONCTIONNALITES_RESTAUREES.md" >nul 2>&1
del /Q "CORRECTIONS_APPLIQUEES.md" >nul 2>&1
del /Q "ARCHITECTURE_MULTI_ACTIVITES.md" >nul 2>&1
del /Q "GUIDE_INTERFACE.md" >nul 2>&1
del /Q "CORRECTIONS_MATERIAL3.md" >nul 2>&1
del /Q "MATERIAL3_CONFIGURATION.md" >nul 2>&1
del /Q "INTEGRATION_KITT.md" >nul 2>&1
del /Q "ACCES_KITT_SIMPLE.md" >nul 2>&1
del /Q "GUIDE_ACCES_KITT.md" >nul 2>&1
del /Q "AUDIT_COMPLET.md" >nul 2>&1
del /Q "AUDIT_OKHTTP.md" >nul 2>&1
del /Q "AUDIT_SERVEURS_FICHIERS.md" >nul 2>&1
del /Q "ENDPOINTS_DOCUMENTATION.md" >nul 2>&1
del /Q "css_card.md" >nul 2>&1
del /Q "csshtmljs.md" >nul 2>&1
echo [OK] Documentations obsoletes supprimees (24 fichiers)
set /a count+=24
echo.

echo [7/7] Suppression des scripts obsoletes...
del /Q "debug_music.bat" >nul 2>&1
del /Q "debug_music_filtered.bat" >nul 2>&1
del /Q "debug_msq.bat" >nul 2>&1
del /Q "debug_storage_config.bat" >nul 2>&1
del /Q "debug_service_restart.bat" >nul 2>&1
del /Q "launch_kitt.bat" >nul 2>&1
del /Q "launch_kitt_direct.bat" >nul 2>&1
del /Q "force_config_direct.bat" >nul 2>&1
del /Q "force_fix_final.bat" >nul 2>&1
del /Q "force_save_config.bat" >nul 2>&1
del /Q "force_storage_config.bat" >nul 2>&1
del /Q "fix_storage_permanent.bat" >nul 2>&1
del /Q "test_directory_debug.bat" >nul 2>&1
del /Q "test_directory_listing.bat" >nul 2>&1
del /Q "test_external_access.bat" >nul 2>&1
del /Q "test_file_server.bat" >nul 2>&1
del /Q "test_music.bat" >nul 2>&1
del /Q "test_sites_simple.bat" >nul 2>&1
del /Q "test_storage_path.bat" >nul 2>&1
del /Q "test_user_sites.bat" >nul 2>&1
del /Q "test_web_interface.bat" >nul 2>&1
del /Q "setup_chatai_files.bat" >nul 2>&1
del /Q "restart_service.bat" >nul 2>&1
echo [OK] Scripts obsoletes supprimes (23 fichiers)
set /a count+=23
echo.

echo ========================================
echo    NETTOYAGE TERMINE !
echo ========================================
echo.
echo Fichiers supprimes: %count% fichiers environ
echo.
echo Gain APK estime: ~10 MB
echo Gain workspace: Beaucoup plus propre !
echo.
echo Backup disponible: BACKUP_CLEANUP__0219\
echo.
echo Scripts conserves (essentiels):
echo   - test_all_apis.bat
echo   - test_servers.bat
echo   - debug_all.bat
echo   - debug_final.bat
echo   - launch_kitt_final.bat
echo   - audit_permissions.bat
echo.
echo Documentations conservees (essentielles):
echo   - README.md
echo   - CHEAT_FINAL_STATUS.md
echo   - GAMEPAD_INTEGRATION.md
echo   - app/src/main/java/com/chatai/cheat/README.md
echo.
pause


