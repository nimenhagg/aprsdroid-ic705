# 🤖 APRSdroid IC-705 Mod - 项目概述与 AI 协同指南

> 💡 **此文档旨在为开发者及 AI 助手（如 Antigravity / Claude / ChatGPT / Cursor 等）提供简洁直观的工程背景与开发指引，方便 Fork 与二次开发。**

---

## 📌 1. 项目简介 (Project Overview)

**APRSdroid IC-705 Mod** 是基于著名开源业余无线电 APRS 客户端 [APRSdroid](https://aprsdroid.org/) 进行深度定制与现代化重构的修改版：
* **核心目标**：添加对 **Icom IC-705 电台内置 Wi-Fi 的局域网直连支持**（无需物理音频线、OTG 转接线或外接硬件 TNC）。
* **网络隔离**：电台通信与手机蜂窝流量隔离，直连电台的同时仍可正常使用 4G/5G 进行 APRS-IS 互联网上报。
* **设计语言**：全面拥抱 **Material 3 / Material You**，提供高对比度户外呼号排版与现代化界面体验。

---

## 🏗️ 2. 技术栈与架构 (Tech Stack)

| 模块 / 组件 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **基础语言与框架** | **Kotlin 1.9.24 / Java 17** | Kotlin 为主，保留少量 Java/JNI 代码，无 Scala 依赖 |
| **直连扩展** | **Kotlin / Java Executors / UDP** | IC-705 UDP 握手、PCM 音频流与网络管理 |
| **JDK 版本** | **Java 17 (Temurin JDK 17)** | 标准构建运行环境 |
| **Android SDK** | `minSdk 24`, `compileSdk 36`, `targetSdk 36` | 支持 Android 7.0+，目标 Android 16 |
| **构建系统** | **Gradle 8.8 + AGP 8.4.2** | 标准 Android Gradle 插件体系 |
| **当前版本** | **1.5.15-ic705** | Android `versionCode 2026082415` |

---

## 📻 3. IC-705 直连核心原理 (IC-705 Integration)

* **Control 端口 (UDP 50001)**：处理与 IC-705 的身份认证与连接维持。
* **CI-V 端口 (UDP 50002)**：执行 CI-V 指令交互，控制电台 PTT 收发与状态监听。
* **Audio 端口 (UDP 50003)**：接收与发送 16-bit、12kHz PCM 原始音频流，采用 20ms / 50fps 分包节奏，对接内置 AFSK 1200 软解调器。
* **多网卡流量绑定**：使用 Android `Network.bindSocket` 逐个绑定 IC-705 UDP Socket，仅让电台流量通过 Wi-Fi，不改变 APRS-IS 等其他连接的系统路由。

---

## 💻 4. 编译与构建 (Build & Run)

```bash
# 1. 确保 JDK 17 与 Android SDK 环境变量已配置
# 2. 先执行离线单元测试与 Android Lint
./gradlew testDebugUnitTest lintDebug --no-daemon

# 3. 编译 Release APK
./gradlew assembleRelease --no-daemon

# 4. 生成的未签名 APK 位于：
# build/outputs/apk/release/APRSdroid-release-unsigned.apk
```

---

## 🤖 5. 给 AI 助手的开发提示词 (Prompt Guidelines for AI)

若作为 AI 助手在此代码库上继续迭代开发，请遵循以下工程准则：
1. **代码规范**：业务代码以 Kotlin 为主；保留的 Java/JNI 模块应按现有边界维护，新增代码优先采用 Kotlin 1.9+。
2. **UI 规范**：使用 Material 3 配色（`?attr/colorPrimary`），避免使用 Android 2.x 时代的过时资源（如 `android.R.drawable.ic_dialog_*`）。
3. **版本迭代**：每次发布必须同步递增 `build.gradle` 中的 `mod_version` / `mod_version_code`，在 `CHANGELOG.md` 记录改动，并创建格式为 `v<版本>-ic705` 的 Tag。CI 的 `verifyReleaseVersion` 会阻止 Tag 与 APK 元数据不一致的发布。
