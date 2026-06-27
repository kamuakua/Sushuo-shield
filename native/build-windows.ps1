$ErrorActionPreference = 'Stop'

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
$include = Join-Path $javaHome 'include'
$includeWin = Join-Path $include 'win32'
$source = Join-Path $PSScriptRoot 'sushuo1337_vm.c'
$outDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'target\native'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'sushuo1337_vm.dll'

$gcc = Get-Command gcc -ErrorAction SilentlyContinue
if (-not $gcc) {
    throw 'gcc not found. Install MSYS2/MinGW or compile native/sushuo1337_vm.c with your C toolchain.'
}

& $gcc.Source -shared -O2 "-I$include" "-I$includeWin" $source -o $out
Write-Host "Built $out"
