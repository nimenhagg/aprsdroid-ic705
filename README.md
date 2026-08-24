# APRSdroid IC-705

APRSdroid 的 Icom IC-705 Wi-Fi 直连修改版 / An APRSdroid fork with direct Icom IC-705 Wi-Fi support.

[中文说明](#中文说明) · [English](#english) · [更新日志 / Changelog](CHANGELOG.md) · [下载 / Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases)

当前版本 / Current release: **1.6.3-ic705**

> 本项目是社区维护的非官方修改版，与 Icom、APRSdroid 原作者或 APRS-IS 运营方不存在隶属关系。发射前请确认当地法规、频率、功率、路径和呼号设置。
>
> This is an unofficial community fork. It is not affiliated with Icom, the original APRSdroid project, or APRS-IS operators. Verify your local regulations, frequency, power, path, and callsign before transmitting.

## 中文说明

### 项目简介

APRSdroid IC-705 在 [APRSdroid](https://aprsdroid.org/) 基础上增加了 IC-705 内置 Wi-Fi 的 APRS 收发能力。手机可以直接连接电台热点或与电台处于同一局域网，通过 UDP 完成会话、CI-V PTT 控制和音频传输，无需音频线、OTG 转接器或外接蓝牙 TNC。

IC-705 的 UDP Socket 会逐个绑定到 Android 的 Wi-Fi `Network`，因此电台流量可以走 Wi-Fi，APRS-IS 等互联网流量仍可使用手机的默认网络（例如 4G/5G）。

### 主要功能

- IC-705 Wi-Fi 半双工 APRS 收发：AX.25、AFSK1200、12 kHz PCM、CI-V PTT。
- PTT 绝对超时看门狗：异常情况下尝试自动释放 PTT，降低持续发射风险。
- IC-705 连接诊断页：查看会话状态、控制事件、音频统计和已解码帧。
- APRS-IS TCP / HTTP POST / UDP，以及 AFSK、KISS、TNC2、Kenwood 等原 APRSdroid 连接方式。
- 蓝牙 SPP、USB 串口和局域网 TCP TNC。
- 智能信标、周期定位、手动位置、台站列表、消息、日志和多地图源。
- Material 3 / Material You 界面，支持简体中文与英文。

### 兼容性

| 项目 | 要求或状态 |
| --- | --- |
| Android | Android 8.1+ |
| 目标平台 | Android 17 / API 37 |
| 电台 | Icom IC-705，启用 WLAN 与 Network User |
| 默认控制端口 | UDP `50001`（CI-V 与音频通常使用后续端口） |
| 默认热点地址 | 常见为 `192.168.59.1`，以电台实际网络为准 |
| 构建环境 | JDK 17、Android SDK API 37 |

不同 IC-705 固件的菜单名称可能略有差异。建议先升级到稳定固件，并在低功率或假负载环境完成首次发射测试。

### 安装

从 [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases) 下载最新 APK。若从旧签名或其他 APRSdroid 分支迁移，Android 可能要求先卸载旧应用；卸载会删除该应用的本地设置和日志，请先自行备份需要的数据。

本项目的应用 ID 是 `me.nimenhagg.aprsdroidic705mod`。发布 APK 是否已签名以对应 Release 的说明和 Android 安装界面为准。

### IC-705 配置

1. 在电台中打开 `MENU` → `SET` → `WLAN & Internet` → `WLAN`。
2. 选择电台热点模式（Access Point）或让手机与电台加入同一个路由器网络。
3. 在 `Network User / Pass` 中建立用户名和密码。当前实现要求用户名非空，用户名和密码最长 16 个 US-ASCII 字符。
4. 确认控制端口，默认是 `50001`。
5. 让手机连接到电台所在 Wi-Fi。若 Android 提示该 Wi-Fi 无互联网，请选择保持连接。

不要在截图、Issue 或日志中公开电台的网络密码。

### 应用配置与首次连接

1. 打开“设置”，填写业余无线电呼号、SSID、数字中继路径和位置来源。
2. 进入“连接偏好设置”，将“连接协议”设为 `IC-705 Wi-Fi`。
3. 填写电台 IP、控制端口、Network User 用户名和密码。
4. 可先打开“IC-705 Wi-Fi 诊断”确认握手、音频接收与 AFSK 解码状态。诊断页不会发射。
5. 返回主页，点击单次位置或开始记录路径。首次启动时按系统提示授予必要权限。
6. 首次发射请使用低功率或假负载，并确认 PTT 能及时释放。

### 权限说明

应用按当前连接方式和位置来源请求权限，不会为所有后端一次性索取全部权限。

| 权限 | 何时使用 |
| --- | --- |
| 本地网络 | Android 17 上连接 IC-705 Wi-Fi 或局域网 TCP TNC |
| 精确/大致位置 | 智能信标、周期定位或使用电台 GPS 的 Kenwood 模式 |
| 通知 | Android 13+ 的前台服务状态通知 |
| 麦克风 | 仅 AFSK 音频后端；IC-705 Wi-Fi 不使用手机麦克风 |
| 蓝牙 | 仅蓝牙 SPP / 蓝牙音频连接 |

拒绝必需权限后，应用不会启动对应 APRS 服务；可以在 Android 的应用信息页重新授权。

### 常见问题

**找不到 IC-705 或握手失败**

- 确认手机仍连接电台 Wi-Fi，电台 IP 和控制端口正确。
- 确认 Network User 凭据为 US-ASCII 且不超过 16 字符，并且没有其他客户端占用会话。
- Android 17 上确认“本地网络”权限已允许。
- 打开 IC-705 诊断页查看最近事件；网络变化后停止并重新开始诊断。

**连接电台后 APRS-IS 没有互联网**

- 保持蜂窝数据开启，不要手动把整个应用绑定到 Wi-Fi。
- 本项目只绑定 IC-705 UDP Socket；设备厂商的“双通道加速”“智能切网”仍可能改变路由，可尝试关闭相关系统功能。

**PTT 未释放**

- 立即在电台上手动解除发射或关闭电台，再停止应用服务。
- 不应仅依赖软件看门狗作为射频安全措施。复现后请附脱敏诊断信息提交 Issue。

### 网络与安全边界

- IC-705 LAN 协议和部分 APRS 服务器本身使用明文 UDP/TCP；本项目保留该兼容行为。HTTP POST 后端使用 Android 原生 `HttpURLConnection`，但裸主机配置仍按兼容规则连接明文 `http://` 端口 `8080`。请只在可信网络使用，并理解凭据与流量可能被同网段设备观察。
- 配置文件导入直接读取 Android 文档提供器授予的 `content://` 数据流，不依赖 `_data` 列或外部存储绝对路径。
- 应用禁用 Android 备份，但连接凭据仍保存在应用本地偏好设置中。不要在不受信任或已 Root 的设备保存敏感凭据。
- 项目包含发射功能。软件故障、Wi-Fi 丢包或电台配置错误都可能造成意外发射；操作者始终对合法合规使用负责。

### 从源码构建

要求：JDK 17、Android SDK API 37。Gradle Wrapper 会固定使用 Gradle 9.5.0；首次构建会下载依赖。Java 17 是 AGP 9.3 的官方基线，本项目没有为了追求版本数字改用 Java 21。

```bash
git clone https://github.com/nimenhagg/aprsdroid-ic705.git
cd aprsdroid-ic705

./gradlew verifyReleaseVersion testDebugUnitTest lintDebug assembleRelease --no-daemon
```

Windows PowerShell 使用 `./gradlew.bat`。Debug APK 位于 `build/outputs/apk/debug/`，Release 构建产物位于 `build/outputs/apk/release/`；没有发布密钥时通常是未签名 APK。

可在 `local.properties` 中提供 Google Maps Key：

```properties
mapsApiKey=YOUR_ANDROID_RESTRICTED_KEY
```

主要构建版本：

- Android Gradle Plugin 9.3.2
- Gradle 9.5.0
- Kotlin / Compose Compiler 2.2.10（AGP 9 内置 Kotlin）
- Compose BOM 2026.08.00
- Java 17

### 开发与发布

- Kotlin 与 Java 源码位于非标准的 `src/` 目录，单元测试位于 `test/java/`。
- 修改 IC-705 发射链时必须保留 PTT OFF 和绝对超时看门狗的安全语义，并增加对应测试。
- 发布时同时更新 `build.gradle`、`CHANGELOG.md`、`AI_CONTEXT.md` 和 README 中的版本信息。
- 标签格式是 `v<版本>-ic705`，例如 `v1.6.3-ic705`；CI 会验证标签和 APK 版本一致。
- 完整的工程交接信息见 [AI_CONTEXT.md](AI_CONTEXT.md)。

## English

### Overview

APRSdroid IC-705 extends [APRSdroid](https://aprsdroid.org/) with direct APRS receive and transmit support over the IC-705's built-in Wi-Fi. The phone connects to the radio's access point, or joins the same LAN, and uses UDP for session control, CI-V PTT, and audio. No audio cable, USB OTG adapter, or external Bluetooth TNC is required for this mode.

Each radio UDP socket is bound to Android's Wi-Fi `Network`. Radio traffic can therefore use Wi-Fi while APRS-IS traffic continues over the phone's default internet path, such as mobile data.

### Highlights

- Half-duplex IC-705 Wi-Fi APRS: AX.25, AFSK1200, 12 kHz PCM, and CI-V PTT.
- An absolute PTT watchdog that attempts to release PTT after an abnormal timeout.
- An IC-705 diagnostics screen for session events, audio statistics, and decoded frames.
- APRS-IS TCP / HTTP POST / UDP plus APRSdroid's AFSK, KISS, TNC2, and Kenwood modes.
- Bluetooth SPP, USB serial, and LAN TCP TNC transports.
- SmartBeaconing, periodic/manual positions, stations, messages, logs, and multiple map sources.
- Material 3 / Material You UI with Simplified Chinese and English resources.

### Requirements

| Item | Requirement or status |
| --- | --- |
| Android | Android 8.1+ |
| Target platform | Android 17 / API 37 |
| Radio | Icom IC-705 with WLAN and a Network User enabled |
| Default control port | UDP `50001`; CI-V and audio normally use the following ports |
| Typical AP address | `192.168.59.1`; verify against the radio's actual network |
| Build environment | JDK 17 and Android SDK API 37 |

Menu labels can vary by radio firmware. Perform the first transmit test at low power or into a suitable dummy load.

### Install

Download the current APK from [GitHub Releases](https://github.com/nimenhagg/aprsdroid-ic705/releases). Android may require uninstalling an APK signed by another key or another APRSdroid fork first. Uninstalling removes that app's local preferences and logs, so back up anything you need.

The application ID is `me.nimenhagg.aprsdroidic705mod`. Check the individual Release notes and Android's installer for signing details.

### Configure the radio

1. Enable `MENU` → `SET` → `WLAN & Internet` → `WLAN` on the IC-705.
2. Use Access Point mode, or connect the phone and radio to the same router.
3. Create credentials under `Network User / Pass`. The current implementation requires a non-empty username; usernames and passwords are limited to 16 US-ASCII characters.
4. Verify the control port, normally `50001`.
5. Connect Android to the radio's Wi-Fi. If Android reports that the network has no internet, choose to remain connected.

Never post the radio password in screenshots, Issues, or logs.

### Configure the app

1. In Settings, enter your amateur-radio callsign, SSID, digipeater path, and location source.
2. Open Connection Preferences and select `IC-705 Wi-Fi` as the connection protocol.
3. Enter the radio IP, control port, Network User username, and password.
4. Optionally run IC-705 Wi-Fi Diagnostics first. The diagnostics screen receives and decodes but does not transmit.
5. Return to the home screen and request a single position or start tracking. Grant the permissions Android requests.
6. Make the first transmission at low power or into a dummy load, and verify that PTT releases promptly.

### Permissions

Permissions are selected from the configured backend and location source instead of requesting every capability globally.

| Permission | Used for |
| --- | --- |
| Local network | IC-705 Wi-Fi and LAN TCP TNC on Android 17 |
| Fine/approximate location | SmartBeaconing, periodic GPS, or Kenwood GPS mode |
| Notifications | Foreground-service status on Android 13+ |
| Microphone | AFSK audio backend only; IC-705 Wi-Fi does not use the phone microphone |
| Bluetooth | Bluetooth SPP or Bluetooth audio transports only |

If a required permission is denied, the corresponding APRS service is not started. You can grant it later from Android's App info screen.

### Troubleshooting

For discovery or login failures, verify the active Wi-Fi, radio IP, port, credentials, and Android 17 Local network permission. Another client may already own the radio session. Use the diagnostics screen for redacted session events and restart diagnostics after a network change.

If APRS-IS loses internet access while the radio is connected, keep mobile data enabled. This project binds only the IC-705 UDP sockets, but vendor-specific “smart network switching” features can still override routing.

If PTT remains asserted, release it on the radio or turn the radio off immediately, then stop the app service. The software watchdog is a fallback, not a substitute for RF safety procedures.

### Network and security boundary

- The IC-705 LAN protocol and some APRS servers use cleartext UDP/TCP. This project preserves that compatibility behavior. The HTTP POST backend uses Android's native `HttpURLConnection`, while a bare host setting still resolves to cleartext `http://` on port `8080`. Use trusted networks and assume that peers on the LAN may observe credentials or traffic.
- Profile imports read the `content://` stream granted by Android's document provider directly; they do not query `_data` or reconstruct external-storage paths.
- Android backup is disabled, but connection credentials are still stored in the app's local preferences. Do not store sensitive credentials on an untrusted or rooted device.
- This software can key a transmitter. The operator remains responsible for lawful operation and for handling software, Wi-Fi, and radio failures safely.

### Build from source

Install JDK 17 and Android SDK API 37. The wrapper pins Gradle 9.5.0 and downloads dependencies during the first build. Java 17 remains the official AGP 9.3 baseline; this project does not require Java 21.

```bash
git clone https://github.com/nimenhagg/aprsdroid-ic705.git
cd aprsdroid-ic705

./gradlew verifyReleaseVersion testDebugUnitTest lintDebug assembleRelease --no-daemon
```

Use `./gradlew.bat` in Windows PowerShell. APKs are written below `build/outputs/apk/`; a local release build is normally unsigned unless release signing properties are supplied. To enable Google Maps, add an Android-restricted `mapsApiKey` to `local.properties`.

The main toolchain is AGP 9.3.2, Gradle 9.5.0, built-in Kotlin / Compose Compiler 2.2.10, Compose BOM 2026.08.00, and Java 17.

### Contributing and releasing

- Production sources use the legacy `src/` layout; unit tests live in `test/java/`.
- Changes to IC-705 transmit code must preserve PTT OFF and absolute-watchdog safety semantics and include tests.
- A release updates `build.gradle`, `CHANGELOG.md`, `AI_CONTEXT.md`, and the version shown here.
- Tags use `v<version>-ic705`, for example `v1.6.3-ic705`; CI rejects a tag that does not match APK metadata.
- See [AI_CONTEXT.md](AI_CONTEXT.md) for the maintainer and AI handover guide.

## 致谢与许可证 / Credits and license

- 基础项目 / Upstream: [ge0rg/APRSdroid](https://github.com/ge0rg/aprsdroid)
- 协议与实现参考 / Protocol and implementation references: [N0BOY/FT8CN](https://github.com/N0BOY/FT8CN), [wfview](https://wfview.org/)
- 许可证 / License: [GNU General Public License v2.0](LICENSE)

项目开发包含 AI 辅助协作；所有变更仍应由维护者审查、测试并承担发布责任。

AI-assisted development is used in this repository; maintainers remain responsible for review, testing, and releases.
