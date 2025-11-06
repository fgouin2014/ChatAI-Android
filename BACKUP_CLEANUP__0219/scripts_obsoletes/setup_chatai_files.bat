@echo off
echo Configuration du Repertoire ChatAI-Files
echo.

echo Arret de l'application...
adb shell "am force-stop com.chatai"

echo.
echo Creation du dossier ChatAI-Files...
adb shell "mkdir -p /storage/emulated/0/ChatAI-Files"
adb shell "mkdir -p /storage/emulated/0/ChatAI-Files/sites"

echo.
echo Creation de fichiers de test dans ChatAI-Files...
adb shell "echo '<html><body><h1>Mon Site Test</h1><p>Ceci est un test dans ChatAI-Files</p></body></html>' > /storage/emulated/0/ChatAI-Files/sites/index.html"
adb shell "echo 'Fichier de test dans ChatAI-Files' > /storage/emulated/0/ChatAI-Files/test.txt"
adb shell "echo 'Documentation' > /storage/emulated/0/ChatAI-Files/README.txt"

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

echo.
echo Configuration ChatAI-Files terminee.
echo Le repertoire par defaut est maintenant peuple avec des fichiers de test.
pause
