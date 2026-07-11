#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [string] $Jar,

    [string] $ProjectRoot,

    # Extra runtime dependencies for the obfuscated jar being probed.
    [string[]] $Classpath = @(),

    [string] $OutFile,

    [switch] $SkipCompile,
    [switch] $NoMetadataInvoke,
    [switch] $NoIndyInvoke,
    [switch] $FailOnProbeError,

    [int] $DetailLimit = 50,
    [int] $ValueStringLimit = 160,
    [long] $InvokeTimeoutMs = 2000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Join-Path $scriptDir '..'
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$JarPath = (Resolve-Path -LiteralPath $Jar).Path

$WorkDir = Join-Path $ProjectRoot 'target\obf-leak-probe'
$LogDir = Join-Path $WorkDir 'logs'
New-Item -ItemType Directory -Force -Path $WorkDir, $LogDir | Out-Null

function Resolve-Tool {
    param([Parameter(Mandatory)][string[]] $Names)
    foreach ($name in $Names) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cmd) { return $cmd.Source }
    }
    throw "Required tool not found on PATH: $($Names -join ', ')"
}

function Invoke-LoggedProcess {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $File,
        [string[]] $Arguments = @(),
        [int[]] $AllowedExitCodes = @(0)
    )
    $safe = $Name -replace '[^A-Za-z0-9_.-]+', '_'
    $log = Join-Path $LogDir ("$safe.log")
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $File @Arguments > $log 2>&1
        $exit = if ($null -ne $LASTEXITCODE) { [int] $LASTEXITCODE } else { 0 }
    } finally {
        $ErrorActionPreference = $old
    }
    if (-not ($AllowedExitCodes -contains $exit)) {
        $tail = if (Test-Path -LiteralPath $log) { (Get-Content -LiteralPath $log -Tail 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine } else { '' }
        throw "Step '$Name' failed with exit code $exit. Log: $log`n$tail"
    }
    return $log
}

function Get-JsonPropertyValue {
    param($Object, [Parameter(Mandatory)][string] $Name)
    if ($null -eq $Object) { return $null }
    $prop = $Object.PSObject.Properties[$Name]
    if ($prop) { return $prop.Value }
    return $null
}

$mvn = Resolve-Tool @('mvn.cmd', 'mvn')
$java = Resolve-Tool @('java.exe', 'java')

if (-not $SkipCompile) {
    Invoke-LoggedProcess -Name 'mvn-test-compile' -File $mvn -Arguments @('-q', '-DskipTests', 'test-compile') | Out-Null
}

$depFile = Join-Path $WorkDir 'test-classpath.txt'
Invoke-LoggedProcess -Name 'mvn-build-classpath' -File $mvn -Arguments @(
    '-q',
    '-DincludeScope=test',
    "-Dmdep.outputFile=$depFile",
    'dependency:build-classpath'
) | Out-Null

$sep = [System.IO.Path]::PathSeparator
$toolCpParts = @(
    (Join-Path $ProjectRoot 'target\test-classes'),
    (Join-Path $ProjectRoot 'target\classes')
)
if (Test-Path -LiteralPath $depFile) {
    $depCp = (Get-Content -LiteralPath $depFile -Raw).Trim()
    if (-not [string]::IsNullOrWhiteSpace($depCp)) {
        $toolCpParts += ($depCp -split [regex]::Escape([string] $sep) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    }
}
$toolCp = ($toolCpParts -join [string] $sep)

$probeArgs = @(
    '-cp', $toolCp,
    'biz.sushuo.shield.tools.ObfuscationLeakProbe',
    '--jar', $JarPath,
    '--detail-limit', [string] $DetailLimit,
    '--value-string-limit', [string] $ValueStringLimit,
    '--invoke-timeout-ms', [string] $InvokeTimeoutMs
)

if ($Classpath.Count -gt 0) {
    $resolvedCp = @()
    foreach ($item in $Classpath) {
        $resolvedCp += (Resolve-Path -LiteralPath $item).Path
    }
    $probeArgs += @('--cp', ($resolvedCp -join [string] $sep))
}
if ($NoMetadataInvoke) { $probeArgs += '--no-metadata-invoke' }
if ($NoIndyInvoke) { $probeArgs += '--no-indy-invoke' }
if ($FailOnProbeError) { $probeArgs += '--fail-on-probe-error' }

$jsonPath = Join-Path $WorkDir ('summary-{0:yyyyMMdd-HHmmss}-{1}.json' -f (Get-Date), $PID)
$errPath = Join-Path $LogDir ('probe-stderr-{0:yyyyMMdd-HHmmss}-{1}.log' -f (Get-Date), $PID)
$old = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $java @probeArgs > $jsonPath 2> $errPath
    $exit = if ($null -ne $LASTEXITCODE) { [int] $LASTEXITCODE } else { 0 }
} finally {
    $ErrorActionPreference = $old
}

if ($exit -ne 0) {
    $stdoutTail = if (Test-Path -LiteralPath $jsonPath) { (Get-Content -LiteralPath $jsonPath -Tail 20 -ErrorAction SilentlyContinue) -join [Environment]::NewLine } else { '' }
    $stderrTail = if (Test-Path -LiteralPath $errPath) { (Get-Content -LiteralPath $errPath -Tail 80 -ErrorAction SilentlyContinue) -join [Environment]::NewLine } else { '' }
    throw "Probe failed with exit code $exit. stdout=$jsonPath stderr=$errPath`n$stdoutTail`n$stderrTail"
}

$json = Get-Content -LiteralPath $jsonPath -Raw
# Validate that stdout remains machine-readable JSON before copying/emitting.
$summary = $json | ConvertFrom-Json
$indyScan = Get-JsonPropertyValue $summary 'invokedynamicScan'
$callSiteShapes = Get-JsonPropertyValue $summary 'callSiteShapes'
$vmAbiShape = Get-JsonPropertyValue $summary 'vmAbiShape'
$runtimeApiRisk = Get-JsonPropertyValue $summary 'runtimeApiRisk'
$descriptorStability = Get-JsonPropertyValue $indyScan 'bootstrapDescriptorStability'
Write-Verbose ("broad probe metrics: bootstrapDescriptorDistinct={0}; ConstantCallSite+MethodHandles.constant methods={1}; publicStaticRuntimeApis={2}; vmAbiOldPublicStatic={3}; vmAbiOldInvokestatic={4}; vmAbiOldNative={5}; vmAbiObjectObjectIntPublicStatic={6}; vmAbiObjectObjectIntInvokestatic={7}; programFactoryShapes={8}; vmCallsiteTriads={9}; bootstrapCentralityRiskClasses={10}; bootstrapCentralityRiskScore={11}" -f `
    (Get-JsonPropertyValue $descriptorStability 'distinctDescriptors'), `
    (Get-JsonPropertyValue $callSiteShapes 'constantCallSiteAndMethodHandlesConstantMethods'), `
    (Get-JsonPropertyValue $runtimeApiRisk 'enumerablePublicStaticRuntimeApis'), `
    (Get-JsonPropertyValue $vmAbiShape 'publicStaticMethods'), `
    (Get-JsonPropertyValue $vmAbiShape 'invokestaticCallSites'), `
    (Get-JsonPropertyValue $vmAbiShape 'nativeBridgeNativeMethods'), `
    (Get-JsonPropertyValue $vmAbiShape 'objectObjectIntPublicStaticMethods'), `
    (Get-JsonPropertyValue $vmAbiShape 'objectObjectIntInvokestaticCallSites'), `
    (Get-JsonPropertyValue $vmAbiShape 'programFactoryObjectArrayShapeCandidates'), `
    (Get-JsonPropertyValue $vmAbiShape 'vmCallsiteShapeCandidates'), `
    (Get-JsonPropertyValue $runtimeApiRisk 'bootstrapVmCentralityRiskClasses'), `
    (Get-JsonPropertyValue $runtimeApiRisk 'bootstrapVmCentralityRiskScore'))

if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
    $outParent = Split-Path -Parent $OutFile
    if ($outParent) { New-Item -ItemType Directory -Force -Path $outParent | Out-Null }
    Set-Content -LiteralPath $OutFile -Value $json -Encoding UTF8
}

Write-Output $json
