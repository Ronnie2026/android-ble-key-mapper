package com.yijia.buttons;

import java.io.*;
import java.util.*;

/** The on-device format is shared by the UI, key service and optional BLE worker. */
public final class ConfigStore {
  public static final String SIMULATOR_ADDRESS = "";
  private final File directory;

  public ConfigStore(File directory) {
    this.directory = directory;
  }

  public File file(String name) {
    return new File(directory, name);
  }

  public Properties read() {
    return readFile(file("config.properties"));
  }

  public static Properties readFile(File file) {
    Properties properties = new Properties();
    try (FileInputStream stream = new FileInputStream(file)) {
      properties.load(stream);
    } catch (IOException ignored) {
    }
    return properties;
  }

  public static synchronized void writeFile(File file, Properties properties) throws IOException {
    File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
    try (FileOutputStream stream = new FileOutputStream(temporary)) {
      properties.store(stream, "Yijia Buttons");
      stream.getFD().sync();
    }
    if (!temporary.renameTo(file)) throw new IOException("无法保存配置");
  }

  public void save(Properties properties) throws IOException {
    writeFile(file("config.properties"), properties);
  }

  public static List<String> ids(Properties p, String key) {
    String value = p.getProperty(key, "");
    return new ArrayList<>(
        value.isEmpty() ? Collections.emptyList() : Arrays.asList(value.split(",")));
  }

  public static void append(Properties p, String key, String id) {
    List<String> values = ids(p, key);
    if (!values.contains(id)) values.add(id);
    p.setProperty(key, String.join(",", values));
  }

  public static String newId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static List<String> packages(Properties p, String command) {
    return ids(p, "command." + command + ".packages");
  }

  public static String commandName(Properties p, String command) {
    return p.getProperty("command." + command + ".name", "未命名指令");
  }

  public static void remove(Properties p, String list, String prefix, String id) {
    List<String> values = ids(p, list);
    values.remove(id);
    p.setProperty(list, String.join(",", values));
    for (String key : new ArrayList<>(p.stringPropertyNames())) {
      if (key.startsWith(prefix + id + ".")) p.remove(key);
    }
  }

  public void migrate() throws IOException {
    Properties p = read();
    if (p.getProperty("schema", "").equals("2")) return;
    boolean legacy = p.containsKey("a") || p.containsKey("b") || p.containsKey("c");
    p.setProperty("schema", "2");
    p.setProperty("mode", legacy ? "ble" : "keyboard");
    p.setProperty("address", p.getProperty("address", SIMULATOR_ADDRESS));
    p.setProperty("holdMs", p.getProperty("holdMs", "1500"));
    p.setProperty("longAction", p.getProperty("longAction", "close"));
    String[] keys = {"a", "b", "c"};
    for (int index = 0; legacy && index < keys.length; index++) {
      String pkg = p.getProperty(keys[index], "");
      if (pkg.isEmpty()) continue;
      String id = "legacy" + index;
      append(p, "commands", id);
      p.setProperty("command." + id + ".name", "指令 " + (char) ('A' + index));
      p.setProperty("command." + id + ".packages", pkg);
      append(p, "mappings", id);
      String base = "mapping." + id + ".";
      p.setProperty(base + "source", "ble");
      p.setProperty(base + "device", p.getProperty("address"));
      p.setProperty(base + "code", Integer.toString(104 + index));
      p.setProperty(base + "modifiers", "0");
      p.setProperty(base + "label", "F" + (13 + index));
      p.setProperty(base + "deviceName", "电脑模拟器");
      p.setProperty(base + "command", id);
    }
    save(p);
  }

  public static String findMapping(
      Properties p, String source, String device, int code, int modifiers) {
    for (String id : ids(p, "mappings")) {
      String base = "mapping." + id + ".";
      if (source.equals(p.getProperty(base + "source"))
          && device.equals(p.getProperty(base + "device"))
          && Integer.toString(code).equals(p.getProperty(base + "code"))
          && Integer.toString(modifiers).equals(p.getProperty(base + "modifiers", "0"))) return id;
    }
    return null;
  }
}
