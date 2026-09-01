# Project JDK resolver. Every PowerShell script that needs java/javac should
# dot-source this file. Do not guess PATH, the IntelliJ SDK, or a temp folder
# that this repo never installs.
#
# Keep $ProjectJavaMajorVersion in sync with backend/pom.xml java.version,
# CI setup-java, and the Docker Temurin tag.
$ProjectJavaMajorVersion = 21

function Get-JavaMajorVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JavaExecutable
    )
    if (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) {
        throw "java not found: $JavaExecutable"
    }
    # java -version writes to stderr. With $ErrorActionPreference=Stop that becomes a
    # terminating ErrorRecord, so capture it as text instead of letting it abort.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $JavaExecutable -version 2>&1 | ForEach-Object { $_.ToString() } | Out-String
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($output -match 'version "(\d+)') {
        return [int]$Matches[1]
    }
    throw "cannot parse Java version from '$JavaExecutable -version':`n$output"
}

function Test-IsJdkHome {
    param([string] $HomePath)
    if ([string]::IsNullOrWhiteSpace($HomePath)) {
        return $false
    }
    $java = Join-Path $HomePath 'bin\java.exe'
    $javac = Join-Path $HomePath 'bin\javac.exe'
    return (Test-Path -LiteralPath $java -PathType Leaf) `
        -and (Test-Path -LiteralPath $javac -PathType Leaf)
}

function Read-ProjectJavaHomeFile {
    $file = Join-Path $PSScriptRoot '..\java-home.local'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $file) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith('#')) {
            return $trimmed.Trim('"').Trim("'")
        }
    }
    return $null
}

function Add-JdkCandidate {
    param(
        [System.Collections.Generic.List[object]] $Candidates,
        [string] $Source,
        [string] $JdkHome
    )
    if ([string]::IsNullOrWhiteSpace($JdkHome)) {
        return
    }
    $Candidates.Add([pscustomobject]@{ Source = $Source; JdkHome = $JdkHome.Trim() })
}

function Resolve-ProjectJdk {
    $candidates = New-Object System.Collections.Generic.List[object]

    # Explicit project overrides first, so an IDE-injected JAVA_HOME cannot win.
    Add-JdkCandidate $candidates 'MES_JAVA_HOME' $env:MES_JAVA_HOME
    Add-JdkCandidate $candidates 'deploy/java-home.local' (Read-ProjectJavaHomeFile)

    # System environment is accepted only when it really is JDK 21.
    Add-JdkCandidate $candidates 'JAVA_HOME' $env:JAVA_HOME
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) {
        $homeFromPath = Split-Path (Split-Path $onPath.Source -Parent) -Parent
        Add-JdkCandidate $candidates 'PATH' $homeFromPath
    }

    $seen = @{}
    $rejected = New-Object System.Collections.Generic.List[string]

    foreach ($candidate in $candidates) {
        $key = $candidate.JdkHome.ToLowerInvariant()
        if ($seen.ContainsKey($key)) {
            continue
        }
        $seen[$key] = $true

        if (-not (Test-IsJdkHome $candidate.JdkHome)) {
            $rejected.Add("$($candidate.Source)=$($candidate.JdkHome) (not a JDK: missing bin\java.exe or bin\javac.exe)")
            continue
        }

        $java = Join-Path $candidate.JdkHome 'bin\java.exe'
        $major = Get-JavaMajorVersion $java
        if ($major -ne $ProjectJavaMajorVersion) {
            $rejected.Add("$($candidate.Source)=$($candidate.JdkHome) (Java $major, need $ProjectJavaMajorVersion)")
            continue
        }

        return [pscustomobject]@{
            Home   = $candidate.JdkHome
            Java   = $java
            Javac  = Join-Path $candidate.JdkHome 'bin\javac.exe'
            Major  = $major
            Source = $candidate.Source
        }
    }

    $example = Join-Path $PSScriptRoot '..\java-home.local.example'
    $nl = [Environment]::NewLine
    $message = @(
        "JDK $ProjectJavaMajorVersion is required (CI Temurin 21 / Docker Temurin 21 / maven.compiler.release=21)."
        'Do not use the IntelliJ project SDK or a newer JDK such as 26: Mockito/Byte Buddy cannot instrument those runtimes.'
        ''
        'Configure one of these (highest priority first):'
        "  1. MES_JAVA_HOME = JDK $ProjectJavaMajorVersion home"
        "  2. Copy deploy\java-home.local.example to deploy\java-home.local and write the JDK $ProjectJavaMajorVersion path"
        "  3. Point JAVA_HOME / PATH at JDK $ProjectJavaMajorVersion"
        ''
        "Example file: $example"
    ) -join $nl
    if ($rejected.Count -gt 0) {
        $message += $nl + $nl + 'Checked but rejected:' + $nl + (($rejected | ForEach-Object { "  - $_" }) -join $nl)
    }
    throw $message
}

function Use-ProjectJdk {
    $resolved = Resolve-ProjectJdk
    $env:JAVA_HOME = $resolved.Home
    $jdkBin = Join-Path $resolved.Home 'bin'
    $env:Path = "$jdkBin;$env:Path"
    Write-Host "Using JDK $($resolved.Major) from $($resolved.Source): $($resolved.Home)" -ForegroundColor DarkGray
    return $resolved
}
