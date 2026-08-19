@echo off
setlocal
cd /d "%~dp0"
echo This will DELETE the local Docker containers, but keep Docker volumes.
echo PostgreSQL, MinIO and other volume data will not be deleted.
echo.
choice /M "Continue"
if errorlevel 2 exit /b 0
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\delete-local-containers.ps1"
if errorlevel 1 pause
