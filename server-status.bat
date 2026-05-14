@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$port = 8080;" ^
  "try { $health = Invoke-RestMethod -Uri ('http://localhost:' + $port + '/api/health') -TimeoutSec 2; if ($health.status -eq 'ok') { Write-Host ('RUNNING http://localhost:' + $port + ' PID=' + $health.pid); exit 0 } } catch {}" ^
  "Write-Host 'STOPPED';" ^
  "if (Test-Path '.\data\server.pid') { Write-Host ('stale server.pid=' + (Get-Content '.\data\server.pid' -ErrorAction SilentlyContinue)) }" ^
  "if (Test-Path '.\data\postgres.pid') { Write-Host ('stale postgres.pid=' + (Get-Content '.\data\postgres.pid' -ErrorAction SilentlyContinue)) }" ^
  "exit 1"

exit /b %ERRORLEVEL%
