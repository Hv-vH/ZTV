# ZTV 开发说明

整理日期：2026-10-23。以下内容基于当前工作区源码与本机构建结果。

## 当前基线

- 应用名称：新蓝 TV；版本：`0.4.0`，`versionCode = 7`。
- 应用 ID：`com.example.cztvtv`。
- 目标设备：Android TV / Google TV，横屏、遥控器操作。
- 技术栈：Java 17、Android 原生 Activity / View、WebView；没有额外的应用层依赖。
- Android 配置：最低 API 23，编译与目标 API 35。
- 构建工具：Android Gradle Plugin 8.13.2、Gradle 8.13、Build Tools 35.0.0。
- Git 初始化提交与 `baseline-2026-10-22` 标签保存接手时的 0.1.1 基线；全天节目单功能在 `feat/daily-program-guide` 分支开发。
- 电视界面 0.3.0 保留在 `feat/tv-interface`；菜单性能修复和已确认的新视觉在 `fix/tv-menu-performance`，性能修复单独提交为 `6730c1a`。

## 运行方式

应用打开新蓝网对应频道的直播网页，由网页执行鉴权与播放逻辑。原生层通过注入 JavaScript 定位 `video` 元素、隐藏外围网页元素、设置全屏显示并尝试有声播放。

原生层还负责频道菜单、遥控器事件、状态提示与 Activity / WebView 生命周期。当前没有集成 Media3 / ExoPlayer，也不保存临时播放地址或下载媒体分片。

## 主要代码入口

| 文件 / 方法 | 职责 |
| --- | --- |
| `app/src/main/java/com/example/cztvtv/MainActivity.java` | 播放外壳、频道与节目单双栏界面、遥控器和生命周期 |
| `CHANNELS`、`currentChannelIndex` | 10 个固定频道及当前频道；默认索引 4，对应民生休闲 106 |
| `onCreate()`、`createChannelMenu()` | 通过 Java 创建 WebView、状态浮层、左侧频道菜单 |
| `createWebView()` | WebView 配置、页面回调、主框架导航限制 |
| `startVideoCheck()`、`checkVideoFrame()` | 单一首帧检查链；代次隔离旧回调，前台约 24 秒超时，菜单中暂缓超时 |
| `app/src/main/assets/player.js` | 一次布置全屏样式、移除海报与控件；使用视频帧回调确认可显示，旧 WebView 有兼容分支 |
| `TvLoadingView.java` | 频道加载、缓冲、按 OK 播放、网络失败与重试提示 |
| `TvMenuViews.java` | 缓存字体与背景；暖白焦点、琥珀直播、可复用节目卡片 |
| `switchChannel()`、`reloadCurrentChannel()` | 切台、重新加载与加载代次控制 |
| `refreshProgramGuide()`、`renderProgramGuide()` | 刷新数据并显示全天列表、当前节目、加载 / 失败 / 空列表状态 |
| `ProgramGuide.java` | 不可变节目列表、北京时间、排序去重、节目边界与当前节目判断 |
| `ProgramGuideRepository.java` | 公开节目接口的网络请求与 JSON 解析 |
| `ProgramGuideManager.java` | 刷新节流、按频道与日期缓存、重试、异步响应隔离 |
| `TvMenuViews.java` | 原生频道 / 节目卡片、焦点样式、播出状态与进度条 |
| `dispatchKeyEvent()` | 遥控器事件分发 |
| `onResume()`、`onPause()`、`onDestroy()` | 前后台恢复与 WebView 释放 |
| `app/src/main/AndroidManifest.xml` | 网络权限、TV 启动入口、横屏与硬件加速 |
| `app/build.gradle.kts` | SDK、应用版本与 Java 编译配置 |
| `build-local.sh` | 使用当前工作区 `.toolchain/` 中的本地工具链构建 |

## 已有交互

| 按键 | 播放画面 | 频道菜单 |
| --- | --- | --- |
| 上 / 下 | 切换上一 / 下一频道，首尾循环 | 频道区循环选择；节目单区浏览列表，可长按连续滚动 |
| OK / Enter | 再次尝试有声播放 | 当前频道直接关闭菜单并保持播放；其他频道才加载切台；焦点在“刷新”时重新获取节目单 |
| 菜单 | 打开频道菜单 | 关闭频道菜单 |
| 左 | 消费事件 | 节目单区返回频道区；频道区保持当前位置 |
| 右 | 消费事件 | 频道区进入节目单并定位当前节目；节目单区直达“刷新” |
| 返回 / Esc | 结束应用任务 | 节目单区返回频道区；频道区关闭菜单 |
| Home 后重新进入 | 重新加载当前频道以恢复播放 | 菜单在进入后台时关闭 |

## 外部数据依赖

- 直播网页前缀：`https://www.cztv.com/liveTV/`，后接频道代码。
- 当前节目摘要接口：`https://p.cztv.com/api/paas/channel/tv`。
- 摘要接口读取 `content.list`，按 `station_code` 匹配频道并显示 `station_playing`。
- 全天节目单接口：`https://p.cztv.com/api/paas/program/{station_code}/{yyyyMMdd}`，与网站直播模块使用的接口一致。
- 全天节目读取 `content.list[0].list`；标题为 `program_title`，`play_time` 与 `duration` 均为毫秒，可能以字符串返回。
- 当前界面展示北京时间当天全部节目，含起止时间、完整标题及播出状态；没有历史回看功能。

2026-10-22 已读取网站公开直播脚本，核对以上接口及字段，并将 10 个频道当天的真实响应交给应用解析器检查。网页实际播放与遥控器界面仍需设备验证。

## 节目单刷新与状态

1. **前台刷新**：启动、回到前台主动请求；打开菜单和切台检查缓存。当前节目摘要间隔 60 秒，当前播放或浏览频道的全天列表间隔 5 分钟；网络失败后 30 秒重试。手动刷新和前台恢复可绕过缓存，2 秒内的重复请求被节流。
2. **本地时间更新**：前台定时器最长 15 秒唤醒一次；已知节目起止点与北京时间午夜会提前唤醒。新数据到达后重新计算下一次唤醒时间。节目状态不依赖摘要接口恰好在交接时刷新。
3. **北京时间换日**：日期、列表时间、午夜边界均使用 `Asia/Shanghai`。日期变化后清空旧日缓存并请求当天数据；旧日期的迟到响应不能写入新日期状态。
4. **并发与生命周期**：后台使用 2 个工作线程，同频道请求去重，结果在主线程写入按频道保存的状态。后台停止定时轮询；销毁时关闭管理器、释放监听器，迟到响应不再更新 Activity。
5. **失败和空数据**：初次失败、成功但空列表、刷新中、已有缓存后刷新失败分别展示。失败保留当天可用列表；摘要过期时不再把旧摘要当作当前节目。接口结构异常或全部条目日期不匹配视为失败。
6. **列表与焦点**：频道变化时默认定位当前节目；刷新同频道时保留所浏览节目的焦点，按开播时间和标题匹配，不强制跳回当前节目。节目名称完整换行显示。
7. **节目边界**：采用开始时刻包含、结束时刻不包含的区间；重叠时下一节目优先；缺少时长时使用下一节目开始或当日午夜。过滤空标题、无效时间、非请求日期条目，排序并去重。
8. **菜单确认与边界导航**：左右键到达频道区左边界或刷新按钮右边界后保持焦点。频道区或节目单中确认正在播放的频道只关闭菜单，不增加加载代次、不停止网页加载、不调用直播页面重新加载；不同频道仍正常切台。刷新按钮使用独立操作，不受同频道确认逻辑影响。

刷新间隔以单调时钟计算，节目时间以系统墙上时钟计算。网络请求独立于视频网页，不会为刷新节目单而重新加载直播。

## 后续开发入口

- 主页面网络错误、HTTP 错误、媒体加载错误与超时进入原生错误页，按 OK 重新连接；尚未覆盖所有播放过程中断流与 WebView 渲染进程退出情形。
- 播放检查在暂停/销毁时取消；切台和新一轮检查使旧回调失效。不同物理电视的快速 Home、返回与解码兼容性仍需验证。
- 当前频道没有持久化，也未从 `savedInstanceState` 恢复；Activity 重建仍会使用默认频道。
- 站点仅提供直播，保留当天节目浏览；不加入回看与点播入口。

## 本次验证

在当前 macOS 工作区，使用项目自带工具链执行：

```bash
./build-local.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

- 30 项单元测试全部通过，覆盖解析、空值、毫秒时间、节目交接、间隙与重叠、时区、跨天、缓存、失败重试、请求去重、频道响应隔离和销毁后的回调。
- 2026-10-22 的基线验证：10 个频道共 1135 个节目与 10 个摘要。2026-10-23 初稿使用 10 个真实摘要和民生休闲全天 114 项节目。
- Debug APK 构建成功；Android Lint 为 0 个错误、7 个警告。报告位于 `app/build/reports/`，警告涉及 SDK / Gradle 更新提示、重复 label、TV 横幅、横屏与备份配置。
- 7 项播放器脚本测试通过：首帧前保持遮罩、海报/控件移除、视频节点替换、旧 WebView 分支、播放拦截后重试与媒体错误。命令：`node --test tools/player-script.test.mjs`。
- APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。
- 已用 `aapt dump badging` 核对包名、版本、SDK 与 TV 启动入口。
- 已用 `apksigner verify --verbose` 验证 Debug APK 签名通过；工具提示一个 `META-INF` 元数据条目不受签名保护。
- 0.4.0 已安装到 Android TV API 36、arm64 的 Television 4K 模拟器，通过 15 项遥控器与 8 项扩展流程，检查实际界面、焦点、全天 114 项首尾浏览、刷新、返回、切台与 200 次快速开关；延迟连接与断网重试通过。模拟器可播放直播；仍需物理电视验证不同 WebView 和硬件解码环境的兼容性。

视觉方案与本次模拟器检查说明见 [UI_DESIGN.md](UI_DESIGN.md)。物理电视回归时仍应检查断网重试、Home 后返回、跨午夜，以及不同系统 WebView 的兼容性。

## Git 跟踪范围

版本库保存源码、资源、README、开发说明、Gradle 构建配置及 Gradle Wrapper（包括 Wrapper JAR）。`gradlew` 与 `build-local.sh` 应保留可执行权限。

以下内容仅保留在本机，不提交：

- `.toolchain/`、`.gradle/`、各级构建输出及 APK / AAB。
- `local.properties`、IDE 本地配置与 macOS `.DS_Store`。
- 本地环境文件、签名密钥及 `keystore.properties`。

后续修改应按功能或问题分别提交，再推送到 GitHub；从当前已验证版本或对应功能分支创建新分支。`main` 仍保留接手基线，不能代替最新功能版本。
