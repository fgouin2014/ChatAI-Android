@echo off
echo Redemarrage du Service ChatAI
echo.

echo Arret de l'application ChatAI...
adb shell "am force-stop com.chatai"

echo.
echo Attente de l'arret...
timeout /t 3

echo.
echo Redemarrage de l'application...
adb shell "am start -n com.chatai/.MainActivity"

echo.
echo Attente du demarrage du service...
timeout /t 10

echo.
echo Test du service...
curl -s http://192.168.131.217:8080/api/status
echo.

echo.
echo Test du File Server...
curl -s http://192.168.131.217:8082/api/files/storage/info
echo.

echo Service redemarre.
pause
