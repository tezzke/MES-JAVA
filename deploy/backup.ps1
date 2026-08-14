[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'backups'),
    [string]$MySqlHost = '127.0.0.1',
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = 'mes',
    [string]$MySqlUser = 'mes_backup',
    [SecureString]$MySqlPassword,
    [string]$SQLitePath = (Join-Path $PSScriptRoot 'dist/data/mes.db'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param([scriptblock]$Command, [string]$Description)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE"
    }
}

function ConvertTo-PlainText {
    param([SecureString]$Value)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if (-not (Get-Command mysqldump -ErrorAction SilentlyContinue)) {
    throw 'mysqldump was not found. Install MySQL 8 Client and add it to PATH.'
}
if (-not (Get-Command sqlite3 -ErrorAction SilentlyContinue)) {
    throw 'sqlite3 was not found. Install SQLite CLI and add it to PATH.'
}
if (-not (Test-Path -LiteralPath $SQLitePath -PathType Leaf)) {
    throw "SQLite file does not exist: $SQLitePath"
}
if (-not $MySqlPassword) {
    $MySqlPassword = Read-Host 'Enter the MySQL backup account password' -AsSecureString
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $OutputDirectory $stamp
if (Test-Path -LiteralPath $backupDirectory) {
    if (-not $Force) { throw "Backup directory exists: $backupDirectory. Use -Force to overwrite it." }
    Remove-Item -LiteralPath $backupDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $backupDirectory | Out-Null

$mysqlFile = Join-Path $backupDirectory 'business.sql'
$sqliteFile = Join-Path $backupDirectory 'telemetry.db'
$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = ConvertTo-PlainText $MySqlPassword
    Invoke-Checked {
        & mysqldump `
            --host=$MySqlHost --port=$MySqlPort --user=$MySqlUser `
            --single-transaction --quick --triggers --no-tablespaces `
            --set-gtid-purged=OFF --default-character-set=utf8mb4 `
            --result-file=$mysqlFile $MySqlDatabase
    } 'MySQL backup'
}
finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousPassword }
}

$escapedSqliteBackup = $sqliteFile.Replace("'", "''")
Invoke-Checked {
    & sqlite3 $SQLitePath '.timeout 10000' 'PRAGMA wal_checkpoint(FULL);' ".backup '$escapedSqliteBackup'"
} 'SQLite checkpoint and backup'

$integrity = & sqlite3 $sqliteFile 'PRAGMA integrity_check;'
if ($LASTEXITCODE -ne 0 -or $integrity -notcontains 'ok') {
    throw "SQLite backup integrity check failed: $($integrity -join '; ')"
}

@{
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    mysqlHost = $MySqlHost
    mysqlPort = $MySqlPort
    mysqlDatabase = $MySqlDatabase
    sqliteSource = (Resolve-Path -LiteralPath $SQLitePath).Path
    mysqlFile = 'business.sql'
    sqliteFile = 'telemetry.db'
} | ConvertTo-Json | Set-Content (Join-Path $backupDirectory 'manifest.json') -Encoding UTF8

Write-Host "Dual-database backup complete: $backupDirectory" -ForegroundColor Green
Write-Host 'Encrypt and copy the complete timestamped directory to independent storage.'
