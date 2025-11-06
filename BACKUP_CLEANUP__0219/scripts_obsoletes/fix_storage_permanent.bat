@echo off
echo Configuration Permanente du Stockage ChatAI
echo.

echo Arret de l'application...
adb shell "am force-stop com.chatai"

echo.
echo Configuration PERMANENTE via SharedPreferences...
adb shell "am broadcast -a com.chatai.SAVE_STORAGE_CONFIG --es storage_path '/storage/emulated/0/site/unified'"

echo.
echo Creation du dossier de stockage PERMANENT...
adb shell "mkdir -p /storage/emulated/0/site/unified"
adb shell "mkdir -p /storage/emulated/0/site/unified/sites"

echo.
echo Creation de fichiers de test PERMANENTS...
adb shell "echo '<html><body><h1>Mon Site Test</h1><p>Ceci est un test permanent</p></body></html>' > /storage/emulated/0/site/unified/sites/index.html"
adb shell "echo 'Fichier de test permanent' > /storage/emulated/0/site/unified/test.txt"
adb shell "echo 'Documentation' > /storage/emulated/0/site/unified/README.txt"

echo.
echo Configuration PERMANENTE sauvegardee dans l'app...
adb shell "am broadcast -a com.chatai.FORCE_STORAGE_CONFIG --es storage_path '/storage/emulated/0/site/unified'"

echo.
echo Redemarrage de l'application...
adb shell "am start -n com.chatai/.MainActivity"

echo.
echo Attente du demarrage...
timeout /t 15

echo.
echo Test de la configuration PERMANENTE...
curl -s http://192.168.131.217:8082/api/files/storage/info
echo.

echo.
echo Test du directory listing PERMANENT...
curl -s http://192.168.131.217:8080/files
echo.

echo.
echo Test des sites utilisateur PERMANENTS...
curl -s http://192.168.131.217:8080/sites
echo.

echo.
echo Configuration PERMANENTE terminee.
echo Le stockage est maintenant configure sur /storage/emulated/0/site/unified
echo Plus besoin de reconfigurer a chaque fois !
pause
