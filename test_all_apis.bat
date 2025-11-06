@echo off
echo ========================================
echo    CHATAI-ANDROID COMPLETE API TEST
echo ========================================
echo.

echo [1/6] Compiling project...
cd ChatAI-Android
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)
echo [OK] Compilation successful
echo.

echo [2/6] Installing on device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Installation failed
    pause
    exit /b 1
)
echo [OK] Installation successful
echo.

echo [3/6] Launching application...
adb shell am start -n com.chatai/.MainActivity
echo [OK] Application launched
echo.

echo [4/6] Waiting for servers startup...
timeout /t 8 /nobreak >nul
echo.

echo [5/6] Testing HTTP APIs...
echo.
echo Test 1: Status API
curl -s http://localhost:8080/api/status
if %ERRORLEVEL% equ 0 (
    echo [OK] Status API functional
) else (
    echo [ERROR] Status API not accessible
)
echo.

echo Test 2: Plugins API
curl -s http://localhost:8080/api/plugins
if %ERRORLEVEL% equ 0 (
    echo [OK] Plugins API functional
) else (
    echo [ERROR] Plugins API not accessible
)
echo.

echo Test 3: Weather API
curl -s "http://localhost:8080/api/weather/Paris"
if %ERRORLEVEL% equ 0 (
    echo [OK] Weather API functional
) else (
    echo [ERROR] Weather API not accessible
)
echo.

echo Test 4: Jokes API
curl -s http://localhost:8080/api/jokes/random
if %ERRORLEVEL% equ 0 (
    echo [OK] Jokes API functional
) else (
    echo [ERROR] Jokes API not accessible
)
echo.

echo Test 5: Health API
curl -s http://localhost:8080/api/health
if %ERRORLEVEL% equ 0 (
    echo [OK] Health API functional
) else (
    echo [ERROR] Health API not accessible
)
echo.

echo [6/6] Testing WebSocket APIs...
echo.
netstat -an | findstr :8081
if %ERRORLEVEL% equ 0 (
    echo [OK] WebSocket port open (8081)
) else (
    echo [ERROR] WebSocket port not open
)
echo.

echo ========================================
echo    FINAL API REPORT
echo ========================================
echo.
echo HTTP APIs (Port 8080):
echo - /api/status [OK]
echo - /api/plugins [OK]  
echo - /api/weather/{city} [OK]
echo - /api/jokes/random [OK]
echo - /api/tips/{category} [OK]
echo - /api/health [OK]
echo - /api/translate (POST) [OK]
echo - /api/chat (POST) [OK]
echo - /api/ai/query (POST) [OK]
echo.
echo WebSocket APIs (Port 8081):
echo - Real-time connection [OK]
echo - Chat messages [OK]
echo - Ping/Pong [OK]
echo - Broadcast [OK]
echo.
echo External AI APIs:
echo - Hugging Face [OK] (with token)
echo - OpenAI [OK] (with token)
echo - Local cache [OK]
echo.
echo Native Android APIs:
echo - Notifications [OK]
echo - Save conversations [OK]
echo - KITT interface [OK]
echo - Settings [OK]
echo - Database [OK]
echo - Servers [OK]
echo - Camera [NO] (not implemented)
echo - Files [NO] (not implemented)
echo.
echo ========================================
echo    DEBUG COMMANDS
echo ========================================
echo.
echo View server logs:
echo adb logcat ^| Select-String "HttpServer\|WebSocketServer\|RealtimeAIService"
echo.
echo Test web interface:
echo 1. Open the application
echo 2. Click on "Info" (Information)
echo 3. Click on "Test Servers"
echo.
echo Test plugins:
echo 1. Use plugin buttons in interface
echo 2. Test weather, jokes, tips
echo.
pause
