package com.yijia.buttons;

import android.content.Context;
import java.io.*;

/** Starts only our worker and enables only our own key-filter service. */
public final class RootController {
  public static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  public static void run(String command) throws Exception {
    java.lang.Process process =
        new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      while (reader.readLine() != null) {}
    }
    if (process.waitFor() != 0) throw new IOException("请在 Magisk 中允许「蓝牙按键」使用 root");
  }

  public static void enableKeys() throws Exception {
    run(
        "component=com.yijia.buttons/com.yijia.buttons.KeyCaptureService; current=$(settings get"
            + " secure enabled_accessibility_services); [ \"$current\" = null ] && current=''; case"
            + " \":$current:\" in *\":$component:\"*) ;; *) if [ -n \"$current\" ]; then"
            + " current=\"$current:$component\"; else current=\"$component\"; fi; settings put"
            + " secure enabled_accessibility_services \"$current\";; esac; settings put secure"
            + " accessibility_enabled 1");
  }

  public static void simulator(Context context, boolean start) throws Exception {
    File directory = context.getFilesDir();
    String pid = quote(new File(directory, "receiver.pid").getPath());
    String command =
        "if [ -f "
            + pid
            + " ]; then p=$(cat "
            + pid
            + "); case $p in *[!0-9]*|'') ;; *) if tr '\\000' ' ' < /proc/$p/cmdline 2>/dev/null |"
            + " grep -q 'com.yijia.buttons.ReceiverMain'; then kill $p; fi;; esac; rm -f "
            + pid
            + "; fi; ";
    if (start)
      command +=
          "umask 022; CLASSPATH="
              + quote(context.getApplicationInfo().sourceDir)
              + " nohup app_process /system/bin com.yijia.buttons.ReceiverMain "
              + quote(new File(directory, "config.properties").getPath())
              + " "
              + quote(new File(directory, "state.properties").getPath())
              + " > "
              + quote(new File(directory, "events.log").getPath())
              + " 2>&1 < /dev/null & echo $! > "
              + pid
              + ";";
    run(command + " echo done");
  }
}
