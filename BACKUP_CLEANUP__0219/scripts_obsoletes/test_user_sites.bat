@echo off
echo Test des Sites Utilisateur ChatAI
echo.

set DEVICE_IP=192.168.131.217
echo Adresse IP du device: %DEVICE_IP%
echo.

echo Test de la liste des sites utilisateur...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/sites
echo.

echo Test de l'acces aux sites utilisateur...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/user-sites
echo.

echo Test d'un site specifique (exemple)...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/sites/mon-site
echo.

echo URLs disponibles pour les sites utilisateur:
echo - Liste des sites: http://%DEVICE_IP%:8080/sites
echo - Sites alternatifs: http://%DEVICE_IP%:8080/user-sites
echo - Site specifique: http://%DEVICE_IP%:8080/sites/[nom-du-site]
echo.

echo Pour creer un site utilisateur:
echo 1. Allez dans le dossier de stockage configure
echo 2. Creez un dossier 'sites'
echo 3. Ajoutez vos fichiers HTML/CSS/JS
echo 4. Accedez via http://%DEVICE_IP%:8080/sites/
echo.

echo Test termine.
pause
