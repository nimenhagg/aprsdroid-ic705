# APRSdroid Mod：Agent 工程维护规范

> 只保留下一次修改真正需要遵守的当前事实、不变量和验证门槛。详细技术架构见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，长期路线见 [ROADMAP.md](ROADMAP.md)，历史变化见 [CHANGELOG.md](CHANGELOG.md)。

## 1. 当前基线

| 项目 | 当前值 |
| --- | --- |
| 最新 GitHub Release | `Mod-v2.4.0` |
| Android | minSdk 28 / compileSdk 37 / targetSdk 37 |
| Build | Gradle 9.5.0 / AGP 9.3.2 / Java 17 |
| Kotlin / Compose Compiler | 2.3.21 |
| Graywolf RX | 0.14.13，commit `34cd0111b7a40e7d91607699b7b4dd188574970a` |
| MapLibre | 13.5.1 |
| Application ID | `me.nimenhagg.aprsdroidic705mod` |

README 必须区分 Latest release 与 Current main；未发布能力不得写成稳定版能力。正式 Release 当前为 ARM64/ARMv7 OpenGL。

## 2. 必须保持的不变量

- IC-705 UDP Socket 只绑定选定 Android Wi-Fi Network；不要把整个 App 绑定到电台 Wi-Fi。
- 生产本地 PCM AFSK1200 RX 统一使用 Graywolf；native 失败不得静默回退 legacy Java demodulator。
- `Afsk1200Modulator` 只保留稳定 TX PCM 生成和 host-JVM 测试辅助；没有独立迁移理由不要重写 TX。
- PTT OFF 必须遵守 ACK、安全状态和 watchdog 语义；本地 UDP send 成功不等于电台确认 RX。
- IC-705 CONTROL / CI-V / AUDIO 使用角色化 liveness 与局部 recovery，不恢复“一条通道超时就 teardown 整个 session”。
- 更新检查：应用启动时自动静默检查一次；无更新、网络失败或解析失败均静默；仅发现新版本时弹窗提示。设置页仍可手动检查；不得后台/周期检查，也不得自动下载/安装。
- 没有完整协议迁移设计前，不要擅自强制 TLS；不要用厂商/机型特判掩盖状态机问题。
- AGP 9 使用 built-in Kotlin；Java 17 是基线，不为版本数字随意切换 Java 21。

具体状态机、时序、参数和 UI/地图约束见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 3. 文档职责

- README：用户下载、安装、配置、功能与故障排查；不放内部架构、CI 细节或 Agent 指令。
- AGENT：当前不变量、修改原则、验证门槛；不堆历史完成清单。
- ARCHITECTURE：稳定技术架构、数据流、状态机和实现约束。
- ROADMAP：未来阶段与 PR 顺序，不是自动任务列表。
- CHANGELOG：历史版本变化。

## 4. 修改与提交

- 一个 commit 表达一个逻辑完整、可验证、可回滚的变化；不是“一次只能改一个文件”。
- 不把占位文件、半套迁移或不能编译的中间状态推到 `main`。
- CI 绿之前不得称为“已修复”。真机 UI、动效、RF 行为即使 CI 绿也要保留真机验证边界。
- 修改前先看对应测试、CI 和架构约束，不只凭旧版本代码推断。

## 5. 验证

常用完整验证：

```bash
./gradlew verifyReleaseVersion \
  testArm64OpenglDebugUnitTest \
  lintArm64OpenglDebug \
  assembleArm64OpenglRelease \
  assembleArm32OpenglRelease \
  --no-daemon --stacktrace
```

Graywolf / AFSK RX 变更还必须：

```bash
bash .github/scripts/test_graywolf_loopback.sh
bash .github/scripts/build_graywolf_android.sh arm64-v8a
bash .github/scripts/build_graywolf_android.sh armeabi-v7a
```

至少确认 Graywolf-only guard、8/11.025/12 kHz regression、JNI ABI/exports、16 KiB alignment，以及 APK native 库与当次源码构建产物一致。

涉及 IC-705 session/TX/recovery：还必须做真机诊断、真实 RF AFSK、低功率/假负载 PTT ON → audio → PTT OFF ACK 测试；synthetic 不能替代 RF。

涉及 UI/导航/动效：按 ARCHITECTURE 的真机验收项检查；CI 不能证明视觉转场正确。

## 6. Release

正式发布同步：`build.gradle` 版本、`CHANGELOG.md`、README 已发布能力、必要时的 AGENT 基线，以及 `Mod-v<major.minor.patch>` tag。

Release workflow 的实际检查以 CI 为准，应覆盖测试/Lint、ARM64/ARMv7 OpenGL、Graywolf、MapLibre、16 KiB alignment、签名、ABI/SHA-256、源码归档、checksums 和 R8 mapping。普通 main CI 绿不等于 Release 已发布。

## 7. 不得反向恢复

- 生产 XML UI；多 Activity 复制四个一级页面；root 整页 cross-fade/slide/alpha；全局 `windowAnimationStyle`。
- 通知进入聊天的 Hub 二次 LaunchedEffect 跳转；通知设置等待 NotificationChannel Binder。
- Mapsforge / 专用离线瓦片下载器；外部存储绝对路径式文档处理。
- PTT OFF 未经 ACK 假定 RX；Graywolf RX 失败静默回退 legacy demodulator。
- 生成的 Graywolf `.so` 提交到源码 `libs/`；统一通道超时直接 teardown 整个 IC-705 session。

## 8. 路线

长期 Hamlib / 多电台计划只看 [ROADMAP.md](ROADMAP.md)。只有用户明确要求推进某阶段时才执行；不要把路线图自动变成当前任务。

`AI_CONTEXT.md` 仅保留兼容入口，不维护第二份规范正文。