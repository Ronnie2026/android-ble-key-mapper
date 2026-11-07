import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Root-only, temporary UHID keyboard used for device-side input integration checks. */
public class KeyboardHarness {
  static final byte[] DESCRIPTOR =
      new byte[] {
        5,
        1,
        9,
        6,
        (byte) 0xa1,
        1,
        5,
        7,
        0x19,
        (byte) 0xe0,
        0x29,
        (byte) 0xe7,
        0x15,
        0,
        0x25,
        1,
        0x75,
        1,
        (byte) 0x95,
        8,
        (byte) 0x81,
        2,
        (byte) 0x95,
        1,
        0x75,
        8,
        (byte) 0x81,
        1,
        (byte) 0x95,
        6,
        0x75,
        8,
        0x15,
        0,
        0x25,
        0x73,
        5,
        7,
        0x19,
        0,
        0x29,
        0x73,
        (byte) 0x81,
        0,
        (byte) 0xc0
      };

  static void string(ByteBuffer event, int offset, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    event.position(offset);
    event.put(bytes);
  }

  static void report(FileOutputStream output, int usage) throws Exception {
    ByteBuffer event = ByteBuffer.allocate(4376).order(ByteOrder.LITTLE_ENDIAN);
    event.putInt(12);
    event.putShort((short) 8);
    event.position(8);
    event.put((byte) usage);
    output.write(event.array());
    output.flush();
  }

  public static void main(String[] args) throws Exception {
    try (RandomAccessFile device = new RandomAccessFile("/dev/uhid", "rw")) {
      FileOutputStream output = new FileOutputStream(device.getFD());
      Thread reader =
          new Thread(
              () -> {
                try {
                  FileInputStream input = new FileInputStream(device.getFD());
                  byte[] event = new byte[4376];
                  while (input.read(event) > 0) {}
                } catch (Exception ignored) {
                }
              });
      reader.setDaemon(true);
      reader.start();
      ByteBuffer create = ByteBuffer.allocate(4376).order(ByteOrder.LITTLE_ENDIAN);
      create.putInt(11);
      string(create, 4, "Yijia integration test keyboard");
      string(create, 132, "yijia-test/input0");
      string(create, 196, "02:11:22:33:44:55");
      create.position(260);
      create.putShort((short) DESCRIPTOR.length);
      create.putShort((short) 5);
      create.putInt(0x1234);
      create.putInt(0x5678);
      create.putInt(1);
      create.putInt(0);
      create.put(DESCRIPTOR);
      output.write(create.array());
      output.flush();
      System.out.println("Created temporary external keyboard");
      System.out.flush();
      File command = new File("/data/local/tmp/yijia-keyboard-command");
      long deadline = System.currentTimeMillis() + 600000;
      while (System.currentTimeMillis() < deadline) {
        if (command.exists()) {
          String value =
              new String(java.nio.file.Files.readAllBytes(command.toPath()), StandardCharsets.UTF_8)
                  .trim();
          command.delete();
          if (value.equals("stop")) break;
          String[] parts = value.split(",");
          int usage = Integer.parseInt(parts[0]);
          int hold = Integer.parseInt(parts[1]);
          report(output, usage);
          Thread.sleep(hold);
          report(output, 0);
          System.out.println("Sent usage " + usage + " hold " + hold);
          System.out.flush();
        }
        Thread.sleep(80);
      }
      ByteBuffer destroy = ByteBuffer.allocate(4376).order(ByteOrder.LITTLE_ENDIAN);
      destroy.putInt(1);
      output.write(destroy.array());
    }
  }
}
