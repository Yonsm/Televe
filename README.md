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
  <strong><a href="https://github.com/Yonsm/Televe/releases/latest">下载</a></strong>
  &nbsp; · &nbsp; <a href="https://Yonsm.github.io/Televe">官网</a>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe">仓库</a>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe/issues">反馈</a>
  &nbsp; · &nbsp; <a href="https://github.com/Yonsm/Televe/blob/main/CHANGELOG.md">变更</a>
</p>

## 功能

- 支持 M3U / TXT 频道列表、多来源切换、本地缓存和手动刷新。
- 支持遥控器、实体键盘与触屏，提供双列频道、来源同屏和面板固定。
- 按来源记住频道，按频道记住画面比例；支持数字选台和切台提示。
- 支持 HLS、DASH、普通 RTSP 与 MPEG-TS-over-RTP 兼容播放；缺少系统音频解码器时使用 FFmpeg 解码 MP2、AC-3 等音频。
- 纯音频广播显示黑底声音图标；频道信息每秒更新音视频参数，支持复制；视频画面可截图。

## 安装与开始观看

在 **[最新版下载页](https://github.com/Yonsm/Televe/releases/latest)** 的 **Assets** 中下载 APK。支持 Android 6.0（API 23）及以上的手机、平板、电视与电视盒子，包含 ARM 32 / 64 位架构（`armeabi-v7a`、`arm64-v8a`），包名为 `net.yonsm.televe`。

首次安装需允许“安装未知应用”。覆盖升级须使用相同签名；卸载会清除设置与缓存。

1. 首次启动优先探测局域网源 `http://192.168.1.1:84/lists.m3u8`，不可用时使用[默认源](https://yonsm.github.io/IPTV/lists.m3u8)。后续启动恢复上次来源及其频道，频道已移除时播放首项。
2. 按 **OK** 或点击画面打开频道列表，选择频道观看。
3. 按 **←** 或向右滑动打开来源与频道同屏面板，在“播放来源”中输入 HTTP(S) 列表地址；留空恢复默认源。

IPTV 源在 [Yonsm/IPTV](https://github.com/Yonsm/IPTV/) 维护。播放是否可用取决于来源与网络环境，请仅使用可信且有权访问的来源。

## 操作指南

确认键：遥控器 **OK**；键盘 **Enter**（含小键盘）或**空格**。菜单键：**Menu / Contents Menu**，键盘 **F10 / ContextMenu**；系统拦截的按键除外。下表中的画面包含黑边。

| 操作动作 | 遥控器 | 实体键盘 | 触摸屏幕 |
| --- | --- | --- | --- |
| 播放：打开频道 | OK | 确认键 | 点击画面 |
| 播放：上一 / 下一频道 | 短按 ↑ / ↓ | 短按 ↑ / ↓ | 下滑 / 上滑 |
| 播放：来源同屏 | ← | ← | 右滑 |
| 播放：仅打开菜单 | 菜单键 | 菜单键 | — |
| 播放：截图 | → | → | 左滑 |
| 播放：频道信息 | 长按 OK，或 Info | 长按确认键 | 长按画面 |
| 数字选台（播放 / 频道列表） | 数字，OK 或等 1.5 秒 | 数字，确认键或等 1.5 秒 | 点选频道 |
| 数字：退格 / 取消 | 退格键 / Back | Backspace / Esc | — |
| 列表、菜单：浏览 / 选择 | ↑↓ / OK | ↑↓ / 确认键 | 拖动 / 点击 |
| 频道：左右导航、展开来源 | ← / →，规则见下文 | 同左 | 右滑或点左上箭头展开 |
| 菜单：展开频道 | → | → | 右滑或点右上箭头 |
| 同屏来源：选择并进入频道 | OK 或 → | 确认键或 → | 点击来源 |
| 频道：固定 / 取消固定 | 菜单键 | 菜单键 | 点右上图标 |
| 频道、菜单：关闭 | Back；来源列可按 ← | Esc；来源列可按 ← | 左滑；未固定时可点面板外 |
| 比例：选择 / 取消 | ↑↓、OK / Back | ↑↓、确认键 / Esc | 点选项 / 弹窗外 |
| 频道信息：滚动 | ↑↓ | ↑↓ | 上下拖动 |
| 频道信息：关闭 | OK、→、Info 或 Back | 确认键、→ 或 Esc | 点面板内外或右滑 |
| 频道信息：复制全部并关闭 | 长按 OK | 长按确认键 | 长按面板 |
| 应用信息：主页 / 关闭 | 网址按钮 OK / Back | 网址按钮确认 / Esc | 点网址 / 弹窗外 |
| 来源编辑：输入 / 确认 | 系统键盘输入，↓ 到确定后 OK | 输入后 Enter；Tab / 方向键切换 | 输入后点确定 |
| 来源编辑：取消 | Back | Esc | 点取消或弹窗外 |
| 播放：退出 | 3 秒内按两次 Back | 3 秒内按两次 Esc | 3 秒内系统返回两次 |

频道单独显示时为双列；来源同屏时按宽度显示一列或两列频道。**←：右列 → 左列 → 来源 → 关闭；→：按频道顺序前进，末项回首项。** ↑↓ 可按住连续浏览。打开频道、在来源与频道间切换时定位当前项，浏览中不自动回卷；重选当前频道不重新起播。当前来源与频道以白色三角标记，长名称聚焦后滚动。

选台默认收起面板，固定后保持展开；返回或左滑仍可关闭，关闭后取消固定。切台提示约显示 3 秒，面板打开时延后显示。纯菜单中再按菜单键关闭，频道信息中按菜单键转到菜单；信息、截图、比例和来源编辑也可从菜单进入。

使用明确的单指上下或左右滑动；短拖动、模糊斜滑和多指触摸不触发操作，屏幕边缘保留给系统手势。列表长按不选择，输入框保留系统选词、复制与粘贴。来源编辑时返回先关软键盘；首次没有可用来源时，需加载成功才能关闭编辑框。

数字选台限三位，无效号码不切台；菜单和频道信息不接收数字选台。返回优先取消待选号码或关闭面板。

### 来源、缓存与恢复

启动、换源优先读缓存，缺失或无效才联网。重选已加载来源只定位频道；换源恢复上次频道，无记录或频道已移除时播放首项。

确认来源地址刷新目录与当前来源的频道，其他来源缓存保留；直接频道列表只下载一次，同一请求不重复提交。换台、返回前台不会刷新列表，也不定时刷新。联网加载频道成功时提示完成。

更新失败或空列表保留有效缓存；无缓存时，目录失败进入地址编辑，频道失败进入来源菜单，可重选或改地址重试。旧请求不覆盖新选择。首次初始化未成功时重试仍探测局域网，成功或手工设置地址后记住选择。

短暂缓冲不闪加载图标；连续播放失败只提示一次，重试间隔逐步延长，稳定播放后重置。超出设备解码能力时提示并停止自动重试。

### 画面、信息与截图

比例提供原始、16:9 和 4:3，按频道保存。未设置时默认 **16:9**，兼容实际宽屏但元数据误标 4:3 的视频；选“原始”才跟随元数据。广播使用黑底声音图标，加载与重试规则同视频。

频道信息每秒更新，显示地址、最近错误，以及音视频分区的分辨率、帧率、编码、标称码率、解码器、硬软解、解码能力、渲染与丢帧、色彩、采样率、声道、语言及缓冲时长。缺失字段不显示，码率不代表实时下载速度。

截图需 Android 7.0 及以上且视频就绪，广播不可截图。保存到 `Pictures/<应用名称>`（电视直播｜Televe）；Android 7–9 首次需授予存储权限。

## 播放源格式

两类 M3U 列表均以 `#EXTM3U` 开头，每项由 `#EXTINF` 和下一行地址组成。支持相对地址，以重定向后的最终列表地址为基准。

### 来源目录（lists）

目录列出可切换的来源，下一行指向频道列表。参考 [lists.m3u8](https://github.com/Yonsm/IPTV/blob/main/lists.m3u8)：

```m3u
#EXTM3U
#EXTINF:2,server-id="http://101.71.255.234:6610/",杭州联通
https://yonsm.github.io/IPTV/hangzhou/unicom.m3u8
```

`server-id` 标记来源并提供标识，不用于拼接播放地址。未标记时，仅 `.m3u` 链接自动识别为来源，`.m3u8` 或无扩展名的列表需显式标记；目录不要混入直播条目。

### 频道列表

每项对应一路直播。参考 [杭州联通](https://github.com/Yonsm/IPTV/blob/main/hangzhou/unicom.m3u8)：

```m3u
#EXTM3U
#EXTINF:01,group-title="央视" tvg-name="CCTV1",央视综合
http://124.90.44.40:6060/010/2/00000001000000060000000000000469?virtualDomain=010.live_hls.zte.com
```

- 支持 `#EXTINF:编号 属性,名称` 和示例中的 `#EXTINF:编号,属性,名称`；后者取末个逗号后的文字为显示名。
- `tvg-name` 用作标识，其次取 `server-id`、`tvg-id`、显示名；`group-title` 是上游分组名，当前忽略，不生成分组或影响排序。
- 首字段为 0–999 整数且不小于前号时用作频道号，否则取前号加 1；首项默认 1，也可指定 0。按原顺序编号后排序，同号按名称排序，数字选台取首个匹配。自动编号可超过 999，超过三位时从列表选择。
- TXT 使用 `10-新闻,http://example.com/live`，编号规则相同；`分组名,#genre#` 分组头忽略。

标准 M3U 的首字段原为时长，此规则无法完全区分整数时长与频道号。普通频道地址可为 `.m3u8`，不会仅凭该后缀当作来源目录。

### 连接与兼容性

网页、标记文档和空响应不作为有效列表。无法从扩展名识别的 HTTP(S) 媒体地址默认按 HLS 加载。

普通 RTSP 使用 Media3 官方模块及 RTP/TCP，不支持的 MPEG-TS-over-RTP 负载回退到 TS 兼容路径；外部应用打开 RTSP 无需 MIME 类型。

为兼容旧系统，列表与 HTTP(S) 媒体请求不校验 HTTPS 服务端证书，无法保证服务端身份，也不保证解决所有 TLS 兼容问题；请仅使用可信来源。

## 构建

当前源码为 **2.1.3（versionCode 5）**，最低 SDK 23，编译及目标 SDK 36。仓库包含完整源码与 Gradle Wrapper，`app/` 负责应用界面、频道及截图，`library/` 负责播放器、网络与界面工具。

使用兼容 Android Gradle Plugin 的 JDK 和 Android SDK 36。用 Android Studio 打开仓库，或在不纳入版本管理的 `local.properties` 配置 SDK 与签名：

```properties
sdk.dir=/path/to/Android/sdk
RELEASE_STORE_FILE=/path/to/release.keystore
RELEASE_STORE_PASSWORD=your-password
RELEASE_KEY_ALIAS=your-alias
RELEASE_KEY_PASSWORD=your-key-password
```

```sh
./gradlew :app:assembleRelease
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/release/Televe.apk
```

Release 构建检查签名并执行 lint，启用代码混淆与资源裁剪；产物为 `app/build/outputs/apk/release/Televe.apk`。

## 更新与授权

版本变更见 [CHANGELOG.md](https://github.com/Yonsm/Televe/blob/main/CHANGELOG.md)，安装包见 [GitHub Releases](https://github.com/Yonsm/Televe/releases)。

项目以 [GNU GPL-3.0](https://github.com/Yonsm/Televe/blob/main/LICENSE) 发布。播放器基于 [AndroidX Media3](https://github.com/androidx/media) 1.6.1，音频软件解码使用 [Jellyfin FFmpeg 扩展](https://github.com/jellyfin/jellyfin-androidx-media)；依赖源码与许可证见上游仓库。
