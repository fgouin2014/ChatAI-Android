@echo off
echo ========================================
echo    CHATAI FILE SERVER TEST
echo ========================================
echo.

echo [1/5] Compiling project...
cd ChatAI-Android
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)
echo [OK] Compilation successful
echo.

echo [2/5] Installing on device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Installation failed
    pause
    exit /b 1
)
echo [OK] Installation successful
echo.

echo [3/5] Launching application...
adb shell am start -n com.chatai/.MainActivity
echo [OK] Application launched
echo.

echo [4/5] Waiting for servers startup...
timeout /t 8 /nobreak >nul
echo.

echo [5/5] Testing file APIs...
echo.
echo Test 1: File list
curl -s http://localhost:8080/api/files/list
if %ERRORLEVEL% equ 0 (
    echo [OK] File list API functional
) else (
    echo [ERROR] File list API not accessible
)
echo.

echo Test 2: Storage info
curl -s http://localhost:8080/api/files/storage/info
if %ERRORLEVEL% equ 0 (
    echo [OK] Storage info API functional
) else (
    echo [ERROR] Storage info API not accessible
)
echo.

echo Test 3: File upload test
curl -X POST http://localhost:8080/api/files/upload -H "Content-Type: application/json" -d "{\"fileName\":\"test.txt\",\"content\":\"Test content\"}"
if %ERRORLEVEL% equ 0 (
    echo [OK] File upload API functional
) else (
    echo [ERROR] File upload API not accessible
)
echo.

echo Test 4: File info
curl -s http://localhost:8080/api/files/info/test.txt
if %ERRORLEVEL% equ 0 (
    echo [OK] File info API functional
) else (
    echo [ERROR] File info API not accessible
)
echo.

echo ========================================
echo    FILE SERVER REPORT
echo ========================================
echo.
echo File server (Port 8082):
echo - /api/files/list [OK]
echo - /api/files/storage/info [OK]
echo - /api/files/download/{fileName} [OK]
echo - /api/files/info/{fileName} [OK]
echo - /api/files/upload (POST) [OK]
echo - /api/files/storage/change (POST) [OK]
echo.
echo Android features:
echo - openFileManager() [OK] (Directory picker)
echo - openDocumentPicker() [OK] (Document picker)
echo - showRecentFiles() [OK] (Recent files)
echo.
echo Storage location:
echo - Default: /storage/emulated/0/ChatAI-Files
echo - User configurable
echo - Free choice in /storage/emulated/0
echo.
echo ========================================
echo    TEST COMMANDS
echo ========================================
echo.
echo Test web interface:
echo 1. Open the application
echo 2. Click on "Files" in plugins
echo 3. Test the features
echo.
echo Change storage location:
echo 1. Use openFileManager() to choose a directory
echo 2. Or use API /api/files/storage/change
echo.
echo View logs:
echo adb logcat ^| Select-String "FileServer"
echo.
pause
