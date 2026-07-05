$ErrorActionPreference = 'Stop'

$javaHome = Split-Path -Parent (Split-Path -Parent (Get-Command java).Source)
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'include\jni.h'))) {
    $javaHome = $env:JAVA_HOME
}
$include = Join-Path $javaHome 'include'
$includeWin = Join-Path $include 'win32'
$source = Join-Path $PSScriptRoot 'sushuo1337_vm.c'
$outDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'target\native'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'sushuo1337_vm.dll'

$gcc = Get-Command gcc -ErrorAction SilentlyContinue
if ($gcc) {
    & $gcc.Source -shared -O2 -fvisibility=hidden -s "-I$include" "-I$includeWin" $source -o $out
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "Built $out"
} else {
    $cl = Get-Command cl.exe -ErrorAction SilentlyContinue
    if (-not $cl) {
        $vswherePaths = @(
            'C:\Program Files\Microsoft Visual Studio\Installer\vswhere.exe',
            'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
        )
        foreach ($vswhere in $vswherePaths) {
            if (Test-Path $vswhere) {
                $found = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find 'VC\Tools\MSVC\**\bin\Hostx64\x64\cl.exe' | Select-Object -First 1
                if ($found) {
                    $cl = Get-Item $found
                    break
                }
            }
        }
    }
    if (-not $cl) {
        Write-Warning 'No C compiler found. Install MSYS2/MinGW or Visual Studio Build Tools to build target\native\sushuo1337_vm.dll.'
        exit 10
    }

    $clPath = if ($cl.Source) { $cl.Source } else { $cl.FullName }
    $vcBin = Split-Path $clPath -Parent
    $vcTools = Split-Path (Split-Path (Split-Path $vcBin -Parent) -Parent) -Parent
    $vcInclude = Join-Path $vcTools 'include'
    $vcLib = Join-Path $vcTools 'lib\x64'
    $kitsRoot = 'C:\Program Files (x86)\Windows Kits\10'
    $includeArgs = @("/I$include", "/I$includeWin", "/I$vcInclude")
    $libArgs = @("/LIBPATH:$vcLib")
    if (Test-Path (Join-Path $kitsRoot 'Include')) {
        $sdkVersion = Get-ChildItem (Join-Path $kitsRoot 'Include') -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if ($sdkVersion) {
            $includeArgs += "/I$(Join-Path $sdkVersion.FullName 'ucrt')"
            $includeArgs += "/I$(Join-Path $sdkVersion.FullName 'um')"
            $includeArgs += "/I$(Join-Path $sdkVersion.FullName 'shared')"
            $libArgs += "/LIBPATH:$(Join-Path $kitsRoot "Lib\$($sdkVersion.Name)\um\x64")"
            $libArgs += "/LIBPATH:$(Join-Path $kitsRoot "Lib\$($sdkVersion.Name)\ucrt\x64")"
        }
    }
    $obj = Join-Path $outDir 'sushuo1337_vm.obj'
    & $clPath /nologo /O2 /LD $includeArgs /Fo"$obj" /Fe"$out" $source /link /OPT:REF /OPT:ICF /INCREMENTAL:NO $libArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "Built $out"
}

$resourceDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'target\classes\sushuo1337\sushuoprotect\lib\native\windows-x64'
New-Item -ItemType Directory -Force -Path $resourceDir | Out-Null
$rawResource = Join-Path $resourceDir 'sushuo1337_vm.dll'
$packedResource = Join-Path $resourceDir 'sushuo1337_vm.dll.dat'
if (-not ('SushuoNativePacker' -as [type])) {
    Add-Type -TypeDefinition @"
public static class SushuoNativePacker {
    public static byte[] EncodeLegacy(byte[] data) {
        byte[] encoded = (byte[]) data.Clone();
        unchecked {
            int state = 0x6D2B79F5 ^ encoded.Length;
            for (int i = 0; i < encoded.Length; i++) {
                state ^= i * 0x45D9F3B;
                state = (int) ((((uint) (state + 0x7F4A7C15)) << 9) | (((uint) (state + 0x7F4A7C15)) >> 23));
                state ^= (int) ((uint) state >> 13);
                state *= 0x5BD1E995;
                state ^= (int) ((uint) state >> 15);
                encoded[i] = (byte) (encoded[i] ^ (byte) ((uint) state >> 24));
            }
        }
        return encoded;
    }
}
"@
}
[byte[]] $nativeBytes = [System.IO.File]::ReadAllBytes($out)
[byte[]] $packedBytes = [SushuoNativePacker]::EncodeLegacy($nativeBytes)
[System.IO.File]::WriteAllBytes($packedResource, $packedBytes)
if (Test-Path $rawResource) {
    Remove-Item -LiteralPath $rawResource -Force
}
Write-Host "Packed encrypted native resource to $packedResource"
