@echo off
echo Test d'acces externe aux serveurs ChatAI
echo.

set DEVICE_IP=192.168.131.217
echo Adresse IP du device: %DEVICE_IP%
echo.

echo Test du serveur HTTP (port 8080)...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/api/status
echo.

echo Test du serveur de fichiers (port 8082)...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8082/api/files/storage/info
echo.

echo Test des en-tetes CORS...
curl -s -I -X OPTIONS http://%DEVICE_IP%:8080/api/status
echo.

echo Test des endpoints disponibles:
echo - http://%DEVICE_IP%:8080/api/status
echo - http://%DEVICE_IP%:8080/api/plugins
echo - http://%DEVICE_IP%:8080/api/health
echo - http://%DEVICE_IP%:8082/api/files/list
echo - http://%DEVICE_IP%:8082/api/files/storage/info
echo.

echo Test termine.
pause
