$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) {
    $Sdk = $env:ANDROID_SDK_ROOT
}
if (-not $Sdk) {
    $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$BuildToolsRoot = Join-Path $Sdk 'build-tools'
$BuildTools = Join-Path $BuildToolsRoot '35.0.0'
if (-not (Test-Path $BuildTools)) {
    $BuildTools = Get-ChildItem $BuildToolsRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

$PlatformJar = Join-Path $Sdk 'platforms\android-34\android.jar'
if (-not (Test-Path $PlatformJar)) {
    $PlatformJar = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'android.jar' } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}

$Aapt2 = Join-Path $BuildTools 'aapt2.exe'
$Aapt = Join-Path $BuildTools 'aapt.exe'
$D8 = Join-Path $BuildTools 'd8.bat'
$Zipalign = Join-Path $BuildTools 'zipalign.exe'
$Apksigner = Join-Path $BuildTools 'apksigner.bat'

function Assert-LastExitCode {
    param([string] $Step)
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$Intermediates = Join-Path $env:TEMP "autowsgr-build-$RunId"
$Dist = Join-Path $Root 'build\dist'
New-Item -ItemType Directory -Force $Intermediates, $Dist | Out-Null

$Manifest = Join-Path $Root 'app\src\main\AndroidManifest.xml'
$ResDir = Join-Path $Root 'app\src\main\res'
$AssetDir = Join-Path $Root 'app\src\main\assets'
$SrcDir = Join-Path $Root 'app\src\main\java'
$CompiledRes = Join-Path $Intermediates 'compiled-res'
$GenDir = Join-Path $Intermediates 'gen'
$ClassesDir = Join-Path $Intermediates 'classes'
$ClassesJar = Join-Path $Intermediates 'classes.jar'
$DexDir = Join-Path $Intermediates 'dex'
$Unsigned = Join-Path $Intermediates 'unsigned.apk'
$UnsignedDex = Join-Path $Intermediates 'unsigned-dex.apk'
$Aligned = Join-Path $Intermediates 'aligned.apk'
$Keystore = Join-Path $env:TEMP 'autowsgr-debug.keystore'
$SignedTemp = Join-Path $Intermediates 'AutoWSGR-Mobile-debug.apk'
$Apk = Join-Path $Dist "AutoWSGR-Mobile-debug-$RunId.apk"

New-Item -ItemType Directory -Force $CompiledRes, $GenDir, $ClassesDir, $DexDir | Out-Null

$ResourceFiles = Get-ChildItem $ResDir -Recurse -File |
    Where-Object { $_.Extension -in @('.xml', '.png', '.jpg', '.jpeg', '.webp') }
foreach ($ResourceFile in $ResourceFiles) {
    & $Aapt2 compile $ResourceFile.FullName -o $CompiledRes
    Assert-LastExitCode "aapt2 compile $($ResourceFile.Name)"
}
$CompiledFlats = Get-ChildItem $CompiledRes -File -Filter '*.flat' | Select-Object -ExpandProperty FullName
& $Aapt2 link `
    -I $PlatformJar `
    --manifest $Manifest `
    --java $GenDir `
    --min-sdk-version 26 `
    --target-sdk-version 34 `
    --version-code 77 `
    --version-name '0.77.0' `
    --auto-add-overlay `
    -A $AssetDir `
    -o $Unsigned `
    $CompiledFlats
Assert-LastExitCode 'aapt2 link'

$Sources = @()
$Sources += Get-ChildItem $SrcDir -Recurse -Filter '*.java'
$Sources += Get-ChildItem $GenDir -Recurse -Filter '*.java'
& javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $PlatformJar -d $ClassesDir $Sources.FullName
Assert-LastExitCode 'javac'

Push-Location $ClassesDir
try {
    & jar cf $ClassesJar .
    Assert-LastExitCode 'jar'
}
finally {
    Pop-Location
}

& $D8 --lib $PlatformJar --output $DexDir $ClassesJar
Assert-LastExitCode 'd8'
Copy-Item $Unsigned $UnsignedDex -Force
Push-Location $DexDir
try {
    & $Aapt add $UnsignedDex 'classes.dex'
    Assert-LastExitCode 'aapt add classes.dex'
}
finally {
    Pop-Location
}

& $Zipalign -f -p 4 $UnsignedDex $Aligned
Assert-LastExitCode 'zipalign'

if (-not (Test-Path $Keystore)) {
    & keytool -genkeypair `
        -keystore $Keystore `
        -storepass android `
        -keypass android `
        -alias autowsgrdebug `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname 'CN=AutoWSGR Mobile Debug,O=OpenWSGR,C=CN'
    Assert-LastExitCode 'keytool'
}

& $Apksigner sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $SignedTemp `
    $Aligned
Assert-LastExitCode 'apksigner sign'

& $Apksigner verify --verbose $SignedTemp
Assert-LastExitCode 'apksigner verify'
[System.IO.File]::Copy($SignedTemp, $Apk, $false)
Write-Host "APK: $Apk"
