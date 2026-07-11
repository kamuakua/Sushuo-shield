#requires -Version 5.1
[CmdletBinding()]
param(
    [string] $ProjectRoot,
    [string] $CfrJar = $env:CFR_JAR,
    [switch] $NoCfrDownload,
    [switch] $DisableAntiVm
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Join-Path $scriptDir '..'
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$TargetDir = Join-Path $ProjectRoot 'target'
$WorkRoot = Join-Path $TargetDir 'obf-regression'
$WorkDir = Join-Path $WorkRoot ('run-{0:yyyyMMdd-HHmmss}-{1}' -f (Get-Date), $PID)
$LogDir = Join-Path $WorkDir 'logs'
$ProbeSourceDir = Join-Path $scriptDir 'probes'
$script:StepIndex = 0

function Write-Step {
    param([Parameter(Mandatory)][string] $Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Resolve-Tool {
    param([Parameter(Mandatory)][string[]] $Names)
    foreach ($name in $Names) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cmd) {
            return $cmd.Source
        }
    }
    throw "Required tool not found on PATH: $($Names -join ', ')"
}

function Format-CommandLine {
    param([string] $File, [string[]] $Arguments)
    $items = @($File) + @($Arguments)
    return ($items | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }) -join ' '
}

function Invoke-LoggedProcess {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $File,
        [string[]] $Arguments = @(),
        [int[]] $AllowedExitCodes = @(0),
        [hashtable] $Environment = @{},
        [switch] $Quiet
    )

    $safe = ($Name -replace '[^A-Za-z0-9_.-]+', '_')
    $script:StepIndex++
    $log = Join-Path $LogDir ('{0:00}-{1}.log' -f $script:StepIndex, $safe)
    $cmdLine = Format-CommandLine -File $File -Arguments $Arguments
    Write-Host "[$Name] $cmdLine"

    $oldEnv = @{}
    foreach ($key in $Environment.Keys) {
        $oldEnv[$key] = [Environment]::GetEnvironmentVariable([string] $key, 'Process')
        [Environment]::SetEnvironmentVariable([string] $key, [string] $Environment[$key], 'Process')
    }

    try {
        $oldErrorActionPreference = $ErrorActionPreference
        try {
            # PowerShell 5 wraps native stderr as NativeCommandError when
            # $ErrorActionPreference is Stop. Keep stderr in the log and let the
            # native exit code decide pass/fail instead.
            $ErrorActionPreference = 'Continue'
            if ($Quiet) {
                & $File @Arguments > $log 2>&1
            } else {
                & $File @Arguments 2>&1 | Tee-Object -FilePath $log | ForEach-Object { Write-Host $_ }
            }
        } finally {
            $ErrorActionPreference = $oldErrorActionPreference
        }
        $lastExitCodeVariable = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
        $exitCode = if ($null -ne $lastExitCodeVariable -and $null -ne $lastExitCodeVariable.Value) {
            [int] $lastExitCodeVariable.Value
        } else {
            0
        }
    } finally {
        foreach ($key in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable([string] $key, $oldEnv[$key], 'Process')
        }
    }

    if (-not ($AllowedExitCodes -contains $exitCode)) {
        $tail = ''
        if (Test-Path -LiteralPath $log) {
            $tail = (Get-Content -LiteralPath $log -Tail 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
        }
        throw "Step '$Name' failed with exit code $exitCode. Log: $log`n$tail"
    }

    if ($Quiet) {
        Write-Host "[$Name] exit=$exitCode log=$log"
    }

    [pscustomobject]@{
        Name = $Name
        ExitCode = $exitCode
        Log = $log
    }
}

function Assert-File {
    param([Parameter(Mandatory)][string] $Path, [string] $Message)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if ([string]::IsNullOrWhiteSpace($Message)) {
            $Message = "Missing expected file: $Path"
        }
        throw $Message
    }
}

function Write-Utf8NoBom {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)][string] $Text)
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Write-ArgFile {
    param([Parameter(Mandatory)][string] $Path, [Parameter(Mandatory)][string[]] $Items)
    $escaped = $Items | ForEach-Object {
        $item = ([string] $_).Replace('\', '/')
        '"' + ($item -replace '"', '\"') + '"'
    }
    Write-Utf8NoBom -Path $Path -Text (($escaped -join [Environment]::NewLine) + [Environment]::NewLine)
}

function Reset-RegressionWorkDir {
    New-Item -ItemType Directory -Force -Path $TargetDir, $WorkRoot, $WorkDir, $LogDir | Out-Null
}

function Add-ZipAssembly {
    Add-Type -AssemblyName System.IO.Compression | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null
}

function Get-ZipEntryNames {
    param([Parameter(Mandatory)][string] $JarPath)
    Add-ZipAssembly
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        return @($zip.Entries | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
}

function Read-ZipEntryBytes {
    param([Parameter(Mandatory)][string] $JarPath, [Parameter(Mandatory)][string] $EntryName)
    Add-ZipAssembly
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $zip.GetEntry($EntryName)
        if (-not $entry) {
            throw "Entry not found in ${JarPath}: $EntryName"
        }
        $stream = $entry.Open()
        try {
            $memory = New-Object System.IO.MemoryStream
            try {
                $stream.CopyTo($memory)
                return $memory.ToArray()
            } finally {
                $memory.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
}

function Find-ZipPlaintextHits {
    param(
        [Parameter(Mandatory)][string] $JarPath,
        [Parameter(Mandatory)][string[]] $Needles
    )
    Add-ZipAssembly
    $latin1 = [System.Text.Encoding]::GetEncoding('ISO-8859-1')
    $hits = @()
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) {
                continue
            }
            foreach ($needle in $Needles) {
                if ($entry.FullName.Contains($needle)) {
                    $hits += [pscustomobject]@{ Entry = $entry.FullName; Needle = $needle; Where = 'entry-name' }
                }
            }
            $stream = $entry.Open()
            try {
                $memory = New-Object System.IO.MemoryStream
                try {
                    $stream.CopyTo($memory)
                    $text = $latin1.GetString($memory.ToArray())
                    foreach ($needle in $Needles) {
                        if ($text.Contains($needle)) {
                            $hits += [pscustomobject]@{ Entry = $entry.FullName; Needle = $needle; Where = 'entry-bytes' }
                        }
                    }
                } finally {
                    $memory.Dispose()
                }
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
    return $hits
}

function Find-TextHits {
    param(
        [Parameter(Mandatory)][string[]] $Files,
        [Parameter(Mandatory)][string[]] $Needles
    )
    $hits = @()
    foreach ($file in $Files) {
        $text = [System.IO.File]::ReadAllText($file)
        foreach ($needle in $Needles) {
            $count = [regex]::Matches($text, [regex]::Escape($needle)).Count
            if ($count -gt 0) {
                $hits += [pscustomobject]@{ File = $file; Needle = $needle; Count = $count }
            }
        }
    }
    return $hits
}

function Find-RegexTextHits {
    param(
        [Parameter(Mandatory)][string[]] $Files,
        [Parameter(Mandatory)][hashtable] $Patterns
    )
    $hits = @()
    $options = [System.Text.RegularExpressions.RegexOptions]::Multiline
    foreach ($file in $Files) {
        $text = [System.IO.File]::ReadAllText($file)
        foreach ($key in $Patterns.Keys) {
            $pattern = [string] $Patterns[$key]
            $count = [regex]::Matches($text, $pattern, $options).Count
            if ($count -gt 0) {
                $hits += [pscustomobject]@{ File = $file; Pattern = [string] $key; Count = $count }
            }
        }
    }
    return $hits
}

function Test-BytePattern {
    param(
        [Parameter(Mandatory)][byte[]] $Haystack,
        [Parameter(Mandatory)][byte[]] $Needle
    )
    if ($Needle.Length -eq 0 -or $Haystack.Length -lt $Needle.Length) {
        return $false
    }
    $lastStart = $Haystack.Length - $Needle.Length
    for ($offset = 0; $offset -le $lastStart; $offset++) {
        $matched = $true
        for ($index = 0; $index -lt $Needle.Length; $index++) {
            if ($Haystack[($offset + $index)] -ne $Needle[$index]) {
                $matched = $false
                break
            }
        }
        if ($matched) {
            return $true
        }
    }
    return $false
}

function Read-Int32LittleEndian {
    param([Parameter(Mandatory)][byte[]] $Bytes, [Parameter(Mandatory)][int] $Offset)
    if ($Offset -lt 0 -or $Offset + 4 -gt $Bytes.Length) {
        throw "Cannot read Int32 at offset $Offset from $($Bytes.Length)-byte buffer."
    }
    return (($Bytes[$Offset] -band 0xFF) `
        -bor (($Bytes[($Offset + 1)] -band 0xFF) -shl 8) `
        -bor (($Bytes[($Offset + 2)] -band 0xFF) -shl 16) `
        -bor (($Bytes[($Offset + 3)] -band 0xFF) -shl 24))
}

function Get-NativeResourceScan {
    param(
        [Parameter(Mandatory)][string] $JarPath,
        [Parameter(Mandatory)][string[]] $EntryNames,
        [Parameter(Mandatory)][string[]] $PlaintextNeedles
    )
    $ascii = [System.Text.Encoding]::ASCII
    $fixedSsn2MagicHits = @()
    $rawMagicHits = @()
    $plaintextHits = @()

    foreach ($entryName in $EntryNames) {
        $bytes = Read-ZipEntryBytes -JarPath $JarPath -EntryName $entryName
        $hasFixedSsn2Magic = $bytes.Length -ge 4 `
            -and $bytes[0] -eq 0x53 `
            -and $bytes[1] -eq 0x53 `
            -and $bytes[2] -eq 0x4E `
            -and $bytes[3] -eq 0x32
        if ($hasFixedSsn2Magic) {
            $fixedSsn2MagicHits += [pscustomobject]@{ Entry = $entryName; Kind = 'fixed-SSN2-header' }
        }

        if ($bytes.Length -ge 2 -and $bytes[0] -eq 0x4D -and $bytes[1] -eq 0x5A) {
            $rawMagicHits += [pscustomobject]@{ Entry = $entryName; Kind = 'raw-MZ-header' }
            if ($bytes.Length -ge 0x40) {
                $peOffset = Read-Int32LittleEndian -Bytes $bytes -Offset 0x3C
                if ($peOffset -ge 0 -and $peOffset + 4 -le $bytes.Length `
                        -and $bytes[($peOffset)] -eq 0x50 `
                        -and $bytes[($peOffset + 1)] -eq 0x45 `
                        -and $bytes[($peOffset + 2)] -eq 0x00 `
                        -and $bytes[($peOffset + 3)] -eq 0x00) {
                    $rawMagicHits += [pscustomobject]@{ Entry = $entryName; Kind = 'raw-PE-signature' }
                }
            }
        }

        foreach ($needle in $PlaintextNeedles) {
            $needleBytes = $ascii.GetBytes($needle)
            if (Test-BytePattern -Haystack $bytes -Needle $needleBytes) {
                $plaintextHits += [pscustomobject]@{ Entry = $entryName; Needle = $needle }
            }
        }
    }

    return [pscustomobject]@{
        EntryCount = $EntryNames.Count
        FixedSsn2MagicHits = @($fixedSsn2MagicHits)
        RawMagicHits = @($rawMagicHits)
        RawPlaintextHits = @($plaintextHits)
    }
}

function Get-LogMetric {
    param([Parameter(Mandatory)][string] $LogPath, [Parameter(Mandatory)][string] $Label)
    $text = Get-Content -Raw -LiteralPath $LogPath
    $pattern = '(?m)^\s*' + [regex]::Escape($Label) + '\s*(\d+)\s*$'
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) {
        throw "Metric '$Label' not found in $LogPath"
    }
    return [int] $match.Groups[1].Value
}

function Get-LogKeyValueInt {
    param([Parameter(Mandatory)][string] $LogPath, [Parameter(Mandatory)][string] $Key)
    $text = Get-Content -Raw -LiteralPath $LogPath
    $pattern = '(?m)(?:^|\s)' + [regex]::Escape($Key) + '=(\d+)\b'
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) {
        throw "Key/value metric '$Key' not found in $LogPath"
    }
    return [int] $match.Groups[1].Value
}

function Assert-LogKeyValueEquals {
    param(
        [Parameter(Mandatory)][string] $LogPath,
        [Parameter(Mandatory)][string] $Key,
        [Parameter(Mandatory)][int] $Expected,
        [Parameter(Mandatory)][string] $Label
    )
    $value = Get-LogKeyValueInt -LogPath $LogPath -Key $Key
    if ($value -ne $Expected) {
        throw "$Label expected $Expected, got $value. Log: $LogPath"
    }
    Write-Host "$Label`: $value"
}

function Assert-MetricGreaterThan {
    param([string] $LogPath, [string] $Label, [int] $Minimum)
    $value = Get-LogMetric -LogPath $LogPath -Label $Label
    if ($value -le $Minimum) {
        throw "Metric '$Label' expected > $Minimum, got $value"
    }
    Write-Host "metric $Label $value"
}

function Resolve-Cfr {
    param([string] $Requested, [switch] $DisableDownload)
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates.Add($Requested)
    }
    $candidates.Add((Join-Path $ProjectRoot 'tools\cfr.jar'))
    $candidates.Add((Join-Path $ProjectRoot 'tools\cfr-0.152.jar'))
    $candidates.Add((Join-Path $TargetDir 'tools\cfr.jar'))
    $candidates.Add((Join-Path $TargetDir 'tools\cfr-0.152.jar'))

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    if ($DisableDownload) {
        throw "CFR jar not found. Pass -CfrJar or set CFR_JAR, or run without -NoCfrDownload."
    }

    $toolDir = Join-Path $TargetDir 'tools'
    New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
    $downloadTo = Join-Path $toolDir 'cfr-0.152.jar'
    $url = 'https://repo.maven.apache.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar'
    Write-Host "Downloading CFR: $url -> $downloadTo"
    $oldProgress = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    try {
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        } catch {
            # Older/non-Windows PowerShell may not need this.
        }
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $downloadTo
    } finally {
        $ProgressPreference = $oldProgress
    }
    Assert-File -Path $downloadTo -Message 'CFR download did not create the expected jar.'
    return (Resolve-Path -LiteralPath $downloadTo).Path
}

function Test-NativePayloadEntry {
    param([Parameter(Mandatory)][string] $JarPath, [Parameter(Mandatory)][string] $EntryName)
    $ignoredEntry = $EntryName.EndsWith('/') -or $EntryName.EndsWith('.class') -or $EntryName.StartsWith('META-INF/', [System.StringComparison]::OrdinalIgnoreCase)
    if ($ignoredEntry) {
        return $false
    }
    $bytes = Read-ZipEntryBytes -JarPath $JarPath -EntryName $EntryName
    # Native payload/resource names and suffixes are intentionally randomized
    # (.bin/.dat/.res/.pak/.idx/.cfg). Identify the packed native payload by
    # size and masked header instead of a fixed path, suffix, or magic.
    return $bytes.Length -ge 32768 `
        -and -not ($bytes.Length -ge 2 -and $bytes[0] -eq 0x4D -and $bytes[1] -eq 0x5A)
}

function Copy-JarWithoutNative {
    param([Parameter(Mandatory)][string] $SourceJar, [Parameter(Mandatory)][string] $DestinationJar)
    Add-ZipAssembly
    if (Test-Path -LiteralPath $DestinationJar) {
        Remove-Item -LiteralPath $DestinationJar -Force
    }
    $removed = 0
    $source = [System.IO.Compression.ZipFile]::OpenRead($SourceJar)
    try {
        $dest = [System.IO.Compression.ZipFile]::Open($DestinationJar, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($entry in $source.Entries) {
                $name = $entry.FullName
                $isNativePayload = $false
                if (-not $name.EndsWith('/')) {
                    # Header and path are masked; the embedded DLL payload remains
                    # a large resource with a randomized suffix.
                    $isNativePayload = Test-NativePayloadEntry -JarPath $SourceJar -EntryName $name
                }
                if ($isNativePayload) {
                    $removed++
                    continue
                }
                $newEntry = $dest.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
                if ($name.EndsWith('/')) {
                    continue
                }
                $inStream = $entry.Open()
                try {
                    $outStream = $newEntry.Open()
                    try {
                        $inStream.CopyTo($outStream)
                    } finally {
                        $outStream.Dispose()
                    }
                } finally {
                    $inStream.Dispose()
                }
            }
        } finally {
            $dest.Dispose()
        }
    } finally {
        $source.Dispose()
    }
    if ($removed -le 0) {
        throw "No native payload entries were removed from $SourceJar"
    }
    return $removed
}

function Get-ClassNamesFromJar {
    param([Parameter(Mandatory)][string] $JarPath)
    Get-ZipEntryNames -JarPath $JarPath |
        Where-Object { $_.EndsWith('.class') -and $_ -ne 'module-info.class' } |
        ForEach-Object { $_.Substring(0, $_.Length - 6).Replace('/', '.') }
}

function Join-ClassPath {
    param([Parameter(Mandatory)][string[]] $Items)
    return ($Items -join [System.IO.Path]::PathSeparator)
}

Reset-RegressionWorkDir
Set-Location -LiteralPath $ProjectRoot

$java = Resolve-Tool @('java.exe', 'java')
$javac = Resolve-Tool @('javac.exe', 'javac')
$jar = Resolve-Tool @('jar.exe', 'jar')
$javap = Resolve-Tool @('javap.exe', 'javap')
$mvn = Resolve-Tool @('mvn.cmd', 'mvn')
$powershell = Resolve-Tool @('powershell.exe', 'powershell', 'pwsh')

$shieldJar = Join-Path $TargetDir 'sushuo-shield-1.0.0.jar'
$nativeDll = Join-Path $TargetDir 'native\sushuo1337_vm.dll'
$snakeReleaseJar = Join-Path $WorkDir 'snake-game-release.jar'
$snakeMaxJar = Join-Path $WorkDir 'snake-game-max.jar'
$snakeMaxReport = Join-Path $WorkDir 'snake-game-max-report.txt'
$noNativeJar = Join-Path $WorkDir 'snake-game-max-no-native.jar'

$semanticNeedles = @(
    'snake/',
    'snake.',
    'SnakeGame',
    'SnakeModel',
    'SnakePanel',
    'GameFrame',
    'GameConfig',
    'FoodSpawner',
    'ScoreBoard',
    'Point2i',
    'Direction',
    'SimulationHarness',
    'HeadlessHarness',
    'SNAKE_OK',
    'Sushuo Snake requires a graphical desktop.',
    'runSmokeTest',
    'bodySnapshot',
    'gameOver'
)

$nativePlaintextNeedles = @(
    'This program cannot be run in DOS mode',
    'JNI_OnLoad',
    'Java_biz_sushuo_shield_runtime_NativeOnlyRuntime',
    'sushuo1337_vm',
    'SushuoNative',
    'VirtualProtect',
    'GetProcAddress'
)

Write-Step 'Build native VM'
Invoke-LoggedProcess -Name 'build-native' -File $powershell -Arguments @(
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    (Join-Path $ProjectRoot 'native\build-windows.ps1')
) | Out-Null
Assert-File -Path $nativeDll -Message 'Native build did not produce target\native\sushuo1337_vm.dll.'

Write-Step 'Run mvn test and mvn package'
Invoke-LoggedProcess -Name 'mvn-test' -File $mvn -Arguments @('-B', 'test') | Out-Null
Invoke-LoggedProcess -Name 'mvn-package' -File $mvn -Arguments @('-B', '-DskipTests', 'package') | Out-Null
Assert-File -Path $shieldJar -Message 'Maven package did not produce target\sushuo-shield-1.0.0.jar.'

Write-Step 'Build snake release jar with headless harness'
$snakeClasses = Join-Path $WorkDir 'snake-classes'
$generatedSnakeSource = Join-Path $WorkDir 'generated-src\snake\HeadlessHarness.java'
New-Item -ItemType Directory -Force -Path $snakeClasses | Out-Null
Write-Utf8NoBom -Path $generatedSnakeSource -Text @'
package snake;

public final class HeadlessHarness {
    private HeadlessHarness() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.out.println(SimulationHarness.runSmokeTest());
    }
}
'@
$snakeSourceRoot = Join-Path $ProjectRoot 'samples\snake\src\main\java'
$snakeSources = @(Get-ChildItem -LiteralPath $snakeSourceRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$snakeSources += $generatedSnakeSource
$snakeArgFile = Join-Path $WorkDir 'snake-sources.args'
Write-ArgFile -Path $snakeArgFile -Items $snakeSources
Invoke-LoggedProcess -Name 'compile-snake' -File $javac -Arguments @(
    '-encoding', 'UTF-8',
    '--release', '17',
    '-d', $snakeClasses,
    "@$snakeArgFile"
) | Out-Null
$snakeManifest = Join-Path $WorkDir 'snake-manifest.mf'
Write-Utf8NoBom -Path $snakeManifest -Text "Manifest-Version: 1.0`r`nMain-Class: snake.HeadlessHarness`r`n`r`n"
Invoke-LoggedProcess -Name 'jar-snake-release' -File $jar -Arguments @(
    '--create',
    '--file', $snakeReleaseJar,
    '--manifest', $snakeManifest,
    '-C', $snakeClasses,
    '.'
) | Out-Null
Assert-File -Path $snakeReleaseJar
$plainRun = Invoke-LoggedProcess -Name 'run-snake-release-headless' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    '-jar', $snakeReleaseJar
) -Quiet
$plainOutput = Get-Content -Raw -LiteralPath $plainRun.Log
if ($plainOutput -notmatch 'SNAKE_OK score=\d+ best=\d+ size=\d+ over=(true|false)') {
    throw "Plain snake headless harness did not produce SNAKE_OK. Log: $($plainRun.Log)"
}

Write-Step 'Obfuscate snake release with --preset max'
$obfuscateArgs = @(
    '-jar', $shieldJar,
    $snakeReleaseJar,
    $snakeMaxJar,
    '--preset', 'max',
    '--seed', '13371337',
    '--report-file', $snakeMaxReport
)
if ($DisableAntiVm) {
    # Optional CI escape hatch for hosts where VM detection would make max-mode
    # runtime checks fail for environmental rather than obfuscation reasons.
    $obfuscateArgs += '--no-anti-vm'
}
$obf = Invoke-LoggedProcess -Name 'obfuscate-snake-max' -File $java -Arguments $obfuscateArgs
Assert-File -Path $snakeMaxJar
Assert-File -Path $snakeMaxReport
Assert-MetricGreaterThan -LogPath $obf.Log -Label 'renamed classes:' -Minimum 0
Assert-MetricGreaterThan -LogPath $obf.Log -Label 'renamed members:' -Minimum 0
Assert-MetricGreaterThan -LogPath $obf.Log -Label 'virtualized methods:' -Minimum 0
Assert-MetricGreaterThan -LogPath $obf.Log -Label 'encrypted strings:' -Minimum 0

Write-Step 'Run obfuscated jar headless with native path'
$maxRun = Invoke-LoggedProcess -Name 'run-snake-max-headless' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    "-Djava.library.path=$($TargetDir)\native",
    '-jar', $snakeMaxJar
) -Quiet
$maxOutput = Get-Content -Raw -LiteralPath $maxRun.Log
if ($maxOutput -notmatch 'SNAKE_OK score=\d+ best=\d+ size=\d+ over=(true|false)') {
    throw "Obfuscated snake headless harness did not produce SNAKE_OK. Log: $($maxRun.Log)"
}

Write-Step 'Scan obfuscated jar for plaintext semantic words and raw native payloads'
$entries = @(Get-ZipEntryNames -JarPath $snakeMaxJar)
$nativeEntries = @($entries | Where-Object { Test-NativePayloadEntry -JarPath $snakeMaxJar -EntryName $_ })
if ($nativeEntries.Count -lt 1) {
    throw 'Expected at least one packed native .bin payload in max jar.'
}
$rawNativeEntries = @($entries | Where-Object { $_ -match '\.(dll|so|dylib)(\.dat)?$' })
if ($rawNativeEntries.Count -gt 0) {
    throw "Raw or legacy native entries leaked into obfuscated jar: $($rawNativeEntries -join ', ')"
}
foreach ($nativeEntry in $nativeEntries) {
    $bytes = Read-ZipEntryBytes -JarPath $snakeMaxJar -EntryName $nativeEntry
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0x4D -and $bytes[1] -eq 0x5A) {
        throw "Native entry contains raw MZ bytes: $nativeEntry"
    }
    if ($bytes.Length -ge 4 -and $bytes[0] -eq 0x53 -and $bytes[1] -eq 0x53 -and $bytes[2] -eq 0x4E -and $bytes[3] -eq 0x32) {
        throw "Native entry leaked fixed SSN2 header instead of masked header: $nativeEntry"
    }
}
$fixedVmMagicEntries = @()
foreach ($entryName in $entries) {
    if ($entryName.EndsWith('/') -or $entryName.EndsWith('.class') -or $entryName.StartsWith('META-INF/', [System.StringComparison]::OrdinalIgnoreCase)) {
        continue
    }
    $bytes = Read-ZipEntryBytes -JarPath $snakeMaxJar -EntryName $entryName
    if ($bytes.Length -ge 4 -and $bytes[0] -eq 0x6D -and $bytes[1] -eq 0x4F -and $bytes[2] -eq 0x9B -and $bytes[3] -eq 0x17) {
        $fixedVmMagicEntries += "$entryName fixed-VM-6D4F9B17"
    }
    if ($bytes.Length -ge 4 -and $bytes[0] -eq 0x53 -and $bytes[1] -eq 0x53 -and $bytes[2] -eq 0x57 -and $bytes[3] -eq 0x4D) {
        $fixedVmMagicEntries += "$entryName fixed-watermark-SSWM"
    }
}
if ($fixedVmMagicEntries.Count -gt 0) {
    throw "Resource entries leaked fixed VM/watermark magic: $($fixedVmMagicEntries -join ', ')"
}
Write-Host 'fixed VM/native/watermark resource magic hits: 0'
$nativeScan = Get-NativeResourceScan -JarPath $snakeMaxJar -EntryNames $nativeEntries -PlaintextNeedles $nativePlaintextNeedles
$nativeFixedMagicHitCount = @($nativeScan.FixedSsn2MagicHits).Count
if ($nativeFixedMagicHitCount -gt 0) {
    $nativeScan.FixedSsn2MagicHits | Format-Table -AutoSize | Out-String | Write-Host
    throw "Native resource magic scan found $nativeFixedMagicHitCount fixed SSN2 magic hit(s)."
}
$nativeRawMagicHitCount = @($nativeScan.RawMagicHits).Count
if ($nativeRawMagicHitCount -gt 0) {
    $nativeScan.RawMagicHits | Format-Table -AutoSize | Out-String | Write-Host
    throw "Native resource magic scan found $nativeRawMagicHitCount raw native magic hit(s)."
}
$nativePlaintextHitCount = @($nativeScan.RawPlaintextHits).Count
if ($nativePlaintextHitCount -gt 0) {
    $nativeScan.RawPlaintextHits | Format-Table -AutoSize | Out-String | Write-Host
    throw "Native resource raw plaintext scan found $nativePlaintextHitCount hit(s)."
}
Write-Host "native packed payload entries: $($nativeEntries.Count)"
Write-Host 'native resource fixed SSN2 magic hits: 0'
Write-Host 'native resource raw magic hits: 0'
Write-Host 'native resource raw plaintext hits: 0'
$jarHits = @(Find-ZipPlaintextHits -JarPath $snakeMaxJar -Needles $semanticNeedles)
if ($jarHits.Count -gt 0) {
    $jarHits | Format-Table -AutoSize | Out-String | Write-Host
    throw "Plaintext semantic words leaked in obfuscated jar: $($jarHits.Count) hit(s)."
}
Write-Host 'jar plaintext semantic hits: 0'

Write-Step 'Collect javap invokedynamic/Object[] descriptor stats'
$javapDir = Join-Path $WorkDir 'javap'
New-Item -ItemType Directory -Force -Path $javapDir | Out-Null
$classNames = @(Get-ClassNamesFromJar -JarPath $snakeMaxJar)
if ($classNames.Count -le 0) {
    throw 'No classes found in obfuscated jar.'
}
$invokeDynamicCount = 0
$objectArrayDescriptorCount = 0
$objectArrayClassCount = 0
foreach ($className in $classNames) {
    $safeClass = ($className -replace '[^A-Za-z0-9_.-]+', '_')
    $outFile = Join-Path $javapDir "$safeClass.txt"
    $javapResult = Invoke-LoggedProcess -Name "javap-$safeClass" -File $javap -Arguments @(
        '-classpath', $snakeMaxJar,
        '-p',
        '-c',
        '-v',
        $className
    ) -Quiet
    Copy-Item -LiteralPath $javapResult.Log -Destination $outFile -Force
    $text = Get-Content -Raw -LiteralPath $outFile
    $classIndy = [regex]::Matches($text, 'InvokeDynamic').Count
    $classObj = [regex]::Matches($text, '\(\)\[Ljava/lang/Object;').Count
    $invokeDynamicCount += $classIndy
    $objectArrayDescriptorCount += $classObj
    if ($classObj -gt 0) {
        $objectArrayClassCount++
    }
}
Write-Host "javap classes: $($classNames.Count)"
Write-Host "javap InvokeDynamic count: $invokeDynamicCount"
Write-Host "javap no-arg Object[] method descriptor count: $objectArrayDescriptorCount"
Write-Host "javap classes with no-arg Object[] method descriptors: $objectArrayClassCount"
if ($invokeDynamicCount -le 0) {
    throw 'Expected InvokeDynamic sites in max obfuscated jar.'
}
if ($objectArrayDescriptorCount -ne 0) {
    throw "Expected zero no-arg VM Object[] descriptors in max obfuscated jar, got $objectArrayDescriptorCount."
}

Write-Step 'Compile and run VM metadata probes'
$probeSrc = Join-Path $WorkDir 'probe-src'
$probeClasses = Join-Path $WorkDir 'probe-classes'
New-Item -ItemType Directory -Force -Path $probeSrc, $probeClasses | Out-Null
Write-Utf8NoBom -Path (Join-Path $probeSrc 'VmMetaProbe.java') -Text @'
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class VmMetaProbe {
    private static boolean containsMarker(Object value, int marker) {
        if (value == null) {
            return false;
        }
        if (value instanceof Integer && ((Integer) value).intValue() == marker) {
            return true;
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
                if (containsMarker(item, marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toURI().toURL()},
                ClassLoader.getPlatformClassLoader());
        int candidates = 0;
        int returned = 0;
        int rejected = 0;
        int resourceLeaks = 0;
        int encodedLeaks = 0;
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.equals("module-info.class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                Class<?> type;
                try {
                    type = Class.forName(className, false, loader);
                } catch (Throwable ignored) {
                    continue;
                }
                Method[] methods;
                try {
                    methods = type.getDeclaredMethods();
                } catch (Throwable ignored) {
                    continue;
                }
                for (Method method : methods) {
                    int modifiers = method.getModifiers();
                    if (!Modifier.isStatic(modifiers)
                            || method.getParameterCount() != 0
                            || !method.getReturnType().isArray()
                            || method.getReturnType().getComponentType() != Object.class) {
                        continue;
                    }
                    candidates++;
                    try {
                        method.setAccessible(true);
                        Object[] descriptor = (Object[]) method.invoke(null);
                        returned++;
                        if (containsMarker(descriptor, 0x53535234)) {
                            resourceLeaks++;
                        }
                        if (containsMarker(descriptor, 0x53535632)) {
                            encodedLeaks++;
                        }
                        System.out.println("RETURNED " + className + "#" + method.getName());
                    } catch (Throwable rejectedFailure) {
                        rejected++;
                    }
                }
            }
        } finally {
            loader.close();
        }
        System.out.println("candidates=" + candidates
                + " returned=" + returned
                + " rejected=" + rejected
                + " resourceLeaks=" + resourceLeaks
                + " encodedLeaks=" + encodedLeaks);
        if (returned > 0 || resourceLeaks > 0 || encodedLeaks > 0) {
            System.exit(2);
        }
    }
}
'@
Write-Utf8NoBom -Path (Join-Path $probeSrc 'BsmProbe.java') -Text @'
import java.io.File;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class BsmProbe {
    private static final class GuardPatchedLoader extends URLClassLoader implements Opcodes {
        private int patched;

        GuardPatchedLoader(File jar) throws Exception {
            super(new URL[]{jar.toURI().toURL()}, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try {
                URL resource = super.findResource(resourceName);
                if (resource == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes;
                try (java.io.InputStream input = resource.openStream()) {
                    bytes = input.readAllBytes();
                }
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                boolean changed = false;
                for (MethodNode method : (List<MethodNode>) node.methods) {
                    if (!"(Ljava/lang/Class;)V".equals(method.desc) || method.instructions == null) {
                        continue;
                    }
                    boolean stackGuard = false;
                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof MethodInsnNode call
                                && "java/lang/Thread".equals(call.owner)
                                && "getStackTrace".equals(call.name)) {
                            stackGuard = true;
                            break;
                        }
                    }
                    if (!stackGuard) {
                        continue;
                    }
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    method.localVariables = null;
                    method.instructions.add(new InsnNode(RETURN));
                    patched++;
                    changed = true;
                }
                if (changed) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    bytes = writer.toByteArray();
                }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (ClassNotFoundException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        GuardPatchedLoader loader = new GuardPatchedLoader(jar);
        int attempted = 0;
        int rejected = 0;
        int leaked = 0;
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes = jf.getInputStream(entry).readAllBytes();
                ClassNode classNode = new ClassNode();
                new ClassReader(bytes).accept(classNode, 0);
                String targetName = classNode.name.replace('/', '.');
                for (MethodNode methodNode : (List<MethodNode>) classNode.methods) {
                    if (methodNode.instructions == null) {
                        continue;
                    }
                    for (AbstractInsnNode insn = methodNode.instructions.getFirst();
                         insn != null;
                         insn = insn.getNext()) {
                        if (!(insn instanceof InvokeDynamicInsnNode)) {
                            continue;
                        }
                        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                        Handle bsmHandle = indy.bsm;
                        if (bsmHandle == null
                                || !bsmHandle.getDesc().contains("Ljava/lang/invoke/CallSite;")
                                || !bsmHandle.getDesc().contains("Ljava/lang/String;IIII)Ljava/lang/invoke/CallSite;")) {
                            continue;
                        }
                        Object[] bsmArgs = indy.bsmArgs;
                        if (bsmArgs == null
                                || bsmArgs.length < 5
                                || !(bsmArgs[0] instanceof String)
                                || !(bsmArgs[1] instanceof Integer)
                                || !(bsmArgs[2] instanceof Integer)
                                || !(bsmArgs[3] instanceof Integer)
                                || !(bsmArgs[4] instanceof Integer)) {
                            continue;
                        }
                        attempted++;
                        try {
                            Class<?> runtime = Class.forName(bsmHandle.getOwner().replace('/', '.'), false, loader);
                            Class<?> target = Class.forName(targetName, false, loader);
                            MethodType bootstrapType = MethodType.fromMethodDescriptorString(
                                    bsmHandle.getDesc(), loader);
                            Method bootstrap = runtime.getDeclaredMethod(
                                    bsmHandle.getName(), bootstrapType.parameterArray());
                            bootstrap.setAccessible(true);
                            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(target, MethodHandles.lookup());
                            Object result = bootstrap.invoke(null,
                                    lookup,
                                    indy.name,
                                    MethodType.fromMethodDescriptorString(indy.desc, loader),
                                    (String) bsmArgs[0],
                                    (Integer) bsmArgs[1],
                                    (Integer) bsmArgs[2],
                                    (Integer) bsmArgs[3],
                                    (Integer) bsmArgs[4]);
                            if (result instanceof CallSite) {
                                CallSite callSite = (CallSite) result;
                                Object value = callSite.dynamicInvoker().invokeWithArguments();
                                System.out.println("LEAK " + targetName + "." + methodNode.name + " -> " + value);
                                leaked++;
                            }
                        } catch (Throwable expected) {
                            rejected++;
                        }
                        if (attempted >= 16) {
                            System.out.println("attempted=" + attempted + " rejected=" + rejected
                                    + " leaked=" + leaked + " guardsPatched=" + loader.patched);
                            loader.close();
                            if (leaked > 0) {
                                System.exit(2);
                            }
                            return;
                        }
                    }
                }
            }
        } finally {
            loader.close();
        }
        System.out.println("attempted=" + attempted + " rejected=" + rejected
                + " leaked=" + leaked + " guardsPatched=" + loader.patched);
        if (attempted == 0) {
            System.exit(3);
        }
        if (leaked > 0) {
            System.exit(2);
        }
    }
}
'@
$metadataDescriptorProbeSource = Join-Path $ProbeSourceDir 'MetadataDescriptorProbe.java'
Assert-File -Path $metadataDescriptorProbeSource -Message "Missing metadata descriptor probe source: $metadataDescriptorProbeSource"
$probeSources = @(
    $metadataDescriptorProbeSource,
    (Join-Path $probeSrc 'VmMetaProbe.java'),
    (Join-Path $probeSrc 'BsmProbe.java')
)
$probeArgFile = Join-Path $WorkDir 'probe-sources.args'
Write-ArgFile -Path $probeArgFile -Items $probeSources
Invoke-LoggedProcess -Name 'compile-probes' -File $javac -Arguments @(
    '-encoding', 'UTF-8',
    '--release', '17',
    '-cp', $shieldJar,
    '-d', $probeClasses,
    "@$probeArgFile"
) | Out-Null
$probeCp = Join-ClassPath @($probeClasses, $shieldJar)
$metadataDescriptorProbe = Invoke-LoggedProcess -Name 'probe-metadata-descriptors' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    '-cp', $probeCp,
    'MetadataDescriptorProbe',
    $snakeMaxJar
)
Assert-LogKeyValueEquals -LogPath $metadataDescriptorProbe.Log -Key 'objectArrayDescriptors' -Expected 0 -Label 'Object[] VM metadata descriptor count'
$metadataVmAbiPublicStaticMethods = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiPublicStaticMethods'
$metadataVmAbiInvokestaticCallSites = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiInvokestaticCallSites'
$metadataVmAbiNativeBridgeNativeMethods = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiNativeBridgeNativeMethods'
$metadataVmAbiObjectObjectIntPublicStaticMethods = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiObjectObjectIntPublicStaticMethods'
$metadataVmAbiObjectObjectIntInvokestaticCallSites = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiObjectObjectIntInvokestaticCallSites'
$metadataVmAbiObjectObjectIntNativeMethods = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmAbiObjectObjectIntNativeMethods'
$metadataProgramFactoryShapeCandidates = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'programFactoryObjectArrayShapeCandidates'
$metadataReferencedProgramFactoryUniqueTargets = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'referencedProgramFactoryUniqueTargets'
$metadataVmCallsiteShapeCandidates = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmCallsiteShapeCandidates'
$metadataVmCallsiteObjectArrayEntryCandidates = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmCallsiteObjectArrayEntryCandidates'
$metadataVmCallsiteObjectObjectIntEntryCandidates = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'vmCallsiteObjectObjectIntEntryCandidates'
$metadataUniqueBootstrapOwners = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'uniqueBootstrapOwners'
$metadataBootstrapCentralityRiskClasses = Get-LogKeyValueInt -LogPath $metadataDescriptorProbe.Log -Key 'bootstrapVmCentralityRiskClasses'
if ($metadataVmAbiPublicStaticMethods -ne 0) {
    throw "Old VM ABI ([Object;[Object;)Object public/static methods must stay 0, got $metadataVmAbiPublicStaticMethods. Log: $($metadataDescriptorProbe.Log)"
}
if ($metadataVmAbiObjectObjectIntInvokestaticCallSites -gt 1) {
    throw "VM ABI (Object,Object,int)Object direct invokestatic call sites must stay <= 1, got $metadataVmAbiObjectObjectIntInvokestaticCallSites. Log: $($metadataDescriptorProbe.Log)"
}
if ($metadataUniqueBootstrapOwners -lt 4) {
    throw "Invokedynamic bootstrap owners must stay distributed across at least 4 owners, got $metadataUniqueBootstrapOwners. Log: $($metadataDescriptorProbe.Log)"
}
if ($metadataBootstrapCentralityRiskClasses -ne 0) {
    throw "Bootstrap/VM centrality risk classes must stay 0, got $metadataBootstrapCentralityRiskClasses. Log: $($metadataDescriptorProbe.Log)"
}
Write-Host "VM ABI ([Object;[Object;)Object public/static methods: $metadataVmAbiPublicStaticMethods"
Write-Host "VM ABI ([Object;[Object;)Object invokestatic call sites: $metadataVmAbiInvokestaticCallSites"
Write-Host "VM ABI ([Object;[Object;)Object NativeBridge native methods: $metadataVmAbiNativeBridgeNativeMethods"
Write-Host "VM ABI (Object,Object,int)Object public/static methods: $metadataVmAbiObjectObjectIntPublicStaticMethods"
Write-Host "VM ABI (Object,Object,int)Object invokestatic call sites: $metadataVmAbiObjectObjectIntInvokestaticCallSites"
Write-Host "VM ABI (Object,Object,int)Object native methods: $metadataVmAbiObjectObjectIntNativeMethods"
Write-Host "program factory (I)Object + Object[] shape candidates: $metadataProgramFactoryShapeCandidates"
Write-Host "referenced program factory unique targets: $metadataReferencedProgramFactoryUniqueTargets"
Write-Host "VM callsite triad candidates: $metadataVmCallsiteShapeCandidates"
Write-Host "VM callsite triad old Object[] entries: $metadataVmCallsiteObjectArrayEntryCandidates"
Write-Host "VM callsite triad Object,Object,int entries: $metadataVmCallsiteObjectObjectIntEntryCandidates"
Write-Host "unique invokedynamic bootstrap owners: $metadataUniqueBootstrapOwners"
Write-Host "bootstrap VM centrality risk classes: $metadataBootstrapCentralityRiskClasses"

$vmMetadataProbe = Invoke-LoggedProcess -Name 'probe-vm-metadata' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    "-Djava.library.path=$($TargetDir)\native",
    '-cp', $probeCp,
    'VmMetaProbe',
    $snakeMaxJar
)
Assert-LogKeyValueEquals -LogPath $vmMetadataProbe.Log -Key 'returned' -Expected 0 -Label 'reflection metadata returned'
Assert-LogKeyValueEquals -LogPath $vmMetadataProbe.Log -Key 'resourceLeaks' -Expected 0 -Label 'reflection metadata resource leaks'
Assert-LogKeyValueEquals -LogPath $vmMetadataProbe.Log -Key 'encodedLeaks' -Expected 0 -Label 'reflection metadata encoded leaks'

Write-Step 'Run bootstrap probe against string decrypt invokedynamic sites'
$bootstrapProbe = Invoke-LoggedProcess -Name 'probe-bootstrap' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    "-Djava.library.path=$($TargetDir)\native",
    '-cp', $probeCp,
    'BsmProbe',
    $snakeMaxJar
)
Assert-LogKeyValueEquals -LogPath $bootstrapProbe.Log -Key 'leaked' -Expected 0 -Label 'bootstrap semantic leaks'
$patchedBootstrapGuards = Get-LogKeyValueInt -LogPath $bootstrapProbe.Log -Key 'guardsPatched'
if ($patchedBootstrapGuards -lt 1) {
    throw "Bootstrap attack probe did not patch any Java stack guards. Log: $($bootstrapProbe.Log)"
}
Write-Host "bootstrap Java guards patched in analysis loader: $patchedBootstrapGuards"

Write-Step 'Run broad obfuscation leak probe'
$broadProbeJson = Join-Path $WorkDir 'obf-leak-probe.json'
Invoke-LoggedProcess -Name 'probe-broad-leaks' -File $powershell -Arguments @(
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    (Join-Path $ProjectRoot 'scripts\probe-obf-leaks.ps1'),
    '-Jar',
    $snakeMaxJar,
    '-Classpath',
    $shieldJar,
    '-OutFile',
    $broadProbeJson,
    '-SkipCompile',
    '-DetailLimit',
    '5',
    '-ValueStringLimit',
    '80',
    '-InvokeTimeoutMs',
    '1000'
) -Quiet | Out-Null
$broadProbe = Get-Content -Raw -LiteralPath $broadProbeJson | ConvertFrom-Json
if ([int] $broadProbe.methodDescriptorCounts.noArgObjectArrayDescriptor -ne 0) {
    throw "Broad probe found no-arg Object[] descriptors: $($broadProbe.methodDescriptorCounts.noArgObjectArrayDescriptor)."
}
if ([int] $broadProbe.methodDescriptorCounts.noArgObjectDescriptor -ne 0) {
    throw "Broad probe found no-arg Object descriptors: $($broadProbe.methodDescriptorCounts.noArgObjectDescriptor)."
}
if ([int] $broadProbe.metadataReflection.leaked -ne 0) {
    throw "Broad probe metadata reflection leaked $($broadProbe.metadataReflection.leaked) value(s)."
}
if ([int] $broadProbe.invokedynamicBootstrapOracle.leaked -ne 0) {
    throw "Broad probe invokedynamic oracle leaked $($broadProbe.invokedynamicBootstrapOracle.leaked) value(s)."
}
$constantCallSiteCtors = [int] $broadProbe.callSiteShapes.constantCallSiteCtor
if ($constantCallSiteCtors -ne 0) {
    throw "Broad probe found ConstantCallSite constructor use(s): $constantCallSiteCtors."
}
$methodHandlesConstants = [int] $broadProbe.callSiteShapes.methodHandlesConstant
if ($methodHandlesConstants -ne 0) {
    throw "Broad probe found MethodHandles.constant use(s): $methodHandlesConstants."
}
$runtimeConstantCallSiteCtors = [int] $broadProbe.runtimeApiRisk.runtimeConstantCallSiteCtor
if ($runtimeConstantCallSiteCtors -ne 0) {
    throw "Broad probe found runtime ConstantCallSite constructor use(s): $runtimeConstantCallSiteCtors."
}
$runtimeMethodHandlesConstants = [int] $broadProbe.runtimeApiRisk.runtimeMethodHandlesConstant
if ($runtimeMethodHandlesConstants -ne 0) {
    throw "Broad probe found runtime MethodHandles.constant use(s): $runtimeMethodHandlesConstants."
}
$staticBootstrapShapes = [int] $broadProbe.callSiteShapes.riskyStaticBootstrapShapes
if ($staticBootstrapShapes -ne 0) {
    throw "Broad probe found static bootstrap oracle shapes (ConstantCallSite/MethodHandles.constant): $staticBootstrapShapes."
}
$runtimeBootstrapShapes = [int] $broadProbe.runtimeApiRisk.riskyStaticBootstrapShapes
if ($runtimeBootstrapShapes -ne 0) {
    throw "Broad probe found runtime static bootstrap oracle shapes: $runtimeBootstrapShapes."
}
$bootstrapDescriptorStability = $broadProbe.invokedynamicScan.bootstrapDescriptorStability
$customBootstrapHandleArguments = [int] $broadProbe.invokedynamicScan.customBootstrapHandleArguments
$constantBootstrapDescriptorStability = $broadProbe.invokedynamicScan.constantCandidateBootstrapDescriptorStability
$bootstrapDescriptorDistinct = [int] $bootstrapDescriptorStability.distinctDescriptors
$bootstrapDescriptorDominantShare = [double] $bootstrapDescriptorStability.dominantDescriptorShare
$constantBootstrapDescriptorDistinct = [int] $constantBootstrapDescriptorStability.distinctDescriptors
$constantBootstrapDescriptorDominantShare = [double] $constantBootstrapDescriptorStability.dominantDescriptorShare
$publicStaticRuntimeApis = [int] $broadProbe.runtimeApiRisk.enumerablePublicStaticRuntimeApis
$publicStaticBootstrapApis = [int] $broadProbe.runtimeApiRisk.publicStaticBootstrapApis
$publicStaticLeakProneApis = [int] $broadProbe.runtimeApiRisk.publicStaticLeakProneDescriptors
$directRuntimeBootstrapForwardCalls = [int] $broadProbe.runtimeApiRisk.directRuntimeBootstrapForwardCalls
$broadVmAbiPublicStaticMethods = [int] $broadProbe.vmAbiShape.publicStaticMethods
$broadVmAbiInvokestaticCallSites = [int] $broadProbe.vmAbiShape.invokestaticCallSites
$broadVmAbiNativeBridgeNativeMethods = [int] $broadProbe.vmAbiShape.nativeBridgeNativeMethods
$broadVmAbiObjectObjectIntPublicStaticMethods = [int] $broadProbe.vmAbiShape.objectObjectIntPublicStaticMethods
$broadVmAbiObjectObjectIntInvokestaticCallSites = [int] $broadProbe.vmAbiShape.objectObjectIntInvokestaticCallSites
$broadVmAbiObjectObjectIntNativeMethods = [int] $broadProbe.vmAbiShape.objectObjectIntNativeMethods
$broadProgramFactoryShapeCandidates = [int] $broadProbe.vmAbiShape.programFactoryObjectArrayShapeCandidates
$broadReferencedProgramFactoryUniqueTargets = [int] $broadProbe.vmAbiShape.referencedProgramFactoryUniqueTargets
$broadVmCallsiteShapeCandidates = [int] $broadProbe.vmAbiShape.vmCallsiteShapeCandidates
$broadVmCallsiteObjectArrayEntryCandidates = [int] $broadProbe.vmAbiShape.vmCallsiteObjectArrayEntryCandidates
$broadVmCallsiteObjectObjectIntEntryCandidates = [int] $broadProbe.vmAbiShape.vmCallsiteObjectObjectIntEntryCandidates
$broadBootstrapCentralityRiskClasses = [int] $broadProbe.runtimeApiRisk.bootstrapVmCentralityRiskClasses
$broadBootstrapCentralityRiskScore = [int] $broadProbe.runtimeApiRisk.bootstrapVmCentralityRiskScore
$broadUniqueBootstrapOwners = [int] $broadProbe.invokedynamicScan.uniqueBootstrapOwners
$broadDominantBootstrapOwnerShare = [double] $broadProbe.runtimeApiRisk.dominantBootstrapOwnerShare
if ($broadVmAbiPublicStaticMethods -ne $metadataVmAbiPublicStaticMethods) {
    throw "Broad probe VM ABI public/static count $broadVmAbiPublicStaticMethods did not match metadata probe count $metadataVmAbiPublicStaticMethods."
}
if ($broadVmAbiInvokestaticCallSites -ne $metadataVmAbiInvokestaticCallSites) {
    throw "Broad probe VM ABI invokestatic call site count $broadVmAbiInvokestaticCallSites did not match metadata probe count $metadataVmAbiInvokestaticCallSites."
}
if ($broadVmAbiNativeBridgeNativeMethods -ne $metadataVmAbiNativeBridgeNativeMethods) {
    throw "Broad probe VM ABI NativeBridge native method count $broadVmAbiNativeBridgeNativeMethods did not match metadata probe count $metadataVmAbiNativeBridgeNativeMethods."
}
if ($broadVmAbiObjectObjectIntPublicStaticMethods -ne $metadataVmAbiObjectObjectIntPublicStaticMethods) {
    throw "Broad probe VM ABI Object,Object,int public/static count $broadVmAbiObjectObjectIntPublicStaticMethods did not match metadata probe count $metadataVmAbiObjectObjectIntPublicStaticMethods."
}
if ($broadVmAbiObjectObjectIntInvokestaticCallSites -ne $metadataVmAbiObjectObjectIntInvokestaticCallSites) {
    throw "Broad probe VM ABI Object,Object,int invokestatic count $broadVmAbiObjectObjectIntInvokestaticCallSites did not match metadata probe count $metadataVmAbiObjectObjectIntInvokestaticCallSites."
}
if ($broadVmAbiObjectObjectIntNativeMethods -ne $metadataVmAbiObjectObjectIntNativeMethods) {
    throw "Broad probe VM ABI Object,Object,int native count $broadVmAbiObjectObjectIntNativeMethods did not match metadata probe count $metadataVmAbiObjectObjectIntNativeMethods."
}
if ($broadProgramFactoryShapeCandidates -ne $metadataProgramFactoryShapeCandidates) {
    throw "Broad probe program factory shape count $broadProgramFactoryShapeCandidates did not match metadata probe count $metadataProgramFactoryShapeCandidates."
}
if ($broadReferencedProgramFactoryUniqueTargets -ne $metadataReferencedProgramFactoryUniqueTargets) {
    throw "Broad probe referenced program factory target count $broadReferencedProgramFactoryUniqueTargets did not match metadata probe count $metadataReferencedProgramFactoryUniqueTargets."
}
if ($broadVmCallsiteShapeCandidates -ne $metadataVmCallsiteShapeCandidates) {
    throw "Broad probe VM callsite triad count $broadVmCallsiteShapeCandidates did not match metadata probe count $metadataVmCallsiteShapeCandidates."
}
if ($broadVmCallsiteObjectArrayEntryCandidates -ne $metadataVmCallsiteObjectArrayEntryCandidates) {
    throw "Broad probe VM callsite old Object[] entry count $broadVmCallsiteObjectArrayEntryCandidates did not match metadata probe count $metadataVmCallsiteObjectArrayEntryCandidates."
}
if ($broadVmCallsiteObjectObjectIntEntryCandidates -ne $metadataVmCallsiteObjectObjectIntEntryCandidates) {
    throw "Broad probe VM callsite Object,Object,int entry count $broadVmCallsiteObjectObjectIntEntryCandidates did not match metadata probe count $metadataVmCallsiteObjectObjectIntEntryCandidates."
}
if ($broadVmAbiPublicStaticMethods -ne 0) {
    throw "Broad probe found old VM ABI ([Object;[Object;)Object public/static method(s): $broadVmAbiPublicStaticMethods."
}
if ($broadVmAbiObjectObjectIntInvokestaticCallSites -gt 1) {
    throw "Broad probe found too many direct VM ABI (Object,Object,int)Object invokestatic call sites: $broadVmAbiObjectObjectIntInvokestaticCallSites."
}
if ($broadUniqueBootstrapOwners -lt 4) {
    throw "Broad probe found insufficient bootstrap owner distribution: $broadUniqueBootstrapOwners owner(s)."
}
if ($broadDominantBootstrapOwnerShare -gt 0.35) {
    throw "Broad probe dominant bootstrap owner share exceeded 0.35: $broadDominantBootstrapOwnerShare."
}
if ($bootstrapDescriptorDistinct -lt 12) {
    throw "Broad probe found insufficient bootstrap descriptor diversity: $bootstrapDescriptorDistinct descriptor(s)."
}
if ($customBootstrapHandleArguments -ne 0) {
    throw "Broad probe found MethodHandle bootstrap arguments on custom call sites: $customBootstrapHandleArguments."
}
if ($bootstrapDescriptorDominantShare -gt 0.35) {
    throw "Broad probe dominant bootstrap descriptor share exceeded 0.35: $bootstrapDescriptorDominantShare."
}
if ($constantBootstrapDescriptorDistinct -lt 10) {
    throw "Broad probe found insufficient constant bootstrap descriptor diversity: $constantBootstrapDescriptorDistinct descriptor(s)."
}
if ($constantBootstrapDescriptorDominantShare -gt 0.35) {
    throw "Broad probe dominant constant bootstrap descriptor share exceeded 0.35: $constantBootstrapDescriptorDominantShare."
}
if ($publicStaticRuntimeApis -gt 31) {
    throw "Broad probe public static runtime API budget exceeded 31: $publicStaticRuntimeApis."
}
if ($publicStaticBootstrapApis -gt 20) {
    throw "Broad probe public static bootstrap API budget exceeded 20: $publicStaticBootstrapApis."
}
if ($publicStaticLeakProneApis -gt 30) {
    throw "Broad probe leak-prone runtime descriptor budget exceeded 30: $publicStaticLeakProneApis."
}
if ($directRuntimeBootstrapForwardCalls -ne 0) {
    throw "Broad probe found direct bootstrap forwards into the core runtime: $directRuntimeBootstrapForwardCalls."
}
if ($broadBootstrapCentralityRiskClasses -ne 0 -or $broadBootstrapCentralityRiskScore -ne 0) {
    throw "Broad probe found bootstrap/VM centrality risk: classes=$broadBootstrapCentralityRiskClasses score=$broadBootstrapCentralityRiskScore."
}
Write-Host 'broad probe no-arg Object descriptors: 0'
Write-Host 'broad probe metadata reflection leaks: 0'
Write-Host 'broad probe invokedynamic oracle leaks: 0'
Write-Host 'broad probe ConstantCallSite constructor uses: 0'
Write-Host 'broad probe MethodHandles.constant uses: 0'
Write-Host 'broad probe runtime ConstantCallSite constructor uses: 0'
Write-Host 'broad probe runtime MethodHandles.constant uses: 0'
Write-Host 'broad probe static bootstrap oracle shapes: 0'
Write-Host 'broad probe runtime static bootstrap oracle shapes: 0'
Write-Host "broad probe ConstantCallSite ctor calls: $($broadProbe.callSiteShapes.constantCallSiteCtor)"
Write-Host "broad probe MethodHandles.constant calls: $($broadProbe.callSiteShapes.methodHandlesConstant)"
Write-Host "broad probe ConstantCallSite+MethodHandles.constant methods: $($broadProbe.callSiteShapes.constantCallSiteAndMethodHandlesConstantMethods)"
Write-Host "broad probe bootstrap descriptor distinct: $($bootstrapDescriptorStability.distinctDescriptors)"
Write-Host "broad probe custom bootstrap MethodHandle arguments: $customBootstrapHandleArguments"
Write-Host "broad probe bootstrap descriptor dominant share: $($bootstrapDescriptorStability.dominantDescriptorShare)"
Write-Host "broad probe zero-arg constant bootstrap descriptor distinct: $($constantBootstrapDescriptorStability.distinctDescriptors)"
Write-Host "broad probe zero-arg constant bootstrap descriptor dominant share: $($constantBootstrapDescriptorStability.dominantDescriptorShare)"
Write-Host "broad probe public static runtime API candidates: $publicStaticRuntimeApis"
Write-Host "broad probe public static bootstrap APIs: $publicStaticBootstrapApis"
Write-Host "broad probe public static leak-prone runtime descriptors: $publicStaticLeakProneApis"
Write-Host "broad probe direct bootstrap forwards into core runtime: $directRuntimeBootstrapForwardCalls"
Write-Host "broad probe VM ABI ([Object;[Object;)Object public/static methods: $broadVmAbiPublicStaticMethods"
Write-Host "broad probe VM ABI ([Object;[Object;)Object invokestatic call sites: $broadVmAbiInvokestaticCallSites"
Write-Host "broad probe VM ABI ([Object;[Object;)Object NativeBridge native methods: $broadVmAbiNativeBridgeNativeMethods"
Write-Host "broad probe VM ABI (Object,Object,int)Object public/static methods: $broadVmAbiObjectObjectIntPublicStaticMethods"
Write-Host "broad probe VM ABI (Object,Object,int)Object invokestatic call sites: $broadVmAbiObjectObjectIntInvokestaticCallSites"
Write-Host "broad probe VM ABI (Object,Object,int)Object native methods: $broadVmAbiObjectObjectIntNativeMethods"
Write-Host "broad probe program factory (I)Object + Object[] shape candidates: $broadProgramFactoryShapeCandidates"
Write-Host "broad probe referenced program factory unique targets: $broadReferencedProgramFactoryUniqueTargets"
Write-Host "broad probe VM callsite triad candidates: $broadVmCallsiteShapeCandidates"
Write-Host "broad probe VM callsite triad old Object[] entries: $broadVmCallsiteObjectArrayEntryCandidates"
Write-Host "broad probe VM callsite triad Object,Object,int entries: $broadVmCallsiteObjectObjectIntEntryCandidates"
Write-Host "broad probe bootstrap VM centrality risk classes: $broadBootstrapCentralityRiskClasses"
Write-Host "broad probe bootstrap VM centrality risk score: $broadBootstrapCentralityRiskScore"
Write-Host "broad probe unique bootstrap owners: $broadUniqueBootstrapOwners"
Write-Host "broad probe dominant bootstrap owner share: $broadDominantBootstrapOwnerShare"

Write-Step 'CFR decompile and verify semantic words remain 0'
$resolvedCfrJar = Resolve-Cfr -Requested $CfrJar -DisableDownload:$NoCfrDownload
$cfrOut = Join-Path $WorkDir 'cfr-snake-max'
New-Item -ItemType Directory -Force -Path $cfrOut | Out-Null
Invoke-LoggedProcess -Name 'cfr-decompile' -File $java -Arguments @(
    '-jar', $resolvedCfrJar,
    $snakeMaxJar,
    '--outputdir', $cfrOut,
    '--caseinsensitivefs', 'true',
    '--comments', 'false',
    '--silent', 'true'
) -Quiet | Out-Null
$cfrJavaFiles = @(Get-ChildItem -LiteralPath $cfrOut -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
if ($cfrJavaFiles.Count -le 0) {
    throw "CFR did not emit Java files into $cfrOut"
}
$cfrHits = @(Find-TextHits -Files $cfrJavaFiles -Needles $semanticNeedles)
if ($cfrHits.Count -gt 0) {
    $cfrHits | Format-Table -AutoSize | Out-String | Write-Host
    throw "CFR semantic words expected 0, got $($cfrHits.Count) hit group(s)."
}
Write-Host "CFR Java files: $($cfrJavaFiles.Count)"
Write-Host 'CFR semantic hits: 0'
$cfrVmStructurePatterns = @{
    'native-object-array-vm-entry' = 'static\s+native\s+Object\s+\S+\s*\(\s*Object\[\]\s+\S+\s*,\s*Object\[\]\s+\S+\s*\)'
    'object-object-int-vm-entry' = 'static\s+Object\s+\S+\s*\(\s*Object\s+\S+\s*,\s*Object\s+\S+\s*,\s*int\s+\S+\s*\)'
    'private-static-program-factory' = 'private\s+static\s+(?:/\*\s*synthetic\s*\*/\s*)?Object\s+\S+\s*\(\s*int\s+\S+\s*\)'
    'object-array-allocation' = 'new\s+Object\[\s*\d+\s*\]'
    'mutable-callsite' = '\bMutableCallSite\b'
    'methodhandles-findstatic' = 'MethodHandles\.lookup\(\)\.findStatic'
    'methodhandle-lookup' = 'MethodHandles\.lookup\(\)'
}
$cfrVmStructureHits = @(Find-RegexTextHits -Files $cfrJavaFiles -Patterns $cfrVmStructurePatterns)
$cfrVmStructureRiskScore = 0
foreach ($hit in $cfrVmStructureHits) {
    $cfrVmStructureRiskScore += [int] $hit.Count
}
if ($cfrVmStructureHits.Count -gt 0) {
    $cfrVmStructureHits |
        Sort-Object Pattern, File |
        Select-Object -First 25 |
        Format-Table -AutoSize |
        Out-String |
        Write-Host
}
Write-Host "CFR VM structure regex groups: $($cfrVmStructureHits.Count)"
Write-Host "CFR VM structure regex risk score: $cfrVmStructureRiskScore"

Write-Step 'Compile CFR output and require failure'
$cfrClasses = Join-Path $WorkDir 'cfr-classes'
New-Item -ItemType Directory -Force -Path $cfrClasses | Out-Null
$cfrSourceArgFile = Join-Path $WorkDir 'cfr-sources.args'
Write-ArgFile -Path $cfrSourceArgFile -Items $cfrJavaFiles
$cfrCompile = Invoke-LoggedProcess -Name 'compile-cfr-output-expected-fail' -File $javac -Arguments @(
    '-encoding', 'UTF-8',
    '--release', '17',
    '-cp', (Join-ClassPath @($snakeMaxJar, $shieldJar)),
    '-d', $cfrClasses,
    "@$cfrSourceArgFile"
) -AllowedExitCodes (0..255) -Quiet
if ($cfrCompile.ExitCode -eq 0) {
    throw 'CFR output unexpectedly compiled successfully; expected javac failure for max obfuscated output.'
}
Write-Host "CFR compile failed as expected with exit code $($cfrCompile.ExitCode)"

Write-Step 'Remove native payload and require runtime failure'
$removedNativeEntries = Copy-JarWithoutNative -SourceJar $snakeMaxJar -DestinationJar $noNativeJar
Write-Host "Removed native entries: $removedNativeEntries"
$noNativeRun = Invoke-LoggedProcess -Name 'run-no-native-expected-fail' -File $java -Arguments @(
    '-Djava.awt.headless=true',
    '-jar', $noNativeJar
) -AllowedExitCodes (0..255) -Quiet
if ($noNativeRun.ExitCode -eq 0) {
    throw 'No-native jar unexpectedly exited 0; max/native-required output should fail when native payload is removed.'
}
$noNativeOutput = Get-Content -Raw -LiteralPath $noNativeRun.Log
if ($noNativeOutput -match 'SNAKE_OK') {
    throw 'No-native jar printed SNAKE_OK despite expected native-required failure.'
}
Write-Host "No-native run failed as expected with exit code $($noNativeRun.ExitCode)"
Write-Host 'remove native must fail: PASS'

Write-Step 'Regression summary'
Write-Host "shield jar:        $shieldJar"
Write-Host "snake release:     $snakeReleaseJar"
Write-Host "snake max:         $snakeMaxJar"
Write-Host "report:            $snakeMaxReport"
Write-Host "CFR output:        $cfrOut"
Write-Host "logs:              $LogDir"
Write-Host 'obf regression: PASS'
