@echo off
echo Test de l'interface web ChatAI
echo.

set DEVICE_IP=192.168.131.217
echo Adresse IP du device: %DEVICE_IP%
echo.

echo Test de l'interface principale...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/
echo.

echo Test de l'interface system...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/system.html
echo.

echo Test du fichier JavaScript...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/chat.js
echo.

echo Test des APIs...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/api/status
echo.

echo URLs disponibles:
echo - Interface principale: http://%DEVICE_IP%:8080/
echo - Interface system: http://%DEVICE_IP%:8080/system.html
echo - API Status: http://%DEVICE_IP%:8080/api/status
echo - API Plugins: http://%DEVICE_IP%:8080/api/plugins
echo - API Health: http://%DEVICE_IP%:8080/api/health
echo.

echo Test termine.
pause
