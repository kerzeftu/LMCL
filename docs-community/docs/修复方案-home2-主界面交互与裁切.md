# 修复与改版方案：home2 主界面交互、裁切与 Axolotl 式主页

> 状态：**v2 已获产品批准（m08955 “ok，开工”）并已实现**；实现提交与验证见 [步骤 21 §10](steps/21-主页重构与侧边栏移除.md)，本文保留为设计与决策记录。
>
> 触发一（m08675）：home2 包“主界面组件（你新增的这些）都不能交互，并且有些框被截断（比如左下角的）底下直接不显示”。
>
> 触发二（m08814/m08817）：“主页展示不下的时候做成可以上下滚动的，然后把账号信息和游玩记录放左边，实例等放右边，左侧加一个类似美西螈启动器的侧边栏，上面放主页，下载，设置和其他四个按钮，继续规划完善md”。
>
> v2 结论先行：v1 的根因分析（公告覆盖层 + 固定槽位高度）不变；新增 **整页纵向滚动、左信息栏/右实例栏、全局 64px 图标侧栏** 三项改版。为了“主页”按钮在任意页面都有意义，侧栏放在 `Navigator` 之外（`DecoratorController`），已由产品批准为**第 7 处上游侵入**。
>
> 结论先行：这不是单个卡片的 CSS 问题，而是 **两层问题叠加**：
> 1. `MainPage` 中上游“夜间构建公告”覆盖层（`announcementPane`）铺满整个首页，且在 z-order 上压在社区 `HomePane` 之上，吞掉了所有鼠标/滚轮事件；
> 2. `HomePane` 把每张卡片设成**固定 `prefHeight` + 固定 `minHeight`**，内容一多就被压扁/裁切；滚动又被第 1 层挡住，所以底部卡片“直接不显示”。

## 1. 分析范围与证据口径

本文覆盖 home2 这轮真正影响主界面的提交，以及它依赖的 13 号挂载提交：

| 提交 | 内容 | 与本次问题的关系 |
| --- | --- | --- |
| `f3629c95b feat(21): remove sidebar, add Axolotl-style HomePane` | 删除 `RootPage` 侧栏、`DecoratorAnimatedPage` 空 left 处理、`CommunityDashboard` 改挂 `HomePane`、新增 `HomePane`、i18n | 引入固定槽位高度与新的 z-order 使用方式 |
| `1bdaee0a1 feat(13): community dashboard framework` | `MainPage` 中 `getChildren().add(0, communityDashboard)` 的挂载方式 | 决定了社区首页位于**最底层**；覆盖层问题由此暴露 |
| `68485c37b feat(19): community node-level css and visual polish` | `CommunityStyles`、`community.css`、字号/标题样式 | 把标题/正文放大，进一步挤压固定槽位；不是根因，但会加重裁切 |

本文所有“上游行为”结论均来自 v3.16.4 源码静态核对；当前开发机为 WSL2，无 DISPLAY，**尚未做 GUI 实机验证**。修复后的验收必须补上 Windows 实机点击与滚动测试。

## 2. 现象拆解

用户看到的现象可以拆成三个可独立验证的事实：

1. **可点击性**：社区首页的卡片、按钮、下拉、滚动条全部不响应；但上游右下角“启动游戏”按钮仍可点。
2. **裁切**：某些卡片（尤其是左列底部、右栏底部）内容显示不全，像被“切掉一截”。
3. **不可达**：底部卡片不显示，滚轮/滚动条也无法把页面往下滚。

这三个现象指向同一个**事件命中测试（hit testing）**问题：首页被一个透明但可接收鼠标的节点盖住了。上游启动按钮在 z-order 上高于覆盖层，所以它仍可点；社区卡片在覆盖层下面，所以全部失效。

## 3. 根因一（P0）：`announcementPane` 全屏覆盖层

### 3.1 代码事实

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java:166-170`：

```java
announcementPane = new TransitionPane();
announcementPane.setContent(announcementBox, ContainerAnimations.NONE);

StackPane.setMargin(announcementPane, new Insets(-15));
getChildren().add(announcementPane);
```

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java:162-164`：

```java
VBox announcementBox = new VBox(16);
announcementBox.setPadding(new Insets(15));
announcementBox.getChildren().add(announcementCard);
```

`HMCL/src/main/java/org/jackhuang/hmcl/ui/animation/TransitionPane.java:31`：

```java
public class TransitionPane extends StackPane {
    ...
    FXUtils.setOverflowHidden(this);
```

`MainPage` 是 `StackPane`，`announcementPane` 作为其子节点会被拉伸到整个可用区域；`announcementBox` 作为 `TransitionPane` 的内容也会被 `StackPane` 拉伸。`StackPane`/`VBox` 默认 `pickOnBounds = true`，因此**整块透明区域都参与鼠标命中测试**，而不是只有公告卡片本身。

`MainPage.java:305-317` 的挂载顺序：

```java
getChildren().addAll(updatePane, launchPane);

javafx.scene.Node communityDashboard =
        org.jackhuang.hmcl.community.dashboard.CommunityDashboard.mount(this);
if (communityDashboard != null) {
    getChildren().add(0, communityDashboard);   // 放到最底层
    ...
    StackPane.setMargin(communityDashboard, new Insets(56, 0, 96, 0));
}
```

最终 children 顺序（从下到上）是：

```text
communityDashboard (index 0)
announcementPane
updatePane
launchPane
```

`announcementPane` 在 `communityDashboard` 之上、在 `updatePane`/`launchPane` 之下。于是：

- 点击/滚轮落在首页任意位置 → 命中 `announcementPane`（或它的全尺寸 `announcementBox`）；
- 事件沿 `announcementPane → MainPage` 冒泡，**不会传给兄弟节点 `communityDashboard`**；
- 右下角启动按钮和右上角更新气泡在它上面，所以仍然可点；
- 社区卡片、滚动条、按钮都在它下面，全部不可交互。

### 3.2 为什么 home2 一定会有这个覆盖层

`HMCL/build.gradle.kts:29`：

```kotlin
val versionType = System.getenv("VERSION_TYPE") ?: if (isOfficial) "nightly" else "unofficial"
```

本次打包没有设置 `VERSION_TYPE`，且本地构建不是官方 CI，因此 `versionType = "unofficial"`，jar 内 `assets/hmcl.properties` 的 `hmcl.version.type=unofficial`。

`HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java:103-113`：

```java
public static boolean isStable() {
    return "stable".equals(BUILD_CHANNEL);
}

public static boolean isDev() {
    return "dev".equals(BUILD_CHANNEL);
}

public static boolean isNightly() {
    return !isStable() && !isDev();
}
```

所以 `Metadata.isNightly() == true`，`MainPage` 的 `if (Metadata.isNightly() || ...)` 一定成立，`announcementPane` 一定被创建。

### 3.3 为什么“关掉公告”也没用

`MainPage.java:145-149` 的关闭逻辑：

```java
btnHide.setOnAction(e -> {
    announcementPane.setContent(new StackPane(), ContainerAnimations.FADE);
    ...
});
```

它只把内容换成空的 `StackPane`，**没有把 `announcementPane` 从 `getChildren()` 移除，也没有关闭 `pickOnBounds`**。空 `StackPane` 仍然被 `TransitionPane` 拉伸到全尺寸，仍然吞事件。用户即使点了公告的关闭按钮，覆盖层依然存在。

> 这也解释了为什么“看起来首页已经渲染出来，但所有新组件都点不动”：覆盖层是透明的，视觉上看不出来。

## 4. 根因二（P1）：`HomePane` 固定槽位高度导致裁切

`HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java:100-115`：

```java
private static final List<Slot> MAIN_SLOTS = List.of(
        new Slot("launch", 168),
        new Slot("pinned-instances", 188),
        new Slot("recent-worlds", 272),
        new Slot("storage", 144),
        new Slot("java-recommend", 168));

private static final List<Slot> RAIL_SLOTS = List.of(
        new Slot("activity-calendar", 318),
        new Slot("memory", 158),
        new Slot("crash-history", 176),
        new Slot("health-check", 154),
        new Slot("network", 142),
        new Slot("snapshot", 176));
```

`HomePane.java:418-431`：

```java
private void addWidget(@NotNull VBox column, @NotNull String id, double height) {
    ...
    widget.setPrefHeight(height);
    widget.setMinHeight(height);
    widget.setMaxWidth(Double.MAX_VALUE);
    ...
}
```

`DashboardWidget` 基类本身是 `VBox`，内容容器 `contentBox` 用 `VBox.setVgrow(contentBox, Priority.ALWAYS)` 撑满剩余空间（`DashboardWidget.java:96-97`），且基类构造里 `setMinHeight(0)`（`DashboardWidget.java:90`）。`HomePane` 又把它覆盖成“固定 `pref` + 固定 `min`”，于是：

- 卡片内容比槽位矮：卡片被强行拉到槽位高度，出现大块空白；
- 卡片内容比槽位高：`VBox` 会压缩可压缩子节点，标题、正文、按钮互相挤，表现为“框被截断”；
- 个别卡片（`storage` 144、`health-check` 154、`network` 142）可用高度最紧，最容易先出问题；
- 步骤 19 把标题放大到 15px、headline 20px、正文 13px 后，同样的固定高度能放下的内容更少，进一步加重。

**底部卡片不显示**不是这些固定高度单独造成的：主列总高约 `168+188+272+144+168+4×12 = 988`，右栏含账户卡约 `1200`，1080p 窗口本来就需要滚动。真正让它“完全不可达”的是根因一：`ScrollPane` 在覆盖层下面，滚轮和滚动条都收不到事件。

## 5. 根因三（P2）：z-order 与局部覆盖

`getChildren().add(0, communityDashboard)` 的设计意图是“上游启动按钮/更新气泡保持在最上层”，这个意图本身没错，但它同时把社区首页放到了公告覆盖层下面。

在根因一修好之后，还要确认两个局部覆盖：

- `updatePane`：230×55，`TOP_RIGHT`（`MainPage.java:173-180`）；
- `launchPane`：HBox，CSS `.launch-pane` 限制 230×57，`BOTTOM_RIGHT`（`MainPage.java:210-217`）。

`HomePane` 当前用 `Insets(56, 0, 96, 0)` 给顶部/底部留出 56/96px，理论上正好避开这两个浮层。修复时**不要简单把这两个浮层也移到最上层并扩大**；应该保留它们的条带，通过 z-order 和 margin 让它们与首页互不遮挡。

## 5A. v2 布局与全局侧栏设计（m08814/m08817）

### 5A.1 目标拆解

用户这句话包含四个独立要求，必须分别验收：

1. **主页可上下滚动**：内容高度超过窗口时，整页（左栏 + 右栏）一起纵向滚动；页头和侧栏固定，不随内容滚走。
2. **账号信息 + 游玩记录在左**：左栏放“当前账号”、`activity-calendar`（游玩记录/日历）、`recent-worlds`（最近游玩世界）。
3. **实例在右**：右栏放启动、置顶实例、实例存储、Java 推荐、快照、崩溃、健康检查、网络等实例/功能卡。
4. **左侧 64px 图标侧栏**：主页、下载、设置、其他四个按钮；Axolotl 式窄图标栏，不是原来的 200px 文本栏。

### 5A.2 目标结构

```text
┌──────┬─────────────────────────────────────────────────────────┐
│ 64px │  HomePane 页头：问候语 + 运行状态（不再有四个导航按钮）   │
│ 图标 ├───────────────────────┬─────────────────────────────────┤
│ 侧栏 │ 左信息栏（340px）      │ 右实例栏（自适应，占满剩余宽度） │
│      │ ┌───────────────────┐ │ ┌─────────────────────────────┐ │
│ 主页 │ │ 当前账号卡片       │ │ │ launch                      │ │
│ 下载 │ ├───────────────────┤ │ ├─────────────────────────────┤ │
│ 设置 │ │ activity-calendar │ │ │ pinned-instances            │ │
│ 其他 │ ├───────────────────┤ │ ├─────────────────────────────┤ │
│      │ │ recent-worlds     │ │ │ storage / java / snapshot   │ │
│      │ │                   │ │ │ crash / health / network    │ │
│      │ └───────────────────┘ │ └─────────────────────────────┘ │
│      │        ↑ 两栏一起在同一个纵向 ScrollPane 内 ↑            │
└──────┴─────────────────────────────────────────────────────────┘
```

侧栏在 `Navigator` 之外，因此下载/设置等页面仍然保留各自的 200px 功能侧栏时，窗口左侧会同时出现 **64px 图标栏 + 该页 200px 侧栏**。这是全局图标栏的代价；如果实机觉得挤压，可在非主页页面隐藏 200px 侧栏，但不在本次范围。

### 5A.3 全局 64px 侧栏

#### 为什么必须放在 `Navigator` 之外

如果侧栏放在 `RootPage.Skin`，它只会在主页出现；用户点“下载”离开主页后侧栏消失，**“主页”按钮永远没有机会被点击**，四个按钮里第一个就是死按钮。因此侧栏必须包在 `Navigator` 外层，随窗口一直存在。

#### 改动点（第 7 处上游侵入）

`HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` 当前：

```java
navigator = new Navigator();
navigator.setOnNavigated(this::onNavigated);
navigator.init(mainPage);

decorator.getContent().setAll(navigator);
```

改为：

```java
navigator = new Navigator();
navigator.setOnNavigated(this::onNavigated);
navigator.init(mainPage);

CommunitySideBar sidebar = new CommunitySideBar();
HBox shell = new HBox(sidebar, navigator);
HBox.setHgrow(navigator, Priority.ALWAYS);
shell.setFillHeight(true);
decorator.getContent().setAll(shell);
```

要点：

- `CommunitySideBar` 固定 `pref/min/maxWidth = 64`；`navigator` 用 `HGrow.ALWAYS` 吃掉剩余宽度；
- 不修改 `Decorator` 自身布局，只替换它 `content` 列表里的根节点；
- 侧栏在窗口内容区内部、标题栏下方，与 Axolotl 的“标题栏下方图标栏”一致；
- 这是第 7 处上游 `.java` 侵入；00 §4.2 的预算需从 6 处改为 7 处，并登记 `DecoratorController.java` 的职责“全局 64px 导航侧栏容器”。**未获批准前不写这条预算**。

#### `CommunitySideBar` 规格

新文件：`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java`

```java
public final class CommunitySideBar extends VBox {
    public CommunitySideBar();          // 64px 宽，加载四个按钮
    public void setActive(@Nullable Node page); // 由 DecoratorController 在导航后调用
}
```

结构：

```text
VBox (styleClass: community-sidebar, 64px)
├─ 品牌小图标（可选，24–32px，LMCL 图标）
├─ 主页    JFXButton(SVG.HOME)      → Controllers.navigate(Controllers.getRootPage())
├─ 下载    JFXButton(SVG.DOWNLOAD)  → DownloadPage.showGameDownloads + navigate
├─ 设置    JFXButton(SVG.SETTINGS)  → SettingsPage.showGameSettings(repo) + navigate
├─ spacer
└─ 其他    JFXButton(SVG.MENU)      → JFXPopup
```

四个按钮统一：

- `setPrefSize(44, 44)`、`setMinSize(44, 44)`、`setMaxSize(44, 44)`；
- 图标 `SVG.X.createIcon(20)`；
- `setContentDisplay(GRAPHIC_ONLY)`；
- `FXUtils.installFastTooltip(button, i18n("community.sidebar.home"))`；
- styleClass `community-sidebar-button`；激活时加 `community-sidebar-button-active`；
- 容器 padding `12 10 12 10`、spacing 6；按钮居中。

“其他”弹层用 `JFXPopup`（与 HMCL 现有 popup 风格一致）：

```java
JFXPopup popup = new JFXPopup(menuBox);
popup.setAutoHide(true);
popup.show(otherButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, 0);
```

菜单项（`VBox` + `JFXButton`，左图标 + 文本；点击后先 `popup.hide()` 再执行）：

| 项 | i18n | 动作 |
| --- | --- | --- |
| 实例管理 | `community.sidebar.other.instances` | `Controllers.navigate(Controllers.getGameListPage())` |
| 账户管理 | `community.sidebar.other.accounts` | `Controllers.navigate(Controllers.getAccountListPage())` |
| 社区设置 | `community.sidebar.other.community` | `CommunitySettingsPanel.open(null)` |
| 反馈 | `community.sidebar.other.feedback` | `SettingsPage.showFeedback()` + navigate |
| 关于 LMCL | `community.sidebar.other.about` | `CommunitySettingsPanel.open(null, "about")` |
| 多人游戏（可选） | `community.sidebar.other.terracotta` | 复用 `HomePane.openTerracotta()` 的 provider 检查 |

`setActive(Node page)` 由 `DecoratorController.onNavigated` 调用：

```text
page == Controllers.getRootPage()       → active home
page == Controllers.getDownloadPage()   → active download
page instanceof LauncherSettingsPage    → active settings
其它                                    → 全部取消 active
```

实现时不要在 `CommunitySideBar` 里反向依赖 `DecoratorController`；只接收 `Node` 判断。

#### 侧栏 CSS（由步骤 19 的 `community.css` 统一提供）

```css
.community-sidebar {
    -fx-background-color: -monet-surface-container;
    -fx-border-color: transparent -monet-outline-variant transparent transparent;
    -fx-border-width: 0 1 0 0;
}
.community-sidebar-button {
    -fx-background-color: transparent;
    -fx-background-radius: 12;
    -fx-padding: 10;
}
.community-sidebar-button:hover {
    -fx-background-color: -monet-surface-container-high;
}
.community-sidebar-button-active {
    -fx-background-color: -monet-secondary-container;
}
```

图标颜色：默认 `-monet-on-surface-variant`；hover/active 用 `-monet-on-secondary-container`。图标着色沿用上游 `SVG` 的 CSS 做法，不在代码里写死 hex。

### 5A.4 `HomePane` 列重排

当前 `HomePane` 是 **左主列（实例）+ 右栏（信息）**；v2 反转：

| 当前 | v2 | 内容 |
| --- | --- | --- |
| `mainColumn`（左，flex） | `instanceColumn`（右，flex） | launch、pinned-instances、storage、java-recommend、snapshot、crash-history、health-check、network |
| `railColumn`（右，340） | `infoColumn`（左，340） | 当前账号卡、activity-calendar、recent-worlds |

常量改为：

```java
private static final int INFO_WIDTH = 340;
private static final List<Slot> INFO_SLOTS = List.of(
        new Slot("activity-calendar", 318),
        new Slot("recent-worlds", 272));
private static final List<Slot> INSTANCE_SLOTS = List.of(
        new Slot("launch", 168),
        new Slot("pinned-instances", 188),
        new Slot("storage", 144),
        new Slot("java-recommend", 168),
        new Slot("memory", 158),
        new Slot("snapshot", 176),
        new Slot("crash-history", 176),
        new Slot("health-check", 154),
        new Slot("network", 142));
```

`memory` 仍留在右列，基线 158；位置放在 `network` 之前或 `java-recommend` 之后都可以，实现时固定一个顺序，不要运行时抖动。

`rebuildBody()` 改为：

```java
infoColumn.getChildren().setAll(buildAccountCard());
for (Slot slot : INFO_SLOTS) addWidget(infoColumn, slot.id(), slot.height());

instanceColumn.getChildren().clear();
for (Slot slot : INSTANCE_SLOTS) addWidget(instanceColumn, slot.id(), slot.height());

infoColumn.setPrefWidth(INFO_WIDTH);
infoColumn.setMinWidth(300);
infoColumn.setMaxWidth(360);
HBox.setHgrow(instanceColumn, Priority.ALWAYS);
instanceColumn.setMinWidth(0);

HBox body = new HBox(COLUMN_GAP, infoColumn, instanceColumn);
body.setAlignment(Pos.TOP_LEFT);
body.setPadding(new Insets(0, 0, 24, 0));
scroll.setContent(body);
```

要点：

- `infoColumn` 固定 340（300–360 夹取），`instanceColumn` 吃掉剩余宽度；窗口窄时 `ScrollPane` 出现横向滚动条，不把右列挤成 0；
- 两列在**同一个** `ScrollPane` 内容里，所以纵向一起滚；
- 左列顶部账号卡不参与 `Slot`（它是固定 ~88px 的卡片），在 `INFO_SLOTS` 之前加入；
- 删除旧 `mainColumn/railColumn/RAIL_WIDTH/MAIN_SLOTS/RAIL_SLOTS` 命名，避免以后又按旧方向读代码；
- `widgets` 的 `LinkedHashMap` 仍按 id 管理，`isEnabled`/`dispose`/`rebuildBody` 语义不变。

### 5A.5 整页滚动

`buildScroll()` 改为：

```java
scroll.setFitToWidth(true);
scroll.setFitToHeight(false);
scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
scroll.setPannable(true);
scroll.setMinHeight(0);
VBox.setVgrow(scroll, Priority.ALWAYS);
```

`HomePane` 构造/初始化时：

```java
setMinHeight(0);
setFillWidth(true);
```

验收标准：

- 在 1366×768 下，左列（账号 + 日历 + 最近世界）与右列（8 张实例卡）总高度都超过可视区，滚轮在左列、右列、页头空白处都能让内容上下滚；
- 滚到最底部时，`network`/`memory` 最后一张卡的下边缘与窗口底部有 24px padding，不被切；
- 页头与侧栏固定，不随滚动移动；
- 横向不出现不必要的滚动条（窗口 ≥ 1100px 时）；< 1100px 时允许横向滚动，不裁切。

### 5A.6 页头简化

`HomePane.buildHeader()` 只保留：

```java
HBox header = new HBox(10, titles, BuiltinWidgets.spacer());
```

`titles` = `greeting`（`community-headline`）+ `statusRow`（`community.home.subtitle` + `runStatus`）。删除：

- 四个导航按钮 `instances/download/settings/more`；
- 220px `AccountAdvancedListItem`（账户入口改到左信息栏）；
- 右上角社区设置齿轮（设置与社区设置都在侧栏/其他菜单）。

这样页头高度稳定，不再与侧栏重复，也不会在窄窗口挤压。若实机觉得页头太空，可在 `spacer` 右侧加一个小号的 `[编辑布局]` 或 `[刷新]`，但不在本次必做范围。

### 5A.7 侧栏导航的返回栈行为

- 主页按钮：`Controllers.navigate(Controllers.getRootPage())`。当前已在主页时，`Navigator.navigate` 的 `from == node` 直接返回，不会重复入栈。
- 从下载/设置返回主页会 **push** 一个新的 `RootPage`，标题栏 back 会回到下载/设置；这是上游返回栈的既有行为，不做特殊 `clear()`，避免破坏向导/弹窗的 close 语义。
- 若产品要求“主页按钮直接清栈回根”，需要在 `DecoratorController` 暴露 `clear()` 并调用 `navigator.clear()`；这会改变返回行为，列为可选，不默认做。

### 5A.8 与现有 F1–F3 的关系

- **F1（z-order/公告）仍然必须做**：侧栏在 `Navigator` 外，但 `MainPage` 内部的公告覆盖层仍会吞掉 `HomePane` 的点击/滚轮；F1 不因 v2 失效。
- **F2（最小高度）仍然必须做**：列重排后卡片仍会因内容超过基线而被裁切；F2 是整页滚动的另一半。
- **F3（ScrollPane）与 5A.5 合并**：按 5A.5 实现，不再重复。
- **F4/F5** 按 v2 重写后的页头与卡片清单执行。

### 5A.9 新增/修改文件清单（批准后）

| 文件 | 类型 | 内容 |
| --- | --- | --- |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` | 上游修改（第 7 处） | 用 `HBox(sidebar, navigator)` 替换 `content.setAll(navigator)`；导航后 `sidebar.setActive(page)` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java` | 新增 | 64px 侧栏、四个按钮、其他菜单、active 状态 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java` | 上游修改（已有第 2 处） | F1：首页置顶、隐藏公告覆盖层 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java` | 修改 | 列反转、页头简化、滚动、最小高度 |
| `HMCL/src/main/resources/assets/css/community.css` | 修改（步骤 19 文件） | 侧栏/按钮/active/菜单样式 |
| `HMCL/src/main/resources/assets/lang/I18N*.properties` | 修改 | `community.sidebar.*` 三份同步 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/RootPage.java` | **不改** | 已移除 200px 文本侧栏；v2 侧栏在 `DecoratorController`，不回到 RootPage |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorAnimatedPage.java` | **不改** | 保留“空 left 不占 200px”的既有修改 |

### 5A.10 i18n 键

新增（三份 `I18N.properties` 的英文键值，中文放 `I18N_zh.properties`/`I18N_zh_CN.properties`）：

```text
community.sidebar.home=Home
community.sidebar.download=Download
community.sidebar.settings=Settings
community.sidebar.other=More
community.sidebar.other.instances=Instances
community.sidebar.other.accounts=Accounts
community.sidebar.other.community=Community settings
community.sidebar.other.feedback=Feedback
community.sidebar.other.about=About LMCL
community.sidebar.other.terracotta=Multiplayer
```

中文：

```text
community.sidebar.home=主页
community.sidebar.download=下载
community.sidebar.settings=设置
community.sidebar.other=其他
community.sidebar.other.instances=实例管理
community.sidebar.other.accounts=账户管理
community.sidebar.other.community=社区设置
community.sidebar.other.feedback=反馈
community.sidebar.other.about=关于 LMCL
community.sidebar.other.terracotta=多人游戏
```

旧的 `community.home.nav.*` 若页头删除后不再引用，可保留不删（避免翻译检查的未使用告警不等于错误），也可在同一批清理；`checkTranslations` 必须 0 缺键。

### 5A.11 验收（新增）

除 §7 的原有交互/裁切验收外，新布局必须额外通过：

1. 任意页面（主页/下载/设置/实例/账户）左侧都有 64px 图标栏；图标提示文字正确；
2. 在下载页点“主页”能回到主页；在主页点“主页”不重复导航；
3. 在设置页点“下载”直接进入下载页；点“设置”不重复压栈；
4. “其他”弹层六个入口都能打开对应页面/面板，弹层自动关闭；
5. 1366×768 下，左栏账号/日历/最近世界与右栏 8 张卡都能通过滚轮到达；页头与侧栏不动；
6. 1920×1080 下无横向滚动条，右列 2 列卡（launch/pinned）宽度合理，不出现大片空白；
7. 窗口缩到 1000px 时，横向滚动条出现而不是卡片被裁；侧栏按钮不换行；
8. 高 DPI 150% 下侧栏图标/提示/按钮点击区域仍完整。

### 5A.12 文档同步（批准后）

| 文档 | 修改 |
| --- | --- |
| `docs/00-整体要求.md` | §4.2 预算 6 → **7 处**，新增 `DecoratorController.java` 行；§6.1/§6.2 步骤 21 工期从 0.5–1 周改为 **1–1.5 周**；§13 新增“全局 64px 侧栏”决策 |
| `docs/steps/21-主页重构与侧边栏移除.md` | 标题/目标改为“200px 文本侧栏移除 + 全局 64px 图标侧栏”；补 `DecoratorController` 侵入、`CommunitySideBar`、四按钮、主页栈行为 |
| `docs/steps/13`/`14`/`15`/`19` | 主页结构、卡片归属、侧栏样式、`CommunityStyles` 调用点补 `CommunitySideBar` |
| `README.md` | 版本号、步骤 21 状态、关键决策“6 处”改 7 处、工期 |
| `docs/审查记录-v3.16.4源码核对.md` | 追加本轮“修复 + v2 侧栏改版”记录 |

## 6. 修复方案

### F1（P0，必须）：让社区首页成为可交互层，并移除全屏公告覆盖

推荐做法（改动集中在 `MainPage` 已有的挂载块，不新增入侵文件）：

```java
getChildren().addAll(updatePane, launchPane);

javafx.scene.Node communityDashboard =
        org.jackhuang.hmcl.community.dashboard.CommunityDashboard.mount(this);
if (communityDashboard != null) {
    // 追加到末尾：社区首页在公告/更新/启动浮层之上，先拿到鼠标事件
    getChildren().add(communityDashboard);
    javafx.scene.layout.StackPane.setAlignment(
            communityDashboard, javafx.geometry.Pos.TOP_LEFT);
    javafx.scene.layout.StackPane.setMargin(
            communityDashboard, new javafx.geometry.Insets(56, 0, 96, 0));

    // 上游 nightly/dev 公告是整页覆盖层；社区首页已有自己的顶部区域，
    // 保留它只会继续吞事件，因此在这里隐藏（不是 setContent 空内容）。
    if (announcementPane != null) {
        announcementPane.setVisible(false);
        announcementPane.setManaged(false);
    }

    // 保留上游启动/更新浮层在其条带内的可点击性（它们很小，不会盖住卡片）。
    updatePane.toFront();
    launchPane.toFront();
}
```

要点与理由：

1. **`add` 而不是 `add(0, ...)`**：社区首页必须是 StackPane 中最后一个 child，才能收到落在其范围内的鼠标/滚轮事件。
2. **隐藏 `announcementPane`**：它是全尺寸覆盖层，且关闭后仍留空壳；社区首页已经有自己的公告/更新提示（05/13 的 `promptUpdateIfNeeded`），保留上游 nightly 公告没有产品价值，还会继续挡事件。
3. **`updatePane.toFront()` / `launchPane.toFront()`**：保证 230×55 与 230×57 的浮动按钮仍在最上层；它们位于 `HomePane` 的 56/96px margin 之外，不会遮住卡片。
4. **不修改 `TransitionPane` 本身**：它是上游通用动画组件，改它的 `pickOnBounds` 会影响其它页面；本问题只需要在 `MainPage` 这一处解决。

备选方案（如果产品坚持保留上游公告横幅）：

- `announcementPane.setPickOnBounds(false)`；
- `announcementBox.setMaxHeight(Region.USE_PREF_SIZE)`（避免 VBox 被拉伸到全高）；
- 关闭时 `getChildren().remove(announcementPane)`，而不是 `setContent(new StackPane())`；
- 仍建议把社区首页放最上层，避免公告卡片与首页 header 重叠。

备选方案改动更多、视觉回归面更大，**不作为首选**。

### F2（P1，必须）：把固定槽位高度改为“最小高度 + 内容自适应”

`HomePane.addWidget` 建议改为：

```java
private void addWidget(@NotNull VBox column, @NotNull String id, double minHeight) {
    ...
    // 槽位高度只作为下限，不强制卡片固定高度；
    // 内容多时卡片可以长高，由外层 ScrollPane 负责滚动。
    widget.setMinHeight(minHeight);
    widget.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
    widget.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    widget.setMaxWidth(Double.MAX_VALUE);
    ...
}
```

要点：

1. `setMinHeight(height)` 保留原来的视觉基线（短卡片仍然整齐）；
2. **删除 `setPrefHeight(height)`**，让 `VBox` 按内容计算首选高度；
3. `setMaxHeight(USE_PREF_SIZE)` 防止父容器把卡片拉得过高；
4. `Slot.height()` 的语义在代码注释与文档中改为“最小高度（像素）”，避免后续又有人按固定高度理解；
5. `DashboardWidget` 基类的 `setMinHeight(0)` 可以保留（作为默认），由 `HomePane` 的槽位下限覆盖；不要反过来把基类改成固定高度。

同时做三项内容侧收口：

- `DashboardWidget` 构造里补 `contentBox.setFillWidth(true)`，让子内容按卡片宽度换行；
- `BuiltinWidgets.body/caption` 等长文本统一 `setWrapText(true)`，必要时 `setTextOverrun(ELLIPSIS)`；
- `health-check`/`network` 的详情行设置最大行数（如 3 行），超出用“…”或“查看详情”而不是无限撑高。

### F3（P1，必须）：保证 ScrollPane 可达

根因一修好后滚轮会恢复；再加两道保险：

```java
scroll.setFitToWidth(true);
scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
scroll.setPannable(true);
scroll.setMinHeight(0);
```

- `HomePane` 本身也应 `setMinHeight(0)`，避免 `StackPane` 把首页撑出窗口；
- `body` 的 bottom padding 从 16 改为 24，最后一张卡片与窗口底部留出呼吸空间；
- 不建议默认 `vbarPolicy(ALWAYS)`：会在内容不足时也常驻滚动条，视觉噪音大；`AS_NEEDED` 配合可滚动内容即可。

### F4（v2 重写，P1）：页头去重与窄窗口

v2 的导航入口迁到全局侧栏后，`HomePane` 页头不再需要 `instances/download/settings/more` 四个按钮，也不再需要 220px 的 `AccountAdvancedListItem`。新页头只保留：

```text
问候语 + 运行状态（+ 版本/更新提示由 promptUpdateIfNeeded 处理）
```

旧的 `buildHeader()`（`HomePane.java:321-363`）改为：

```java
HBox header = new HBox(10, titles, BuiltinWidgets.spacer());
```

账户入口在左信息栏“当前账号”卡片；下载/设置/其他在全局侧栏；实例管理在侧栏“其他”与左栏账号卡下方均可进入。这样页头在 900px 宽度下也不会被挤压，且不会与侧栏重复。

**注意**：页头删除四个导航按钮后，原有 `openInstances()/openDownloads()/openLauncherSettings()/openAccounts()/openTerracotta()/openFeedback()` 中只有仍被引用的保留，未引用的删除，避免死代码；i18n 的 `community.home.nav.*` 可保留（不再使用）或统一清理，二者择一。

这不是原始报障主因，但 v2 布局必须一起处理，否则侧栏 + 页头导航会出现两套入口。

### F5（v2 重写，P2）：按新列分组逐卡复查

v2 的列分组见 §5A.4。槽位高度只作为**最小高度**，不再固定；下表的“基线”是 `Slot.minHeight()`，不是最终高度：

| 列 | 卡片 | 基线 | 修复后检查点 |
| --- | --- | ---: | --- |
| 左信息栏 | `account`（`buildAccountCard`） | 88 | 账户名/类型不换行溢出；未登录态完整 |
| 左信息栏 | `activity-calendar` | 318 | 26 周模式是否超高；紧凑模式是否仍触发 |
| 左信息栏 | `recent-worlds` | 272 | 3 行世界；长世界名省略；340px 宽是否可读 |
| 右实例栏 | `launch` | 168 | 实例名/状态/按钮换行；空态不塌陷 |
| 右实例栏 | `pinned-instances` | 188 | 3 个 tile 是否换行；`+N` 是否可见 |
| 右实例栏 | `storage` | 144 | 路径行在窄宽度下是否可读；空路径不占位 |
| 右实例栏 | `java-recommend` | 168 | 发行版名/平台名换行；两个 tab 不挤压 |
| 右实例栏 | `snapshot` | 176 | 上次成功/文件数/大小 + 三个按钮 |
| 右实例栏 | `crash-history` | 176 | 2 条记录 + 红点；空态/错误态高度 |
| 右实例栏 | `health-check` | 154 | 详情最多 3 行；复查按钮不被挤掉 |
| 右实例栏 | `network` | 142 | 主源/镜像/兜底三行；错误文案换行 |

`memory` 卡仍在右列（不属于“实例”，但保留在右侧功能组），基线 158；其指标行 + 按钮 + `gameRunning` 小字是右侧最紧的一张，优先实机检查。

## 7. 验证计划

### 7.1 静态断言

```bash
# 1) MainPage 中不应再有 add(0, communityDashboard)
grep -n "add(0, communityDashboard)\|communityDashboard" \
  lmcl/HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java

# 2) HomePane 中不应再有固定 setPrefHeight(height)；只允许 setMinHeight
grep -n "setPrefHeight(height)\|setMinHeight(height)" \
  lmcl/HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java

# 3) 确认 announcementPane 在挂载分支被隐藏/移除
grep -n "announcementPane" \
  lmcl/HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java

# 4) 确认全局侧栏包在 Navigator 外层，而不是 RootPage.Skin
grep -n "CommunitySideBar\|HBox(sidebar, navigator)\|setAll(shell)" \
  lmcl/HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java

# 5) 确认 HomePane 已反转列名/槽位
grep -n "INFO_SLOTS\|INSTANCE_SLOTS\|infoColumn\|instanceColumn" \
  lmcl/HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java

# 6) 确认四个侧栏按钮与 i18n 键
grep -n "community.sidebar" \
  lmcl/HMCL/src/main/resources/assets/lang/I18N*.properties
```

### 7.2 构建与测试

```bash
export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11
export GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home
cd /home/kerze/hmclce/lmcl

./gradlew :HMCL:compileJava --no-daemon
./gradlew :HMCL:checkTranslations --no-daemon
./gradlew :HMCL:test --tests 'org.jackhuang.hmcl.community.*' --no-daemon
VERSION_ROOT=3.16 BUILD_NUMBER=1000 ./gradlew :HMCL:shadowJar --no-daemon
```

预期：编译通过；翻译检查通过；community 测试仍为 **132/0**；jar 打包成功，`Main-Class` 仍为 `org.jackhuang.hmcl.community.CommunityBootstrap`。

### 7.3 Windows 实机手工验收（必须）

至少覆盖 1366×768、1920×1080、2560×1440 三种窗口/缩放：

1. 打开首页，不关闭任何弹窗，直接点击每张卡片的按钮/链接/下拉，全部有响应；
2. 鼠标滚轮在左信息栏、右实例栏、页头空白处滚动，页面都能上下滚动；页头与 64px 侧栏保持固定；
3. 滚到最底部，`memory`、`health-check`、`network` 等卡片完整可见，最后一张下边缘有 24px 呼吸位；
4. 关闭上游公告（如果保留备选方案）后再次重复 1–3，确认空覆盖层不再吞事件；
5. 左侧四个按钮逐一点击：主页/下载/设置/其他；从下载页点“主页”能回来，从设置页点“下载”能直接切换；
6. “其他”六个菜单项逐一点击，弹层自动收起，对应页面/面板打开；
7. 打开“设置/下载源/实例管理/账户”，再返回首页，首页仍可交互；
8. 拖动窗口变窄到 1000px：侧栏不换行、页头不重叠、横向滚动条出现而不是卡片被裁；
9. 高 DPI 125%/150% 下重复 1–8，确认文字、图标、点击区域不被裁切。

### 7.4 回归包

修复后重新构建、重新打 zip，**不要复用 home2 的 zip**；包内 `LMCL.exe` 的 stub 前缀必须仍是 454,144 B，且 `exe[454144:] == jar`，SHA-256 重新计算。

## 8. 风险与回退

| 风险 | 影响 | 处理 |
| --- | --- | --- |
| 第 7 处上游侵入（`DecoratorController`） | 全局 UI 容器改动，回归面比卡片大 | 只替换 `content` 根节点为 `HBox(sidebar, navigator)`，不改导航/动画；保留回退开关；实机覆盖所有页面 |
| 全局侧栏与页面自带 200px 侧栏叠加 | 下载/设置页左侧变成 264px，内容变窄 | 1366px 宽实机检查；必要时在非主页隐藏页面 200px 侧栏（另立步骤，不在本次） |
| “主页”按钮压栈 | 从下载页回主页后再 back 会回下载页 | 上游 `Navigator` 既有行为；若产品要求清栈，再在 `DecoratorController` 暴露 `clear()`，单独评审 |
| 隐藏上游 nightly 公告 | 看不到官方“这是夜间构建”的提示 | 社区首页已有版本/更新提示；如产品坚持保留，改用 F1 备选方案 |
| 卡片改为自适应高度 | 首页不再严格等高，视觉上参差 | 用 `minHeight` 保持短卡片基线；长卡片长高是可接受的 |
| `toFront()` 浮层遮挡 | 更新/启动浮层可能压住卡片局部 | 保持 56/96 margin，并在实机验收中专门检查右上/右下 |
| 删侧栏后的返回路径 | 其它页面需返回首页 | 全局侧栏提供“主页”；`DecoratorPage` 标题栏 back 仍保留 |
| 页头删除导航后入口丢失 | 实例/账户/下载/设置入口看似变少 | 侧栏四按钮 + “其他”六项覆盖；左栏账号卡补账户入口 |
| 无 GUI 自动化 | 回归依赖人工 | 在 CI 之外保留 Windows 手工清单；后续可补 TestFX/Monocle |

回退方式：只回退 F1/F2/F3 的改动即可恢复 home2 行为；不涉及数据格式、配置或上游其它页面。

## 9. 工作量与执行顺序

| 项 | 预计 |
| --- | --- |
| F1 `MainPage` z-order + 隐藏公告 | 0.5 人日 |
| F2 `HomePane` 槽位自适应 + 内容收口 | 0.5 人日 |
| F3/5A.5 ScrollPane/尺寸保险 | 0.2 人日 |
| F4 页头简化（v2） | 0.2 人日 |
| F5 逐卡复查 + 实机验收 | 0.5 人日 |
| 5A 全局 64px 侧栏（`DecoratorController` + `CommunitySideBar` + CSS + i18n） | 1.0–1.5 人日 |
| 5A.4 列反转/滚动布局 | 0.5 人日 |
| 重新构建/打包/文档回填（00/21/README/审查记录） | 0.5–1.0 人日 |
| **合计** | **约 4.4–5.4 人日** |

执行顺序：**F1 → F2/5A.4/5A.5（先让首页可交互、可滚、列反转）→ 5A.3 全局侧栏 → F4 页头简化 → 编译/测试 → Windows 实机验收 → F5 卡片收口 → 重新打包 → 文档回填（00 预算 6→7、步骤 21、README、审查记录）**。F1 与 5A 的侧栏未完成前不要打包，否则用户看到的仍是“点不动/滚不动”。

**批准点**：5A.3 是第 7 处上游侵入，请用户在批准时明确同意 6→7；若不同意，只能退回“仅主页侧栏”，此时“主页”按钮永远不生效，不建议。

## 10. 附：关键文件与行号索引

| 文件 | 行号 | 内容 |
| --- | --- | --- |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java` | 145-149 | 关闭公告只 setContent 空 StackPane |
| 同上 | 162-170 | `announcementBox` / `announcementPane` 创建并加入 children |
| 同上 | 173-180 | `updatePane` 230×55，TOP_RIGHT |
| 同上 | 210-217 | `launchPane`，BOTTOM_RIGHT |
| 同上 | 305-317 | 社区首页 `add(0, ...)` + margin |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/animation/TransitionPane.java` | 31-36 | extends StackPane；overflow hidden |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java` | 54-58 | 当前 `content.setAll(navigator)`；v2 改为 `HBox(sidebar, navigator)`（第 7 处侵入） |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java` | 新增 | 64px 图标栏；四按钮 + “其他”弹层 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java` | 100-115 | 当前主列/右栏固定槽位高度；v2 改 `INFO_SLOTS`/`INSTANCE_SLOTS` |
| 同上 | 321-363 | 当前页头四导航 + 账户项；v2 只留问候/状态 |
| 同上 | 367-381 | 当前 ScrollPane；v2 加 `pannable/minHeight` |
| 同上 | 384-415 | 当前主列/右栏 body；v2 反转为 info/instance |
| 同上 | 418-433 | 当前固定 pref/min；v2 只留 min |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/DashboardWidget.java` | 88-97 | 基类 `setMinHeight(0)`、contentBox vgrow |
| `HMCL/src/main/resources/assets/css/community.css` | 新增规则 | `.community-sidebar*`（步骤 19 文件） |
| `HMCL/src/main/resources/assets/lang/I18N*.properties` | 三份 | `community.sidebar.*` |
| `HMCL/build.gradle.kts` | 29 | `VERSION_TYPE` 默认 unofficial |
| `HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java` | 103-113 | `isStable/isDev/isNightly` |

> 行号为当前 home2 基线（HEAD `065d64bec` 附近）；改代码后以实际为准，文档只保留“文件:职责”对应关系，不承诺行号永久不变。

---

**等待批准**：批准后按 **F1 → F2/5A.4/5A.5 → 5A.3 全局侧栏 → F4 → F5** 的顺序改代码，先在本机编译/测试，再打一个新的 Windows 测试包；不会在未批准情况下改 `MainPage`/`HomePane`/`DecoratorController` 或重新打包。

批准时请同时确认 **第 7 处上游侵入**（`DecoratorController.java`，全局 64px 侧栏）。若只批准 6 处、不允许第 7 处，则只能做“仅主页侧栏”，四按钮中的“主页”在离开主页后不可见，方案不完整，建议不要选。
