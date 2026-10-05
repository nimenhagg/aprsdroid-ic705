<div align="center">

<img src="img/icon.png" alt="APRSdroid Mod" width="160" height="160" />

# APRSdroid Mod

**APRSdroid 的现代化社区修改版 · Icom IC-705 等 WLAN / USB 直连**

增加 Icom IC-705 等 WLAN / USB 直连 APRS 收发与 Hamlib 多电台控制，并持续维护现代 Android UI、诊断和性能改进。

[![License: GPL-2.0](https://img.shields.io/badge/License-GPLv2-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%209%2B-green.svg)]()
[![Latest Release](https://img.shields.io/badge/Release-Mod--v2.6.0-orange.svg)](https://github.com/nimenhagg/aprsdroid-ic705/releases)
[![Target](https://img.shields.io/badge/Target-Android%2017%20(API%2037)-brightgreen.svg)]()
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)]()

交流群：[QQ 群 1124716537](https://qun.qq.com/universal-share/share?ac=1&authKey=PjrBi5011R2U%2F1ExGjHnHycWPkdQUhKRJUbPyQltK6QFnujWWIouWDJGP2ZJ7%2BJ%2B&busi_data=eyJncm91cENvZGUiOiIxMTI0NzE2NTM3IiwidG9rZW4iOiJWK0xVZ0JGNVVNMEF3T2FYOEY2UjFWUnJla2dpZVdERWpwNERWS21VOUJNRWxYWXhPWjBlYVlYL1NQdXZ1NE1FIiwidWluIjoiMzIwMDQ1NjE3NCJ9&data=y4e4QQ6_nM_FMmqYhGCnyQtGt-qJn7kUuOWVY_ho9XyiHYs8vwZBnMAKdxOdDOpMUdNBpwK8B8H8F1_Z6wln7g&svctype=4&tempid=h5_group_info) · Telegram：[@APRSdroid_Mod](https://t.me/APRSdroid_Mod) · 最新版发布：[GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases)

**🌐 语言 / Language：** [中文说明](#中文说明) · [English](#english) · [更新日志 / Changelog](CHANGELOG.md)

**最新稳定版 / Latest release: `Mod-v2.6.0`**

</div>

---

> 本项目是非官方社区修改版，与 Icom、原 APRSdroid 作者或 APRS-IS 运营方不存在隶属关系。发射前请确认当地法规、频率、功率、路径和呼号设置。

## 中文说明

### 主要功能

- **WLAN 电台直连与 IC-705 深度联动**：支持 Icom IC-705、IC-9700、IC-7610、IC-905 及自定义型号；半双工 AX.25、AFSK1200、12 kHz 单声道 PCM、CI-V PTT；自定义 CI-V 地址配置与默认预置自动填充；**新增工作频率（Operating Frequency）自动同步与机载 GPS 经纬度/航向/速度联动**（连接设置中选择 IC-705 后动态展开联动开关，可优先使用电台 GPS 发送信标）。
- **现代 APRS 协议全规格实现**：
  - **APRS 1.2 DAO 亚米级高精度定位**：支持 Base91 及微度格式，经纬度分辨率由 ~18.5 米提升至分米/厘米级，发送自动追加、接收自动融入高精度浮点坐标。
  - **APRS 101 第 9 章 PHG 数据扩展**：发射功率（0~81W）、天线高度（10~5120ft）、增益（0~9dB）与主波束指向性设置、发送及台站卡片直观展示。
  - **APRS 101 第 10 章 RNG / PHG 覆盖半径推算**：支持 `RNGxxxx` 直接覆盖报文解析，并依据严密经验物理公式智能推算直频覆盖半径（km）。
  - **APRS 101 第 12 章 WX 气象报文解析**：支持风向、风速、阵风、温度（°F / °C 双单位）、湿度、气压及多时段降雨量，并在台站卡片与详情页展示专属气象徽章与卡片。
  - **APRS 1.01 第 15 章 Queries 指令自动应答**：自动响应 `?PING?`、`?APRST`、`?VER?` 查询，内置防刷流控与冷却保护。
- **智能自适应多编码字符集解码（消除乱码）**：内置 GB2312 / GBK / GB18030 与 UTF-8 字符集智能评分探测模型，并实现 Latin-1 错误转码逆向还原修复算法，彻底解决中国及亚太地区台站中文备注与气象播报乱码问题。
- **USB 电台（Hamlib）**：集成 Hamlib 4.7.2 原生库与 400+ 款常见电台（Icom、Yaesu、Kenwood、Elecraft 等），支持品牌字母排序与即时模糊搜索；内置免 root 本地 Android USB CAT 环回桥接与通用音频后端，提供专属 USB 声卡输入/输出选择与发射音量控制滑块。
- **发射安全与物理排空计算**：依据音频采样率与缓冲区精确推算声卡物理排空延时，彻底杜绝 PTT 释放挂起与空载波长发；保留 PTT OFF ACK 与安全 watchdog 机制。
- **体积与性能优化**：Hamlib 原生库深度优化（`-Os`、函数/数据分节、Dead Code 剪裁与符号剥离），结合 R8 优化，安装包体积由近 100 MB 缩减至 ~44 MB。
- **电台与网络隔离**：电台流量绑定选定 Wi-Fi Network，APRS-IS 等互联网流量仍可走手机默认蜂窝网络。
- 本地 AFSK1200 RX 统一使用 Graywolf，支持 IC-705 12 kHz、AudioRecord 11.025 kHz、Bluetooth SCO 8 kHz；旧 Java modulator 仅用于 TX 音频生成。
- 持久结构化诊断日志与可分享 ZIP，记录网络、电台 session、PTT、恢复和崩溃现场；敏感字段自动脱敏。
- 应用启动时自动静默检查更新；只有发现新版本才弹窗提示，无更新或检查失败均不提示。设置页仍可手动检查；不会后台/定时联网，也不会自动下载/安装 APK。
- Android 16+ 可选 Live Updates / 状态胶囊，显示连接、接收、发射、信标和错误等状态。
- Material 3 + Jetpack Compose；台站、地图、消息、报文四个一级页面统一导航，并支持紧凑列表与即时呼号搜索。
- MapLibre Native 支持高德、OpenStreetMap 和自定义栅格；Google Maps SDK 支持 Google 普通/卫星地图。
- 保留 APRSdroid 原有 APRS-IS、AFSK、KISS（已修复严谨转义与多线程安全）、TNC2、Kenwood、蓝牙、USB、LAN TCP TNC 等路径。

### 兼容性

| 项目 | 要求或状态 |
| --- | --- |
| Android | Android 9+ / API 28 |
| 目标平台 | Android 17 / API 37 |
| 正式 APK | ARM64 OpenGL、ARMv7 OpenGL、x86 OpenGL/Vulkan、x86_64 OpenGL/Vulkan |
| 电台 | Icom WLAN 电台（IC-705 等）或通过 USB OTG / 声卡连接的 Hamlib 支持电台 |
| 默认控制端口 | UDP `50001`（WLAN 模式） |
| 构建环境 | JDK 17、Android SDK API 37 |

源码还保留 ARM64 Vulkan flavor；当前正式本地 AFSK RX 只支持 ARM64/ARMv7。x86 / x86_64 包面向模拟器等 Intel/AMD 平台，可直接构建，但不属于本地 AFSK RX 的正式支持 ABI。

### 下载与安装

从 [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases) 下载对应 APK：

| 文件 | 适用设备 |
| --- | --- |
| `...-arm64-v8a-opengl.apk` | 大多数现代 64 位 ARM 手机 |
| `...-armeabi-v7a-opengl.apk` | 支持 32 位 ARM 应用的设备 |
| `...-x86-opengl.apk` | x86 模拟器等 Intel 32 位平台 |
| `...-x86_64-opengl.apk` | x86_64 模拟器等 Intel/AMD 64 位平台 |

正式 Release 提供 `SHA256SUMS.txt`；部分 64 位系统不能运行 ARMv7 APK，因此 ARMv7 不是 ARM64 的通用回退包。若正式 Release 缺少某个 ABI 的 APK，说明该 ABI 的构建或校验未通过，可用源码自行构建。

应用 ID：`me.nimenhagg.aprsdroidic705mod`。若旧 APK 使用不同签名，Android 可能要求先卸载；卸载会删除该安装的本地设置和诊断日志。

### 电台配置

#### 1. WLAN 电台（IC-705 / IC-9700 / IC-7610 / IC-905）

1. 电台端：以 IC-705 为例，`MENU → SET → WLAN & Internet → WLAN` 开启 WLAN。
2. 使用电台 Access Point 模式，或让手机与电台接入同一局域网。
3. 在 Network User / Pass 建立用户名和密码（用户名和密码最长 16 个 US-ASCII 字符）。
4. 确认控制端口（默认 `50001`）。
5. 手机保持连接电台 Wi-Fi；Android 提示网络无互联网时选择继续连接。
6. 应用内配置：
   - 连接协议选择 `IC-705 Wi-Fi`。
   - 选择预置型号（`IC-705`、`IC-9700`、`IC-7610`、`IC-905` 或 `CUSTOM`），系统会自动填充默认 CI-V 地址（如 IC-705 填充 `A4`）。
   - 若电台修改了 CI-V 地址，可直接在此处输入对应的 2 位十六进制 CI-V 地址。
   - 填写电台 IP、端口及 Network User 凭据。可先使用 IC-705 诊断页确认握手与音频接收。

#### 2. USB 电台（Hamlib）

1. 手机通过 USB OTG 转接线连接电台自带的 USB 接口（如 IC-705、IC-7300、FT-891、FT-991A 等自带 USB 串口与 USB 声卡）。
2. 应用内配置：
   - 连接协议选择 `USB 电台 (Hamlib)`。
   - 点击“电台型号”，在弹出列表中搜索并选择电台型号（已内置 400+ 款常见电台，按品牌连续排列）。
   - 系统将根据型号自动设定推荐波特率与默认 CI-V 地址，也可按需手动调整。
   - 在“音频输入设备”与“音频输出设备”中选择识别到的 USB 声卡（例如 USB Audio Device）。
   - 可在“发射音量”滑块中微调输出电平，防止调制过度过冲或驱动不足。

**安全提示：不要在截图、Issue 或日志中公开电台网络密码。首次发射请使用低功率或合适的假负载，并确认 PTT 能及时释放。**

### 诊断与故障排查

设置中的诊断功能可以导出包含人类可读报告和持久结构化事件的 ZIP。优先查看最早的 network/session failure，而不是只看最后几行日志。

**找不到 IC-705 / 登录失败**

- 检查手机是否仍连接正确 Wi-Fi、IP/端口和 Network User 凭据。
- Android 17 检查“本地网络”权限。
- 确认没有其它客户端占用 IC-705 网络会话。

**连接电台后 APRS-IS 没网**

- 保持蜂窝数据开启，不要把整个 App 或系统默认网络绑定到电台 Wi-Fi。
- 厂商的智能切网/双通道加速功能可能改变路由。

**PTT 未释放**

- 立即在电台上手动解除发射或关闭电台，再停止 App 服务。
- 软件 watchdog 不是硬件互锁。

### 地图

- 高德、OpenStreetMap、自定义在线栅格：MapLibre Native。
- Google 普通、卫星/混合地图：Google Maps SDK。
- OpenStreetMap 请求使用可识别 User-Agent，并显示 attribution。
- 提供 MapLibre Offline 区域下载/管理入口，但当前仅允许用户配置的 Custom tile source；标准 OpenStreetMap `tile.openstreetmap.org` 不提供预取/离线下载，仍只使用正常交互缓存。

### 从源码构建

要求 JDK 17、Android SDK API 37、Rust/Cargo、`protoc`。Graywolf Android helper 使用 NDK `28.2.13676358`；缺失时脚本可通过 sdkmanager 安装。

```bash
git clone https://github.com/nimenhagg/aprsdroid-ic705.git
cd aprsdroid-ic705

bash .github/scripts/test_graywolf_loopback.sh
bash .github/scripts/build_graywolf_android.sh arm64-v8a
bash .github/scripts/build_graywolf_android.sh armeabi-v7a

./gradlew verifyReleaseVersion \
  testArm64OpenglDebugUnitTest \
  lintArm64OpenglDebug \
  assembleArm64OpenglRelease \
  assembleArm32OpenglRelease \
  --no-daemon --stacktrace
```

Graywolf 的 `Cargo.lock` 已提交，native 构建使用 `--locked`。Windows 可通过 WSL / Git Bash / CI 运行 Bash helper。

主要版本基线：AGP 9.3.1、Gradle 9.7.0、Kotlin/Compose Compiler 2.4.20、Compose BOM 2026.08.00、Material 1.14.0、OkHttp 5.3.0、Navigation Compose 2.10.0、MapLibre Native 13.5.1、Graywolf 0.14.13、NDK 28.2.13676358。

详细架构约束见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)；维护规范见 [AGENT.md](AGENT.md)；长期路线见 [ROADMAP.md](ROADMAP.md)。

## English

### Overview

APRSdroid Mod is an unofficial APRSdroid fork adding direct Icom IC-705 WLAN APRS support, modern Android UI, persistent diagnostics, and performance improvements.

**Latest stable release: `Mod-v2.6.0`.**

### Highlights

- **WLAN Radios & IC-705 Linkage**: Direct half-duplex WLAN APRS for Icom IC-705, IC-9700, IC-7610, IC-905, and custom models using AX.25, AFSK1200, 12 kHz mono PCM, and CI-V PTT with configurable CI-V hex addresses. **New operating frequency sync and internal IC-705 GPS linkage** (allows prioritizing radio GPS coordinates for position beacons).
- **Modern APRS Protocol Specifications**:
  - **APRS 1.2 DAO Sub-meter Precision**: Base91 `!w..!` and micro-degree positional resolution, reducing precision grid step from ~18.5 m down to centimeter level.
  - **APRS 101 Chapter 9 PHG Extension**: Power (0~81W), Height (10~5120ft), Gain (0~9dB), and Directivity configuration and tag rendering.
  - **APRS 101 Chapter 10 RNG / PHG Radio Range**: Parsing `RNGxxxx` direct radio coverage range and physical propagation estimation for stations with PHG data.
  - **APRS 101 Chapter 12 Weather Reports**: Full weather parser for wind speed/direction/gusts, temperature (°F/°C), humidity, barometric pressure, and rainfall with dedicated UI cards.
  - **APRS 1.01 Chapter 15 Queries Auto-Reply**: Automatic rate-limited responses to `?PING?`, `?APRST`, and `?VER?` query frames.
- **Adaptive Charset Decoding**: Multi-charset scoring model dynamically detecting GB2312 / GBK / GB18030 vs UTF-8, plus automated Latin-1 stream reverse byte recovery, completely resolving comment and weather character encoding issues in China and APAC.
- **USB Radios (Hamlib)**: Integrated Hamlib 4.7.2 supporting 400+ radios (Icom, Yaesu, Kenwood, Elecraft, etc.), alphabetical brand sorting, instant search, root-free local USB CAT loopback bridge, dedicated USB soundcard I/O selection, and TX output volume control.
- **PTT Timing & Audio Drain Safety**: Physical audio drain duration calculated from sample rate and buffer length to eliminate empty-carrier hanging issues upon PTT release.
- **Size & Performance Optimization**: Native Hamlib build compiled with `-Os` and dead-code stripping, coupled with R8 optimizations, cutting APK size by >50% (down to ~44 MB).
- **Network Isolation**: Selected Android Wi-Fi Network is used only for radio traffic, allowing APRS-IS to keep using the phone's default internet path.
- Graywolf is the production local AFSK1200 RX engine; the legacy Java modulator remains for stable TX PCM generation.
- ACK-aware PTT safety, watchdogs, channel-specific recovery, persistent structured diagnostics and exportable reports.
- GitHub Release checking runs once silently at app startup; failures and an up-to-date result remain silent. Settings also provides a manual check. No background/periodic polling and no automatic APK download/install.
- Android 16+ Live Updates / status chip display, Material 3 / Jetpack Compose UI, station search, and compact lists.
- MapLibre Native for AMap/OSM/custom raster maps and Google Maps SDK for Google map/satellite modes.
- Original APRSdroid APRS-IS, AFSK, KISS (hardened escape and concurrency handling), TNC2, Kenwood, Bluetooth, USB and LAN TNC paths remain available.

### Requirements and downloads

- Android 9+ / API 28
- Target Android 17 / API 37
- Official APKs: ARM64 OpenGL, ARMv7 OpenGL, x86 OpenGL/Vulkan and x86_64 OpenGL/Vulkan. The x86/x86_64 builds target emulators and Intel/AMD platforms; local AFSK RX is officially supported on ARM64/ARMv7 only.
- Radios: Icom WLAN radios (IC-705, etc.) or USB OTG/soundcard radios supported by Hamlib
- JDK 17 / Gradle 9.7.0 / AGP 9.3.1

Download official builds from [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases).

### Build from source

```bash
bash .github/scripts/test_graywolf_loopback.sh
bash .github/scripts/build_graywolf_android.sh arm64-v8a
bash .github/scripts/build_graywolf_android.sh armeabi-v7a
./gradlew verifyReleaseVersion testArm64OpenglDebugUnitTest lintArm64OpenglDebug assembleArm64OpenglRelease assembleArm32OpenglRelease --no-daemon --stacktrace
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for implementation constraints and [AGENT.md](AGENT.md) for maintainer rules.

## 致谢与许可证 / Credits and license

- 基础项目 / Upstream: [ge0rg/APRSdroid](https://github.com/ge0rg/aprsdroid)
- AFSK RX / modem lineage: [Graywolf](https://github.com/chrissnell/graywolf) (GPL-2.0), based on Dire Wolf AFSK demodulator work by John Langner WB2OSZ。
- 协议与实现参考 / Protocol references: [N0BOY/FT8CN](https://github.com/N0BOY/FT8CN), [wfview](https://wfview.org/)
- 地图引擎 / Map engine: [MapLibre Native](https://maplibre.org/maplibre-native/); OpenStreetMap data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright)
- 许可证 / License: [GNU General Public License v2.0](LICENSE)
- 附加授权 / Additional grant: 本项目作者独立编写的电台连接与诊断文件（共 84 个，见 [LICENSING.md](LICENSING.md)）**额外按 GPL-2.0-or-later 提供**，可由其他项目按 “GPLv2 或更高版本” 引用；项目整体仍按 GPL-2.0-only 分发。

  The radio-link and diagnostics files authored inside this repository (84 files, listed in [LICENSING.md](LICENSING.md))
  are **additionally available under GPL-2.0-or-later**; the project as a whole remains GPL-2.0-only.
  来源与核查记录见 [PROVENANCE.md](PROVENANCE.md)。

项目开发包含 AI 辅助协作；维护者仍负责审查、测试和发布。

AI-assisted development is used in this repository; maintainers remain responsible for review, testing and releases.
