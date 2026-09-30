# 许可说明 / Licensing

> 本项目**整体**仍然是 **GPL-2.0-only**（上游 APRSdroid 的决定，见 [LICENSE](LICENSE) 与 [README](README.md)）。
> 本文件只说明一件事：**哪些由本项目作者独立编写的文件，额外按 GPL-2.0-or-later 提供**，以便这些文件（例如电台连接功能）被其他项目引用。
>
> The project as a whole remains **GPL-2.0-only**. The files listed below are, additionally, made
> available under **GPL-2.0-or-later** by their author. See "Consumer guidance" at the end.

## 1. 附加授权范围

下列文件由本项目作者独立编写（上游不存在对应实现，见第 2 节判定证据），因此作者有权**在这些文件上**单独授予 GPL-2.0-or-later。文件头部带有：

```kotlin
// SPDX-License-Identifier: GPL-2.0-or-later
```

| 范围 | 文件数 | 内容 |
| --- | --- | --- |
| `src/ic705/**` | 26 | IC-705 LAN 协议编解码、UDP 传输、会话状态机与恢复、PTT 时序、后端控制器、Android 网络绑定、诊断页 |
| `src/radio/**` | 12 | Hamlib CAT 控制、USB 串口/音频路由、PTT 序列、电台型号目录、WLAN 型号表 |
| `src/hamlib/**` | 5 | Hamlib JNI 封装（运行时依赖 LGPL-2.1 的 `libhamlib`） |
| `src/diagnostic/**` | 6 + README | 结构化 JSONL 日志、诊断包导出、网络快照 |
| `src/backend/Ic705WifiBackend.kt` | 1 | IC-705 WLAN 后端适配 |
| `src/backend/UsbRadioBackend.kt` | 1 | USB/Hamlib 电台后端适配 |
| `src/ui/screen/Ic705RxDiagnosticScreen.kt` | 1 | IC-705 实时诊断界面 |
| `test/java/org/aprsdroid/app/{ic705,radio,hamlib}/**` | 30 | 上述功能的单元测试 |

合计 82 个 Kotlin 文件。

## 2. 判定方法与证据

判定标准只有一条：**该文件是否包含上游 APRSdroid（GPL-2.0-only）的表达。**

- 比较基线：上游最后提交 `65fb5fa`（2025-02-05，"Update Symbols"，作者 Mike）。
- 上游在该基线下**不存在** `src/ic705/`、`src/radio/`、`src/hamlib/`、`src/diagnostic/`、`src/ui/`、`src/service/` 目录（各 0 文件），也不存在 `Ic705WifiBackend.kt`、`UsbRadioBackend.kt`、`Ic705RxDiagnosticScreen.kt`，以及 `test/java/org/aprsdroid/app/{ic705,radio,hamlib}/`。
- 复核命令：

  ```bash
  git ls-tree -r --name-only 65fb5fa30ed2ea283004495384ad3c22c464e448 | grep -E '^src/(ic705|radio|hamlib|diagnostic)/'
  ```

- 内容层面亦复核过与上游日志设施（`src/LogActivity.scala`、`src/AprsService.scala`）的相似度：共享字符串 0，共享标识符仅通用词。

## 3. 明确**不**适用附加授权的范围（保持 GPL-2.0-only）

- `src/audio/**` —— 上游 AFSK / `AudioBufferProcessor.java` 血统，含 Graywolf（GPL-2.0）封装。
- `src/backend/` 下其余文件（`AprsBackend.kt`、`BluetoothTnc.kt`、`TcpUploader.kt`、`UdpUploader.kt`、`HttpPostUploader.kt`、`UsbTnc.kt`、`AfskDemodulator.kt`、`AfskInWrapper.kt`、`AfskUploader.kt`、`AfskBluetoothAudioRouter.kt`）—— 上游 `src/backend/*.scala` 的移植或修改。
- `src/tncproto/**`、`src/service/**`、其余 `src/*.kt` 顶层文件 —— 上游 `*.scala` / `*.java` 的移植或修改。
- `res/**`、构建脚本与配置。

**禁止**在上游派生文件里单独标注 `GPL-2.0-or-later`：组合物实际仍是 GPL-2.0-only，混标只会误导下游。

## 4. 组合与分发

- 本项目发布的 APK 是 "GPL-2.0-only 项目 + 上述 82 个 GPL-2.0-or-later 文件" 的组合，**整体按 GPL-2.0-only 分发**（GPLv2 与 GPLv2+ 兼容）。
- 下游若只取第 1 节列出的文件（例如把电台连接部分抽成独立库），可以选择按 **GPL-2.0-or-later** 使用，从而与 GPLv3 项目结合。
- 上游派生文件永远不得按 v2+ 使用；GPLv3 项目也不得引用这些派生文件。

### Consumer guidance

1. The **APK / the project as a whole** is GPL-2.0-only: you must follow GPLv2.
2. The 82 files listed in section 1 are **also** offered under GPL-2.0-or-later. If you use only those
   files (for example as a radio-link library), you may take them under "GPLv2 or any later version",
   which makes them combinable with GPLv3 projects.
3. Files in section 3 are GPL-2.0-only. Do not treat them as "or later".

## 5. 出处与工作记录

参考来源（仅作协议行为、数据格式与互操作性对照）与 AI 辅助说明见 [PROVENANCE.md](PROVENANCE.md)。
