# APRSdroid IC-705 (Wi-Fi Mod)

这是一个给 [APRSdroid](https://aprsdroid.org/) 添加 **Icom IC-705 Wi-Fi 直连收发** 支持的修改版。

平时野外通联或车台使用时，不需要再插音频线、OTG 转接头或外接蓝牙 TNC，手机直接连上 IC-705 自带的 Wi-Fi 热点，就能收发 APRS 报文、自动发定位信标、在地图上查看附近友台。

---

## 📋 更新日志

详细版本演进与改动记录请查看 [CHANGELOG.md](CHANGELOG.md)。

---

## 🛠️ 主要改动与功能

- **📻 IC-705 Wi-Fi 直连（半双工收发）**
  - 对接 IC-705 内置的局域网 UDP 协议（Control: 50001 / CI-V: 50002 / Audio: 50003）。
  - 内置软件 AFSK1200 调制解调，CI-V 自动控制 PTT。
  - 音频流参考了 [FT8CN](https://github.com/N0BOY/FT8CN) 的 12kHz 采样率与 20ms（每秒 50 包）分包节奏，解决无线传输抖动导致的空载波问题。
- **🌐 独立 Wi-Fi 绑定**
  - 仅把电台通信走 IC-705 的 Wi-Fi，手机自带的 4G/5G 移动流量和 APRS-IS 互联网上报不受影响。
- **🛡️ 后台与退出优化**
  - 常驻通知栏增加了「完全退出」按钮，点击可直接断开电台连接并退出后台。
  - 移除了原版的开机自启（BOOT_COMPLETED）机制。
- **🎨 界面与细节**
  - 支持 Material 3 / Material You 动态取色与卡片化显示。
  - 完善了中文设置项说明，更换了新图标。

---

## 🤖 AI 协同开发交接指引 (AI Handover & Context)

本项目维护了专用的 AI 开发提示词与工程交接文档 [AI_CONTEXT.md](AI_CONTEXT.md)，任何 AI 编程助手（如 Antigravity / Claude / ChatGPT / Cursor / Copilot 等）均可直接读取该文件作为 System Prompt 进行无缝续护与功能开发。

## 🤖 制作说明 (Vibe Coding)

本项目通过 **Vibe Coding（AI 辅助编程）** 协同开发，主要参与模型：
- **Claude Opus 4.6**
- **GPT-5.6 Sol**
- **DeepSeek V4 Flash**
- **Gemini 3.7 Flash**

---

## 📖 使用方法

### 1. IC-705 电台设置
1. 打开电台菜单：`MENU` -> `SET` -> `WLAN & Internet` -> `WLAN` -> 开 (`ON`)。
2. **Connection Type** 选择 `Access Point`（热点模式），或者让电台加入随身 Wi-Fi 路由器。
3. 进入 `WLAN & Internet` -> `Network User / Pass` 添加一个连接账号和密码。
4. 确认 `Control Port` 为 `50001`。
5. 手机连接到电台的 Wi-Fi。

### 2. APRSdroid 设置
1. 打开应用，进入 **设置 / 首选项** -> **连接偏好设置**。
2. **连接协议** 选择 `IC-705 Wi-Fi`。
3. **电台 IP** 填入电台地址（热点模式一般默认是 `192.168.59.1`）。
4. 填入刚才在电台里设置的用户名和密码。
5. 返回主界面，点击「开始记录路径」即可开始工作。

---

## 💻 编译说明

- **环境**：JDK 17 + Android SDK（API 36）
- **命令**：
  ```bash
  git clone https://github.com/nimenhagg/aprsdroid-ic705.git
  cd aprsdroid-ic705
  ./gradlew testDebugUnitTest lintDebug
  ./gradlew assembleDebug
  ```

---

## 📄 致谢与协议

- 本项目遵循 GPLv2 协议开源。
- 感谢 **[ge0rg/aprsdroid](https://github.com/ge0rg/aprsdroid)** 的开源底座。
- 感谢 **[N0BOY/FT8CN](https://github.com/N0BOY/FT8CN)** 与 **[wfview](https://wfview.org/)** 在 Icom 网络协议和音频分包逻辑上的参考。
