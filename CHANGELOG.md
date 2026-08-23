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

