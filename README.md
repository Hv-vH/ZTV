# 新蓝 TV

一个面向 Android TV/Google TV 的 WebView 直播播放器：保留网页负责鉴权和 HLS 播放，由原生外壳负责全屏显示和遥控器交互。

## 当前实现

- 默认播放页面：`https://www.cztv.com/liveTV/106`（民生休闲）
- WebView 负责执行页面 JavaScript、处理临时播放签名和 HLS 播放。
- 启动或切台时显示原生加载页，琥珀指示条循环移动表示正在连接；视频首帧就绪后再露出直播画面。动效在菜单覆盖、后台或失败时停止；移除网页海报、默认播放图标与控制条，保留全屏视频。
- 默认尝试有声自动播放；如果电视系统拦截，按 OK 重新触发。连接失败或超时进入原生错误提示，按 OK 重新加载当前频道。
- 播放时上键切换上一频道、下键切换下一频道，10 个频道首尾循环；左右键不做任何操作。
- 返回/退出键采用层级退出：节目单区先回到频道区，再关闭菜单，视频模式下结束应用任务。
- 从 TV Home 回到应用时会暂停并重新加载当前频道，避免复用已经失效的视频合成层；销毁时直接释放 WebView，不导航到 `about:blank`。
- 菜单键打开双栏原生菜单：左侧选择频道，右侧显示该频道当天的完整节目单，包括起止时间、完整名称和“已播出 / 正在播出 / 待播出”状态。
- 电视界面采用炭灰底、暖白焦点和少量琥珀色直播状态；思源黑体正文/标题与 IBM Plex Sans 数字统一缓存，品牌和直播图标使用生成后按显示尺寸优化的本地 PNG。顶部显示当前观看频道与北京时间，底部提示随焦点区域变化。
- 安装图标和 Android TV 桌面横幅统一使用新 Z 品牌；横幅包含暖白“新蓝 TV”字标。
- 节目列表只创建可见卡片并循环复用；隐藏菜单时不绑定数据，覆盖播放器时停止隐藏画面合成，直播音频与页面保持连续。
- 在频道区按右键进入节目单，自动定位当前节目；上下浏览，可长按连续滚动。节目单内按右键直达“刷新”，按 OK 更新；按左依次经过“刷新 → 刚才浏览的节目 → 频道”，返回键直接回到频道区。
- 节目单中按 OK 返回所选频道直播：已经在播放该频道时直接关闭菜单，保持连续播放；选择其他频道时才加载并切台。频道区确认当前频道也直接关闭菜单。
- 左右键只负责区域导航，到边界后保持当前位置：频道区按左键不退出，刷新按钮上继续按右键不移动。返回键或菜单键用于关闭菜单。
- 当前节目摘要约每分钟刷新；当前播放或正在浏览频道的全天节目单约每 5 分钟刷新，打开菜单和切台时也会检查是否需要更新。
- 节目播出状态直接根据时间更新，不必等待下一次网络请求；按北京时间自动换日，从后台回到应用会主动刷新。
- 网络异常会显示失败状态并在 30 秒后自动重试；已有的当天节目单继续可用，首次获取失败不会一直显示“读取中”。支持顶部按钮手动刷新。
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

正式版的固定签名、构建命令、首次从调试版迁移及签名备份见 [正式版发布说明](docs/RELEASE.md)。本机正式构建命令：

```bash
./build-local.sh --offline :app:assembleRelease
```

输出为 `app/build/outputs/apk/release/app-release.apk`。签名文件仅保存在本机，未配置时 Gradle 会输出 unsigned APK，不能直接安装。

## 继续开发

项目结构、代码入口、构建检查结果和待处理问题见 [开发说明](docs/DEVELOPMENT.md)。

Git 中保存源码、资源、构建配置和 Gradle Wrapper；本机工具链、缓存、APK 和签名密钥均通过 `.gitignore` 排除。

当前应用版本为 `0.4.1`。运行节目单解析、刷新与时间边界测试，并编译和静态检查：

```bash
./build-local.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

测试使用 JUnit 和 JSON 测试库，不会增加 APK 的运行时依赖。首次运行测试需要下载测试依赖。播放器首帧、海报/控件、旧 WebView 兼容分支与重试另有 7 项独立测试：

```bash
node --test tools/player-script.test.mjs
```

本版界面在 Android TV API 36 的 Television 4K 模拟器中验证；视觉规范和界面检查说明见 [电视界面设计](docs/UI_DESIGN.md)。

菜单性能修复、可比计时与测试限制见 [菜单性能说明](docs/PERFORMANCE.md)。设计方向与资产来源见 [品牌与视觉说明](brand-spec.md)；可查看初稿保存在 `design/v0/`。

## 已知限制

- 依赖电视上存在可用的 Android System WebView/Chromium。
- 网页布局和网页播放器更新后，WebView 原型可能需要调整。
- 节目单显示北京时间当天的日程；确认操作返回该频道直播，当前频道保持原有播放，其他频道正常切台。
- 自动刷新只在应用前台运行，后台停止轮询；回到前台会重新请求最新数据。
- 如果站点未来要求验证码、登录或改变播放鉴权逻辑，应使用站点允许的正常流程，不绕过验证。
- 电视端若 WebView 性能或兼容性不稳定，下一步应迁移到 Media3/ExoPlayer，并在原生网络层处理播放地址刷新。
