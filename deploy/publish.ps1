# Test and publish frontend plus backend into deploy/dist.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $PSScriptRoot 'dist'

. (Join-Path $PSScriptRoot 'lib\java.ps1')
$null = Use-ProjectJdk

Write-Host '[1/6] Testing frontend...' -ForegroundColor Cyan
Push-Location (Join-Path $root 'frontend')
npm ci
if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
npm run test
if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed with exit code $LASTEXITCODE" }

Write-Host '[2/6] Building frontend...' -ForegroundColor Cyan
npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed with exit code $LASTEXITCODE" }
Pop-Location

Write-Host '[3/6] Testing and packaging backend...' -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
& (Join-Path (Get-Location) 'mvnw.cmd') -B clean package
if ($LASTEXITCODE -ne 0) { throw "Backend tests or package failed with exit code $LASTEXITCODE" }
Pop-Location

Write-Host '[4/6] Collecting artifacts...' -ForegroundColor Cyan
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item $dist -ItemType Directory | Out-Null
Copy-Item (Join-Path $root 'backend/mes-api/target/mes-api.jar') $dist
Copy-Item (Join-Path $root 'frontend/dist') (Join-Path $dist 'wwwroot') -Recurse -Force
Copy-Item (Join-Path $root 'backend/mes-api/src/main/resources/devices.json') $dist
Copy-Item (Join-Path $root 'backend/mes-api/src/main/resources/plant.json') $dist

Write-Host '[5/6] Creating safe launcher...' -ForegroundColor Cyan
@'
@echo off
setlocal
if /I not "%MES_BUSINESS_DB_ENABLED%"=="true" goto :missing_enabled
if "%MES_BUSINESS_DB_URL%"=="" goto :missing_url
if "%MES_BUSINESS_DB_USERNAME%"=="" goto :missing_user
if "%MES_BUSINESS_DB_PASSWORD%"=="" goto :missing_password
if "%MES_ALLOWED_ORIGINS%"=="" goto :missing_origins
if /I not "%MES_SESSION_COOKIE_SECURE%"=="true" goto :insecure_cookie
where java >nul 2>&1
if errorlevel 1 goto :missing_java
java -version 2>&1 | findstr /C:"version \"21" >nul
if errorlevel 1 goto :wrong_java
java -XX:MaxRAMPercentage=75 -jar mes-api.jar --mes.devices-config=devices.json --mes.plant-config=plant.json
set EXIT_CODE=%ERRORLEVEL%
pause
exit /b %EXIT_CODE%

:missing_enabled
echo ERROR: MES_BUSINESS_DB_ENABLED must be true.
exit /b 2
:missing_url
echo ERROR: MES_BUSINESS_DB_URL is required.
exit /b 2
:missing_user
echo ERROR: MES_BUSINESS_DB_USERNAME is required.
exit /b 2
:missing_password
echo ERROR: MES_BUSINESS_DB_PASSWORD must not be empty.
exit /b 2
:missing_origins
echo ERROR: MES_ALLOWED_ORIGINS is required.
exit /b 2
:insecure_cookie
echo ERROR: MES_SESSION_COOKIE_SECURE must be true for production.
exit /b 2
:missing_java
echo ERROR: java was not found. Install JRE 21 and put it on PATH.
exit /b 2
:wrong_java
echo ERROR: this release requires Java 21. Higher versions are not supported.
java -version
exit /b 2
'@ | Set-Content (Join-Path $dist 'start.bat') -Encoding OEM

Write-Host '[6/6] Creating startup guide...' -ForegroundColor Cyan
@'
# Required startup environment

Runtime is **Java 21** (same major version as the CI Temurin 21 image and
`maven.compiler.release`). `start.bat` refuses any other `java -version`.

`start.bat` does not contain, persist, or print passwords. Configure these values
in the secured environment of the Windows service account:

- `MES_BUSINESS_DB_ENABLED=true`
- `MES_BUSINESS_DB_URL`: MySQL JDBC URL
- `MES_BUSINESS_DB_USERNAME`: least-privilege application account
- `MES_BUSINESS_DB_PASSWORD`: strong secret injected by secret management
- `MES_ALLOWED_ORIGINS`: public HTTPS Origin, for example `https://mes.example.com`
- `MES_SESSION_COOKIE_SECURE=true`
- `MES_TELEMETRY_DB_URL`: optional; defaults to `data/mes.db`
- `MES_BOOTSTRAP_ADMIN_PASSWORD`: temporary, first empty-database startup only; no default

Never place secrets in `start.bat`, this file, source control, or command history.
After the bootstrap admin password is rotated, remove the bootstrap variable.
Run the application through a Windows service wrapper and terminate TLS at a
properly configured reverse proxy.

See the deployment and operations guide in the source repository for the
complete procedure.
'@ | Set-Content (Join-Path $dist 'STARTUP.md') -Encoding UTF8

Write-Host ''
Write-Host "Publish complete: $dist" -ForegroundColor Green
Write-Host 'Read dist/STARTUP.md and configure the environment before running start.bat.'
