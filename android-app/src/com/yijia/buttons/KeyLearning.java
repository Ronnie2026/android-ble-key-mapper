package com.yijia.buttons;

import android.view.InputDevice;
import android.view.KeyEvent;
import java.io.*;
import java.util.*;

/** Learning is explicit, time-limited and never stores an ordinary typing history. */
public final class KeyLearning {
  public static int modifiers(KeyEvent event) {
    return (event.isShiftPressed() ? 1 : 0)
        | (event.isAltPressed() ? 2 : 0)
        | (event.isCtrlPressed() ? 4 : 0)
        | (event.isMetaPressed() ? 8 : 0);
  }

  public static String label(int keyCode, int modifiers) {
    String name = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");
    return ((modifiers & 4) != 0 ? "Ctrl + " : "")
        + ((modifiers & 2) != 0 ? "Alt + " : "")
        + ((modifiers & 1) != 0 ? "Shift + " : "")
        + ((modifiers & 8) != 0 ? "Meta + " : "")
        + name;
  }

  public static boolean external(KeyEvent event) {
    InputDevice device = event.getDevice();
    return device != null && device.isExternal() && event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN;
  }

  public static boolean capture(
      ConfigStore store,
      String source,
      String device,
      String deviceName,
      int code,
      int modifiers,
      String label) {
    Properties request = ConfigStore.readFile(store.file("learn.properties"));
    try {
      if (!source.equals(request.getProperty("source"))
          || System.currentTimeMillis() > Long.parseLong(request.getProperty("deadline", "0")))
        return false;
      String id = request.getProperty("request", "");
      if (id.isEmpty()) return false;
      Properties previous = ConfigStore.readFile(store.file("learn-result.properties"));
      if (id.equals(previous.getProperty("request"))) return true;
      Properties result = new Properties();
      result.setProperty("request", id);
      result.setProperty("source", source);
      result.setProperty("device", device);
      result.setProperty("deviceName", deviceName);
      result.setProperty("code", Integer.toString(code));
      result.setProperty("modifiers", Integer.toString(modifiers));
      result.setProperty("label", label);
      ConfigStore.writeFile(store.file("learn-result.properties"), result);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
