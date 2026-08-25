## 🚀 [v1.8.3-ic705] - 2026-08-25

### 📦 核心依赖升级与 Google Maps 链路加固
* **核心依赖升级（Batch 2 Dependency Upgrades）**：
  - `androidx.appcompat:appcompat` 升级至 `1.8.0`。
  - `com.google.android.material:material` 升级至 `1.14.0`。
  - `com.squareup.okhttp3:okhttp` 升级至 `5.3.0`。
* **Google 地图链路恢复与安全回退保障**：
  - 确认在配置有效 `MAPS_API_KEY` 构建中 Google 普通地图与 Google 卫星/混合地图正常可见与交互。
  - 保持无 Key、无 Google Play 服务或网络不可达时安全回退至 OpenStreetMap / MapLibre，防止应用崩溃。
* 版本元数据更新为 `1.8.3-ic705`（`versionCode 2026082583`）。

## 🚀 [v1.8.2-ic705] - 2026-08-25

### 🎨 Material 3 全设置弹窗统一、OpenGL 专属发布与系统兼容性加固
* **全量 Material 3 偏好设置弹窗（Modern M3 Settings Dialogs）**：
  - 升级 `EditTextPreferenceWithValue` 与 `ListPreferenceWithValue`，全面支持 Material 3 视觉规范（28dp 大圆角卡片、OutlinedBox 外边框输入框、动态 Surface 色彩与 MD3 单选列表）。
  - 呼号、SSID、APRS-IS 认证密码、服务器与端口、过滤规则、位置与信标等全部设置项与中继路径（Digipath）保持统一的现代化视觉体验。
* **纯粹高效的 OpenGL 渲染发布流（OpenGL-Only Architecture）**：
  - CI/CD 与正式构建专注于轻量高兼容的 OpenGL 渲染管线（提供 `arm64-v8a` OpenGL 推荐版与 `armeabi-v7a` OpenGL 兼容版），彻底规避部分机型 Vulkan 驱动闪退。
* **华为 / 荣耀 / EMUI 前台通知与定位闪退修复**：
  - 修复前台定位服务使用多色自适应启动图标导致部分定制 ROM（如 MagicUI / EMUI）在通知栏抛出 `BadNotificationException` 闪退的问题，改用标准 24x24dp 单色白底矢量通知图标 `ic_stat_notify`，并增加服务启动异常保护。
* **全新安装首次进入地图 SQLite 语法异常修复**：
  - 修复在全新安装无历史台站数据时，打开地图执行排序查询触发 `SQLiteException: near "DESC": syntax error` 崩溃的缺陷。
* **极简顶栏与彩蛋式诊断日志**：
  - 顶栏移除常驻的调试图标，界面更加清爽均衡；支持长按顶栏标题 `APRSdroid IC-705` 1.5 秒一键生成并分享系统诊断报告。
* **全套 V1 + V2 + V3 + V4 APK 签名方案**：
  - 本地与 GitHub CI 构建全面启用 V1 (JAR) + V2 (Full APK) + V3 (Key Rotation) + V4 (Streaming) 全版本签名方案，保证 Android 8.0 ~ Android 17 设备极致安装兼容性。
* 版本元数据更新为 `1.8.2-ic705`（`versionCode 2026082582`）。

## 🚀 [v1.8.1-ic705] - 2026-08-25

### ⚡ MapLibre 启动稳定性修复、PTT 释放安全重试与广播加固
* 修复 `APRSdroidApplication` 中由于 OkHttpClient 初始化先行触发 MapLibre 上下文校验导致的冷启动崩溃（`Using MapView requires calling MapLibre.getInstance(...)`）。
* 加固 IC-705 PTT 释放状态机：引入 CI-V PTT OFF 的 ACK 握手确认、指数退避重试与硬件安全回退机制，避免弱网下意外长发。
* 适配 Android 14+ 内部广播注册规范，显式使用 `RECEIVER_NOT_EXPORTED` 消除 `SecurityException` 崩溃隐患。
* 规范化 USB TNC 权限请求与设备插拔监听，适配 Android 12+ `FLAG_IMMUTABLE` 约束。
* 版本元数据更新为 `1.8.1-ic705`（`versionCode 2026082581`）。

## 🚀 [v1.8.0-ic705] - 2026-08-24

### 📦 多架构轻量发布、OSM 合规与正式构建收缩
* 将 Release 重组为五个独立规格：推荐的 `arm64-v8a` Vulkan、ARM64 OpenGL 兼容版、`armeabi-v7a` OpenGL，以及 `x86` / `x86_64` Vulkan+OpenGL 双后端版；不再生成把全部 ABI 塞入一个文件的通用 APK。
* 不清楚设备规格时请下载文件名以 `Recommended_` 开头的 ARM64 Vulkan 包；若发布页没有所需的 ABI/渲染组合，可按 README 自行编译。
* Release 正式启用 R8 代码优化/混淆和资源裁剪，移除旧 `-dontobfuscate` 开关，并由 CI 单独保存每个变体的 `mapping.txt`；本地未签名推荐包由上一版约 40.6 MiB 降至约 18.2 MiB。
* OpenStreetMap 请求使用可识别且稳定的应用 User-Agent，地图上始终显示可点击的 `© OpenStreetMap contributors`；继续遵守服务器缓存响应，不加入预取、批量下载或 MapLibre Offline 模块。
* 保留 Google 普通地图与卫星/混合地图，但删除源码内置 Key 和实际无效的运行时 Key 输入项；正式 Key 仅从 `MAPS_API_KEY` CI Secret 注入，自行构建未配置 Key 时自动隐藏 Google 图源。
* 发布流水线会构建、签名、验签并检查全部五个 APK 的 ABI 与 MapLibre 后端数量，推荐文件以 `Recommended_` 开头，同时发布 `SHA256SUMS.txt` 和独立 R8 mapping 构建产物。
* 版本元数据更新为 `1.8.0-ic705`（`versionCode 2026082480`）；IC-705 协议、PTT/音频链、明文服务器兼容策略和最低 Android 版本均未改变。

## 🚀 [v1.7.1-ic705] - 2026-08-24

### 🗺️ MapLibre 地图引擎与 ARM64 渲染链
* 将高德、OpenStreetMap 与自定义在线瓦片从 Mapsforge 0.3.0 迁移到 MapLibre Native 13.5.1；删除旧 Mapsforge 聚合 JAR、三个专用瓦片下载器及相应 ProGuard 遗留规则。
* 使用 MapLibre Vulkan/OpenGL 多后端构建，由运行设备能力自动优先选择 Vulkan，不满足条件时回退 OpenGL；发布 APK 仅包含 `arm64-v8a` 原生库。
* 保持现有地图页面、顶栏、缩放/定位按钮、坐标选择器与图源菜单布局不变；APRS 符号、缩放后呼号标签、点击台站和多台站选择均迁移到 GeoJSON/SymbolLayer 实现。
* Google 普通地图与卫星地图继续使用 Google Maps SDK；图源切换现在严格路由到对应引擎，不再由 Google SDK 代绘高德、OSM 或自定义瓦片。
* 未加入可选的 MapLibre Offline 插件、离线包下载或离线地图管理界面；现有在线自定义瓦片 URL 与明文网络兼容策略保持不变。
* 版本元数据更新为 `1.7.1-ic705`（`versionCode 2026082471`），新增瓦片模板展开测试，并继续以 JVM 测试、Android Lint 和 Release 构建作为发布门禁。

## 🚀 [v1.6.3-ic705] - 2026-08-24

### 🧹 现代 Activity Result、存储权限与零告警治理
* 将地图坐标选择、通知铃声选择和运行时权限授权迁移到 AndroidX Activity Result launcher；在 Activity 重建时保存待处理动作，不再依赖 `startActivityForResult`、`onActivityResult` 或 `onRequestPermissionsResult`。
* 日志与配置导出改用应用专属外部 Documents 目录并继续通过 `FileProvider` 分享，删除不再需要的外部存储读写权限声明。
* 系统清理 Android Lint 的 1001 条报告：修复 KTX、字符串格式化、复数规则、无障碍描述、布局过绘制、调度器和版本恒真分支；精确保留既有兼容行为，不使用全局 Lint baseline 掩盖问题。
* 删除 86 个无运行引用的资源标识及其各语言死翻译，保留默认资源与全部 58 个 locale 目录中的活动多语言资源；统一省略号、修正已确认的拼写问题，并补全复数分支。
* IC-705 诊断、数字中继预设和关于界面的默认文案改为英文资源，简体中文使用独立翻译；清除 XML 与 Compose 中的硬编码中文。
* 版本元数据更新为 `1.6.3-ic705`（`versionCode 2026082463`）；JVM 测试、Android Lint 和 Release 构建继续作为发布门禁。

## 🚀 [v1.6.2-ic705] - 2026-08-24

### 🌐 原生 HTTP 上传与现代文档访问
* 将 HTTP POST 后端从已废弃的 Apache `DefaultHttpClient` 迁移到 Android 原生 `HttpURLConnection`，增加 15 秒连接/读取超时、固定长度 UTF-8 请求体和规范的 `Accept` 请求头，同时保持裸主机默认使用明文 `http://:8080/` 的兼容行为。
* 删除 Gradle `org.apache.http.legacy` 编译库和 Manifest `<uses-library>` 声明；新增请求 URL、Header、Body、超时、状态与连接关闭测试。
* 配置导入改用 Activity Result `OpenDocument`，通过 `ContentResolver.openInputStream()` 直接读取 `content://`；PKCS#12 和配置输入流现在均保证关闭，不再查询 `_data`、重写 DocumentsProvider URI 或拼接 `/storage` 路径。
* 删除从未注册到当前地图模式、没有运行消费者的离线地图文件和渲染主题选择入口；仅同步移除这些死入口在各语言文件中的 8 个孤立资源键，所有仍在使用的多语言界面资源完整保留。
* 版本元数据更新为 `1.6.2-ic705`（`versionCode 2026082462`）；TLS、IC-705 协议、PTT/音频链和在线地图行为均未改变。

## 🚀 [v1.6.1-ic705] - 2026-08-24

### 🧹 移除迁移遗留与 Android 兼容层化石
* 删除 Compose 页面接管后已无引用的 5 个 RecyclerView Activity/Adapter 源文件和 13 个旧 View/XML 布局，减少重复 UI 实现与资源噪声。
* 移除未参与 Gradle 构建的 Android 2.x `PacketDroid` Git 子模块；应用当前使用的根目录 AFSK/AX.25 实现保持不变。
* 清理 ProGuard 中已失效的 Scala、`AsyncTask` 与 `android.preference` 规则，以及无用 `AlertDialog` 导入。
* 依据 `minSdk 24` 移除 IC-705 Wi-Fi 网络选择器中恒真的旧 API 分支和 `@TargetApi` 标注，并删除已被现代 target SDK 忽略的 `requestLegacyExternalStorage` 属性。
* 版本元数据更新为 `1.6.1-ic705`（`versionCode 2026082461`）；TLS、Apache HTTP、Mapsforge、IC-705 协议和射频收发行为均未改动。

## 🚀 [v1.6.0-ic705] - 2026-08-24

### 📱 Android 17、现代构建链与双语文档
* 将 `compileSdk` / `targetSdk` 提升至 API 37（Android 17），为 IC-705 Wi-Fi 与局域网 TNC 增加 `ACCESS_LOCAL_NETWORK` 声明及按需运行时授权；服务启动入口现在会统一校验连接、定位和通知权限。
* 构建链升级至 Gradle 9.5.0、Android Gradle Plugin 9.3.2、内置 Kotlin 2.2.10 / Compose Compiler 2.2.10 与 Compose BOM 2026.08.00；Java 源码、字节码及 CI 继续使用官方基线 JDK 17。
* 完成 AGP 9 迁移：拆分 Java/Kotlin 源集，显式启用资源值，改用优化版默认 ProGuard 配置，升级 Gradle Play Publisher 4.0 和 AndroidX 仪器测试组件，并清理面向 Gradle 10 的旧 Groovy DSL 写法。
* 修复 Kotlin 2.2 暴露的 Activity Intent 空安全签名、权限回调结果越界风险、旧版 `PreferenceManager` 和冗余 Service 生命周期入口；新增 Android 17 局域网权限策略测试。
* 将版本元数据更新为 `1.6.0-ic705`（`versionCode 2026082460`），全面重写 README 为中英双语使用与开发指南，并同步扩充 `AI_CONTEXT.md`。
* IC-705 / APRS-IS 的现有传输协议保持不变；本版本未引入 TLS，明文服务器与电台局域网连接行为不变。

## 🚀 [v1.5.15-ic705] - 2026-08-24

### 🧹 代码质量与发布门禁
* 修复 Android Lint 报告的权限检查、版本化主题资源、文档 URI 权限、菜单属性及自定义 View 兼容性问题。
* 增加 PTT OFF 发送失败的状态机测试，并在失败时保持看门狗运行，以便自动重试安全释放发射状态。
* 发布流水线新增单元测试、Android Lint 与版本标签一致性校验；APK 版本元数据同步至 `1.5.15-ic705`。
* 移除未使用的旧版 Okio 直接依赖，更新 JUnit 4 测试依赖，并修正文档中的 SDK、音频采样率与网络绑定说明。

## 🚀 [v1.5.12-ic705] - 2026-08-24

### 🌟 视觉与交互全面升级：电台点击 BottomSheet、地图标题规范化、全新 M3 关于界面
* **电台点击气泡升级（Material 3 BottomSheet）**：
  - 点击地图（高德/OSM/Google Maps）上的任意电台图标时，唤起现代化的 **Material 3 底部抽屉（BottomSheet）**；
  - 动态展示高清矢量电台图标、呼号、距离方位、语音频率胶囊、精准经纬度坐标与发射内容；
  - 提供快捷入口：💬 **发消息**（直接进入点对点聊天）、📋 **查看轨迹**（单站详情）、🧭 **外部导航**。
* **地图界面标题规范化**：
  - 移除了顶栏生硬的“高德地图”字样，规范统一为主功能标题 **“APRS 地图”**，图源属性回归底层。
* **“关于”界面全新重构（Material 3 Compose）**：
  - 彻底淘汰原版古董纯文本弹窗；
  - 全新设计 Modern Squircle 应用图标、版本标识、定制核心功能亮点介绍（IC-705 Wi-Fi 直连/Compose M3/多源地图/挂号信短消息机制）、开源许可证及 GitHub 开源主页一键跳转。

## 🚀 [v1.5.11-ic705] - 2026-08-24

### 🌟 Phase 3 核心交互与体验升级：日志监控 Compose 化、地图控件 M3 重构与消息状态语义精准化
* **报文日志监控页（`LogActivity`）全量 Compose 重构**：
  - 全新 `LogScreen` 声明式流式报文界面；
  - 动态分类徽标色彩分流（`TX 发射`、`RX 接收`、`ERROR 异常`、`INFO 系统`）；
  - 支持呼号/内容实时搜索与多类型（全部/RX/TX/异常）FilterChip 快速过滤；
  - 支持单条长按一键复制原始报文到剪贴板，保留导出日志与清空日志功能。
* **地图界面现代化重构（告别 Android 2.x 古董黑灰缩放条）**：
  - 彻底停用并移除 Android 2.x 时代内置 `ZoomButtonsController`；
  - 右侧悬浮构建 Material 3 垂直药丸式卡片控制器（`➕ 放大` / `➖ 缩小`），支持平滑缩放；
  - 新增 Material 3 `🎯 回到我的位置` 悬浮卡片按钮，一键快速平移居中并聚焦本机坐标。
* **短消息传输状态精准化（彻底消除重试误解）**：
  - 优化状态文案语义：`0/7 排队待发` ➔ `1/7 📡 已发射，等待对方应答` ➔ `✓✓ 对方已确认 (ACK)` ➔ `⊘ 对方无应答 (已中止)`，完美契合 APRS 双向回执信函机制。

## 🚀 [v1.5.10-ic705] - 2026-08-24

### 🛠️ 导航回退栈与主界面菜单弹出定位修复
* **修复返回键直接退出 App 的问题**：从主界面进入地图页和日志页时保留 BackStack 导航回退栈，按返回键精准退回到主界面（HubActivity）。
* **修复顶部三点溢出菜单弹出锚点偏差**：修正 `DropdownMenu` 在 `TopAppBar` `actions` 中的父级 `Box` 锚定关系，菜单紧贴右上角三点按钮正下方优雅弹出。

## 🚀 [v1.5.9-ic705] - 2026-08-24

### 📱 主界面台站列表 & 消息会话总览全面 Compose 化 (Phase 2)
* **台站主页（`HubActivity`）全量 Compose 重构**：
  - 迁移至声明式 `HubStationScreen`：
    - 置顶本机卡片 M3 主色高亮与圆角裁剪，彻底杜绝传统背景切角 Bug；
    - 列表项无缝集成 `SymbolBadge` 高清矢量电台图标；
    - 智能集成语音频率 Chip（`📻 439.500 MHz`）、距离、方位角与时间戳；
    - 现代化沉浸式底栏：发信位置（单次）+ 开始/停止记录路径双大按钮。
* **消息会话总览页（`ConversationsActivity`）Compose 重构**：
  - 迁移至 `ConversationsScreen`，支持电台图标展示、最近一条消息摘要、删除会话确认与新建会话 FAB 弹窗。

## 🚀 [v1.5.8-ic705] - 2026-08-24

### 🎨 引入 Jetpack Compose 架构 & Material 3 现代化短消息会话
* **Jetpack Compose 现代化架构落地**：
  - 引入 Jetpack Compose 2024.05.00 (BOM) + Compose Material 3 框架；
  - 建立全局 `AprsTheme`，原生支持 Android 12+ Monet 壁纸动态取色（`dynamicLightColorScheme` / `dynamicDarkColorScheme`）；
  - 封装 Compose 声明式高清 APRS 矢量图标组件 `SymbolBadge`。
* **短消息聊天页（`MessageActivity`）全量 Compose 重构**：
  - 重构为 `MessageChatScreen` 声明式聊天界面：
    - 现代 Material 3 左右气泡布局与 Monospace 字体排版；
    - 即时发送状态指示：`⏳ 发送中 (0/7)`、`✓✓ 已送达 (ACK)`、`✕ 对方拒绝 (REJ)`、`⊘ 发送失败/已中止`；
    - 底部沉浸式 Material 3 圆角输入栏与发送交互；
    - 长按消息快捷弹窗操作（复制文本、重发、中止、单条删除）。
* **CI 构建流水线优化**：
  - GitHub Actions 增加 `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24`，消除 Node.js 20 运行时弃用警告。

## 🚀 [v1.5.7-ic705] - 2026-08-24

### 🗺️ 地图偏好设置动态联动 & 自定义瓦片源精简
* **地图偏好设置项智能动态联动**：
  - 仅在选择「自定义瓦片源」时，才动态显示自定义瓦片 URL 与负载均衡子域配置项；
  - 仅在选择「谷歌地图 / 卫星地图」时，才动态显示 Google Maps API Key 配置项；
  - 选择「高德地图」或「OpenStreetMap」时，界面保持清爽整洁，隐藏不相关的输入项。
* **自定义瓦片默认值精简**：
  - 清空自定义瓦片中的冗余高德默认地址与旧配置，避免与内置原生高德地图混淆。

## 🚀 [v1.5.6-ic705] - 2026-08-24

### 💬 消息发送状态与即时上屏 & 卡片圆角修复 & 高清矢量图标徽章
* **短消息即时发送状态可视化**：
  - 聊天界面全面升级发送状态反馈：支持 `⏳ 发送中 (重试 0/7)`、`✓✓ 已送达 (ACK)`、`✕ 对方拒绝 (REJ)`、`⊘ 发送失败/已中止`、`📥 来自 [呼号]`，直观掌握报文下发与对方确认情况；
  - 修复发送消息后未触发本地数据重载导致必须退出重进的 Bug，实现 0ms 立即上屏与自动滚动到底部。
* **卡片圆角背景切角修复**：
  - 修复「本机呼号」及高亮卡片背景直角矩形溢出遮挡卡片圆角的视觉 Bug，改用 `MaterialCardView` 纯正 M3 动态取色圆角裁剪与高亮外边框。
* **现代重绘高清 APRS 电台图标**：
  - 升级为 2048x2304 Ultra HD 矢量高清图集，告别粗糙像素；
  - 引入随系统壁纸动态取色的 Material 3 圆角微卡片徽章容器。

## 🚀 [v1.5.5-ic705] - 2026-08-24

### 📡 即时位置信标触发修复 & V1+V2+V3 签名 & 原生地图脱壳

## 🚀 [v1.5.4-ic705] - 2026-08-24

### 🗺️ 多瓦片地图源支持 & 真·莫奈动态取色 & 安全加固与代码清理
* **多瓦片地图源支持 (高德 / OSM / 谷歌 / 自定义)**：
  - 新增 `OnlineTileProvider` 瓦片服务，支持标准 `{x}`, `{y}`, `{z}`, `{s}` 模板与分流子域解析；
  - 内置支持**高德地图**瓦片 (`webrd0{s}.is.autonavi.com`)、**OpenStreetMap**、**谷歌标准**、**谷歌卫星**及**自定义瓦片源**；
  - 地图界面右上角「图层」菜单与偏好设置页支持即时自由切换地图源，并可自定义瓦片 URL 与负载均衡子域。
* **真·莫奈 (Material You / Dynamic Colors) 动态取色重构**：
  - 移除 `themes.xml` 中写死的覆盖色，适配 Android 12+ 随系统壁纸动态变色的 Monet 调色板；
  - 全量适配器（日志、会话、消息、台站）改用 `MaterialColors` 动态解析主题主色、次色与状态高亮，彻底消除写死 Hex 颜色。
* **安全配置与凭据冗余清理**：
  - 设置 `android:allowBackup="false"`，封堵系统与 ADB 备份导出凭据风险；
  - 移除诊断页独立的二次 SharedPreferences 存储，统一使用主配置。
* **古代残留全面清理**：
  - 迁移 `MapAct.kt` 遗留的 `AlertDialog.Builder` 至 `MaterialAlertDialogBuilder`；
  - 全面清退 `@android:drawable/ic_menu_*` Holo 图标，改用现代 Vector 矢量图标；
  - 规范所有菜单文件的 `app:showAsAction` 命名空间；
  - 清理 `UIHelper.kt` 未引用旧 API import 及 `themes.xml` 无用旧 ActionBar 样式。

## 🚀 [v1.5.3-ic705] - 2026-08-23

### 🔧 代码审查修复与安全加固
* **[H1] 诊断页采样率修复**：
  - 修复 `Ic705RxDiagnosticActivity` 中硬编码 48kHz 与 `Ic705RxAudioReceiver` 要求 12kHz 不一致导致诊断页永远无法解码音频的 Bug；
  - 修正 `Afsk1200PcmGenerator` 类注释（48kHz → 12kHz），与实际默认值一致。
* **[H2] PTT 发射看门狗（Watchdog）实现**：
  - 在 `Ic705PttStateMachine` 中实现绝对超时看门狗：PTT ON 后启动 5s 定时器，超时自动发 PTT OFF 强制释放，防止 Wi-Fi 丢包导致电台无限发射（频率占用/法规风险）；
  - 补全 `handleAck()` 诊断日志记录。
* **[M1] 并发安全修复**：
  - 将 `channelRuntimes` 与 `channels`（EnumMap）包装为 `Collections.synchronizedMap`，消除 controlExecutor 写线程与 transmit/txExecutor 读线程之间的数据竞争。
* **[M4] 死代码清理**：
  - 移除未使用的 `Ic705RxSession.pendingTxDatagrams` 字段；
  - 移除 `libs.versions.toml` 中残留的 `scala-library` / Scala 2.11.12 条目。
* **[L5] 过时注释修正**：
  - 更新 `Ic705WifiBackendController` 类注释（"RX-only" → 反映完整的 TX+RX 全双工能力）。
* **[L6/L7] 工程文件清理**：
  - 修复 `push_to_github.ps1` 中泄漏的开发者绝对路径（改用 `$PSScriptRoot`）；
  - 删除已停服的 `.travis.yml`（CI 已迁移至 GitHub Actions）。
* **文档修正**：
  - 修正 `AI_CONTEXT.md` 中过时的版本号（Gradle 8.4→8.8, AGP 8.1.3→8.4.2, targetSdk 36/37→36, Kotlin 1.9.20+→1.9.24）。

## 🚀 [v1.5.2-ic705] - 2026-08-23

### 🛡️ 全局对话框 Material 3 确认按钮补全 & 过时权限清理 & 文档协作模型更新
* **全量 Dialog 适配 MaterialAlertDialogBuilder**：
  - 彻底将应用内所有 `android.app.AlertDialog` 迁移为 `MaterialAlertDialogBuilder` 与 `androidx.appcompat.app.AlertDialog`；
  - 修复「删除会话」、「清空所有消息」、「清空日志」、「新建消息」及「证书导入」等弹窗在 AppCompat 下确认/取消按钮不显示的问题，全面提供圆角 M3 弹窗与醒目操作按钮。
* **清理 Android 权限声明**：
  - 移除已废弃且存在安全隐患的 `BROADCAST_STICKY` 权限；
  - 规范声明 Android 12+ 运行时权限 `BLUETOOTH_CONNECT` 与 `BLUETOOTH_SCAN`（`neverForLocation`），并将旧版 `BLUETOOTH` / `BLUETOOTH_ADMIN` 限制在 `maxSdkVersion="30"`；
  - 规范 `READ_EXTERNAL_STORAGE`（`maxSdkVersion="32"`）与 `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion="28"`）。
* **README 协作模型更新**：
  - README 协同模型名单新增 **Claude Opus 4.6**。

## 🚀 [v1.5.1-ic705] - 2026-08-23

### 🎨 顶栏渲染修复与现代化「关于」对话框重构
* **顶栏双重标题栏修复**：
  - 彻底切换至 `Theme.Material3.DayNight.NoActionBar` + `MaterialToolbar` 标准架构；
  - 修复顶栏图标在 AppCompat 下隐藏的问题，恢复显示报文日志、折叠地图、聊天会话等快捷图标。
* **现代化「关于」卡片弹窗（AboutDialog）**：
  - 采用嵌套可滚动 Material 3 弹窗布局，完整展示版本号、原作者版权（DO1GL）、GNU GPLv2 许可证法律声明、致谢与翻译者名单；
  - 新增醒目的 Material 3 全宽主色胶囊「确定」确认按钮，并支持所有外链直接点击跳转。

## 🚀 [v1.5.0-ic705] - 2026-08-23

### ⚡ 核心列表架构全量现代化：RecyclerView + ListAdapter + DiffUtil 重构
* **彻底淘汰废弃基类**：
  - 彻底移除 `android.app.ListActivity`（API 30 废弃）与 `android.widget.SimpleCursorAdapter`（API 11 废弃）；
  - 新建现代抽象基类 `BaseRecyclerActivity` 与 `MainRecyclerActivity`，全面继承自 `AppCompatActivity`。
* **引入强类型不可变数据模型**：
  - 新增 `StationItem`、`LogPostItem`、`ConversationItem`、`MessageItem` 数据类，彻底解耦 UI 线程与 SQLite Cursor，杜绝主线程阻塞。
* **4 大适配器全面现代化**：
  - `StationRecyclerAdapter`：基于 `ListAdapter` + `DiffUtil`，台站列表支持局部高帧率差量刷新与 ViewHolder 缓存；
  - `LogRecyclerAdapter`：报文日志流式渲染，支持 120Hz 高刷屏平滑滚动与语法高亮；
  - `ConversationRecyclerAdapter`：会话列表现代化卡片渲染与长按删除手势；
  - `MessageRecyclerAdapter`：消息气泡复用优化，发送/接收自动平滑滚动触底。
* **5 大核心页面全量升级**：
  - `HubActivity`（电台列表）、`LogActivity`（报文日志）、`ConversationsActivity`（消息会话）、`MessageActivity`（聊天详情）、`StationActivity`（台站详情）全量升级至 `RecyclerView`；
  - 接入 Material 3 `LinearProgressIndicator` 现代顶部线性加载指示器与空状态占位图。

## 🧹 [v1.4.3-ic705] - 2026-08-23

### 🏛️ 全面清理历史包袱与“岁月史书” (Legacy Cleanup)
* **清除 Scala 时代残留垫片**：彻底删除无用的 `MyAsyncTask.java` 历史兼容类；
* **清理废弃构建插件**：删除早已停用的 `build-logic/scalroid` 遗留目录（42 个废弃 Scala-AGP 插件源码文件）；
* **移除远古系统版本分支判断**：
  - `UIHelper.kt`：剔除 `< KITKAT` (Android 4.4) 冗余分支，使用现代文档存储路径；
  - `PermissionHelper.kt`：剔除 `< M` (Android 6.0) 兼容分支，采用现代化 Kotlin 集合过滤权限；
  - `KenwoodProto.kt`：移除 2009 年 Android 2.0 (API 5) 的 `NmeaListenerR5` 与废弃回调，全面使用标准的 `OnNmeaMessageListener`；
  - `BluetoothTnc.kt`：使用 `BluetoothManager` 获取适配器，消除 `BluetoothAdapter.getDefaultAdapter()` 废弃 API 调用；
  - `ServiceNotifier.kt`：现代化震动与通知逻辑，适配 Android 12+ `VibratorManager` 与 `VibrationEffect`；
  - `AudioBufferProcessor.java`：清理 2011 年硬编码 `/sdcard/` 调试文件路径及模板冗余注释；
  - 合并并移除冗余根目录 `ChangeLog` 文件。

## 📡 [v1.4.2-ic705] - 2026-08-23

### 🎨 IC-705 Wi-Fi 诊断全量现代化与设置深度集成
* **诊断入口归位至连接偏好设置**：彻底移除顶部主菜单的三点诊断入口，将其规范集成于「偏好设置 ➔ 连接偏好设置 (IC-705 Wi-Fi)」专属条目中，符合 Android 原生设置逻辑。
* **诊断界面 Material 3 全量重构**：
  - **MaterialToolbar & 返回导航**：标准浅色 M3 顶栏与原生返回手势；
  - **自动带入电台配置**：自动读取用户在偏好设置中保存的 IP、端口与密码，无需繁琐重复输入；
  - **M3 现代数据看板**：音频块数、PCM 采样点数、已解码 AX.25 帧数与重置计数均采用高对比度圆角卡片化排版；
  - **优雅事件流终端**：等宽字体与圆角底衬，清晰展现 IC-705 UDP 握手、PCM 音频流及 AX.25 解码实时生命周期。

## 🧹 [v1.4.1-ic705] - 2026-08-23

### 🗑️ 彻底移除 Wi-Fi 试验性诊断入口 & 全面支持消息会话/单条消息删除与清空
* **彻底清除废弃诊断入口**：彻底移除冗余且过时的「IC-705 Wi-Fi 诊断」页面与全部菜单项，保持应用菜单纯粹清爽。
* **消息与会话删除全覆盖**：
  - **聊天界面 3 点菜单**：支持点击「清除消息」，二次确认后一键清空与当前呼号的所有历史消息；
  - **单条消息长按**：支持长按任一单条收/发报文弹出「删除此消息」；
  - **会话列表 3 点菜单**：新增「清空所有消息」入口；
  - **会话列表长按**：支持长按任意会话条目弹出「删除会话」；
  - **数据库底层支持**：新增 `deleteMessage(id)` 与 `deleteAllMessages()` 高效删除方法。

## 🚀 [v1.4.0-ic705] - 2026-08-23

### 💎 AndroidX Preference 全量现代化、构建链升级 (Gradle 8.8) 与丝滑 M3 共享轴转场动效
* **Target API 36 (Android 16) 全面适配**：适配最新 Android 平台标准，提升系统级手势响应与安全性。
* **构建链平滑升级**：升级构建系统至 **Gradle 8.8 + AGP 8.4.2 + Kotlin 1.9.24**，大幅提升增量编译与字节码生成效率。
* **AndroidX Preference 深度重构**：
  - 彻底淘汰已废弃的 `android.preference` 架构，全量迁移至 `androidx.preference:1.2.1`；
  - 接入标准 `MaterialToolbar` 容器，彻底消除旧版双 Action Bar 冲突与顶部条目被遮挡的隐患；
  - 修复「连接首选项」、「位置设置」、「APRS 符号」、「位置隐私」、「通知」点击秒级流畅跳转。
* **转场动效全面现代化（告别生硬缩放）**：重构 Activity 切换动画为 **Material 3 共享轴（Shared-Axis X）** 平滑动效（280ms `fast_out_slow_in`），呈现如原生 Pixel / Android 14+ 设置般的丝滑操作手感。
* **注释字段历史数据自动平滑迁移**：启动时自动将用户持久化存储中的旧版默认网址迁移更新为 `APRSDroid Mod`。
* **溢出菜单与弹窗优化**：精细化调整右上角三点菜单背景与紧凑边距，去除多余留白。

## 🎨 [v1.3.4-ic705] - 2026-08-23

### 🛠️ 现代化子设置界面、Dialog 适配与默认信标注释更新
* **默认注释更新**：将默认状态注释字段由官方网页链接全面更新为 `APRSDroid Mod`。
* **独立通知与隐私设置 Activity**：新建 `NotificationPrefs` 与 `PrivacyPrefs` 专属设置 Activity，彻底消除 Android 原生内嵌子 PreferenceScreen 无顶栏和背景底色偏紫偏粉的失真问题，统一浅色 Material 3 背景与返回导航栏。
* **系统 Dialog 弹窗适配与防崩溃**：适配 `Material3PlatformDialogTheme`，并对 `EditTextPreference` / `ListPreference` 捕获 Android 14+ 系统的 `WindowInsetsController` 空指针崩溃隐患，确保呼号、SSID、中继路径等弹窗输入框与按钮完整渲染。
* **关闭防反编译混淆（保留开源透明与性能）**：Release 构建配置 `-dontobfuscate` 并禁用 minify 混淆，保持 100% 原始源码结构与极速编译。

## ⚡ [v1.3.3-ic705] - 2026-08-23

### 🚀 多线程编译优化与首选项点击无限重复 Bug 彻底修复
* **首选项点击重复累加根治**：修复 `EditTextPreferenceWithValue`、`ListPreferenceWithValue` 与 `PreferenceWithValue` 在多次重新绑定/点击时，动态获取上一轮 summary 并造成 `192.168.1.143: 192.168.1.143: ...` 无限累加的严重 Bug，改为在构造期固化初始 summary，彻底根绝重复与排版崩溃。
* **首选项图标统一容器与边距对齐**：为所有新生成的矢量图标（网络、端口、用户、密码、GPS、定时器等）统一封装 40x40 dp Material 3 浅青色圆角方块（Squircle）底板，提供 8dp 内边距与标准左侧外边距，彻底告别“图标太靠左”、“裸图标无底衬”的不协调感。
* **多线程与多核增量构建提速**：在 `gradle.properties` 中开启 `org.gradle.parallel=true`、`kotlin.incremental=true`、增至 4GB 堆内存并启用多核并发 GC，大幅提升本地调试与打包推送速度。

## 🎨 [v1.3.2-ic705] - 2026-08-23

### ✨ 地图模式修复与全套 Material 3 现代图标与偏好设置美化
* **地图加载修复**：修复 `MapMenuHelper` 与 `GoogleMapAct` 中 `setMapMode()` 导致自身误调 `finish()` 的循环关闭 Bug，地图视图现可秒级正常开启并渲染 Google Map / Mapsforge。
* **Google 地图自定义 API Key**：在偏好设置 -> 显示设置中新增「Google 地图 API Key」输入项，支持用户填入自有 API Key。
* **顶部动作栏与上下文菜单现代化**：用全新的 Material 3 矢量图标（信息、地图、发送、清除、图层、日历等）全面替换 Android 2.x 时代的旧拟物图标。
* **偏好设置视觉与对比度增强**：为分组标题（`PreferenceCategory`）提供深青色（`#006874`）加粗高对比度样式，并修复点击后布局折叠紧凑的问题。
* **全量首选项图标补充**：为连接设置、协议配置（IC-705 Wi-Fi、APRS-IS、蓝牙、USB 等）、定位源配置、通知设置、隐私设置中所有缺失图标的条目补齐了专属矢量图标。
* **首选项 Summary 优化**：修复 `EditTextPreferenceWithValue` 在 summary 为空时拼接 `"null: "` 的问题，并对 IC-705 密码等字段进行 `••••••••` 安全脱敏掩码。

## 🚑 [v1.3.1-ic705] - 2026-08-23

### 🐛 修复纯 Kotlin 迁移后首发页面与服务启动崩溃
* **路径记录启动崩溃修复**：将 `AprsService` 的 `prefs` 等关键属性从类构造期立即初始化改为 `by lazy` 懒加载，彻底消除在 Android 服务上下文未挂载前调用 `getPackageName()` 导致的 `NullPointerException` 崩溃。
* **连接首选项与定位偏好修复**：补全 `BackendPrefs` 与 `LocationPrefs` 的基础 XML、协议专属 XML 动态装载及 `OnSharedPreferenceChangeListener` 变更监听，恢复所有连接方式与定位源的设置。
* **IC-705 Wi-Fi 诊断入口修复**：在 `HubActivity` 与 `LogActivity` 菜单项分发中接入 `Ic705RxDiagnosticActivity` 页面路由。
* **地图生命周期修复**：在 `GoogleMapAct` 中补齐 `onStart()` 与 `onStop()` 代理分发，确保 Google Map 与 Mapsforge 矢量地图正常初始化渲染。

## 🚀 [v1.3.0-ic705] - 2026-08-23

### ⚡ 全量架构重构：Scala 2.11 迁移至纯 Kotlin 1.9+ & 彻底解绑构建系统
* **Scala 代码 100% 清零**：全工程全部 59 个历史 `.scala` 源码文件全量转写为现代规范的 Kotlin 1.9+ (`.kt`) 代码，无任何 Scala 残留。
* **构建系统彻底解绑**：彻底移除限制 Gradle 升级的 `scalroid` 复合插件与 `scala-library:2.11.12` 运行时依赖，构建系统完全转变为现代标准 Gradle 8 + Android Gradle Plugin + Kotlin 编译体系。
* **通信协议与核心业务零回退**：完整保留 IC-705 Wi-Fi、APRS-IS (TCP/SSL)、KISS、TNC2、Kenwood、AFSK、USB/蓝牙 TNC、SmartBeaconing 智能信标与地图交互等全部业务逻辑。
* **打包产物体积精简**：移除 Scala 标准库后，Release 安装包体积进一步缩减，冷启动与运行时内存性能显著提升。

## 🐛 [v1.2.8-ic705] - 2026-08-23

### 🐛 修复重大崩溃与前台保活 Bug
* **Android 14 FGS 崩溃修复**：在 `AndroidManifest.xml` 中补全了 `specialUse` 前台服务必须的 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 属性声明，彻底解决 Android 14+ 下无权限启动时的 `SecurityException` 崩溃问题。
* **前台服务类型覆盖修复**：在 `ServiceNotifier` 中将服务类型修改为按位或运算，确保在拥有定位或麦克风权限时，不会丢失关键的 `specialUse` 标记，从而保证网络后台保活不受影响。
* **内部广播安全性修复**：在 `MainListActivity` 中，将内部生命周期广播的注册由 `Context.RECEIVER_EXPORTED` 改为 `Context.RECEIVER_NOT_EXPORTED`，防止被外部应用恶意干扰。
* **退出功能通知栏残留修复**：在 `AprsService` 的 `handleStart` 方法中，接收到 `SERVICE_STOP` 时，主动调用 `ServiceNotifier.instance.stop(this)` 撤销前台通知栏，解决 `stopSelf()` 无法立刻触发 `onDestroy()` 导致的通知栏残留问题。

## 🌟 [v1.2.7-ic705] - 2026-08-23

### 🛡️ 全面消除已知源码漏洞与多语言格式化隐患
* **现代网络选择器**：升级 `Ic705WifiNetworkSelector`，在 Android 10+ 上优先使用 `activeNetwork` 探测并增加全局 `runCatching` 异常捕获，彻底避免无网热点断连竞态；
* **多语言占位符修复**：修复所有语言包（包含中文、德语、法语、韩语等 59 个语言文件）中 `bt_connecting_to_channel` 的多重 `%s` 非位置占位符问题，消除所有编译期资源警告。

## 🌟 [v1.2.6-ic705] - 2026-08-23

### 🛡️ Android 14+ 前台服务与生命周期安全防护 & CI 签名环境优化
* **前台服务权限按位组合**：修复 `ServiceNotifier` 中定位与麦克风前台服务类型（FGS Types）互斥导致的 Android 14+ 后台 GPS 定位隐患，支持多重权限按位组合；
* **广播安全保护**：为 `MainListActivity` 的接收器注销调用添加异常拦截保护；
* **CI 签名流水线增强**：使用环境变量严格隔离 GitHub Secrets 私钥与密码传递，注入 `tr -d '\r\n'` 彻底杜绝 Base64 换行解码异常，并在 CI 构建中加入 `apksigner verify --verbose` 自动签名校验。

## 🌟 [v1.2.5-ic705] - 2026-08-23

### 🤖 交付 AI 协同开发交接文档 (AI Handover Context)
* **新增 AI 提示词与交接指南**：创建 [AI_CONTEXT.md](AI_CONTEXT.md)，系统整理了项目架构、IC-705 协议核心、Material 3 UI 规范、本地与 CI 编译签名流程及版本递增铁律，便于任何 AI 助手无缝接手；
* **文档与链接集成**：在 `README.md` 中集成交接文档入口。

## 🌟 [v1.2.4-ic705] - 2026-08-23

### 🛡️ 回滚至高可用稳定架构 & 绑定个人提交者身份
* **稳定性优先回滚**：彻底撤回试验性诊断页面调整，恢复呼号高对比度莫奈青、台站信息流排版等完整稳定能力；
* **提交身份绑定**：配置项目与全局 Git 提交身份为 `nimenhagg <ldsjljq@gmail.com>`；
* **版本号统一迭代**：按规则推进至 `v1.2.4-ic705`。

# 📋 更新日志 (Changelog)

所有针对 **APRSdroid IC-705 (Wi-Fi Mod)** 的重要更新和版本迭代记录均归档于此。

---

## 🌟 [v1.2.0-ic705] - 2026-08-23

### 💎 呼号与报文字体排版全面重构（高对比度 Material 3）
* **呼号专属配色**：友台与本台呼号（Callsign）全面升级为加粗大洋青（Oceanic Teal `#00677D`），彻底移除 2009 年遗留的荧光绿/暗紫配色；
* **报文与遥测数据**：报文载荷（IMEI/RSSI/电压/里程/卫星数）统一采用高对比度深色等宽字体（`#191C1E`），在浅色卡片上极具辨识度；
* **频率与距离**：频率（QRG）采用高亮青色（`#006874`），距离方位采用中灰（`#40484C`）；
* **台站详情页布局修复**：修复 StationActivity 顶部卡片被截断及大片空白问题，底部「地图 / APRS.FI / QRZ.COM」升级为 48dp 莫奈大圆角胶囊按键。

## 🌟 [v1.1.0-ic705] - 2026-08-23

### 🎨 Material 3 视觉与动效全面重构
* **呼号与报文字体排版全面重构（高对比度 Material 3）**：
  - 彻底重构了 2009 年遗留的荧光绿/暗紫配色；
  - 友台与本台呼号（Callsign）全面采用 **加粗大洋青（Oceanic Teal #00677D）** 强化可读性；
  - 报文内容、遥测数据（IMEI/RSSI/电压/里程/卫星数）与聊天记录统一采用高对比度深色等宽字体（#191C1E），在 Material 3 浅色卡片上极具辨识度；
  - 频率（QRG）采用高亮青色（#006874），距离方位采用中灰（#40484C）。
* **40dp Tonal 胶囊微容器图标**：重构了全套设置项前置图标（Leading Icons），采用 Google Pixel 系统同款的 40dp 莫奈色圆角微容器（`@color/md3_primary_container`），彻底消除原生 Preference 界面图标偏左偏小、空隙突兀的排版问题。
* **Android 13+ 莫奈动态桌面图标**：新增全矢量自适应图标（Adaptive Icon）及单色遮罩（Monochrome Themed Icon），应用图标可跟随 Android 13/14/15/16/17 系统壁纸主题自动变色。
* **MD3 Expressive 卡片与交互**：
  - 呼号卡片、消息气泡升级为 16dp 莫奈圆角与精致轮廓边框；
  - 底部操作栏升级为 52dp 双色大胶囊 Filled / Tonal 按钮；
  - 接入 Android 14~17 预测式返回手势（`enableOnBackInvokedCallback="true"`）与 FastOutSlowIn 页面平滑动效。
* **全面屏手势小白条沉浸式（Edge-to-Edge Insets）**：
  - 状态栏与手势导航栏完全透明沉浸；
  - 底部控制栏与聊天输入框接入系统 Window Insets 自动避让，绝不遮挡底部操作按键与输入法。

### ⚡ 信标发射核心逻辑修复（解决“发射叛逆/不发”Bug）
* **即时单发位置秒级兜底**：修复了原版点击「单次发射」时傻等 GPS 回调的缺陷。点击单发立即提取系统最近缓存的 GPS/网络已知位置（Last Known Location）瞬间编码并触发电台发射。
* **穿透限频拦截**：在 SmartBeaconing（智能信标）与 PeriodicGPS（周期性定位）中注入单发特权标识，100% 绕过静止状态下 1200 秒（20分钟）的限频和距离过滤，彻底解决“点发射不响”的问题。

### 🏗️ 构建系统与工程架构升级
* **升级 Target/Compile SDK**：适配至最新 Android 16/17（API 36/37），提升运行时容器性能。
* **提升 Min SDK 至 24（Android 7.0+）**：剔除陈旧的 MultiDex 与兼容垫片，释放 Java 17 / Scala 2.13 现代语言运行时优化。
* **去除坏死软链接（Symlinks）**：将 `AudioBufferProcessor.java` 与 `PacketCallback.java` 转为独立源码文件，完美支持 GitHub Actions Linux 云端直接拉取构建。
* **全自动化 CI/CD Release 流水线**：新增 `.github/workflows/release.yml`，升级为 `actions/setup-java@v5`，推送 Tag 或 master 分支全自动云端打包并发布 Release。

---

## 📻 [v1.0.0-ic705] - 2026-08-20

### 初始功能发布 (Initial IC-705 Wi-Fi Mod Release)
* **IC-705 Wi-Fi 直连通信**：原生支持通过 Wi-Fi UDP 局域网协议连接 Icom IC-705，实现免音频线、免外接 TNC 纯无线 APRS 报文收发。
* **内置软件 AFSK 调制解调器**：基于 12kHz 采样率与 20ms 分包节拍优化，彻底杜绝无线抖动丢包与空载波。
* **CI-V 自动控制**：实现 UDP 协议上的 PTT 自动置位与复位。
* **独立 Wi-Fi 路由**：蜂窝流量与电台通信互不干扰。
* **常驻通知优化**：增加一键「完全退出」按钮，移除开机自动拉起。

