[CmdletBinding()]
param(
    [int]$MySqlPort = 3307
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$environmentFile = Join-Path $PSScriptRoot '.env.local'

# Resolve JDK 21 first. mvnw and java -jar both honor JAVA_HOME; do not wait for Maven to fail on 26.
. (Join-Path $PSScriptRoot 'lib\java.ps1')
$jdk = Use-ProjectJdk

& (Join-Path $PSScriptRoot 'setup-dev-mysql.ps1') -Port $MySqlPort

foreach ($line in Get-Content -LiteralPath $environmentFile) {
    if ($line -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

$env:MES_BUSINESS_DB_ENABLED = 'true'
$env:MES_BUSINESS_DB_URL =
    "jdbc:mysql://127.0.0.1:$MySqlPort/mes?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
$env:MES_BUSINESS_DB_USERNAME = $env:MYSQL_USER
$env:MES_BUSINESS_DB_PASSWORD = $env:MYSQL_PASSWORD
$env:MES_SESSION_COOKIE_SECURE = 'false'

$backendDirectory = Join-Path $root 'backend'
$maven = Join-Path $backendDirectory 'mvnw.cmd'
$applicationJar = Join-Path $backendDirectory 'mes-api\target\mes-api.jar'

Push-Location $backendDirectory
try {
    & $maven -B -pl mes-api -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed with exit code $LASTEXITCODE"
    }
    if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
        throw "Backend jar was not generated: $applicationJar"
    }

    & $jdk.Java -jar $applicationJar
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
