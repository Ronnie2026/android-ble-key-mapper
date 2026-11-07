package com.yijia.buttons;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;

/** Optional compatibility transport for the existing Windows BLE test buttons. */
public class ReceiverMain {
  private static ConfigStore store;
  private static File stateFile;
  private static Context context;
  private static final Map<Integer, Press> pressed = new HashMap<>();

  private static class Press {
    long time;
    String mapping;

    Press(String mapping) {
      this.mapping = mapping;
      time = SystemClock.elapsedRealtime();
    }
  }

  private static void state(String message) {
    System.out.println(message);
    Properties state = new Properties();
    state.setProperty("message", message);
    try {
      ConfigStore.writeFile(stateFile, state);
    } catch (IOException ignored) {
    }
  }

  public static void main(String[] args) throws Exception {
    store = new ConfigStore(new File(args[0]).getParentFile());
    stateFile = new File(args[1]);
    Looper.prepareMainLooper();
    Class<?> activityThread = Class.forName("android.app.ActivityThread");
    Object thread = activityThread.getMethod("systemMain").invoke(null);
    context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      state("未找到蓝牙适配器");
      return;
    }
    if (!adapter.isEnabled()) adapter.enable();
    new Handler().postDelayed(() -> scan(adapter), 1500);
    Looper.loop();
  }

  private static void scan(BluetoothAdapter adapter) {
    state("正在寻找电脑模拟器…");
    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
    if (scanner == null) {
      new Handler().postDelayed(() -> scan(adapter), 3000);
      return;
    }
    scanner.startScan(
        Arrays.asList(
            new ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("7d57a001-841e-4aee-bb8e-768a425a7a71"))
                .build()),
        new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
        new ScanCallback() {
          public void onScanResult(int type, ScanResult result) {
            if (!result
                .getDevice()
                .getAddress()
                .equalsIgnoreCase(
                    store.read().getProperty("address", ConfigStore.SIMULATOR_ADDRESS))) return;
            scanner.stopScan(this);
            connect(result.getDevice());
          }

          public void onScanFailed(int error) {
            state("扫描失败，请重新启动模拟接收");
          }
        });
  }

  private static void connect(BluetoothDevice device) {
    state("正在连接电脑…");
    device.connectGatt(
        context,
        false,
        new BluetoothGattCallback() {
          public void onConnectionStateChange(BluetoothGatt gatt, int status, int connection) {
            if (connection == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices();
            else if (connection == BluetoothProfile.STATE_DISCONNECTED) {
              pressed.clear();
              state("模拟连接已断开，正在重连…");
              new Handler(Looper.getMainLooper()).postDelayed(() -> gatt.connect(), 2500);
            }
          }

          public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService hid =
                gatt.getService(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"));
            BluetoothGattCharacteristic report =
                hid == null
                    ? null
                    : hid.getCharacteristic(
                        UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb"));
            BluetoothGattDescriptor notify =
                report == null
                    ? null
                    : report.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (notify == null) {
              state("未找到模拟按键接口");
              return;
            }
            gatt.setCharacteristicNotification(report, true);
            notify.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(notify);
          }

          public void onDescriptorWrite(
              BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            state(status == 0 ? "模拟器已连接 · 等待按键" : "订阅失败，请重新连接");
          }

          public void onCharacteristicChanged(
              BluetoothGatt gatt, BluetoothGattCharacteristic report) {
            byte[] data = report.getValue();
            if (data == null || data.length < 3) return;
            int flags = data[0] & 255;
            int modifiers =
                ((flags & 0x22) != 0 ? 1 : 0)
                    | ((flags & 0x44) != 0 ? 2 : 0)
                    | ((flags & 0x11) != 0 ? 4 : 0)
                    | ((flags & 0x88) != 0 ? 8 : 0);
            Set<Integer> current = new HashSet<>();
            for (int i = 2; i < data.length; i++)
              if ((data[i] & 255) > 3) current.add(data[i] & 255);
            String address = device.getAddress();
            for (int code : current)
              if (!pressed.containsKey(code)) {
                String label = code >= 104 && code <= 115 ? "F" + (code - 91) : "模拟按键 " + code;
                boolean learning =
                    KeyLearning.capture(store, "ble", address, "电脑模拟器", code, modifiers, label);
                String mapping =
                    learning
                        ? null
                        : ConfigStore.findMapping(store.read(), "ble", address, code, modifiers);
                pressed.put(code, new Press(mapping));
              }
            for (int code : new ArrayList<>(pressed.keySet()))
              if (!current.contains(code)) {
                Press press = pressed.remove(code);
                if (press.mapping != null)
                  ActionRunner.mapping(
                      context, store, press.mapping, SystemClock.elapsedRealtime() - press.time);
              }
          }
        },
        BluetoothDevice.TRANSPORT_LE);
  }
}
