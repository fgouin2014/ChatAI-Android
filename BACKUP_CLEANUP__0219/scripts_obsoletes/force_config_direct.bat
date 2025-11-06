@echo off
echo Configuration Forcee Directe du Stockage
echo.

echo Arret de l'application...
adb shell "am force-stop com.chatai"

echo.
echo Configuration directe via SharedPreferences...
adb shell "am broadcast -a com.chatai.FORCE_STORAGE_CONFIG --es storage_path '/storage/emulated/0/site/unified'"

echo.
echo Creation du dossier de stockage...
adb shell "mkdir -p /storage/emulated/0/site/unified"
adb shell "mkdir -p /storage/emulated/0/site/unified/sites"

echo.
echo Creation de fichiers de test...
adb shell "echo '<html><body><h1>Test Site</h1></body></html>' > /storage/emulated/0/site/unified/sites/test.html"
adb shell "echo 'Test file content' > /storage/emulated/0/site/unified/test.txt"

echo.
echo Redemarrage de l'application...
adb shell "am start -n com.chatai/.MainActivity"

echo.
echo Attente du demarrage...
timeout /t 15

echo.
echo Test de la configuration...
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

echo Configuration forcee terminee.
pause
