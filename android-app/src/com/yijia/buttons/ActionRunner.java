package com.yijia.buttons;

import android.content.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Executes named commands; package identifiers stay inside this layer. */
public final class ActionRunner {
  private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor();

  public static void mapping(Context context, ConfigStore store, String mapping, long heldMs) {
    QUEUE.execute(
        () -> {
          Properties p = store.read();
          String command = p.getProperty("mapping." + mapping + ".command", "");
          try {
            long threshold = Long.parseLong(p.getProperty("holdMs", "1500"));
            if (heldMs >= threshold) {
              if (p.getProperty("longAction", "close").equals("close")) {
                Set<String> packages = new LinkedHashSet<>();
                for (String id : ConfigStore.ids(p, "mappings")) {
                  packages.addAll(
                      ConfigStore.packages(p, p.getProperty("mapping." + id + ".command", "")));
                }
                for (String pkg : packages)
                  if (valid(pkg) && !pkg.equals(context.getPackageName())) {
                    run(store, Arrays.asList("am", "force-stop", pkg));
                  }
                status(store, "已关闭映射指令中的应用");
              }
            } else execute(context, store, p, command);
          } catch (Exception e) {
            status(store, "动作未完成：" + e.getMessage());
          }
        });
  }

  public static void command(Context context, ConfigStore store, String command) {
    QUEUE.execute(
        () -> {
          try {
            execute(context, store, store.read(), command);
          } catch (Exception e) {
            status(store, "指令未完成：" + e.getMessage());
          }
        });
  }

  private static boolean valid(String pkg) {
    return pkg.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+");
  }

  private static void execute(Context context, ConfigStore store, Properties p, String command)
      throws Exception {
    List<String> packages = ConfigStore.packages(p, command);
    if (packages.isEmpty()) throw new IOException("请为指令选择应用");
    for (String pkg : packages) {
      if (!valid(pkg)) throw new IOException("应用配置无效");
      Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
      if (launch == null || launch.getComponent() == null) throw new IOException("所选应用已卸载或无法打开");
      if (context instanceof android.app.Activity) {
        // Foreground "try command" works in the UI preview without root.
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(300);
        continue;
      }
      run(
          store,
          Arrays.asList(
              "am",
              "start",
              "-W",
              "-a",
              Intent.ACTION_MAIN,
              "-c",
              Intent.CATEGORY_LAUNCHER,
              "-n",
              launch.getComponent().flattenToString()));
    }
    status(store, "已执行「" + ConfigStore.commandName(p, command) + "」");
  }

  private static void run(ConfigStore store, List<String> arguments) throws Exception {
    List<String> invocation = arguments;
    if (android.os.Process.myUid() != 0) {
      StringBuilder command = new StringBuilder();
      for (String argument : arguments) command.append(RootController.quote(argument)).append(' ');
      invocation = Arrays.asList("su", "-c", command.toString());
    }
    File output = File.createTempFile("action-", ".log", store.file(".").getCanonicalFile());
    try {
      java.lang.Process process =
          new ProcessBuilder(invocation).redirectErrorStream(true).redirectOutput(output).start();
      if (!process.waitFor(15, TimeUnit.SECONDS)) {
        process.destroy();
        throw new IOException("打开应用超时");
      }
      String text =
          new String(
              java.nio.file.Files.readAllBytes(output.toPath()),
              java.nio.charset.StandardCharsets.UTF_8);
      if (process.exitValue() != 0 || text.contains("Error:"))
        throw new IOException("系统未能执行，请检查 root 授权和应用");
    } finally {
      output.delete();
    }
  }

  private static void status(ConfigStore store, String message) {
    Properties p = new Properties();
    p.setProperty("message", message);
    p.setProperty("time", Long.toString(System.currentTimeMillis()));
    try {
      ConfigStore.writeFile(store.file("action.properties"), p);
    } catch (IOException ignored) {
    }
  }
}
