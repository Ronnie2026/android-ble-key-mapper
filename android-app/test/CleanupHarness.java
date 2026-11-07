import java.io.*;
import java.util.*;

/** Removes only mappings explicitly tagged as the temporary integration keyboard. */
public class CleanupHarness {
  public static void main(String[] args) throws Exception {
    File file = new File(args[0]);
    Properties p = new Properties();
    try (FileInputStream input = new FileInputStream(file)) {
      p.load(input);
    }
    Set<String> testCommands = new HashSet<>();
    List<String> mappings =
        new ArrayList<>(Arrays.asList(p.getProperty("mappings", "").split(",")));
    int removed = 0;
    for (String id : new ArrayList<>(mappings)) {
      String prefix = "mapping." + id + ".";
      if (!"Yijia integration test keyboard".equals(p.getProperty(prefix + "deviceName"))) continue;
      testCommands.add(p.getProperty(prefix + "command", ""));
      for (String key : new ArrayList<>(p.stringPropertyNames()))
        if (key.startsWith(prefix)) p.remove(key);
      mappings.remove(id);
      removed++;
    }
    p.setProperty("mappings", String.join(",", mappings));
    List<String> commands =
        new ArrayList<>(Arrays.asList(p.getProperty("commands", "").split(",")));
    for (String id : testCommands) {
      String prefix = "command." + id + ".";
      if (!"指令 4".equals(p.getProperty(prefix + "name"))
          || !"com.openai.chatgpt,mark.via".equals(p.getProperty(prefix + "packages"))) continue;
      for (String key : new ArrayList<>(p.stringPropertyNames()))
        if (key.startsWith(prefix)) p.remove(key);
      commands.remove(id);
    }
    p.setProperty("commands", String.join(",", commands));
    p.setProperty("mode", "ble");
    p.setProperty("deviceName", "电脑模拟器");
    try (FileOutputStream output = new FileOutputStream(file)) {
      p.store(output, "Yijia Buttons");
    }
    System.out.println("Removed temporary test mappings: " + removed);
  }
}
