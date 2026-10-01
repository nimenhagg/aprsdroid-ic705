# 出处与工作记录 / Provenance

本文件记录"电台连接与诊断"部分（见 [LICENSING.md](LICENSING.md) 第 1 节的 82 个文件）的来源、参考材料与核查记录，
用于支撑这些文件**额外**按 GPL-2.0-or-later 提供这一授权。

## 1. 开发方式

- 代码由**维护者主持、AI 辅助生成**：由维护者提出设计约束与验收标准，AI 生成实现，维护者负责审查、真机验证与发布。
- 该说明与 [README](README.md) 中的 "AI-assisted development is used in this repository; maintainers remain responsible
  for review, testing and releases." 一致。
- 可核验的过程记录：本仓库的提交历史（IC-705 LAN 功能首个提交 `a68a0ae`）与真机测试记录。早期提交使用了未正确配置的 Git author email（显示为 `APRSdroid Contributor <contributor@aprsdroid.org>`）；该 Git 元数据是本地配置错误，不用于判断实际代码作者或版权归属。

### 当前工作记录

- 主要开发时段：2026-08 至 2026-10。
- 主要真机验证设备：Icom IC-705（WLAN 直连 / USB OTG）、Google Pixel 8。
- 本文件不把具体 AI 工具或模型名称作为版权归属依据；代码归属以实际作者、贡献记录和适用许可证为准。

## 2. 参考来源（仅作协议行为、数据格式与互操作性对照）

| 来源 | 许可 | 用途 |
| --- | --- | --- |
| Icom RS-BA1 / OEM LAN 协议资料与自有抓包 | 事实性资料 | 报文字段、时序、端口、操作码 |
| [nonoo/kappanhang](https://github.com/nonoo/kappanhang) | MIT | 该协议的开源原型实现，用于行为交叉验证 |
| rigplane-core | MIT | 端点/客户端 ID 行为交叉验证 |
| [N0BOY/FT8CN](https://github.com/N0BOY/FT8CN) | MIT | 12 kHz 音频节奏与字节序约定的交叉验证 |
| Hamlib | LGPL-2.1 | USB CAT 控制的运行时依赖 |
| Graywolf | GPL-2.0 | AFSK/AX.25 接收（位于 `src/audio/**`，属 GPL-2.0-only 范围） |
| [wfview](https://github.com/eliggett/wfview) | GPL-3.0 | **仅行为对照**，用于确认互操作性差异，非代码来源 |

## 3. 相似度核查记录（可复现）

核查对象：`src/ic705/**` 与 wfview `src/radio/icom*.cpp`、`include/packettypes.h`。结论：

| 检查项 | 结果 |
| --- | --- |
| 代码注释逐字重合 | 0 / 215 |
| 字符串字面量逐字重合 | 0 / 259 |
| 十六进制常量 | 137 个中仅协议强制项与 wfview 相同（`passcode` 替代表、操作码）；本项目独有 3 个 |
| 十进制常量 | 16 个中 6 个相同，均为通用值（100/1000/127/255/705） |
| 架构形态 | 本项目为编解码器 + 数据类 + 纯归约状态机；wfview 为 `union` + `memset`/`memcpy` + Qt 信号槽 |
| 命名重合 | 4 个函数名（`sendAreYouThere` / `sendLogin` / `sendPing` / `sendConnectionInfo`）与 3 个字段名（`requestReply` / `requestType` / `payloadSize`，字段偏移由协议决定）；wfview 的上游 Kappanhang（MIT）并不使用这套命名 |

## 4. 未决事项

- 上述命名重合属于**命名层面**，不构成受保护表达的复现。维护者此前规划的逐函数复核尚未完成；
  若复核结论认为某些函数是 wfview 源码的翻译，将按"仅依据协议资料 + MIT 参考实现 + 自有抓包，不参考 wfview 源码"
  的方式重写，并在本文件追加记录（日期 + 提交号）。
- 在复核完成前，请勿把本节结论对外表述为"已完成法律意见"。本文件是工程记录，不是法律意见。
