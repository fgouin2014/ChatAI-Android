@echo off
echo Debug Configuration Stockage ChatAI
echo.

echo Verification des logs de configuration...
adb logcat | Select-String "storage_path\|storagePath\|FileServer\|loadConfiguredStorage" | head -20
echo.

echo Test de la configuration actuelle...
curl -s http://192.168.131.217:8082/api/files/storage/info
echo.

echo Pour forcer le rechargement de la configuration:
echo 1. Allez dans l'app ChatAI
echo 2. Ouvrez KITT
echo 3. Allez dans "CONFIG SERVER WEB"
echo 4. Changez le chemin de stockage
echo 5. Sauvegardez
echo 6. Redemarrez l'app
echo.

echo Test termine.
pause
