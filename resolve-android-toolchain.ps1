# Shared by the build scripts. Supports a standard SDK or the original local cache.
function Get-AndroidToolchain {
    $jdkCandidates = @($env:JAVA_HOME, (Join-Path $PSScriptRoot '.tools\jdk\jdk-17.0.21+9'))
    $jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
    if (!$jdk) { throw 'JDK not found. Install JDK 17 and set JAVA_HOME.' }

    $sdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
    $marker = Join-Path $PSScriptRoot '.tools\emulator-sdk-path.txt'
    if (Test-Path $marker) { $sdkCandidates += (Get-Content $marker -Raw).Trim() }
    $platform = $null
    $buildTools = $null
    foreach ($sdk in ($sdkCandidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (!$platform -and (Test-Path "$sdk\platforms\android-30\android.jar")) { $platform = "$sdk\platforms\android-30\android.jar" }
        if (!$buildTools -and (Test-Path "$sdk\build-tools")) {
            $buildTools = Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object { try { [version]($_.Name -replace '-.*$', '') } catch { [version]'0.0' } } -Descending |
                Where-Object { Test-Path (Join-Path $_.FullName 'aapt.exe') } | Select-Object -First 1 -ExpandProperty FullName
        }
    }
    if (!$platform -and (Test-Path "$PSScriptRoot\.tools\android-platform\android-11\android.jar")) { $platform = "$PSScriptRoot\.tools\android-platform\android-11\android.jar" }
    if (!$buildTools -and (Test-Path "$PSScriptRoot\.tools\build-tools\android-11\aapt.exe")) { $buildTools = "$PSScriptRoot\.tools\build-tools\android-11" }
    if (!$platform -or !$buildTools) { throw 'Android toolchain not found. Install platforms;android-30 and build-tools;35.0.0, then set ANDROID_SDK_ROOT.' }
    $dexCandidates = @("$PSScriptRoot\.tools\r8-probe.jar", "$buildTools\lib\d8.jar", "$buildTools\lib\r8.jar")
    $dex = $dexCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (!$dex) { throw 'D8/R8 compiler not found in Android Build Tools.' }
    [pscustomobject]@{ JavaBin = (Join-Path $jdk 'bin'); AndroidJar = $platform; BuildTools = $buildTools; DexJar = $dex }
}
