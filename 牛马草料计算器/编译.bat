@echo off
rem One-click build for people who got this code from git. ASCII content only.
rem Compiles desktop exe + syncs web version into dist\ (calls release.ps1).
cd /d %~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1"
pause
