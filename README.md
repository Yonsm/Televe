<p align="center">
  <img src="https://raw.githubusercontent.com/Yonsm/Televe/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_tv.png" width="96" height="96" alt="Televe 应用图标">
</p>

<h1 align="center">Televe</h1>

<p align="center">面向 Android 手机、平板和电视的 IPTV 直播播放器</p>

<p align="center">
  <a href="https://github.com/Yonsm/Televe/releases/latest"><img src="https://img.shields.io/github/v/release/Yonsm/Televe?label=Release&amp;color=087f8c" alt="最新版本"></a>
  <a href="https://github.com/Yonsm/Televe/releases/latest"><img src="https://img.shields.io/badge/Android-6.0%2B-3a853c" alt="Android 6.0 及以上"></a>
  <a href="https://github.com/Yonsm/Televe/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0 许可证"></a>
</p>

<p align="center">
  <strong><a href="https://github.com/Yonsm/Televe/releases/latest">下载最新版</a></strong>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe">GitHub 仓库</a>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe/issues">问题反馈</a>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe/blob/main/CHANGELOG.md">更新日志</a>
</p>

---

## 功能亮点

| 功能 | 说明 |
| --- | --- |
| **多源直播** | 支持 M3U / TXT 频道列表，编辑源地址、标记来源列表、切换与刷新播放源。 |
| **大屏与触屏** | 适配手机、平板与 Android TV，支持遥控器、键盘和滑动手势。 |
| **快捷换台** | 上下切台、数字选台、频道抽屉与短暂显示的频道提示。 |
| **接着观看** | 本地缓存播放列表，记住上次使用的播放源与频道。 |
| **画面与声音** | 高清筛选、多种画面比例；缺少系统音频解码器时，以 FFmpeg 软件解码 MP2、AC-3 等音频。纯音频广播显示电台名称、播放状态与音频参数。 |
| **信息与截图** | 查看源地址及动态音视频参数，长按保存当前画面。 |

## 下载与安装

在 **[最新版下载页](https://github.com/Yonsm/Televe/releases/latest)** 的 **Assets** 中下载 APK 并安装。旧版见[历史版本](https://github.com/Yonsm/Televe/releases)。

| 运行要求 | 说明 |
| --- | --- |
| 系统 | Android 6.0（API 23）及以上 |
| 设备 | Android 手机、平板、电视与电视盒子 |
| 架构 | ARM 32 位 / 64 位（`armeabi-v7a`、`arm64-v8a`） |
| 包名 | `net.yonsm.televe` |

首次安装需允许“安装未知应用”。覆盖升级须使用相同签名；卸载会清除设置与缓存。

## 开始观看

1. 首次启动优先探测局域网源，不可用时加载默认源；后续启动先加载上次来源，再恢复其中的频道，频道已移除时播放首项。
2. 按 **OK** 或点击画面，打开频道列表。
3. 选择列表顶部的来源名称，切换或编辑播放源。

播放是否可用取决于播放源与网络环境，请仅使用可信且有权访问的来源。

## 使用指南

“播放源”支持 HTTP(S) 列表地址，自动识别来源列表与频道列表。默认地址见 `internal.playlist.PlaylistRepository.DEFAULT_URL`。

IPTV 源在 [Yonsm/IPTV](https://github.com/Yonsm/IPTV/) 仓库维护。

### 快捷操作

**全屏播放时：** 方向键与滑动方向一致，**↑↓ 换台 · → 列表 · ← 信息**。

| 操作 | 遥控器 / 键盘 | 触摸屏 |
| --- | --- | --- |
| 上一 / 下一频道 | <kbd>↑</kbd> / <kbd>↓</kbd> | ↑ / ↓ 滑动 |
| 频道列表 | <kbd>→</kbd> 或 <kbd>OK</kbd> / <kbd>Enter</kbd> | → 滑动或轻点画面 |
| 频道信息 | <kbd>←</kbd> 或 <kbd>Info</kbd> | ← 滑动 |
| 播放源菜单 | <kbd>Menu</kbd> 开关，或在列表顶部选来源 | 点击列表顶部来源 |
| 截图 | 长按 <kbd>OK</kbd> | 长按画面 |
| 数字换台 | <kbd>0</kbd>–<kbd>9</kbd> 输入频道号 | 在列表中选择频道 |
| 修改 / 取消数字 | <kbd>⌫</kbd> 退格 / 返回键 | — |
| 关闭列表或弹窗 | 返回键 | 系统返回 |
| 退出应用 | 3 秒内连按两次返回键 | 3 秒内连续返回两次 |

菜单与列表内：**↑↓ 移动 · OK 选择 · 返回关闭**。Menu 键仅在未被系统拦截时有效。

左侧频道列表展开时聚焦当前播放频道，左右键在同一行频道间循环移动，不关闭列表。频道信息从右侧展开，与频道列表使用相同的进出动画。

确认当前播放频道只收起列表，不重新加载视频；确认其他频道才切台。点击播放区域可开关频道列表，菜单键和频道信息方向键可再次操作以收起对应浮层。

- **画面设置**：播放源菜单中选择高清筛选、默认比例、16:9 或 4:3。
- **截图位置**：`Pictures/Televe`；Android 9 及以下需存储权限。
- **频道提示**：切台后显示约 3 秒。
- **关于**：在播放源菜单打开；↑↓ 滚动或聚焦仓库链接，OK 打开；触屏可直接点击链接。

### 数字换台

输入频道号后，按 **OK** 或等待 **1.5 秒**换台；无效编号不影响当前播放。列表与切台提示统一显示频道编号及名称。

频道号按播放列表原始顺序生成，再用于列表排序：

- `#EXTINF:` 后 0–999 范围内的整数，且不小于前一个频道号时，作为显式频道号。
- 为空、负数、小数、超出范围、比前一个频道号小或无法解析时，取前一个频道号加 1。
- 首项默认从 1 开始，也可显式指定为 0。自动编号可以超过 999。
- TXT 列表使用频道名称前的数字前缀，如 `10-新闻,http://example.com/live`，遵循相同规则。
- 高清筛选保留原频道号，不重新编号。连续重复指定同一有效频道号时，数字换台选择当前列表中首个匹配频道。

例如首项指定 `0` 有效；依次指定 `10、-1、5、1000` 时，最终频道号为 `10、11、12、13`。

标准 M3U 的 `EXTINF` 首字段表示时长，单位为秒，可带小数，直播常用 `-1` 表示未知时长。本项目将满足上述条件的整数用于频道号；该启发式规则不能完全区分整数时长与频道号。

### 频道信息

显示源地址、分辨率、帧率、音视频编码、码率、解码器、硬软解方式、解码能力、渲染与丢失帧数、色彩、采样率、声道、语言、缓冲时长及最近播放错误。

每 **5 秒**刷新，选择文本复制时暂停。遥控器 ↑↓ 滚动，触屏拖动；←、→、Info 或横向滑动可关闭，也可从播放源菜单打开。

“关于”弹窗与右侧频道信息均可按返回键或轻点面板外关闭。仓库链接在外部浏览器打开，无浏览器时提示。

缺失字段显示“未知”；码率来自流元数据，不代表实时下载速度。

### 播放源格式与兼容性

普通频道列表可直接包含 `.m3u8` 直播地址，无需分组。来源列表可引用 `.m3u` 文件；使用 `.m3u8` 或无扩展名的列表地址时，在 M3U 条目中以 `server-id` 明确标记来源：

```m3u
#EXTM3U
#EXTINF:1 server-id="main",主播放源
https://example.com/channels.m3u8
```

启动时优先读取缓存，缓存不可用时重新拉取，无定时刷新。切换或重新选择来源会刷新其频道列表；编辑源地址并确认会刷新来源目录及当前频道列表。加载失败后可点击重试，普通换台和返回前台不刷新列表。

HTTP(S) 播放地址无法从扩展名识别类型时，默认按 HLS 加载。

普通 RTSP 使用 Media3 官方协议模块及 RTP/TCP 传输；不支持的 MPEG-TS-over-RTP 负载回退到现有 TS 兼容路径。RTSP 源仍需网络可达且具备相应访问权限。

为兼容旧系统根证书库，播放源与 HTTP(S) 媒体请求不强制校验 HTTPS 服务端证书。请仅使用可信来源；该兼容策略不能保证服务端身份，也不能解决所有旧系统 TLS 协议兼容问题。

## 项目结构

```text
app/                 应用入口、频道界面与截图
  src/main/java/
    internal/playlist/  频道模型、列表解析与缓存
library/             播放器、网络与必要的界面工具
```

仓库包含完整源码与 Gradle Wrapper，无需其他应用仓库或共享目录。

## 构建

当前源码版本为 2.1.2（versionCode 4），编译和目标 SDK 为 36。

需要兼容 Android Gradle Plugin 的 JDK（推荐 JDK 21）和 Android SDK 36。用 Android Studio 打开仓库，或在 `local.properties` 配置 `sdk.dir` 后执行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

产物位于 `app/build/outputs/apk/debug/Televe.apk` 和 `app/build/outputs/apk/release/Televe.apk`。

签名参数从不纳入版本管理的 `local.properties` 读取：

```properties
RELEASE_STORE_FILE=/path/to/release.keystore
RELEASE_STORE_PASSWORD=your-password
RELEASE_KEY_ALIAS=your-alias
RELEASE_KEY_PASSWORD=your-key-password
```

Release 必须配置以上四项，不回退为调试签名。请勿将签名信息提交到仓库。

### 发布与安装

发布使用 `:app:assembleRelease` 生成的签名 APK。该任务包含 Release lint 检查，产物经过代码混淆与资源裁剪，仅包含上述两种 ARM 架构。

```sh
adb install -r app/build/outputs/apk/release/Televe.apk
```

## 更新日志

版本变更记录见 [CHANGELOG.md](https://github.com/Yonsm/Televe/blob/main/CHANGELOG.md)，安装包与发行说明见 [GitHub Releases](https://github.com/Yonsm/Televe/releases)。

## 授权

以 GNU General Public License v3.0 发布，详见 [LICENSE](https://github.com/Yonsm/Televe/blob/main/LICENSE)。

播放器基于 [AndroidX Media3](https://github.com/androidx/media)，音频软件解码使用 [Jellyfin FFmpeg 扩展](https://github.com/jellyfin/jellyfin-androidx-media)（`org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1`）。各依赖的源码与许可证见其上游仓库。

---

[项目主页](https://yonsm.github.io/Televe/) · [源码仓库](https://github.com/Yonsm/Televe) · [下载最新版](https://github.com/Yonsm/Televe/releases/latest) · [问题反馈](https://github.com/Yonsm/Televe/issues)

