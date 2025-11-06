@echo off
echo === DEBUG INDICATEUR MSQ KITT ===
echo.
echo Installation de l'APK...
adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
echo Démarrage de l'application...
adb shell am start -n com.chatai/.activities.KittActivity
echo.
echo === SURVEILLANCE DES LOGS MSQ ET MUSIQUE ===
echo Appuyez sur Ctrl+C pour arrêter
echo.
adb logcat | findstr "Music MSQ com.chatai"
