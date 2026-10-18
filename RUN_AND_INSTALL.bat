@echo off
@chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==============================================================================
echo [RUN & INSTALL] SMART EXPENSES TRACKER - ALL-IN-ONE MASTER WORKFLOW
echo ==============================================================================

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%"

echo [Stage 1] Launching Local Backend and Admin Services...
call "%PROJECT_ROOT%START_ALL.bat"

echo.
echo [Stage 2] Invoking USB APK Installer...
call "%PROJECT_ROOT%INSTALL_ANDROID_USB.bat"
