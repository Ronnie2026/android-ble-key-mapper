param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($Rebuild) { & "$PSScriptRoot\build-app.ps1" }
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$sdkPathFile = Join-Path $PSScriptRoot '.tools\emulator-sdk-path.txt'
if (Test-Path -LiteralPath $sdkPathFile) {
    $configuredSdk = (Get-Content -LiteralPath $sdkPathFile -Raw).Trim()
    if (Test-Path -LiteralPath (Join-Path $configuredSdk 'emulator\emulator.exe')) { $sdk = $configuredSdk }
}
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = 'emulator-5554'
$apk = Join-Path $PSScriptRoot 'dist\YijiaButtons-v1.2.apk'
if (!(Test-Path $emulator) -or !(Test-Path $adb)) { throw 'Android Emulator is not installed.' }
if (!(Test-Path $apk)) { & "$PSScriptRoot\build-app.ps1" }
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$devices = & $adb devices
if (!($devices | Select-String '^emulator-5554\s')) {
    Start-Process -FilePath $emulator -WindowStyle Normal -ArgumentList @('-avd','Yijia_UI','-port','5554','-gpu','auto','-netdelay','none','-netspeed','full','-no-boot-anim','-timezone','Asia/Shanghai') -RedirectStandardOutput "$PSScriptRoot\emulator-out.log" -RedirectStandardError "$PSScriptRoot\emulator-error.log" | Out-Null
}
$deadline = [DateTime]::UtcNow.AddMinutes(5)
while ([DateTime]::UtcNow -lt $deadline) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $state = & $adb -s $serial shell getprop sys.boot_completed 2>$null
        $packageManager = & $adb -s $serial shell cmd package path android 2>$null
    }
    finally { $ErrorActionPreference = $previousErrorPreference }
    if (($state -join '').Trim() -eq '1' -and ($packageManager -join '').Trim().StartsWith('package:')) { break }
    Start-Sleep -Seconds 2
}
if ([DateTime]::UtcNow -ge $deadline) { throw 'Emulator boot timed out. See emulator-error.log.' }
& $adb -s $serial install -r $apk
if ($LASTEXITCODE) { throw 'Could not install the preview APK.' }
& $adb -s $serial shell am start -n com.yijia.buttons/.MainActivity
if ($LASTEXITCODE) { throw 'Could not open the preview app.' }
