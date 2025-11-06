@echo off
echo Test des sites utilisateur ChatAI

echo.
echo Test du directory listing...
curl -s http://192.168.131.217:8080/browse

echo.
echo.
echo Test de la liste des sites...
curl -s http://192.168.131.217:8080/sites

echo.
echo.
echo Test d'un site specifique...
curl -s http://192.168.131.217:8080/sites/monsite

echo.
echo Test termine.
pause
