@echo off
REM ==========================================
REM K8s log viewer - Windows launcher
REM Bypasses PowerShell execution policy; works from cmd / PowerShell / Run dialog
REM Usage: klog.cmd <service or JAR prefix> [keyword] [-f] [lines]
REM ==========================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0klog.ps1" %*
exit /b %ERRORLEVEL%
