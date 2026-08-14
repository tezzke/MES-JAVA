[CmdletBinding()]
param(
    [int]$MySqlPort = 3307
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$environmentFile = Join-Path $PSScriptRoot '.env.local'

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

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    $portableJavaHome = Join-Path $env:TEMP `
        'mes-toolchain\microsoft-jdk\PFiles64\Microsoft\jdk-21.0.12.8-hotspot'
    $portableJava = Join-Path $portableJavaHome 'bin\java.exe'
    if (Test-Path -LiteralPath $portableJava -PathType Leaf) {
        $env:JAVA_HOME = $portableJavaHome
        $java = $portableJava
    } else {
        throw 'java was not found. Install Java 21 and configure JAVA_HOME before running this script.'
    }
} else {
    $java = $javaCommand.Source
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
}

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

    & $java -jar $applicationJar
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
