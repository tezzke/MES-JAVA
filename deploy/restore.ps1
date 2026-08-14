[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupDirectory,
    [string]$MySqlHost = '127.0.0.1',
    [int]$MySqlPort = 3306,
    [string]$MySqlDatabase = 'mes',
    [string]$MySqlUser = 'mes_restore',
    [SecureString]$MySqlPassword,
    [string]$SQLitePath = (Join-Path $PSScriptRoot 'dist/data/mes.db'),
    [switch]$ApplicationStopped,
    [switch]$ConfirmRestore
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

if (-not $ConfirmRestore) {
    throw 'Restore overwrites both databases. Pass -ConfirmRestore explicitly. No write was performed.'
}
if (-not $ApplicationStopped) {
    throw 'Stop MES and verify SQLite is unused, then pass -ApplicationStopped.'
}
foreach ($tool in 'mysql', 'mysqldump', 'sqlite3') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool was not found. Install MySQL 8 Client and SQLite CLI, then update PATH."
    }
}

$mysqlFile = Join-Path $BackupDirectory 'business.sql'
$sqliteFile = Join-Path $BackupDirectory 'telemetry.db'
$manifestFile = Join-Path $BackupDirectory 'manifest.json'
foreach ($file in $mysqlFile, $sqliteFile, $manifestFile) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Backup file is missing: $file" }
}
if ((Get-Item -LiteralPath $mysqlFile).Length -eq 0) {
    throw 'business.sql is empty. Restore refused.'
}
$integrity = & sqlite3 $sqliteFile 'PRAGMA integrity_check;'
if ($LASTEXITCODE -ne 0 -or $integrity -notcontains 'ok') {
    throw "SQLite backup integrity check failed: $($integrity -join '; ')"
}
if (-not $MySqlPassword) {
    $MySqlPassword = Read-Host 'Enter the MySQL restore account password' -AsSecureString
}

$sqliteParent = Split-Path $SQLitePath -Parent
if (-not (Test-Path -LiteralPath $sqliteParent)) {
    New-Item -ItemType Directory -Path $sqliteParent | Out-Null
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$safetyDirectory = Join-Path $PSScriptRoot "pre-restore-$stamp"
New-Item -ItemType Directory -Path $safetyDirectory | Out-Null
$safetyMySql = Join-Path $safetyDirectory 'business-before.sql'
$safetySqlite = Join-Path $safetyDirectory 'telemetry-before.db'

$previousPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = ConvertTo-PlainText $MySqlPassword

    Invoke-Checked {
        & mysqldump `
            --host=$MySqlHost --port=$MySqlPort --user=$MySqlUser `
            --single-transaction --quick --triggers --no-tablespaces `
            --set-gtid-purged=OFF --default-character-set=utf8mb4 `
            --result-file=$safetyMySql $MySqlDatabase
    } 'Pre-restore MySQL safety backup'

    if (Test-Path -LiteralPath $SQLitePath -PathType Leaf) {
        $escapedSafety = $safetySqlite.Replace("'", "''")
        Invoke-Checked {
            & sqlite3 $SQLitePath '.timeout 10000' 'PRAGMA wal_checkpoint(FULL);' ".backup '$escapedSafety'"
        } 'Pre-restore SQLite safety backup'
    }

    $sourceCommand = "source " + ((Resolve-Path -LiteralPath $mysqlFile).Path.Replace('\', '/'))
    Invoke-Checked {
        & mysql `
            --host=$MySqlHost --port=$MySqlPort --user=$MySqlUser `
            --database=$MySqlDatabase --default-character-set=utf8mb4 `
            --binary-mode=1 --execute=$sourceCommand
    } 'MySQL restore'
}
finally {
    if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousPassword }
}

$temporarySqlite = "$SQLitePath.restore-$PID.tmp"
Copy-Item -LiteralPath $sqliteFile -Destination $temporarySqlite
$temporaryIntegrity = & sqlite3 $temporarySqlite 'PRAGMA integrity_check;'
if ($LASTEXITCODE -ne 0 -or $temporaryIntegrity -notcontains 'ok') {
    Remove-Item -LiteralPath $temporarySqlite -Force -ErrorAction SilentlyContinue
    throw 'Temporary SQLite restore failed integrity validation. MySQL is already restored; keep MES stopped.'
}

Remove-Item -LiteralPath "$SQLitePath-wal", "$SQLitePath-shm" -Force -ErrorAction SilentlyContinue
Move-Item -LiteralPath $temporarySqlite -Destination $SQLitePath -Force

Write-Host 'Dual-database restore complete. Validate Flyway, health, and critical records before traffic.' -ForegroundColor Green
Write-Host "Pre-restore safety backup: $safetyDirectory"
