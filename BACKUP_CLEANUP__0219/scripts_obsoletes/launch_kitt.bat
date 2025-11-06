@echo off
echo ========================================
echo    LAUNCH KITT INTERFACE
echo ========================================
echo.

echo Compiling project...
call gradlew assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Compilation failed
    pause
    exit /b 1
)

echo.
echo Installing APK...
call gradlew installDebug

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Installation failed
    pause
    exit /b 1
)

echo.
echo Launching KITT interface...
adb shell am start -n com.chatai/.activities.KittActivity

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Launch failed
    echo Check that your device is connected
    pause
    exit /b 1
)

echo.
echo ========================================
echo    KITT INTERFACE LAUNCHED !
echo ========================================
echo.
echo The KITT interface should now open on your device.
echo.
echo Available features:
echo - Power Switch to activate KITT
echo - Animated scanner with 24 segments
echo - VU-meter with 3 modes
echo - Complete voice interface
echo - KITT commands menu
echo.
pause
