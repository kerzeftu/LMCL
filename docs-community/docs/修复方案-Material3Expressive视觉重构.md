# 修复方案：Material 3 Expressive 视觉重构

> **状态（2026-10-24 规划优化）：切片 A 基座已实现并默认关闭；v3.17.1 主页卡片动态高度、v3.17.2 账户卡/5×7 日历、v3.17.3 日历右栏统计/设置·统计、v3.17.4 日历色阶随主题明暗反转、v3.17.5 每日一言、v3.17.6 本地源选择、v3.17.7 每日一言设置层级调整已交付；本文新增 §19 执行规范（参考 M3E 主提示词）；M3 切片 B–G 仍待 Windows 对切片 A 的验收。**
>
> - 当前产品：LMCL 1.0.0，基于 HMCL v3.16.4；文档 v3.17.7
> - 当前代码基线：`/home/kerze/hmclce/lmcl`，v3.17.7 交付后 `main`；切片 A 的 `m3expressive1` 已由 `m3expressive9` 取代
> - 切片 A 已接入 `M3Host`/`M3Settings`/`M3MotionSupport`/`CommunityControls`；
>   `-Dlmcl.visual.m3=on` 时只给壳层子树安装 m3fx，默认关闭时与 1.0.0 一致。
> - 阶段 0 的 `m3spike1–m3spike14` 是历史记录；当前测试包为 `m3expressive9`（v3.17.7 每日一言设置层级调整 + 本地源选择 + 每日一言 + 账户卡/7×5 日历 + 右栏统计 + 明暗自适应色阶）。
> - 本次复启按 §17 的垂直切片执行，不再按旧的“阶段 1 一次性全量 token”推进。
> - §19 吸收用户提供的《HMCL × Material 3 Expressive 重构主提示词》，补齐硬约束、JavaFX 能力缺口、参考资源、每步交付物与“不要一次改全 UI”的执行纪律；不改变已批准的 D1–D7。
>
> 本文回答“怎么做、分几步、每步改哪些文件、怎么验收、失败了怎么退”。
> 阶段 0 spike 已落盘；切片 A 已实现；B–G 仍需逐切片批准。

---

## 0. 一页摘要

### 0.1 要做什么

把 LMCL 现有界面从“HMCL 原有 Material 2/JFoenix 视觉 + 社区版补丁样式”
升级为统一的 **Material 3 Expressive** 视觉体系，并落地四个可感知特征：

1. **全界面视觉规范统一**：颜色角色、字体层级、圆角/形状、间距、状态层、动效曲线。
2. **m3fx 组件库接入**：使用 `Glavo/m3fx` 提供的 M3 组件与主题，而不是自己重画控件。
3. **侧栏与主页组件毛玻璃**：64px 侧栏、主页 9 张卡片、主页头部获得“壁纸磨砂玻璃”表面。
4. **Wavy 进度指示器**：下载/任务/加载的线性与环形进度改为 M3 Expressive 波浪形态。

### 0.2 推荐范围（需要用户批准）

推荐采用 **“全量视觉规范 + 重点界面组件替换 + 壳层/主页结构视觉重做”**：

- **L1 全量**：所有页面纳入 M3 token/字体/形状/状态层/动效规范；
- **L2 重点**：主页、壳层、设置、管理、其他、下载入口、任务/加载、对话框优先使用 m3fx 组件；
- **L3 结构**：不改 9 卡布局、不改 64px 侧栏宽度、不改页面导航行为，只重做视觉与表面。

**不推荐**把 243 个上游 UI 类的 skin 全部替换成 M3FX 控件。原因：

- HMCL 的大量控件有继承设置、只读模式、键盘操作、拖放、虚拟化列表等行为；
- m3fx 是独立组件库，控件语义与 JFoenix/HMCL 自定义控件不完全等价；
- 全量替换会把“视觉升级”变成“重写交互”，回归面不可控。

如果用户要求“连控件行为也全部 M3 化”，需要另立“控件迁移”子项目，工期会从 16–22 周升到 30 周以上，本文不承诺。

### 0.3 关键数字

| 项 | 估算 |
|---|---|
| 阶段数 | 9（0–8） |
| MVP（阶段 0–4） | 8–11 周 |
| 全量（阶段 0–8） | 16–22 周（单人；可与功能开发部分并行） |
| 新增生产依赖 | m3fx（vendor 源码，Apache-2.0） |
| 新增运行时 jar 体积 | 约 1.3–1.5 MB（未压缩） |
| 新增代码侵入 | **设计上 0 处**；完整 Wavy/加载已批准 +3（`TaskListPane`/`DialogPane`/`SpinnerPane`，见 §6.2/§14；代码落地前 00 仍记 13） |
| 新增资源 | `assets/css/m3-community.css`、m3fx 自带样式、NOTICE/LICENSE |
| 目标平台 | Windows 10/11 优先；Linux/macOS 做降级验证 |
| 默认开关 | 开发期 `visual.m3=off`，发布版 `on`；可回退 |

### 0.4 五个需要拍板的决策

| # | 决策 | 推荐 | 备选与代价 |
|---|---|---|---|
| D1 | 替换范围 | **已选：全量视觉 + 重点组件**（不重写全部上游控件行为） | 全量控件替换：回归风险高，工期 +50% |
| D2 | 毛玻璃实现 | **已选（暂定）：A 壁纸快照磨砂**；C 仅作后续 Windows 实验 | C 仅 Windows、依赖 DWM；不能模糊应用内卡片后的内容 |
| D3 | 依赖方式 | **已选：阶段 0 用 JitPack，阶段 1 起 vendor 源码** | 长期 JitPack：可用性/可复现性差；fork 发布：维护成本 |
| D4 | 侧栏形态 | **已选：保留 64px 自定义侧栏，M3 重绘** | 换 `M3NavigationRail` 96px：挤压内容，需改布局 |
| D5 | 波浪强度 | 默认 amplitude 2.5、wavelength 40（待最终确认） | 强波浪 amplitude 4+：更“Expressive”，但小尺寸下显抖 |
| D7 | 启动按钮 | **已选（暂定）：形态 1**，保留双段按钮只做 M3 视觉 | 形态 2 M3 FAB：信息结构和交互需重建 |

D6（进度侵入）**已批准**；D7（启动按钮是否 FAB）与 D8（MVP 排期）见 §14 第 7–8 项。

---

## 1. 需求、边界与验收

### 1.1 用户需求原文（m14217）

> “新任务，规划以Material 3 Expressive风格重构全部ui界面和视觉效果，引入m3fx库，
> 为侧边栏和主页组件添加毛玻璃效果，为下载和加载时的进度条设计Wavy progress indicator，
> 类似LinearWavyProgressIndicator，并且引入其他Material 3 Expressive的画面特征
> （优先引入已有库和m3fx包含的），写详细分布md”

拆成可执行项：

| 编号 | 需求 | 本文落点 |
|---|---|---|
| R1 | 全部 UI 界面与视觉效果 M3 Expressive 化 | §4 规范、§5 阶段 1/5/6/7、§8 覆盖矩阵 |
| R2 | 引入 m3fx 库 | §3 评估、§4.2 依赖、阶段 0/1 |
| R3 | 侧边栏毛玻璃 | §4.6、阶段 2 |
| R4 | 主页组件毛玻璃 | §4.6、阶段 2/3 |
| R5 | 下载/加载 Wavy 进度条 | §4.7、阶段 4 |
| R6 | 其他 M3 Expressive 特征，优先 m3fx | §3.4 组件矩阵、§4.8 替换策略 |
| R7 | 写详细分步 md | 本文 |

### 1.2 覆盖定义

本文的“全部 UI”指 **用户能看到的全部界面表面**，分三层：

- **L1 视觉覆盖（所有页面）**：
  颜色、字体、字号/字重、圆角、描边、阴影、状态层、焦点环、滚动条、动效曲线。
  不改变控件类、布局结构和事件语义。
- **L2 组件覆盖（重点页面）**：
  用 m3fx 的 `M3Button`、`M3IconButton`、`M3Card`、`M3Switch`、`M3Slider`、
  `M3TextField`、`M3SearchBar`、`M3FilterChip`、`M3TabBar`、`M3ProgressBar`、
  `M3ProgressIndicator`、`M3DialogPane` 等替换叶子控件。
- **L3 结构视觉覆盖（壳层/主页）**：
  侧栏、窗口装饰、主页双列、主页头部、主页卡片、背景/玻璃层。

不在本文范围：

- 页面导航结构、功能入口、业务流程；
- 9 张主页卡片的数量/数据来源（由步骤 14/15/21 决定）；
- 安装器、stub、CI、签名、更新服务；
- 全量重写上游 `GameSettingsPage`、`GameListPage`、`VersionPage`、`ModListPage` 等复杂页的控件行为。

### 1.3 成功标准

功能与视觉同时满足才算完成：

1. **统一性**：任意两个页面的按钮/输入框/卡片/对话框，圆角、颜色角色、字体层级一致；
2. **可读性**：正文对比度 ≥ 4.5:1，大字号 ≥ 3:1；玻璃表面上的文字仍达标；
3. **可操作性**：所有原按钮、菜单、快捷键、Tab 顺序不变；
4. **性能**：冷启动增量 ≤ 300 ms；主页空闲 60 FPS；滚动无明显掉帧；
5. **可降级**：`visual.m3=off` 时回到 v3.15 视觉；`visual.glass=off` 时回到不透明 M3 表面；
6. **可复现**：不依赖 JitPack 运行时；构建可离线（依赖已 vendor）；
7. **不扩散侵入**：设计上不新增第 14 处；完整 Wavy/加载若必须改 `TaskListPane`/`DialogPane`/`SpinnerPane`，须先批准并登记为 #14–#16（见 §6.2）。

### 1.4 非目标

- 不把 HMCL 的 JFoenix 控件全部删除；
- 不重写 `TransitionPane`/`Navigator` 的页面切换机制；
- 不实现 Windows 原生 Acrylic（可另立实验项）；
- 不改变 64px 侧栏宽度，不把主页改成单列；
- 不引入 WebView/前端技术栈。

---

## 2. 现状盘点（v3.15 实测）

### 2.1 壳层与侧栏

| 组件 | 文件 | 现状 |
|---|---|---|
| 全局侧栏 | `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java` | 64px `VBox`，5 个 `JFXButton`（主页/下载/管理/设置/其他），`community-sidebar` 样式，`CommunityStyles.apply` |
| 导航壳 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` | `HBox(sidebar, navigator)`，`decorator.getContent().setAll(shell)`，`onNavigated` 同步 active |
| 窗口装饰 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/Decorator.java` / `DecoratorSkin.java` | `StageStyle.TRANSPARENT`；`DecoratorSkin` 内有 8px 圆角 clip、`backgroundNode`、`frame` |
| 场景 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java` | `scene.setFill(Color.TRANSPARENT)`，`StyleSheets.init(scene)` |

**结论**：壳层已是“透明窗口 + 自绘背景 + 全局侧栏”，具备做玻璃和 M3 表面的结构条件。

### 2.2 主页

`HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java`：

- `extends VBox`，`HOME_PADDING = Insets(10,12,12,12)`；
- 左列 `infoColumn`（宽 340）：固定账户卡 + `activity-calendar` + `recent-worlds`；
- 右列 `instanceColumn`：`launch`、`pinned-instances`、`java-recommend`、
  `snapshot`、`crash-history`、`health-check`、`network`；
- 左右各一个独立 `ScrollPane`；账户卡固定左栏，不进入可拖拽组件列表；
- 共 9 张组件卡，无 memory/storage 卡（v3.5 移除 memory，v3.9 移除 storage）；
- **v3.17.1 已删除固定槽位表**：`HomePane.addWidget` 设 `minHeight=USE_PREF_SIZE`、
  `prefHeight=USE_COMPUTED_SIZE`、`maxHeight=USE_PREF_SIZE`，卡片高度 = 标题 + 实际内容 + footer；
- 卡片 footer 统一由 `BuiltinWidgets.cardFooter` / `cardContent` 生成：一个按钮靠右，
  恰好两个一左一右，三个及以上靠右成组；正文 `VBox` 吸收多余高度，footer 贴底。

**结论**：布局已稳定，M3 化只换表面与字体/按钮；**不要重新引入固定卡片高度或槽位表**。

### 2.3 进度/加载现状

| 位置 | 文件:行 | 现状 | 替换目标 |
|---|---|---|---|
| 对话框进度 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/DialogPane.java:41` | `JFXProgressBar` | `M3ProgressBar` |
| 任务列表 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/TaskListPane.java:288` | `JFXProgressBar` | `M3ProgressBar` |
| Spinner | `HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/SpinnerPane.java:209` | `JFXSpinner` | `M3LoadingIndicator` |
| 微软登录 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/ClassicAccountLoginDialog.java:58` | `JFXProgressBar` | `M3ProgressBar` |
| Terracotta | `HMCL/src/main/java/org/jackhuang/hmcl/ui/terracotta/TerracottaControllerPage.java:513` | `JFXProgressBar` | `M3ProgressBar` |

**结论**：替换点集中且少，是低风险高收益的 M3 Expressive 切入点。

### 2.4 样式与主题

- `HMCL/src/main/resources/assets/css/root.css`：2011 行、376 个类选择器，HMCL 视觉主体；
- `HMCL/src/main/resources/assets/css/community.css`：127 行，社区版语义类；
- `CommunityStyles` 只挂 `/assets/css/community.css`，节点级、幂等，不改 `StyleSheets`；
- `StyleSheets.init(Scene)` 用 `Bindings.bindContent(scene.getStylesheets(), stylesheets)`，
  **scene 级样式表被上游独占**，不能直接往 scene 加 m3fx 样式。

### 2.5 主题/字体 API

- `HMCL/src/main/java/org/jackhuang/hmcl/theme/Themes.java`：
  - `colorSchemeProperty()` :549；
  - `getColorScheme()` :554，返回 `org.glavo.monetfx.ColorScheme`；
  - `backgroundProperty()` 返回 `LauncherBackground(Background, opacity)`。
- `HMCL/src/main/java/org/jackhuang/hmcl/setting/StyleSheets.java`：
  - 字体/主题/亮度/root.css 四张表；
  - `FontManager.fontProperty()` 变化时替换字体表。
- `HMCL/src/main/java/org/jackhuang/hmcl/setting/FontManager.java`：
  - `getFont()` :234 返回 `FontReference(family, style)`；
  - 字体可以是用户自定义文件。

### 2.6 构建与版本

- Java 17 运行，JavaFX 21.0.8（`buildSrc/.../JavaFXPlatform.java:28`）；
- 已依赖 MonetFX 0.4.0（`gradle/libs.versions.toml:22`、`HMCL/build.gradle.kts:64`）；
- 构建环境：
  ```bash
  export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11
  export GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home
  export HMCL_LAUNCHER_EXE=/home/kerze/hmclce/.tools/stub-build/HMCLauncher.exe
  VERSION_ROOT=3.16 BUILD_NUMBER=1000 ./gradlew :HMCL:makeExecutables --no-daemon
  ```

### 2.7 侵入预算

`docs/00-整体要求.md` §4.2 当前固定 **13 处**代码侵入。与本方案相关的已有位置：

| # | 文件 | 用途 |
|---|---|---|
| 7 | `DecoratorController.java` | 全局侧栏与导航壳 |
| 9 | `Controllers.java` | 深链/对话框入口 |
| 12 | `PersonalizationPage.java` | 外观设置内嵌主页自定义 |

**侵入策略**：M3 主题安装、玻璃、样式默认不新增侵入；只有“完整 Wavy/加载替换”
需要把 `TaskListPane`/`DialogPane`/`SpinnerPane` 登记为 #14–#16（需批准，见 §6.2/§14）。默认落点：

- 已有第 7 处 `DecoratorController.java` 内（壳层组装）；
- 新增 `org.jackhuang.hmcl.community.ui.m3` 包；
- 新增 `assets/css/m3-community.css`；
- 社区自己的 `HomePane`/`CommunitySideBar`/`DashboardWidget` 包装类。

如果实现中发现必须改第 14 个上游文件，**停止并回到本文 §11 重新审批**，不允许先改后补。

---

## 3. m3fx 评估

### 3.1 库身份

| 项 | 值 |
|---|---|
| 仓库 | `https://github.com/Glavo/m3fx` |
| 作者 | Glavo（HMCL 贡献者、MonetFX 作者） |
| 本地参考 | `/home/kerze/hmclce/.reference/m3fx` |
| 参考 commit | `96ccd6d26fa8b38b7621c47332cfcc0caff034bf`（2026-09-11） |
| 版本 | 源码 `1.0-SNAPSHOT`；无 git tag |
| 许可证 | Apache-2.0 |
| Java | 17（`options.release=17`） |
| JavaFX | 编译用 21；`requires transitive javafx.controls/graphics` |
| 模块 | `org.glavo.m3fx` |
| 发布 | **无 Maven Central**；仅 JitPack |
| JitPack 可用构建 | `com.github.Glavo:m3fx:2481f88fd6`（POM 依赖 MonetFX 0.4.0、annotations 26.1.0，jar 1.36 MB） |
| JitPack 最新 | `96ccd6d26f` 构建 Error，不可用 |

> 结论：m3fx 方向正确、与 HMCL 技术栈同源，但**不是可长期依赖的正式发布件**。
> 阶段 0 可用 JitPack 验证，阶段 1 必须 vendor。

### 3.2 与 HMCL 的契合点

1. **同一套 MonetFX 色板**：m3fx 用 `org.glavo.monetfx.ColorScheme` 生成 M3 token，
   HMCL 已有 `Themes.getColorScheme()`，可直接 `M3Theme.fromColorScheme(...)`，不需要二次取色。
2. **同一 JavaFX 版本线**：HMCL JavaFX 21.0.8，m3fx 以 21 编译；Java 17 运行。
3. **作者熟悉 HMCL**：m3fx 仓库内有 `hmcl-md3-demo` 子工程，用 HMCL v3.16.2 资源做
   首页/实例/下载/设置/账户 demo，说明它已针对 HMCL 的信息密度做过适配。
4. **组件覆盖足够**：按钮、卡片、对话框、进度、开关、滑块、搜索、Tab、导航、Snackbar 都有，
   不必自己画 Wavy。

### 3.3 风险与限制

| 风险 | 说明 | 缓解 |
|---|---|---|
| 未正式发布 | 无 tag、无 Maven Central，JitPack 最新构建失败 | vendor 源码，固定 commit |
| API 可能变 | 1.0-SNAPSHOT | vendor 后由我们控制升级 |
| 与 HMCL CSS 冲突 | `.m3-root` 会设字体/背景 | 局部 host + 高优先级覆盖（§4.3） |
| 控件语义差异 | `M3TextField`/`M3Switch` 与 JFoenix 行为不同 | 只替换叶子控件，保留原 handler/binding |
| 独立 popup/window | m3fx 主题只覆盖安装的 Parent 子树 | 对话框/弹窗单独安装（阶段 7） |
| 许可 | Apache-2.0 与 GPLv3 聚合 | 保留 LICENSE/NOTICE，不分叉闭源 |
| 体积 | jar +1.36 MB | 可接受；不做 ProGuard 裁剪（风险高） |

### 3.4 组件能力矩阵（只列本方案会用到的）

| 需求 | m3fx 组件 | 用途 | 采用阶段 |
|---|---|---|---|
| 主题/色板 | `M3Theme`、`M3Profile.EXPRESSIVE_2025`、`M3ThemeManager` | 全局 token | 1 |
| 卡片 | `M3Card`、`M3CardVariant` | 新建面板、关于页 | 3/5/7 |
| 按钮 | `M3Button`、`M3ButtonVariant`、`M3IconButton` | 主页头部/设置/对话框 | 3/5/7 |
| 线性进度 | `M3ProgressBar` | 下载/任务/对话框 | 4 |
| 环形/加载 | `M3ProgressIndicator`、`M3LoadingIndicator` | SpinnerPane/加载页 | 4 |
| 开关 | `M3Switch` | 新设置项（玻璃/Beta） | 2/5 |
| 滑块 | `M3Slider` | 波纹幅度/模糊半径调试页（仅开发） | 0/5 |
| 输入 | `M3TextField` | 下载源 URL、搜索 | 5/6 |
| 搜索 | `M3SearchBar` | 实例/模组/世界列表 | 6 |
| 筛选 | `M3FilterChip` | 版本列表筛选 | 6 |
| Tab | `M3TabBar` | 不替换上游 `TabHeader`；用于新面板 | 3/7 |
| 顶栏 | `M3TopAppBar` | 新面板/对话框标题 | 3/7 |
| 导航栏 | `M3NavigationRail` | **不替换** 64px 侧栏；仅作视觉参考 | 2 |
| 对话框 | `M3DialogPane`、`M3Dialog` | 新对话框；旧对话框先换壳 | 7 |
| 消息条 | `M3Snackbar` | 保存成功/导入结果 | 5/7 |
| 徽标 | `M3Badge` | 崩溃红点、未读 | 3 |
| 提示 | `M3Tooltip` | 替换部分 `FXUtils.installFastTooltip` | 5 |
| 文本 | `M3Text`、`M3TextRole` | 新面板标题/正文 | 3/5 |
| 无障碍 | `M3ColorAccessibility` | 对比度测试 | 8 |
| 动效 | `M3MotionScheme.expressive()`、`M3StateTransition`、`M3AnimatedVisibility`、`M3AnimatedContent`、`M3LayoutTransition` | 页面/卡片/面板动效 | 2/3/5 |

### 3.5 阶段 0 的 Go/No-Go 门槛

阶段 0 结束必须全部满足，否则暂停并重新评估：

1. 用 JitPack 坐标在 `HMCL` 模块编译通过（Java 17 + JavaFX 21.0.8）；
2. 运行 `M3SpikePane`，在 Windows 实机看到 M3 按钮/卡片/进度；
3. 与现有 `root.css`/`community.css` 共存，无明显字体/背景污染；
4. 启动增量 ≤ 300 ms，jar 增量 ≤ 2 MB；
5. 深色/浅色、中文/英文都不崩；
6. 记录 JitPack 构建不可用时的 fallback（vendor 源码）步骤。

> **阶段 0 实施状态（2026-10-22）**：第 1 项已满足；第 6 项已记录。第 2/3/5 项必须在 Windows 实机验证，本机 WSL2 没有可用 X11，尚未验证；第 4 项的 jar 增量已测（+562,803 B，约 0.56 MB），启动增量与内存待实机。因此 Go/No-Go **尚未正式通过**，当前只达到“可编译、可打包的 spike”状态。

---

## 4. 总体设计

### 4.1 分层

```
┌────────────────────────────────────────────────────────────┐
│ L3  壳层/主页结构视觉                                      │
│  DecoratorController: StackPane(壁纸层, M3Host(HBox(sidebar,navigator))) │
│  HomePane: 头部 + 双列 ScrollPane + GlassCard 包装          │
├────────────────────────────────────────────────────────────┤
│ L2  重点界面组件替换                                       │
│  主页头部按钮、设置/管理/其他/下载入口、任务/加载、对话框   │
├────────────────────────────────────────────────────────────┤
│ L1  全量视觉规范（所有页面）                               │
│  m3-community.css + M3Theme token + 字体/形状/状态层/动效   │
└────────────────────────────────────────────────────────────┘
```

### 4.2 依赖引入

#### 4.2.1 阶段 0：JitPack 临时依赖

`gradle/libs.versions.toml`：

```toml
[versions]
m3fx = "2481f88fd6"

[libraries]
m3fx = { module = "com.github.Glavo:m3fx", version.ref = "m3fx" }
```

`settings.gradle.kts` 的 `pluginManagement`/`dependencyResolutionManagement` 增加：

```kotlin
maven { url = uri("https://jitpack.io") }
```

`HMCL/build.gradle.kts`：

```kotlin
dependencies {
    implementation(libs.m3fx)
}
```

> 只允许在阶段 0 分支使用 JitPack。阶段 1 必须删除 JitPack 仓库，改为 vendor。

#### 4.2.2 阶段 1：vendor 源码（推荐）

目录：

```
lmcl/
├── lib/
│   └── m3fx/                    # 从 .reference/m3fx 复制的源码（保留 Apache-2.0 头）
│       ├── build.gradle.kts
│       ├── src/main/java/...
│       ├── src/main/resources/org/glavo/m3fx/styles/...
│       ├── LICENSE
│       └── NOTICE
└── settings.gradle.kts          # include(":m3fx")；projectDir = "lib/m3fx"
```

`settings.gradle.kts`：

```kotlin
include(":m3fx")
project(":m3fx").projectDir = file("lib/m3fx")
```

`HMCL/build.gradle.kts`：

```kotlin
dependencies {
    implementation(project(":m3fx"))
}
```

`lib/m3fx/build.gradle.kts` 改为与本项目一致的 Java 17：

```kotlin
plugins { `java-library` }
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}
dependencies {
    api("org.glavo:MonetFX:0.4.0")
    compileOnlyApi("org.jetbrains:annotations:26.1.0")
}
```

注意：

- 不复制 `.git`、`hmcl-md3-demo`、测试资源中与运行无关的文件；
- 保留 `LICENSE` 与所有源码头；
- 在仓库根 `LICENSES/` 或 `docs-community/` 增加来源说明；
- 更新 `patches/BASELINE.txt`？不需要，m3fx 不是上游 HMCL；
- 在 `README`/`docs` 记录 vendor commit `96ccd6d...` 与 JitPack 验证 commit `2481f88...`。

#### 4.2.3 依赖决策记录

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| JitPack commit | 1 分钟接入 | 服务不可用、无源码、不可审计 | 仅 spike |
| vendor 源码 | 可控、可审计、可离线 | 维护成本、升级需 merge | **采用** |
| fork + GitHub Packages | 可复用 CI | 需建仓库/发布流程、额外凭证 | 备选 |
| 自己实现 M3 | 无依赖 | 工作量巨大、质量低 | 否决 |

### 4.3 主题桥接：`CommunityM3`

#### 4.3.1 为什么不直接 `M3ThemeManager.install(scene, ...)`

- `StyleSheets.init(scene)` 用 `Bindings.bindContent` 独占 scene stylesheet；
- `M3ThemeManager.install(Scene, ...)` 可能接管 scene root，导致 HMCL 的字体/背景被覆盖；
- 我们需要“M3 主题只作用于 LMCL 内容，不破坏窗口背景和用户字体”。

#### 4.3.2 Host 结构

```
Decorator (Control)
└── DecoratorSkin
    ├── wrapper (背景)
    ├── backgroundNode (壁纸)
    └── content
        └── StackPane root                    ← DecoratorController 设置
            ├── Region wallpaperLayer         ← 绑定 Themes.backgroundProperty
            └── M3Host (StackPane)            ← CommunityM3.install(host)
                └── HBox(sidebar, navigator)
```

`M3Host` 是一个空的 `StackPane`，只负责挂 M3 主题和布局；自身背景透明。

#### 4.3.3 `CommunityM3` 代码骨架

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.theme.M3Profile;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.setting.FontManager;

import java.net.URL;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// LMCL 的 M3 主题宿主。
///
/// 只安装在 [M3Host] 子树，不接管 Scene；主题色跟随 [Themes#getColorScheme()]，
/// 字体跟随 [FontManager#getFont()]，减少动画跟随 HMCL 的“禁用动画”设置。
@NotNullByDefault
public final class CommunityM3 {

    private static final String CSS = "/assets/css/m3-community.css";

    /// 样式表必须用 URL 外部形式挂载；JavaFX 不接受裸 classpath 路径。
    private static String cssUrl() {
        URL url = CommunityM3.class.getResource(CSS);
        return url != null ? url.toExternalForm() : null;
    }

    private final StackPane host;
    private final ChangeListener<ColorScheme> schemeListener;
    private final ChangeListener<FontManager.FontReference> fontListener;
    private final ChangeListener<Boolean> animationListener;

    private CommunityM3(@NotNull StackPane host) {
        this.host = host;
        this.schemeListener = (observable, oldValue, newValue) -> applyTheme();
        this.fontListener = (observable, oldValue, newValue) -> applyFont();
        this.animationListener = (observable, oldValue, disabled) ->
                M3MotionSupport.setReducedMotion(Boolean.TRUE.equals(disabled));
    }

    /// 在 FX 线程安装；同一 host 只安装一次。
    public static @NotNull CommunityM3 install(@NotNull StackPane host) {
        Object existing = host.getProperties().get("lmcl.community.m3");
        if (existing instanceof CommunityM3 installed) {
            return installed;
        }
        CommunityM3 result = new CommunityM3(host);
        host.getProperties().put("lmcl.community.m3", result);
        host.getStyleClass().add("community-m3-host");
        host.setPickOnBounds(false);
        result.applyTheme();
        result.applyFont();
        result.applyMotion();
        Themes.colorSchemeProperty().addListener(result.schemeListener);
        FontManager.fontProperty().addListener(result.fontListener);
        settings().animationDisabledProperty().addListener(result.animationListener);
        return result;
    }

    private void applyTheme() {
        ColorScheme scheme = Themes.getColorScheme();
        if (scheme == null) {
            LOG.warning("No MonetFX color scheme; keep current M3 theme");
            return;
        }
        M3Theme theme = M3Theme.fromColorScheme(M3Profile.EXPRESSIVE_2025, scheme);
        M3ThemeManager.install(host, theme);
        // install 会追加 m3fx 样式；确保我们的覆盖样式在最后。
        String css = cssUrl();
        if (css != null) {
            host.getStylesheets().remove(css);
            host.getStylesheets().add(css);
        } else {
            LOG.warning("M3 community stylesheet is missing: " + CSS);
        }
    }

    private void applyFont() {
        FontManager.FontReference font = FontManager.getFont();
        String family = font != null ? font.family() : "System";
        host.setStyle("-fx-font-family: \"" + family + "\"; -fx-font-size: 12px;");
    }

    private void applyMotion() {
        M3MotionSupport.setReducedMotion(settings().animationDisabledProperty().get());
    }

    /// 释放监听器；页面销毁/重建时调用。
    public void dispose() {
        Themes.colorSchemeProperty().removeListener(schemeListener);
        FontManager.fontProperty().removeListener(fontListener);
        settings().animationDisabledProperty().removeListener(animationListener);
    }
}
```

注意：

- 已核对：`LauncherSettings` 只有 `animationDisabledProperty()`，没有 `isAnimationDisabled()`，
  因此必须用 `settings().animationDisabledProperty().get()`。
- m3fx 文档说明：generated parent stylesheet 在 application-owned stylesheet 之前，
  因此 `install` 之后追加的 `m3-community.css` 会覆盖默认 token/控件样式；
  阶段 0 仍要用 CSS 探针确认顺序，若被清空则 `Platform.runLater` 重新挂载。
- `M3MotionSupport` 是本文建议的薄封装（§4.5），不要让业务代码直接散落 m3fx 调用。

### 4.4 视觉 token 与 CSS

#### 4.4.1 颜色角色映射

| 语义 | M3 token | 用途 |
|---|---|---|
| 窗口背景 | `surface` / `surface-container-lowest` | 壁纸不可用时的兜底 |
| 普通卡片 | `surface-container-low` | 主页卡片、列表容器 |
| 浮起卡片 | `surface-container` | 对话框、菜单 |
| 高浮起 | `surface-container-high` | hover/选中 |
| 主色 | `primary` | 主按钮、激活态 |
| 主色容器 | `primary-container` | 激活背景、成功态 |
| 次色容器 | `secondary-container` | 提示/信息 |
| 第三色容器 | `tertiary-container` | 警告 |
| 错误容器 | `error-container` | 错误 |
| 正文 | `on-surface` | 主文字 |
| 次正文 | `on-surface-variant` | 说明、时间 |
| 描边 | `outline-variant` | 分隔线、卡片边 |
| 反色 | `inverse-surface` / `inverse-on-surface` | Snackbar |

实现方式：

- 新增 `assets/css/m3-community.css`，**不修改** `root.css`/`community.css`；
- 旧类（`.card`、`.title-label`、`.jfx-button`）通过新增选择器覆盖，不动上游规则；
- 新组件使用 m3fx 自带样式；
- 颜色一律引用 `-m3-color-*` 或 `-monet-*`，禁止硬编码 hex（玻璃 tint 除外，见 §4.6.6）。

#### 4.4.2 字体层级

| 角色 | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| display-small | 36/44 | 400 | 主页问候语（可选） |
| headline-small | 24/32 | 400 | 页面标题 |
| title-large | 22/28 | 400 | 卡片大标题 |
| title-medium | 16/24 | 500 | 卡片标题 |
| body-large | 16/24 | 400 | 正文 |
| body-medium | 14/20 | 400 | 次要正文 |
| label-large | 14/20 | 500 | 按钮 |
| label-medium | 12/16 | 500 | 说明、时间 |

落地：

- 保留 HMCL 的字体族（`FontManager`），只改字号/字重/行高；
- 新增 `.community-m3-display`、`.community-m3-headline`、`.community-m3-title`、
  `.community-m3-body`、`.community-m3-label`；
- 旧的 `.community-headline/title/body/label` 保留，逐步映射，避免一次性替换；
- 中文行高比英文略大，统一用上表。

#### 4.4.3 形状/间距/高度

| token | 值 | 用途 |
|---|---|---|
| `shape-xs` | 4 | 标签、徽标 |
| `shape-sm` | 8 | 输入框、菜单项 |
| `shape-md` | 12 | 按钮、列表项 |
| `shape-lg` | 16 | 卡片 |
| `shape-xl` | 28 | 对话框、底部面板 |
| `shape-full` | 999 | 胶囊按钮、开关 |
| `space-1` | 4 | 图标与文字 |
| `space-2` | 8 | 控件内间距 |
| `space-3` | 12 | 卡片内间距 |
| `space-4` | 16 | 卡片间距 |
| `space-5` | 24 | 区块间距 |
| `control-height` | 40 | 标准按钮/输入框 |

主页保持现有 `GAP=12`/卡片槽位；不改 `HomePane.INFO_WIDTH=340`。

#### 4.4.4 状态层

- hover：`on-surface` 8% 叠加；
- pressed：12%；
- focus：2px `primary` 外环 + 2px 内缩；
- selected：`primary-container` 背景 + `on-primary-container` 文字；
- disabled：`on-surface` 38% 文字 + 12% 背景；
- 拖拽：`primary` 24% 背景 + 虚线描边。

CSS 变量：

```css
.community-m3-host {
    -community-state-hover: 0.08;
    -community-state-pressed: 0.12;
    -community-focus-width: 2px;
}
```

JavaFX CSS 不支持自定义属性？JavaFX 支持 `-fx-` 开头的自定义 looked-up colors，但不支持任意变量。
因此实际写法用具体颜色/透明度，不写 `-community-*` 变量；本文只表达语义。

### 4.5 动效桥接

#### 4.5.1 原则

- 使用 m3fx 的 `M3MotionScheme.expressive()` 与 `M3MotionSettings`；
- 不替换 HMCL 的 `TransitionPane`/`AnimationUtils`；
- 尊重“禁用动画”设置；`AnimationUtils.isAnimationEnabled()` 是类初始化快照，
  不能用它做实时判断，必须监听 `settings().animationDisabledProperty()`；
- 动效只用于“出现/消失/位置变化/状态变化”，不用于阻塞操作。

#### 4.5.2 `M3MotionSupport`

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.scene.Node;
import org.glavo.m3fx.animation.M3MotionSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// M3 动效的薄封装，统一 reduced-motion 语义。
@NotNullByDefault
public final class M3MotionSupport {
    private M3MotionSupport() {}

    public static void setReducedMotion(boolean reduced) {
        M3MotionSettings.setGlobalReducedMotionRequested(reduced);
    }

    public static boolean isReducedMotion(@NotNull Node node) {
        return M3MotionSettings.shouldReduceMotion(node);
    }
}
```

#### 4.5.3 应用点

| 场景 | 动效 | 时长 |
|---|---|---|
| 主页卡片进入 | fade + 8px 上移 | 200 ms |
| 侧栏激活指示 | 胶囊横向滑动 | 250 ms |
| 设置分组切换 | `M3AnimatedContent` | 220 ms |
| 对话框出现 | scale 0.92→1 + fade | 180 ms |
| 保存成功 | Snackbar 滑入 | 250 ms |
| 进度条 determinate | 连续波浪 | 持续 |
| 进度条 indeterminate | 移动段 + 波浪 | 持续 |

禁止：

- 页面切换时整页 3D 翻转；
- 卡片持续漂浮；
- 大于 300ms 的阻塞式过渡；
- 同时运行超过 3 个无限动画（性能）。

### 4.6 毛玻璃设计

#### 4.6.1 先明确一个事实

JavaFX **没有** CSS `backdrop-filter`。真正的“背景实时模糊”有三条路：

| 方案 | 原理 | 跨平台 | 性能 | 结论 |
|---|---|---|---|---|
| A. 壁纸快照 + 模糊 | 把窗口壁纸渲染成图，模糊一次，每个玻璃表面按位置取 viewport | 是 | 好（一次快照） | **采用** |
| B. 内容快照 + 模糊 | 截取表面后面的真实内容（卡片/滚动） | 是 | 差（每帧/每次滚动） | 否决 |
| C. Windows Acrylic/DWM | JNA 调 `DwmSetWindowAttribute`/`SetWindowCompositionAttribute` | 否（仅 Win） | 好 | 不做；可另立实验项 |

A 的视觉语义是“磨砂壁纸”，不是“磨砂卡片后的内容”。主页卡片后面就是壁纸，
所以视觉上等价；侧栏后面也是壁纸；滚动时壁纸不动，玻璃纹理稳定，反而更像磨砂玻璃。

#### 4.6.1a 两种候选的详细对比（用户要求分别介绍）

用户可在以下两种“毛玻璃”中选一种，视觉与实现代价完全不同：

| 维度 | A. 壁纸快照磨砂（本文推荐） | C. Windows Acrylic / DWM |
|---|---|---|
| 模糊对象 | 当前壁纸的静态快照；每个侧栏/卡片按窗口位置取一块 viewport | 窗口背后的桌面/其他窗口；由 Windows 合成器实时模糊 |
| 能否模糊“卡片后面的其他卡片/文字” | 不能；卡片后面是壁纸，不是被遮住的 UI | 也不能；Acrylic 只模糊窗口背后的内容，不模糊应用内兄弟节点 |
| 视觉效果 | 类似 macOS 控制中心：卡片是半透明磨砂板，颜色随壁纸变化，有细噪点/描边 | 类似 Windows 11 设置/开始菜单：整个窗口呈现系统亚克力，窗口内面板透明处透出模糊桌面 |
| 平台 | Windows/Linux/macOS 全平台 | 仅 Windows 10 1803+ / Windows 11；macOS/Linux 无 |
| 实现 | 纯 JavaFX：`snapshot` + `BoxBlur` + `ImageView` viewport + tint | JNA/FFM 调 `SetWindowCompositionAttribute`（Win10）或 `DwmSetWindowAttribute(DWMWA_SYSTEMBACKDROP_TYPE)`（Win11） |
| 前提 | 窗口已有壁纸层；无需改 Stage 样式 | 透明 Stage、DWM 合成、窗口边框/阴影设置；Win10 与 Win11 API 不同 |
| 性能 | 壁纸变化时一次快照/模糊；滚动只改 viewport，稳定 | 系统合成，通常好；Win10 拖动/缩放时可能掉帧/发白 |
| 风险 | 快照内存（约等于一张屏幕图）、壁纸动画时需节流、不能模糊窗口后内容 | 未公开 API、杀软误报、透明窗口/阴影/圆角冲突、仅 Windows、无法自动化截图验收 |
| 回退 | `visual.glass=off` 直接半透明 surface | 失败/非 Windows 自动回退到 A 或纯色 |
| 工期 | 约 5–7 人日（含滚动/缩放/降级） | 约 7–10 人日（spike 3–5 + 集成/Windows 验收） |

**视觉上最直观的区别**：

- A 是“**应用自己画的磨砂玻璃**”。壁纸是彩色时，卡片会带壁纸的色；壁纸是纯色时，卡片只是轻微变亮/变暗。它不会因为卡片后面有另一张卡片而变模糊，因为采样源不是屏幕内容。
- C 是“**系统给窗口加的亚克力**”。窗口整体像 Win11 的亚克力面板；只有窗口透明的地方能看到模糊桌面。卡片如果保持不透明，C 对卡片没有影响；要让 C 透出来，卡片必须接近全透明，文字对比度和可读性会明显下降。
- 两者可以叠加（B 窗口 Acrylic + A 卡片磨砂），但视觉容易“糊上加糊”，且 Windows 10/11 表现不一致，不建议首版做。

**推荐与当前选择**：用 A 做侧栏 + 主页卡片；C 只作为 Windows 专属“窗口亚克力”实验开关，不进入 MVP，不作为验收前置条件。用户 2026-10-22 已选 A（暂定），因此首版按 A 实现，但 `GlassSurface` 的重建接口保留，后续可插入 C 的窗口层。


#### 4.6.2 玻璃层结构

```
StackPane root
├── Region wallpaperLayer          // 绑定 Themes.backgroundProperty，铺满
└── M3Host
    └── HBox
        ├── SidebarGlassHost
        │   ├── GlassSurface       // 壁纸模糊 + tint + border
        │   └── CommunitySideBar   // 背景透明
        └── Navigator
            └── HomePane
                ├── GlassHeader (可选)
                └── ScrollPane
                    └── VBox column
                        └── GlassCard
                            ├── GlassSurface
                            └── DashboardWidget
```

#### 4.6.3 `GlassBackdrop` 服务

职责：

1. 监听 `Themes.backgroundProperty()`、窗口宽高、屏幕缩放；
2. 在 FX 线程对 `wallpaperLayer` 做一次 snapshot；
3. 用 `BoxBlur` 生成模糊图；
4. 缓存 `Image`；窗口尺寸/壁纸变化 150ms 防抖后重建；
5. 失败/禁用时返回 `null`，所有 `GlassSurface` 回退到 CSS tint。

代码骨架：

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 壁纸模糊背景的单例服务。
///
/// 只做“壁纸快照 + 模糊”，不截取卡片内容；FX 线程。
@NotNullByDefault
public final class GlassBackdrop {

    private static final double MAX_DIMENSION = 1920.0;
    private static final double BLUR_RADIUS = 18.0;
    private static final Duration DEBOUNCE = Duration.millis(150);

    private static final GlassBackdrop INSTANCE = new GlassBackdrop();
    public static @NotNull GlassBackdrop get() { return INSTANCE; }

    private final ObjectProperty<@Nullable Image> image = new SimpleObjectProperty<>();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private @Nullable Region source;
    private @Nullable ChangeListener<Object> sizeListener;
    private double scale = 1.0;
    private double originX;
    private double originY;

    private GlassBackdrop() {
        debounce.setOnFinished(event -> rebuild());
    }

    public @NotNull ObjectProperty<@Nullable Image> imageProperty() { return image; }
    public @Nullable Image getImage() { return image.get(); }

    /// 在 FX 线程绑定壁纸层；重复调用会解绑旧源。
    public void attach(@NotNull Region wallpaper) {
        detach();
        source = wallpaper;
        sizeListener = (observable, oldValue, newValue) -> schedule();
        wallpaper.widthProperty().addListener(sizeListener);
        wallpaper.heightProperty().addListener(sizeListener);
        schedule();
    }

    public void detach() {
        if (source != null && sizeListener != null) {
            source.widthProperty().removeListener(sizeListener);
            source.heightProperty().removeListener(sizeListener);
        }
        source = null;
        sizeListener = null;
        debounce.stop();
        image.set(null);
    }

    private void schedule() {
        debounce.playFromStart();
    }

    private void rebuild() {
        Region region = source;
        if (region == null || region.getWidth() <= 0 || region.getHeight() <= 0) {
            return;
        }
        if (!GlassSettings.isEnabled()) {
            image.set(null);
            return;
        }
        try {
            double max = Math.max(region.getWidth(), region.getHeight());
            scale = Math.min(1.0, MAX_DIMENSION / max);
            var origin = region.localToScene(region.getLayoutBounds());
            originX = origin.getMinX();
            originY = origin.getMinY();
            int w = Math.max(1, (int) Math.round(region.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(region.getHeight() * scale));

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage raw = new WritableImage(w, h);
            region.snapshot(params, raw);

            ImageView blurSource = new ImageView(raw);
            blurSource.setEffect(new BoxBlur(BLUR_RADIUS * scale, BLUR_RADIUS * scale, 2));

            SnapshotParameters blurredParams = new SnapshotParameters();
            blurredParams.setFill(Color.TRANSPARENT);
            WritableImage blurred = new WritableImage(w, h);
            blurSource.snapshot(blurredParams, blurred);

            image.set(blurred);
            LOG.debug("Glass backdrop rebuilt: " + w + "x" + h + ", scale=" + scale);
        } catch (Throwable e) {
            LOG.warning("Failed to build glass backdrop", e);
            image.set(null);
        }
    }

    /// 快照图相对窗口 scene 的缩放比。
    public double scale() { return scale; }

    /// 快照原点在 scene 中的坐标；GlassSurface 用它把 scene 坐标换成图片坐标。
    public double originX() { return originX; }
    public double originY() { return originY; }
}
```

实现注意：

- `region.snapshot` 要求节点已在 scene 中；在 `Platform.runLater` 后执行；
- `BoxBlur` 的半径按 `scale` 缩放，保持视觉一致；
- `WritableImage` 尺寸至少 1×1；
- 快照失败不能阻塞 UI，只记录 WARNING；
- 壁纸层不在 scene 原点时，必须用 `originX()/originY()` 减去偏移，否则玻璃取样错位；
- 壁纸切换/图片异步加载后需要重新触发 `rebuild()`；阶段 2 监听 `Themes.backgroundProperty()`，
  或给它加 `refresh()`，否则会一直用旧壁纸；
- 4K 屏最大边压到 1920，避免 33MB 大图；视觉上足够（模糊本来就会丢失细节）。

#### 4.6.4 `GlassSurface`

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 一块毛玻璃背景：模糊壁纸 viewport + tint + 圆角裁剪。
/// 只作为背景层，不承载内容；`mouseTransparent=true`。
@NotNullByDefault
public final class GlassSurface extends StackPane {

    private final ImageView background = new ImageView();
    private final Region tint = new Region();
    private final Rectangle clip = new Rectangle();
    private double radius = 20.0;

    public GlassSurface() {
        getStyleClass().add("community-glass");
        setMouseTransparent(true);
        background.setPreserveRatio(false);
        background.setSmooth(true);
        background.setVisible(false);

        tint.getStyleClass().add("community-glass-tint");
        tint.setMouseTransparent(true);

        getChildren().addAll(background, tint);
        setClip(clip);

        GlassBackdrop.get().imageProperty().addListener((observable, oldValue, newValue) -> render());
        widthProperty().addListener((observable, oldValue, newValue) -> {
            clip.setWidth(Math.max(0, getWidth()));
            render();
        });
        heightProperty().addListener((observable, oldValue, newValue) -> {
            clip.setHeight(Math.max(0, getHeight()));
            render();
        });
        // 滚动时 localToScene 改变但不会触发布局，必须监听变换。
        localToSceneTransformProperty().addListener((observable, oldValue, newValue) -> render());
        render();
    }

    public void setRadius(double radius) {
        this.radius = radius;
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        render();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        render();
    }

    private void render() {
        Image image = GlassBackdrop.get().getImage();
        if (image == null || getScene() == null || getWidth() <= 0 || getHeight() <= 0) {
            background.setVisible(false);
            return;
        }
        GlassBackdrop backdrop = GlassBackdrop.get();
        double scale = backdrop.scale();
        var sceneBounds = localToScene(getLayoutBounds());
        double x = (sceneBounds.getMinX() - backdrop.originX()) * scale;
        double y = (sceneBounds.getMinY() - backdrop.originY()) * scale;
        double w = sceneBounds.getWidth() * scale;
        double h = sceneBounds.getHeight() * scale;
        if (x < 0 || y < 0 || x + w > image.getWidth() + 0.5 || y + h > image.getHeight() + 0.5) {
            background.setVisible(false);
            return;
        }
        background.setViewport(new Rectangle2D(x, y, Math.max(1, w), Math.max(1, h)));
        background.setFitWidth(getWidth());
        background.setFitHeight(getHeight());
        background.setVisible(true);
    }
}
```

实现注意：

- `render()` 在每一次 layout 都调用，但只做 viewport 计算，开销很低；
- `localToScene` 必须在节点已挂到 scene 后调用；未挂载时直接返回；
- 边界判断允许 0.5px 误差；
- 圆角裁剪用 `Rectangle`，不依赖 CSS `-fx-background-radius`；
- 如果性能测试发现 `render()` 太频繁，可改为只在 `boundsInParent`/scene 变换变化时调用，
  但第一版保持简单；滚动时 `localToSceneTransform` 已覆盖。

#### 4.6.5 `GlassCard` 包装

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 给任意卡片套一层毛玻璃背景；卡片自身背景改为透明。
@NotNullByDefault
public final class GlassCard extends StackPane {

    public GlassCard(@NotNull Node content) {
        getStyleClass().add("community-glass-card");
        GlassSurface surface = new GlassSurface();
        surface.setRadius(16);
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        getChildren().addAll(surface, content);
        setPickOnBounds(false);
    }
}
```

配合 CSS：

```css
.community-glass-card {
    -fx-background-color: transparent;
    -fx-padding: 0;
}
.community-glass-content.card {
    -fx-background-color: transparent;
    -fx-border-color: transparent;
}
```

注意：JavaFX CSS 支持 `>` 子选择器，但 `.community-glass-card > .card` 仍可能误伤
卡片内部嵌套的 `.card`；给内部 `DashboardWidget` 显式加 `community-glass-content` 类，
用 `.community-glass-content.card` 精确命中。

#### 4.6.6 tint 与对比度

```css
.community-m3-host.m3-root {
    -fx-background-color: transparent;
}
.community-glass-tint {
    -fx-background-color: -m3-color-surface-container-low;
    -fx-opacity: 0.78;
    -fx-border-color: -m3-color-outline-variant;
    -fx-border-width: 1;
    -fx-background-radius: 20;
    -fx-border-radius: 20;
}
```

- 浅色：opacity 0.78；深色：0.82（深色玻璃更容易脏）；
- 如果对比度测试不达标，提高到 0.86；
- 边框 1px `outline-variant`，让卡片在壁纸上仍有边界；
- 阴影：`-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 18, 0, 0, 6);`（可选，玻璃表面不宜重阴影）。

#### 4.6.7 侧栏玻璃

`DecoratorController` 里把 `sidebar` 包一层：

```java
CommunitySideBar sidebar = new CommunitySideBar();
GlassCard sidebarGlass = new GlassCard(sidebar);
sidebarGlass.setPrefWidth(CommunitySideBar.WIDTH);
sidebarGlass.setMinWidth(CommunitySideBar.WIDTH);
sidebarGlass.setMaxWidth(CommunitySideBar.WIDTH);
HBox shell = new HBox(sidebarGlass, navigator);
```

并给 `CommunitySideBar` 加 `community-sidebar-transparent` 类，CSS：

```css
.community-sidebar.community-sidebar-transparent {
    -fx-background-color: transparent;
    -fx-border-color: transparent;
}
```

侧栏玻璃圆角 0（贴边），只保留右侧 1px 分隔线；`GlassSurface.setRadius(0)`。
如果 64px 太窄导致模糊纹理不明显，可以降低 tint opacity 到 0.62，或加 8% 的
`primary` 色调（避免“灰玻璃”）。

#### 4.6.8 主页卡片

`HomePane.addWidget` 改为：

```java
private void addWidget(@NotNull VBox column, @NotNull String id, double minHeight) {
    if (!isEnabled(id)) {
        return;
    }
    DashboardWidget widget = registry.create(id, context);
    if (widget == null) {
        return;
    }
    widget.getStyleClass().add("community-glass-content");
    widget.setMinHeight(minHeight);
    widget.setPrefHeight(Region.USE_COMPUTED_SIZE);
    widget.setMaxHeight(Region.USE_PREF_SIZE);
    widget.setMaxWidth(Double.MAX_VALUE);

    GlassCard card = new GlassCard(widget);
    card.setMinHeight(minHeight);
    card.setPrefHeight(Region.USE_COMPUTED_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setMaxWidth(Double.MAX_VALUE);

    widgets.put(id, widget);
    column.getChildren().add(card);
    widget.initialize();
    widget.requestRefresh();
}
```

账户卡同理：`infoColumn.getChildren().add(new GlassCard(buildAccountCard()))`。

注意：

- `DashboardWidget` 原 `.card` 的 padding 由 CSS 提供，玻璃卡片要保留内边距；
  如果 `GlassCard` 没有 padding，内部 `.card` 的 padding 仍然生效，只是背景透明；
- `GlassCard` 的 `StackPane` 会按内容计算 prefHeight，`VBox` 的槽位高度仍由
  `minHeight` 控制；
- 内部卡片必须带 `community-glass-content` 类，才能命中透明背景规则；
- 不要写 `.community-glass-card .card` 或 `> .card`，避免误伤嵌套卡片；用显式类；
- 若 `GlassCard` 导致高度计算异常，退回“给 `DashboardWidget` 设置透明背景 +
  单独 `GlassSurface` 作为 `StackPane` 包裹”的同一方案，不改 9 卡布局。

#### 4.6.9 主页头部

`HomePane` 头部是 `HBox`；可选方案：

- 给头部包一层 `GlassCard`（圆角 16、tint 0.72），让问候语/运行状态/按钮浮在玻璃上；
- 或者保持透明，只给按钮 M3 样式。

推荐第一版只给头部加 **低透明度 tint + 无模糊**（性能更好），第二版再上模糊。
理由是头部高度小，模糊收益低，但 snapshot viewport 更新频率高。

#### 4.6.10 降级与开关

`GlassSettings`：

```java
public enum Mode { AUTO, ON, OFF }
```

- 默认 `AUTO`；
- `AUTO` 判断：
  - `Platform.isSupported(ConditionalFeature.EFFECT)`；
  - `System.getProperty("prism.order", "")` 不含 `sw`；
  - 窗口背景不是纯透明；
  - 屏幕缩放 ≤ 2.0（4K 200% 时 snapshot 成本高，可关）；
- 失败/关闭时：
  - `GlassBackdrop.image=null`；
  - `GlassSurface` 只显示 tint（半透明 surface，不模糊）；
  - 日志 INFO 一次，不刷屏。

开关落点：

- `community-settings.json` 新增 `visual` 段：
  ```json
  {"schema":1,"glass":"auto","m3":true,"wavy":true}
  ```
- 设置 → 外观 → 新增“视觉效果”分组（由 `PersonalizationPage` 已有第 12 处侵入内嵌，
  或 `HomeCustomizationPane` 扩展；不新增侵入）；
- 开发期可用 `-Dlmcl.visual.glass=off` 覆盖。

### 4.7 Wavy 进度设计

#### 4.7.1 参数

| 场景 | 组件 | 参数 |
|---|---|---|
| 下载/任务（determinate） | `M3ProgressBar` | `progress=0..1`、`waveAmplitude=2.5`、`wavelength=40`、`trackThickness=10`、`stopSize=4`、`trackGap=4` |
| 下载/任务（indeterminate） | `M3ProgressBar` | `progress=-1`、`indeterminateWavelength=20`、其余同上 |
| 对话框进度 | `M3ProgressBar` | 同上；宽度随对话框，高约 15（track 10 + 两侧振幅） |
| 加载圈 | `M3LoadingIndicator` | 默认 40px；小尺寸 24px 用 `scaleX/Y=0.6` |
| 按钮内加载 | `M3ProgressIndicator` | 16–20px，track 2，无波浪（避免小尺寸抖动） |

#### 4.7.2 工厂

```java
package org.jackhuang.hmcl.community.ui.m3;

import javafx.scene.control.ProgressIndicator;
import org.glavo.m3fx.controls.M3ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 统一的 LMCL 波浪进度条工厂。
@NotNullByDefault
public final class WavyProgress {
    private WavyProgress() {}

    public static @NotNull M3ProgressBar bar() {
        M3ProgressBar bar = new M3ProgressBar();
        bar.setWaveAmplitude(2.5);
        bar.setWavelength(40);
        bar.setIndeterminateWavelength(20);
        bar.setTrackThickness(10);
        bar.setTrackGap(4);
        bar.setStopSize(4);
        bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        return bar;
    }

    public static @NotNull M3ProgressBar determinate(double progress) {
        M3ProgressBar bar = bar();
        bar.setProgress(progress);
        return bar;
    }
}
```

> `M3ProgressBar` 的 setter 名称以实际 API 为准；如果只有 property，用
> `bar.waveAmplitudeProperty().set(2.5)`。

#### 4.7.3 替换 `TaskListPane`

`HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/TaskListPane.java`：

- 字段 `private final JFXProgressBar bar = new JFXProgressBar();`
  → `private final M3ProgressBar bar = WavyProgress.bar();`
- 保留 `bar.progressProperty()` 的绑定/解绑逻辑；
- `ProgressIndicator.INDETERMINATE_PROGRESS` 仍为 `-1`，语义一致；
- 样式：去掉 `.jfx-progress-bar` 相关覆盖，新增 `.community-wavy-progress`；
- 行高可能变化：`M3ProgressBar` 默认高度 4 + padding，需要给 task row 留 8px；
- 不要给每个 task 建一个 `M3ProgressBar` 之外的动画，`M3ProgressBar` 自带。

#### 4.7.4 替换 `DialogPane`

`HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/DialogPane.java:41`：

- 字段类型改 `M3ProgressBar`；
- `getProgressBar()` 返回类型改 `M3ProgressBar`；
- 检查所有调用方：`getProgressBar().setProgress(...)`/`bind(...)` 是否仍兼容；
- 如果调用方声明 `JFXProgressBar`，改为 `ProgressBar` 或 `M3ProgressBar`。

#### 4.7.5 替换 `SpinnerPane`

`SpinnerPane.Skin.updateContent()`：

```java
spinnerPane = new StackPane(new M3LoadingIndicator());
spinnerPane.getStyleClass().add("notice-pane");
```

- `M3LoadingIndicator` 用 `setContainerSize(24)`/`setIndicatorSize(16)` 控制尺寸，不依赖 CSS 类名；
  在 `SpinnerPane` 里按 `notice-pane` 可用高度选 24 或 32。
- 如果 m3fx 的 loading indicator 在 16px 以下不可读，退回 `M3ProgressIndicator`
  16px + `trackThickness=2`，或保留 `JFXSpinner` 仅用于按钮内（记录例外）。

#### 4.7.6 其他两处

- `ClassicAccountLoginDialog.java:58`：`JFXProgressBar` → `M3ProgressBar`；
- `TerracottaControllerPage.java:513`：`JFXProgressBar` → `M3ProgressBar`；
- 全仓 grep `JFXProgressBar` 应为 0（或仅保留在明确弃用注释中）。

#### 4.7.7 reduced motion

- `M3MotionSettings` 会处理 indeterminate 动画的 reduced motion；
- 仍保留一个静态的“移动段”，不能完全静止，否则用户不知道在加载；
- 禁用动画时，determinate 只更新数值，不做波浪相位动画。

### 4.8 组件替换策略

#### 4.8.1 替换原则

1. **叶子优先**：按钮、输入框、开关、进度、徽标、提示先换；
2. **容器后换**：卡片、Tab、对话框、导航最后换；
3. **行为不变**：原 `setOnAction`/binding/validation 原样保留；
4. **可回退**：每个替换点用工厂方法（`CommunityControls.button(...)`），
   出问题时改工厂即可回退，不必改业务代码；
5. **不替换复杂 skin**：`GameSettingsPage`、`GameListPage`、`VersionPage`、
   `ModListPage` 的私有 skin 不动，只改 CSS 和叶子控件。

#### 4.8.2 工厂

新增 `CommunityControls`：

```java
public final class CommunityControls {
    public static M3Button filled(String text, Runnable action) { ... }
    public static M3Button tonal(String text, Runnable action) { ... }
    public static M3Button text(String text, Runnable action) { ... }
    public static M3Button outlined(String text, Runnable action) { ... }
    public static M3IconButton icon(SVG icon, String tooltipKey, Runnable action) { ... }
}
```

这样可以：

- 统一 i18n/tooltip；
- 统一 disabled/焦点；
- 未来换库只改工厂。

#### 4.8.3 不替换清单

| 控件/页面 | 原因 |
|---|---|
| `TabHeader` + `TransitionPane` | HMCL 页面切换核心，替换会影响导航/动画 |
| `GameListPage`/`VersionPage` 的私有 skin | 复杂虚拟化/拖放/右键菜单 |
| `ModListPage`/`WorldListPage` 的表格 | 列宽/排序/多选行为 |
| `JFXCheckBox`/`JFXRadioButton`（已有设置） | 继承/三态/只读语义；只改 CSS |
| `JFXTextField`（表单校验） | 校验/提示/焦点逻辑；优先 CSS，逐步换 |
| `Decorator` 的窗口按钮 | 自绘标题栏，替换风险高；只改视觉 |
| `CodeArea`/`NBTEditor` | 第三方控件，保持 |

### 4.9 降级矩阵

| 条件 | 行为 |
|---|---|
| m3fx 初始化失败 | 记录 ERROR；不安装主题；保留 v3.15 视觉；`visual.m3` 置 false |
| 玻璃快照失败 | tint-only；日志 WARNING 一次 |
| 软件渲染 | 玻璃自动关闭；M3 主题仍可用 |
| 4K/200% 缩放 | 玻璃默认关（auto），用户可强制开 |
| 禁用动画 | M3 reduced motion；无无限动画 |
| 高对比度 | 关闭玻璃，使用不透明 surface + 2px 描边 |
| 自定义字体缺失 | fallback `System`；不阻塞 |
| JitPack 不可用（仅阶段 0） | 暂停阶段 0 合并；改用 vendor 源码 |

### 4.10 其他 M3 Expressive 特征采用清单

“Expressive”不等于“大圆角 + 波浪”，它同时包含**更强的色彩表达、更大的形状尺度、
更有弹性的动效、更明确的层级**。下表按优先级列出本方案采用的 m3fx 能力；
P0 必做，P1 尽量做，P2 只在成本低且不影响行为时做。

| 优先级 | 特征 | m3fx 组件/API | 落点 | 阶段 | 风险/备注 |
|---|---|---|---|---|---|
| P0 | Expressive 色板 | `M3Profile.EXPRESSIVE_2025` + `M3Theme.fromColorScheme` | 全局 | 1 | 与 HMCL 主题色/亮度同步 |
| P0 | 大形状尺度 | `M3ShapeTokens` / CSS 圆角 | 卡片 16、对话框 28、按钮 full | 1 | 不能把 64px 侧栏按钮做成过大 |
| P0 | 层级字体 | `M3TextRole` + `M3Text` | 新面板/主页标题 | 1/3 | 旧标签用 CSS 映射，不强制换控件 |
| P0 | Wavy 线性进度 | `M3ProgressBar` | 下载/任务/对话框 | 4 | 见 §6.2 侵入决策 |
| P0 | Expressive 加载 | `M3LoadingIndicator` / `M3ProgressIndicator` | SpinnerPane、新面板 | 4 | 小尺寸需调 containerSize |
| P0 | 弹性空间动效 | `M3MotionScheme.expressive()`、`M3StateTransition` | 侧栏激活、卡片出现、面板展开 | 2/3 | 尊重 reduced motion |
| P1 | 激活指示器 | `M3NavigationRail` 的视觉（不换控件） | 64px 侧栏 | 2 | 自绘胶囊，不引入 96px rail |
| P1 | FAB / FAB 菜单 | `M3FloatingActionButton`、`M3FabMenu` | 主页右下启动/新建（可选） | 3 | 与现有 `launchPane` 位置/行为冲突，默认只换视觉 |
| P1 | 分段按钮 | `M3SegmentedButton`、`M3SegmentedButtonGroup` | 列表/网格切换、下载源排序 | 5/6 | 保留原 toggle 逻辑 |
| P1 | 徽标 | `M3Badge`、`M3BadgedBox` | 崩溃红点、未读、更新提示 | 3/5 | 不用颜色单独表达状态 |
| P1 | 顶栏/工具栏 | `M3TopAppBar`、`M3Toolbar` | 新管理/其他页的标题区 | 5 | 不替换 HMCL `Decorator` 标题栏 |
| P1 | 搜索 | `M3SearchBar`/`M3SearchView` | 实例/模组/世界列表 | 6 | 旧搜索框可先 CSS，后换控件 |
| P1 | 筛选 chip | `M3FilterChip`/`M3ChipGroup` | 版本类型/加载器筛选 | 6 | 保留多选/清空逻辑 |
| P1 | Snackbar | `M3Snackbar` + `M3OverlayPane` | 保存/导入结果 | 5/7 | HMCL 已有通知条，先复用位置 |
| P1 | 菜单 | `M3Menu`/`M3MenuItem` | 右键/更多菜单 | 7 | 键盘导航必须保留 |
| P2 | 侧边面板 | `M3SideSheet`/`M3BottomSheet` | 窄窗口/移动布局 | 7 | 当前无移动端，低优先 |
| P2 | 轮播 | `M3Carousel` | 新闻/推荐模组（若未来有） | 6 | 无现成内容源，暂不引入 |
| P2 | 表格 | `M3TableView` | 不替换现有 `TableView` | — | 列宽/排序/多选回归风险高 |
| P2 | 日期/时间选择 | `M3DatePicker`/`M3TimePicker` | 不替换现有日期控件 | — | 行为差异大，暂不引入 |
| P2 | 颜色选择 | `M3ColorPicker` | 主题自定义未来可评估 | — | HMCL 已有主题色选择器 |
| 避免 | 大面积 carousel/自动播放 | — | — | — | 启动器不是内容消费应用 |

#### 4.10.1 Expressive 形态的三个“度”

1. **圆角**：卡片 16、面板 24、对话框 28、按钮 full；不要所有东西都 28。
2. **动效**：出现 180–220ms、位置变化 250ms、弹簧只用于小元件；页面切换仍用 HMCL 的
   `TransitionPane`，不叠加第二套过渡。
3. **色彩**：主色只用于主动作/激活；大面积背景仍用 surface 系列；避免整页高饱和。

#### 4.10.2 与 HMCL 现有视觉的冲突点

| 冲突 | 处理 |
|---|---|
| HMCL 卡片圆角 4px | 主页/设置卡片提升到 16；列表 cell 保持 8 |
| JFoenix 按钮有阴影/波纹 | M3 按钮用状态层，不再叠双重阴影 |
| HMCL 标题栏是自绘 | 不换 `M3TopAppBar`，只改字体/按钮 |
| HMCL 侧栏 64px | 不换 `M3NavigationRail`，只借激活指示器语言 |
| 现有 `TabHeader` 横向 tab | 不换 `M3TabBar`，CSS 改选中指示条 |
| 现有 `JFXCheckBox` 三态 | 不换 `M3CheckBox`，CSS 改勾选/焦点 |

---

### 4.11 启动按钮两种形态

主页右下角不是普通按钮，而是 `MainPage` 里的 `launchPane`：

- `HBox`，固定 230×57，`StackPane.setAlignment(BOTTOM_RIGHT)`；
- 左侧 200×55 主按钮：两行文字（16px“启动游戏/停止”+12px 当前实例名），`-monet-primary-container`，圆角 4px；
- 右侧 27×55 下拉按钮：切换实例/新建实例菜单，分隔线 3px；
- 带滚轮切换实例、启动中/冷却/失败状态、当前实例监听。

两种形态的区别：

| 维度 | 形态 1：保留现有双段按钮，只做 M3 视觉化（推荐） | 形态 2：替换为 M3 FAB / Extended FAB |
|---|---|---|
| 结构 | 仍是“主按钮 + 下拉按钮”双段 pill | 单个 56dp 圆形 FAB，或 80–140×56 的 Extended FAB（图标 + 文字） |
| 信息量 | 保留当前实例名、启动状态、冷却提示 | 圆形 FAB 只有图标；Extended FAB 只能放短标签，当前实例名通常放不下 |
| 切实例/新建 | 保留右侧下拉箭头，滚轮切换也保留 | 需要另加 `M3FabMenu`/菜单按钮；滚轮切换要额外实现，或放弃 |
| 视觉权重 | 中：primary-container 色块，和卡片同一层级 | 高：FAB 是页面唯一强调色，容易压过 9 张卡片；Expressive 大 FAB 更明显 |
| 占用空间 | 230×57（现有） | 56×56 或约 140×56；圆形更省空间，但信息少 |
| 交互风险 | 低：只改 CSS/圆角/阴影/字体，不动 `launchPane` 逻辑 | 高：要重建启动/停止/冷却/实例切换/菜单，容易与 `Versions.launch`、08 钩子脱节 |
| M3 表达 | 可以做成“M3 形状的扩展按钮”：28px 圆角、tonal 色、elevation、状态层 | 最“Material”：FAB 是 M3 的标志性组件，Expressive 形态更强 |
| 适用 | 首版/MVP，功能优先、风险低 | 视觉演示/后续阶段 8 的可选增强 |

**结论**：形态 2 不是“换个控件”那么简单，它会改变主页唯一主操作的信息结构和交互路径。用户 2026-10-22 已选形态 1（暂定）：保留 `launchPane` 的位置/行为/双段结构，只把圆角、颜色、阴影、字体换成 M3 Expressive 令牌（必要时左侧加 20px 启动图标）；阶段 8 如果实机验收认为 FAB 更合适，再单独做“FAB 包装 + 隐藏上游按钮”的增强。

## 5. 分阶段计划

> 每个阶段都是“可独立合并的 PR”，但默认在 `feature/24-m3` 分支上推进；
> 阶段 0–4 完成即可发 MVP 测试包；阶段 5–8 继续全量覆盖。
>
> 每个阶段结束必须：
> 1. `:HMCL:compileJava` 通过；
> 2. `:HMCL:checkTranslations` 通过；
> 3. `:HMCL:test --tests 'org.jackhuang.hmcl.community.*'` 全绿；
> 4. 新增/更新文档；
> 5. Windows 截图/手工验收（至少阶段 0/2/3/4/8）。

### 阶段 0：技术 spike 与 Go/No-Go（3–5 人日）

#### 目标

验证 m3fx 能在 LMCL 构建/运行，确认主题、CSS、进度、字体不冲突；产出截图与结论。

#### 文件

- `gradle/libs.versions.toml`（临时 m3fx）
- `HMCL/build.gradle.kts`（临时依赖）
- 新增 `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3SpikePane.java`
- 新增 `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityM3.java`（骨架）
- 新增 `HMCL/src/main/resources/assets/css/m3-community.css`（空壳 + 覆盖规则）
- 入口：`HomePane` 头部临时按钮（`-Dlmcl.m3.spike=true` 时显示）

#### 任务

1. 加 JitPack 依赖，编译；
2. `M3SpikePane` 放：
   - `M3Button` 五种 variant；
   - `M3Card` 两种 variant；
   - `M3ProgressBar` determinate/indeterminate（Wavy 2.5 / Flat 0 对照）；
   - `M3ProgressIndicator`/`M3LoadingIndicator`；
   - `M3Switch`/`M3Slider`/`M3TextField`；
3. 用 `M3Theme.fromColorScheme(EXPRESSIVE_2025, Themes.getColorScheme())` 安装到 pane；
4. 检查 `.m3-root` 是否污染字体/背景；记录覆盖 CSS；
5. Windows 实机截图：浅色/深色、中文/英文、100%/150% 缩放；
6. 测启动时间、jar 大小、内存；
7. 写 `docs/审查记录` 的 spike 小节（或独立 `docs/修复方案-M3-spike结果.md`）。

#### 验收

- [ ] 编译通过；
- [ ] 5 类组件可见、可点击/可输入；
- [ ] 进度条波浪可见（Wavy 2.5 / Flat 0 对照、thickness 4 / gap 4 / stop 4 / wavelength 40 截图）；
- [ ] 现有主页/设置页无字体/背景突变；
- [ ] 启动增量 ≤ 300ms；
- [ ] 记录 JitPack fallback。

#### 风险与退出

- 如果 `M3ThemeManager` 与 HMCL 的 `StyleSheets` 无法共存：
  改为“只在 `M3SpikePane` 子树安装”，L1 用纯 CSS token 实现；
- 如果 JavaFX 21.0.8 与 m3fx 不兼容：暂停，评估 JavaFX 升级（高成本）或放弃 m3fx（只做 CSS）；
- 如果 JitPack 构建不可用：直接 vendor 源码，不阻塞。

#### 阶段 0 实施记录（2026-10-22）

已落地（分支 `feature/24-m3`，基于 `7dfac239f`）：

| 项 | 实际值 |
|---|---|
| 依赖坐标 | `com.github.Glavo:m3fx:2481f88fd6`（JitPack）；根构建已含 `https://jitpack.io` 仓库 |
| Gradle 声明 | `gradle/libs.versions.toml` 的 `m3fx`；`HMCL/build.gradle.kts` 的 `implementation(libs.m3fx)` |
| 主题桥接 | `community/ui/m3/CommunityM3.java`：`M3Theme.fromColorScheme(M3Profile.EXPRESSIVE_2025, Themes.getColorScheme())` + `M3ThemeManager.install(root, theme)`，只装传入子树；追加 `assets/css/m3-community.css`；仅当 `FontManager` 解析到字体时覆盖 `font-family`，否则继承组件字体 |
| Spike 面板 | `community/ui/m3/M3SpikePane.java`：5 种 `M3ButtonVariant`、3 种 `M3CardVariant`、2 条确定进度（Wavy 65% amplitude 2.5 / Flat 65% amplitude 0，thickness 4 / gap 4 / stop 4 / wavelength 40）+ 1 条连续自绘不确定波形、`M3LoadingIndicator` + `M3ProgressIndicator`、`M3Switch`/`M3Slider`/`M3TextField` |
| 覆盖 CSS | `HMCL/src/main/resources/assets/css/m3-community.css`：`.m3-root` 透明背景、状态文字色 |
| 调试入口 | `HomePane.buildHeader()`：仅 `-Dlmcl.m3.spike=true` 时显示 `[M3 Spike]`，点击弹 `DialogPane`（`ScrollPane`，640 高） |
| 第三方许可 | `META-INF/LICENSE-m3fx.txt`（Apache-2.0 全文 + 来源/commit）；包内另附 `THIRD-PARTY-NOTICES.txt` |

验证：

- `:HMCL:compileJava` **BUILD SUCCESSFUL**，m3fx JitPack 解析成功，仅 3 条既有 JavaFX `--add-exports` 警告；
- `:HMCL:test --tests 'org.jackhuang.hmcl.community.*'` **153 / 0 failures / 0 errors / 0 skipped**；全量 `:HMCL:test` **263 / 0 failures / 0 errors / 2 skipped**；`:HMCL:checkTranslations` 通过；
- `:HMCL:makeExecutables` 成功：jar **11,785,366 B** SHA-256 `e0ed2f50ce1005f7fdb3b7e88f122215ddc54f7071180794b35f8b197a4f0c04`；exe **12,986,518 B** SHA-256 `82ce6ac847a175fc3eccfb8633a5036e5d6ced27c37a1216c55ac39b541199a5`；stub **1,201,152 B** `89b58fa0…`；`exe[:stub]==stub`、`exe[stub:]==jar`、PK 偏移 1,201,152；jar 含 376 个 `org/glavo/m3fx/` class；
- 相对 v3.15 的 jar `11,220,705 B`，增量 **+563,049 B**，低于 Go/No-Go 的 2 MB 门槛；
- 测试包 `dist/LMCL-3.16.1000-win-x64-m3spike5.zip` **66,010,554 B** SHA-256 `b4c19498666aa92ce0037b17b13f8c50a94d264e7a9b2bc56101b5ac95abc730`；323 entries、`testzip=None`；含 `run-m3-spike.bat`、`THIRD-PARTY-NOTICES.txt`、`jre-x64/bin/java.exe`；
- 本机 `:HMCL:run` 无法完成 GUI 启动：`/home/kerze/.local/share/LMCL: Read-only file system`，且 WSL2 无 X11（`AWTError: Can't connect to X11 window server using ':0'`）。这是运行环境限制，不是编译/依赖错误；
- 未生成 Windows 截图，未测启动增量/内存；Windows 实机验收由 `m3spike5` 测试包完成；
- 代码提交 `73e3438ba feat(24): M3 Expressive phase 0 spike via m3fx`，文档提交 `da4b94bb4 docs: v3.16 M3 Expressive phase 0 spike`（分支 `feature/24-m3`）。

修复（2026-10-22，m3spike2）：

- 现场：用户点击 `[M3 Spike]` 弹出错误框，`NullPointerException: Cannot invoke "org.jackhuang.hmcl.setting.FontManager$FontReference.family()" because "font" is null`，位置 `CommunityM3.java:76`；Windows 11 + Java 17.0.20.1。
- 根因：`FontManager.getFont()` 并非保证非 null；当用户没有自定义字体且默认字体探测失败时返回 null，旧代码无条件 `font.family()`。
- 修复：抽出 `CommunityM3.fontStyle(@Nullable FontReference)`；null 时返回 null，`install` 不写 inline font 样式，继续继承组件字体；有字体时才设置并转义引号/反斜杠；`CommunityM3` 还给子树加 `community-m3-host` 类。
- 回归：新增 `CommunityM3Test` 2 个测试（null 字体、字体名转义）；community **149/0**、全量 **259/0/2 skipped**。
- 新产物：jar **11,783,754 B**（`39f25911…`）、exe **12,984,906 B**（`b2dece72…`）、`m3spike2` zip **66,266,200 B**（`dbd7d1b5…`）；代码提交 `f8cd1b9ec fix(24): avoid null FontManager font in M3 spike`；文档提交 `17f5fa6ff docs: v3.16.1 m3spike2 null-font fix`。

修复（2026-10-22，m3spike3）：

- 进度条观感：用户反馈 `[M3 Spike]` 中波浪进度“有点怪”。原阶段 0 用 thickness 4 + amplitude 0/2.5/4 三条对照，桌面端像细线且强波浪比例失衡；改为 Material 3 Expressive 规范尺寸 thickness 10 / gap 4 / stop 4 / wavelength 40，仅保留 Wavy 65%（amplitude 2.5）与 Flat 65%（amplitude 0）两条对照，移除振幅 4.0；`progressLine` 撑满宽度避免进度条只占半行。 **该 10 px 方案随后被用户判定过粗，m3spike4 已恢复到 4 px，见本节下方修复记录。**
- 内置插件端点：零内置只适用于 Minecraft 游戏本体/加载器/资源索引；Modrinth/CurseForge 的 API 与 CDN 属于协议端点，不能依赖 BMCLAPI 兼容镜像。新增 `CommunityAddonSources` 放行 4 个官方 HTTPS 主机（`api.modrinth.com`、`cdn.modrinth.com`、`api.curseforge.com`、`edge.forgecdn.net`），`MissingDownloadProvider` 仅对这些地址放行；其余 URL 继续抛缺源异常。
- 测试：新增 `CommunityAddonSourcesTest` 4 个测试（官方端点、相似域名/非 HTTPS 拒绝、Missing 放行、Minecraft URL 仍拒绝）；community **153/0**、全量 **263/0/2 skipped**。
- 新产物：jar **11,785,366 B**（`e0ed2f50…`）、exe **12,986,518 B**（`82ce6ac8…`）、`m3spike3` zip **66,003,199 B**（`07fca89c…`）；代码提交 `6ccf011c3 fix(24): tune wavy progress and keep addon endpoints built in`。`m3spike2` 包被取代。

修复（2026-10-22，m3spike4）：

- 线宽回归：用户反馈 `m3spike3` 的 10 px 线太粗，上一版 4 px 合适；`M3SpikePane.TRACK_THICKNESS` 恢复 4，gap 4 / stop 4 / wavelength 40 不变，Wavy 65% 与 Flat 65% 两条确定进度仍保留。
- 连续波形：m3fx `M3ProgressBarSkin` 的 indeterminate 动画从固定起点 `0.18` 循环，每轮在左侧露出约四分之一段，造成“跳回前面”。新增 `M3SpikePane.ContinuousWavyBar`：轨道宽度的周期位移（每轮恰好平移一整条轨道，首尾图案相同），活动段 0.58 轨道宽、间隔 1 个轨道宽，一段从左进入的同时另一段从右离开；`m3-community.css` 增加 `.community-wavy-spike .track/.wave` 的 m3fx 主题色规则。
- 验证：`:HMCL:compileJava` 通过；community **153/0**；全量 **263/0/2 skipped**；`:HMCL:checkTranslations` 通过。
- 新产物：jar **11,788,701 B** SHA-256 `6b1ffcee0c1098dd011cdd95141b7b3b33e114f95056bf4e9f51c55e190465ac`；exe **12,989,853 B** SHA-256 `dafe151cd8ebfcb0874b2260418ba754fcad8bad9970ea45a7e57205f678ebae`；stub 1,201,152 B `89b58fa0…`；`exe[:stub]==stub`、`exe[stub:]==jar`、PK 偏移 1,201,152。
- `m3spike4` zip **66,010,237 B** SHA-256 `c33b55b3cc1b0201b0d9e35a4cfe27c1bb29c289bd6b1563b1e28ff394a09711`；323 entries、`testzip=None`；代码提交 `539449b31 fix(24): continuous wavy indeterminate progress and 4px track`。`m3spike3` 包被取代。

修复（2026-10-22，m3spike5）：

- 现象：用户反馈 m3spike4 的不确定进度条“只有左右移动的感觉，没有上下波动”。检查 `ContinuousWavyBar.rebuild()`，`y = centerY + sin(2π * localX / WAVELENGTH) * AMPLITUDE` 只依赖段内 `localX`；每段波形形状固定，整段随 `offset` 平移，所以视觉上只有横向移动，没有波峰/波谷的上下运动。
- 修复：新增 `WAVE_CYCLES = 2`，`wavePhase = offset * 2 * Math.PI * WAVE_CYCLES`，纵坐标改为 `centerY + sin(2π * localX / WAVELENGTH + wavePhase) * AMPLITUDE`。每 2.2 秒一个循环内波形自身走 2 个完整周期，同时整段仍平移一个轨道宽；终点 `wavePhase = 4π`，与起点相位相同，因此循环处不跳变。
- 验证：`:HMCL:compileJava` / `:HMCL:checkTranslations` 通过；community **153/0**；全量 **263/0/2 skipped**。
- 新产物：jar **11,788,756 B** SHA-256 `c740ba6d1019c4937a17a34f74aad6883fd297a16d3da1ff498f12f9bc0ced03`；exe **12,989,908 B** SHA-256 `7cec96b11c1d93cad8a926bf8f4e115823562ce65e88227a8370a77df0a954bb`；stub 1,201,152 B `89b58fa0…`；`exe[:stub]==stub`、`exe[stub:]==jar`。
- `m3spike5` zip **66,010,554 B** SHA-256 `b4c19498666aa92ce0037b17b13f8c50a94d264e7a9b2bc56101b5ac95abc730`；323 entries、`testzip=None`；代码提交 `b456f2130 fix(24): animate wavy progress phase for vertical undulation`。`m3spike4` 包被取代。

打开方式：

1. 双击包内 `run-m3-spike.bat`（内部设置 `LMCL_JAVA_OPTS=-Dlmcl.m3.spike=true -Xmx1g` 后启动 `LMCL.exe`）；
2. 或手动设置环境变量后启动：`set LMCL_JAVA_OPTS=-Dlmcl.m3.spike=true`；
3. 进入主页，标题行出现 `[M3 Spike]`，点击打开面板。

偏差/未完成：

- 计划写“`M3Card` 两种 variant”，实际展示三种（ELEVATED/FILLED/OUTLINED），便于一次看全；
- 计划写 `CommunityM3` 骨架，实际已包含主题/字体/CSS 安装逻辑，阶段 1 可继续扩展；
- 阶段 0 验收第 2/3/5 项尚未完成（需 Windows）；
- JitPack 构建不是可复现的长期方案；阶段 1 仍需 vendor 源码并补完整 NOTICE。

### 阶段 1：M3 基座与全量 token（7–10 人日）

#### 目标

建立可持续的 M3 主题/样式/字体/动效基座；所有页面在 `visual.m3=on` 时获得统一 token，
但不改变控件行为。

#### 文件

- 新增 `community/ui/m3/`：
  - `CommunityM3.java`
  - `M3Host.java`
  - `M3MotionSupport.java`
  - `M3Settings.java`
  - `CommunityControls.java`
- 新增 `assets/css/m3-community.css`
- 修改 `DecoratorController.java`（第 7 处，壳层组装）
- 修改 `CommunityStyles.java`（可选：加载 m3 CSS）
- `community-settings.json` 新增 `visual` 段（`CommunitySettings` section）
- `PersonalizationPage`/`HomeCustomizationPane` 增加开关（不新增侵入）

#### 任务

1. vendor m3fx 源码，改为子工程；
2. 实现 `M3Host`/`CommunityM3`，安装到壳层；
3. CSS token 覆盖：
   - `.title-label`/`.subtitle-label` 字号/字重/颜色；
   - `.card` 圆角 16、背景 surface-container-low；
   - `.jfx-button` 圆角/高度/状态层；
   - `.jfx-text-field`/`.jfx-combo-box` 圆角/焦点环；
   - `.scroll-bar` 细轨道；
   - `.md-list-cell` hover/selected；
4. 字体同步：`FontManager.fontProperty` 监听；
5. 动效：`M3MotionSettings` 跟随 `animationDisabled`；
6. 开关：`visual.m3`（默认 on；开发期 off）；
7. 截图对比 v3.15，记录“有意变化/回归”。

#### 验收

- [ ] 所有页面能打开，无 CSS 解析错误；
- [ ] 字体族仍是用户设置；字号层级统一；
- [ ] 深色/浅色 token 正确；
- [ ] 禁用动画后无 M3 过渡；
- [ ] 现有测试全绿；
- [ ] `visual.m3=off` 回到 v3.15 视觉（截图对比）。

#### 风险

- CSS 覆盖优先级：`root.css` 的 `.card` 可能比我们的选择器更具体；
  解决：用 `.community-glass-content.card` 精确命中，不用宽泛的 `.community-m3-host .card`；
- 字体 `-fx-font-size` 继承：M3FX 组件可能固定字号；接受组件内部字号；
- 主题重装导致 stylesheet 重复：监听器里先 remove 再 add。

### 阶段 2：壳层、侧栏与毛玻璃（10–15 人日）

#### 目标

侧栏毛玻璃、窗口背景层、M3 侧栏视觉；玻璃开关与降级可用。

#### 文件

- 新增 `community/ui/m3/GlassBackdrop.java`
- 新增 `community/ui/m3/GlassSurface.java`
- 新增 `community/ui/m3/GlassCard.java`
- 新增 `community/ui/m3/GlassSettings.java`
- 修改 `CommunitySideBar.java`（透明背景、M3 激活指示）
- 修改 `DecoratorController.java`（`StackPane(wallpaper, M3Host)`）
- 修改 `Controllers.java`（仅在需要时；优先不改）
- `m3-community.css` 增加玻璃/侧栏规则
- i18n：`community.settings.visual.*`

#### 任务

1. 在 `DecoratorController` 建 `wallpaperLayer`，绑定 `Themes.backgroundProperty`；
   - `wallpaperLayer.setMouseTransparent(true)`；
   - 用**不透明**背景覆盖上游 `backgroundNode`，不要两层半透明叠加（否则壁纸变暗）；
     若 `LauncherBackground.opacity<1`，只在快照/玻璃 tint 中应用一次；
2. `GlassBackdrop.attach(wallpaperLayer)`；壁纸切换/图片异步加载后要重新 `rebuild()`，
   否则玻璃一直用旧图；
3. 侧栏包 `GlassCard`，背景透明；
4. 侧栏按钮 M3 化：
   - 44px → 48px 触达区（视觉 40px）；
   - active 胶囊 32×28，`primary-container`；
   - hover 状态层；
   - tooltip 保留现有；
5. 窗口背景：M3 表面兜底；保留壁纸；
6. 玻璃开关 UI：
   - 自动/开/关；
   - 说明“壁纸磨砂，不是实时模糊”；
7. 性能测试：1080p/1440p/4K、100%/150%/200%、滚动、resize 50 次；
8. 降级测试：软件渲染、透明壁纸、纯色壁纸。

#### 验收

- [ ] 侧栏玻璃可见，文字/图标对比度达标；
- [ ] 滚动主页时玻璃纹理稳定，无每帧重建；
- [ ] resize 后 150ms 内更新；
- [ ] 玻璃关闭时侧栏为不透明 surface；
- [ ] 启动增量 ≤ 300ms；
- [ ] 空闲 CPU 无持续快照。

### 阶段 3：主页 M3 Expressive 化（12–18 人日）

#### 目标

主页 9 卡 + 头部 + 账户卡统一 M3 视觉；卡片毛玻璃；按钮/徽标/日历/指标 M3 化；
不改数据/布局。

#### 文件

- `HomePane.java`
- `DashboardWidget.java`（只加 class/包装钩子）
- `BuiltinWidgets.java`（按钮/标题/对话框工厂）
- 各 widget：`LaunchWidget`/`PinnedInstancesWidget`/`CrashHistoryWidget`/
  `JavaRecommendWidget`/`SnapshotWidget`/`ActivityCalendarWidget`/
  `RecentWorldsWidget`/`HealthCheckWidget`/`NetworkWidget`
- `m3-community.css`
- i18n（新增标题/说明 key 按需）

#### 任务

1. 头部：
   - 问候语 `headline-small`；
   - 运行状态 `label-large` + 状态点；
   - [实例][下载][设置][更多] 换 `M3Button` TONAL/TEXT；
   - 账户 chip 保留原控件，只改表面；
2. 卡片：
   - `GlassCard` 包装；
   - 标题 `title-medium`，说明 `body-medium`；
   - 卡片内边距 12；
   - hover 只提亮 4%，不做位移；
3. 启动卡：
   - 主按钮 `M3Button` FILLED，高 40；
   - 状态 `M3Badge`/pill；
   - 最近启动时间用 `label-medium`；
4. 置顶卡：
   - 实例 tile 用 M3 卡片表面；
   - 选中态 `primary-container`；
5. 崩溃卡：
   - 红点用 `M3Badge`；
   - 原因文字 `body-medium`；
6. 日历：
   - cell 圆角 8；
   - level 0–4 用 `primary` 透明度阶梯；
   - 今天用 2px 描边；
7. 指标/内存/网络：
   - 数字 `headline-small`；
   - 说明 `label-medium`；
8. 滚动条：M3 细轨道；
9. 深色/浅色/玻璃 off 截图。

#### 验收

- [ ] 9 卡全部可交互；
- [ ] 无裁切/重叠（1280×720、1920×1080、2560×1440）；
- [ ] 卡片高度随内容增长；
- [ ] 玻璃表面文字对比度 ≥ 4.5:1；
- [ ] 启动/切换/置顶/回滚等原行为不变；
- [ ] 现有 147 个社区测试全绿。

### 阶段 4：Wavy 进度与加载（4–6 人日）

#### 目标

下载/任务/对话框/加载圈的 Wavy 进度；三处上游类替换后，仅登录/Terracotta
保留 `JFXProgressBar`（CSS 近似，记录为例外），`SpinnerPane` 不再用 `JFXSpinner`。

#### 文件

- 新增 `community/ui/m3/WavyProgress.java`
- 修改 `DialogPane.java`
- 修改 `TaskListPane.java`
- 修改 `SpinnerPane.java`
- **不修改** `ClassicAccountLoginDialog.java` / `TerracottaControllerPage.java`
  （保持 `JFXProgressBar` 字段，只用 CSS 近似；见 §6.2 的例外说明）
- `m3-community.css`
- 测试：`WavyProgressTest`（工厂参数、determinate/indeterminate）

#### 任务

1. `WavyProgress.bar()/determinate()`；
2. 逐个替换，保留 binding；
3. 调整行高/内边距，避免进度条被裁；
4. 小尺寸加载（按钮内）用 `M3ProgressIndicator`，不用 `M3ProgressBar`；
5. reduced motion 验证；
6. **独立对话框/弹出层**：m3fx 的 token 只装在 `M3Host` 子树；对话框若不在 host 下，
   先在对话框根安装 `CommunityM3`（或把阶段 7 的“对话框 host”提前），否则
   `M3ProgressBar` 取不到 `-m3-color-*`，会退回默认色；阶段 4 先覆盖壳层内对话框，
   场景级 overlay 与阶段 7 一起验收；
6. 截图：下载中、任务列表、SpinnerPane；登录/Terracotta 只验证不破版。

#### 验收

- [ ] 下载/任务进度是连续波浪；
- [ ] indeterminate 是移动波浪，不是静止；
- [ ] 对话框不因进度条变高而跳变；
- [ ] 禁用动画后仍能看出进度；
- [ ] 全仓 `JFXProgressBar` 只剩登录/Terracotta 两处（CSS 近似，有注释），其余为 0。

### 阶段 5：设置/管理/其他/下载入口（15–20 人日）

#### 目标

高频页面 M3 化：设置（游戏/设置/个性化/下载）、管理（账户/实例/Java）、
其他（内存优化/设置导入导出/帮助/反馈/关于）、下载页入口。

#### 文件

- `LauncherSettingsPage.java`（已有第 8 处，只改视觉/容器）
- `GameSettingsPage.java`（第 11 处，已改过内存项；本阶段只改 CSS/叶子控件）
- `DownloadSettingsPage.java`（第 4 处，已有社区编辑器）
- `PersonalizationPage.java`（第 12 处，已有主页自定义）
- `ManagementPage.java`、`AccountsPane.java`、`InstancesPane.java`
- `OtherPage.java`、`MemoryOptimizationView.java`、`SettingsTransferPane.java`、
  `HelpPage.java`、`FeedbackPage.java`、`AboutLMCLPage.java`
- `CommunityDownloadSettingsPane.java`（输入框/按钮/卡片）
- `m3-community.css`

#### 任务

1. 设置页：
   - 左侧 200px 菜单用 M3 导航样式（不改 `AdvancedListBox` 行为）；
   - Tab 标题/分组标题 `title-medium`；
   - 设置项卡片 `.card` 圆角 16；
   - 继承按钮/开关只改 CSS；
2. 管理页：
   - 账户/实例 tab 只改视觉；
   - Java 管理列表行高/选中态；
3. 其他页：
   - 内存优化面板：M3 卡片 + 开关 + 按钮；
   - 设置导入导出：拖入区 M3 虚线边框；
   - 关于页：产品卡/开发人员/致谢列表 M3；
4. 下载：
   - 下载源编辑器：`M3TextField`（URL）、`M3Button`（测试/删除/保存）；
   - 保留校验/自动保存/测速逻辑；
5. 对话框：
   - 只替换“新建”的对话框为 `M3DialogPane`；
   - 旧 `MessageDialogPane` 保留，CSS 改圆角/按钮；
6. 截图 + 行为回归。

#### 验收

- [ ] 设置所有项可改、可保存；
- [ ] 下载源导入/测速/自动保存仍工作；
- [ ] 管理页账户/实例/Java 可操作；
- [ ] 其他页四 tab 可切换；
- [ ] 未新增未经批准的上游侵入；
- [ ] i18n 三份同步。

### 阶段 6：游戏/下载/账户/模组页面（20–25 人日）

#### 目标

剩余复杂页面的 L1 视觉覆盖；列表/搜索/筛选按 M3 规范；不重写业务。

#### 文件

- `GameListPage`/`VersionPage`/`GameSettingsPage` 的 CSS
- `ModListPage`/`WorldListPage`/`ResourcePackListPage`/`ShaderPackListPage`/
  `DatapackListPage`
- `DownloadPage` 及各类下载页
- `CreateAccountPane`/`MicrosoftAccountLoginPane`/`AuthlibInjector`
- `m3-community.css`

#### 任务

1. 列表：行高 56/64、hover/selected、分隔线；
2. 搜索：`M3SearchBar` 用于新搜索框；旧搜索框 CSS；
3. 筛选：`M3FilterChip` 用于版本类型/加载器筛选（保留原逻辑）；
4. 下载卡：M3 卡片 + 进度 + 来源标签；
5. 账户：M3 输入框/按钮；OAuth 流程不变；
6. 模组/世界/资源包：表格只改视觉，不改列；
7. 截图 + 回归。

#### 验收

- [ ] 所有列表可滚动/选择/右键；
- [ ] 下载/安装流程不变；
- [ ] 账户登录流程不变；
- [ ] 无控件行为回归；
- [ ] 性能无明显下降。

### 阶段 7：对话框、弹窗与独立窗口（8–12 人日）

#### 目标

统一对话框/菜单/Tooltip/崩溃窗口/日志窗口的 M3 视觉；玻璃不进入独立窗口（或单独处理）。

#### 文件

- `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java`（第 9 处，慎改）
- `DialogPane.java`/`MessageDialogPane.java`/`InputDialogPane.java`
- `TaskExecutorDialogPane.java`/`ProgressDialog.java`
- `GameCrashWindow.java`/`LogWindow.java`
- `JFXPopup` 菜单相关
- `m3-community.css`

#### 任务

1. 对话框：
   - 圆角 28、surface-container-high、scrim 32%；
   - 按钮右对齐，主按钮 FILLED；
   - 标题 `headline-small`；
2. 菜单/弹出：
   - 圆角 12、阴影、选中态；
   - 保持键盘导航；
3. 独立窗口：
   - 崩溃/日志窗口单独安装 M3 主题；
   - 不启用玻璃（无壁纸）；
4. Tooltip：
   - 可选替换 `M3Tooltip`；先保留 `FXUtils.installFastTooltip`，避免全局焦点问题；
5. 截图。

#### 验收

- [ ] 所有对话框可关闭/确认/取消；
- [ ] 键盘 Tab/Esc 正常；
- [ ] 弹窗不超出屏幕；
- [ ] 崩溃/日志窗口可读；
- [ ] 独立窗口无玻璃/无透明背景问题。

### 阶段 8：无障碍、性能与收口（10–15 人日）

#### 目标

对比度、键盘、缩放、RTL、reduced motion、软件渲染、性能、文档、打包。

#### 任务

1. 对比度审计：用 `M3ColorAccessibility` + 手工检查；
2. 键盘审计：Tab 顺序、焦点环、Esc；
3. 缩放：100/125/150/200%，1280×720 到 4K；
4. RTL：至少不崩、文本不重叠；
5. 软件渲染：`-Dprism.order=sw` 启动，玻璃自动关；
6. 性能：
   - 冷启动 ≤ +300ms；
   - 主页 idle 60 FPS；
   - 滚动 50 次无内存增长；
   - 玻璃重建 ≤ 1 次/秒；
7. 清理：
   - 删除 spike 代码/临时 JitPack 仓库；
   - 删除未使用 CSS/类；
   - 更新 `NOTICE`/`LICENSE`；
8. 文档：
   - 00 §4.2（不新增侵入但记录新包）、§6 工期；
   - README 版本/changelog/包状态；
   - 新步骤 24 文档；
   - 审查记录；
   - docs-community 同步；
9. 打包：
   - `:HMCL:makeExecutables`；
   - zip + SHA256SUMS；
   - 附 `README-测试说明.txt`。

#### 验收

- [ ] 对比度全达标（或记录例外）；
- [ ] 键盘可完成主要流程；
- [ ] 玻璃/动画可关；
- [ ] 全量测试 0 失败；
- [ ] 包可启动；
- [ ] 文档 0 断链。

---

## 6. 文件与类清单

### 6.1 新增

| 路径 | 职责 | 阶段 |
|---|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityM3.java` | 主题安装/同步/释放 | 0/1 |
| `.../m3/M3Host.java` | 透明主题宿主 | 1 |
| `.../m3/M3MotionSupport.java` | reduced motion 封装 | 1 |
| `.../m3/M3Settings.java` | `visual` 段读写 | 1 |
| `.../m3/CommunityControls.java` | M3 控件工厂 | 1 |
| `.../m3/GlassBackdrop.java` | 壁纸快照/模糊/缓存 | 2 |
| `.../m3/GlassSurface.java` | 玻璃背景层 | 2 |
| `.../m3/GlassCard.java` | 玻璃卡片包装 | 2 |
| `.../m3/GlassSettings.java` | 玻璃开关/环境判断 | 2 |
| `.../m3/WavyProgress.java` | 波浪进度工厂 | 4 |
| `.../m3/M3SpikePane.java` | spike 临时页（阶段 8 删除） | 0 |
| `HMCL/src/main/resources/assets/css/m3-community.css` | M3 覆盖样式 | 0/1 |
| `lib/m3fx/**` | vendor 源码 | 1 |
| `HMCL/src/test/java/.../community/ui/m3/GlassMathTest.java` | viewport 数学 | 2 |
| `.../m3/WavyProgressTest.java` | 工厂参数 | 4 |
| `.../m3/M3SettingsTest.java` | 开关读写 | 1 |
| `docs/steps/24-Material3Expressive视觉重构.md` | 实现记录（阶段 8） | 8 |

### 6.2 修改（已有文件）

| 路径 | 修改 | 侵入 |
|---|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` | `StackPane(wallpaper, M3Host(HBox(sidebar,navigator)))`；侧栏玻璃包装 | 已有 #7 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java` | 透明背景/M3 激活态 | 社区文件 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java` | GlassCard 包装、头部 M3 | 社区文件 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunityStyles.java` | 可选加载 m3 CSS | 社区文件 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/construct/TaskListPane.java` | 进度条替换 | 上游文件，拟 #14（需批准） |
| `.../construct/DialogPane.java` | 进度条替换 | 上游文件，拟 #15（需批准） |
| `.../construct/SpinnerPane.java` | spinner 替换 | 上游文件，拟 #16（需批准） |
| `.../account/ClassicAccountLoginDialog.java` | 进度条替换（CSS 近似） | 不改字段，0 新增 |
| `.../terracotta/TerracottaControllerPage.java` | 进度条替换（CSS 近似） | 不改字段，0 新增 |

> **重要**：`TaskListPane`/`DialogPane`/`SpinnerPane`/`ClassicAccountLoginDialog`/
> `TerracottaControllerPage` 是上游文件。若直接改字段类型，会新增 **5 个代码侵入点**
> （#14–#18）；未批准时违反“固定 13 处”。本方案只申请前 3 个（#14–#16），
> 登录/Terracotta 两处保留 `JFXProgressBar`，用 CSS 近似。
>
> 处理方式（三选一，阶段 4 前必须定）：
>
> 1. **不改上游类**：在社区层用 CSS 给 `JFXProgressBar` 套 M3 皮肤（`-fx-shape`/动画），
>    但无法实现真正 Wavy；不满足 R5；
> 2. **批准新增 3 处侵入**：`TaskListPane`（任务/下载进度）、`DialogPane`（对话框进度）、
>    `SpinnerPane`（加载圈），登记为 #14/#15/#16，更新 00 §4.2、README、审查记录；
> 3. **子类/替换注入**：用 `TaskListPane` 的 public API 替换 bar？字段是 private，做不到。
>
> 推荐 **方案 2 的收敛版**：只改上述 3 处（覆盖任务/下载/对话框/加载四类主场景，
> 登记 #14–#16）；`ClassicAccountLoginDialog`/`TerracottaControllerPage` 保留
> `JFXProgressBar`，用 CSS 圆角/颜色近似，直到下一次侵入审批。
>
> 这需要用户明确批准“新增 3 处侵入”，否则阶段 4 只能做 CSS 近似，R5 不完整。
> 如果用户不接受任何新侵入，则把 R5 降级为“社区新建面板使用 Wavy，上游进度条只做 M3 圆角/配色”，
> 并在验收中明确标注。

### 6.3 删除/替换

| 文件 | 处理 |
|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunityStyles.java` 中重复逻辑 | 保留，增加 M3 CSS 加载 |
| `M3SpikePane.java` | 阶段 8 删除 |
| `JFXProgressBar` import | 替换后清理 |
| `JFXSpinner` import（SpinnerPane 如不改） | 保留并注明 |

---

## 7. i18n

新增 key 至少三份：`I18N.properties`、`I18N_zh.properties`、`I18N_zh_CN.properties`。

| key | 英文 | 中文 |
|---|---|---|
| `community.settings.visual.title` | Visual effects | 视觉效果 |
| `community.settings.visual.glass` | Frosted glass | 毛玻璃 |
| `community.settings.visual.glass.auto` | Auto | 自动 |
| `community.settings.visual.glass.on` | On | 开启 |
| `community.settings.visual.glass.off` | Off | 关闭 |
| `community.settings.visual.glass.hint` | Blurs the wallpaper behind panels; not live content blur. | 对面板后的壁纸做磨砂；不是实时内容模糊。 |
| `community.settings.visual.m3` | Material 3 Expressive | Material 3 Expressive |
| `community.settings.visual.wavy` | Wavy progress | 波浪进度 |
| `community.settings.visual.performance` | Reduce effects on low-end devices | 低配设备减少特效 |
| `community.visual.fallback.software` | Software rendering detected; glass disabled. | 检测到软件渲染，已关闭毛玻璃。 |
| `community.visual.fallback.no_wallpaper` | No wallpaper; using solid surface. | 无壁纸，使用纯色表面。 |

注意：

- `checkTranslations` 禁止中文全角括号，文案用 ASCII `()`；
- 不重复已有 key；
- 动态拼接 key（如 `community.m3.*`）要能被静态检查识别，尽量用完整字面量。

---

## 8. 测试与验收矩阵

### 8.1 自动化

| 测试 | 内容 | 阶段 |
|---|---|---|
| `M3SettingsTest` | `visual` 段默认值/损坏回退/往返 | 1 |
| `GlassMathTest` | viewport 边界、缩放、0 尺寸、超界 | 2 |
| `WavyProgressTest` | determinate/indeterminate、参数、reduced motion | 4 |
| `CommunityControlsTest` | 工厂 variant/禁用/action | 1 |
| 现有 `community.*` | 147+ 测试全绿 | 全程 |
| `:HMCL:checkTranslations` | 三份 key 完整/占位符 | 全程 |

### 8.2 手工视觉矩阵

每个阶段至少覆盖：

| 维度 | 值 |
|---|---|
| 主题 | 浅色 / 深色 / 跟随系统 |
| 主题色 | 默认紫 / 红 / 蓝 / 绿 / 自定义 |
| 字体 | 默认 / 自定义字体 / 大字号 |
| 缩放 | 100% / 125% / 150% / 200% |
| 分辨率 | 1280×720 / 1920×1080 / 2560×1440 / 3840×2160 |
| 壁纸 | 图片 / 纯色 / 透明（窗口透明） |
| 玻璃 | auto / on / off |
| 动画 | 开 / 关 |
| 语言 | 中文 / 英文 |
| 渲染 | 硬件 / `-Dprism.order=sw` |

### 8.3 回归清单

- [ ] 主页 9 卡全部显示；
- [ ] 启动游戏（含体检/快照/内存优化）；
- [ ] 下载源导入/测速/保存；
- [ ] 设置导入导出；
- [ ] 账户登录/切换；
- [ ] 实例新建/复制/删除；
- [ ] 模组/世界/资源包列表；
- [ ] 崩溃窗口/日志窗口；
- [ ] 更新提示（未配置仓库时静默）；
- [ ] 关闭动画/玻璃后不崩。

### 8.4 性能门槛

| 指标 | 目标 | 测量 |
|---|---|---|
| 冷启动 | ≤ v3.15 + 300ms | 3 次平均 |
| 主页 idle FPS | 60 | JavaFX pulse |
| 滚动 10 秒 | 无 >100ms 卡顿 | 手工/日志 |
| 玻璃重建 | ≤ 1 次/秒 | 日志计数 |
| 内存 | ≤ +30MB | 任务管理器 |
| jar | ≤ +2MB | 构建产物 |

---

## 9. 兼容性与可访问性

### 9.1 Java/JavaFX

- 运行 Java 17+；JavaFX 21.0.8；
- 不在 m3fx 中使用 Java 21 API；
- 如果未来升级 JavaFX，m3fx 源码可一起升级。

### 9.2 操作系统

| 平台 | 玻璃 | M3 | 说明 |
|---|---|---|---|
| Windows 10/11 | 快照模糊 | 完整 | 主目标 |
| Linux X11/Wayland | 快照模糊（性能降级） | 完整 | 无 DISPLAY 的 CI 不测 GUI |
| macOS | 快照模糊 | 完整 | 需实机验证 |
| 无 GPU/软件渲染 | 关闭 | 完整 | `prism.order=sw` |

### 9.3 可访问性

- 焦点环 2px，颜色 `primary`，对比度 ≥ 3:1；
- 所有图标按钮有 `accessibleText`/tooltip；
- 不用颜色单独表达状态（红点配文字/图标）；
- 玻璃表面文字对比度 ≥ 4.5:1；
- 支持 `M3MotionSettings` reduced motion；
- Tab 顺序不因包装层改变（`GlassCard` 不获取焦点）；
- 高对比度模式关闭玻璃。

### 9.4 RTL

- 侧栏/主页使用 `Pos`/`HBox` 方向，不硬编码左右；
- M3FX 组件支持 `NodeOrientation`；
- 第一版只保证不崩/不重叠，完整 RTL 排版放阶段 8。

---

## 10. 侵入登记与文档同步

### 10.1 代码侵入

- 本方案设计上 **0 新增**；
- 阶段 4 若坚持替换 `TaskListPane`/`DialogPane`/`SpinnerPane` 的进度条/加载，需要 **+3 处**（#14–#16）；
- 这 3 处必须：
  1. 写入 `docs/00-整体要求.md` §4.2 表；
  2. 更新 §7.2 固定数量、§8 DoD、§9 风险；
  3. README 第 111 行“侵入 13 处”改 16；
  4. 审查记录新增条目；
  5. 用户书面批准（对话即可，但要记录 ref）。

### 10.2 文档更新清单

| 文档 | 更新 |
|---|---|
| `docs/00-整体要求.md` | §4.2 包清单加 `community.ui.m3`；§6 工期；§13 决策 #30；§9 风险（m3fx/玻璃） |
| `README.md` | 版本 v4.0、步骤 24 行、包状态、changelog |
| `docs/steps/24-Material3Expressive视觉重构.md` | 新建，记录实现/验证/偏差 |
| `docs/步骤 21` | 侧栏/主页视觉引用 |
| `docs/步骤 19` | MD3 主题章节改为“由步骤 24 扩展” |
| `docs/附录/关键类速查` | 增加 `CommunityM3`/`GlassBackdrop`/`WavyProgress` |
| `docs/审查记录` | 新增 §42 |
| `lmcl/docs-community/` | rsync 同步 |

---

## 11. 排期

### 11.1 分阶段

| 阶段 | 内容 | 人日 | 累计 |
|---|---|---|---|
| 0 | spike/Go-No-Go | 3–5 | 0.6–1 周 |
| 1 | 基座/token | 7–10 | 2–3 周 |
| 2 | 壳/侧栏/玻璃 | 10–15 | 4–6 周 |
| 3 | 主页 | 12–18 | 6.5–9.5 周 |
| 4 | Wavy 进度 | 4–6 | 7.5–10.5 周（MVP） |
| 5 | 设置/管理/其他/下载 | 15–20 | 10.5–14.5 周 |
| 6 | 游戏/账户/模组 | 20–25 | 14.5–19.5 周 |
| 7 | 对话框/弹窗 | 8–12 | 16–21.5 周 |
| 8 | 无障碍/性能/收口 | 10–15 | 18–24.5 周 |

> 单人顺序执行约 **18–24.5 周**；若阶段 5/6 与功能开发并行、阶段 7 与 8 部分重叠，
> 可压到 **16–20 周**。MVP（0–4）约 **8–11 周**。
>
> 以上不含 Windows 实机等待、上游 rebase、用户验收往返。

### 11.2 里程碑

| 里程碑 | 内容 | 可交付 |
|---|---|---|
| M0 | spike 通过 | 截图 + 结论 |
| M1 | 基座 + 壳/玻璃 + 主页 + 进度 | **MVP 测试包**（m3-1） |
| M2 | 设置/管理/其他/下载 | 内测包（m3-2） |
| M3 | 游戏/账户/模组 + 对话框 | Beta 包（m3-3） |
| M4 | 无障碍/性能/文档 | 正式包（m3-final） |

---

## 12. 风险登记

| # | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | m3fx 未发布/JitPack 故障 | 高 | 中 | vendor 源码；阶段 0 fallback |
| R2 | m3fx API 变化 | 中 | 中 | 固定 commit；封装工厂 |
| R3 | CSS 冲突/字体污染 | 高 | 中 | 局部 host；高优先级覆盖；spike 验证 |
| R4 | 玻璃性能/内存 | 中 | 高 | 快照降采样；auto 关闭；性能门槛 |
| R5 | 玻璃不是真 Acrylic | 高 | 低 | 文档说明；可选 DWM 实验 |
| R6 | 控件语义差异 | 高 | 高 | 只换叶子；保留 handler；回归清单 |
| R7 | 全量替换工期爆炸 | 高 | 高 | 明确非目标；分阶段 |
| R8 | 新增上游侵入 | 中 | 高 | 阶段 4 前审批；优先 CSS 近似 |
| R9 | 无 Windows GUI 验证 | 高 | 中 | 用户实机验收；WSL 只做编译 |
| R10 | 上游 rebase 冲突 | 中 | 中 | 新文件为主；第 7/4/8 处改动小 |
| R11 | 许可证/NOTICE 遗漏 | 低 | 高 | vendor 时加 LICENSE/NOTICE；审查 |
| R12 | 对比度不达标 | 中 | 中 | 自动化 + 手工；tint 提高 |
| R13 | 动画引发眩晕 | 中 | 中 | reduced motion；关闭开关 |
| R14 | 4K/HiDPI snapshot 内存 | 中 | 中 | 最大边 1920；auto 关 |
| R15 | 独立 popup 未继承主题 | 高 | 低 | 阶段 7 单独安装 |

---

## 13. 回退

### 13.1 运行时开关

| 开关 | 值 | 效果 |
|---|---|---|
| `-Dlmcl.visual.m3=off` | off | 不安装 M3 主题；保留 v3.15 视觉 |
| `-Dlmcl.visual.glass=off` | off | 玻璃 tint-only 或不透明 |
| `-Dlmcl.visual.wavy=off` | off | 用普通 `ProgressBar`（如果保留回退工厂） |
| `community-settings.json` 的 `visual` | 同上 | UI 可改 |

### 13.2 代码回退

- 阶段 0–4 在 `feature/24-m3` 分支；
- 每阶段一个 merge commit，可单独 revert；
- `CommunityControls`/`WavyProgress`/`GlassCard` 是薄封装，业务代码不直接依赖 m3fx；
- 如果必须整体回退：删除 `community.ui.m3` 包 + `m3-community.css` + 依赖，
  恢复 `DecoratorController` 的 `HBox(sidebar, navigator)`，其余社区文件回到 v3.15。

### 13.3 包回退

- 保留 v3.15 `settings4` 稳定包；
- 新包命名 `m3spike1`/`m3-2`/...，不覆盖稳定包；
- 每次打包附 `README-测试说明.txt` 与 SHA256SUMS。

---

## 14. 决策状态

| # | 决策 | 状态（2026-10-22） | 说明 |
|---|---|---|---|
| D1 | 范围 | **已批准** | 全量视觉 + 重点组件；不做全量控件行为重写 |
| D2 | 毛玻璃 | **已选（暂定）：A 壁纸快照磨砂** | 用户 2026-10-22 选择 A；C 保留为后续 Windows 实验项 |
| D3 | 依赖 | **已批准** | 阶段 0 JitPack，阶段 1 起 vendor m3fx 源码 |
| D4 | 侧栏 | **已批准** | 保留 64px 自定义侧栏，M3 重绘 |
| D5 | 波浪强度 | 默认 2.5 | 用户未反对；实机验收可调 2.5/4.0 |
| D6 | 进度侵入 | **已批准** | +3：`TaskListPane`(#14)、`DialogPane`(#15)、`SpinnerPane`(#16)；阶段 4 落地后 00 改为 16 处 |
| D7 | 启动按钮 | **已选（暂定）：形态 1** | 保留双段按钮/滚轮/菜单，只做 M3 视觉；形态 2 留作后续增强 |
| D8 | 排期 | 未确认 | 暂按 MVP 8–11 周估算，不作为承诺；实机后按阶段报进度 |

D2/D7 已按“暂定”记录：首版采用 A 壁纸磨砂 + 形态 1，但保留 `visual.glass` 与按钮包装层的可替换结构，后续可切换 C/FAB。D8 未确认，暂按 8–11 周估算；D5 按 2.5 执行。

**阶段 0 spike 状态（2026-10-22）**：代码已落盘，编译/community 测试/构建通过，JitPack 依赖可用；Windows 实机视觉/启动增量尚未验证，Go/No-Go 未正式通过。测试包命名 `m3spike5`（m3spike1 因 null font NPE 作废；m3spike2 被进度条/内置插件端点修订取代；m3spike3 被 4 px/连续波形修订取代；m3spike4 被上下起伏相位修订取代）。

---

## 15. 关联文档

- [整体要求](00-整体要求.md) §4.2 侵入预算、§6 工期、§13 决策
- [步骤 19：MD3 主题完善](steps/19-MD3主题完善.md)
- [步骤 21：主页重构与侧边栏移除](steps/21-主页重构与侧边栏移除.md)
- [步骤 22：其他页面与关于 LMCL](steps/22-其他页面与关于LMCL.md)
- [步骤 23：管理页与启动内存优化](steps/23-管理页与启动内存优化.md)
- [步骤 07：下载源配置](steps/07-下载源配置.md)
- [审查记录](审查记录-v3.16.4源码核对.md)

---

## 16. 批准记录

| 日期 | 决策 | 记录 |
|---|---|---|
| 2026-10-22 | D1 范围 | 全量视觉 + 重点组件，不含全量控件行为重写 |
| 2026-10-22 | D3 依赖 | 阶段 0 JitPack，阶段 1 起 vendor m3fx 源码 |
| 2026-10-22 | D4 侧栏 | 保留 64px 自定义侧栏，M3 重绘 |
| 2026-10-22 | D6 进度侵入 | 批准 +3（#14–#16），待阶段 4 落地 |
| 2026-10-22 | D2 毛玻璃 | 暂定选 A 壁纸快照磨砂；保留后续 C 实验位 |
| 2026-10-22 | D7 启动按钮 | 暂定选形态 1 保留双段按钮只做 M3 视觉 |
| 待定 | D8 排期 | 暂按 MVP 8–11 周估算，待实机后确认 |
| 2026-10-22 | 阶段 0 spike | 代码/编译/构建完成；Windows 实机视觉验收待 `m3spike5` 测试包 |
| 待定 | 本文整体 | 已批准范围/依赖/侧栏/进度侵入/玻璃/按钮；D8 待确认 |

---

## 17. 2026-10-23 复启：1.0.0 现状盘点与执行顺序

> 本节是阶段 0 之后的重新基线。旧文 §5 的阶段划分保留作历史依据，但执行时以本节为准。
> 本节只更新方案，不修改代码。

### 17.1 现状盘点

| 层 | 现状 | 与 M3 目标的差距 |
|---|---|---|
| 壳层 | `DecoratorController` 已是 `HBox(CommunitySideBar, Navigator)`；`Decorator`/标题栏仍为上游实现 | 没有 M3 主题宿主；侧栏仍是 JFoenix 按钮 + 社区 CSS |
| 主题 | `CommunityM3.install(Parent)` 可安装 m3fx 主题；只有 `M3SpikePane` 调用 | 未覆盖真实页面；没有主题色/字体变化监听；没有持久化开关 |
| 样式 | `community.css` 162 行，主要是社区组件；`m3-community.css` 仅 3 条规则 | 上游 `root.css`/JFoenix 仍是主导；没有 M3 token 映射 |
| 主页 | `HomePane` 固定左信息栏 + 右实例栏，9 张卡；卡片来自上游 `.card` | 没有玻璃表面、M3 卡片/按钮/指标样式；卡片仍是 HMCL 视觉 |
| 管理/其他/设置/下载 | 200px `AdvancedListBox` + 上游 `SpinnerPane`/列表/表单 | 没有 M3 导航列表、表单、按钮、对话框视觉 |
| 进度/加载 | 生产仍用上游 `ProgressBar`/`SpinnerPane`；Wavy 只在 spike | 没有生产级 `LinearWavyProgressIndicator` 和加载指示器 |
| 依赖 | JitPack `com.github.Glavo:m3fx:2481f88fd6`；jar 约 11.87 MB | 未 vendor；发布仍依赖 JitPack；许可证文件仅在 jar 内 |
| 验证 | 编译/测试/构建通过；WSL2 无 GUI，Windows 实机截图未系统化 | 缺少逐切片的 Windows 视觉验收与回退包 |

### 17.2 对“完整重做”的界定

本轮按 D1 的既有批准执行：

- **做**：所有用户可见页面统一 M3 表面、字体层级、圆角、状态层、按钮/列表/表单/对话框视觉；
  侧栏、主页卡片、弹窗、任务/加载进度全部纳入；
- **不做**：不重写 243 个上游 UI 类的业务行为、键盘交互、虚拟化列表、拖放和继承设置逻辑；
- **不变**：9 卡主页布局、64px 侧栏宽度、现有导航结构、数据与 i18n 语义。

如果要求“每个控件的行为也换成 m3fx”，那不是本轮范围，需要另立控件迁移项目。

### 17.3 垂直切片（每个切片可打包验收）

| 切片 | 内容 | 预计 | 测试包 |
|---|---|---|---|
| **A. 基座** | `M3Host`/`M3Settings`/`M3MotionSupport`/`CommunityControls`；主题宿主接入壳层；token CSS；`visual.m3` 开关；字体/主题色监听 | 3–5 人日 | `m3expressive1` |
| **B. 壳层与玻璃** | 壁纸快照、`GlassBackdrop`/`GlassSurface`/`GlassCard`；侧栏玻璃与 M3 激活胶囊；标题栏/窗口背景兜底 | 4–6 人日 | `m3expressive2` |
| **C. 主页** | 头部、9 卡、按钮、指标、日历、空态/错误态统一 M3；卡片玻璃 | 4–6 人日 | `m3expressive3` |
| **D. 四个入口页** | 管理/其他/设置/下载：200px 导航、列表、表单、按钮、页头 | 5–7 人日 | `m3expressive4` |
| **E. 进度与加载** | 生产级 Wavy 线性/环形、`TaskListPane`/`SpinnerPane`/`DialogPane` 三处侵入 | 2–3 人日 | `m3expressive10+` |
| **F. 对话框与独立窗口** | `DialogPane`、`MessageDialogPane`、`JFXPopup`、独立 Stage 的主题安装 | 3–5 人日 | `m3expressive10+` |
| **G. 收口** | 无障碍、对比度、RTL、性能、4K/HiDPI、回归与文档 | 4–6 人日 | 候选发布包 |

每个切片结束必须：编译 + community 测试 + 全量测试 + `checkTranslations` + `makeExecutables` +
Windows 截图对比 + 更新本文件“实施记录” + 打独立测试包。未通过不进入下一个切片。

### 17.4 切片 A：M3 基座（先做这个）

#### 目标

让 M3 主题可以安全地覆盖整个 LMCL 壳层，但不改变任何页面布局和业务行为；默认关闭，
通过 `-Dlmcl.visual.m3=on` 或设置项打开，便于 A/B 对比和回退。

#### 文件

新增：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3Settings.java`
  - 读写 `CommunitySettings` 的 `visual` 段；
  - 字段：`enabled`（默认 `false`，开发期）、`glass`（`auto/on/off`）、`wavy`（默认 `true`）、`reducedMotion`；
  - 系统属性优先级：`-Dlmcl.visual.m3=on/off` > 设置文件 > 默认值；
  - 只做内存缓存 + 同步落盘，不引入新的配置文件。
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3Host.java`
  - `extends StackPane`；构造时 `CommunityM3.install(this)`；
  - 监听 `Themes.colorSchemeProperty()`、`FontManager.fontProperty()`、`M3Settings` 变化，重新安装主题；
  - 提供 `setContent(Node)`；`dispose()` 移除监听；
  - 不接管 `Scene`，只在自身子树安装主题。
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3MotionSupport.java`
  - 把上游 `animationDisabled` 与社区 `visual.reducedMotion` 映射到 `M3MotionSettings.setAnimationsEnabled(boolean)`；
  - 监听设置变化；只影响 m3fx 组件。
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityControls.java`
  - 薄封装：M3 主/次/文字按钮、卡片、列表项、表单行；
  - 业务代码不直接依赖 m3fx，后续切片统一通过这里创建。

修改：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityM3.java`
  - 抽出 `refresh(Parent)`，支持主题色/字体变化后重新安装；
  - `install` 保留幂等；允许重复 `refresh` 不重复追加 stylesheet；
  - 不再把 `INSTALLED_PROPERTY` 当作永久锁。
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java`（已有第 7 处侵入）
  - 把 `HBox shell` 包进 `M3Host`；`M3Settings.enabled()` 为 false 时直接放原 `shell`；
  - `M3Host` 的 content 为 `shell`，`HBox.setHgrow` 保持在 `navigator`；
  - 不改变 `decorator.getContent()` 的语义。
- `HMCL/src/main/resources/assets/css/m3-community.css`
  - 新增 `.lmcl-m3` 作用域下的 token 映射：`.title-label`、`.subtitle-label`、`.card`、`.jfx-button`、
    `.jfx-text-field`、`.jfx-combo-box`、`.scroll-bar`、`.md-list-cell`；
  - 颜色一律用 m3fx 的 `-m3-color-*` 变量，不写死 hex；
  - 不改上游 `root.css`。
- `HMCL/src/main/resources/assets/lang/I18N*.properties`
  - 新增 `community.settings.visual.m3`、`community.settings.visual.glass`、
    `community.settings.visual.wavy`、`community.settings.visual.reduced_motion` 及说明；
  - 仅在三份基准语言文件中写，其余语言回退英文。

#### 不做什么

- 不在 A 切片做玻璃、Wavy 进度、主页卡片重绘；
- 不删除 `M3SpikePane`（保留为组件样张）；
- 不改 `PersonalizationPage` 的上游布局；设置入口可临时放 `OtherPage` 的内存/视觉区，D 切片再收口；
- 不默认开启 M3，避免影响当前测试包。

#### 验收

> 状态（2026-10-23）：代码、测试、构建、打包已完成；所有需要 GUI 的验收项待 Windows 实机截图。

- [x] `visual.m3=off` 时 `DecoratorController` 直接放原 `shell`，不构造 `M3Host`（代码路径保证；实机截图待补）；
- [x] `visual.m3=on` 时 `M3Host` 安装主题，壳层子树可加载 `m3-community.css`（页面可打开待实机）；
- [x] 监听 `Themes.colorSchemeProperty()` 与 `FontManager.fontProperty()`，变化时 `refresh()`；
- [x] `M3MotionSupport` 监听 `SettingsManager.settings().animationDisabledProperty()` 与 `visual.reducedMotion`；
- [x] `CommunityM3.install` 幂等；`refresh` 先卸载再安装；`uninstall` 清理属性/样式类/stylesheet；
- [x] `M3SettingsTest` 4 个测试；community 165/0，全量 275/0/2 skipped，`checkTranslations` 通过；
- [x] `makeExecutables` 通过，生成 `m3expressive1` 包与 `run-m3-expressive.bat`/`run-classic.bat`；
- [ ] Windows 实机：off 与 1.0.0 截图一致、on 所有页面可打开、主题/字体/动画跟随、无布局回归。

#### 风险

- 上游 `root.css` 优先级高：所有覆盖必须带 `.lmcl-m3` 作用域，不用宽泛 `.card`；
- 主题重装导致样式表重复：`refresh` 前先移除旧 URL；
- `FontManager.getFont()` 可能为 null：沿用 `CommunityM3.fontStyle` 的空值保护；
- 全局主题可能影响 JFoenix 默认色：A 切片只改字体/表面/按钮圆角，不改控件结构；
- 无 Windows GUI：A 切片先由用户截图验收，不承诺“视觉完成”。

### 17.5 依赖与命名

- 切片 A–C 继续用 JitPack `com.github.Glavo:m3fx:2481f88fd6`，缩短迭代；
- 切片 D 前 vendor m3fx 源码到 `lmcl/m3fx/`（Apache-2.0，保留 LICENSE/NOTICE），
  移除 JitPack 依赖；vendor 提交单独一个 commit，不与视觉改动混在一起；
- 包名从 `m3spike*` 切换为 `m3expressive1..6`，稳定包 `home3`/`settings4` 保留不动；
- 所有新包必须带 `README-测试说明.txt`、`SHA256SUMS`、回退说明。

### 17.6 需要用户确认

| # | 问题 | 推荐 |
|---|---|---|
| Q1 | 是否按 A→G 垂直切片推进，每切片出包？ | 是；比一次性全量更容易定位回归 |
| Q2 | A 切片默认关闭、用启动参数预览，还是直接默认开启？ | 默认关闭；截图确认后再默认开启 |
| Q3 | D 切片前 vendor m3fx，还是继续 JitPack 到发布前？ | D 前 vendor；发布不依赖 JitPack |
| Q4 | 主页 9 卡布局是否保持不变？ | 保持；只换表面与组件视觉 |

> 结论：切片 A 已完成并出 `m3expressive1` 测试包；下一步由用户实机验收，
> 通过后再开始 B 的壳层毛玻璃；未经确认不开始 B。

---

## 18. 切片 A 实施记录（2026-10-23）

### 18.1 触发与范围

- User said (m18878): “这个先搁置，你要记下来，然后继续看重构UI这件事”。
- 品牌/Microsoft OAuth 项转入暂停，当前只推进 M3 视觉；切片 A 的目标是“主题可以安全覆盖壳层，
  但默认不改变 1.0.0 的布局和业务行为”。
- 没有做玻璃、Wavy 进度、主页卡片重绘、四页逐页收口；`M3SpikePane` 保留为组件样张。

### 18.2 新增与修改

| 文件 | 类型 | 内容 |
|---|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3Settings.java` | 新增 | `visual` 段读写；`enabled`（默认 false，兼容旧 `m3`）、`glass`、`wavy`（默认 true）、`reducedMotion`；系统属性优先；`parseBoolean` 供测试 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3Host.java` | 新增 | `StackPane` 宿主；`lmcl-m3-host`/`lmcl-m3`；安装主题、监听主题色/字体、`refresh`/`dispose` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/M3MotionSupport.java` | 新增 | 把 `animationDisabled` 与 `visual.reducedMotion` 桥接到 `M3MotionSettings.setAnimationsEnabled(boolean)` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityControls.java` | 新增 | `filledButton/tonalButton/outlinedButton/textButton`、`filledCard/outlinedCard`、`listItem`、`textField` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/m3/CommunityM3.java` | 修改 | `install` 幂等；新增 `refresh`/`uninstall`/`isInstalled`；`stylesheetUrl` 空资源保护；保留字体 null 保护 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` | 修改（第 7 处侵入内） | `M3Settings.isM3Enabled()` 为 true 时把 `HBox(sidebar,navigator)` 包进 `M3Host`，否则原样 |
| `HMCL/src/main/resources/assets/css/m3-community.css` | 修改 | `.lmcl-m3-host`/`.lmcl-m3` 作用域；标题/副标题、卡片、JFX 按钮、输入框、滚动条使用 `-m3-color-*`，无 hex |
| `HMCL/src/main/resources/assets/lang/I18N.properties` 等三份 | 修改 | 各新增 10 个 `community.settings.visual.*` key |
| `HMCL/src/test/java/org/jackhuang/hmcl/community/ui/m3/M3SettingsTest.java` | 新增 | 4 个测试：常见/未知布尔、系统属性覆盖、glass 规范化 |

### 18.3 行为说明

- 默认 `visual.enabled=false`，`DecoratorController` 不创建 `M3Host`；关闭路径与 1.0.0 相同。
- `-Dlmcl.visual.m3=on` 或设置 `visual.enabled=true` 时，只对 `M3Host` 子树安装 m3fx，不接管 `Scene`。
- `M3Host` 监听主题色与字体变化，调用 `CommunityM3.refresh`；`dispose` 时移除监听并卸载主题。
- `M3MotionSupport` 只在首次安装成功后注册监听，避免失败后永久锁死。
- `CommunityControls` 是后续切片的统一入口，业务代码不直接依赖 m3fx。
- 主题色/字体变化后 M3 子树跟随，不需要重启；上游“关闭动画”和社区 `reducedMotion` 任一为真即停用 m3fx 动画。

### 18.4 与方案的偏差

1. **未做设置 UI 入口**：方案允许 A 临时放 `OtherPage`，但本切片只落地系统属性/设置文件读写，
   没有新增设置页控件；`community.settings.visual.*` 已准备，D 切片或 B 收口时接入。
2. **CSS 作用域同时使用 `.lmcl-m3-host` 与 `.lmcl-m3`**：前者用于宿主，后者用于后续独立预览节点；
   两者都限定在社区子树，避免影响上游 `root.css`。
3. **新增 `CommunityControls`**：方案只要求“薄封装”，本切片把它作为 B–G 的统一构造入口，先落最小 API。
4. **`M3SpikePane` 保留**：只保留 `-Dlmcl.m3.spike=true` 的调试入口，不进入正式导航。

### 18.5 验证

- `:HMCL:compileJava`：BUILD SUCCESSFUL（仅 3 条既有 JavaFX `--add-exports` 警告）。
- `:HMCL:checkTranslations`：BUILD SUCCESSFUL。
- community 测试：**165 / 0 / 0 / 0**；全量测试：**275 / 0 / 0 / 2 skipped**。
- `:HMCL:makeExecutables`：成功；jar **11,881,236 B**，SHA-256
  `90505cd010605fa61425ddba09d5b57a8e610b0a88f0b8e35f7348ba2aef0e6d`；
  exe **13,082,388 B**，SHA-256
  `b1e1be17d8a09e56b3a86a05eaefd1e83fa3c9d295eed5b289a08ff5b9d2ca1e`；
  stub **1,201,152 B**，SHA-256
  `ded29e28971755afc83041f91dec9fce7ff4f4a130c0bb07983b4a42f8f3370b`。
- `exe[:len(stub)] == stub`、`exe[len(stub):] == jar`、jar 头 `PK\x03\x04` 均通过；jar 含 376 个
  `org/glavo/m3fx/` class。
- 测试包：`dist/LMCL-1.0.0-win-x64-m3expressive1.zip`，**74,915,628 B**，SHA-256
  `fa089d80972e91f0069661405f6d7280705a71c25acdf76fbd3a36cb20c588e8`；328 entries、
  `testzip=None`；包内 `SHA256SUMS` 327 个文件全过。
- 旧 `m3spike14` 包已删除；稳定包 `home3`/`settings4` 保留。

### 18.6 未验证与风险

- 开发环境 WSL2 无 Windows GUI，未在 Windows 实机点击；`run-m3-expressive.bat`/`run-classic.bat`
  的对照、主题色/字体跟随、动画停用、布局回归都待用户截图验收。
- m3fx 主题可能改变 JFoenix 控件的内边距/字体度量，尤其侧栏 64px 宽度和主页卡片高度；
  如果出现裁切，优先在 `.lmcl-m3` 作用域补最小覆盖，不改上游布局。
- A 切片默认关闭；在用户确认前不把默认值改为 true，也不开始 B 的毛玻璃。

### 18.7 下一步

1. 用户用 `m3expressive1` 包做 off/on 对照截图，确认默认关闭无回归、开启后页面可打开。
2. 根据截图修 A 的布局/CSS 问题，必要时补 `visual` 设置入口。
3. A 验收通过后再规划 B：64px 侧栏毛玻璃、主页头部/卡片玻璃、壁纸快照与降级。

---

## 19. 执行规范：从参考主提示词吸收的约束与工作法

> 本节依据用户 2026-10-24 提供的《HMCL × Material 3 Expressive 重构 —— 主提示词》
> （附件 `HMCL_M3E重构_AI提示词..md`）整理，并结合 LMCL 已批准的 D1–D7 与现有代码基线。
> 它**补充** §4/§5，不替代已批准的决策；冲突时以本文 §0.4/§14 的决策表和用户最新指令为准。

### 19.1 硬约束（违反即返工）

| # | 约束 | 在 LMCL 的落点 |
|---|---|---|
| H1 | 禁止引入 Jetpack Compose、Android、Web 前端 API | 只允许 JavaFX 21 + 现有 HMCL/m3fx API；不引入 WebView 方案 |
| H2 | 不改 `HMCLCore` 的下载、认证、游戏版本、任务框架逻辑 | M3 切片只改 `HMCL/src/main/java/org/jackhuang/hmcl/community/**`、CSS、资源；上游侵入只走已登记的 13 处 |
| H3 | 不修改既有 i18n key 的含义/拼写 | M3 重构阶段不重命名上游 key；只新增 `community.*` key，并跑 `:HMCL:checkTranslations` |
| H4 | 每个切片必须能过构建 | `:HMCL:compileJava`、`:HMCL:checkTranslations`、community 测试；切片合并前再跑全量测试 |
| H5 | 尊重 reduced motion；触控目标 ≥ 48dp（桌面按钮至少 36–40px 视觉 + 足够点击区） | 复用 `animationDisabled` 与 `M3Settings.reducedMotion`；图标按钮不缩小到不可点 |
| H6 | 颜色角色必须配对使用 | `primary/onPrimary`、`surface/onSurface`、`surfaceContainer/onSurface` 成对；玻璃表面文字对比度 ≥ 4.5:1 |
| H7 | GPLv3 + Apache-2.0 合规 | 保留 m3fx `LICENSE`/`NOTICE`/来源 commit；vendor 源码修改处标注修改人与日期；包内 `THIRD-PARTY-NOTICES.txt` 同步 |
| H8 | 一次只改一个切片/一个页面 | 每个 PR 的 diff 要能单独回退；不把“换主题”和“重写交互”混在一起 |

**与参考稿的差异**：参考稿写“i18n key 一个字符都不能动”；LMCL 的社区功能本来就有自己的
`community.*` key，因此本方案解释为“**不改上游既有 key**，社区新增 key 必须三份语言同步”。

### 19.2 JavaFX 能力缺口与我们的决策

参考稿列出的 M3E 能力缺口，逐项落到 LMCL 的实现策略：

| M3E 能力 | JavaFX 现实 | 本方案决策 | 明确不做 |
|---|---|---|---|
| Spring 物理动画 | 无刚度-阻尼模型；`Timeline` 打断会跳变 | 优先用 m3fx 的 motion API；只给 1–2 个 hero moment 做弹性；其余用 `Timeline` + `EASE_BOTH` | 不给所有 hover/展开都加 spring；不自研通用物理引擎 |
| Shape morphing（35 形状） | 无任意多边形顶点插值 | 只用圆角/胶囊/圆形三类 shape token；需要 shape 变化时用固定两态切换 | 不逐帧插值 35 种形状；不在首版做 SVGPath morph |
| 可变字体轴/连续字重 | `-fx-font-weight` 基本失效，Prism 降级为 bold 布尔 | 用字号、颜色、字距、留白建立层级；不伪造 350/450/550 字重 | 不引入可变字体渲染器；不承诺连续字重 |
| Ripple / state layer | CSS `:hover/:pressed` 可用；JFXRippler 可复用 | 复用现有 JFXRippler 或 CSS 状态层；只给主要按钮加 ripple | 不重写全部控件的手势系统 |
| 新组件族（ButtonGroup/SplitButton/FAB Menu/LoadingIndicator/Wavy…） | 没有原生实现 | 用 m3fx 已有组件；m3fx 没有的，用 HMCL 现有控件 + CSS 降级 | 不一次性自研整套 M3 组件库 |
| 动态色角色扩展 | MonetFX 已生成 `-monet-*`；缺 vibrant/harmonization/12 fixed accent/5 级 surface container | 在 `M3Host` 的主题桥接里补少量角色映射；缺的角色用最接近的 `-monet-*` 兜底 | 不引入第二套 HCT/CAM16 实现 |
| Reduced motion 系统开关 | JavaFX 无系统级查询 | 用 `visual.reducedMotion` + `animationDisabled` 两个显式开关；`M3MotionSupport` 统一桥接 | 不假装能读 Windows“减少动画”系统设置 |
| Haptics / Wear 圆屏 | 桌面端无硬件形态 | 直接砍掉 | 不投入预算 |

**结论**：把“形状 + 大色块 + 字号层级”作为主要表达手段；“弹簧动效”只用于 1–2 个 hero moment。
这条结论与参考稿的“重构重心排序”一致。

### 19.3 参考资源与使用边界

以下资源只用于**查 token/规范/交互**，不自动成为运行时依赖；引入代码前必须核对许可证：

| 资源 | 用途 | 许可证/边界 |
|---|---|---|
| `Glavo/m3fx` 与其 `hmcl-md3-demo` | 组件、主题、motion、HMCL 结构参考 | Apache-2.0；当前 JitPack，阶段 1 起 vendor 源码 |
| verve-tokens | 35 个 Expressive shape、12 个 spring token、HCT/CAM16 | MIT；只抄 token/算法，保留版权 |
| `@materialwebunofficial/md3e-web` | 36 组件规格、spring 阻尼参考 | 先核对仓库许可证；不直接复制运行时 |
| `@ibx34/m3x` | 闭式 spring solver 参考 | 先核对许可证；只参考公式 |
| MD3E frontend skill | `md3e-tokens.css` / `md3e-motion.css` / 组件规格 / 审计脚本 | 先核对许可证；不把 skill 当运行时依赖 |
| Compose Material3 Expressive Catalog | 权威视觉对照 | 只做截图对照，不移植 Compose 代码 |
| AtlantaFX | SASS 模块化 token、Sampler 热重载思路 | MIT；只参考组织方式 |
| JMetro | 零耦合、Skin API 增强现有控件的思路 | BSD-3；不复制整库 |
| `Redlnn/md3-HMCL-demo` | **明确排除** | 非自由软件；只看公开截图找灵感，不参考实现、不复制代码 |

**执行要求**：任何“参考实现”进入代码前，在 PR/文档里写清来源 URL、commit、许可证、
复制范围；不能确认许可证的，只写规格结论，不贴代码。

### 19.4 工作方法：token → 控件 → 页面 → hero

每个切片按同一顺序，不允许从页面硬编码颜色开始：

1. **token 层**：先确认需要哪些 color/type/shape/motion token；缺的补到 `m3-community.css`
   或 `CommunityM3` 的映射，不在页面里写 hex。
2. **基础控件**：只改该切片需要的按钮/输入/卡片/进度；用 `CommunityControls` 或 m3fx 工厂，
   不新造平行控件。
3. **页面**：把 token/控件装到页面；一次只做一个页面或一个页面组。
4. **hero 动效**：最后加，最多 1–2 个；其余保持安静。

补充纪律：

- 每个切片单独分支/提交，diff 小、可回滚；
- 不把“视觉替换”和“交互重写”放在同一个 PR；
- 先跑 demo/spike 看方向，再改正式页面；
- 遇到 API 不确定时，先写最小验证，不凭记忆编 API 名；
- 每屏最多 1–2 个 hero moment，禁止“到处都在弹”。

### 19.5 每个切片的交付物（强制）

参考稿要求每步四项，LMCL 再加两项工程项：

1. **token 变更**：改了哪些 token、为什么；没有就写“无”。
2. **代码 diff**：精确到文件；列新增/修改/删除。
3. **前后对比截图**：light/dark 各一组；Windows 实机；WSL2 无 GUI 时明确写“未验证”。
4. **不确定清单**：没把握的 API、平台限制、需要用户拍板的点；不知道就写不知道。
5. **验证记录**：编译、翻译检查、community/全量测试、`makeExecutables` 结果。
6. **回退方式**：关了哪个开关/还原哪些文件能回到上一版。

### 19.6 与现有 §5 阶段计划的关系

- §5 的阶段划分、文件清单、工期仍然有效；
- §19 补充“每步怎么做、做到什么程度、交付什么”；
- 如果 §5 的旧验收与 §19 冲突，以 §19 的硬约束和交付物为准；
- 阶段 0 的历史记录保留，不再重复“Go/No-Go 已通过”的表述；当前状态是“切片 A 已实现、默认关闭、Windows 实机待验收”。

### 19.7 当前基线校正（避免按旧数字执行）

| 项 | 旧方案写法 | 2026-10-24 实际 |
|---|---|---|
| 主页卡片高度 | `addWidget` 设固定 `minHeight` 槽位 | v3.17.1 已删除槽位表；高度内容驱动，footer 贴底 |
| 主页卡片数量 | 早期 10/11 卡 | 9 张组件卡 + 固定账户卡；memory/storage 已移除 |
| 当前测试包 | `m3expressive1` | `m3expressive9`（jar 11,968,995 B / zip 74,815,170 B） |
| 切片 A 状态 | 待 Windows 验收 | 仍待 Windows 实机验收；默认关闭，未改发布默认值 |
| 主页账户卡/日历 | 未涉及 | v3.17.2 已交付：账户卡 ▼ 下拉/200 ms 动画/新建·管理；日历固定 5×7 自然周；v3.17.3 右栏本日/本周统计 + 设置·统计页；v3.17.4 删除固定说明句，色阶随主题明暗反转；v3.17.5 问候语下改为每日一言；v3.17.6 本地源可分别启用；v3.17.7 每日一言设置收为单卡片 |

### 19.8 下一步建议

1. [主页账户卡与游玩日历改版](修复方案-主页账户卡与游玩日历改版.md)、[每日一言与主页顶栏改造](修复方案-每日一言与主页顶栏改造.md)与[每日一言设置层级调整](修复方案-每日一言设置层级调整.md)均已实施、验证并打包 `m3expressive9`，不并入 M3 B–G；
2. 用户先对 `m3expressive9` 做 Windows 实机验收（账户卡展开、日历 5×7、右栏统计、设置·统计跳转、明暗主题色阶、每日一言 10 分钟刷新/多源随机/静默回退、本地源开关、设置层级/在线源置灰、动画/DPI）；
3. M3 切片 B（侧栏/主页玻璃）继续等 Windows 对切片 A 的验收结论；
4. 每个 M3 切片开始前，按 §19.4 先补 token 与控件清单，再改页面。
