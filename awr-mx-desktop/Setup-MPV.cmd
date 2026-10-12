@echo off
setlocal
cd /d "%~dp0"
echo ==============================================
echo    AWR MX Desktop - MPV Setup v4
echo ==============================================
echo.
echo The installer will first check your old download cache.
echo If a previous download was interrupted, it can resume.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Setup-MPV.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if "%RESULT%"=="0" (
    echo SUCCESS: mpv is ready. Open AWR-MX-Desktop.exe
) else (
    echo Error. Open Setup-MPV.log and send the last lines.
    echo You can put the official Windows mpv ZIP next to this CMD.
)
echo.
pause
exit /b %RESULT%
