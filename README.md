# Televe

Televe 是面向 Android 手机、平板和电视的 IPTV 直播应用。

- 包名：`net.yonsm.televe`
- 最低系统版本：Android 6.0（API 23）
- 编译和目标 SDK：36
- 版本：2.1.0
- CPU 架构：`armeabi-v7a`、`arm64-v8a`

## 功能

- 读取 M3U 和 TXT 频道列表，切换直播源、刷新列表。
- 本地缓存直播源和频道列表，记住上次使用的直播源与频道。
- 「播放源」菜单支持编辑地址、标记列表，切换来源后自动播放首个频道。
- 遥控器上下键或上下滑动手势切台，确认键或点击画面打开频道列表。
- 直播源菜单支持高清筛选、默认比例、16:9 和 4:3。
- 遥控器右键或长按画面保存截图到 `Pictures/Televe`；Android 9 及以下需授予存储权限。
- 手机桌面和 Android TV 启动器均可直接启动应用。
- 系统缺少相应音频解码器时，使用 FFmpeg 软件解码 MP2、AC-3 等音频。

默认播放源地址定义在 `internal.playlist.PlaylistRepository.DEFAULT_URL`。菜单中的“播放源”可切换到任意 HTTP 或 HTTPS M3U 地址；确认后会重新请求该地址。应用会自动识别源列表与直接频道列表。

## 项目结构

```text
app/                 应用入口、频道界面与截图
  src/main/java/
    internal/playlist/  频道模型、列表解析与缓存
library/             播放器、网络与必要的界面工具
```

本仓库包含完整源码与 Gradle Wrapper，可以单独检出、构建和发布。两个模块均由本仓库维护，不需要其他应用仓库或共享目录。

## 构建

需要已安装且与 Android Gradle Plugin 兼容的 JDK 及 Android SDK 36。项目不固定 Gradle Daemon 的 JDK 版本；本机 JBR 21 已用于构建验证。通过 Android Studio 打开仓库根目录，或在 `local.properties` 配置本机 `sdk.dir` 后执行：

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

Release 构建必须配置以上四个参数，缺失时会失败，不会回退为调试签名。密钥路径、别名和密码仅保存在未纳入版本控制的本机 `local.properties` 中。

应用的设置和列表缓存属于自身包名。其他应用的数据不会自动导入。

## 更新日志

版本变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 授权

以 GNU General Public License v3.0 发布，详见 [LICENSE](LICENSE)。

