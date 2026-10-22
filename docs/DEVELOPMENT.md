# ZTV 开发说明

整理日期：2026-10-22。以下内容基于当前工作区源码与本机构建结果。

## 当前基线

- 应用名称：新蓝 TV；版本：`0.1.1`，`versionCode = 2`。
- 应用 ID：`com.example.cztvtv`。
- 目标设备：Android TV / Google TV，横屏、遥控器操作。
- 技术栈：Java 17、Android 原生 Activity / View、WebView；没有额外的应用层依赖。
- Android 配置：最低 API 23，编译与目标 API 35。
- 构建工具：Android Gradle Plugin 8.13.2、Gradle 8.13、Build Tools 35.0.0。
- 当前目录此前没有 Git 历史；初始化提交用于保存接手时的代码基线，不能恢复更早的修改记录。

## 运行方式

应用打开新蓝网对应频道的直播网页，由网页执行鉴权与播放逻辑。原生层通过注入 JavaScript 定位 `video` 元素、隐藏外围网页元素、设置全屏显示并尝试有声播放。

原生层还负责频道菜单、遥控器事件、状态提示与 Activity / WebView 生命周期。当前没有集成 Media3 / ExoPlayer，也不保存临时播放地址或下载媒体分片。

## 主要代码入口

| 文件 / 方法 | 职责 |
| --- | --- |
| `app/src/main/java/com/example/cztvtv/MainActivity.java` | 当前全部应用逻辑，674 行 |
| `CHANNELS`、`currentChannelIndex` | 10 个固定频道及当前频道；默认索引 4，对应民生休闲 106 |
| `onCreate()`、`createChannelMenu()` | 通过 Java 创建 WebView、状态浮层、左侧频道菜单 |
| `createWebView()` | WebView 配置、页面回调、主框架导航限制 |
| `tryStartVideo()` | 注入全屏样式、尝试播放、最多 20 次延时重试；另有首次尝试 |
| `switchChannel()`、`reloadCurrentChannel()` | 切台、重新加载与加载代次控制 |
| `loadChannelPrograms()` | 后台读取节目摘要，更新菜单中的当前节目名称 |
| `dispatchKeyEvent()` | 遥控器事件分发 |
| `onResume()`、`onPause()`、`onDestroy()` | 前后台恢复与 WebView 释放 |
| `app/src/main/AndroidManifest.xml` | 网络权限、TV 启动入口、横屏与硬件加速 |
| `app/build.gradle.kts` | SDK、应用版本与 Java 编译配置 |
| `build-local.sh` | 使用当前工作区 `.toolchain/` 中的本地工具链构建 |

## 已有交互

| 按键 | 播放画面 | 频道菜单 |
| --- | --- | --- |
| 上 / 下 | 切换上一 / 下一频道，首尾循环 | 移动选择，首尾循环 |
| OK / Enter | 再次尝试有声播放 | 播放所选频道并关闭菜单 |
| 菜单 | 打开频道菜单 | 关闭频道菜单 |
| 左 | 消费事件 | 关闭频道菜单 |
| 右 | 消费事件 | 消费事件 |
| 返回 / Esc | 结束应用任务 | 关闭频道菜单 |
| Home 后重新进入 | 重新加载当前频道以恢复播放 | 菜单在进入后台时关闭 |

## 外部数据依赖

- 直播网页前缀：`https://www.cztv.com/liveTV/`，后接频道代码。
- 当前节目摘要接口：`https://p.cztv.com/api/paas/channel/tv`。
- 当前解析逻辑读取 `content.list`，按 `station_code` 匹配频道并显示 `station_playing`。
- 菜单目前只有当前节目名称，没有完整的按时间排列节目单。

以上地址与字段来自当前代码。本次构建检查没有确认网页实际播放或节目接口当前响应结构，后续联调需要单独验证。

## 已确认的开发入口

以下区分源码可确认的行为与需要设备验证的风险，并非已修复列表。

1. **播放失败与恢复**：页面主框架网络错误目前仅显示提示；没有明确的重新加载入口或自动恢复流程。播放尝试最终统一提示“按确认键开始有声播放”，未区分播放器未出现、媒体错误与自动播放受限。
2. **前后台与异步任务衔接**：`onPause()` 未取消播放重试或延时恢复任务；`tryStartVideo()` 只检查 WebView、加载代次与尝试次数，没有前台状态检查。快速按 Home、返回和切台时的实际影响需要真机复现。
3. **节目摘要刷新**：只在 `onCreate()` 请求一次，没有定时刷新。请求失败被忽略，初次失败或频道未匹配时会保留“节目单读取中…”文本。
4. **状态保存**：当前频道没有写入持久化存储，也未从 `savedInstanceState` 恢复；Activity 重建后会使用默认频道。
5. **结构维护**：频道数与频道数组分别声明，新增频道时需要同步修改；界面、网络、播放脚本和生命周期均在同一个 Activity 中。
6. **测试覆盖**：当前没有单元测试或设备测试源码。关键回归场景应覆盖连续切台、菜单循环、网络中断、Home 后返回、连续 OK 重试和退出后的声音释放。

## 本次验证

在当前 macOS 工作区，使用项目自带工具链执行：

```bash
./build-local.sh --offline :app:assembleDebug :app:lintDebug
./build-local.sh --offline :app:assembleDebug --rerun-tasks
```

- 两次构建均成功；第二次重新执行 APK 构建任务，确认当前源码能够重新生成产物。
- Android Lint：0 个错误、9 个警告。
- 警告涉及目标 / 编译 SDK 版本提示、重复 label、横屏配置提示、备份配置、TV 横幅矢量尺寸、未使用颜色资源和硬编码界面文字。
- APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。
- 已用 `aapt dump badging` 核对包名、版本、SDK 与 TV 启动入口。
- 已用 `apksigner verify --verbose` 验证 Debug APK 签名通过；工具提示一个 `META-INF` 元数据条目不受签名保护。
- 尚未进行电视真机或模拟器播放验证；构建和签名通过不等于所有频道在设备上均可播放。

## Git 跟踪范围

版本库保存源码、资源、README、开发说明、Gradle 构建配置及 Gradle Wrapper（包括 Wrapper JAR）。`gradlew` 与 `build-local.sh` 应保留可执行权限。

以下内容仅保留在本机，不提交：

- `.toolchain/`、`.gradle/`、各级构建输出及 APK / AAB。
- `local.properties`、IDE 本地配置与 macOS `.DS_Store`。
- 本地环境文件、签名密钥及 `keystore.properties`。

后续修改应按功能或问题分别提交，再推送到 GitHub；需要独立开发时从 `main` 创建工作分支，以保留清晰的变更与回退记录。
