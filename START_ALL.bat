@echo off
@chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==============================================================================
echo [START] SMART EXPENSES TRACKER - SERVICES LAUNCHER
echo ==============================================================================

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%"

echo [1/4] Checking Node.js runtime...
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js is not found in PATH! Please install Node.js v18+.
    pause
    exit /b 1
)

echo [2/4] Starting Backend API Service (Port 4000)...
start "SmartExpenses-Backend" cmd /k "chcp 65001 >nul && cd /d "%PROJECT_ROOT%backend" && npm run dev"

echo [3/4] Starting React Admin Panel (Port 5173)...
start "SmartExpenses-Admin" cmd /k "chcp 65001 >nul && cd /d "%PROJECT_ROOT%admin" && npm run dev"

echo [4/4] Verifying local development endpoints...
echo - Backend API:  http://localhost:4000
echo - Backend Health: http://localhost:4000/health
echo - Admin Portal:  http://localhost:5173
echo.
echo ==============================================================================
echo [SUCCESS] Local services launched in dedicated terminal windows!
echo Use STOP_ALL.bat to shut down local development processes.
echo ==============================================================================
