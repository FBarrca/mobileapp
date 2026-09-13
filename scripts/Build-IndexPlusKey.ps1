param(
    [switch]$SkipTests,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$Java21 = $env:JAVA_HOME,
    [string]$Java17 = $env:JAVA17_HOME
)
$ErrorActionPreference = 'Stop'
$source = Split-Path $PSScriptRoot -Parent
foreach ($entry in @(@('AndroidSdk', $AndroidSdk), @('Java21', $Java21), @('Java17', $Java17))) {
    if ([string]::IsNullOrWhiteSpace($entry[1]) -or -not (Test-Path -LiteralPath $entry[1])) { throw "Set -$($entry[0]) to the installed toolchain directory." }
}
if (-not (Test-Path (Join-Path $source 'androidApp/src/google-services.json'))) { throw 'Provide local androidApp/src/google-services.json initialization configuration.' }
$env:JAVA_HOME = $Java21
$env:ANDROID_HOME = $AndroidSdk
$gitUtilities = Join-Path $env:ProgramFiles 'Git/usr/bin'
if (Test-Path $gitUtilities) { $env:PATH = "$gitUtilities;$env:PATH" }
$output = Join-Path $source 'artifacts/index-plus-key'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$arguments = @(':androidApp:assembleDebug')
if (-not $SkipTests) { $arguments += @(':experimental:testAndroidHostTest', '--tests', 'coredevices.ring.pluskey.PlusKeyGesturesTest', '--tests', 'coredevices.ring.endpoints.CustomEndpointsTest', '--tests', 'coredevices.ring.pluskey.setup.*') }
$arguments += @("-Porg.gradle.java.installations.paths=$($Java17.Replace('\','/'))", '--console=plain', '--no-configuration-cache')
Push-Location $source
try {
    & ./gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build failed ($LASTEXITCODE)." }
    $apk = Join-Path $output 'Index-Plus-Key.apk'
    Copy-Item -LiteralPath 'androidApp/build/outputs/apk/debug/androidApp-debug.apk' -Destination $apk -Force
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
} finally { Pop-Location }
