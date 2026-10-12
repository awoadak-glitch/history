@echo off
cd /d "%~dp0"
"%~dp0AWR-MX-Desktop.exe" --register-protocol
if errorlevel 1 (
 echo Failed to connect AWR MX. Open AWR-MX-Desktop.exe and try again.
 pause
 exit /b 1
)
start "" "https://palma-web-lac.vercel.app/"
