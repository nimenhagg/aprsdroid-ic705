## [Mod-v2.0.3] - 2026-08-28

### Fixed
- 修复从地图、消息或报文等任意底栏页面返回“台站”时，由于 `stations` 同时是 NavGraph 起始目的地而走特殊 pop 路径，导致转场生命周期与其它一级页面不一致并出现卡顿；一级导航现在统一 pop 到 NavGraph entry，再按目标 route 恢复状态，四个底栏目的地使用同一 navigate/restore 路径。
- 修复一级页面切换时对整页执行 alpha 混合增加合成开销的问题；底栏切换改为 140/120 ms 的小幅纯横向位移，避免台站 LazyColumn 与 APRS symbol Canvas 在动画期间承担额外整页透明度合成。
- 修复聊天返回消息主页或其它来源一级页时动效与项目其它二级页面不一致的问题；聊天前进/返回统一使用现有 Material Activity motion 的 280/240 ms、25% 前景位移与 12% 背景位移语义。

### Changed
- 版本更新为 `Mod-v2.0.3`（`versionCode 2026082901`）。

## [Mod-v2.0.2] - 2026-08-28

### Fixed
- 通知设置不再使用与其它设置页不一致的嵌套 `NavHost` 整页 slide；改为复用项目现有 Activity motion 参数的覆盖层转场，进入时前景从右侧 25% 滑入并淡入、底层主设置轻移至左侧并降至 75% 不透明度，返回方向相反。
- 修复通知设置仍有可感知停顿的问题：NotificationChannel 改为进程级一次确保，Application 在后台预热，前台服务和消息通知在真正发送前保留同步兜底；进入通知页或点击频道设置时不再等待 `createNotificationChannel()`。
- 从 Android 系统频道设置返回通知页时不再刷新被覆盖的整张主设置页，避免无意义的偏好读取和地图可用性检查造成掉帧。

### Changed
- 主设置中的可用地图模式列表在 Compose 生命周期内只计算一次，避免通知子页切换触发重复 Google Play Services 可用性检查。
- 版本更新为 `Mod-v2.0.2`（`versionCode 2026082900`）。

## [Mod-v2.0.1] - 2026-08-28

### Added
- 新增“紧凑列表”显示模式，可在“设置 → 地图与显示”中切换，也可从台站/报文页菜单快速切换；该模式只收紧留白和图标尺寸，不覆盖 Android 系统字体缩放。

### Fixed
- 修复主界面迁移到 Navigation Compose 后默认 cross-fade 导致一级页面出现明显整页淡入淡出的问题；一级页改为短促、方向明确的 Material 风格切换，聊天继续保留二级前进/返回层级动画。
- 修复进入“通知设置”仍会短暂卡顿的问题：通知设置并入 `PrefsAct` 内部 Compose 导航，删除独立 `NotificationPrefs` Activity，并将 NotificationChannel 确保过程移到后台线程，避免首帧前同步 Binder 调用。

### Changed
- 台站列表和报文列表默认密度收紧约 10–15%；缩小台站 symbol、卡片 padding 和卡间距，并降低报文卡片 padding/间距。
- 系统字体放大时优先减少非核心留白；台站备注在较大 fontScale 下限制为一行，正文仍遵循系统字体比例。
- 版本更新为 `Mod-v2.0.1`（`versionCode 2026082899`）。

## [Mod-v2.0.0] - 2026-08-28

### Added
- 新增统一的 Material 3 顶层导航壳，底部固定为“台站 / 地图 / 消息 / 报文”四个一级目的地，并使用 Navigation Compose 保存和恢复各一级页面状态。
- 地图加入主 `NavHost`：高德、OpenStreetMap、自定义瓦片继续由 MapLibre Native 渲染，Google 普通/卫星图继续由 Google Maps SDK 渲染，图源切换不再通过顶层 Activity 跳转。
- 点对点聊天加入主导航栈；聊天页作为二级页面自动隐藏底部导航。

### Fixed
- 修复从台站、消息列表、地图台站卡进入聊天后的返回语义：返回会回到实际来源页面，而不是退出应用或跳到错误的一级页。
- 消息通知改为通过 `HubActivity` 构造“消息列表 → 聊天”导航栈；冷启动或已有实例收到通知时，返回均落到消息列表。
- 主导航中的台站、地图、消息和报文页面移除与底部导航重复的返回箭头、跨页按钮和临时菜单入口；报文页不再叠加第二层“发送位置 / 开始停止跟踪”底栏。

### Changed
- 台站页顶栏固定显示“台站”，完整呼号与 APRS 运行状态移入状态卡；跟踪启停放入状态卡，单次发送位置改为 Extended FAB。
- `HubActivity` 作为四个一级页面和应用内聊天的统一宿主；旧 `MapAct` / `GoogleMapAct`、`MessageActivity` 等仍保留给坐标选择器、外部入口或兼容路径，不要求整个应用删除所有 Activity。
- 嵌入式 MapLibre / Google `MapView` 生命周期由 Compose destination 跟随宿主 Lifecycle 管理，离开地图时保存相机位置并释放对应 View 生命周期。
- 版本更新为 `Mod-v2.0.0`（`versionCode 2026082898`）。

## [Mod-v1.9.7] - 2026-08-28

### Fixed
- 通知设置中的“消息通知”和“状态通知”入口不再在每次点击前重复创建系统通知频道；频道在页面创建时统一确保存在，点击后直接进入 Android 系统频道设置，减少不必要的主线程 Binder 调用。

### Changed
- 完成 Compose / Material 3 设置体系的遗留收尾：移除地图 About、APRS-IS passcode 等 `ComponentDialog + ComposeView` 兼容壳，以及 AndroidX Preference / AppCompat 的直接依赖、旧 Preference XML/主题和失去入口的旧通知配置。
- Android 8.1+ 通知行为统一以 `NotificationChannel` 为设置源，删除旧的 per-notification LED、振动、铃声读取与调用参数。
- IC-705 session 将音频乱序/sequence、scheduled task 注册、timing、connection-info timer/retry 等低风险纯逻辑拆为独立 policy/组件并补充 JVM 单测；PTT、TX pacing、鉴权 wire 行为和 CI-V 安全语义保持不变。
- 清理未使用的 lifecycle ViewModel Compose 依赖、旧通知资源、Preference 主题和 Ant/API 19 时代的 `project.properties`。
- 版本更新为 `Mod-v1.9.7`（`versionCode 2026082897`）。

## [Mod-v1.9.6] - 2026-08-27

### Fixed
- 修复位置设置页在 SmartBeaconing™ 为当前位置来源时，右侧值标签使用完整说明导致左侧标题和摘要被极端压缩换行的问题；设置页当前值改用短标签，选择对话框仍保留详细说明。
- 修正 Release Notes 生成逻辑：显式传入的 tag 优先于工作流环境变量，并按 `Mod-vX.Y.Z` 正确生成标题，避免再次出现 `main` 或重复 `Mod` 的发布说明标题。

### Changed
- 版本更新为 `Mod-v1.9.6`（`versionCode 2026082796`）。

## [Mod-v1.9.5] - 2026-08-27

### Fixed
- 修复“默认地图图源”设置项误直接打开地图的问题；现在会在设置页原地选择并保存默认图源，下次打开 APRS 地图时生效。
- 恢复主设置页的通知设置入口，并改为直接管理 Android 8.1+ 系统通知频道，避免继续暴露在受支持系统上无效的旧式单通知 LED/振动/铃声开关。
- 修复 APRS-IS 验证码未设置时仍显示“已设置”的状态判断。
- APRS SSID 选择恢复完整 0–15，并让长选项对话框可滚动；SSID 0 明确表示不附加后缀。
- 周期 GPS 设置页移除当前 `PeriodicGPS` 实现并未消费的距离、GPS 启用策略和网络定位开关，避免保存无实际效果的配置。
- APRS Objects 开关改为按目标布尔值写入，避免状态切换与 Compose UI 不一致。

### Changed
- 全面重写主设置、定位、SmartBeaconing、APRS-IS、IC-705、AFSK、TNC 与通知页面的中英文文案，移除中英混排、过时术语和含糊的旧翻译。
- 统一位置来源、SSID、中继路径、连接方式、GPS 策略、位置隐私、更新检查等设置的标题、摘要与选项表述。
- 清理呼号/APRS-IS 验证码对话框与 APRS 符号选择器文案，并将现代 Compose 设置流程中的硬编码界面文字迁移到可本地化资源。
- 版本更新为 `Mod-v1.9.5`（`versionCode 2026082795`）。

## [Mod-v1.9.4] - 2026-08-27

### Added
- 台站详情的历史 APRS 报文改为结构化解析视图，展示源/目标、路径、位置、速度/航向、海拔、频率、消息/备注等信息；每条记录提供正常尺寸的“显示原始数据”按钮按需展开 TNC2 原文。
- 主设置页增加“是否发送电量信息？”开关，仅 APRS-IS 生效；开启后在长度允许时向位置信标附加完整 `BAT:xx%` 字段，空间不足时整项省略，不发送截断字段。
- 首页台站单击/长按操作可互换，默认保持单击发消息、长按查看详情。
- 自动识别 destination 为 `APFMO*` 的 FMO 台站，并在台站卡片中以 `FMO` 标签在前、语音频率标签在后的顺序显示。

### Changed
- 应用名称统一为 `APRSdroid Mod`，修正历史默认状态中的 `APRSDroid Mod` 大小写；旧默认值会自动迁移。
- 版本/tag 命名改为 `Mod-vX.Y.Z`；本版本为 `Mod-v1.9.4`（`versionCode 2026082794`）。更新检查继续兼容历史 `vX.Y.Z-ic705` tag。
- 最低系统版本调整为 Android 8.1 / API 27。

## [v1.9.3-ic705] - 2026-08-26

### Added
- 增加持久化结构化诊断日志：关键 App、Android Network、IC-705 session/recovery、PTT 与崩溃事件同时写入 Logcat 和轮转 JSONL，并可从设置页导出诊断 ZIP。
- 设置页增加手动 GitHub Releases 更新检查；仅在用户点击时联网，不在启动或后台周期检查，也不自动下载或安装。

### Fixed
- 修复 IC-705 TX、session teardown 与 UDP send/close 的竞态；TX 在进入 draining/recovery 后停止音频发送，tracked packet 实际发送失败时不再留下伪重传记录。
- 收紧 PTT timer/watchdog 生命周期，旧 session generation 不再遗留 zombie callback；PTT OFF 仍坚持以电台 ACK 作为实际释放确认。
- CONTROL、CI-V、AUDIO 改为角色化 liveness：CI-V/AUDIO 超时优先执行 stream-local recovery，连续失败后才升级完整 reconnect；TX 期间 AUDIO RX 静默及 PTT OFF 后恢复宽限期不再误触发重连。

### Changed
- 正式 ARM64/ARMv7 OpenGL Release 保留官方 MapLibre Android 13.5.1 AAR 的 API、资源和依赖，但以同版本源码重新构建 `libmaplibre.so`，使用 CMake `MinSizeRel` + IPO/LTO，并在 APK 打包后重新执行 16 KiB `zipalign`、签名和 SHA-256 一致性校验。
- ARM64 `libmaplibre.so` 的同条件 strip 对比由 `10,843,520` B 降至 `6,707,856` B（减少 `38.14%`）；推荐 ARM64 OpenGL APK 由 `17,837,278` B 降至 `13,417,695` B（减少 `24.78%`）。
- APRS 符号表资源由 PNG 转为 WebP，并启用 Gradle configuration cache 以减少后续构建开销。
- 版本更新为 `1.9.3-ic705`（`versionCode 2026082693`）。

## [v1.9.2-ic705] - 2026-08-26

### Fixed
- IC-705 PTT 关闭在未收到电台 ACK 时不再回退为 `RX_IDLE`；状态保持为待释放并由绝对看门狗继续重试，避免把“本地发送成功但电台未确认”误判为 PTT 已释放。
- 修复数据库从历史 v1/v2 直接升级到当前 v4 时跳过中间迁移的问题。
- 配置文件导入增加允许键、类型、大小和字符串长度校验，并阻止 `service_running`、`firstrun` 等运行状态项被外部配置覆盖。
- 配置导入日志不再输出配置值明文。
- 将 `play-publish-credentials.json` 加入 `.gitignore`，降低 Play 发布凭据误提交风险。

### Changed
- 版本更新为 `1.9.2-ic705`（`versionCode 2026082692`）。

## [v1.9.1-ic705] - 2026-08-25

### 🔧 稳定性修复：IC-705 Wi-Fi TX 异常防御与 Socket 健壮性
* **[严重] IC-705 发射线程崩溃修复**：
  - 修复 IC-705 Wi-Fi 发射信标/短消息音频流（`startTxAudioStreaming`）过程中，若发生网络重连、Socket 关闭或通道解构时，`NoSuchElementException`、`IllegalStateException`、`SocketException` 逃逸至 `ic705-tx` 线程导致应用崩溃的问题。
  - 在 `Ic705RxSession` 中重构 `sendTracked`、`sendUntracked` 以及 TX 音频推流循环：
    - 增加严格的空安全查找与 `channel.isOpen` 检查。
    - 捕获传输层 I/O 与运行期异常，遇到网络异常时安全中止推流并自动调用 `pttStateMachine.forceRelease()` 安全释放电台 PTT，彻底杜绝崩溃和无线电卡 PTT 风险。
* **版本元数据**：
  - 更新版本为 `1.9.1-ic705`（`versionCode 2026082591`）。

## [v1.9.0-ic705] - 2026-08-25

### 🚀 100% Jetpack Compose 全量现代化重构与体验升级
* **100% Jetpack Compose 架构达成**：
  - 彻底清空并移除 `res/layout` 目录下的所有历史 XML 布局文件（0 个 XML layout 遗留）。
  - 全部页面（Hub、日志、消息列表、点对点会话、电台详情、地图控制悬浮层、主设置、连接设置、定位设置、通知设置、IC-705 诊断监控）均转换为纯 Compose + Material 3 实现。
  - 引入现代响应式架构：ViewModel + Repository + StateFlow + `collectAsStateWithLifecycle()`。
* **IC-705 Wi-Fi 实时诊断界面全面优化**：
  - 诊断控制按钮完整中文汉化（“开始诊断”、“停止诊断”），采用 48dp 药丸胶囊排版与单行自适应，彻底消除排版拥挤与文字截断。
  - 实时状态角标、2x2 关键解码指标（PCM 采样点数、已解码 AX.25 帧数、音频重置次数）与等宽字体事件日志卡片。
* **设置体验与 APRS 中继路径预设**：
  - 系统统一命名为“设置”，呼号（纯 3~7 位字符）与 SSID（-0 ~ -15）清晰隔离。
  - 主设置页去除了多余重复的 Wi-Fi 诊断入口。
  - APRS 路径预设一条也不自带，支持用户自定义输入、一键保存为预设 Chip、点击填入与叉号实时删除。
  - 将位置隐私设置（坐标模糊度、发送速度方位、发送海拔）平铺整合在“定位设置”最下方。
* **版本元数据**：
  - 更新版本为 `1.9.0-ic705`（`versionCode 2026082590`）。

## [v1.8.6-ic705] - 2026-08-25

### 🔧 七项 Bug 修复：游标逻辑、日志标签、资源泄露、重复发送、距离计算、配置残留与线程泄露
* **[严重] 地图电台 BottomSheet 弹窗失效修复**：
  - 修复 `StationBottomSheetHelper.show` 中 `cursor.moveToFirst()` 预消费与 `StationItem.fromCursor` 内部 `moveToNext()` 冲突，导致单行查询结果被跳过、BottomSheet 永远无法弹出的 100% 必现 Bug。
  - 补充 `Station.COLUMNS_MAP` 中缺失的 `TS` 列，修复电台时间戳始终显示为 1970 年的问题。
* **[中等] 发射信标日志标签错误修复**：
  - `AprsService.sendPacket()` 改用 `TYPE_TX` 替代 `TYPE_POST` 写入数据库，修复日志页面所有自己发出的位置信标全部显示绿色 "RX" 接收徽章的问题。
  - 扩展 `addPost` 中 `parsePacket` 守卫条件覆盖 `TYPE_TX`，确保发射包仍被解析入 stations 表。
  - 扩展 `getExportPosts` 导出查询过滤条件（`type in (0, 3)` → `type in (0, 3, 4)`），确保 TX 包被纳入日志导出。
* **[中等] 日志导出空结果 Cursor 泄露修复**：
  - `UIHelper.LogExporter` 在 `c.count == 0` 分支补充 `c.close()`，修复空导出时 SQLite Cursor 句柄泄露。
* **[中等] 消息 Activity 重建重复发送修复**：
  - `MessageActivity.onCreate` 添加 `savedInstanceState == null` 守卫，防止屏幕旋转、暗黑模式切换或进程恢复时重复向空中无线电/APRS-IS 发射报文。
* **[轻微] 未定位时 Null Island 虚假距离修复**：
  - `HubStationScreen`、`StationDetailScreen`、`StationBottomSheet` 三处距离计算添加 `hasMyPosition` 守卫，当设备未获得 GPS 定位时显示 `"—"` 替代从 (0°, 0°) 计算出的荒谬万公里距离。
* **[轻微] 呼号 SSID 去除后旧值残留修复**：
  - `PasscodeDialog.saveFirstRun` 当用户输入不含 `-` 的纯呼号时，执行 `remove("ssid")` 清除 SharedPreferences 中残留的旧 SSID。
* **[轻微] TCP 后端 Executor 线程泄露修复**：
  - `TcpUploader.stop()` 补充 `executor.shutdown()`，防止服务反复停止/启动时 `SingleThreadExecutor` 线程资源累积泄露。
* 版本元数据更新为 `1.8.6-ic705`（`versionCode 2026082586`）。

## [v1.8.5-ic705] - 2026-08-25

### Changed
- IC-705 Wi-Fi 诊断页重构为 Compose Material 3 实现（`Ic705RxDiagnosticScreen`），包含双通道连接参数、实时数据仪表盘和内存事件流。
- 停用 V1 (JAR) 签名，改用 Android V2、V3、V4 签名。
- 版本元数据更新为 1.8.5-ic705。

## [v1.8.4-ic705] - 2026-08-25

### Fixed
- PTT OFF 连续发送失败后增加安全释放尝试与重试机制。
- 修复配置导出无法打开目标文档时误报成功的问题，并指定 UTF-8 编码。
- 统一 MapLibre 依赖与 OSM 请求的版本常量。
- Google 图源可用性改为检查 Google Play 服务的实际状态。
- 清理 Compose 页面与 IC-705 诊断页硬编码中文，默认提供英文资源，保留简体中文资源。

### Changed
- 更新 Lint Gradle DSL 写法。
- 同步 README 与工程交接文档策略。
- 版本元数据更新为 1.8.4-ic705。

## [v1.8.3-ic705] - 2026-08-25

### Changed
- `androidx.appcompat:appcompat` 升级至 1.8.0。
- `com.google.android.material:material` 升级至 1.14.0。
- `com.squareup.okhttp3:okhttp` 升级至 5.3.0。
- 调整 Google 地图构建逻辑：无 Key 时隐藏 Google 图源，无 Google Play 服务时回退 MapLibre。
- 版本元数据更新为 1.8.3-ic705。

## [v1.8.2-ic705] - 2026-08-25

### Changed
- 偏好设置弹窗迁移至 Material 3 风格。
- 发布产物调整为仅保留 OpenGL 渲染管线版本（arm64-v8a 和 armeabi-v7a）。
- 移除顶栏调试图标，支持长按顶栏标题生成系统诊断报告。
- GitHub CI 与本地 Release 构建启用 V1、V2、V3、V4 签名。
- 版本元数据更新为 1.8.2-ic705。

### Fixed
- 修复部分定制 ROM 前台定位服务使用多色图标导致的 `BadNotificationException`，改用单色矢量图标。
- 修复全新安装无数据时打开地图排序触发 `SQLiteException` 的问题。

## [v1.8.1-ic705] - 2026-08-25

### Fixed
- 修复冷启动时 OkHttpClient 初始化触发 MapLibre 上下文校验导致的崩溃。
- 增加 CI-V PTT OFF 的 ACK 握手确认与定时重试机制。
- 适配 Android 14+ 广播注册规范，使用 `RECEIVER_NOT_EXPORTED`。
- 规范化 USB TNC 权限请求与设备插拔监听，适配 Android 12+ 约束。
- 版本元数据更新为 1.8.1-ic705。

## [v1.8.0-ic705] - 2026-08-24

### Changed
- Release 产物重组为独立规格。
- 启用 R8 代码优化/混淆和资源裁剪。
- OpenStreetMap 请求统一 User-Agent，并显示版权信息。
- Google 图源配置改为仅从 CI Secret 注入，移除源码内置 Key。
- 发布流水线增加验签和 ABI 检查。
- 版本元数据更新为 1.8.0-ic705。

## [v1.7.1-ic705] - 2026-08-24

### Changed
- 地图引擎由 Mapsforge 0.3.0 迁移至 MapLibre Native 13.5.1。
- 优先使用 Vulkan 渲染，不支持则回退 OpenGL。
- APRS 符号、标签等地图元素迁移至 GeoJSON/SymbolLayer 实现。
- Google 图源切换路由优化，不再由 Google SDK 代绘其他瓦片。
- 版本元数据更新为 1.7.1-ic705。

### Removed
- 删除旧 Mapsforge 聚合 JAR 及专用瓦片下载器。

## [v1.6.3-ic705] - 2026-08-24

### Changed
- 地图坐标、通知铃声选择和权限申请迁移至 AndroidX Activity Result launcher。
- 日志与配置导出改用应用专属 Documents 目录并使用 FileProvider 分享。
- 修复 1001 条 Android Lint 警告，补全语言复数分支与拼写。
- IC-705 诊断、数字中继预设和关于界面的默认文案改为英文，清理硬编码中文。
- 版本元数据更新为 1.6.3-ic705。

### Removed
- 删除外部存储读写权限声明。
- 删除 86 个无用资源标识及其翻译。

## [v1.6.2-ic705] - 2026-08-24

### Changed
- HTTP POST 后端从 Apache `DefaultHttpClient` 迁移到 `HttpURLConnection`，增加超时与规范化请求头。
- 配置导入改用 Activity Result `OpenDocument`。
- 版本元数据更新为 1.6.2-ic705。

### Removed
- 删除旧版 Okio 依赖。
- 删除离线地图文件选择入口及其相关资源。

## [v1.6.1-ic705] - 2026-08-24

### Removed
- 删除 5 个 RecyclerView 源文件和 13 个旧 View/XML 布局。
- 移除未使用的 `PacketDroid` Git 子模块。
- 清理已失效的 ProGuard 规则与无用导包。
- 删除 IC-705 Wi-Fi 网络选择器中的旧 API 分支及 `requestLegacyExternalStorage` 属性。
- 版本元数据更新为 1.6.1-ic705。

## [v1.6.0-ic705] - 2026-08-24

### Changed
- 提升 `compileSdk` / `targetSdk` 至 API 37。
- 为局域网连接增加 `ACCESS_LOCAL_NETWORK` 权限声明及运行时授权。
- 构建链升级：Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10, Compose 2026.08.00。
- 拆分 Java/Kotlin 源集，更新测试组件与 Gradle DSL。
- README 重写为中英双语，并扩充 AI_CONTEXT.md。
- 版本元数据更新为 1.6.0-ic705。

### Fixed
- 修复 Kotlin 2.2 相关的 Intent 空安全及冗余 Service 生命周期入口。

## [v1.5.15-ic705] - 2026-08-24

### Added
- 发布流水线新增单元测试、Lint 与版本标签校验。

### Fixed
- 修复 Lint 报告的权限、主题及文档 URI 权限问题。
- 增加 PTT OFF 发送失败的看门狗重试逻辑。

### Changed
- 移除旧版 Okio 依赖，更新 JUnit 4，修正文档说明。
- 版本元数据更新为 1.5.15-ic705。

## [v1.5.12-ic705] - 2026-08-24

### Added
- 地图点击电台时弹出 Material 3 BottomSheet，显示相关信息并提供快捷入口。
- 关于对话框改为 Compose Material 3 实现。

### Changed
- 地图页顶栏标题统一为 "APRS 地图"。

## [v1.5.11-ic705] - 2026-08-24

### Changed
- 报文日志页（`LogActivity`）重构为 Compose 界面，支持分类颜色、实时搜索、快速过滤。
- 地图界面缩放控件与定位按钮重构为 Material 3 悬浮按钮。
- 短消息传输状态文案优化。

### Removed
- 移除旧版 `ZoomButtonsController` 缩放条。

## [v1.5.10-ic705] - 2026-08-24

### Fixed
- 修复从地图或日志页按返回键直接退出应用的问题，恢复导航栈。
- 修正主界面顶部菜单弹出的锚点位置。

## [v1.5.9-ic705] - 2026-08-24

### Changed
- 主界面台站列表（`HubActivity`）重构为 Compose 界面。
- 消息会话总览页（`ConversationsActivity`）重构为 Compose 界面。

## [v1.5.8-ic705] - 2026-08-24

### Changed
- 引入 Jetpack Compose 与 Material 3 框架，支持动态取色。
- 短消息聊天页（`MessageActivity`）重构为 Compose 界面，更新布局并支持长按操作。
- GitHub Actions 配置更新 Node.js 版本以消除警告。

## [v1.5.7-ic705] - 2026-08-24

### Changed
- 地图偏好设置实现动态联动，根据所选图源显示或隐藏相应配置项。
- 精简自定义瓦片默认值。

## [v1.5.6-ic705] - 2026-08-24

### Fixed
- 修复短消息发送后列表未即时刷新的问题。
- 修复卡片背景溢出遮挡圆角的问题。

### Changed
- 短消息聊天界面升级发送状态显示。
- 更新 APRS 矢量图标。

## [v1.5.5-ic705] - 2026-08-24

### Changed
- （无详细记录：即时位置信标触发修复 & V1+V2+V3 签名 & 原生地图脱壳）

## [v1.5.4-ic705] - 2026-08-24

### Added
- 新增多瓦片地图源支持，包含高德、OpenStreetMap、谷歌地图及自定义瓦片。

### Changed
- 适配 Android 12+ 动态取色。
- 设置 `android:allowBackup="false"`。
- 统一诊断页配置存储。

### Removed
- 替换旧版 `AlertDialog` 为 `MaterialAlertDialogBuilder`。
- 替换旧图标为 Vector 矢量图标。

## [v1.5.3-ic705] - 2026-08-23

### Fixed
- 修复诊断页硬编码 48kHz 导致无法解码的问题，修正为 12kHz。
- 增加 PTT 发射看门狗，超时自动释放。
- 修复并发数据竞争。
- 修复 CI 脚本路径泄露，更新 README 协作者名单。

### Removed
- 清理死代码与过时注释。

## [v1.5.2-ic705] - 2026-08-23

### Changed
- 将应用内 Dialog 替换为 `MaterialAlertDialogBuilder`。
- 规范并清理权限声明，适配 Android 12+ 蓝牙权限要求。
- 更新 README 协同模型名单。

## [v1.5.1-ic705] - 2026-08-23

### Fixed
- 修复顶栏在 AppCompat 下隐藏的问题，改用 `MaterialToolbar`。

### Changed
- 关于对话框重构为 Material 3 布局。

## [v1.5.0-ic705] - 2026-08-23

### Changed
- 使用 `RecyclerView` 和 `ListAdapter` 替换旧版 `ListActivity` 和 `SimpleCursorAdapter`。
- 引入强类型数据模型解耦 UI 与 Cursor。
- `HubActivity`、`LogActivity`、`ConversationsActivity`、`MessageActivity`、`StationActivity` 升级至 `RecyclerView`，接入进度指示器。

## [v1.4.3-ic705] - 2026-08-23

### Removed
- 删除 Scala 兼容类 `MyAsyncTask.java` 和废弃的 `scalroid` 插件。
- 移除冗余的系统版本兼容分支代码及废弃回调。
- 移除冗余的硬编码路径。

## [v1.4.2-ic705] - 2026-08-23

### Changed
- 将诊断入口移至「连接偏好设置」专属条目。
- 诊断界面重构为 Material 3，自动读取电台配置并更新数据看板样式。

## [v1.4.1-ic705] - 2026-08-23

### Added
- 增加消息与会话的删除功能，支持单条删除和全部清空。

### Removed
- 移除顶部的实验性 Wi-Fi 诊断入口。

## [v1.4.0-ic705] - 2026-08-23

### Changed
- 适配 Target API 36 (Android 16)。
- 升级至 Gradle 8.8, AGP 8.4.2, Kotlin 1.9.24。
- 将 `android.preference` 迁移至 `androidx.preference`，并接入 `MaterialToolbar`。
- 重构 Activity 切换动画为 Material 3 共享轴平滑动效。
- 默认注释字段更新为 `APRSDroid Mod`。
- 关闭混淆（`-dontobfuscate`）。

## [v1.3.4-ic705] - 2026-08-23

### Changed
- 新建独立 Activity 处理通知与隐私设置。
- 适配 Dialog 的 `WindowInsetsController`，防止崩溃。

## [v1.3.3-ic705] - 2026-08-23

### Fixed
- 修复首选项点击时 summary 无限累加的 Bug。

### Changed
- 统一首选项图标的 Material 3 浅青色圆角底板。
- 开启 Gradle 并行编译与增量编译。

## [v1.3.2-ic705] - 2026-08-23

### Added
- 在设置中新增 Google 地图自定义 API Key 输入项。

### Fixed
- 修复地图加载时误调 `finish()` 的循环关闭 Bug。
- 修复首选项中 null 字符串拼接的问题。

### Changed
- 替换旧图标为 Material 3 矢量图标。
- 优化偏好设置分组标题样式并补充缺失图标。

## [v1.3.1-ic705] - 2026-08-23

### Fixed
- 将 `AprsService` 属性改为懒加载，解决空指针崩溃。
- 修复连接首选项与定位偏好的 XML 装载与变更监听。
- 修复诊断入口路由。
- 补齐 Google 地图的生命周期回调代理。

## [v1.3.0-ic705] - 2026-08-23

### Changed
- 移除 Scala 代码，转写为 Kotlin 1.9+。
- 移除 `scalroid` 插件与 Scala 依赖，切换至 Gradle 8 构建体系。

## [v1.2.8-ic705] - 2026-08-23

### Fixed
- 补全 Android 14 前台服务所需的 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 属性声明以修复崩溃。
- 修复前台服务权限按位组合问题。
- 内部广播注册改为 `RECEIVER_NOT_EXPORTED`。
- 修复退出服务时通知栏残留问题。

## [v1.2.7-ic705] - 2026-08-23

### Fixed
- 优化网络选择器以防止热点断连竞态。
- 修复多语言包中占位符格式错误。

## [v1.2.6-ic705] - 2026-08-23

### Fixed
- 增加广播注销异常保护。
- 优化 CI 流水线签名流程，防止私钥解析异常。

## [v1.2.5-ic705] - 2026-08-23

### Added
- 新增 AI_CONTEXT.md，整理项目架构与开发规范，并更新 README。

## [v1.2.4-ic705] - 2026-08-23

### Changed
- 回滚试验性诊断页面，恢复原先的呼号排版。
- 绑定 Git 提交者身份。
- 版本元数据更新为 1.2.4-ic705。

# 更新日志 (Changelog)

所有针对 APRSdroid IC-705 (Wi-Fi Mod) 的重要更新和版本迭代记录均归档于此。

---

## [v1.2.0-ic705] - 2026-08-23

### Changed
- 呼号和报文字体更新为高对比度配色（大洋青与深色等宽字体）。
- 频率和距离配色调整。
- 修复台站详情页布局并更新底部按键样式。

## [v1.1.0-ic705] - 2026-08-23

### Changed
- 界面配色与排版适配 Material 3 动态取色。
- 设置图标统一为 40dp 胶囊底板。
- 支持 Android 13+ 动态桌面图标。
- 适配 Material 3 边距、卡片及圆角效果。
- Target SDK 升级至 API 36/37，Min SDK 提升至 24。
- 移除多余的兼容代码并新增全自动化 CI/CD Release 流水线。

### Fixed
- 修复单发位置逻辑，点击单发可立即发送已知位置。
- 允许智能信标单发越过限频限制。

---

## [v1.0.0-ic705] - 2026-08-20

### Added
- 支持通过 Wi-Fi UDP 局域网协议连接 IC-705。
- 内置 AFSK 调制解调器（12kHz 采样率）。
- 支持 CI-V PTT 自动控制。
- 支持独立的 Wi-Fi 路由设置。
- 增加应用常驻通知的一键退出按钮。