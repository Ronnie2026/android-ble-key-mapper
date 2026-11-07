using System.Collections.Generic;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using System;
using Windows.Storage.Streams;
public static class WinRtCollections {
    public static void AddAppearance(BluetoothLEAdvertisementPublisher publisher, BluetoothLEAdvertisementDataSection section) {
        publisher.Advertisement.DataSections.Add(section);
    }
    public static int CountClients(GattLocalCharacteristic report) { return report.SubscribedClients.Count; }
    public static string Send(GattLocalCharacteristic report, byte[] data) {
        var writer = new DataWriter(); writer.WriteBytes(data);
        var operation = report.NotifyValueAsync(writer.DetachBuffer());
        var deadline = DateTime.UtcNow.AddSeconds(10);
        while(operation.Status == Windows.Foundation.AsyncStatus.Started) {
            if(DateTime.UtcNow > deadline) throw new TimeoutException("Bluetooth notify timed out");
            System.Threading.Thread.Sleep(10);
        }
        var results = operation.GetResults();
        var statuses = new List<string>();
        foreach(var r in results) statuses.Add(r.Status.ToString());
        return String.Join(",", statuses);
    }
}
