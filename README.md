# APRSdroid (Icom IC-705 Wi-Fi Direct Mod)

[APRSdroid](https://aprsdroid.org/) 是 Android 平台上最经典知名的业余无线电 APRS（自动位置报告系统）应用程序。

本项目为 **APRSdroid 的增强衍生版本（Mod）**，新增了对 **Icom IC-705** 电台的原生 Wi-Fi 直连双向收发（Full-Duplex Transceiver Backend）支持。**无需任何外部音频线、蓝牙 TNC 或 USB 声卡，手机直连 IC-705 的 Wi-Fi 热点即可进行全自动 APRS 定位信标发射、信息收发与邻近站点实时图上追踪。**

---

## 🤖 Vibe Coding 创作声明 (AI Co-Creation)

本项目由 **Vibe Coding（AI 全程协同编程）** 深度驱动开发完成，涵盖从 Icom UDP LAN 协议逆向分析、FT8CN 12kHz 架构对齐、AFSK1200 纯软件调制解调器实现、Android 现代构建升级到真机联合调试的全过程。

参与协同创作的 AI 大模型包括：
- 🧠 **GPT-5.6 Sol**
- ⚡ **DeepSeek V4 Flash**
- 🚀 **Gemini 3.7 Flash**

---

## ✨ 核心特性与架构 (Key Features)

- **📻 IC-705 Wi-Fi 原生全双工通信 (UDP LAN Protocol)**
  - 直接实现 Icom 官方网络协议握手（Control: `50001` / CI-V: `50002` / Audio: `50003`）。
  - 支持 UDP Token 鉴权、能力协商（0x90/0x50）与心跳保活。
- **⚡ FT8CN 标杆架构 12kHz 采样率与 20ms 分包时钟**
  - 音频流彻底对齐 [FT8CN](https://github.com/N0BOY/FT8CN) 工业级标准：采用 **12,000 Hz 采样率** 与 **240 采样/20ms 精确分包（每秒 50 包）**。
  - 引入 60ms Pre-Roll 预充安全水库与 150ms DSP 排空延时，**彻底根除了 Android Wi-Fi 调度抖动导致的发射空载波与中途断调问题**。
- **📡 纯软件 AFSK1200 高保真调制/解调**
  - 内置纯软件 AX.25 / AFSK1200 调制解调器，直接生成/解调 PCM16LE 数字音频流。
  - CI-V 自动 PTT 状态机：支持高频连续手动点击「发送位置」与定时「记录路径」自动发射。
- **🌐 独立 Wi-Fi 路由绑定 (Network-Bound Sockets)**
  - 自动绑定 Android 系统的 IC-705 Wi-Fi 链路，**绝不影响手机自身的 4G/5G 移动数据与 APRS-IS 互联网上行**。
- **🛡️ Phase 2 生产级保活与退出加固**
  - 前台常驻保活通知栏专属 **「完全退出」** 按钮，一键释放电台连接、停止定位并销毁常驻通知。
  - **彻底移除原版开机自启（`BOOT_COMPLETED`）机制**，避免开机静默唤醒。
- **🛠️ 现代化工程构建**
  - 升级支持 Gradle 8.4 + AGP 8.1.3 + JDK 17 + Scala 2.11 / Kotlin 1.9 混合构建，内置 122 项全量自动化单元测试。

---

## 🚀 快速上手 (Quick Start)

### 1. IC-705 电台端设置
1. 打开 IC-705 菜单：`MENU` -> `SET` -> `WLAN & Internet` -> `WLAN` -> `ON`。
2. 将 **Connection Type** 设置为 `Access Point`（或加入同一局域网路由器）。
3. 在 `WLAN & Internet` -> `Network User / Pass` 中添加一个用户（设置自定义用户名与密码）。
4. 确认 `Control Port` 为 `50001`。
5. 手机连接到 IC-705 的 Wi-Fi 热点。

### 2. APRSdroid 手机端设置
1. 打开 APRSdroid，进入 **设置 / 首选项 (Preferences)**。
2. **连接偏好设置 (Connection Preferences)**：
   - **连接协议 (Connection Protocol)**：选择 `IC-705 Wi-Fi`。
   - **电台 IP (Radio IP)**：输入 IC-705 的 IP 地址（热点模式默认通常为 `192.168.59.1` 或路由器分配给电台的局域网 IP）。
   - **控制端口 (Control Port)**：`50001`。
   - **用户名与密码**：输入在电台端设置的网络用户名与密码。
3. 返回主界面，点击 **「开始记录路径」** 开启自动位置信标，或点击 **「发送位置」** 进行手动单次发射。

---

## 💻 源码编译 (Compilation)

### 环境要求
- **JDK**：OpenJDK 17
- **Android SDK**：API Level 34 / Build-Tools 34.0.0

### 构建命令
```bash
git clone https://github.com/nimenhagg/aprsdroid-ic705.git
cd aprsdroid-ic705

# 运行自动化单元测试
./gradlew testDebugUnitTest

# 编译 Debug APK
./gradlew assembleDebug
```
编译产物位于 `build/outputs/apk/debug/APRSdroid-debug.apk`。

---

## 📜 开源协议与致谢 (License & Acknowledgments)

- 本项目基于 GPLv2 协议开源。
- 感谢 **[ge0rg/aprsdroid](https://github.com/ge0rg/aprsdroid)** 提供的 APRSdroid 原版基础。
- 感谢 **[N0BOY/FT8CN](https://github.com/N0BOY/FT8CN)** 在 Icom Wi-Fi LAN UDP 协议与 12kHz 音频分包时钟算法上的标杆参考。
- 感谢 **[wfview](https://wfview.org/)** 团队在 Icom 网络协议分析领域的开源贡献。
