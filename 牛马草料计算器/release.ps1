# 一键发布：编译桌面版 + 同步网页版 -> dist/
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File release.ps1
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

# 1) 编译桌面版 -> build\NiuMaIncome.exe（build.bat 自动建目录、补图标）
cmd /c "`"$root\src\desktop\build.bat`""
if (-not (Test-Path "$root\build\NiuMaIncome.exe")) { throw "desktop build failed" }

# 2) 发布物就位 -> dist/（只放交付用户的文件）
if (-not (Test-Path "$root\dist")) { New-Item -ItemType Directory -Path "$root\dist" | Out-Null }
Copy-Item "$root\build\NiuMaIncome.exe" "$root\dist\牛马草料计算器.exe" -Force
Copy-Item "$root\src\web\index.html" "$root\dist\牛马草料计算器网页版.html" -Force

Write-Host "RELEASE OK -> dist\ (牛马草料计算器.exe / 牛马草料计算器网页版.html)"
