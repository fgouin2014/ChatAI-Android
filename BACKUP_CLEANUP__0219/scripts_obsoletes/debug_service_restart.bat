@echo off
echo Debug Redemarrage Service ChatAI
echo.

echo Arret de l'application...
adb shell "am force-stop com.chatai"

echo.
echo Verification des logs de service...
adb logcat | Select-String "BackgroundService\|FileServer\|storage_path" | head -10

echo.
echo Redemarrage de l'application...
adb shell "am start -n com.chatai/.MainActivity"

echo.
echo Attente du demarrage...
timeout /t 15

echo.
echo Verification des logs apres redemarrage...
adb logcat | Select-String "BackgroundService\|FileServer\|storage_path" | head -10

echo.
echo Test de la configuration...
curl -s http://192.168.131.217:8082/api/files/storage/info
echo.

echo Debug termine.
pause
