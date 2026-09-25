# APRSdroid Mod：Hamlib 与多电台开发路线

> 本文件描述长期开发路线与阶段性架构计划。它不是当前任务指令。只有用户明确要求推进某个阶段时，Agent 才应按此路线实施。

## Hamlib 与多电台架构规划

> **状态：分阶段实现中，尚未形成稳定版多电台能力。** PR1（Android 构建基础）、PR2（最小 JNI 交互）与 PR3（通用 RadioControl 契约与适配层）已合并至主线；当前正在实施 PR4（Android USB CAT bridge 本地回环桥接）。不得把后续目标描述成当前已发布能力。当前 IC-705 WLAN 路径、PTT 安全状态机、Graywolf RX 和既有设置仍以本文件前文的现状约束为准。

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

Hamlib PR1 已把项目 `minSdk` 从 27 提升到 **28**，与固定 Hamlib 4.7.2 的 Android NDK 构建基线对齐。后续不得在没有完整兼容性与 native 构建验证时下调该基线。

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

#### PR 4：Android USB CAT bridge (已合并到主线)

- Android USB Host/serial 权限与生命周期；
- loopback CAT bridge；
- Hamlib network pathname 接入；
- detach/reconnect/EOF/timeout 诊断；
- 首先验证 Icom CI-V 字节流。

验收：真机可由 Hamlib 对 USB 电台稳定完成读频、设频、mode 和无 RF 风险的控制操作。

#### PR 5：通用 Radio Audio backend (已实现)

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

