@echo off
echo Test du chemin de stockage storage/emulated/0/site/unified
echo.

echo Verification des permissions Android...
adb shell ls -la /storage/emulated/0/
echo.

echo Test de creation du repertoire site...
adb shell mkdir -p /storage/emulated/0/site
echo.

echo Test de creation du repertoire unified...
adb shell mkdir -p /storage/emulated/0/site/unified
echo.

echo Verification de l'acces au repertoire...
adb shell ls -la /storage/emulated/0/site/
echo.

echo Test d'ecriture dans le repertoire...
adb shell touch /storage/emulated/0/site/unified/test.txt
echo.

echo Verification du fichier de test...
adb shell ls -la /storage/emulated/0/site/unified/
echo.

echo Nettoyage du fichier de test...
adb shell rm /storage/emulated/0/site/unified/test.txt
echo.

echo Test termine.
pause
