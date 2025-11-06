@echo off
echo Fix Final du Problème de Stockage
echo.

echo Arret de l'application...
adb shell "am force-stop com.chatai"

echo.
echo Creation du dossier de stockage...
adb shell "mkdir -p /storage/emulated/0/site/unified"
adb shell "mkdir -p /storage/emulated/0/site/unified/sites"

echo.
echo Creation de fichiers de test...
adb shell "echo '<html><body><h1>Mon Site Test</h1><p>Ceci est un test</p></body></html>' > /storage/emulated/0/site/unified/sites/index.html"
adb shell "echo 'Fichier de test' > /storage/emulated/0/site/unified/test.txt"

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
echo Le probleme est que le code ne s'execute pas.
echo Il faut recompiler et reinstaller l'application.
echo.

echo Fix final termine.
pause
