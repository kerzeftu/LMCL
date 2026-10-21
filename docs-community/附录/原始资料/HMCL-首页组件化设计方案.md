# HMCL 首页组件化设计方案

目标：把美西螈那种「首页由可自定义小组件构成」的体验移植到 HMCL，
同时遵守核心约束 —— **自身改动相对固定，长期只合并上游**。

---

## 一、可行性结论

**可行，且是三个候选基座里最省事的。** 关键数字：

| 事实 | 数值 | 含义 |
|---|---|---|
| `MainPage.java` | **20 KB** | 首页本体很小，改造面窄 |
| `RootPage.java` | 10 KB | 根页面（侧栏） |
| `ui/construct/` | **59 个现成组件** | 有大量可复用控件 |
| `SVG.java` | 39 KB | 图标系统现成 |
| `ui/` Java 文件总数 | 243 个 / 2.19 MB | 整个 UI 层比 Prism（367 文件 / 4.4 万行）小一个量级 |

对比 Prism 重写 UI 需要动 4.4 万行，HMCL 的首页组件化**只需新增代码 + 约 3 处极小的侵入点**。

---

## 二、HMCL 现有 UI 架构（改造要依附的骨架）

```
Controllers                 中央枢纽：页面注册、导航、对话框
  └── DecoratorController   窗口控制器，持有 Navigator（栈式导航）
        └── Decorator       自定义窗口装饰（UNDECORATED + 自绘标题栏）
              └── DecoratorSkin
                    └── TransitionPane     页面切换动画
                          └── Navigator    栈式页面管理

页面基类：DecoratorAnimatedPage
  ├── left   : VBox         左侧栏
  └── center : StackPane    内容区
```

关键类：

| 类 | 作用 |
|---|---|
| `main/RootPage.java` | 根页面，200px 侧栏，用 `AdvancedListBox` 建导航（账户/游戏/通用分组） |
| `main/MainPage.java` | 首页，**右下启动按钮 + 更新公告 + 版本选择 + 切换实例菜单** |
| `construct/Navigator.java` | 栈式导航 |
| `construct/TwoLineListItem.java` | 标准列表项（主标题 + 副标题，支持 tag） |
| `construct/AdvancedListBox.java` | 侧栏容器，支持分类 |
| `ui/SVG.java` | 统一矢量图标，viewBox 0 0 24 24，CSS 走 **Monet 颜色系统** |
| `ui/FXUtils.java` | `runInFX`、`onChange` 等工具方法 |

官方文档对首页的描述：**侧边导航栏 + 提示面板（仅开发版/预览版）+ 更新面板（有更新时）+ 启动游戏面板**。结构简单，正好是可插拔的土壤。

---

## 三、架构设计

### 3.1 整体结构：全部放在新 package

```
org.jackhuang.hmcl.ui.dashboard
├── DashboardPane.java          组件容器（网格 + 自适应列数）
├── DashboardWidget.java        组件抽象基类
├── WidgetRegistry.java         组件注册表（id → 工厂）
├── WidgetDescriptor.java       元数据：id / 显示名 / 图标 / 默认尺寸 / 是否可移除
├── DashboardConfig.java        布局配置的持久化（独立 JSON）
├── DashboardEditor.java        编辑模式（拖拽排序、增删、尺寸切换）
└── widgets/
    ├── LaunchWidget.java
    ├── PinnedInstancesWidget.java
    ├── RecentWorldsWidget.java
    ├── ActivityCalendarWidget.java
    ├── GreetingWidget.java
    ├── StorageStatusWidget.java
    ├── HealthCheckWidget.java
    └── UpdateNoticeWidget.java
```

**整个 package 上游完全不知道，不会与之冲突。**

### 3.2 组件基类

```java
public abstract class DashboardWidget extends VBox {
    private final WidgetDescriptor descriptor;

    public DashboardWidget(WidgetDescriptor descriptor) {
        this.descriptor = descriptor;
        getStyleClass().add("dashboard-widget");
    }

    public final WidgetDescriptor getDescriptor() { return descriptor; }

    /** 首次挂载时调用，构建静态 UI */
    protected abstract Node buildContent();

    /** 数据刷新。必须异步，不得阻塞 FX 线程 */
    public abstract void refresh();

    /** 释放资源、解绑监听，防止内存泄漏 */
    public void dispose() {}

    /** 可选的编辑模式控制条 */
    public Node createEditorControls() { return null; }
}
```

要点：

- `refresh()` **必须异步**——首页是常驻页面，同步加载会让启动器卡住
- `dispose()` 必须解绑所有 listener。JavaFX 里监听器持有强引用是内存泄漏的常见来源，而美西螈正是栽在"切页面内存不回收"上，不要重蹈覆辙
- 用 `FXUtils.runInFX()` 回到 FX 线程更新 UI

### 3.3 注册表

```java
public final class WidgetRegistry {
    private static final Map<String, Supplier<DashboardWidget>> FACTORIES = new LinkedHashMap<>();

    public static void register(String id, Supplier<DashboardWidget> factory) { ... }
    public static DashboardWidget create(String id) { ... }
    public static Collection<String> availableIds() { ... }
}
```

启动时（在 `Launcher` 初始化阶段或 `DashboardPane` 首次构造时）注册全部内置组件。
**第三方扩展只需调用 `register()` 即可新增组件，不需要改任何已有代码。**

### 3.4 容器：自适应网格

不要用 `GridPane` 硬编码行列。推荐 `FlowPane` 或自绘网格，按窗口宽度算列数：

```java
// 列宽基准 320px，最小列数 1，最大 4
int columns = clamp((int)(paneWidth / 320), 1, 4);
```

窗口缩放时重算并重排。每个组件占 `colSpan × rowSpan` 格（1×1 / 2×1 / 2×2）。

### 3.5 持久化：独立 JSON 文件

**不要往 HMCL 的 `Settings` 里塞字段**——那会增加与上游的冲突面。

用自己的文件：`~/.hmcl/dashboard.json`（或 `config.json`）

```json
{
  "version": 1,
  "columns": 3,
  "items": [
    { "id": "greeting",     "enabled": true,  "colSpan": 2, "rowSpan": 1, "order": 0 },
    { "id": "launch",       "enabled": true,  "colSpan": 2, "rowSpan": 1, "order": 1 },
    { "id": "pinned",       "enabled": true,  "colSpan": 2, "rowSpan": 2, "order": 2 },
    { "id": "recent-worlds","enabled": true,  "colSpan": 2, "rowSpan": 1, "order": 3 },
    { "id": "calendar",     "enabled": false, "colSpan": 2, "rowSpan": 2, "order": 4 }
  ]
}
```

`version` 字段用于后续迁移。旧的/未注册的 id 在加载时忽略，保证向前兼容。

### 3.6 编辑模式

- 长按或点"自定义"按钮进入编辑态
- 拖拽排序：JavaFX 没有内置 reorder，需自己实现 `setOnDragDetected` / `setOnDragOver` / `setOnDragDropped`
- 每个组件右上角出现移除按钮与尺寸切换按钮
- 底部"添加组件"抽屉，列出 `WidgetRegistry.availableIds()` 中未启用的

---

## 四、侵入点清单（控制在 3 处）

要保持"只合并上游"，侵入式改动必须极少：

| # | 位置 | 改动 | 冲突风险 |
|---|---|---|---|
| 1 | `MainPage.java` | 在 center 区域挂载 `DashboardPane`（约 5–10 行） | 中 —— 上游会改 MainPage |
| 2 | `Launcher.java`（初始化） | 注册内置 widget（1 行调用） | 低 |
| 3 | `root.css` | 新增 `.dashboard-widget` 等样式（追加，不改动已有规则） | 低 |

**第 1 点是唯一真正有冲突风险的。** 缓解办法：把挂载逻辑写成一行

```java
dashboardPane = new DashboardPane();
center.getChildren().add(dashboardPane);
```

上游改动 MainPage 时，多半冲突在这一块，但 diff 极小，人工合并几秒就能解决。

更保守的替代方案：**不改 MainPage，而是把 Dashboard 做成 RootPage 侧栏的一个新导航项**（类似"首页"tab）。这样侵入点降到 0，但用户需要多点一次才能看到组件化首页。建议先做这个零侵入版本验证，再决定是否替换首页。

---

## 五、可直接复用的现成资产

这是 HMCL 相比其他基座的巨大优势——组件化所需的砖块基本都在：

| 资产 | 位置 | 用途 |
|---|---|---|
| `TwoLineListItem` | `construct/` | 列表型组件的标准项 |
| `AdvancedListBox` | 侧栏用 | 分组展示 |
| `SVG` | `ui/SVG.java` | 图标，且**已接入 Monet 颜色系统** |
| `FXUtils` | `ui/FXUtils.java` | `runInFX` / `onChange` |
| `SpinnerPane` | `construct/` | 加载态 |
| `RipplerContainer` | `construct/` | MD 风格波纹点击 |
| `ContainerAnimations` | `animation/` | 页面/组件动画 |
| `TransitionPane` | `animation/` | 切换动画 |
| `TaskListPane` | `construct/` | 任务进度展示 |
| Monet CSS token | `assets/css/root.css` | 颜色直接引用，天然支持动态取色 |

**特别是 SVG 已走 Monet 颜色**——意味着组件化首页能自动跟随莫奈取色，与你的 MD3 方向天然一致，不需要额外适配。

---

## 六、组件清单与优先级

参考美西螈的首页设计，按性价比排序：

### 首批（P0，建议首发就做）

| 组件 | 说明 | 数据源 | 估工 |
|---|---|---|---|
| `LaunchWidget` | 大号启动按钮 + 当前实例信息 + 切换实例 | `Profiles` | 2 天 |
| `PinnedInstancesWidget` | 固定的常用实例，点击直达 | `Profiles.getSelectedProfile()` | 3 天 |
| `GreetingWidget` | 问候语 + 当日日期（情绪价值） | 本地时间 | 0.5 天 |
| `UpdateNoticeWidget` | 更新公告 | 上游已有逻辑，包装即可 | 1 天 |

### 第二批（P1）

| 组件 | 说明 | 数据源 | 估工 |
|---|---|---|---|
| `RecentWorldsWidget` | 最近进入过的存档，点即进入 | 存档解析（HMCLCore 有） | 3 天 |
| `StorageStatusWidget` | 磁盘剩余 / 内存状态 | `MemoryStatusBar` 可参考 | 2 天 |
| `ActivityCalendarWidget` | 游玩日历（哪天玩了哪个实例） | **需自建游玩记录** | 4 天 |

### 第三批（P2，与"启动保障"联动）

| 组件 | 说明 | 估工 |
|---|---|---|
| `HealthCheckWidget` | 启动前体检结果红黄绿摘要 | 3 天 |
| `SnapshotWidget` | 最近快照状态 / 一键回滚入口 | 2 天 |

> `ActivityCalendarWidget` 需要一个游玩时长记录的基础设施（实例启动/退出时打点并落盘），这是它额外 4 天的主因。但这个基础设施本身也有独立价值。

---

## 七、工作量估算（单人业余）

| 阶段 | 内容 | 工期 |
|---|---|---|
| 1. 框架 | 基类 + 注册表 + 容器 + 持久化 + 编辑模式 | **2–3 周** |
| 2. 首批组件 | 4 个 P0 组件 | **1–1.5 周** |
| 3. 打磨 | 响应式布局、动画、空态、错误处理 | **1 周** |
| 4. 第二批 | 3 个 P1 组件 | **1.5–2 周** |
| **合计（P0+P1）** | | **约 6–8 周** |

对比：Prism 完整 QML 重写是 12–24 个月。这个差距是数量级的。

---

## 八、风险与坑

### 8.1 内存泄漏（最重要）

美西螈的 Issue #421 就是"切页面内存不回收"。JavaFX 里同类风险来自：

- 组件持有外部对象引用且未解绑 → **`dispose()` 必须实现**
- 监听器用强引用 → 优先用 `FXUtils.onChange` 或 `WeakChangeListener`
- 后台任务未取消 → 组件销毁时取消 `Task`/`ScheduledService`

建议：开发期用 JavaFX 的 ScenicView 或 JFR 周期性检查首页反复进入退出后的堆占用。

### 8.2 首页卡顿

首页是启动器门面，卡一下用户立刻感知。

- 所有 I/O（读存档、扫磁盘、读游玩记录）**必须异步**
- 组件数据缓存 + 增量刷新，不要每次进首页全量重算
- 大量实例时参考上游 GP-5174 的做法（优化大量实例下的列表性能）

### 8.3 拖拽排序

JavaFX 对 `GridPane`/`FlowPane` 的子节点重排没有内置支持，需手写 DnD。
建议：拖拽时显示插入位置的占位符（半透明矩形），松手后再真正重排并落盘。

### 8.4 与上游 MainPage 的冲突

虽然只有 1 处侵入，但 MainPage 是上游会持续改动的活跃文件。
**缓解**：把挂载代码尽量压缩成 1–2 行，且放在文件尾部一个独立方法里，减少与其他改动重叠的概率。

### 8.5 中文字体与排版

组件内文字要遵循 MD3 的 type scale，中文需映射思源黑体/系统字体。
HMCL 已有 `-Dhmcl.font.override=` 参数，可直接复用其字体处理逻辑。

---

## 九、建议的执行顺序

1. **先做零侵入版**：把 Dashboard 作为 RootPage 侧栏的新导航项（不动 MainPage），验证框架 + 4 个 P0 组件。约 3–4 周
2. **收集反馈后再决定是否替换首页**
3. 稳定后逐步补 P1/P2 组件
4. 全程保持 widget 全部在 `ui/dashboard` package 内，侵入点不扩散

这样即使第 2 步决定不替换首页，第一步的产出也是完整可用的功能。

---

## 十、与整体路线的关系

首页组件化是**差异化体验**层，而"启动保障三件套"（启动前体检 / 崩溃现场还原 / 实例快照回滚）是**实用价值**层。

建议的优先级：

1. 先做启动保障三件套（解决"能不能玩上"，是硬需求）
2. 再做首页组件化（解决"好不好用"，是体验加分）
3. MD3 主题完善放在两者之后（上游已在做 MonetFX，不必抢跑）

组件化的 `HealthCheckWidget` 和 `SnapshotWidget` 正好是两者的结合点——把启动保障的结果以组件形式呈现在首页。
