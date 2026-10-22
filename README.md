# 新蓝 TV

一个面向 Android TV/Google TV 的 WebView 直播播放器：保留网页负责鉴权和 HLS 播放，由原生外壳负责全屏显示和遥控器交互。

## 当前实现

- 默认播放页面：`https://www.cztv.com/liveTV/106`（民生休闲）
- WebView 负责执行页面 JavaScript、处理临时播放签名和 HLS 播放。
- 启动后隐藏网页头部、频道列表和网页控制条，只保留视频并铺满电视画面。
- 默认尝试有声自动播放；如果电视系统拦截有声自动播放，按一次 OK 可重新触发。
- 播放时上键切换上一频道、下键切换下一频道，10 个频道首尾循环；左右键不做任何操作。
- 返回/退出键采用层级退出：菜单打开时关闭菜单，视频模式下结束应用任务。
- 从 TV Home 回到应用时会暂停并重新加载当前频道，避免复用已经失效的视频合成层；销毁时直接释放 WebView，不导航到 `about:blank`。
- 菜单键打开左侧原生频道菜单，可用上下选择、OK 切换、左键关闭；菜单会显示公开频道接口返回的当前节目名称。
- 当前不抓取 TS 分片，也不保存临时播放地址。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0 和 Gradle 8.13。完整 Android Studio 不是必须的。

本工作区已经准备好本地命令行工具链，可以直接执行：

```bash
./build-local.sh :app:assembleDebug
```

`.toolchain/` 和 `local.properties` 仅属于本机环境，不纳入 Git。新克隆的项目需要先配置 JDK 17 和 Android SDK，再使用下面的标准 Gradle Wrapper 命令。

在其他电脑上，使用 Android Studio 或标准 Gradle Wrapper 即可：

```bash
./gradlew :app:assembleDebug
```

生成的 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的 Android TV：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 继续开发

项目结构、代码入口、构建检查结果和待处理问题见 [开发说明](docs/DEVELOPMENT.md)。

Git 中保存源码、资源、构建配置和 Gradle Wrapper；本机工具链、缓存、APK 和签名密钥均通过 `.gitignore` 排除。

## 已知限制

- 依赖电视上存在可用的 Android System WebView/Chromium。
- 网页布局和网页播放器更新后，WebView 原型可能需要调整。
- 左侧菜单目前显示的是“当前节目”摘要，还不是完整的按时间排列节目单；完整 EPG 可作为下一步功能。
- 如果站点未来要求验证码、登录或改变播放鉴权逻辑，应使用站点允许的正常流程，不绕过验证。
- 电视端若 WebView 性能或兼容性不稳定，下一步应迁移到 Media3/ExoPlayer，并在原生网络层处理播放地址刷新。
