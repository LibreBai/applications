@echo off
rem Build script - ASCII only. (cmd reads .bat files in ANSI codepage; keep this file pure ASCII.)
cd /d %~dp0
set CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist icon.ico call powershell -NoProfile -ExecutionPolicy Bypass -File make_icon.ps1
if not exist "..\..\build" mkdir "..\..\build"
"%CSC%" /nologo /codepage:65001 /t:winexe /out:..\..\build\NiuMaIncome.exe /win32icon:icon.ico /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll Program.cs
if errorlevel 1 (echo BUILD FAILED) else (echo BUILD OK - output: build\NiuMaIncome.exe)
