[CmdletBinding()]
param(
    [int]$Port = 3307,
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [switch]$SkipUserEnvironment
)

$ErrorActionPreference = 'Stop'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqlAdmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
$instanceDirectory = Join-Path $env:LOCALAPPDATA 'MES-JAVA\mysql'
$dataDirectory = Join-Path $instanceDirectory 'data'
$configFile = Join-Path $instanceDirectory 'my.ini'
$environmentFile = Join-Path $PSScriptRoot '.env.local'

foreach ($executable in $mysql, $mysqlAdmin, $mysqld) {
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
        throw "MySQL 8 executable was not found: $executable"
    }
}

function New-RandomPassword {
    param([int]$Length = 32)
    $characters = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $bytes = New-Object byte[] $Length
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    -join ($bytes | ForEach-Object { $characters[$_ % $characters.Length] })
}

function Read-EnvironmentFile {
    $values = @{}
    if (Test-Path -LiteralPath $environmentFile) {
        foreach ($line in Get-Content -LiteralPath $environmentFile) {
            if ($line -match '^\s*([^#][^=]*)=(.*)$') {
                $values[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }
    return $values
}

New-Item -ItemType Directory -Force -Path $instanceDirectory | Out-Null
$values = Read-EnvironmentFile
if (-not $values.MYSQL_PASSWORD) {
    $values.MYSQL_PASSWORD = New-RandomPassword
}
if (-not $values.MYSQL_ROOT_PASSWORD) {
    $values.MYSQL_ROOT_PASSWORD = New-RandomPassword
}
$values.MYSQL_DATABASE = 'mes'
$values.MYSQL_USER = 'mes_app'
$values.MES_BOOTSTRAP_ADMIN_PASSWORD = 'admin123'
$values.MES_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'

@(
    "MYSQL_DATABASE=$($values.MYSQL_DATABASE)"
    "MYSQL_USER=$($values.MYSQL_USER)"
    "MYSQL_PASSWORD=$($values.MYSQL_PASSWORD)"
    "MYSQL_ROOT_PASSWORD=$($values.MYSQL_ROOT_PASSWORD)"
    "MES_BOOTSTRAP_ADMIN_PASSWORD=$($values.MES_BOOTSTRAP_ADMIN_PASSWORD)"
    "MES_ALLOWED_ORIGINS=$($values.MES_ALLOWED_ORIGINS)"
) | Set-Content -LiteralPath $environmentFile -Encoding UTF8

$normalizedHome = $MySqlHome.Replace('\', '/')
$normalizedData = $dataDirectory.Replace('\', '/')
@"
[mysqld]
basedir=$normalizedHome
datadir=$normalizedData
port=$Port
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
log-error=$($instanceDirectory.Replace('\', '/'))/mysql-error.log

[client]
port=$Port
host=127.0.0.1
default-character-set=utf8mb4
"@ | Set-Content -LiteralPath $configFile -Encoding ASCII

$newInstance = -not (Test-Path -LiteralPath (Join-Path $dataDirectory 'auto.cnf'))
if ($newInstance) {
    New-Item -ItemType Directory -Force -Path $dataDirectory | Out-Null
    & $mysqld "--defaults-file=$configFile" --initialize-insecure
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL initialization failed with exit code $LASTEXITCODE"
    }
}

$listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $listening) {
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=`"$configFile`"" `
        -WorkingDirectory $instanceDirectory -WindowStyle Hidden | Out-Null
}

$ready = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    & $mysqlAdmin --host=127.0.0.1 --port=$Port --user=root --silent ping 2>$null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $values.MYSQL_ROOT_PASSWORD
        & $mysqlAdmin --host=127.0.0.1 --port=$Port --user=root --silent ping 2>$null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previousPassword
        }
    }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    throw "MySQL did not become ready on port $Port. Check $instanceDirectory\mysql-error.log"
}

if ($newInstance) {
    $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($values.MYSQL_ROOT_PASSWORD)';
CREATE DATABASE IF NOT EXISTS mes CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'mes_app'@'127.0.0.1' IDENTIFIED BY '$($values.MYSQL_PASSWORD)';
CREATE USER IF NOT EXISTS 'mes_app'@'localhost' IDENTIFIED BY '$($values.MYSQL_PASSWORD)';
GRANT ALL PRIVILEGES ON mes.* TO 'mes_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON mes.* TO 'mes_app'@'localhost';
FLUSH PRIVILEGES;
"@
    $sql | & $mysql --host=127.0.0.1 --port=$Port --user=root
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL database/user creation failed with exit code $LASTEXITCODE"
    }
}

if (-not $SkipUserEnvironment) {
    $applicationEnvironment = @{
        MES_BUSINESS_DB_ENABLED = 'true'
        MES_BUSINESS_DB_URL = "jdbc:mysql://127.0.0.1:$Port/mes?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
        MES_BUSINESS_DB_USERNAME = $values.MYSQL_USER
        MES_BUSINESS_DB_PASSWORD = $values.MYSQL_PASSWORD
        MES_BOOTSTRAP_ADMIN_PASSWORD = $values.MES_BOOTSTRAP_ADMIN_PASSWORD
        MES_SESSION_COOKIE_SECURE = 'false'
        MES_ALLOWED_ORIGINS = $values.MES_ALLOWED_ORIGINS
    }
    $environmentKey = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment')
    try {
        foreach ($entry in $applicationEnvironment.GetEnumerator()) {
            $environmentKey.SetValue($entry.Key, $entry.Value, [Microsoft.Win32.RegistryValueKind]::String)
        }
    }
    finally {
        $environmentKey.Dispose()
    }
}

Write-Host "Project MySQL is ready on 127.0.0.1:$Port." -ForegroundColor Green
Write-Host 'Credentials were written to deploy/.env.local (ignored by Git).'
if (-not $SkipUserEnvironment) {
    Write-Host 'IDE environment was updated. Restart the IDE before launching the backend again.'
}
