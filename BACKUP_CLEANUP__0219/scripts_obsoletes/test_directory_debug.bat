@echo off
echo Test Debug Directory Listing
echo.

echo Test de l'API FileServer...
curl -s http://192.168.131.217:8082/api/files/list
echo.

echo.
echo Test du directory listing HTML...
curl -s http://192.168.131.217:8080/files
echo.

echo.
echo Test des sites utilisateur...
curl -s http://192.168.131.217:8080/sites
echo.

echo.
echo Verification des fichiers sur le device...
adb shell "ls -la /storage/emulated/0/ChatAI-Files/"
echo.

echo Debug termine.
pause
