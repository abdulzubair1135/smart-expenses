@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo [STOP] STOPPING LOCAL SMART EXPENSES SERVICES
echo ==============================================================================

echo Terminating backend & admin dev processes...
taskkill /F /FI "WINDOWTITLE eq SmartExpenses-Backend*" >nul 2>nul
taskkill /F /FI "WINDOWTITLE eq SmartExpenses-Admin*" >nul 2>nul

echo Releasing ports 4000 and 5173 if occupied...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :4000') do (
    taskkill /F /PID %%a >nul 2>nul
)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5173') do (
    taskkill /F /PID %%a >nul 2>nul
)

echo [SUCCESS] All local services have been stopped.
