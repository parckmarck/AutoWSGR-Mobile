$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Gradlew = Join-Path $Root 'gradlew.bat'
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$Dist = Join-Path $Root 'build\dist'

& $Gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

$SourceApk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $SourceApk)) { throw "Gradle APK not found: $SourceApk" }
New-Item -ItemType Directory -Force $Dist | Out-Null
$Apk = Join-Path $Dist "AutoWSGR-Mobile-debug-$RunId.apk"
Copy-Item -LiteralPath $SourceApk -Destination $Apk -Force

$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$Apksigner = Join-Path $Sdk 'build-tools\35.0.0\apksigner.bat'
& $Apksigner verify --verbose $Apk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $LASTEXITCODE" }
Write-Host "APK: $Apk"
