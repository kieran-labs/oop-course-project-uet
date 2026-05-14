@echo off
setlocal
cd /d "%~dp0"

call "%~dp0server-stop.bat"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$root = (Resolve-Path .).Path;" ^
  "$targets = @('.\data\postgres', '.\data\postgres.pid');" ^
  "foreach ($t in $targets) { $resolved = Resolve-Path -LiteralPath $t -ErrorAction SilentlyContinue; if ($resolved) { if (-not $resolved.Path.StartsWith($root)) { throw ('Refusing to remove path outside workspace: ' + $resolved.Path) }; Remove-Item -LiteralPath $resolved.Path -Recurse -Force } }" ^
  "Write-Host 'Database reset complete. data\postgres will be recreated on next server start.'"

exit /b %ERRORLEVEL%
