@echo off
echo ========================================
echo    DIRECT KITT INTERFACE LAUNCH
echo ========================================
echo.

echo Compiling KITT project only...
call gradlew :app:compileDebugKotlin

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Kotlin compilation failed
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
echo Direct launch of KITT interface...
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
