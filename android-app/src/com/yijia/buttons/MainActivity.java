package com.yijia.buttons;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Device setup, reusable commands and any number of learned key bindings. */
public class MainActivity extends Activity {
  private final Handler handler = new Handler();
  private ConfigStore store;
  private Properties config;
  private TextView receiverStatus, deviceStatus, lastAction;
  private BluetoothProfile hidHost;
  private AlertDialog learningDialog;
  private String learningRequest;
  private boolean foreground, busy;
  private int activeTab = 0;
  private Button connectButton;
  private String learningCommand;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Ui.BACKGROUND);
    getWindow().setNavigationBarColor(Ui.CARD);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    store = new ConfigStore(getFilesDir());
    try {
      store.migrate();
    } catch (IOException e) {
      toast(e.getMessage());
    }
    render();
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null)
      adapter.getProfileProxy(
          this,
          new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
              hidHost = proxy;
            }

            public void onServiceDisconnected(int profile) {
              hidHost = null;
            }
          },
          4);
  }

  private void show(TextView view, String value) {
    if (view != null && value != null && !value.contentEquals(view.getText())) view.setText(value);
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private boolean save() {
    try {
      store.save(config);
      return true;
    } catch (IOException e) {
      toast(e.getMessage());
      return false;
    }
  }

  private void render() {
    config = store.read();
    receiverStatus = deviceStatus = lastAction = null;
    connectButton = null;
    LinearLayout root = Ui.column(this);
    root.setBackgroundColor(Ui.BACKGROUND);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    LinearLayout page = Ui.column(this);
    page.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 24));
    scroll.addView(page);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (activeTab == 0) renderHome(page);
    else if (activeTab == 1) renderActions(page);
    else renderPreferences(page);
    View divider = new View(this);
    divider.setBackgroundColor(Ui.BORDER);
    root.addView(divider, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    LinearLayout navigation = new LinearLayout(this);
    navigation.setBackgroundColor(Ui.CARD);
    String[] labels = {"快捷键", "动作", "设置"};
    String[] icons = {"keyboard", "apps", "settings"};
    for (int i = 0; i < labels.length; i++) {
      final int tab = i;
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER);
      int color = activeTab == i ? Ui.ACCENT : Ui.MUTED;
      item.addView(Ui.icon(this, icons[i], color, 23));
      TextView label = Ui.text(this, labels[i], 12, color);
      label.setGravity(Gravity.CENTER);
      label.setPadding(0, Ui.dp(this, 5), 0, 0);
      item.addView(label);
      item.setContentDescription(labels[i]);
      item.setOnClickListener(
          v -> {
            activeTab = tab;
            render();
          });
      navigation.addView(item, new LinearLayout.LayoutParams(0, Ui.dp(this, 68), 1));
    }
    root.addView(navigation);
    setContentView(root);
    updateStatus();
  }

  private void heading(LinearLayout page, String title, String subtitle) {
    TextView name = Ui.text(this, title, 27, Ui.TEXT);
    name.setTypeface(null, Typeface.BOLD);
    page.addView(name);
    TextView caption = Ui.note(page, subtitle);
    caption.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 22));
  }

  private void renderHome(LinearLayout page) {
    heading(page, "蓝牙按键", "常用的应用，按一下就打开。");
    LinearLayout connection = Ui.card(page);
    LinearLayout line = new LinearLayout(this);
    line.setGravity(Gravity.CENTER_VERTICAL);
    ImageView bluetooth = Ui.icon(this, "bluetooth", Ui.ACCENT, 48);
    bluetooth.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
    bluetooth.setBackground(Ui.background(this, Ui.TINT, 16));
    line.addView(bluetooth);
    LinearLayout details = Ui.column(this);
    details.setPadding(Ui.dp(this, 14), 0, 0, 0);
    deviceStatus = Ui.text(this, "连接你的键盘", 17, Ui.TEXT);
    deviceStatus.setTypeface(null, Typeface.BOLD);
    details.addView(deviceStatus);
    receiverStatus = Ui.note(details, "");
    receiverStatus.setTextSize(12);
    line.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
    connection.addView(line);
    connectButton = Ui.button(connection, "连接键盘", false, this::connectKeyboard);

    TextView section = Ui.text(this, "我的快捷键", 19, Ui.TEXT);
    section.setTypeface(null, Typeface.BOLD);
    section.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 14));
    page.addView(section);
    List<String> mappings = ConfigStore.ids(config, "mappings");
    if (mappings.isEmpty()) {
      LinearLayout empty = Ui.card(page);
      empty.setGravity(Gravity.CENTER_HORIZONTAL);
      ImageView icon = Ui.icon(this, "keyboard", Ui.ACCENT, 64);
      icon.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
      icon.setBackground(Ui.background(this, Ui.TINT, 22));
      empty.addView(icon);
      TextView title = Ui.text(this, "还没有快捷键", 18, Ui.TEXT);
      title.setGravity(Gravity.CENTER);
      title.setTypeface(null, Typeface.BOLD);
      title.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
      empty.addView(title);
      TextView hint = Ui.note(empty, "比如，按下数字 1，打开聊天应用。\n连接键盘后，来设置第一个吧。");
      hint.setGravity(Gravity.CENTER);
      Ui.button(empty, "添加第一个快捷键", true, this::addShortcut);
    } else {
      for (String id : mappings) {
        String base = "mapping." + id + ".";
        String command = config.getProperty(base + "command", "");
        LinearLayout card = Ui.card(page);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView key = Ui.text(this, config.getProperty(base + "label", "按键"), 16, Ui.ACCENT);
        key.setTypeface(null, Typeface.BOLD);
        key.setGravity(Gravity.CENTER);
        key.setBackground(Ui.background(this, Ui.TINT, 12));
        row.addView(key, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 50)));
        LinearLayout information = Ui.column(this);
        information.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        information.addView(Ui.text(this, ConfigStore.commandName(config, command), 16, Ui.TEXT));
        TextView apps =
            Ui.note(information, AppPicker.summary(this, ConfigStore.packages(config, command)));
        apps.setMaxLines(2);
        apps.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(information, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.icon(this, "edit", Ui.MUTED, 20));
        card.addView(row);
        card.setOnClickListener(v -> editMapping(id));
      }
      Ui.button(page, "添加快捷键", true, this::addShortcut);
    }
    LinearLayout tip = new LinearLayout(this);
    tip.setGravity(Gravity.CENTER_VERTICAL);
    tip.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, 0);
    tip.addView(Ui.icon(this, "help_outline", Ui.MUTED, 18));
    TextView help = Ui.text(this, "先选一个动作，再按一下键盘上的键。", 12, Ui.MUTED);
    help.setPadding(Ui.dp(this, 7), 0, 0, 0);
    tip.addView(help, new LinearLayout.LayoutParams(0, -2, 1));
    page.addView(tip);
    lastAction = Ui.note(page, "");
  }

  private void renderActions(LinearLayout page) {
    heading(page, "我的动作", "选好要打开的应用，再给动作起个名字。");
    List<String> commands = ConfigStore.ids(config, "commands");
    if (commands.isEmpty()) {
      LinearLayout empty = Ui.card(page);
      Ui.title(empty, "你想一键做什么？");
      Ui.note(empty, "打开聊天应用、地图或音乐，都可以保存成一个动作。之后再绑定到按键。");
    }
    for (String id : commands) {
      LinearLayout card = Ui.card(page);
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      List<String> packages = ConfigStore.packages(config, id);
      ImageView icon = Ui.icon(this, "apps", Ui.ACCENT, 42);
      if (!packages.isEmpty()) {
        try {
          icon.setImageDrawable(getPackageManager().getApplicationIcon(packages.get(0)));
          icon.setImageTintList(null);
        } catch (Exception ignored) {
        }
      }
      row.addView(icon);
      LinearLayout details = Ui.column(this);
      details.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
      details.addView(Ui.text(this, ConfigStore.commandName(config, id), 17, Ui.TEXT));
      TextView names = Ui.note(details, AppPicker.summary(this, packages));
      names.setMaxLines(2);
      names.setEllipsize(android.text.TextUtils.TruncateAt.END);
      row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(Ui.icon(this, "edit", Ui.MUTED, 20));
      card.addView(row);
      card.setOnClickListener(v -> editCommand(id));
    }
    Ui.button(page, "新建动作", true, () -> editCommand(null));
    Ui.note(page, "一个动作可以绑定到多个按键，随时可以修改。");
    lastAction = Ui.note(page, "");
  }

  private void renderPreferences(LinearLayout page) {
    heading(page, "设置", "按你的习惯，轻松调整。");
    LinearLayout hold = Ui.card(page);
    Ui.title(hold, "长按时做什么");
    Switch close = new Switch(this);
    close.setText("关闭快捷键关联的应用");
    close.setTextSize(15);
    close.setMinimumHeight(Ui.dp(this, 52));
    close.setTextColor(Ui.TEXT);
    close.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
    close.setChecked(config.getProperty("longAction", "close").equals("close"));
    hold.addView(close);
    Ui.note(hold, "松开按键后执行，不会删除应用或数据。");
    close.setOnCheckedChangeListener(
        (button, checked) -> {
          config = store.read();
          config.setProperty("longAction", checked ? "close" : "none");
          save();
        });
    String value = config.getProperty("holdMs", "1500");
    Ui.button(
        hold,
        "长按时长    " + (Double.parseDouble(value) / 1000) + " 秒",
        false,
        () -> {
          String[] labels = {"0.5 秒", "1 秒", "1.5 秒（推荐）", "2 秒", "3 秒"};
          String[] values = {"500", "1000", "1500", "2000", "3000"};
          new AlertDialog.Builder(this)
              .setTitle("按住多久算长按？")
              .setItems(
                  labels,
                  (dialog, index) -> {
                    config = store.read();
                    config.setProperty("holdMs", values[index]);
                    if (save()) render();
                  })
              .setNegativeButton("取消", null)
              .show();
        });
    LinearLayout help = Ui.card(page);
    Ui.title(help, "第一次使用？");
    Ui.note(help, "连接键盘、选择动作、按一下要用的键，就能设置好。");
    Ui.button(
        help,
        "看看怎么设置",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("三步，设置一个快捷键")
                .setMessage(
                    "1. 连接键盘\n"
                        + "在手机蓝牙里完成配对，回到这里选择键盘。\n\n"
                        + "2. 选择动作\n"
                        + "选一个已有动作，或选择应用新建一个。\n\n"
                        + "3. 按一下键\n"
                        + "按要绑定的实体键，保存后就能用了。")
                .setPositiveButton("明白了", null)
                .show());
    Ui.button(page, "更换键盘", false, this::chooseDevice);
    Ui.button(page, "暂停按键功能", false, () -> receive(false));
    Ui.button(
        page,
        "高级设置",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("高级设置")
                .setItems(
                    new String[] {"电脑模拟测试", "连接参数"},
                    (dialog, index) -> {
                      if (index == 0) {
                        config = store.read();
                        config.setProperty("mode", "ble");
                        config.setProperty("deviceName", "电脑模拟器");
                        if (save()) {
                          activeTab = 0;
                          render();
                          receive(true);
                        }
                      } else settings();
                    })
                .setNegativeButton("返回", null)
                .show());
    Ui.note(page, "你的按键设置只保存在本机。版本 1.2");
  }

  private void connectKeyboard() {
    Properties p = store.read();
    if (p.getProperty("mode", "keyboard").equals("ble") && !store.file("receiver.pid").exists())
      receive(true);
    else if (!p.getProperty("selectedAddress", "").isEmpty()
        && KeyCaptureService.instance == null
        && !p.getProperty("mode", "keyboard").equals("ble")) receive(true);
    else chooseDevice();
  }

  private void chooseDevice() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter == null) {
      new AlertDialog.Builder(this)
          .setTitle("连接你的键盘")
          .setMessage("在手机上打开蓝牙，完成键盘配对后，就能添加快捷键。\n\n电脑预览中可以先体验创建动作和选择应用。")
          .setPositiveButton("知道了", null)
          .show();
      return;
    }
    List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
    devices.sort((a, b) -> deviceName(a).compareToIgnoreCase(deviceName(b)));
    List<String> labels = new ArrayList<>();
    for (BluetoothDevice device : devices)
      labels.add(deviceName(device) + (connected(device) ? " · 已连接" : " · 已配对"));
    labels.add("添加新键盘，去蓝牙配对");
    new AlertDialog.Builder(this)
        .setTitle(devices.isEmpty() ? "先把键盘连接到手机" : "选择你的键盘")
        .setItems(
            labels.toArray(new String[0]),
            (dialog, index) -> {
              if (index == devices.size()) {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                return;
              }
              BluetoothDevice selected = devices.get(index);
              config = store.read();
              config.setProperty("mode", "keyboard");
              config.setProperty("selectedAddress", selected.getAddress());
              config.setProperty("deviceName", deviceName(selected));
              if (save()) {
                activeTab = 0;
                render();
                receive(true);
              }
            })
        .setNegativeButton("稍后再连", null)
        .show();
  }

  private String deviceName(BluetoothDevice device) {
    String name = device.getName();
    return name == null || name.isEmpty() ? "未命名蓝牙设备" : name;
  }

  private boolean connected(BluetoothDevice device) {
    try {
      return hidHost != null && hidHost.getConnectedDevices().contains(device);
    } catch (Exception ignored) {
      return false;
    }
  }

  private void updateStatus() {
    Properties p = store.read();
    if (deviceStatus != null) {
      boolean simulator = p.getProperty("mode", "keyboard").equals("ble");
      boolean configured = false, connected = false;
      if (simulator) {
        show(deviceStatus, "电脑模拟器");
        configured = store.file("receiver.pid").exists();
        String message =
            ConfigStore.readFile(store.file("state.properties")).getProperty("message", "");
        connected = configured && message.contains("已连接");
        if (!busy) show(receiverStatus, connected ? "已连接，可以设置快捷键" : "点击下方继续连接");
      } else {
        String address = p.getProperty("selectedAddress", "");
        String name = "连接你的键盘";
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null)
          for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (address.equals(device.getAddress())) {
              configured = true;
              name = deviceName(device);
              connected = connected(device);
            }
          }
        show(deviceStatus, name);
        if (!busy)
          show(
              receiverStatus,
              !configured
                  ? "先完成蓝牙配对，就能开始使用。"
                  : KeyCaptureService.instance == null
                      ? "再开启按键功能，就准备好了。"
                      : connected ? "已连接，可以设置快捷键" : "等待键盘连接，请确认键盘已打开。");
      }
      if (connectButton != null) {
        String label = !configured ? "连接键盘" : connected ? "更换键盘" : "继续连接";
        if (!label.contentEquals(connectButton.getText())) connectButton.setText(label);
      }
    }
    String message =
        ConfigStore.readFile(store.file("action.properties")).getProperty("message", "");
    if (activeTab == 0 && ConfigStore.ids(p, "mappings").isEmpty()) message = "";
    show(lastAction, message);
    if (lastAction != null) lastAction.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void receive(boolean start) {
    if (busy) return;
    boolean simulator = config.getProperty("mode", "keyboard").equals("ble");
    if (start && simulator && config.getProperty("address", "").isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle("先设置电脑连接参数")
          .setMessage("运行电脑上的蓝牙检测脚本，将显示的地址填入连接参数后再开始测试。")
          .setPositiveButton("设置连接参数", (dialog, which) -> settings())
          .setNegativeButton("取消", null)
          .show();
      return;
    }
    if (start && !simulator && config.getProperty("selectedAddress", "").isEmpty()) {
      toast("先连接键盘，就可以开始设置了");
      return;
    }
    busy = true;
    show(receiverStatus, start ? "正在开启接收…" : "正在停止…");
    new Thread(
            () -> {
              try {
                if (simulator) RootController.simulator(this, start);
                else if (start) RootController.enableKeys();
                else if (KeyCaptureService.instance != null)
                  KeyCaptureService.instance.disableSelf();
                handler.post(
                    () -> {
                      busy = false;
                      toast(start ? "按键功能已开启" : "按键功能已暂停");
                      updateStatus();
                    });
              } catch (Exception e) {
                handler.post(
                    () -> {
                      busy = false;
                      show(receiverStatus, e.getMessage());
                      new AlertDialog.Builder(this)
                          .setMessage(e.getMessage())
                          .setPositiveButton("知道了", null)
                          .setNeutralButton(
                              "去开启按键功能",
                              (d, w) ->
                                  startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                          .show();
                    });
              }
            })
        .start();
  }

  private void editCommand(String existing) {
    editCommand(existing, null);
  }

  private void editCommand(String existing, CommandChoice afterSave) {
    config = store.read();
    String id = existing == null ? ConfigStore.newId() : existing;
    List<String> packages = new ArrayList<>(ConfigStore.packages(config, id));
    LinearLayout content = Ui.column(this);
    content.setPadding(Ui.dp(this, 22), Ui.dp(this, 6), Ui.dp(this, 22), Ui.dp(this, 8));
    Ui.note(content, "先选择想打开的应用");
    TextView apps = Ui.text(this, AppPicker.summary(this, packages), 16, Ui.TEXT);
    content.addView(apps);
    EditText name = new EditText(this);
    name.setSingleLine();
    name.setMinHeight(Ui.dp(this, 48));
    name.setTextSize(16);
    name.setHint("例如：打开聊天、听音乐（可选）");
    name.setText(existing == null ? "" : ConfigStore.commandName(config, id));
    Ui.button(
        content,
        packages.isEmpty() ? "选择应用" : "更换应用",
        true,
        () ->
            AppPicker.show(
                this,
                packages,
                selected -> {
                  packages.clear();
                  packages.addAll(selected);
                  apps.setText(AppPicker.summary(this, packages));
                }));
    TextView nameLabel = Ui.note(content, "给这个动作起个名字");
    nameLabel.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));
    content.addView(name);
    Ui.note(content, "留空也可以，我们会根据应用自动命名。");
    ScrollView scroller = new ScrollView(this);
    scroller.addView(content);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "新建动作" : "编辑动作")
            .setView(scroller)
            .setNegativeButton("取消", null)
            .setPositiveButton(afterSave == null ? "保存动作" : "下一步", null)
            .create();
    dialog.setOnShowListener(
        ignored ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      if (packages.isEmpty()) {
                        toast("先选择一个要打开的应用吧");
                        return;
                      }
                      String title = name.getText().toString().trim();
                      if (title.isEmpty())
                        title =
                            packages.size() == 1
                                ? "打开 " + AppPicker.name(this, packages.get(0))
                                : "打开 " + packages.size() + " 个应用";
                      config = store.read();
                      config.setProperty("command." + id + ".name", title);
                      config.setProperty("command." + id + ".packages", String.join(",", packages));
                      ConfigStore.append(config, "commands", id);
                      if (save()) {
                        dialog.dismiss();
                        render();
                        if (afterSave != null) afterSave.selected(id);
                        else toast("动作已保存");
                      }
                    }));
    if (existing != null) {
      Ui.button(content, "试一试这个动作", false, () -> ActionRunner.command(this, store, id));
      Ui.button(
          content,
          "删除动作",
          false,
          () -> {
            config = store.read();
            for (String mapping : ConfigStore.ids(config, "mappings"))
              if (id.equals(config.getProperty("mapping." + mapping + ".command"))) {
                toast("这个动作还在被快捷键使用，请先解绑快捷键");
                return;
              }
            new AlertDialog.Builder(this)
                .setTitle("删除这个动作？")
                .setMessage("只会删除动作设置，不会删除应用或数据。")
                .setNegativeButton("保留", null)
                .setPositiveButton(
                    "删除",
                    (confirm, which) -> {
                      config = store.read();
                      ConfigStore.remove(config, "commands", "command.", id);
                      if (save()) {
                        dialog.dismiss();
                        render();
                      }
                    })
                .show();
          });
    }
    dialog.show();
    dialog
        .getWindow()
        .setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  private void addShortcut() {
    config = store.read();
    String source = config.getProperty("mode", "keyboard");
    boolean ready =
        source.equals("ble")
            ? store.file("receiver.pid").exists()
            : !config.getProperty("selectedAddress", "").isEmpty()
                && KeyCaptureService.instance != null;
    if (!ready) {
      new AlertDialog.Builder(this)
          .setTitle("先连接键盘，再绑定按键")
          .setMessage("你也可以先选好应用，保存成动作。连接键盘后，再回来绑定按键。")
          .setPositiveButton("连接键盘", (dialog, which) -> connectKeyboard())
          .setNeutralButton(
              "先添加动作",
              (dialog, which) -> {
                activeTab = 1;
                render();
                editCommand(null);
              })
          .setNegativeButton("稍后", null)
          .show();
      return;
    }
    List<String> ids = ConfigStore.ids(config, "commands");
    List<String> labels = new ArrayList<>();
    for (String id : ids)
      labels.add(
          ConfigStore.commandName(config, id)
              + "\n"
              + AppPicker.summary(this, ConfigStore.packages(config, id)));
    labels.add("新建一个动作");
    new AlertDialog.Builder(this)
        .setTitle("这个快捷键要做什么？")
        .setItems(
            labels.toArray(new String[0]),
            (dialog, index) -> {
              if (index == ids.size()) editCommand(null, this::learnForCommand);
              else learnForCommand(ids.get(index));
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void learnForCommand(String command) {
    config = store.read();
    learningCommand = command;
    String source = config.getProperty("mode", "keyboard").equals("ble") ? "ble" : "keyboard";
    learningRequest = ConfigStore.newId();
    Properties request = new Properties();
    request.setProperty("request", learningRequest);
    request.setProperty("source", source);
    request.setProperty("deadline", Long.toString(System.currentTimeMillis() + 30000));
    try {
      store.file("learn-result.properties").delete();
      ConfigStore.writeFile(store.file("learn.properties"), request);
    } catch (IOException e) {
      toast("暂时无法录入，请再试一次");
      return;
    }
    learningDialog =
        new AlertDialog.Builder(this)
            .setTitle("最后一步，按一下键盘上的键")
            .setMessage(
                "这个键将用于「"
                    + ConfigStore.commandName(config, command)
                    + "」。\n\n可以按数字 1、2、3，也可以按字母或组合键。我们会自动识别。")
            .setNegativeButton("返回", (dialog, which) -> cancelLearning())
            .create();
    learningDialog.setOnCancelListener(dialog -> cancelLearning());
    learningDialog.show();
  }

  private void cancelLearning() {
    learningRequest = null;
    learningCommand = null;
    if (store == null) return;
    store.file("learn.properties").delete();
    store.file("learn-result.properties").delete();
    if (learningDialog != null) learningDialog.dismiss();
  }

  private void pollLearning() {
    if (learningRequest == null) return;
    Properties result = ConfigStore.readFile(store.file("learn-result.properties"));
    if (learningRequest.equals(result.getProperty("request"))) {
      String command = learningCommand;
      cancelLearning();
      new AlertDialog.Builder(this)
          .setTitle("用这个键，好吗？")
          .setMessage(
              "按下「"
                  + result.getProperty("label")
                  + "」，就能执行「"
                  + ConfigStore.commandName(store.read(), command)
                  + "」。\n\n来自："
                  + result.getProperty("deviceName"))
          .setPositiveButton("保存快捷键", (dialog, which) -> saveMapping(result, command))
          .setNegativeButton("重新按一次", (dialog, which) -> learnForCommand(command))
          .show();
    } else {
      Properties request = ConfigStore.readFile(store.file("learn.properties"));
      if (System.currentTimeMillis() > Long.parseLong(request.getProperty("deadline", "0"))) {
        cancelLearning();
        new AlertDialog.Builder(this)
            .setTitle("还没有收到按键")
            .setMessage("确认键盘已经打开并连接到手机，再试一次吧。")
            .setPositiveButton("知道了", null)
            .show();
      }
    }
  }

  private void saveMapping(Properties result, String command) {
    config = store.read();
    String id =
        ConfigStore.findMapping(
            config,
            result.getProperty("source"),
            result.getProperty("device"),
            Integer.parseInt(result.getProperty("code")),
            Integer.parseInt(result.getProperty("modifiers")));
    if (id == null) id = ConfigStore.newId();
    String base = "mapping." + id + ".";
    for (String key : new String[] {"source", "device", "deviceName", "code", "modifiers", "label"})
      config.setProperty(base + key, result.getProperty(key));
    config.setProperty(base + "command", command);
    ConfigStore.append(config, "mappings", id);
    if (save()) {
      activeTab = 0;
      render();
      toast("设置好了，现在可以按一下试试");
    }
  }

  private interface CommandChoice {
    void selected(String command);
  }

  private void chooseCommand(String title, CommandChoice callback) {
    Properties p = store.read();
    List<String> ids = ConfigStore.ids(p, "commands");
    List<String> labels = new ArrayList<>();
    for (String id : ids)
      labels.add(
          ConfigStore.commandName(p, id)
              + "\n"
              + AppPicker.summary(this, ConfigStore.packages(p, id)));
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(labels.toArray(new String[0]), (d, index) -> callback.selected(ids.get(index)))
        .setNegativeButton("取消", null)
        .show();
  }

  private void editMapping(String id) {
    new AlertDialog.Builder(this)
        .setTitle("修改快捷键")
        .setItems(
            new String[] {"换一个动作", "删除快捷键"},
            (d, which) -> {
              if (which == 0)
                chooseCommand(
                    "选择一个动作",
                    command -> {
                      config = store.read();
                      config.setProperty("mapping." + id + ".command", command);
                      if (save()) render();
                    });
              else {
                new AlertDialog.Builder(this)
                    .setTitle("删除这个快捷键？")
                    .setMessage("只删除按键关联，动作和应用都会保留。")
                    .setNegativeButton("保留", null)
                    .setPositiveButton(
                        "删除",
                        (confirm, index) -> {
                          config = store.read();
                          ConfigStore.remove(config, "mappings", "mapping.", id);
                          if (save()) render();
                        })
                    .show();
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void settings() {
    config = store.read();
    LinearLayout content = Ui.column(this);
    content.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
    Switch close = new Switch(this);
    close.setText("长按关闭映射指令中的应用");
    close.setChecked(config.getProperty("longAction", "close").equals("close"));
    content.addView(close);
    Ui.note(content, "长按松开时执行，不清空其他最近任务。");
    EditText delay = new EditText(this);
    delay.setInputType(2);
    delay.setText(config.getProperty("holdMs", "1500"));
    Ui.note(content, "长按时长（毫秒，500～10000）");
    content.addView(delay);
    EditText address = new EditText(this);
    address.setSingleLine();
    address.setText(config.getProperty("address", ConfigStore.SIMULATOR_ADDRESS));
    Ui.note(content, "电脑模拟器地址（实体键盘无需填写）");
    content.addView(address);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("连接参数（电脑测试）")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存设置", null)
            .create();
    dialog.setOnShowListener(
        ignored ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        int value = Integer.parseInt(delay.getText().toString());
                        if (value < 500 || value > 10000) throw new Exception();
                        String mac = address.getText().toString().trim().toUpperCase(Locale.ROOT);
                        if (!mac.isEmpty() && !mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))
                          throw new Exception();
                        config = store.read();
                        config.setProperty("holdMs", Integer.toString(value));
                        config.setProperty("longAction", close.isChecked() ? "close" : "none");
                        config.setProperty("address", mac);
                        if (save()) {
                          dialog.dismiss();
                          toast("设置已保存");
                        }
                      } catch (Exception e) {
                        toast("请检查长按时长和模拟器地址");
                      }
                    }));
    dialog.show();
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (foreground) {
            updateStatus();
            pollLearning();
            handler.postDelayed(this, 600);
          }
        }
      };

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    foreground = false;
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    cancelLearning();
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    if (adapter != null && hidHost != null) adapter.closeProfileProxy(4, hidHost);
    super.onDestroy();
  }
}
