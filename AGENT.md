# APRSdroid Mod：Agent 工程维护规范

> 本文件是仓库中面向维护者与 AI/Agent 编程助手的**唯一当前维护规范正文**。若本文与代码、测试、Gradle 配置或 CI 行为冲突，以代码与可复现验证为准，并在同一次变更中修正文档。
>
> `AI_CONTEXT.md` 只保留兼容入口；后续只维护 `AGENT.md`，不得维护两份独立正文。

## 1. 版本基线与当前 main

| 项目 | 当前值 |
| --- | --- |
| 最新 GitHub Release | `Mod-v2.2.4` |
| `build.gradle` 默认版本 | `2.2.4` |
| Android versionCode | `2026091301` |
| 上游历史基线 | APRSdroid `v1.7.0` |
| Android | `minSdk 27`，`compileSdk 37`，`targetSdk 37` |
| 构建链 | Gradle `9.5.0`，AGP `9.3.2` |
| Kotlin / Compose Compiler | AGP 9 built-in Kotlin `2.3.21` / Compose Compiler `2.3.21` |
| Java | `17` |
| 核心库 | Material `1.14.0`，OkHttp `5.3.0`，Browser `1.10.0`，Core-KTX `1.19.0`，Activity Compose `1.13.0`，Lifecycle runtime-compose `2.11.0`，Navigation Compose `2.10.0` |
| AFSK1200 RX | Graywolf `graywolf-demod 0.14.13`，固定 upstream commit `34cd0111b7a40e7d91607699b7b4dd188574970a` |
| 地图 | MapLibre Native `13.5.1` + Google Maps SDK |
| 应用 ID | `me.nimenhagg.aprsdroidic705mod` |
| UI | Jetpack Compose + Material 3；生产页面无 `res/layout` XML |

`Mod-v2.2.4` 是当前发布基线，main 与该版本一致：在 2.2.3 的消息/ACK/PTT 修复基础上，优化接收触发的地图与列表刷新，并新增台站呼号/备注搜索。性能修复使用不可变地图快照、稳定图标缓存、后台位图/GeoJSON、可见页面查询与合并队列；搜索在 SQL LIMIT 之前筛选，不扩大默认结果数量。APRS-IS 文本、ACK/PTT 和 Graywolf 路径保持原有语义。legacy LoTW/实验性 TLS trust 行为仍保持兼容，直到真实 24580 握手能证明严格 CA/hostname 验证可用。后续 main 若领先最新 tag / GitHub Release，仍必须区分已发布与未发布能力。

README 必须始终区分 **Latest release** 与 **Current main**。未打 tag 的功能、修复和行为变化不得提前写成稳定版能力。

## 2. 项目目标与边界

本 Fork 在 APRSdroid 基础上增加 Icom IC-705 Wi-Fi 直连 APRS 收发，并保留 APRS-IS、AFSK、KISS、TNC2、Kenwood、蓝牙、USB、LAN TCP TNC 等原有路径。

核心目标：

- 手机与 IC-705 通过 WLAN UDP 会话通信，不依赖音频线或外接 TNC。
- IC-705 UDP Socket 单独绑定 Android 选定的 Wi-Fi `Network`；APRS-IS 等互联网流量继续使用系统默认互联网路径。
- 使用 AX.25 + AFSK1200 + 12 kHz 单声道 PCM 进行收发，以 CI-V 控制 PTT。
- 在厂商 Wi-Fi 栈、线程调度和网络切换有差异时，通过角色化 liveness、局部恢复和完整 reconnect 尽量维持 session。
- 保留 Compose Material 3 UI、消息、日志、台站、地图、定位和诊断能力。

明确边界：

- IC-705 LAN 协议和部分 APRS 服务本身使用明文 UDP/TCP；`android:usesCleartextTraffic="true"` 是兼容性决定。没有完整协议迁移设计时不要擅自强制 TLS。
- 模拟器、JVM 单测、Lint、synthetic AFSK loopback 与 Release 构建不能替代真机、真电台、真实空中弱信号及低功率/假负载验证。
- 软件 PTT watchdog 不是硬件互锁；任何本地状态变化都不能作为“电台一定已经回到 RX”的唯一证据。
- 更新检查保持**显式用户操作**：不得在启动、后台、WorkManager、Alarm 或 Service 中自动/周期检查，也不得自动下载安装。

## 3. 代码、目录与构建结构

仓库保留 APRSdroid 的非标准单模块布局：

| 路径 | 内容 |
| --- | --- |
| `src/` | Kotlin / Java 生产源码 |
| `src/ic705/` | IC-705 protocol / transport / session / backend / diagnostic |
| `src/audio/` | AFSK1200 与 PCM Kotlin 边界 |
| `native/graywolf-jni/` | Rust JNI adapter 与 Graywolf AFSK1200 receive core |
| `src/diagnostic/` | 持久日志、网络事件、诊断快照与 ZIP 导出 |
| `src/update/` | 手动 GitHub Release 检查 |
| `res/` | values、drawable、mipmap、menu 等；生产页面无 `res/layout` |
| `test/java/` | JVM 单测 |
| `androidTest/java/` | Android instrumentation 测试 |
| `.github/workflows/` | 构建、测试、Lint、Release 和专项 CI |

AGP 9 使用 built-in Kotlin；不要重新应用 `org.jetbrains.kotlin.android`。Compose Compiler 与 Kotlin 基线保持一致。Java 17 是当前基线，没有明确需求和完整验证时不要仅为版本数字切换 Java 21。

正式 GitHub Release 当前发布：

- `arm64Opengl`，文件名与 ARMv7 保持同一命名规则，不使用额外 `Recommended_` 前缀
- `arm32Opengl`

源码还保留 ARM64 Vulkan、x86、x86_64 flavor。当前 Graywolf Android native helper 与正式本地 AFSK RX 只覆盖 ARM64/ARMv7；x86/x86_64 不属于本地 AFSK RX 的正式支持 ABI。不要未经产品/构建设计讨论重新合并 Universal APK。

### 3.1 Graywolf AFSK1200 RX 合同

所有**生产本地 PCM AFSK1200 接收**统一使用 Graywolf，不允许静默回退旧 Java demodulator：

```text
IC-705 WLAN AUDIO 12 kHz ──────┐
普通 AudioRecord 11.025 kHz ───┼→ FeedableAfskDecoder
Bluetooth SCO 8 kHz ───────────┘
                                  → GraywolfAfskDecoder
                                  → bulk ShortArray JNI
                                  → Graywolf RECOMMENDED_3DEMOD
                                  → FCS-stripped raw AX.25
                                  → existing APRSdroid consumer/parser
```

约束：

- Graywolf 依赖由 `native/graywolf-jni/Cargo.toml` 固定到 `graywolf-demod 0.14.13` 对应 upstream commit `34cd0111b7a40e7d91607699b7b4dd188574970a`；`native/graywolf-jni/Cargo.lock` 必须提交，synthetic 与 Android native 构建必须使用 `--locked`。升级时必须显式改 pin、审查 lockfile diff，并重新跑 synthetic/native/APK gate。
- `FeedableAfskDecoder` 是生产本地 PCM RX 的统一入口。native library 缺失、ABI 不匹配、初始化失败或 processing exception 必须显式失败，不得偷偷创建 legacy decoder。
- 生产 `src/` 不得重新引用 `Afsk1200Demodulator`、旧 multimon `AudioBufferProcessor` / `PacketCallback` 或 `loadLibrary("multimon")`；专项 CI 会硬性扫描。
- 旧 `jsoundmodem` / `Afsk1200Modulator` 目前只允许用于 **TX AFSK PCM 生成**与 host-JVM 测试辅助；在 TX 稳定且没有独立迁移理由时不要为了“全 Rust”重写发送链。
- JNI 输入必须按 `ShortArray` 批量送入，严禁 per-sample JNI。Rust 侧使用 handle registry 管理 decoder；`nativeCreate/nativeProcess/nativeDestroy` 不得让 panic 穿过 JNI 边界。
- Kotlin `reset()` 采用“先成功创建 replacement，再销毁旧 handle”的语义；`close()` 必须幂等并释放 native state。
- Graywolf 默认 110-sample dedup window 是 44.1 kHz 假设，本项目固定语义为 **3 symbols**，按 `ceil(3 * sampleRate / 1200)` 动态计算：8 kHz=`20`，11.025 kHz=`28`，12 kHz=`30` samples。
- synthetic test 至少覆盖 8 kHz / 11.025 kHz / 12 kHz clean round-trip、非 packet 边界分块，以及确定性的轻度幅度失衡/噪声/削波回归；synthetic 绿只能证明可重复的 DSP 基线，不代表真实 RF 弱信号性能。
- Android ARM64/ARMv7 `.so` 必须由 CI 从源码生成到 `build/generated/rustJniLibs/<abi>/libaprs_graywolf.so`，不得把生成 `.so` 提交回源码 `libs/`。
- Release 必须校验 JNI exports、单 ABI、16 KiB ELF/page alignment 与 APK 内 `.so` SHA-256，并随 tagged Release 提供 pinned Graywolf 对应源码归档。

## 4. UI、导航与动效设计规范（必须遵守）

这一节是规范性设计合同，不是“当前实现随手描述”。修改导航、动画、设置页或二级页面前必须先核对本节。

### 4.1 总原则：一个转场只能有一个权威动画系统

**MUST：同一次前进/返回只能由一个系统负责窗口或页面 motion。**

不得叠加以下组合：

- Android predictive back + Compose `popEnter/popExit`；
- Activity window transition + 页面内部再补一套整页 slide/fade；
- 系统 BackDispatcher + 手工 `finish()` 后再触发 Compose 动画；
- Navigation Compose 默认 cross-fade + 自己额外 graphicsLayer 整页动画。

如果平台已经负责 Activity/predictive back，就让平台完整负责，不要再“补一点动画”。

本项目参考 Android 17 Settings 的原则是**划清动画责任边界**，不是照抄某个 Google App 的私有时长或 easing 参数。

### 4.2 四个一级页面：单 Hub、无整页转场

四个一级 destination 固定为：

```text
stations / map / messages / packets
```

由 `HubActivity` + Navigation Compose 承载，手机端使用 Material 3 Bottom Navigation。

**MUST：**

- 底栏一级页切换继续通过 `navigateTopLevel()`。
- 使用 `popUpTo(graph.findStartDestination().id) { saveState = true }` + `launchSingleTop = true` + `restoreState = true`，保持 `stations` 常驻并恢复各 root 状态。
- `NavHost` 的 root `enter/exit/popEnter/popExit` 保持 `None`。
- 一级页视觉反馈只使用 Material 3 NavigationBar 自身的选中动效。

**MUST NOT：**

- 恢复 root cross-fade；
- 给四个 root 做整页 alpha；
- 给 MapView、LazyColumn、APRS symbol Canvas 整页横移；
- 用多个 Activity 复制底栏来模拟一级导航。

原因：地图和台站列表属于重页面，整页 alpha / slide 会增加 GPU layer、合成和双页面同时绘制成本，而且没有必要的层级语义。

### 4.3 二级页面：优先让 Android 平台负责返回与 predictive back

聊天、台站详情、通知设置、连接设置、定位设置等具有明确“进入/返回”层级的页面可以使用独立 Activity。

当前正式边界：

- `MessageActivity`：聊天；
- `StationActivity`：台站详情；
- `NotificationSettingsActivity`：通知设置；
- `BackendPrefs`：连接设置；
- `LocationPrefs`：定位设置。

**MUST：**

- toolbar Back 调用 `onBackPressedDispatcher.onBackPressed()`；
- 系统返回键、手势 predictive back、toolbar Back 共用同一权威入口；
- 默认使用 Android 平台窗口转场；
- 从台站/消息/地图打开聊天时直接启动 `MessageActivity`，Activity 关闭后自然回到原 Hub route。

**MUST NOT：**

- 在这些 Activity 外再叠 NavHost pop transition；
- 为来源页面制造 `fromStation` / `fromMessages` 等人工来源标记；
- 为了“统一视觉”恢复全局 `android:windowAnimationStyle`。

### 4.4 禁止全局 Activity 动画覆盖

`DefaultTheme` 不得重新设置全局 `android:windowAnimationStyle`。

旧 `Animation.Material3.Activity` 与 `m3_activity_enter/exit/pop_*` 已删除。若某个极特殊流程确实需要 override transition：

1. 先证明 Android 平台默认 motion 无法满足；
2. 仅限定在该明确流程；
3. 不影响 predictive back；
4. 不扩散成全 App 默认；
5. 必须用真机录屏验证进入、返回、手势取消和完成四种状态。

### 4.5 通知进入聊天：一次建立系统任务栈

消息通知使用 Activity 数组 / `PendingIntent.getActivities()` 一次建立：

```text
HubActivity(EXTRA_START_DESTINATION=messages)
    → MessageActivity
```

这样从通知进入聊天后 Back 固定回“消息”主页。

**禁止恢复：**

```text
通知
→ HubActivity
→ Compose LaunchedEffect
→ 再启动聊天
```

也不要恢复 `EXTRA_CHAT_CALL` 让 Hub 负责二次消费聊天跳转。

### 4.6 通知设置与 NotificationChannel：导航不能等待 Binder

`NotificationSettingsActivity` 是 APRSdroid 自己的轻量二级 Activity。

NotificationChannel 策略：

- `APRSdroidApplication` 可后台预热；
- `ServiceNotifier` 在真正发送通知前保留同步兜底；
- 进入通知设置页面不得等待 `createNotificationChannel()`；
- 点击“消息通知/状态通知”不得等待频道创建完成后再跳转；
- 设置页不得在首帧前做无必要的 NotificationManager Binder 工作。

### 4.7 同 App 卡顿与跨应用卡顿必须区分

以下是**同 App 内**路径：

```text
Hub → MessageActivity
Hub → StationActivity
PrefsAct → NotificationSettingsActivity
```

这些路径若明显卡顿，仍属于本项目可分析/优化范围，不能用“跨应用本来就卡”解释。

以下才是**跨应用**路径：

```text
NotificationSettingsActivity
→ ACTION_CHANNEL_NOTIFICATION_SETTINGS
→ Android 系统 Settings
```

APRSdroid 能做到的是：立即发 Intent、不额外等 Binder、不先做第二段应用内动画。

之后的这些成本不由 APRSdroid 完全控制：

- `com.android.settings` 冷启动/进程唤醒；
- 系统 Settings Activity 首帧；
- PackageManager / NotificationManager 数据准备；
- 厂商 ROM 的窗口动画、调度和系统 Settings 实现。

判断冷启动影响的实用方法：若第一次打开系统频道设置明显慢，而立刻返回后第二次明显更快，优先怀疑系统 Settings 冷启动/缓存，而不是继续在 APRSdroid 内调动画参数。

### 4.8 性能验收原则

导航/动效改动至少人工检查：

1. 四个底栏 root 连续来回切换；
2. 任意 root → 台站，不应比其它 root 特殊卡顿；
3. 台站 → 聊天 → Back；
4. 消息 → 聊天 → Back；
5. 地图台站 Bottom Sheet → 聊天 → Back；
6. predictive back 手势拖动、取消、完成；
7. toolbar Back 与系统手势视觉一致；
8. 通知冷启动 → 聊天 → Back 到消息；
9. APRSdroid 通知设置第一次/连续打开；
10. Android 系统频道页冷/热启动分别观察，不把系统冷启动误判成 App 内 jank。

只看 CI 绿不等于动画正确；动效必须真机录屏验收。

主页台站搜索保持右上角放大镜入口，默认不展示搜索框。按呼号/备注的字面子串匹配必须在数据库结果 LIMIT 之前执行，保留距离排序和显示期限；搜索与收包刷新共用合并队列，不能恢复每次输入并发查询。

### 4.9 信息密度与无障碍

`ui.compact_lists` 是台站/报文列表的共享 UI-only 偏好。

- 紧凑模式只调整 padding、间距、圆角、symbol/图标尺寸和次要信息占用；
- 不得覆盖 Android 系统 `fontScale`；
- 大字号时优先减少留白和次要文本行数，而不是偷偷缩小字体；
- 当前 `fontScale >= 1.15` 时台站备注最多一行。

## 5. 地图架构

`Mod-v2.2.4` 包含地图/列表刷新性能修复：地图使用独立的不可变 `MapStation` 快照，图标按呼号/符号复用；Canvas 位图生成和 GeoJSON 序列化在后台执行，MapLibre Style 与 Google Marker 操作仍在主线程。不得恢复每条 UPDATE 广播删除全部图片或 `GoogleMap.clear()` 后重建的路径。

台站、地图、报文使用 `LatestQuery` 合并请求并保证同一页面最多一个查询进行中。对应 ViewModel 必须由 Activity 的 ViewModelStore 管理，避免常驻刷新协程脱离生命周期；过滤条件切换必须抑制旧结果。Hub 只更新当前页面所需数据，地图页额外读取本台位置，不查询隐藏报文列表或完整邻站列表。

主 `map` destination 直接内嵌地图：

- 高德 / OSM / 自定义栅格：MapLibre；
- Google 普通 / 卫星：Google Maps SDK。

MapLibre 与 Google `MapView` 跟随 Compose destination / 宿主 Lifecycle；离开 destination 或切换 renderer 前保存 camera 并执行对应 pause/stop/destroy。

`MapAct` / `GoogleMapAct` 只保留给坐标选择器和兼容入口，不应重新成为四个一级页面的默认导航实现。

正式 ARM64/ARMv7 OpenGL APK 使用官方 MapLibre 13.5.1 AAR 的 Java/Kotlin API、资源和依赖，并在 Release CI 中以同版本源码构建的 `MinSizeRel` + IPO/LTO `libmaplibre.so` 替换对应 ABI 原生库。不要把这描述成项目维护了独立 MapLibre fork。

OSM 必须保留可识别 User-Agent 与可点击的 `© OpenStreetMap contributors`；不得批量抓取/预取整个区域瓦片。

## 6. IC-705 数据流与恢复模型

主要链路：

1. `AprsService` 根据 `PrefsWrapper` 选择后端；
2. `Ic705WifiBackend` 适配 Service/Prefs 到 controller；
3. controller 创建 generation 化的 `Ic705RxSession`；
4. RX：Wi-Fi UDP → AUDIO codec → PCM16LE 12 kHz → `FeedableAfskDecoder` → Graywolf → AX.25/APRS；
5. TX：APRS/AX.25 → 旧稳定 AFSK modulator 生成 PCM → CI-V PTT ON → AUDIO UDP → CI-V PTT OFF。

默认 control 端口 UDP `50001`；CI-V / AUDIO 端口由会话协商。IC-705 codec 是 12 kHz；Graywolf RX 细节与其它 AudioRecord 采样率见 3.1。

不要退回“任意通道 N 秒没包就销毁整个 session”的统一超时模型，也不要为了更换 DSP 修改 `Ic705RxSession` 的 recovery ownership；decoder 继续通过 controller 的 `Ic705DecoderFactory` / `PcmSink` seam 注入。

当前默认 timing：

| 参数 | 默认值 | 语义 |
| --- | ---: | --- |
| CONTROL timeout | `5000 ms` | session 级权威存活信号 |
| CI-V timeout | `3000 ms` | 控制流；优先局部恢复 |
| AUDIO timeout | `30000 ms` | RX 音频允许长静默 |
| AUDIO post-TX grace | `5000 ms` | PTT OFF ACK 后等待 RX AUDIO 恢复 |
| stream recovery wait | `3000 ms` | 单次 stream rediscovery 等待 |
| stream recovery attempts | `2` | 局部恢复上限 |
| watchdog cadence | `500 ms` | liveness 检查周期 |

角色规则：

- **CONTROL**：超时属于 session 级失败，进入完整 recovery/reconnect；
- **CI-V**：RX/空闲超时先做 stream rediscovery，连续失败再升级完整 reconnect；PTT 期间按安全关键路径处理；
- **AUDIO**：TX 期间 RX 静默不能触发 session teardown；长时间失活先局部恢复，失败后升级。

`Mod-v2.1.0` 已包含 recovery/link-state 修复；涉及 link on/off、poster 状态、session recovery 行为时先阅读相应测试和专项 CI，不要只凭更早 Release 代码推断。

## 7. TX / PTT 安全不变量

`Ic705PttStateMachine` 的核心状态语义包括：

- `RX_IDLE`
- `TX_STREAMING`
- `DRAINING`

必须保持：

- PTT ON 只能在 session 与 TX 条件满足后发生；
- `canStreamAudio` 只在 `TX_STREAMING` 为 true；进入 `DRAINING` 后必须停止 TX audio；
- “电台可能仍在 PTT”与“允许继续发音频”不是同一个 Boolean；
- PTT OFF 必须收到电台 ACK 后才可确认释放并回到安全 RX；
- 本地 UDP `send()` 成功绝不等于电台已 ACK；
- OFF 失败/NAK/ACK 丢失时不得伪造 `RX_IDLE`；
- teardown 前尽最大可能停止音频并请求 PTT OFF；
- timer/watchdog/generation 生命周期必须封闭，旧 generation 不得留下 zombie callback。

修改 PTT、ACK、TX pacing、audio drain、retransmit、teardown 顺序必须补/改测试。

## 8. 并发、UDP 与设备无关性

历史真机曾暴露 session teardown 与 TX executor 并发导致 `channel missing`、`channel not open`、`Socket closed` 等竞态。

约束：

- send/close 生命周期必须避免可预防的本地竞态；
- TX loop 必须观察 session phase 与 `canStreamAudio`；
- 控制/PTT 关键发送异常不得被完全吞掉；
- tracked packet 实际未发送成功时不得长期留在 retransmit store；
- reconnect generation、PTT retry/watchdog 与 teardown 生命周期必须有明确 owner。

不要添加 Huawei/Pixel/某 ROM 型号特判来掩盖状态机问题；优先保持设备无关语义。

## 9. 诊断与日志

正式故障报告不依赖“导出时最后几百行 logcat”。

`AppLog`：

- 同时写 Logcat 与应用内 JSONL；
- 位于 `noBackupFilesDir/diagnostic_events/`；
- 单线程顺序写盘；
- 记录 process start、版本、源码 revision、网络/session/PTT/recovery/crash；
- password、passcode、secret、token、精确经纬度等必须脱敏。

`NetworkEventLogger` 用于区分：

1. Android Wi-Fi `Network` 真的 lost；
2. Wi-Fi 仍存在但 IC-705 CONTROL/CI-V/AUDIO/session 自身失活。

新增 IC-705/network/recovery/PTT 逻辑时优先增加结构化事件，不要只加难以关联的 `Log.d()`。

## 10. Android 17 权限与配置安全

- IC-705 Wi-Fi 与 LAN TCP TNC 在 Android 17+ 按需申请 `ACCESS_LOCAL_NETWORK`；
- 公网 APRS-IS 不应因自身连接请求本地网络权限；
- Android 13+ 前台服务遵守通知权限；
- Android 16+ Live Updates 只允许由通知设置中的显式用户开关触发；只有系统允许 promoted notifications 时才能请求 promoted ongoing，普通消息通知不得提升，且不要增加独立的常驻权限状态项；
- AFSK 后端按需请求麦克风；
- 蓝牙路径按需请求对应权限；
- 定位权限由位置来源决定。

配置导入：

- 使用 `OpenDocument` / `ContentResolver` 读取 `content://`；
- 不查询 `_data`，不把 `content://` 强转成 File；
- 保留键 allowlist、类型、大小和字符串长度限制；
- `service_running`、`firstrun` 等运行状态不得被外部 profile 覆盖；
- 日志/异常/诊断 UI 不输出密码、passcode、token、secret。

## 11. 更新检查

更新检查必须保持：

- 只在用户点击“检查更新”后访问 GitHub Releases；
- 不在 Application/Activity 启动时自动调用；
- 不引入 WorkManager、Alarm、后台 Service 周期轮询；
- 不自动下载或安装；
- 发现新稳定版时只提示并打开 Release 页面；
- 版本比较逻辑由 JVM 单测覆盖。

HTTP/HTTPS 外链统一通过 `UrlOpener` 请求 AndroidX Browser Custom Tabs，并保留系统 `ACTION_VIEW` fallback；不要强制 Chrome，也不要为普通外链内嵌 WebView。

## 12. 代码与提交工作流

### 12.1 原子提交

“原子提交”指**一个 commit 表达一个逻辑完整、可以独立理解、独立验证和独立回滚的变化**，不是“一次只能改一个文件”。

例如：

- “二级页面统一走系统 BackDispatcher”可以是一个原子提交；
- “删除全局自定义 Activity window motion”可以是另一个原子提交；
- 发版时 `build.gradle + CHANGELOG + README + AGENT.md` 必须同步，因此四文件可以共同组成一个 release 原子提交。

不要把无关功能、半成品、调试代码和文档修复混成一笔；也不要为了追求“一个文件一个 commit”制造大量无法独立工作的碎片提交。

### 12.2 main 完整性

- 尽量在临时分支/独立 tree 上组装完整改动；
- 不要把占位文件、半套迁移、不能编译的中间状态推到 `main`；
- 若出现误写 main，应立即恢复到最后正确 SHA，再以完整树快进；
- CI 绿之前不要把未验证改动描述成“已修复”；
- 真机 UI/动效问题即使 CI 绿，也必须保留“需要真机复测”的事实边界。

## 13. 验证

常用完整验证：

```bash
./gradlew verifyReleaseVersion \
  testArm64OpenglDebugUnitTest \
  lintArm64OpenglDebug \
  assembleArm64OpenglRelease \
  assembleArm32OpenglRelease \
  --no-daemon --stacktrace
```

Graywolf / AFSK RX 变更还必须：

```bash
bash .github/scripts/test_graywolf_loopback.sh
bash .github/scripts/build_graywolf_android.sh arm64-v8a
bash .github/scripts/build_graywolf_android.sh armeabi-v7a
```

并满足：

- production source Graywolf-only guard 通过；
- `Cargo.lock` 已跟踪且 `cargo --locked` 不产生 lockfile diff；
- synthetic 8 kHz / 11.025 kHz / 12 kHz 与 impairment/fragmentation cases 通过；
- ARM64/ARMv7 JNI exports 与 16 KiB load alignment 通过；
- APK 内 `libaprs_graywolf.so` 的 ABI 和 SHA-256 与当次源码构建产物一致；
- tagged release 能生成并附带 pinned Graywolf 对应源码归档。

涉及 IC-705 session/TX/recovery 时还必须验证：

1. 诊断页可连接并持续接收 PCM；
2. **真机真实 RF AFSK 解码正常**；synthetic loopback 不能替代这一项；
3. 低功率/假负载下 PTT ON → audio → PTT OFF ACK 顺序正确；
4. 多次 TX/RX 不出现假 `RX_IDLE`、僵尸 timer 或 teardown 后继续发 AUDIO；
5. TX 中断网不因 socket/channel 竞态崩溃；
6. AUDIO 静默和 TX 期间静默不会无故整 session 重连；
7. CI-V/AUDIO timeout 先局部恢复，CONTROL timeout 或局部恢复失败才升级；
8. 电台 Wi-Fi 在线时 APRS-IS 默认互联网路径不被整个 App 绑定；
9. Android 17 权限授权/拒绝/重新授权行为正确；
10. 导出诊断 ZIP 能看到 revision、network lifecycle、session/generation、recovery 与 PTT 时间线。

## 14. Release 流程

正式发布必须同步：

1. `build.gradle`：`mod_version` 和递增的 `mod_version_code`；
2. `CHANGELOG.md`：顶部新增对应版本；
3. `README.md`：更新 Latest release，并修正已经发布/未发布描述；
4. `AGENT.md`：更新发布基线与 current main；
5. tag：`Mod-v<major.minor.patch>`。

Release workflow 会：

- 测试、Lint；
- 构建 ARM64/ARMv7 OpenGL；
- 从源码构建 Graywolf ARM64/ARMv7 JNI 到 `build/generated/rustJniLibs`；
- 构建并注入 MapLibre `MinSizeRel` + IPO/LTO 原生库；
- 16 KiB zipalign，并校验 Graywolf ELF/load alignment；
- V2/V3/V4 签名；
- 校验 ABI、Graywolf/MapLibre 数量与 SHA-256；
- tagged release 归档 pinned Graywolf 对应源码并与 APK 一起发布；
- 生成 `SHA256SUMS.txt`；
- 保存 R8 mapping，并随 tagged GitHub Release 作为独立资产发布；
- 在 tag 构建发布 GitHub Release。

`main` 普通 push 通过 CI 不等于 Release 已发布。只有 tag workflow 与 GitHub Release 资产真正完成后才能说“已落地”。

## 15. 不得反向恢复的迁移

以下迁移已经完成，除非有新的明确设计决策，不要反向恢复：

- Gradle 9.5 / AGP 9.3.2 / built-in Kotlin / API 37 / Java 17；
- 生产 UI Compose Material 3，无历史 `res/layout` 页面；
- 四个一级页面收敛到 `HubActivity` + Navigation Compose；
- root 页面无整页 cross-fade/slide/alpha motion；
- 聊天、台站详情、通知设置等二级页面由 Activity + Android BackDispatcher/predictive back 负责；
- 不使用全局 `windowAnimationStyle`；
- 通知设置导航不等待 NotificationChannel Binder；
- 通知点击一次构造 messages → chat Activity 栈，不用 Hub 二次 LaunchedEffect；
- HTTP/HTTPS 外链统一走 Custom Tabs + `ACTION_VIEW` fallback；
- 台站/报文列表支持标准与紧凑两档几何密度并尊重系统 fontScale；
- Mapsforge 与专用离线瓦片下载器已移除；主地图为 MapLibre + Google Maps；
- 外部存储读写权限已删除；文档导入/导出使用 SAF / ContentResolver；
- HTTP POST 使用 `HttpURLConnection`；
- Release 使用 R8 并保留 mapping；
- Android 17 本地网络权限已接入 IC-705 / LAN TCP TNC；
- PTT OFF 使用 ACK 确认语义；
- IC-705 watchdog 为 CONTROL/CI-V/AUDIO 角色化 liveness + stream-local recovery；
- 生产本地 PCM AFSK RX 为 Graywolf-only；旧 Java/jsoundmodem demodulator 不得作为 fallback，旧 modulator 仅保留 TX；
- Graywolf Android `.so` 是 build-generated artifact，不提交到源码 `libs/`；
- 诊断为持久结构化事件 + Network 生命周期 + ZIP；
- MapLibre Release 原生库使用同版本源码 `MinSizeRel` + IPO/LTO；
- AndroidX Preference / AppCompat 直接依赖已移除；
- `AI_CONTEXT.md` 只保留兼容入口，`AGENT.md` 是唯一规范正文。

历史细节属于 `CHANGELOG.md` 与 Git 历史；不要在 `AGENT.md` 持续堆积每个旧版本的完成清单，只保留会影响下一次修改决策的当前事实和设计约束。

## 16. Hamlib 与多电台架构规划

> **状态：规划，尚未实现。** 本节定义后续多电台支持的实施方向，不得把这里的目标描述成当前已发布能力。当前 IC-705 WLAN 路径、PTT 安全状态机、Graywolf RX 和既有设置仍以本文件前文的现状约束为准。

### 16.1 总体原则

后续多电台支持采用“**Hamlib 负责电台控制语义，Android/Kotlin 负责设备与网络 transport，APRSdroid 负责 APRS/AFSK/DSP**”的分层，不继续为每个型号手写一套 CAT/CI-V 命令。

目标边界：

```text
                    APRSdroid
                        │
             ┌──────────┴──────────┐
             │                     │
        APRS / AFSK            Radio Control
       AX.25 / DSP                Hamlib
             │                     │
        Radio Audio            CAT byte stream
             │                     │
      ┌──────┴──────┐       ┌──────┴─────────┐
      │             │       │                │
 Android Audio   Icom LAN   USB Serial    Network
      │             │       │
 USB声卡        现有会话层   Android USB bridge
```

必须保持：

- Hamlib 不负责 APRS packet、AX.25、AFSK1200 调制/解调或 APRSdroid 数据模型；
- Graywolf 继续作为生产本地 PCM AFSK1200 RX 的唯一 decoder；
- 现有 AFSK TX PCM 生成链不因 Hamlib 接入而重写；
- Android USB 权限、USB serial、USB audio、Wi-Fi `Network.bindSocket()` 和移动数据/VPN 共存仍由 Android/Kotlin 层掌握；
- 不为 IC-7100、FT-710、TS-590 等分别创建独立 APRS backend；型号差异优先交给 Hamlib；
- 现有 `proto=ic705`、`ic705.*` 设置和 IC-705 用户升级兼容性不得被首轮 Hamlib 接入破坏。

### 16.2 API 基线与 Hamlib 构建

首个 Hamlib 实施 PR 应把项目 `minSdk` 从 27 提升到 **28**，与 Hamlib 官方 Android NDK 构建基线对齐。该动作属于实现变更；在真正修改 `build.gradle` 前，本文件前文的“当前基线”仍保持 minSdk 27，不得提前伪装成已完成。

Hamlib 集成要求：

- 固定明确的 Hamlib upstream revision/tag，不跟随浮动 master；
- CI 从源码交叉编译 Android Hamlib，不把预构建 `.so` 直接提交到源码目录；
- 正式 ABI 首先覆盖 `arm64-v8a` 与 `armeabi-v7a`，x86/x86_64 只按项目实际支持策略补充；
- 记录 Hamlib revision、构建参数、许可证和可获取源码方式；
- Hamlib library 以其 LGPL-2.1-or-later 条款使用；项目分发必须保留相应许可义务；
- Hamlib 官方 Android 构建当前使用 NDK/autotools，并明确 `--without-libusb`；不要假设提升到 API 28 就自动获得 Android USB Host/libusb 能力；
- 若后续 Hamlib Android 官方构建方式发生变化，以实际 upstream 和可复现 CI 为准，并更新本节。

### 16.3 Hamlib JNI 边界

JNI 必须保持薄层，不把业务逻辑搬进 native。

建议 Kotlin/native 边界只覆盖：

- Hamlib version/build revision；
- 枚举 rig model / manufacturer / capability；
- create/init / open / close / cleanup；
- get/set frequency；
- get/set mode；
- get/set PTT；
- 必要的 VFO / split / config token；
- 错误码、错误文本和最小诊断信息。

Kotlin 侧建立通用 `RadioControl` / `HamlibRadioControl` 抽象。单个 rig handle 的 Hamlib 调用必须串行化，避免 UI、service、TX/PTT 与轮询线程并发敲同一 handle。

首轮 JNI 不得：

- 暴露整个 Hamlib C struct 给 Kotlin；
- 复制一套 Icom/Yaesu/Kenwood 型号判断到 Kotlin；
- 在 JNI 中实现 APRS/AFSK；
- 为了 Android 方便大规模 fork Hamlib I/O 核心；
- 把现有 IC-705 LAN PTT 安全状态机替换成简单 `rig_set_ptt()`。

### 16.4 USB CAT transport

Hamlib Android 当前 `--without-libusb`，因此 USB CAT 的首选架构是：

```text
Hamlib rig backend
      │
  127.0.0.1:<ephemeral port>
      │
Local CAT byte-stream bridge
      │
Android UsbSerialPort / USB Host
      │
     Radio
```

原则：

- Android 层负责枚举 USB 设备、权限、打开/关闭、断线和重连；
- bridge 只做可靠字节流搬运，不解析 CI-V/CAT；
- Hamlib 继续看到普通 network byte stream，并负责 Icom/Yaesu/Kenwood 控制语义；
- 本地监听仅绑定 loopback，不开放到 LAN；
- 端口使用临时端口并有明确生命周期 owner；
- bridge teardown 必须先阻止新写入，再关闭 socket/USB，避免 zombie reader/writer；
- 必须记录连接建立、USB detach、bridge EOF、Hamlib open/close 和错误原因；
- 若特定 Hamlib backend 不能可靠使用 network pathname，再评估 native PTY/custom transport；不要在没有证据时先 fork Hamlib。

### 16.5 Radio Audio 与 CAT 解耦

音频设备与电台控制是两个正交配置，不得把“USB Audio + Hamlib CAT”硬编码成某个型号 backend。

目标数据流：

```text
RX:
USB/Android Audio
    → PCM
    → FeedableAfskDecoder
    → Graywolf
    → AX.25/APRS

TX:
APRSPacket
    → AX.25
    → AFSK PCM
    → Android/USB Audio output
    → Radio

PTT:
RadioControl.setPtt(true)
    → TX audio
    → drain
    → RadioControl.setPtt(false)
```

要求：

- Android 使用明确选定的输入/输出 `AudioDeviceInfo`，不能靠系统默认设备碰运气；
- RX sample-rate adaptation 继续进入现有统一 `FeedableAfskDecoder` seam；
- TX 必须保留“PTT ON 确认 → 允许 audio → drain → PTT OFF”的安全时序；
- 普通 Hamlib PTT 后端若不能提供 ACK/readback 级确认，UI/诊断必须如实区分“命令已提交”和“已确认 RX”；
- IC-705 WLAN 路径仍遵守本文件第 7 节更严格的 ACK/PTT 安全不变量，不能因统一 `RadioControl` 接口而降级。

### 16.6 IC-705 / Icom LAN 的处理

**首轮 Hamlib 集成不得迁移或重写现有 IC-705 WLAN backend。**

现有实现已经具备 Android 特有且必须保留的能力：

- Wi-Fi `Network.bindSocket()`，允许电台 AP 与移动数据/VPN 并存；
- generation 化 session，旧 generation callback 隔离；
- CONTROL / CI-V / AUDIO 角色化 watchdog；
- CI-V / AUDIO stream-local soft recovery；
- AUDIO reorder / gap concealment / discontinuity reset；
- tracked retransmission；
- PTT ACK/readback/retry/watchdog 和 teardown 安全语义。

Icom LAN 后续泛化时，协议行为应交叉参考：

1. **Hamlib Icom network backend / PR #2178**：协议分层、streaming、codec、liveness、测试和已知硬件 quirks；
2. **FT8CN**：Android/Java 的 Icom LAN 实例、0xA8 capability、CI-V address/TX/audio capability 解析；
3. **wfview**：长期 Icom LAN 行为与型号 capability/profile 参考。

许可证边界：

- FT8CN 为 MIT，可在满足 attribution/license notice 的前提下参考或移植适当代码；
- wfview 为 GPLv3；当前项目不得把其 GPLv3 实现直接复制进 GPLv2-only 代码路径，除非先明确解决项目整体许可兼容性；
- 优先把 wfview 当行为、数据格式和测试 oracle，而不是代码来源。

Icom LAN capability 不能只相信运行时 bitmap。后续策略应为：

```text
runtime 0xA8 capability
        ↓
known model / validated quirk database
        ↓
actual stream-arrival watchdog
        ↓
advanced manual override（仅必要时）
```

原因：Hamlib Icom network 实测已经出现“电台 capability 宣称支持某 codec/sample-rate，但协商成功后实际无 audio”的组合。必须用真实流到达情况做最后判定。

### 16.7 分阶段实施 PR

后续新对话/Agent 应按小步 PR 推进，不做一次性大重构。

#### PR 1：Hamlib build foundation

- `minSdk 27 → 28`；
- pin Hamlib revision；
- CI 可重复构建 Android Hamlib；
- ARM64/ARMv7 native artifact 校验；
- license/source attribution；
- 不改变任何现有运行行为。

验收：现有 unit/lint/release/Graywolf/IC-705 gate 全绿，APK 能加载 Hamlib native library。

#### PR 2：最小 Hamlib JNI

- version；
- rig enumeration；
- init/open/close；
- freq/mode/PTT；
- capability 与错误映射；
- dummy/mock rig 测试；
- 不接真 USB，不进行真实发射。

验收：JVM/native/instrumentation 可证明 JNI 生命周期和错误处理正确，无 handle 泄漏或并发 use-after-close。

#### PR 3：通用 RadioControl

- 引入 `RadioControl`；
- 新增 `HamlibRadioControl`；
- 把“型号”和“transport”从 APRS backend 概念中拆开；
- 保持现有 IC-705 WLAN PTT/session 不变。

验收：Hamlib dummy 与现有 IC-705 路径均可独立工作，旧设置不迁移、不丢失。

#### PR 4：Android USB CAT bridge

- Android USB Host/serial 权限与生命周期；
- loopback CAT bridge；
- Hamlib network pathname 接入；
- detach/reconnect/EOF/timeout 诊断；
- 首先验证 Icom CI-V 字节流。

验收：真机可由 Hamlib 对 USB 电台稳定完成读频、设频、mode 和无 RF 风险的控制操作。

#### PR 5：通用 Radio Audio backend

- 显式 USB Audio input/output 选择；
- RX → Graywolf；
- TX → AFSK PCM → audio device；
- Hamlib CAT PTT；
- audio/CAT 独立配置；
- TX drain 与失败恢复。

验收：synthetic + Android audio loopback 先通过，再进入低功率/假负载真机测试。

#### PR 6：首台完整 Hamlib 电台

首选 **IC-7100**，因为它同时验证：

- Icom CI-V；
- Android USB CAT；
- USB Audio；
- VHF/UHF；
- APRS AFSK 收发；
- CAT PTT。

验证顺序：

1. open/close；
2. read frequency；
3. set frequency；
4. mode/VFO；
5. PTT 空载/假负载安全测试；
6. RX AFSK；
7. TX AFSK；
8. 长时间收发与 USB detach/reconnect。

只有完成对应硬件验证后才能在 README/Release 中标为支持；未充分验证时只能标 Experimental。

#### PR 7：Icom LAN 泛化

在 Hamlib USB 路径稳定后，再逐步：

- 抽取 `IcomLan*` 通用概念；
- 增加 0xA8 capability decoder；
- 动态 CI-V address；
- model quirk database；
- 优先试验 IC-9700；
- 后续 IC-905；
- 保持 `Ic705*` 兼容 wrapper 或等价迁移层，避免旧用户配置断裂。

禁止把“大规模 rename + capability + 新型号 + Hamlib + UI 重构”塞进一个 PR。

### 16.8 设置模型目标

最终设置应从“每个型号一个 backend”逐渐转成组合式模型：

```text
连接方式
  Icom LAN
  USB Radio

电台
  Icom IC-7100
  Yaesu FT-710
  Kenwood TS-590SG
  ...

控制
  Hamlib

CAT 设备
  <USB serial device>

音频输入
  <USB audio input>

音频输出
  <USB audio output>

PTT
  CAT
```

IC-705 WLAN 继续可表现为：

```text
连接方式
  Icom LAN

电台
  Auto / IC-705

地址
  192.168.x.x

控制
  Icom LAN

音频
  Icom LAN
```

设置层必须支持未来“同一型号不同 transport”，例如 IC-705 可有 Icom LAN，也可有 USB Audio + Hamlib CI-V；不得把型号和 transport 永久绑死。

### 16.9 测试与安全门槛

Hamlib/多电台相关变更除本文件第 13 节通用 gate 外，还至少要求：

- Hamlib pinned revision 可从干净 checkout 重建；
- JNI ABI/export/16 KiB page alignment 校验；
- rig handle create/open/close/reopen 循环测试；
- 并发调用被串行化；
- USB permission grant/deny/detach/replug；
- loopback bridge 不可从非 loopback 网络访问；
- USB reader/writer teardown 无 zombie thread；
- CAT 失败不能让 APRS service 崩溃；
- Audio device 消失时立即停止 TX audio，并进入安全 PTT release 流程；
- 不允许“本地写成功”直接等价为电台 PTT 已确认；
- 真实 RF TX 必须使用低功率/假负载并人工确认 PTT OFF；
- 每个新型号都要区分“Hamlib 声称支持”与“本项目真机验证支持”。

### 16.10 当前实施起点

下一次开始此规划时，**第一批工作只做 PR 1 + PR 2**：

1. 提升 minSdk 到 28；
2. pin Hamlib；
3. 建立可重复 Android NDK build；
4. 接入最小 JNI；
5. 枚举 rigs；
6. dummy/mock rig；
7. 验证 freq/mode/PTT API 与生命周期。

在这两步稳定前，不启动 IC-9700/IC-905 支持，不重命名现有 `Ic705*`，不迁移 IC-705 WLAN PTT，不同时重做连接设置 UI。

