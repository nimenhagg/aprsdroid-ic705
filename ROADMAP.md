# APRSdroid Mod：后续开发路线

> 本文件只记录**尚未完成的长期方向**。已经合并到 `main` 的工作不再作为“待办 PR”重复列出；具体历史请看 [CHANGELOG.md](CHANGELOG.md)，当前工程不变量请看 [AGENT.md](AGENT.md)。

## 1. 当前基线

截至 `Mod-v2.7.0`：

- Hamlib Android 构建、JNI、USB CAT loopback bridge、通用 RadioControl、USB Audio 路由和 TX drain 已进入主线。
- USB 电台设置、400+ Hamlib 电台型号选择、品牌排序、自定义 CI-V 地址已经进入主线。
- IC-705 WLAN 支持 IC-705、IC-9700、IC-7610、IC-905 预置以及 CUSTOM CI-V 地址；支持 CI-V 工作频率自动同步与内部 GPS 联动。
- APRS 现代协议全面实现：APRS 1.2 DAO 亚米级高精度位置扩展、APRS 101 第 9 章 PHG / 第 10 章 RNG 覆盖半径估算、第 12 章 WX 气象解析、第 13 章遥测报文深度解析与校准方程、第 14 章第三方嵌套报文递归解包与网关中继追踪、第 15 章 Queries 协议指令自动应答。
- 协议字段清洗与备注净化：剥离 PHG、RNG、DAO、海拔、频率与气象碎片，存储与 UI 界面备注全面净化。
- 数据库结构升级至 v5：stations 表新增 phg 与 rng 独立列，实现平滑升级与存量备注回填清洗。
- 智能自适应多编码字符集解码器（GB2312/GBK/GB18030/UTF-8 与 Latin-1 恢复），彻底消除国内台站备注与气象乱码。
- 本地 AFSK1200 RX 使用 Graywolf；TX 继续使用稳定的 APRSdroid PCM 生成链。
- 正式 Release 当前提供 ARM64/ARMv7 OpenGL APK。
- 项目整体仍为 GPL-2.0-only；[LICENSING.md](LICENSING.md) 列出的独立文件另有 GPL-2.0-or-later 授权。

## 2. 后续方向

### 2.1 IC-705 WLAN 核心可复用化

目标是把与 APRSdroid 数据模型、Android UI 和服务生命周期无关的 IC-705 核心整理成清晰的独立边界，便于未来复用到其它 APRS/TNC 项目。

优先拆分：

1. protocol codecs；
2. UDP transport；
3. session/recovery state；
4. PTT state machine；
5. RX/TX audio packetization；
6. 与 APRSdroid 的 adapter。

约束：

- 不复制 APRSdroid 的 AX.25/AFSK/PCM 数据模型进核心；
- 不把 Android Activity/Service 生命周期塞进协议核心；
- 不改变现有 IC-705 真机行为；
- 拆分前继续遵守 [LICENSING.md](LICENSING.md) 的逐文件授权边界；
- wfview 仍只作为行为/协议对照，不能直接引入其 GPL-3.0-only 实现。

### 2.2 Icom LAN 泛化

在现有 IC-705 路径稳定的基础上，继续验证更多 Icom WLAN 电台。

原则：

- 型号能力表与 transport 分离；
- CI-V 地址、端口和 capability 不写死在 session 状态机；
- runtime capability、实际音频流到达和已验证型号行为共同决定能力；
- 每个新型号都区分“Hamlib/协议上支持”和“本项目已经真机验证”。

新增型号必须经过：

1. handshake/control；
2. CI-V；
3. RX audio；
4. PTT ON/OFF；
5. TX audio；
6. 长时间收发；
7. Wi-Fi 断开/重连；
8. 低功率或假负载 RF 验证。

### 2.3 Hamlib 多电台继续扩展

继续使用组合式模型：

`连接方式 + 电台型号 + 控制 + 音频 + PTT`

而不是为每个电台重新创建 APRS backend。

后续重点：

- 更多真实硬件验证；
- USB serial permission / detach / reconnect 的边界测试；
- USB Audio 设备变化与安全 PTT release；
- Hamlib backend 能力与实际硬件能力的差异诊断；
- 保持 CAT、音频和 APRS packet 层解耦。

### 2.4 APK 体积与构建产物优化

继续优化正式 APK 的体积，但不能以删除运行时能力或破坏 ABI 为代价。

优先检查：

- native library strip / dead-code elimination；
- R8 与资源裁剪；
- MapLibre native library；
- ABI 产物是否包含不必要内容；
- Release CI 中源码构建产物、APK 中 native library 和 source archive 是否一致。

每次体积优化都必须同时验证功能、ABI、16 KiB alignment、签名和 SHA-256。

### 2.5 UI / Android 平台演进

继续维护 Compose / Material 3 / predictive back / Android 17 行为。

原则：

- 不恢复旧 XML 一级页面；
- 不恢复全局 Activity animation override；
- 不用设备/ROM 特判掩盖生命周期或状态机问题；
- Live Updates、权限和前台服务行为以当前 Android 平台实际要求为准；
- 真机视觉与交互验证不能由 CI 替代。

### 2.6 诊断与可维护性

继续让结构化诊断成为网络、电台和 PTT 问题的主要排障入口。

后续可扩展：

- 更明确的 session/recovery timeline；
- 更容易脱敏和分享的诊断包；
- IC-705 / Hamlib / USB Audio 的统一连接状态模型；
- 对关键 recovery、PTT 和 transport 竞态增加回归测试。

## 3. 明确不作为当前路线的事项

除非重新评估并明确提出，不恢复：

- 旧 XML UI；
- Mapsforge / 旧专用瓦片下载器；
- 全局 Activity 动画；
- IC-705 “任一通道超时即 teardown”模型；
- PTT OFF 未经电台确认就假定 RX；
- Graywolf RX 失败时静默回退 legacy demodulator；
- 把整个 App 绑定到电台 Wi-Fi；
- 为每个电台型号复制一套 APRS backend。

## 4. 推进原则

后续工作继续采用小步、可验证的 PR。任何涉及真实 RF、PTT、音频或网络 recovery 的修改，都必须先通过单元/CI 验证，再进行低功率或假负载真机验证。

路线图不是自动任务队列；只有明确提出某项工作时才开始实施。
