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

- 支持 M3U、M3U8 与 TXT 频道列表、LST 来源目录、地址前缀与模板、多来源切换、本地缓存和手动刷新。
- 支持遥控器、实体键盘与触屏，提供双列频道、来源同屏和面板固定；来源菜单按名称自动分组，可展开与折叠。
- 支持 HLS、DASH、普通 RTSP、MPEG-TS-over-RTP 兼容播放及 IPTV 组播（`rtp://`、`igmp://`、`udp://`）；缺少系统音频解码器时使用 FFmpeg 解码 MP2、AC-3 等音频。
- 播放时自动管理音频焦点，其他应用播放或来电时暂停，恢复后继续。
- 按来源记住频道，按频道记住画面比例，提供原始、16:9、4:3、铺满与裁剪；支持数字选台、CH+ / CH- 换台和切台提示。
- 返回桌面自动进入画中画；纯音频广播显示黑底声音图标；频道信息每秒更新音视频参数，支持复制；视频画面可截图。

## 安装与开始观看

在 **[最新版下载页](https://github.com/Yonsm/Televe/releases/latest)** 的 **Assets** 中下载 APK。支持 Android 6.0（API 23）及以上的手机、平板、电视与电视盒子，包名为 `net.yonsm.televe`。

2.1.5 安装包：`Televe_2.1.5_armeabi-v7a.apk` 用于 ARM32，`Televe_2.1.5_arm64-v8a.apk` 用于 ARM64；不确定设备架构时选通用包 `Televe_2.1.5.apk`。

1. 首次启动优先探测局域网源 `http://192.168.1.1:84/lists.txt`，不可用时使用[默认源](https://yonsm.github.io/IPTV/lists.txt)。后续启动恢复上次来源及其频道，频道已移除时播放首项。
2. 按 **OK** 或点击画面打开频道列表，选择频道观看。
3. 按 **←** 或向右滑动打开来源与频道同屏面板，在“播放来源”中输入 HTTP(S) 列表地址；省略协议时补全 `http://`，留空恢复默认源。

IPTV 源在 [Yonsm/IPTV](https://github.com/Yonsm/IPTV/) 维护；请仅使用可信且有权访问的来源。

## 操作指南

确认键：遥控器 **OK**；键盘 **Enter**（含小键盘）或**空格**。菜单键：**Menu / Contents Menu**，键盘 **F10 / ContextMenu**。

| 操作动作 | 遥控器 | 实体键盘 | 触摸屏幕 |
| --- | --- | --- | --- |
| 播放：打开频道 | OK | 确认键 | 点击画面 |
| 播放：上一 / 下一频道 | 短按 ↑ / ↓，或 CH+ / CH- | 短按 ↑ / ↓ | 下滑 / 上滑 |
| 播放：进入画中画 | 按 Home | 按 Home | 按 Home |
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

频道单独显示时为双列；来源同屏时按宽度显示一列或两列频道。**←：右列 → 左列 → 来源 → 关闭；→：按频道顺序前进，末项回首项。** ↑↓ 可按住连续浏览。打开频道、在来源与频道间切换时定位当前项，浏览中不自动回卷；重选当前频道不重新起播。当前来源与频道以三角标记，长名称聚焦后滚动。来源分组行可用 ← 折叠、→ 展开。

选台默认收起面板，固定后保持展开；返回或左滑仍可关闭，关闭后取消固定。切台提示约显示 3 秒，面板打开时延后显示。纯菜单中再按菜单键关闭，频道信息中按菜单键转到菜单；信息、截图、比例和来源编辑也可从菜单进入。

使用明确的单指上下或左右滑动；短拖动、模糊斜滑和多指触摸不触发操作，屏幕边缘保留给系统手势。列表长按不选择，输入框保留系统选词、复制与粘贴。来源编辑时返回先关软键盘；首次没有可用来源时，需加载成功才能关闭编辑框。

数字选台限三位，无效号码不切台；菜单和频道信息不接收数字选台。返回优先取消待选号码或关闭面板。

### 来源、缓存与恢复

启动、换源优先读缓存，缺失或无效才联网。重选已加载来源只定位频道；换源恢复上次频道，无记录或频道已移除时播放首项。

确认来源地址先刷新来源目录，成功后关闭编辑框，再单独刷新当前来源的频道；直接频道列表只下载一次。来源与频道联网加载成功时分别提示完成。频道刷新成功后清理其他频道缓存，后续切换来源时重新获取。换台、返回前台不会刷新列表，也不定时刷新。

更新失败或空列表保留有效缓存；编辑地址刷新失败时保留输入与编辑框，不替换原来源。频道加载失败时优先使用缓存，无缓存则进入来源菜单重试。取消刷新或重新选择后，过期排队请求跳过，已发出的过期请求结果丢弃。首次初始化未成功时重试仍探测局域网，成功或手工设置地址后记住选择。

短暂缓冲不闪加载图标；连续播放失败只提示一次，重试间隔逐步延长，稳定播放后重置。超出设备解码能力时提示并停止自动重试。

### 画面、信息与截图

比例提供 16:9、4:3、原始、铺满与裁剪，按频道保存，可在菜单内选择。未设置时默认 **16:9**，兼容实际宽屏但元数据误标 4:3 的视频；选“原始”跟随元数据，“铺满”拉伸填满画面，“裁剪”保持比例并裁去超出部分。返回桌面时，Android 8.0 及以上且设备支持画中画会自动进入小窗播放，Android 12 及以上由系统自动进入；音频广播不进入画中画。广播使用黑底声音图标，加载与重试规则同视频。

频道信息每秒更新，显示地址、最近错误，以及音视频分区的分辨率、帧率、编码、标称码率、解码器、硬软解、解码能力、渲染与丢帧、色彩、采样率、声道、语言及缓冲时长。缺失字段不显示，码率不代表实时下载速度。

截图需 Android 7.0 及以上且视频就绪，广播不可截图。保存到 `Pictures/<应用名称>`（电视直播｜Televe）；Android 7–9 首次需授予存储权限。

## 播放源格式

规则按来源标注：**标准**（M3U、HLS、RFC）、**惯例**（社区通用但非正式标准）、**Televe 扩展**。

### 文件类型

| 地址 | 用途 | 依据 |
| --- | --- | --- |
| `*.m3u`、`*.m3u8` | 频道列表 | 标准，M3U / HLS 播放列表 |
| `*.txt` | 频道列表 | 惯例，`名称,地址` 单行格式 |
| 任意文件名 + `.lst` | 来源目录 | Televe 扩展 |
| 路径以 `/lists.txt` 结尾 | 来源目录（默认源） | Televe 扩展 |

按地址路径判断，忽略 `?`、`#` 之后的部分；其余地址一律按频道列表处理。“来源目录 / 频道列表”分层是 Televe 概念，主流播放器把所有列表都当频道列表。

### 条目格式

- `#EXTM3U` 开头、`#EXTINF:` 行 + 下一行地址：**标准** M3U。
- `名称,地址` 单行、`分组名,#genre#` 跳过：**惯例**。
- 单行地址：没有逗号的行按地址处理，但必须已出现 `#EXTINF` 或 `#EXTM3U`，否则忽略（标准）。
- 有 `#EXTM3U` 时，逗号前出现 `://` 或 `?` 的行按地址处理，避免把带逗号的 URL 当名称（Televe 规则）。
- 属性 `tvg-name` 作标识，其次 `tvg-id`，再次显示名；`tvg-logo`、`group-title` 忽略（惯例，标识取值是 Televe 规则）。
- `#EXTINF:编号,属性,名称` 的属性夹在逗号间、显示名取末个逗号后的文字：**Televe 规则**；名称不要含逗号。
- 以 `<` 开头的行视为网页，直接拒绝（Televe 校验）。

### 频道号与排序（Televe 规则）

- `#EXTINF` 首字段或 TXT 的 `10-` 前缀，若为 0–999 整数且不小于前一号，用作频道号；`10-` 从标题中剥掉。
- 否则取前一号 + 1；首项默认 1，可显式写 0。
- 按频道号排序，同号按标题、标识、地址排序。
- 数字选台只匹配三位以内的号码；自动编号超过 999 的频道只能从列表选择。

标准 M3U 的首字段本是时长，这一规则无法完全区分整数时长与频道号。

### 相对地址（标准）

- 按 RFC 3986 解析，基准为重定向后的最终列表 URL。
- `//host/path` 按列表协议补全；`?query` 追加到当前文档地址；`../`、`./`、绝对路径按 RFC 处理。
- 非法或解析失败的条目直接丢弃。

### `#BASE:` 前缀（Televe 扩展）

- 从该行起对后续相对地址生效，可多次设置；绝对地址不变。
- 不以 `/` 结尾时按目录处理；含 `{}` 时按模板替换。
- 可相对于列表 URL 指定。
- 来源目录的 Base 传给对应频道列表（来源地址为绝对 URL 时同样继承），频道列表自己的 `#BASE:` 覆盖继承值。
- 不跨文件：`#LOAD:` 导入的子列表按自身地址解析，不继承外层 Base。
- 同一频道列表配不同 Base 时分别缓存。

```text
#BASE:http://example.com/live/
1-综合,cctv1.m3u8
2-财经,cctv2.m3u8
```

```text
#BASE:http://example.com/play?id={}
1-综合,cctv1
2-财经,cctv2
```

### `#LOAD:` 与 `#http` 导入（Televe 扩展）

- `#LOAD:` 导入另一份**同类型**列表：来源目录导入来源，频道列表导入频道，最多两层。导入地址需为绝对 URL，相对地址无法解析、会被静默跳过。
- 子列表按自身最终 URL 解析，保留子列表自己的 `#BASE:`，不继承外层 Base；失败静默跳过。
- 旧写法 `#http` 兼容：地址以 `/` 结尾当作 `#BASE:` 前缀，否则当作 `#LOAD:` 导入；建议改用显式指令。

### 来源目录示例（Televe 分层）

目录列出可切换的来源，条目指向频道列表。参考 [lists.txt](https://github.com/Yonsm/IPTV/blob/main/lists.txt)：

```m3u
#EXTM3U
#EXTINF:2,杭州联通
https://yonsm.github.io/IPTV/hangzhou/unicom.m3u8
```

可简写为 `杭州联通,https://yonsm.github.io/IPTV/hangzhou/unicom.m3u8`。选中来源后，用来源地址加继承的 Base 加载频道列表；若把非来源地址填进“播放来源”，会降级为单一来源，整份内容按频道列表加载。目录不要混入直播条目。

来源菜单按名称开头的两个汉字自动分组，例如“湖南卫视”“湖南经视”归入“湖南”组；点击分组展开或折叠，组内条目缩进显示并在标题中去掉重复前缀。

### 频道列表示例

每项对应一路直播。参考 [杭州联通](https://github.com/Yonsm/IPTV/blob/main/hangzhou/unicom.m3u8)：

```m3u
#EXTM3U
#EXTINF:01,group-title="央视" tvg-name="CCTV1",央视综合
http://124.90.44.40:6060/010/2/00000001000000060000000000000469?virtualDomain=010.live_hls.zte.com
```

### 连接与兼容性

网页、标记文档和空响应不作为有效列表。无法从扩展名识别的 HTTP(S) 媒体地址默认按 HLS 加载。

普通 RTSP 使用 Media3 官方模块及 RTP/TCP，不支持的 MPEG-TS-over-RTP 负载回退到 TS 兼容路径；外部应用打开 RTSP 无需 MIME 类型。

IPTV 组播支持 `rtp://`、`igmp://` 与 `udp://` 地址，自动识别 RTP 与裸 MPEG-TS 负载；逐接口加入组播组，Wi-Fi 下持有组播锁（需 `CHANGE_WIFI_MULTICAST_STATE` 权限）。能否接收取决于网络是否转发组播及设备网络接口。

为兼容旧系统，列表与 HTTP(S) 媒体请求不校验 HTTPS 服务端证书，无法保证服务端身份，也不保证解决所有 TLS 兼容问题；请仅使用可信来源。

## 构建

当前源码为 **2.1.5（versionCode 7）**，最低 SDK 23，编译及目标 SDK 36。应用信息显示版本号及北京时间 `MMddHHmm` 构建标识。仓库包含完整源码与 Gradle Wrapper，`app/` 负责应用界面、频道及截图，`library/` 负责播放器、网络与界面工具。

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
./gradlew :app:bundleRelease
./gradlew :app:testDebugUnitTest
adb install -r app/build/outputs/apk/release/Televe_2.1.5.apk
```

Release 构建检查签名并执行 lint，启用代码混淆与资源裁剪。`assembleRelease` 在 `app/build/outputs/apk/release/` 生成 ARM32、ARM64 与通用 APK；`bundleRelease` 生成 `app/build/outputs/bundle/release/app-release.aab`，AAB 由分发平台按设备拆分。需要两种产物时分别执行这两个命令。

Debug 在签名配置完整时使用同一证书，否则使用默认调试证书。

## 更新与授权

版本变更见 [CHANGELOG.md](https://github.com/Yonsm/Televe/blob/main/CHANGELOG.md)，安装包见 [GitHub Releases](https://github.com/Yonsm/Televe/releases)。

项目以 [GNU GPL-3.0](https://github.com/Yonsm/Televe/blob/main/LICENSE) 发布。播放器基于 [AndroidX Media3](https://github.com/androidx/media) 1.6.1，音频软件解码使用 [Jellyfin FFmpeg 扩展](https://github.com/jellyfin/jellyfin-androidx-media)；依赖源码与许可证见上游仓库。
