# APRSdroid Mod

APRSdroid 的现代化修改版，包含 Icom IC-705 Wi-Fi 直连 / A modern APRSdroid fork including direct Icom IC-705 Wi-Fi support.

[中文说明](#中文说明) · [English](#english) · [更新日志 / Changelog](CHANGELOG.md) · [下载 / Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases)

**最新稳定版 / Latest release: `Mod-v1.9.4`**

> `Mod-v1.9.4` 完成品牌统一，并加入台站报文结构化解析、可交换的台站单击/长按操作、APRS-IS 可选电量字段与 FMO 台站标记。
>
> `Mod-v1.9.4` unifies the APRSdroid Mod brand and adds structured station-packet parsing, swappable station tap/long-press actions, optional APRS-IS battery reporting, and FMO station badges.

> 本项目是社区维护的非官方修改版，与 Icom、APRSdroid 原作者或 APRS-IS 运营方不存在隶属关系。发射前请确认当地法规、频率、功率、路径和呼号设置。
>
> This is an unofficial community fork. It is not affiliated with Icom, the original APRSdroid project, or APRS-IS operators. Verify local regulations, frequency, power, path, and callsign settings before transmitting.

## 中文说明

### 项目简介

APRSdroid Mod 在 [APRSdroid](https://aprsdroid.org/) 基础上增加了 IC-705 内置 Wi-Fi 的 APRS 收发能力。手机可直接连接电台热点，或与电台处于同一局域网，通过 UDP 完成会话控制、CI-V PTT 和音频传输，不需要音频线、OTG 转接器或外接蓝牙 TNC。

IC-705 的 UDP Socket 会逐个绑定到 Android 选定的 Wi-Fi `Network`，因此电台流量可以走 Wi-Fi，而 APRS-IS 等互联网流量仍可走手机默认网络，例如 4G/5G。

### 主要功能

- IC-705 Wi-Fi 半双工 APRS 收发：AX.25、AFSK1200、12 kHz 单声道 PCM、CI-V PTT。
- PTT 安全状态机与绝对超时看门狗；未收到电台 PTT OFF ACK 时不会假装已经回到 RX。
- IC-705 通道健康检查：CONTROL 负责整套 session 存活；CI-V 与 AUDIO 可先进行局部 stream recovery，再在连续失败后升级为完整重连。
- TX 期间不会因为 RX AUDIO 暂停而误判断线；PTT OFF 后为音频恢复保留 grace period。
- 持久化结构化诊断日志：关键 App、网络、IC-705、PTT、重连和崩溃事件同时写入 Logcat 与轮转 JSONL 文件，进程重启后仍可导出。
- 设置页可一键分享诊断 ZIP，包含文本报告与结构化事件日志。
- 设置页提供**手动检查更新**；只有用户点击时才请求 GitHub Releases，不会开机检查、后台轮询、定时联网或自动下载安装。
- 台站详情中的历史 APRS 数据默认以结构化字段显示；原始 TNC2 报文通过“显示原始数据”按钮按需展开。
- 首页台站单击/长按动作可在设置中互换；默认仍为单击发消息、长按查看详情。
- APRS-IS 模式可选择在位置信标中附加 `BAT:xx%` 电量字段；其他射频/本地后端不发送该信息。
- 自动识别 `APFMO*` destination 的 FMO 台站，并在台站卡片以 `FMO` → 语音频率的顺序显示标签。
- APRS-IS TCP / HTTP POST / UDP，以及 AFSK、KISS、TNC2、Kenwood、蓝牙 SPP、USB 串口和 LAN TCP TNC 等原 APRSdroid 路径。
- 智能信标、周期/手动定位、台站、消息、日志和多地图源。
- Material 3 / Material You + Jetpack Compose；生产页面不再使用 `res/layout` XML 布局。
- MapLibre Native 在线栅格地图：高德、OpenStreetMap、自定义瓦片；Google 普通/卫星图仍使用 Google Maps SDK。
- 正式 ARM64/ARMv7 OpenGL APK 会将官方 MapLibre 13.5.1 AAR 内的原生库替换为同版本源码构建的 `MinSizeRel` + IPO/LTO `libmaplibre.so`；Java/Kotlin API、资源和 Maven 依赖仍来自官方 AAR。

### 兼容性

| 项目 | 要求或状态 |
| --- | --- |
| Android | Android 8.1+（API 27） |
| 目标平台 | Android 17 / API 37 |
| CPU / ABI | `arm64-v8a`、`armeabi-v7a`、`x86_64`、`x86`，源码中为独立 flavor |
| 正式 Release | ARM64 OpenGL + ARMv7 OpenGL |
| 电台 | Icom IC-705，启用 WLAN 与 Network User |
| 默认控制端口 | UDP `50001` |
| 常见电台热点地址 | `192.168.59.1`，以实际网络为准 |
| 构建环境 | JDK 17、Android SDK API 37 |

不同 IC-705 固件的菜单名称可能略有差异。首次发射建议使用低功率或合适的假负载。

### 安装

从 [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases) 下载与你设备匹配的 APK：

| 文件规格 | 适用设备 |
| --- | --- |
| `Recommended_...-arm64-v8a-opengl.apk` | 大多数现代 64 位 ARM 手机，推荐 |
| `...-armeabi-v7a-opengl.apk` | 仍支持 32 位 ARM 应用的设备 |

正式 Release 当前只发布 ARM64 与 ARMv7 两个 OpenGL APK，并提供 `SHA256SUMS.txt`。源码仍保留 ARM64 Vulkan、x86 与 x86_64 变体，供自行构建。部分 64 位系统不能运行 32 位应用，因此 ARMv7 不是 ARM64 的通用回退包。

应用 ID：`me.nimenhagg.aprsdroidic705mod`。若旧 APK 使用不同签名，Android 可能要求先卸载；卸载会删除该安装的本地设置和诊断日志。

### IC-705 配置

1. 在电台中启用 `MENU` → `SET` → `WLAN & Internet` → `WLAN`。
2. 使用电台 Access Point 模式，或让手机和电台加入同一局域网。
3. 在 `Network User / Pass` 建立用户名和密码。当前实现要求用户名非空，用户名和密码最长 16 个 US-ASCII 字符。
4. 确认控制端口，通常为 `50001`。
5. 让手机保持连接电台 Wi-Fi；如果 Android 提示该网络无互联网，请选择继续连接。

不要在截图、Issue 或日志中公开电台网络密码。

### 应用配置与首次连接

1. 打开“设置”，填写呼号、SSID、数字中继路径和位置来源。
2. 在连接设置中将协议选为 `IC-705 Wi-Fi`。
3. 填写电台 IP、控制端口、Network User 用户名与密码。
4. 可先打开 IC-705 诊断页确认握手、音频接收和 AFSK 解码；诊断页不会发射。
5. 首次发射使用低功率或假负载，并确认 PTT 能及时释放。

### IC-705 连接恢复策略

当前 `main` 将三类通道分开处理，而不是“任意一条 UDP 3 秒没数据就重建整个 session”：

- `CONTROL`：整套 IC-705 session 的权威存活信号；超时会进入完整恢复/重连。
- `CI-V`：低延迟控制流；空闲超时优先尝试局部 rediscovery，连续失败后才升级到完整重连。PTT 期间 CI-V 故障按射频安全优先处理。
- `AUDIO`：允许较长 RX 静默；TX 期间不因 AUDIO RX 静默触发重连，PTT OFF 后还有恢复宽限期。长时间无音频时先局部恢复。

这些策略用于提高不同 Android 厂商网络栈、线程调度和 Wi-Fi 驱动下的容错能力，但软件恢复不能替代电台侧安全操作。

### 诊断与故障报告

设置页的“分享系统诊断与运行日志”会生成 ZIP。当前诊断系统的重点是保留**第一现场**，而不是只抓导出瞬间的最后几百行 Logcat。

持久日志会记录：

- App 版本、`versionCode`、构建类型和源码 revision。
- Android Wi-Fi Network 的 available/lost、Capabilities、LinkProperties 等变化。
- IC-705 generation、session phase、通道选择/Socket 绑定、watchdog、soft recovery 与完整 reconnect。
- PTT ON/OFF 请求、ACK、重试、watchdog 与 TX 状态变化。
- 未捕获异常及栈信息。

敏感字段会自动脱敏；密码、passcode、secret、token 和精确经纬度不应写入持久日志。日志位于应用 `noBackupFilesDir`，按大小轮转，覆盖安装和普通进程重启不会自动清除，卸载应用会删除。

### 手动检查更新

设置 → “应用支持与关于” → “检查更新”。

- 仅在用户点击时访问 GitHub Releases。
- 不在应用启动时检查。
- 不使用 WorkManager/Alarm/后台 Service 做周期检查。
- 不自动下载或安装 APK。
- 有新稳定版时仅提示并打开对应 Release 页面。

### 地图引擎

- 高德、OpenStreetMap 和自定义在线瓦片使用 MapLibre Native 13.5.1。
- 正式 ARM64/ARMv7 Release 使用 OpenGL；源码还提供 ARM64 Vulkan 与 x86/x86_64 双后端 flavor。
- Release CI 从 MapLibre Native `android-v13.5.1` 构建 `MinSizeRel` + IPO/LTO 原生库，替换 APK 内对应 ABI 的 `libmaplibre.so` 后重新执行 16 KiB 对齐、签名和 SHA-256 校验。
- OpenStreetMap 请求包含可识别的 User-Agent，遵循服务端缓存规则，并在地图上显示可点击的 `© OpenStreetMap contributors`。
- Google 普通地图和卫星/混合地图使用 Google Maps SDK。正式 Release 在构建时注入受包名和签名证书限制的 Key；自行构建未配置 Key 时隐藏 Google 图源。
- 不提供 MapLibre Offline 区域下载/管理功能，也不批量预取 OSM 瓦片。

### 权限说明

应用按连接方式和位置来源请求权限，不会为全部后端一次性索取所有权限。

| 权限 | 何时使用 |
| --- | --- |
| 本地网络 | Android 17 上连接 IC-705 Wi-Fi 或 LAN TCP TNC |
| 精确/大致位置 | 智能信标、周期定位或相关定位模式 |
| 通知 | Android 13+ 前台服务状态通知 |
| 麦克风 | AFSK 音频后端；IC-705 Wi-Fi 不使用手机麦克风 |
| 蓝牙 | 蓝牙 SPP / 蓝牙音频路径 |

拒绝必需权限后，应用不会启动对应 APRS 服务；可在 Android 应用信息页重新授权。

### 常见问题

**找不到 IC-705 / 登录失败**

- 确认手机仍连接电台所在 Wi-Fi，IP、端口和 Network User 凭据正确。
- 确认没有其他客户端正在占用 IC-705 网络会话。
- Android 17 上确认“本地网络”权限已允许。
- 使用 IC-705 诊断页和诊断 ZIP 查看最早的 network/session failure，而不是只看后续 PTT 重试。

**连接电台后 APRS-IS 没网**

- 保持蜂窝数据开启，不要把整个 App 或系统默认网络强制绑定到电台 Wi-Fi。
- 本项目只绑定 IC-705 UDP Socket；厂商的“智能切网/双通道加速”等功能仍可能改变路由行为。

**PTT 未释放**

- 立即在电台上手动解除发射或关闭电台，再停止 App 服务。
- 软件 PTT watchdog 是最后的容错措施，不是硬件互锁。

### 网络与安全边界

- IC-705 LAN 协议和部分 APRS 服务器使用明文 UDP/TCP，本项目为兼容性保留该行为。
- HTTP POST 后端使用 Android `HttpURLConnection`；裸主机配置仍兼容明文 `http://:8080/`。
- 配置导入通过 Android 文档提供器的 `content://` 数据流读取，不依赖 `_data` 或外部存储绝对路径。
- Android 备份已禁用，但连接凭据仍保存在应用本地偏好设置中。
- 本项目包含发射功能；操作者始终对合法合规和射频安全负责。

### 从源码构建

要求：JDK 17、Android SDK API 37。Gradle Wrapper 固定为 Gradle 9.5.0。

```bash
git clone https://github.com/nimenhagg/aprsdroid-ic705.git
cd aprsdroid-ic705

./gradlew verifyReleaseVersion testArm64OpenglDebugUnitTest lintArm64OpenglDebug assembleRelease --no-daemon --stacktrace
```

Windows PowerShell 使用 `./gradlew.bat`。

主要工具链：

- Android Gradle Plugin 9.3.2
- Gradle 9.5.0
- AGP 9 built-in Kotlin / Compose Compiler 2.3.21
- Compose BOM 2026.08.00
- AppCompat 1.8.0 / Material 1.14.0 / OkHttp 5.3.0
- MapLibre Native 13.5.1（Release 原生库使用 MinSizeRel + IPO/LTO）
- Java 17

Google Maps Key 可从 `MAPS_API_KEY` 环境变量、Gradle property 或未纳入版本控制的 `local.properties` 中 `mapsApiKey` 注入。不要把 Key 提交到仓库。

### 开发与发布

- 生产源码位于历史布局 `src/`，单元测试位于 `test/java/`。
- UI 已迁移为 Compose Material 3；不要重新引入生产 `res/layout` 页面。
- 修改 IC-705 发射/会话恢复代码时必须保留 PTT OFF、ACK 与 watchdog 安全语义并增加测试。
- “最新稳定版”和“当前 main”是两个概念；未打 tag 的 main 功能不要写成已经发布。
- 发版时同步更新 `build.gradle`、`CHANGELOG.md`、`README.md`、`AI_CONTEXT.md`。
- 标签格式：`v<major.minor.patch>-ic705`，例如 `v1.9.3-ic705`。
- Tag CI 会验证版本，测试、Lint、构建 ARM64/ARMv7 OpenGL APK，进行签名/ABI/渲染后端校验，生成 `SHA256SUMS.txt` 和 R8 mapping 后创建 GitHub Release。

完整维护约束见 [AI_CONTEXT.md](AI_CONTEXT.md)。

## English

### Overview

APRSdroid IC-705 adds direct IC-705 WLAN APRS receive/transmit support to APRSdroid. Radio UDP sockets are bound to the selected Android Wi-Fi `Network`, allowing IC-705 traffic to stay on Wi-Fi while APRS-IS can continue through the phone's default internet path.

**Latest stable release: `v1.9.3-ic705`.** The `main` branch may contain newer unreleased work.

### Highlights

- Half-duplex IC-705 Wi-Fi APRS using AX.25, AFSK1200, 12 kHz mono PCM and CI-V PTT.
- PTT OFF ACK-aware safety state machine and absolute watchdog.
- Role-specific liveness: CONTROL is session-authoritative; CI-V and AUDIO can recover locally before escalating to a full reconnect.
- TX-aware audio watchdog behavior to avoid treating expected RX audio silence as a dead session.
- Persistent rotating JSONL diagnostics plus logcat output, crash capture and exportable diagnostic ZIP bundles.
- Android network lifecycle logging to distinguish an actual Wi-Fi `Network` loss from an IC-705 protocol/session failure.
- Manual Settings-only GitHub Release check. It never runs at startup, periodically, or in the background, and it does not auto-download/install updates.
- Jetpack Compose + Material 3 UI with no production `res/layout` screens.
- MapLibre Native for AMap/OSM/custom raster tiles and Google Maps SDK for Google map/satellite modes. Official ARM64/ARMv7 OpenGL releases replace the AAR native library with a same-version `MinSizeRel` + IPO/LTO build while retaining the official AAR API/resources/dependencies.

### Requirements and packages

| Item | Status |
| --- | --- |
| Android | 7.1+ / API 25 minimum |
| Target | Android 17 / API 37 |
| Official APKs | ARM64 OpenGL (`Recommended_...`) and ARMv7 OpenGL |
| Radio | Icom IC-705 with WLAN and Network User enabled |
| Control port | UDP `50001` by default |
| Build | JDK 17, Gradle 9.5.0, AGP 9.3.2 |

Download official builds from [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases). The source tree also retains ARM64 Vulkan and x86/x86_64 variants for local builds.

### Diagnostics

Settings can export a ZIP containing a human-readable report and persistent structured event logs. Events include build/source revision, Android network changes, IC-705 session/generation state, watchdog and recovery decisions, PTT transitions and crashes. Passwords, secrets, tokens and precise coordinates are redacted.

### Manual update check

The update checker is intentionally explicit-user-action only. It contacts GitHub Releases only after the user taps **Check for updates** in Settings. Do not add startup, periodic, WorkManager, alarm, background-service, auto-download or auto-install behavior without an explicit product decision.

### Build from source

```bash
./gradlew verifyReleaseVersion testArm64OpenglDebugUnitTest lintArm64OpenglDebug assembleRelease --no-daemon --stacktrace
```

Java 17 is the project baseline. See [AI_CONTEXT.md](AI_CONTEXT.md) for current architecture, safety invariants and release rules.

## 致谢与许可证 / Credits and license

- 基础项目 / Upstream: [ge0rg/APRSdroid](https://github.com/ge0rg/aprsdroid)
- 协议与实现参考 / Protocol references: [N0BOY/FT8CN](https://github.com/N0BOY/FT8CN), [wfview](https://wfview.org/)
- 地图引擎 / Map engine: [MapLibre Native](https://maplibre.org/maplibre-native/); OpenStreetMap data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright)
- 许可证 / License: [GNU General Public License v2.0](LICENSE)

项目开发包含 AI 辅助协作；维护者仍负责审查、测试和发布。

AI-assisted development is used in this repository; maintainers remain responsible for review, testing, and releases.
