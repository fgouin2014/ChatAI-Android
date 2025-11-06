@echo off
echo Test du Directory Listing ChatAI
echo.

set DEVICE_IP=192.168.131.217
echo Adresse IP du device: %DEVICE_IP%
echo.

echo Test du directory listing...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/files
echo.

echo Test du directory listing avec slash...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/files/
echo.

echo Test du browse...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/browse
echo.

echo Test de l'API files/list...
curl -s -w "Status: %%{http_code}\n" http://%DEVICE_IP%:8080/api/files/list
echo.

echo URLs disponibles:
echo - Directory Listing: http://%DEVICE_IP%:8080/files
echo - Browse: http://%DEVICE_IP%:8080/browse
echo - API Files: http://%DEVICE_IP%:8080/api/files/list
echo - API Storage Info: http://%DEVICE_IP%:8080/api/files/storage/info
echo.

echo Test termine.
pause
