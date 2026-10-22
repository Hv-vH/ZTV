# 新蓝 TV：视觉调整与资产清单

## 范围和已确认方向

- 模式：Redesign · Preserve。维持原生 Android 应用、左右双栏、频道顺序、全天节目数据和遥控器交互。
- 使用场景：客厅电视，按约 3 米观看距离安排字号；菜单承担快速查找与切台，加载页只表达当前频道与连接状态。
- 视觉温度：安静、温暖、克制；密度沿用当前双栏，长节目名继续换行。
- 校准：视觉变化 2，动效 1，信息密度 7，图像依赖 4，品牌保留 7。Z 字母与应用名称延续，色彩、字形层级和生成图标按用户请求调整。
- 用户已确认：炭灰＋暖白＋少量琥珀色、思源黑体；已看过并确认 v0，按该初稿接入原生 Android 0.4.0。

## 现状审计

- 0.3.0 使用深蓝渐变、亮蓝焦点、青绿直播状态，系统 sans-serif / medium，重复的卡片渐变和阴影。
- 双栏结构、全天节目、同频道确认只收起菜单、左侧与右侧边界保留。
- 改善：字体与对比层级、焦点/直播的区分、加载时露出的网页海报和控件。
- 技术问题单独修复：一次创建全部节目 View、隐藏菜单仍绑定数据、重复焦点刷新、遮住播放器后仍合成隐藏画面。
- 旧界面资源（历史提交 `832f274`）：`app/src/main/res/drawable/ic_brand.xml`、`ic_refresh.xml`。真实截图：`app/build/qa/`；用户提供电视照片作为加载问题证据。

## v0 系统

| 角色 | 规格 |
| --- | --- |
| 背景 | #141615 |
| 面板 / 普通卡片 | #1A1E1B / #202521 |
| 主文字 / 焦点填充 | #F4F1E8 |
| 次文字 | #A9AEA7 |
| 直播、加载反馈 | #DDB477，限量使用 |
| 焦点文字 | #1B211C |
| 字体 | 思源黑体：400 / 500；IBM Plex Sans：时间与编号 |
| 大小 | 频道标题 28sp，节目 18sp，频道名 19sp，辅助 12–15sp |
| 间距 | 4dp 基准；外边距 32dp；双栏间距 26dp |
| 圆角 | 卡片 / 按钮 12dp，侧栏 16dp |
| 阴影 / 动效 | 无厚重阴影，v0 静态；正式版仅必要状态反馈 |

## 文件与来源

- 初稿：`design/v0/index.html`，独立评审稿。正式界面为原生 Android View，并未迁移到网页。
- 真实节目快照：`design/v0/data.json`；来源记录在快照内，使用网站公开接口。
- 思源黑体：`design/v0/assets/fonts/SourceHanSansCN-VF.woff2`，来源 https://github.com/adobe-fonts/source-han-sans ，随附 SourceHanSans-LICENSE.txt。
- IBM Plex Sans：`design/v0/assets/fonts/IBMPlexSans-Regular.woff2`，来源 https://github.com/IBM/plex ，随附 IBMPlex-LICENSE.txt。
- 生成品牌图标：`design/v0/assets/brand-icon.png`，沿用 Z 意象，暖白缎面与浅香槟金属，不使用电视台官方标志。
- 生成直播信号图标：`design/v0/assets/live-signal.png`，香槟色中心圆与信号弧线。
- 生成提示词与工具记录：`design/v0/assets/GENERATION.md`。
- 正式资源：`app/src/main/res/drawable-nodpi/brand_mark.png`（512×512）和 `live_signal.png`（128×128）；只用于品牌、状态和加载页，不放入每个节目行。
- 原生字体：`app/src/main/assets/fonts/`，思源字体固定到 400/500 并裁剪为 GB2312 与常用标点，家族名改为 ZTV Han；超出子集使用系统字体回退。保留版权与 OFL 文件，构建脚本 `tools/prepare-tv-fonts.py`。字体设为 APK 不压缩资源，应用初始化时只读取与缓存一次。
- 真实 4K 截图与加载/错误状态：`app/build/qa/v04/`，不纳入 Git。

## 受保护的交互

菜单键开关；频道左键停在边界；节目右键到刷新，再右键停留；节目上下支持长按；同频道 OK 只收起菜单；其他频道才加载；返回从节目到频道、再关闭。无回看与点播，不改变官方网页鉴权与直播地址。

最大风险为节目列表复用时的焦点恢复，使用 TV 模拟器按键与实际节目列表验证；已在初稿确认后接入正式 UI。旧视觉可由 `832f274` 恢复，性能修复单独提交。
