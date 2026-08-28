# APRSdroid Mod：工程与 AI 交接上下文

> 本文是维护者和 AI 编程助手的当前事实基线。若本文与代码、测试、Gradle 配置或 CI 行为冲突，以代码和可复现验证为准，并在同一次变更中修正文档。

## 1. 版本基线与 main 状态

### 最新稳定版

| 项目 | 当前值 |
| --- | --- |
| 最新 GitHub Release | `Mod-v2.0.3` |
| `build.gradle` 默认版本 | `2.0.3` |
| Android versionCode | `2026082901` |
| 上游历史基线 | APRSdroid `v1.7.0` |
| Android | `minSdk 27`，`compileSdk 37`，`targetSdk 37` |
| 构建链 | Gradle `9.5.0`，AGP `9.3.2` |
| Kotlin / Compose Compiler | AGP 9 built-in Kotlin `2.3.21` / Compose Compiler `2.3.21` |
| Java | `17` |
| 核心库 | Material `1.14.0`，OkHttp `5.3.0`，Core-KTX `1.19.0`，Activity Compose `1.13.0`，Lifecycle runtime-compose `2.11.0`，Navigation Compose `2.10.0` |
| 地图 | MapLibre Native `13.5.1` + Google Maps SDK |
| 应用 ID | `me.nimenhagg.aprsdroidic705mod` |
| UI | Jetpack Compose + Material 3；生产页面无 `res/layout` XML |

### 当前 main 状态

`Mod-v2.0.3` 是当前发布基线。2.0.0 将之前分散在多个一级 Activity 的主界面收敛为一个顶层导航宿主；2.0.1 调整信息密度与主导航表现；2.0.2 修正通知设置动效和首帧/返回性能；2.0.3 将四个底栏目的地的导航语义彻底对称并统一聊天层级动效：

- `HubActivity` 承载 Navigation Compose `NavHost`，四个一级目的地固定为 `stations` / `map` / `messages` / `packets`，手机界面使用 Material 3 Bottom Navigation。
- `navigateTopLevel()` 使用 `popUpTo(graph.id) { saveState = true }` + `launchSingleTop = true` + `restoreState = true`，四个一级目的地都走相同的 navigate/restore 路径。不要改回 `graph.findStartDestination().id`；后者会让作为 start destination 的 `stations` 再次变成特殊 pop-only 路径。
- 一级 destination 切换不做整页 alpha 混合；当前仅使用低幅横向 translation：目标页约 140 ms、`width / 24` 进入，来源页约 120 ms、`width / 48` 退出，以减少台站 LazyColumn 和 APRS symbol Canvas 在转场期间的合成开销。
- `chat/{call}` 是二级 route，前进/返回与项目既有 `m3_activity_*` motion 对齐：前景进入 280 ms、约 25% 位移；底层退出/返回 240 ms、约 12% 位移并使用 0.75 alpha；聊天返回仍依赖真实 back stack 回到实际来源一级页。
- `chat/{call}` 进入时隐藏底部导航；从台站、消息或地图进入聊天后使用真实 back stack 返回来源页。
- 消息通知直接进入 `HubActivity`，先建立“消息”一级目的地再进入 `chat/{call}`；冷启动和 `singleTop` 已有实例都通过同一 intent 消费逻辑处理，因此返回落到消息列表。
- 顶层地图直接内嵌 MapLibre 或 Google `MapView`；高德/OSM/自定义使用 MapLibre，Google 普通/卫星使用 Google Maps，切换图源不再启动另一个顶层 Activity。
- `MapAct` / `GoogleMapAct` 仍保留用于坐标选择器和兼容入口；`MessageActivity` 等兼容 Activity 也未被强行删除。这里的“单 Activity”指四个一级页面和应用内聊天的统一导航壳，不代表整个 APK 只能声明一个 Activity。
- 台站页顶栏只承担当前页面标题与页面级菜单；完整呼号和 APRS 状态位于状态卡，跟踪启停在状态卡内，单次发送位置使用 Extended FAB。
- 顶层消息/地图/报文在宿主模式下不显示与 Bottom Navigation 重复的返回箭头或跨页快捷入口；旧独立 Activity 模式仍可保留兼容控件。
- 通知设置不再使用独立 `NotificationPrefs` Activity，也不再使用嵌套 `NavHost` 切换整棵设置树；`PrefsAct` 以覆盖层显示通知子页。进入时前景按既有 Activity motion 从右侧约 25% 滑入并淡入，底层主设置约向左 12% 且降至 75% 不透明度，返回反向。
- NotificationChannel 为进程级一次确保：`APRSdroidApplication` 后台预热，`ServiceNotifier.start()` / `notifyMessage()` / `notifyPosition()` 在真正发送前保留同步兜底。进入通知设置和点击系统频道详情入口不得等待 `createNotificationChannel()`。
- 通知子页可见时，从 Android 系统频道设置返回不会刷新被覆盖的整张主设置页；主设置中的可用地图模式也在 Compose 生命周期内 memoize，避免子页切换重复执行 Google Play Services 可用性检查。
- 台站与报文列表的默认 padding/间距较 2.0.0 收紧，并提供持久化 `ui.compact_lists` 开关。紧凑模式只改变 padding、间距、圆角和 symbol/图标尺寸，不得覆盖 Android 系统 fontScale。
- 系统 fontScale 较大时优先减少非核心留白和备注占用；当前台站备注在 `fontScale >= 1.15` 时最多一行，正文仍按系统字体比例缩放。

README 必须继续区分 “Latest release” 与后续可能出现的 “Current main”；新的未打 tag 功能不得写成已经发布。

Java 17 是当前构建基线。没有明确需求与完整兼容性验证时，不要仅为了数字更新切换 Java 21。

## 2. 项目目标与边界

本 Fork 在 APRSdroid 上增加 Icom IC-705 Wi-Fi 直连 APRS 收发，并保留 APRS-IS、AFSK、KISS、TNC2、Kenwood、蓝牙、USB、LAN TCP TNC 等原有路径。

主要目标：

- 手机与 IC-705 通过 WLAN UDP 会话通信，不依赖音频线或外接 TNC。
- IC-705 UDP Socket 单独绑定 Android 选定 Wi-Fi `Network`，不把 APRS-IS 等互联网流量整体绑到无互联网的电台热点。
- 使用 AX.25 + AFSK1200 + 12 kHz 单声道 PCM 进行收发，以 CI-V 控制 PTT。
- 在厂商 Wi-Fi 栈、线程调度和网络切换存在差异时，尽可能通过角色化 liveness 和局部恢复保持会话稳定。
- 保留 Compose Material 3 UI、消息、日志、台站、地图和定位能力。

### 明确的非目标 / 不应擅自改变的边界

- IC-705 LAN 协议和部分 APRS 服务器本身使用明文 UDP/TCP；`android:usesCleartextTraffic="true"` 是兼容性决定。HTTP POST 使用 `HttpURLConnection`，裸主机仍兼容 `http://:8080/`。没有明确测试条件时不要擅自强制 TLS 或删除明文能力。
- 模拟器、JVM 单测、Lint 与 Release 构建不能替代真机、真电台、低功率/假负载验证。
- 软件 PTT watchdog 不是硬件互锁；不能把“本地状态重置”当作“电台已经实际回到 RX”的证据。
- 手动更新检查必须保持**显式用户操作**：不得在启动、后台、WorkManager、Alarm、前台/后台 Service 中自动或周期检查，也不得自动下载/安装 APK，除非维护者明确重新定义产品行为。

## 3. 目录与构建结构

该仓库保留 APRSdroid 的非标准单模块布局：

| 路径 | 内容 |
| --- | --- |
| `src/` | Kotlin / Java 生产源码 |
| `src/ic705/` | IC-705 protocol / transport / session / backend / diagnostic |
| `src/audio/` | AFSK1200 与 PCM 相关实现 |
| `src/diagnostic/` | 持久化日志、Network 事件、诊断快照与 ZIP 导出 |
| `src/update/` | 手动 GitHub Release 检查 |
| `res/` | values、drawable、mipmap、menu 等 Android 资源；**没有生产 `res/layout` 页面** |
| `test/java/` | JVM 单元测试 |
| `androidTest/java/` | Android instrumentation 测试 |
| `.github/workflows/` | 测试、Lint、Release APK / GitHub Release 流水线 |

AGP 9 使用 built-in Kotlin；不要重新应用 `org.jetbrains.kotlin.android`。Compose 编译器版本与 Kotlin 版本保持一致。

### Product flavors / 发布包

Gradle 保留五种目标规格：

- `arm64Vulkan`
- `arm64Opengl`
- `arm32Opengl`
- `x86Multi`
- `x8664Multi`

正式 GitHub Release 当前只发布：

- `arm64Opengl`，文件名前缀 `Recommended_`
- `arm32Opengl`

ARM64 Vulkan 与 x86/x86_64 变体保留用于源码构建和兼容性验证。不要未经设计讨论重新合并成 Universal APK。Tag Release 会分别构建/缓存 ARM64 与 ARMv7 的 MapLibre `MinSizeRel` 原生库；普通 `main` 构建只验证推荐 ARM64 路径以控制 CI 时间。

## 4. UI、导航与地图事实

- 主 UI 已迁移到 Jetpack Compose + Material 3；不要恢复历史 `res/layout` 页面，也不要在 AI_CONTEXT 中继续引用已经删除的 `mapview.xml`。
- 四个一级页面由 `HubActivity` + Navigation Compose 管理：`stations`、`map`、`messages`、`packets`。底栏切换必须使用 `navigateTopLevel()`；当前实现 pop 到 `graph.id` 保存当前一级页状态，再 navigate/restore 目标 route。不要使用 `findStartDestination()` 作为 `popUpTo` 目标，否则 `stations` 会重新成为特殊的根节点返回路径。
- 一级页面转场故意不使用整页 fade/cross-fade；当前是 140/120 ms 的低幅横向 slide，减少重页面（尤其台站列表和 symbol Canvas）的 GPU alpha 合成成本。不要为了“更明显”重新叠加全屏透明度动画。
- `chat/{call}` 是二级 route。台站 → 聊天 → Back 必须回台站；消息 → 聊天 → Back 必须回消息；地图台站 Bottom Sheet → 聊天 → Back 必须回地图。
- 聊天层级 motion 与项目 Activity motion 保持一致：前进时聊天前景从右侧约 25% 进入、280 ms；底层向左约 12% 并降至约 0.75 alpha、240 ms；返回使用对应反向 240 ms transition。不要另写一套轻微 10%/4% 位移参数。
- 消息通知使用 `HubActivity.EXTRA_CHAT_CALL` 请求宿主建立“消息 → 聊天”栈；不要改回通知直接启动 `MessageActivity`，否则冷启动返回语义会退化。
- `PrefsAct` 内部负责主设置与通知设置子页；旧 `NotificationPrefs` Activity 已删除。通知子页是同 Activity 内的覆盖层，不使用单独嵌套 `NavHost`。打开单个 Android NotificationChannel 详情仍然通过系统 Settings Activity，这是系统边界。
- 通知子页的 motion 与项目 `m3_activity_*` 参数保持一致：前进时子页从右侧约 25% 进入并淡入，底层向左约 12% 且 alpha 降到约 0.75；返回方向相反。避免为这一页再引入独立的一套 180ms 整页 slide。
- NotificationChannel setup 不得成为通知设置导航的同步或异步等待条件。当前 `APRSdroidApplication` 后台预热一次，`ServiceNotifier` 在真正发通知前同步兜底；设置页只负责立即导航到应用内通知页或 Android 系统频道页。
- 通知子页显示期间 `PrefsAct.onResume()` 不刷新被覆盖的根设置；可用地图模式列表通过 `remember` 固定在当前 Compose 生命周期内，避免从系统频道页返回时重复做 Google Play Services availability 检查。
- `ui.compact_lists` 是台站/报文列表的共享 UI-only 偏好；设置页和两页溢出菜单可切换。该偏好不得修改系统字体倍率，紧凑模式只调整几何密度。
- 大字号适配应优先减少留白、图标和次要文本占用，而不是屏蔽用户 fontScale；当前 `fontScale >= 1.15` 时台站备注限制为一行。
- 顶层 `map` destination 内根据当前 `MapMode` 使用嵌入式 MapLibre 或 Google Maps renderer。MapLibre 负责高德、OpenStreetMap、自定义在线瓦片；Google Maps SDK 负责 Google 普通/卫星图源。
- 嵌入式 `MapView` 生命周期由 Compose destination 观察宿主 Lifecycle；离开 destination 时执行 pause/stop/destroy 并保存相机位置。切换 MapLibre/Google renderer 时同样先保存位置。
- `MapAct` / `GoogleMapAct` 是坐标选择器和兼容入口，不应重新成为四个一级页面的默认导航实现。
- 正式 ARM64/ARMv7 OpenGL APK 仍依赖官方 MapLibre Android 13.5.1 AAR 提供 Java/Kotlin API、资源、manifest 与传递依赖；Release CI 仅从 `android-v13.5.1` 源码重建对应 ABI 的 `libmaplibre.so`，使用 `MinSizeRel` + IPO/LTO 后替换 APK 内原生库。不要把这描述成维护了一个独立 MapLibre fork。
- APRS 台站、呼号标签等通过 MapLibre 图层渲染。
- MapLibre Offline 区域下载/管理不是当前功能；不要把主 AAR 中存在 offline API 类误写成项目启用了离线地图。
- OSM 必须保留可识别 User-Agent 与可点击的 `© OpenStreetMap contributors` 署名，不得批量抓瓦片或预取整个区域。
- Google Maps Key 只允许从 `MAPS_API_KEY` 环境变量、Gradle property 或未纳入版本控制的 `local.properties` 中 `mapsApiKey` 注入；不得提交 Key。未配置 Key 时自行构建版本隐藏 Google 图源。

## 5. IC-705 数据流

主要链路：

1. `AprsService` 根据 `PrefsWrapper` 选择后端。
2. `Ic705WifiBackend` 适配 APRSdroid Service/Prefs 到 `Ic705WifiBackendController`。
3. Controller 创建一代（generation）`Ic705RxSession`，并把接收 PCM 送入 `FeedableAfskDecoder`。
4. RX：Wi-Fi UDP → `Ic705AudioPacketCodec` → PCM16LE → AFSK1200 → AX.25/APRS。
5. TX：APRS/AX.25 → `Afsk1200PcmGenerator` → `Ic705TxAudioPacketizer` → CI-V PTT ON → Audio UDP → CI-V PTT OFF。

默认 control 端口是 UDP `50001`。CI-V 与 AUDIO 端口由会话协商。当前 DSP/codec 路径是 12 kHz；历史协议抓包中的 48 kHz RS-BA1 注释不能作为修改当前默认采样率的依据。

## 6. IC-705 liveness 与恢复模型

不要重新回到“任意通道 N 秒没包就销毁整个 session”的模型。

`Ic705RxSessionTiming` 当前默认值：

| 参数 | 默认值 | 语义 |
| --- | ---: | --- |
| CONTROL timeout | `5000 ms` | session 级权威存活信号 |
| CI-V timeout | `3000 ms` | 控制流存活；优先局部恢复 |
| AUDIO timeout | `30000 ms` | RX 音频允许较长静默 |
| AUDIO post-TX grace | `5000 ms` | PTT OFF ACK 后等待 RX 音频恢复 |
| stream recovery wait | `3000 ms` | 单次 stream rediscovery 等待新流量 |
| stream recovery attempts | `2` | 局部恢复次数上限 |
| watchdog cadence | `500 ms` | liveness 检查周期 |

### 角色规则

- **CONTROL**：超时属于 session 级失败，进入完整 recovery/reconnect。
- **CI-V**：RX/空闲状态超时先做 stream rediscovery；若局部恢复连续失败，再升级完整 reconnect。PTT 期间 CI-V 是安全关键控制路径，故障不应通过“悄悄重开流”掩盖 PTT 状态不确定性。
- **AUDIO**：允许 30 秒 RX 静默；TX 期间 AUDIO RX 静默不能触发 session teardown。PTT OFF ACK 后还有 5 秒 grace。真正长时间失活先局部恢复，失败后再升级。

soft recovery 的 STARTED / SUCCEEDED / ESCALATED 等关键决策应进入 `AppLog`，便于真机诊断。

## 7. TX / PTT 安全不变量

`Ic705PttStateMachine` 当前状态：

- `RX_IDLE`
- `TX_STREAMING`
- `DRAINING`

必须保持以下语义：

- PTT ON 只能在会话与 TX 条件满足后发生。
- `canStreamAudio` 只在 `TX_STREAMING` 为 true；进入 `DRAINING` 后 TX audio 必须停止。
- “radio 可能仍然被 PTT”与“允许继续发音频”是不同概念，不要合并成一个 Boolean。
- PTT OFF 只有收到电台 ACK 后才能确认释放并回到安全 RX 状态；UDP 本地 `send()` 成功不是电台 ACK。
- PTT OFF 失败/NAK/ACK 丢失时不得伪造 `RX_IDLE`；看门狗应保留保守状态并继续有限安全释放逻辑。
- `shutdown()` 只终止当前 PTT coordinator 的本地 timer/callback 生命周期，防止 zombie watchdog；**shutdown 不等于电台已经释放 PTT**。
- session 永久销毁前必须停止 TX audio，并在 CI-V transport 尚可用时尽最大可能请求 PTT OFF，然后再终止 coordinator 和 socket 生命周期。
- 修改 PTT、ACK、音频 drain、packet sequence、retransmit 或 teardown 顺序时必须补/改测试。

## 8. UDP / tracked packet 并发规则

华为 Android 12 真机曾暴露以下历史竞态：session teardown 与 TX executor 同时发生时，TX 线程可能撞到 `AUDIO channel missing`、`channel not open` 或 `Socket closed`。

当前约束：

- UDP channel 的 send/close 生命周期必须保证不会出现可避免的本地 close/send 竞态。
- TX loop 必须观察 session phase 与 `canStreamAudio`，不能在 recovery/teardown 后继续盲发 AUDIO。
- 关键 PTT/控制发送不能把 transport 异常完全吞掉；调用方必须知道发送失败。
- tracked packet 若实际 UDP send 失败，不应长期留在 retransmit store 中制造“未真正发送但以后又被重传”的假历史。
- teardown、PTT retry/watchdog 与 reconnect generation 的生命周期必须封闭，旧 generation 不能留下继续运行的 PTT timer。

不要增加 Huawei/Pixel 型号特判来掩盖这些问题；应保持设备无关的状态机语义。

## 9. 持久化诊断系统

正式故障报告不再依赖“导出时抓最后 600 行 logcat”。

### `AppLog`

`src/diagnostic/AppLog.kt`：

- 同时写 Android Logcat 与应用内 JSONL。
- 日志位于 `noBackupFilesDir/diagnostic_events/`。
- 当前文件上限约 `1 MiB`，保留 `4` 个历史归档，加当前文件约 `5 MiB`。
- 单线程 executor 顺序落盘。
- 未捕获异常会记录 crash、thread、exception/stack，并尽力 flush 后交回系统/原 crash handler。
- `process_start` 记录 PID、`VERSION_NAME`、`VERSION_CODE`、build type、`SOURCE_REVISION`。
- 密码、passcode、secret、token、精确纬经度字段自动脱敏。

### Network 诊断

`NetworkEventLogger` 记录 Android `NetworkCallback` 的关键生命周期，包括 available/lost、Capabilities 与 LinkProperties 变化。目标是区分：

1. Android/厂商 Wi-Fi `Network` 真正丢失；
2. Wi-Fi 仍在，但 IC-705 CONTROL/CI-V/AUDIO/session 自身失活。

### 导出

设置页“分享系统诊断与运行日志”通过 `LogReportManager` 生成 ZIP，包含人类可读报告和持久 JSONL 事件文件。导出逻辑运行在后台线程并通过 `FileProvider` 分享。

新增 IC-705/network/reconnect/PTT 逻辑时，优先增加**结构化事件**，不要只加难以关联的 `Log.d()` 文本。

## 10. 手动更新检查

实现位于 `src/update/`，设置入口位于“应用支持与关于”。

必须保持：

- 用户点击“检查更新”后才访问 GitHub Releases。
- 不在 `Application` / Activity 启动时自动调用。
- 不保存“下次检查时间”并偷偷周期联网。
- 不引入 WorkManager、AlarmManager、后台 Service 做更新轮询。
- 不自动下载或安装 APK。
- 发现新稳定版时仅提示当前/最新版本并打开 GitHub Release 页面。
- 版本比较逻辑应由 JVM 单测覆盖。

若以后确实要做自动更新，必须作为独立产品决策重新设计，而不是在现有 checker 上顺手加定时逻辑。

## 11. Android 17 权限与服务启动

API 37 的 `ACCESS_LOCAL_NETWORK` 是运行时权限。当前策略：

- IC-705 Wi-Fi (`ic705`) 与 LAN TCP TNC (`tcpip`) 在 Android 17+ 请求本地网络权限。
- 公网 APRS-IS TCP/HTTP/UDP 不因自身连接请求本地网络权限。
- Android 13+ 前台服务通知遵守通知权限要求。
- AFSK 后端按需请求麦克风；蓝牙路径按需请求对应蓝牙权限；定位权限由位置来源决定。
- 停止服务不应被新增权限拦截。
- 新增服务入口必须复用当前统一权限链，不要绕过运行时授权直接启动 APRS 服务。

USB attach 属于系统事件路径，修改时单独评估后台启动限制。

## 12. 配置导入与敏感数据

- 配置导入使用 Android `OpenDocument` / `ContentResolver` 直接读取 `content://` 流。
- 不查询 `_data`，不把 `content://` 强转成 `File`，不拼 `/storage/...` 绝对路径。
- 导入必须保留键 allowlist、类型、大小、字符串长度限制。
- `service_running`、`firstrun` 等运行状态不得被外部 profile 覆盖。
- 日志、异常、诊断 UI、`toString()` 不输出密码、passcode、token、secret 或完整鉴权数据。

## 13. 代码约定

- 新业务代码优先 Kotlin；尊重现有 Java/JNI 边界。
- 后台 I/O 使用现有 executor/调度器，不在主线程做 Socket、数据库、DSP 或 GitHub API I/O。
- NotificationManager / NotificationChannel 的 Binder 调用不得绑在设置导航首帧，也不得让“打开通知设置/系统频道页”等待其完成；实际发送通知前可以保留必要的同步兜底。
- UI 页面使用 Compose Material 3；不要为了小功能重新引入第二套 XML View 页面。
- 四个一级页面必须继续通过主 `NavHost` 切换；不要用 `startActivity()`、`REORDER_TO_FRONT` 或多个 Activity 各自复制 Bottom Navigation 来模拟一级导航。
- 一级 `navigateTopLevel()` 应保持 `popUpTo(graph.id) + saveState/restoreState + launchSingleTop` 的对等语义；不要为了套用示例代码退回 `findStartDestination()`，否则台站根页面会再次走不同的 pop transition 生命周期。
- 二级页面若进入主导航栈，应依赖真实 back stack 返回来源，不要保存 `fromStation` / `fromMessages` 一类人工来源标记。
- 使用 AndroidX API；不要重新引入 `android.preference.*` 或旧 Support Test 包。
- HTTP 后端保持 `HttpURLConnection`，不要恢复 Apache `DefaultHttpClient` / `org.apache.http.legacy`。
- Activity 结果与文档选择使用 Activity Result API。
- Socket/channel close 必须幂等；异步 timer/callback 必须有明确 owner 生命周期和 shutdown/cancel 路径。
- 文档中的端口、采样率、版本、权限和行为必须从实现核对，不从旧 README/旧 Release 文案反推当前代码。

## 14. 验证命令

常用完整验证：

```bash
./gradlew verifyReleaseVersion \
  testArm64OpenglDebugUnitTest \
  lintArm64OpenglDebug \
  assembleArm64OpenglRelease \
  assembleArm32OpenglRelease \
  --no-daemon --stacktrace
```

需要验证全部源码 flavor 时可运行：

```bash
./gradlew assembleRelease --no-daemon --stacktrace
```

Windows PowerShell 使用 `./gradlew.bat`。

### IC-705 人工验证

涉及 session/TX/recovery 的改动，自动测试通过后仍至少验证：

1. IC-705 诊断页能连接、持续接收 PCM、AFSK 解码正常。
2. 低功率/假负载下单次 TX：PTT ON → audio → PTT OFF ACK 顺序正确。
3. 连续多次 TX/RX 切换不出现假 `RX_IDLE`、AUDIO 线程继续发送或 PTT timer 累积。
4. TX 中断网时不会发生 `Socket closed`/missing channel 导致进程崩溃；PTT 释放语义保持保守。
5. RX AUDIO 长时间静默、PTT 期间 AUDIO 静默不会无故重建整个 session。
6. CI-V/AUDIO timeout 优先局部恢复；CONTROL timeout 或局部恢复失败才升级完整 reconnect。
7. 电台 Wi-Fi 在线时，APRS-IS 的默认互联网路径没有被整个 App 绑定到 Wi-Fi。
8. Android 17 首次授权、拒绝、重新授权和本地网络权限行为正确。
9. 故障后导出的诊断 ZIP 能看到 source revision、network lifecycle、session/generation、soft recovery 和 PTT 时间线。

## 15. R8 与 Release 可诊断性

- Release 启用 R8 `minifyEnabled` 与 `shrinkResources`。
- 不要重新加入全局 `-dontobfuscate`。
- JNI、反射、序列化、MapLibre 集成变更必须在 Release 构建中验证，只添加必要 keep 规则。
- Tag Release CI 保存正式发布 flavor 的 `mapping.txt`；反混淆必须使用与 APK 完全匹配的 mapping。
- `BuildConfig.SOURCE_REVISION` 来自 Git/GitHub SHA，用于诊断精确确认测试 APK 对应源码；不要删掉这个字段。
- MapLibre 原生瘦身通过 `.github/scripts/build_maplibre_minsizerel.sh` 与 `replace_apk_maplibre.py` 完成；替换后必须重新做 16 KiB `zipalign`、签名，并校验 APK 内 `libmaplibre.so` SHA-256 与自编译产物一致。

## 16. 版本与发布流程

正式发布时保持一致：

1. `build.gradle`：`mod_version` 与递增的 `mod_version_code`。
2. `CHANGELOG.md`：顶部新增对应版本，内容只写实际进入该 tag 的改动。
3. `README.md`：更新 Latest release，并移除/调整已经发布的 “main unreleased” 描述。
4. `AI_CONTEXT.md`：更新发布基线与仍未发布的 main 状态。
5. tag 使用 `Mod-v<major.minor.patch>`。

CI `verifyReleaseVersion` 会在 tag 构建时检查 tag 与 APK 版本一致。Release workflow 会测试、Lint、构建 ARM64/ARMv7 OpenGL APK，构建并注入同版本 MapLibre `MinSizeRel` + IPO/LTO 原生库，重新执行 16 KiB 对齐与签名，校验 ABI/MapLibre 数量及原生库 SHA-256，生成 `SHA256SUMS.txt`、R8 mapping，并在 tag 时创建 GitHub Release。正式 tag 缺少签名 secrets 或必要 Maps Key 时必须失败，不能悄悄发布不符合预期的包。

`main` 普通 push 也会触发构建验证，但不等于创建新 GitHub Release。

## 17. CHANGELOG 写作规范

CHANGELOG 是工程记录，不是营销文案。

- 陈述事实，不堆砌“彻底、全面、极致、丝滑、革命性、告别”等营销词。
- 不使用 emoji 作为标题或条目前缀。
- 一个用户可感知或工程上重要的改动写一行；小改动不要扩成发布会文案。
- `Added / Fixed / Changed / Removed` 按需出现，没有内容的分类不写。
- 对安全/网络问题写清“原因 + 行为变化”，避免只有“修复 Bug”。

示例：

错误：
> 全面重构 IC-705 网络层，彻底告别断线！

正确：
> CI-V 与 AUDIO timeout 改为先执行 stream-local recovery；连续恢复失败后才升级为完整 session reconnect。

## 18. 历史迁移：只保留仍影响当前维护的事实

以下迁移已经完成，不应在新代码中反向恢复：

- Gradle 9.5 / AGP 9.3.2 / built-in Kotlin / API 37 / Java 17。
- 生产 UI 已迁移到 Compose Material 3，历史 `res/layout` 页面已删除。
- 四个一级页面已从多 Activity 导航收敛为 `HubActivity` + Navigation Compose；四个 root 当前统一使用 `popUpTo(graph.id)` 的保存/恢复路径，`stations` 不再因是 start destination 走特殊 pop-only 语义。
- 顶层 destination 的默认 cross-fade/整页 alpha 合成已移除，当前使用低幅纯横移；聊天层级动效与项目 `m3_activity_*` 参数统一。不要通过 `findStartDestination()` 或重新加全屏 fade 把这两个回归带回。
- 通知设置已从独立 Activity 和嵌套 `NavHost` 收敛为 `PrefsAct` 内覆盖层；其动效与项目既有 Activity motion 参数一致。NotificationChannel 使用应用后台预热 + 真正发送前兜底，不得重新让设置导航等待频道创建。
- 通知子页覆盖根设置时不执行根设置 `onResume` 刷新；可用地图模式在当前 Compose 生命周期内 memoize，避免系统频道页返回造成无意义重计算。
- 台站/报文列表已有标准与紧凑两档几何密度，并保持系统 fontScale；不要用全局缩字号代替密度设计。
- Mapsforge 与专用离线瓦片下载器已移除；当前地图为 MapLibre + Google Maps SDK 分工，主地图已内嵌到顶层 `map` destination。
- 外部存储读写权限已删除；文档导入/导出使用 SAF / `ContentResolver`。
- HTTP POST 已从 Apache HTTP 客户端迁移到 `HttpURLConnection`。
- Release 使用 R8 压缩/资源裁剪并保留 mapping。
- Android 17 本地网络权限已接入 IC-705 / LAN TCP TNC 路径。
- 配置导入已经过 allowlist/类型/大小/状态项加固。
- PTT OFF 已改为 ACK 确认语义，不允许“本地 UDP 发送成功 = 已释放”。
- IC-705 watchdog 已从统一短超时演进为 CONTROL/CI-V/AUDIO 角色化 liveness + stream-local recovery。
- 诊断已从依赖 logcat 尾部升级为持久化结构化事件 + Network 生命周期 + ZIP 导出。
- 正式 ARM64/ARMv7 MapLibre OpenGL 原生库已改为同版本源码 `MinSizeRel` + IPO/LTO 构建并在发布阶段替换；APRS 符号表资源已从 PNG 转为 WebP。
- AndroidX Preference 与 AppCompat 的直接依赖已移除；设置页和兼容对话框已收敛为 Compose/Material 3，通知配置由系统 NotificationChannel 管理。

不要在本文继续堆积每个旧版本的完成清单；历史细节属于 `CHANGELOG.md` 和 Git 历史。本文只保留会影响下一次修改决策的当前事实。