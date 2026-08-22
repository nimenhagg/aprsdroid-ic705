# 📋 更新日志 (Changelog)

所有针对 **APRSdroid IC-705 (Wi-Fi Mod)** 的重要更新和版本迭代记录均归档于此。

---

## 🌟 [v1.2.0-ic705] - 2026-08-23

### 💎 呼号与报文字体排版全面重构（高对比度 Material 3）
* **呼号专属配色**：友台与本台呼号（Callsign）全面升级为加粗大洋青（Oceanic Teal `#00677D`），彻底移除 2009 年遗留的荧光绿/暗紫配色；
* **报文与遥测数据**：报文载荷（IMEI/RSSI/电压/里程/卫星数）统一采用高对比度深色等宽字体（`#191C1E`），在浅色卡片上极具辨识度；
* **频率与距离**：频率（QRG）采用高亮青色（`#006874`），距离方位采用中灰（`#40484C`）；
* **台站详情页布局修复**：修复 StationActivity 顶部卡片被截断及大片空白问题，底部「地图 / APRS.FI / QRZ.COM」升级为 48dp 莫奈大圆角胶囊按键。

## 🌟 [v1.1.0-ic705] - 2026-08-23

### 🎨 Material 3 视觉与动效全面重构
* **呼号与报文字体排版全面重构（高对比度 Material 3）**：
  - 彻底重构了 2009 年遗留的荧光绿/暗紫配色；
  - 友台与本台呼号（Callsign）全面采用 **加粗大洋青（Oceanic Teal #00677D）** 强化可读性；
  - 报文内容、遥测数据（IMEI/RSSI/电压/里程/卫星数）与聊天记录统一采用高对比度深色等宽字体（#191C1E），在 Material 3 浅色卡片上极具辨识度；
  - 频率（QRG）采用高亮青色（#006874），距离方位采用中灰（#40484C）。
* **40dp Tonal 胶囊微容器图标**：重构了全套设置项前置图标（Leading Icons），采用 Google Pixel 系统同款的 40dp 莫奈色圆角微容器（`@color/md3_primary_container`），彻底消除原生 Preference 界面图标偏左偏小、空隙突兀的排版问题。
* **Android 13+ 莫奈动态桌面图标**：新增全矢量自适应图标（Adaptive Icon）及单色遮罩（Monochrome Themed Icon），应用图标可跟随 Android 13/14/15/16/17 系统壁纸主题自动变色。
* **MD3 Expressive 卡片与交互**：
  - 呼号卡片、消息气泡升级为 16dp 莫奈圆角与精致轮廓边框；
  - 底部操作栏升级为 52dp 双色大胶囊 Filled / Tonal 按钮；
  - 接入 Android 14~17 预测式返回手势（`enableOnBackInvokedCallback="true"`）与 FastOutSlowIn 页面平滑动效。
* **全面屏手势小白条沉浸式（Edge-to-Edge Insets）**：
  - 状态栏与手势导航栏完全透明沉浸；
  - 底部控制栏与聊天输入框接入系统 Window Insets 自动避让，绝不遮挡底部操作按键与输入法。

### ⚡ 信标发射核心逻辑修复（解决“发射叛逆/不发”Bug）
* **即时单发位置秒级兜底**：修复了原版点击「单次发射」时傻等 GPS 回调的缺陷。点击单发立即提取系统最近缓存的 GPS/网络已知位置（Last Known Location）瞬间编码并触发电台发射。
* **穿透限频拦截**：在 SmartBeaconing（智能信标）与 PeriodicGPS（周期性定位）中注入单发特权标识，100% 绕过静止状态下 1200 秒（20分钟）的限频和距离过滤，彻底解决“点发射不响”的问题。

### 🏗️ 构建系统与工程架构升级
* **升级 Target/Compile SDK**：适配至最新 Android 16/17（API 36/37），提升运行时容器性能。
* **提升 Min SDK 至 24（Android 7.0+）**：剔除陈旧的 MultiDex 与兼容垫片，释放 Java 17 / Scala 2.13 现代语言运行时优化。
* **去除坏死软链接（Symlinks）**：将 `AudioBufferProcessor.java` 与 `PacketCallback.java` 转为独立源码文件，完美支持 GitHub Actions Linux 云端直接拉取构建。
* **全自动化 CI/CD Release 流水线**：新增 `.github/workflows/release.yml`，升级为 `actions/setup-java@v5`，推送 Tag 或 master 分支全自动云端打包并发布 Release。

---

## 📻 [v1.0.0-ic705] - 2026-08-20

### 初始功能发布 (Initial IC-705 Wi-Fi Mod Release)
* **IC-705 Wi-Fi 直连通信**：原生支持通过 Wi-Fi UDP 局域网协议连接 Icom IC-705，实现免音频线、免外接 TNC 纯无线 APRS 报文收发。
* **内置软件 AFSK 调制解调器**：基于 12kHz 采样率与 20ms 分包节拍优化，彻底杜绝无线抖动丢包与空载波。
* **CI-V 自动控制**：实现 UDP 协议上的 PTT 自动置位与复位。
* **独立 Wi-Fi 路由**：蜂窝流量与电台通信互不干扰。
* **常驻通知优化**：增加一键「完全退出」按钮，移除开机自动拉起。
