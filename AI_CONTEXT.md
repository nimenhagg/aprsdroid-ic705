# APRSdroid IC-705 Mod：工程与 AI 交接上下文

> 本文是维护者和 AI 编程助手的事实基线。修改代码前先核对本文与实际实现；若两者冲突，以代码、测试和当前构建配置为准，并在同一次变更中修正文档。

## 1. 当前发布基线

| 项目 | 当前值 |
| --- | --- |
| 版本 | `1.7.1-ic705` |
| Android versionCode | `2026082471` |
| 上游基线 | APRSdroid `v1.7.0` |
| Android | `minSdk 25`，`compileSdk 37`，`targetSdk 37` |
| 原生 ABI | 仅 `arm64-v8a` |
| 构建链 | Gradle `9.5.0`，AGP `9.3.2` |
| 语言/编译器 | AGP 9 内置 Kotlin `2.2.10`，Compose Compiler `2.2.10`，Java `17` |
| UI | Compose BOM `2026.08.00` + Material 3；部分旧页面仍为 View/XML |
| 应用 ID | `me.nimenhagg.aprsdroidic705mod` |
| 地图 | MapLibre Native `13.5.1` 多后端 + Google Maps SDK |
| 发布标签 | `v1.7.1-ic705` |

Java 17 是 AGP 9.3 的默认与最低 JDK 基线。没有明确需求和完整兼容性验证时，不要仅为提高版本号切换 Java 21。

## 2. 项目目标与边界

本 Fork 在 APRSdroid 上增加 Icom IC-705 Wi-Fi 直连 APRS 收发，同时保留 APRS-IS、AFSK、KISS、TNC2、Kenwood、蓝牙、USB 和 TCP TNC 等原有路径。主要目标：

- 手机与 IC-705 通过 WLAN UDP 会话通信，不依赖音频线或外接 TNC。
- IC-705 UDP Socket 绑定到选定 Wi-Fi `Network`，避免把 APRS-IS 等互联网连接整体绑到无互联网的电台热点。
- 使用 AX.25 + AFSK1200 + 12 kHz 单声道 PCM 进行收发，以 CI-V 控制 PTT。
- 保留 Material 3 / Material You 界面、消息、日志、台站、地图和定位功能。

### 明确的非目标

- IC-705 LAN 和用户指定的 APRS 服务器可能使用明文 UDP/TCP。`android:usesCleartextTraffic="true"` 是当前兼容性决定；1.7.1 未改变传输协议或加入 TLS。HTTP POST 使用 `HttpURLConnection`，但裸主机仍兼容明文 `http://:8080/`。除非维护者明确要求并能提供服务器/电台测试条件，不要擅自强制 TLS、删除明文能力或改变证书逻辑。
- 不保证在模拟器中完成无线电硬件验证。协议单测、构建和 Lint 通过不能替代真机、真电台、低功率/假负载测试。

## 3. 目录与构建特点

该仓库保留 APRSdroid 的非标准单模块布局：

| 路径 | 内容 |
| --- | --- |
| `src/` | Kotlin、Java 生产源码；AGP 9 中同时声明为 Java/Kotlin source set |
| `src/ic705/` | IC-705 codec、transport、session、backend、diagnostic |
| `src/audio/` | AFSK1200 与 PCM 抽象/实现 |
| `res/` | Android XML、字符串、主题和图标 |
| `test/java/` | JVM 单元测试 |
| `androidTest/java/` | Android 仪器测试 |
| `.github/workflows/` | 测试、Lint、Release APK 与 GitHub Release 流水线 |

AGP 9 使用 built-in Kotlin：不要重新应用 `org.jetbrains.kotlin.android`。Compose 编译器插件版本应与 Kotlin 版本一致。

### 地图架构

- `MapAct` 使用 MapLibre Native 13.5.1 渲染高德、OpenStreetMap 与自定义在线栅格瓦片；`GoogleMapAct` 只处理 Google 普通地图和卫星地图。图源切换必须进入对应 Activity，不要重新在 Google SDK 中实现 MapLibre 图源。
- 依赖为 `org.maplibre.gl:android-sdk-vulkan-opengl`。`MapLibre.getInstance(application)` 使用库的默认渲染引擎选择：符合版本和设备能力条件时选择 Vulkan，否则使用 OpenGL。不要强制 Vulkan，以免破坏回退。
- Gradle 的 `abiFilters` 只发布 `arm64-v8a`。Release 验证必须检查 APK 中没有 `armeabi-v7a`、`x86` 或 `x86_64` 原生库，并同时保留 MapLibre 的 Vulkan 与 OpenGL ARM64 库。
- 不依赖可选的 MapLibre Offline 插件，代码中不得引用 `org.maplibre.android.offline`，也没有离线区域下载/管理 UI。MapLibre 主 AAR 内部自带的 API 类不等于项目启用了 Offline 功能。
- 地图页面继续复用 `mapview.xml` 的现有 Material 控件；APRS 台站通过 GeoJSON `SymbolLayer` 呈现，缩放 10 级起显示呼号组合图标。

## 4. IC-705 数据流

主要调用链：

1. `AprsService` 根据 `PrefsWrapper` 选择 `AprsBackend`。
2. `Ic705WifiBackend` 适配 APRSdroid Service/Prefs 到 `Ic705WifiBackendController`。
3. Controller 建立 `Ic705RxSession`，并把接收 PCM 送入 `FeedableAfskDecoder`。
4. 接收链：Wi-Fi UDP → `Ic705AudioPacketCodec` → PCM16LE → AFSK1200 → AX.25/APRS。
5. 发射链：APRS → AX.25 → `Afsk1200PcmGenerator` → `Ic705TxAudioPacketizer` → CI-V PTT ON → Audio UDP → CI-V PTT OFF。

默认控制端口是 UDP `50001`，CI-V 和 Audio 通常为其后两个端口。协商采样率与当前编解码路径是 12 kHz。协议捕获中关于 48 kHz RS-BA1 的注释可能描述历史/参考抓包，不能据此把当前默认 DSP 链改为 48 kHz。

## 5. 射频安全不变量

修改发射代码时必须保持以下语义：

- PTT ON 只能在会话与发送条件满足后发生。
- 音频发送结束、失败、取消、关闭或超时都必须尝试 PTT OFF。
- `Ic705PttStateMachine` 的绝对超时看门狗默认 5 秒；PTT OFF 发送失败时看门狗继续保留，以便重试安全释放。
- 不得在日志、异常、`toString()` 或诊断 UI 中输出 IC-705 用户名/密码明文。
- 修改 PTT 状态机、音频包节奏、序号或会话关闭顺序时必须增加/更新测试。

软件看门狗不是硬件互锁。发布说明和用户文档应继续建议低功率或假负载首测。

## 6. Android 17 权限与服务启动

API 37 的 `ACCESS_LOCAL_NETWORK` 是运行时权限。当前策略：

- IC-705 Wi-Fi (`ic705`) 与局域网 TCP TNC (`tcpip`) 在 Android 17+ 请求本地网络权限。
- 公网 APRS-IS TCP/HTTP/UDP 不请求本地网络权限。
- `BaseRecyclerActivity.startAprsServiceWithPermissions()` 在启动前台服务前统一组合后端权限与位置来源权限。
- Android 13+ 请求通知权限；AFSK 后端请求麦克风；蓝牙后端请求对应蓝牙权限；定位权限由位置来源决定。
- 停止服务不应被新增权限拦截。

新增服务入口时必须复用统一启动函数，不能绕过权限链直接启动 APRS 服务。USB attach 的恢复入口属于独立的系统事件路径，修改前需单独评估后台启动限制。

## 7. 代码约定

- 新业务代码优先 Kotlin；尊重已有 Java/JNI 边界。
- 后台 I/O 使用现有 Executor/调度器，不在主线程做 Socket、数据库或 DSP 重活。
- UI 新页面优先 Compose Material 3；维护旧 View 页面时不要引入第二套不一致主题。
- 使用 AndroidX API，避免重新引入 `android.preference.*` 或旧 Support Test 包。
- HTTP 后端使用 `HttpURLConnection`；不要重新加入 `org.apache.http.legacy` 或 Apache `DefaultHttpClient`。
- 文档提供器 URI 必须通过 `ContentResolver` 流读取；不要查询 `_data`、把 `content://` 当作 `File`，或自行拼接 `/storage` 路径。
- 权限与页面结果使用 AndroidX Activity Result launcher；待处理动作必须跨 Activity 重建保存，网络/Socket 关闭必须幂等。
- 不记录口令、密钥、完整鉴权包或未经脱敏的用户数据。
- 文档中的端口、采样率、版本与权限必须从实现核对，不凭历史 README 推断。

## 8. 验证命令

本地发布前至少运行：

```bash
./gradlew verifyReleaseVersion testDebugUnitTest lintDebug assembleRelease --no-daemon --stacktrace
```

Windows PowerShell 使用 `./gradlew.bat`。API 37 SDK 未安装但许可证已接受时，可以临时传入：

```powershell
./gradlew.bat testDebugUnitTest '-Pandroid.builder.sdkDownload=true' --no-daemon
```

对于发射链变更，还应执行以下人工验证：

1. IC-705 诊断页可连接、接收 PCM 并观察解码统计。
2. 低功率/假负载下单次发射，确认 PTT ON、音频、PTT OFF 顺序。
3. 发射过程中断开 Wi-Fi，确认看门狗/关闭路径会尝试释放 PTT。
4. 电台 Wi-Fi 在线时确认蜂窝 APRS-IS 路由未被整体绑到 Wi-Fi。
5. Android 17 真机首次授权、拒绝后重试及从设置页恢复权限。

## 9. 版本与发布流程

发布必须保持以下内容一致：

1. `build.gradle` 的 `mod_version` 与递增的 `mod_version_code`。
2. `CHANGELOG.md` 顶部新增对应版本。
3. `README.md` 和本文中的当前版本/工具链事实。
4. 标签严格使用 `v<major.minor.patch>-ic705`。
5. 先运行完整验证，再提交并创建标签；不要移动已经发布的标签。

CI 的 `verifyReleaseVersion` 会在 Tag 构建中检查标签是否等于 `v${mod_version}-ic705`。工作流随后测试、Lint、构建、签名（若 secrets 可用）并创建 GitHub Release。

## 10. 1.7.1 交接状态

- 构建链已迁移到 Gradle 9.5.0 / AGP 9.3.2 / built-in Kotlin 2.2.10 / API 37。
- Java 源与字节码目标保持 17。
- Android 17 本地网络权限已按 IC-705 与 LAN TNC 后端接入统一服务启动链。
- README 已扩充为中英双语用户/开发指南，并记录 ARM64 与地图渲染边界。
- Compose 迁移后无引用的 RecyclerView 外壳、旧 XML 布局和未参与构建的 `PacketDroid` 子模块已移除；根目录中实际使用的 AFSK/AX.25 路径保留。
- 地图、铃声和运行时权限均使用 Activity Result launcher；外部存储权限已删除，导出位于应用专属目录并通过 `FileProvider` 分享。
- HTTP POST 后端已迁移到 `HttpURLConnection`，Gradle 与 Manifest 不再依赖 `org.apache.http.legacy`。
- 配置导入使用 Activity Result `OpenDocument` 和 `ContentResolver` 流；不可达的 map/theme 文件选择器及 86 个无引用资源标识已从全部语言文件同步移除，默认资源、58 个 locale 目录与活动翻译保持完整。
- Android Lint 从 1001 项降为 `No issues found`，未引入 baseline；TLS 兼容项仅做窄范围说明性抑制，行为未改。
- Mapsforge 0.3.0、专用瓦片下载器和遗留 ProGuard 规则已移除；高德、OSM、自定义图源迁移到 MapLibre，Google 图源仍使用 Google SDK。
- MapLibre 使用 ARM64 Vulkan/OpenGL 多后端并保留自动回退；未加入可选 Offline 插件或离线地图功能。
- TLS 未修改，明文兼容行为保持不变。
- 1.7.1 已完成 134 项 JVM 测试（133 通过、1 跳过）、Android Lint（0 error、0 warning）、Release APK 与 instrumentation APK 编译；APK 元数据为 API 25/37，原生库清单仅含 `arm64-v8a`，并包含 Vulkan/OpenGL 两套 MapLibre 后端。仍需在具备硬件时补做 MapLibre Vulkan/OpenGL 真机地图与 IC-705 收发验证。
