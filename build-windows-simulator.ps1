$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$runtime = Get-ChildItem (Join-Path $env:WINDIR 'Microsoft.NET\assembly\GAC_MSIL\System.Runtime') -Filter System.Runtime.dll -Recurse | Select-Object -First 1 -ExpandProperty FullName
$metadata = Join-Path $env:WINDIR 'System32\WinMetadata'
& "$framework\csc.exe" /nologo /target:library /out:WinRtCollections-v2.dll "/r:$metadata\Windows.Devices.winmd" "/r:$metadata\Windows.Foundation.winmd" "/r:$metadata\Windows.Storage.winmd" "/r:$runtime" WinRtCollections.cs
if ($LASTEXITCODE) { throw 'Windows BLE helper compilation failed.' }
