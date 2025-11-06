@echo off
echo === DEBUG MUSIQUE KITT FILTRÉ ===
echo.
echo Installation de l'APK...
adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
echo Démarrage de l'application...
adb shell am start -n com.chatai/.activities.KittActivity
echo.
echo === LOGS MUSIQUE CHATAI ===
echo Appuyez sur Ctrl+C pour arrêter
echo.
adb logcat | findstr "Music com.chatai"

