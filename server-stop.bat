@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'SilentlyContinue';" ^
  "$port = 8080;" ^
  "$token = if (Test-Path '.\data\server.token') { (Get-Content '.\data\server.token' -Raw).Trim() } else { '' };" ^
  "$running = $false;" ^
  "try { $health = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $port + '/api/health') -TimeoutSec 2; $running = ($health.status -eq 'ok') } catch {}" ^
  "if ($running -and $token) { Write-Host ('Stopping server PID ' + $health.pid + ' gracefully...'); try { Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:' + $port + '/internal/shutdown') -Headers @{ 'X-Shutdown-Token' = $token } -TimeoutSec 10 | Out-Null } catch { Write-Host ('Graceful shutdown request failed: ' + $_.Exception.Message) } }" ^
  "$deadline = (Get-Date).AddSeconds(35);" ^
  "do { Start-Sleep -Milliseconds 500; try { $health = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $port + '/api/health') -TimeoutSec 1; $running = ($health.status -eq 'ok') } catch { $running = $false } } while ($running -and (Get-Date) -lt $deadline);" ^
  "if ($running) { Write-Host 'Graceful stop timed out. Forcing server process...'; if (Test-Path '.\data\server.pid') { $serverPid = [int](Get-Content '.\data\server.pid' -Raw); $serverProcess = Get-Process -Id $serverPid -ErrorAction SilentlyContinue; if ($serverProcess -and $serverProcess.ProcessName -in @('java','javaw')) { Stop-Process -Id $serverPid -Force } } }" ^
  "Start-Sleep -Seconds 2;" ^
  "if (Test-Path '.\data\postgres.pid') { $pg = [int](Get-Content '.\data\postgres.pid' -Raw); $pgProcess = Get-Process -Id $pg -ErrorAction SilentlyContinue; if ($pgProcess -and $pgProcess.ProcessName -eq 'postgres') { Stop-Process -Id $pg -Force } }" ^
  "Remove-Item '.\data\server.pid','.\data\launcher.pid' -Force;" ^
  "Write-Host 'Server stopped.'"

exit /b %ERRORLEVEL%
