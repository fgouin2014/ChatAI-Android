@echo off
echo ========================================
echo AUDIT COMPLET DES AUTORISATIONS ANDROID
echo ========================================
echo.

echo 1. VERIFICATION DES PERMISSIONS DEMANDEES...
echo.
adb shell dumpsys package com.chatai | findstr "permission"
echo.

echo 2. VERIFICATION DES PERMISSIONS ACCORDEES...
echo.
adb shell pm list permissions -d -u | findstr "READ_EXTERNAL_STORAGE\|WRITE_EXTERNAL_STORAGE\|MANAGE_EXTERNAL_STORAGE\|READ_MEDIA_IMAGES\|READ_MEDIA_VIDEO\|READ_MEDIA_AUDIO\|INTERNET\|CAMERA\|RECORD_AUDIO"
echo.

echo 3. VERIFICATION DES PERMISSIONS DE L'APP...
echo.
adb shell pm list permissions -d -u | findstr "com.chatai"
echo.

echo 4. VERIFICATION DU STOCKAGE EXTERNE...
echo.
adb shell ls -la /storage/emulated/0/ | findstr "ChatAI"
echo.

echo 5. VERIFICATION DES PORTS RESEAU...
echo.
netstat -an | findstr "8080\|8081\|8082\|8083"
echo.

echo 6. VERIFICATION DES PROCESSUS ANDROID...
echo.
adb shell ps | findstr "chatai"
echo.

echo 7. VERIFICATION DES LOGS D'ERREUR...
echo.
adb logcat -d | findstr "Permission\|Storage\|Network\|Server" | tail -20
echo.

echo 8. VERIFICATION DES SERVICES ACTIFS...
echo.
adb shell dumpsys activity services | findstr "chatai"
echo.

echo ========================================
echo AUDIT TERMINE
echo ========================================
pause
