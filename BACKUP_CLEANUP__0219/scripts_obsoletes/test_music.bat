@echo off
echo Test de la musique KITT - Logs de debug
echo.
echo Installation de l'APK...
adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
echo Démarrage de l'application...
adb shell am start -n com.chatai/.activities.KittActivity
echo.
echo Surveillance des logs de musique...
echo Appuyez sur Ctrl+C pour arrêter
echo.
adb logcat | Select-String "Music"

