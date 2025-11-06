@echo off
echo ========================================
echo    CHATAI-ANDROID SERVERS TEST
echo ========================================
echo.

echo [1/4] Compiling project...
cd ChatAI-Android
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Compilation failed
    pause
    exit /b 1
)
echo [OK] Compilation successful
echo.

echo [2/4] Installing on device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% neq 0 (
    echo ERROR: Installation failed
    pause
    exit /b 1
)
echo [OK] Installation successful
echo.

echo [3/4] Launching application...
adb shell am start -n com.chatai/.MainActivity
echo [OK] Application launched
echo.

echo [4/4] Testing servers...
timeout /t 5 /nobreak >nul
echo Testing HTTP server...
curl -s http://localhost:8080/api/status || echo [ERROR] HTTP server not accessible
echo Testing WebSocket server...
netstat -an | findstr :8081 || echo [ERROR] WebSocket port not open
echo.

echo ========================================
echo    TEST RESULTS
echo ========================================
echo.
echo To see real-time logs:
echo adb logcat ^| Select-String "HttpServer\|WebSocketServer\|RealtimeAIService"
echo.
echo To test web interface:
echo 1. Open the application
echo 2. Click on "Info" (Information) button
echo 3. Click on "Test Servers"
echo.
pause
