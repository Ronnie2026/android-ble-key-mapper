$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Devices.Bluetooth.BluetoothAdapter,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
function Await-WinRT($Operation, [Type]$ResultType) {
    $method = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name.StartsWith('IAsyncOperation')
    } | Select-Object -First 1
    $task = $method.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    if (-not $task.Wait(15000)) { throw 'Bluetooth operation timed out' }
    $task.Result
}
$adapter = Await-WinRT ([Windows.Devices.Bluetooth.BluetoothAdapter]::GetDefaultAsync()) ([Windows.Devices.Bluetooth.BluetoothAdapter])
if ($null -eq $adapter) { throw 'No Bluetooth adapter available' }
$address = '{0:X12}' -f $adapter.BluetoothAddress
Write-Output ('Bluetooth address: ' + ($address -replace '(..)(?=.)', '$1:'))
$adapter | Select-Object IsLowEnergySupported,IsPeripheralRoleSupported,IsCentralRoleSupported,IsClassicSupported | ConvertTo-Json
$null = [Windows.Devices.Radios.Radio,Windows.System.Devices,ContentType=WindowsRuntime]
$radio = Await-WinRT ($adapter.GetRadioAsync()) ([Windows.Devices.Radios.Radio])
Write-Output "Radio state: $($radio.State)"
if ($env:YIJIA_ENABLE_BLUETOOTH -eq '1' -and $radio.State -ne 'On') {
    $access = Await-WinRT ([Windows.Devices.Radios.Radio]::RequestAccessAsync()) ([Windows.Devices.Radios.RadioAccessStatus])
    Write-Output "Radio access: $access"
    $stateResult = Await-WinRT ($radio.SetStateAsync([Windows.Devices.Radios.RadioState]::On)) ([Windows.Devices.Radios.RadioAccessStatus])
    Write-Output "Enable Bluetooth: $stateResult"
}
