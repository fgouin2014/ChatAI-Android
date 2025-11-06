@echo off
echo Configuration Forcee du Stockage ChatAI
echo.

echo Configuration du chemin de stockage vers /storage/emulated/0/site/unified...
adb shell "am broadcast -a com.chatai.FORCE_STORAGE_CONFIG --es storage_path '/storage/emulated/0/site/unified'"

echo.
echo Redemarrage de l'application ChatAI...
adb shell "am force-stop com.chatai"
timeout /t 2
adb shell "am start -n com.chatai/.MainActivity"

echo.
echo Attente du demarrage...
timeout /t 10

echo.
echo Test de la nouvelle configuration...
curl -s http://192.168.131.217:8082/api/files/storage/info
echo.

echo.
echo Test du directory listing...
curl -s http://192.168.131.217:8080/files
echo.

echo.
echo Test des sites utilisateur...
curl -s http://192.168.131.217:8080/sites
echo.

echo Configuration terminee.
pause