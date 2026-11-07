$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\bluetooth-probe.ps1"
Add-Type -AssemblyName System.Windows.Forms
if (!(Test-Path "$PSScriptRoot\WinRtCollections-v2.dll")) { & "$PSScriptRoot\build-windows-simulator.ps1" }
Add-Type -Path "$PSScriptRoot\WinRtCollections-v2.dll"
$null = [Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProvider,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.DataWriter,Windows.Storage.Streams,ContentType=WindowsRuntime]
$null = [Windows.Storage.Streams.IBuffer,Windows.Storage.Streams,ContentType=WindowsRuntime]
function Buffer([byte[]]$Bytes) {
    return ,([System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::AsBuffer($Bytes))
}
function Uuid([string]$Short) { [Guid]("0000$Short-0000-1000-8000-00805f9b34fb") }
function Characteristic($Service, $Uuid, $Properties, [byte[]]$Value) {
    $p = New-Object Windows.Devices.Bluetooth.GenericAttributeProfile.GattLocalCharacteristicParameters
    $p.CharacteristicProperties = $Properties
    $p.ReadProtectionLevel = 'EncryptionRequired'
    $p.WriteProtectionLevel = 'EncryptionRequired'
    if ($null -ne $Value -and $Value.Length -gt 0) { $p.StaticValue = Buffer $Value }
    $r = Await-WinRT ($Service.CreateCharacteristicAsync((Uuid $Uuid),$p)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattLocalCharacteristicResult])
    if ($r.Error -ne 'Success') { throw "Characteristic $Uuid failed: $($r.Error)" }
    $r.Characteristic
}
if (-not $adapter.IsPeripheralRoleSupported) { throw 'BLE peripheral role is unavailable' }
$result = Await-WinRT ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProvider]::CreateAsync((Uuid '1812'))) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProviderResult])
if ($result.Error -ne 'Success') { throw "HID service failed: $($result.Error)" }
$provider = $result.ServiceProvider
$service = $provider.Service
# Standard eight-byte keyboard input report, with no report ID in the payload.
$map = [byte[]](0x05,0x01,0x09,0x06,0xA1,0x01,0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x08,0x81,0x02,0x95,0x01,0x75,0x08,0x81,0x01,0x95,0x06,0x75,0x08,0x15,0x00,0x25,0x73,0x05,0x07,0x19,0x00,0x29,0x73,0x81,0x00,0xC0)
$report = Characteristic $service '2a4d' 'Read, Notify' ([byte[]]::new(8))
$map = [byte[]]($map[0..5] + @(0x85,0x01) + $map[6..($map.Length-1)])
$dp = New-Object Windows.Devices.Bluetooth.GenericAttributeProfile.GattLocalDescriptorParameters
$dp.ReadProtectionLevel = 'EncryptionRequired'
$dp.StaticValue = Buffer ([byte[]](1,1))
$descriptor = Await-WinRT ($report.CreateDescriptorAsync((Uuid '2908'),$dp)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattLocalDescriptorResult])
if ($descriptor.Error -ne 'Success') { throw "Report descriptor failed: $($descriptor.Error)" }
$null = Characteristic $service '2a4b' 'Read' $map
$null = Characteristic $service '2a4a' 'Read' ([byte[]](0x11,0x01,0,2))
$null = Characteristic $service '2a4c' 'WriteWithoutResponse' $null
$adv = New-Object Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProviderAdvertisingParameters
$adv.IsDiscoverable = $true
$adv.IsConnectable = $true
$provider.StartAdvertising($adv)
$markerResult = Await-WinRT ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProvider]::CreateAsync([Guid]'7d57a001-841e-4aee-bb8e-768a425a7a71')) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattServiceProviderResult])
if ($markerResult.Error -ne 'Success') { throw "Marker service failed: $($markerResult.Error)" }
$markerProvider = $markerResult.ServiceProvider
$markerProvider.StartAdvertising($adv)
$null = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
$appearancePublisher = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher
$section = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementDataSection
$section.DataType = 0x19
$section.Data = Buffer ([byte[]](0xC1,0x03))
[WinRtCollections]::AddAppearance($appearancePublisher,$section)
try { $appearancePublisher.Start() } catch { "Optional appearance advertisement unavailable: $($_.Exception.Message)" | Set-Content "$PSScriptRoot\appearance-error.log" }
Start-Sleep -Seconds 2
"Advertisement status: $($provider.AdvertisementStatus)" | Tee-Object -FilePath "$PSScriptRoot\bluetooth-status.log"
if ($provider.AdvertisementStatus -ne 'Started') { throw "Advertising failed: $($provider.AdvertisementStatus)" }
if ($env:YIJIA_PROBE_ONLY -eq '1') { $provider.StopAdvertising(); exit }
$form = New-Object System.Windows.Forms.Form
$form.Text = 'Android BLE Key Mapper buttons'
$form.Width = 510; $form.Height = 240
$label = New-Object System.Windows.Forms.Label
$label.SetBounds(15,15,470,55)
$label.Text = 'A/B/C send F13/F14/F15. Configure actions and long-press behavior in the Android app. Requires the phone-side simulator receiver.'
$form.Controls.Add($label)
function Send-Key([byte]$Code) {
    $bytes = [byte[]]::new(8); $bytes[2] = $Code
    $status = [WinRtCollections]::Send($report,$bytes)
    "Code=$Code Notify=$status" | Add-Content "$PSScriptRoot\hid-notify.log"
}
foreach ($i in 0..2) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = [string][char](65+$i)
    $button.Tag = [byte](0x68+$i)
    $button.SetBounds((20+$i*155),85,140,65)
    $button.Add_MouseDown({ try { Send-Key $this.Tag } catch { $label.Text = $_.Exception.Message } })
    $button.Add_MouseUp({ try { Send-Key 0 } catch { $label.Text = $_.Exception.Message } })
    $form.Controls.Add($button)
}
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 1000
$timer.Add_Tick({
    $clientCount = [WinRtCollections]::CountClients($report)
    $form.Text = "Android BLE Key Mapper - subscribers: $clientCount"
    "HID=$($provider.AdvertisementStatus) Appearance=$($appearancePublisher.Status) Clients=$clientCount" | Set-Content "$PSScriptRoot\bluetooth-live.log"
    $commandPath = "$PSScriptRoot\hid-command.txt"
    if (Test-Path $commandPath) {
        $command = (Get-Content $commandPath -Raw).Trim().Split(',')
        Remove-Item -LiteralPath $commandPath
        try {
            if ($clientCount -eq 0) { throw 'No subscribed Bluetooth receiver' }
            Send-Key ([byte]$command[0])
            Start-Sleep -Milliseconds ([int]$command[1])
            Send-Key 0
            'Sent Bluetooth press and release' | Set-Content "$PSScriptRoot\hid-test.log"
        } catch { $_.Exception.ToString() | Set-Content "$PSScriptRoot\hid-test.log" }
    }
})
$timer.Start()
try { [System.Windows.Forms.Application]::Run($form) } finally { $timer.Stop(); $provider.StopAdvertising(); $markerProvider.StopAdvertising(); $appearancePublisher.Stop() }
