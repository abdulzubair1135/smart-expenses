@echo off
@chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==============================================================================
echo [INSTALL] SMART EXPENSES TRACKER - USB APK INSTALLER
echo ==============================================================================

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%"

set "ADB_EXE=C:\Users\abdul\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB_EXE%" (
    where adb >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        set "ADB_EXE=adb"
    ) else (
        echo [ERROR] ADB not found at %ADB_EXE% or in PATH!
        pause
        exit /b 1
    )
)

echo [1/4] Detecting USB Connected Android Devices...
"%ADB_EXE%" devices > devices_temp.txt
type devices_temp.txt

findstr /r /c:"[a-zA-Z0-9].*device$" devices_temp.txt >nul
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [WARNING] No authorized device attached or device is unauthorized/offline!
    echo - Make sure USB Debugging is ENABLED on your Android phone.
    echo - Check your phone screen and tap 'Always allow from this computer'.
    del devices_temp.txt >nul 2>nul
    pause
    exit /b 1
)
del devices_temp.txt >nul 2>nul

echo [2/4] Verifying Compiled Debug APK...
set "APK_PATH=%PROJECT_ROOT%APK\SmartExpenses-v1.0.apk"
if not exist "%APK_PATH%" (
    set "APK_PATH=%PROJECT_ROOT%android\app\build\outputs\apk\debug\app-debug.apk"
)
if not exist "%APK_PATH%" (
    echo [INFO] APK not found in APK folder. Triggering Gradle build...
    cd /d "%PROJECT_ROOT%android"
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    call "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%PROJECT_ROOT%android\gradle\wrapper\gradle-wrapper.jar" assembleDebug
    cd /d "%PROJECT_ROOT%"
    set "APK_PATH=%PROJECT_ROOT%android\app\build\outputs\apk\debug\app-debug.apk"
)

if not exist "%APK_PATH%" (
    echo [ERROR] Failed to locate or build APK at %APK_PATH%!
    pause
    exit /b 1
)

echo [3/4] Installing APK to phone (%APK_PATH%)...
"%ADB_EXE%" install -r "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ADB Install failed! Please inspect device connection.
    pause
    exit /b 1
)

echo [4/4] Launching Smart Expenses Tracker on device...
"%ADB_EXE%" shell am start -n "com.smarttracker/com.smarttracker.ui.splash.SplashActivity"

echo.
echo ==============================================================================
echo [SUCCESS] APK successfully installed and launched on your Android phone!
echo ==============================================================================
pause
