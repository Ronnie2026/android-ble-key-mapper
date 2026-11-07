# 开发与架构

## 构建环境

当前脚本支持 Windows x64、Windows PowerShell 5.1 / PowerShell 7、JDK 17 和 Android SDK。本项目使用原生 Java 与 Android SDK 命令行工具，暂未配置 Gradle 工程。

安装 SDK Command-line Tools 后，准备平台和构建工具：

```powershell
sdkmanager "platforms;android-30" "build-tools;35.0.0" "platform-tools"
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\path\to\Android\Sdk'
.\build-app.ps1
```

`resolve-android-toolchain.ps1` 按环境变量查找 SDK，也兼容最初开发环境中 `.tools` 下的缓存。正常克隆仓库不需要获得原开发机器的缓存。

构建包括：Java 编译 → D8 转换 → AAPT 打包 → zipalign 对齐 → APK 签名及校验。输出为 `dist/Android-BLE-Key-Mapper-v0.1.apk`。

首次构建会在 `.keys/yijia.jks` 生成本地开发签名。脚本中的 `android` 是开发签名的默认密码，不是生产凭据。发布和覆盖升级应持续使用自己保管的签名密钥；不要将密钥或密钥备份提交到仓库。

## 模块

| 文件 | 职责 |
| --- | --- |
| `MainActivity.java` | 快捷键、动作、设置和新手向导 |
| `Ui.java` | 颜色、卡片、按钮、图标和字号 |
| `AppPicker.java` | 应用图标、名称、搜索、多选及打开顺序 |
| `ConfigStore.java` | 配置读取、原子保存和旧格式迁移 |
| `KeyLearning.java` | 用户主动发起、30 秒限时的按键录入 |
| `KeyCaptureService.java` | 已配置外接键盘事件的过滤和短／长按区分 |
| `ActionRunner.java` | 应用启动、前台试运行和长按关闭应用 |
| `RootController.java` | 自身按键服务及可选接收进程的启停 |
| `ReceiverMain.java` | Windows BLE 模拟器的可选接收核心 |

配置保存在 APP 私有目录 `files/config.properties`。当前格式为 `schema=2`，动作和映射有独立 ID，映射通过 ID 引用动作。应用包名只用于内部存储和启动，不要求用户手工填写。

标准键盘映射通过设备描述符、Android 键码和修饰键区分。BLE 模拟器映射通过已配置的电脑地址、HID 用法码和修饰键区分。未映射的普通键盘输入继续传给系统。

## 电脑预览

准备名为 `Yijia_UI` 的 Android 11 x86_64 AVD，以及 Android Emulator、Platform Tools：

```powershell
avdmanager create avd -n Yijia_UI -k "system-images;android-30;default;x86_64" -d pixel_3a
.\preview-app.ps1 -Rebuild
```

先通过 SDK Manager 安装对应系统镜像。模拟器硬件加速按电脑环境配置。

预览脚本固定使用 `emulator-5554`，构建后只安装到这个模拟器。模拟器的数据与手机分开。前台「试一试这个动作」可使用公开的 Android 启动接口；root 关闭应用和真实蓝牙设备兼容性应在手机验证。

## 可选的 Windows BLE 模拟器

在 Windows PowerShell 5.1 中运行：

```powershell
.\bluetooth-probe.ps1
.\build-windows-simulator.ps1
.\bluetooth-buttons.ps1
```

需要 Windows 蓝牙适配器及驱动支持 BLE Peripheral。检测脚本会显示电脑蓝牙地址。在 Android APP 的「设置 → 高级设置 → 连接参数」填写此地址，再开启「电脑模拟测试」。默认地址为空，仓库不绑定某台开发电脑。

A/B/C 只是模拟器的测试按钮，分别发送 F13/F14/F15。它们不限制 Android APP 可以学习的按键数量和名称。模拟器发布标准 HID 报告与一个识别用的专用 GATT 服务；当前 Android 端使用自己的接收核心，不能据此宣称实现了 Windows 到 Android 的免接收程序原生键盘连接。

## 验证与测试工具

- Android 11 实机：安装、升级、配置保存、应用启动及关闭已验证。
- 临时 UHID 外接键盘：数字键 1、2 的学习和后台触发，以及多应用顺序启动曾在 root 手机测试。
- Android 11 模拟器：浅色界面、三个导航页、应用搜索和多选、自动命名、删除确认、前台试运行已检查。
- `android-app/test/KeyboardHarness.java` 是开发用临时虚拟键盘；`CleanupHarness.java` 只清理明确标记的测试记录。它们不打入用户 APK，运行前应阅读源码，并仅用于自己的测试设备。

这些是人工集成验证记录，不是自动化测试覆盖率承诺。第三方实体键盘型号、Android 12 及以上、锁屏、长期后台和重启恢复仍需单独测试。

## Release 发布

公开版本从 0.1 开始，后续依次为 0.2、0.3、0.4。发布前先完成模拟器和实机验证；更新 Manifest 的 versionName 与递增的 versionCode，补充 docs/releases/v版本.md，然后推送对应版本标签。GitHub Actions 会构建、验证签名、生成 SHA-256 校验文件并创建 Release；构建失败不会发布。

首次发布前，仓库需配置 ANDROID_KEYSTORE_BASE64 Secret，内容为现有 .keys/yijia.jks 的 Base64。不要创建新签名替代原签名，否则已有安装无法直接覆盖升级。密钥不得提交到 Git。工作流也支持对已存在的版本标签手动运行；不会自动覆盖已有 Release。
