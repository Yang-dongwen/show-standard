@echo off
setlocal

REM Pure ASCII launcher. Logic lives in package-desktop.ps1 (UTF-8 safe).
REM CWD may be anywhere; the .ps1 resolves the repo root itself.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-desktop.ps1"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo Packaging failed. Exit code: %ERR%
  pause
  exit /b %ERR%
)

echo.
pause
exit /b 0
