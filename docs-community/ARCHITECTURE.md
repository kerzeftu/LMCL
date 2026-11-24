# LMCL 架构说明（ARCHITECTURE）

> 项目已暂停主动维护。本页描述最后一轮 LMCL 实现（v3.17.35）的源码结构，不是后续开发承诺。定位时先辨认模块，再按需读代码。正文中出现的工作区绝对路径来自原开发环境，在独立克隆中请从仓库根目录查找。

---

## 1. 项目定位

LMCL（Lumina Minecraft Launcher）是 HMCL v3.16.4（release/3.16，`fbcc1747`）的社区分支，保留上游启动器能力，主页/管理/设置/“其他”页由 `org.jackhuang.hmcl.community.*` 接管，视觉主线是 **M3 Expressive + 实时壁纸磨砂（五层玻璃）+ 边缘渐隐**。

- 代码仓库：`lmcl/`（Gradle 多模块；`lmcl/.git`，无 `origin`）
- 文档与发布物料：工作区根 `docs/`、`README.md`、`dist/`、`.harness/`、`.tools/`
- 构建要求：JDK 17（`.tools/jdk-17.0.13+11`）、JavaFX 21.0.8（测试包随包携带）、Gradle 离线缓存 `.tools/gradle-home`
- 开发环境：WSL2，无 Windows GUI/X11；**任何“视觉已通过/已修复”的说法都必须在 Windows 实机后才有依据**

---

## 2. 目录与职责

| 路径 | 职责 |
| --- | --- |
| `lmcl/HMCL/` | 主应用模块（上游 HMCL 代码 + `org.jackhuang.hmcl.community.*` 社区代码） |
| `lmcl/m3fx/` | vendored M3FX 源码（commit `96ccd6d26fa8b38b7621c47332cfcc0caff034bf`，416 java + 63 resources） |
| `lmcl/AGENTS.md` | Java 代码风格硬约束（`///` Javadoc、`@Nullable` 显式化、`@Unmodifiable`） |
| `hmclauncher/` | 自建 Windows 启动壳（stub）源码/构建 |
| `docs/` | 项目文档：总纲、步骤、修复方案、审查记录、待办总览 |
| `lmcl/docs-community/` | 社区文档镜像，必须与根 `docs/` 同源、逐字节一致 |
| `.tools/` | 构建与打包工具链（JDK、Gradle 缓存、stub、win-jre、javafx-win、mingw32） |
| `.harness/` | PRD 与 Run 台账（`prd/`、`runs/`、`.lock`） |
| `structure.md` / `subagent.md` | 子代理编排的结构图与工作方式（每 Run 更新） |
| `dist/` | 发布包（当前只有 v3.17.35 补丁包；完整绿色包下次打包重建） |
| `README.md` | 版本发布说明与步骤总表（每版追加一段，含包哈希与测试数） |
| `USER-UNDERSTANDING.md` / `TROUBLESHOOTING.md` / `PUBLISHING.md` / `MAINTENANCE.md` / `ARCHITECTURE.md` | 本组“先查文档”入口，见 `DOCS.md` |

---

## 3. 构建与产物

- 模块：`:HMCL`（应用）、`:m3fx`（M3 组件）、上游依赖；`build.gradle.kts` 位于 `lmcl/HMCL/`。
- 产物：`LMCL-1.0.0-any.jar`（shadowJar，约 12.39 MB）+ `LMCL.exe`（自建 stub 1,317,390 B + jar 拼接）+ 可选 `jre-x64/` + `dependencies/windows-x86_64/openjfx/`。
- 关键构建约定：
  - `HMCL_LAUNCHER_EXE` 必须指向 `.tools/stub-build/HMCLauncher.exe`，否则 `LMCL.exe` 会沿用旧 HMCL 图标。
  - `--no-daemon` 串行执行；多个 Gradle 进程并行会踩 `in-progress-results-generic.bin` 竞态。
  - 打包用 Python `zipfile`（环境无 `zip`/`unzip` 二进制），`compresslevel=9`。
  - 包内 `SHA256SUMS` 逐行对应文件；`exe = stub + jar` 可用 `head -c`/`tail -c +` 分段校验。

---

## 4. 应用分层与启动链

```
MainPage (ui/main/MainPage.java)
├─ CommunitySideBar（左侧 64px 导航）
├─ HomePane (community/dashboard/builtin/HomePane.java)      ← 主页
│   ├─ 顶部导航/每日一言
│   └─ HBox：infoColumn（左列） + instanceColumn（右列）
│       └─ 每列：StackPane[ScrollPane, GlassEdgeFade(top), GlassEdgeFade(bottom)]
│           └─ 卡片：BuiltinWidgets.materialCard(DashboardWidget) = StackPane[GlassSurface, content]
├─ UpdatePane（右上更新气泡，默认隐藏）
└─ launchPane（右下 230×57 浮动启动按钮，toFront，pickOnBounds=false）
```

- 挂载：`CommunityDashboard.mount(this)` → `CommunityM3` → `HomePane`；挂载后 `updatePane.toFront()`、`launchPane.toFront()`、隐藏上游公告层（否则全屏空层吞点击）。
- 主题：`M3Host`（`lmcl-m3-host`/`lmcl-m3`）→ `M3ThemeManager.install` + `community-m3-host` + 样式表；Classic = M3 关闭。
- 主要页面：`community/ui/*`（关于/崩溃/转移）、`community/dashboard`（主页）、管理页与设置页复用上游页面 + 社区注入；18 处上游侵入点登记在 `docs/00-整体要求.md` §3。

---

## 5. 主页材质管线（玻璃）

```
Themes.background（壁纸） → DecoratorSkin 背景节点（样式类 lmcl-launcher-background，#18 侵入）
  → GlassBackdrop.LiveSource(backgroundNode, image, opacity) + liveRevision
  → GlassSurface（每张卡/侧栏/顶栏）：
      5×ImageView（实时采样壁纸，BoxBlur 半径 192/128/80/48/24，迭代 3/3/2/2/2）
      + CSS 权重 0.16/0.18/0.20/0.22/0.22（compact：0.10/0.14/0.16/0.24/0.28）
      + surface-container-low tint 0.22（侧栏 0.40）
  → MaterialCardSurface 守卫：玻璃开启时清掉卡片内容根实色 Background，关闭/Classic 恢复
```

- 快照路径（`GlassSnapshot`）仅作回退：上限 960×640、场景外扩 290 px。
- 调试：`-Dlmcl.visual.debug=true` 输出 `[glass]`/`[glass-material]`/`[glass-edge]`；`-Dlmcl.visual.glass=on|off|auto` 控制玻璃开关。
- 规则：玻璃层永远在内容之下；卡片点击不应依赖玻璃层；1×1 小卡靠 `materialCard` 的透明守卫，不靠 CSS 优先级。

---

## 6. 边缘过渡带（GlassEdgeFade）

- 位置：每列 `StackPane` 的顶/底各一条 `GlassEdgeFade`（`extends Region`，72 px，`mouseTransparent(true)`，`pickOnBounds(false)`）。
- 机制：`setDistance` 按滚动剩余距离量化成可见行数（`DISTANCE_STEP=1`）；`GlassEdgeFadeImage.render` 一次渲染整条 `width×bandHeight`：cover 采样壁纸 → 24 px 块平均 + 双线性上采样 → 22% tint → 逐行 alpha（外缘 255、内侧 0，曲线 `(1-t)^2`）→ 逐像素写入；滚动只 `setFitHeight(rows)` 缩放，不重建像素。
- 底部几何（v3.17.34/35）：
  - 右列视口底部内缩 56 px（`HomePane.INSTANCE_VIEWPORT_BOTTOM_INSET`），视口底边 = 页底 − 64 px；`createColumnFrame` 对滚动区与底部带都加 `Insets(0,0,56,0)`，渐隐带底边与内容截断处对齐。
  - `HOME_PADDING = 10/12/8/12`；`LAUNCH_PANE_CLEARANCE=64` 已 `@Deprecated`（不再参与布局）。
  - 启动按钮 `launch-pane` 230×57，BOTTOM_RIGHT；内容底边在其上方 7 px。
- 左列无 56 px 内缩；顶部带两列共用同一渲染器。

---

## 7. 主页组件（DashboardWidget）

- 基类：`DashboardWidget`（标题 + 描述 + `contentBox`）；卡片外壳 `BuiltinWidgets.materialCard(widget, arc, id)`。
- 通用原语（`BuiltinWidgets`）：`cardContent(body, footer)` = `VBox(6, bodyBox, footer)`（bodyBox `vgrow=ALWAYS`）、`cardFooter(...)`（HBox(8)/右对齐/≥3 按钮靠右成组）、`borderButton`（`jfx-button-border` + `RoundedHitArea`）、`filledButton`。
- 实例快照 `SnapshotWidget`：`FlowPane(8,6)`（回滚/标记/管理…，宽列自动换行 + tooltip）在卡片底部 footer；`updateButtons()`：回滚需 `hasSelection && latestSuccess(AUTO_START)!=null`，标记/管理需 `hasSelection`；空状态文案“请先选择实例”，三按钮为禁用态（非遮挡）。
- 其他卡：账户、最近存档、游玩日历、Java、崩溃、体检、网络、置顶实例等；卡间距/列宽由 `HomePane` 控制。

---

## 8. 硬约束速查

1. 上游侵入点共 **18 处**（`docs/00-整体要求.md` §3）；新增/移动必须登记。
2. Java：每个类/字段/方法 `///` Javadoc；可能为空的类型必须 `@Nullable`；不可变集合用 `@Unmodifiable`/`@UnmodifiableView`（`lmcl/AGENTS.md`）。
3. CSS：玻璃作用域用 4 类选择器（`.lmcl-m3-glass .community-home .community-material-card > .card`），不要用 `-fx-` 变量跨作用域硬覆盖。
4. i18n：新增文案必须同时改 `I18N.properties` / `I18N_zh.properties` / `I18N_zh_CN.properties`（`checkTranslations` 会挡住）。
5. 文档镜像：`lmcl/docs-community/` 必须与根 `docs/` 逐字节一致（`diff -rq` + `cmp`）。
6. 不新增依赖、不 relocate 包名、不做自动更新（已定案）。

---

## 9. 改动入口速查

| 想改什么 | 先看哪里 |
| --- | --- |
| 主页列布局/留白/滚动视口 | `community/dashboard/builtin/HomePane.java` |
| 卡片外观/玻璃参数 | `community/ui/m3/GlassSurface.java`、`m3-community.css` |
| 壁纸采样/背景节点 | `GlassBackdrop.java`、`GlassLiveViewport.java`、`DecoratorSkin.java`（#18） |
| 边缘渐隐/过渡带 | `GlassEdgeFade.java`、`GlassEdgeFadeImage.java`、`HomePane.createColumnFrame` |
| 快照卡按钮/文案 | `SnapshotWidget.java`、`I18N*.properties`、`community.css` |
| 启动按钮/窗口层序 | `ui/main/MainPage.java`、`root.css (.launch-pane)` |
| 构建/打包 | `PUBLISHING.md`、`docs/steps/21 §35.5` |

---

## 10. 已知限制

- WSL2 无 GUI：不能截图、不能跑 Windows 交互；所有视觉结论需用户实机截图。
- `dist/` 已按用户要求清理过，完整绿色包需要按 `PUBLISHING.md` 从 `.tools/` 重建（JRE/JavaFX/壳资产）。
- 顶部带与底部带共用渲染器：改底部带会同步影响顶部带外观（v3.17.32 起顶部带外缘也是材质色，属已披露偏差）。
