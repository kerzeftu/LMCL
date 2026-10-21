# 其他页面与“关于 LMCL”重构方案

> 状态：**第一阶段已实施（2026-10-22，m10129）**。实现记录见[步骤 22](steps/22-其他页面与关于LMCL.md) §6 与[审查记录 §34](审查记录-v3.16.4源码核对.md)；测试包 `dist/LMCL-3.16.1000-win-x64-other1.zip`。
>
> 2026-10-22 已按 m10063 调整为“第一阶段只做帮助/反馈/关于”，账户/实例/社区设置内嵌延后。
>
> 目标版本：v3.8（第一阶段已交付）
>
> 基线：v3.16.4 / `fbcc17472f08e877d60d6b715734e8054e9e61ee`
>
> 当前代码：`/home/kerze/hmclce/lmcl`；v3.7 基线（代码 `bc9730d95`，文档 `bf0978ca5`），v3.8 第一阶段改动已落盘并验证。

---

## 0. 用户需求

User said (m09949):

> 其他也单独做成一个页面，像下载和设置这样的，然后里面的内容再做在二级菜单里，关于lmcl这边写开发人员和致谢列表，开发人员写个指尖的宇宙kerze，致谢列表先写hmcl的开发者（从hmcl那边同步信息），再预留捐赠者的位置。设置里的帮助反馈和关于统一移到其他里面就好了，其他选项里的其他内容也用hmcl的视觉风格重构，先写md

拆成 6 个可验收项：

1. 点全局侧栏“其他”不再弹 `JFXPopup`，而是导航到一个独立页面，和下载页、设置页同级。
2. 其他页内部有 200px 二级菜单，菜单项按 HMCL 的设置页样式分组。
3. 新增“关于 LMCL”：开发人员 `指尖的宇宙 kerze`；致谢列表读取 HMCL 的 `thanks.json`；预留捐赠者区域。
4. 设置页的“帮助 / 反馈 / 关于”三个 tab 和左侧三项从设置页移除，统一从其他页进入。
5. 账户/实例、社区设置最终都必须**内嵌为 tab**，但本轮**暂缓**；第一阶段 OtherPage 只交付帮助/反馈/关于，避免先上线用户已明确否定的“点击跳转完整页”形态。
6. 先交付本 md；批准后再改代码。

### 0.1 m10063 用户决定（2026-10-22）

User said (m10063):

> 账户/实例不接受“二级菜单点击后跳转到现有完整页面”，但是这部分开发可以暂缓。社区设置要内嵌tab，但是可以暂缓。致谢列表全部展示，暂不加入捐赠入口。关于页等我做出来图标再替换

据此锁定：

- **第一阶段范围**：OtherPage 只放“帮助 / 反馈 / 关于 LMCL”三个 tab；不注册账户、实例、社区设置入口。
- **账户/实例**：最终形态必须是内嵌 tab，需拆 `GameListPage`/`AccountListPage`；本轮不做，仍由主页账号 chip、启动卡/实例管理入口到达。
- **社区设置**：最终形态必须是 OtherPage 内嵌 tab；本轮不做，仍从主页 `[设置]` 打开现有对话框；`CommunitySettingsPanel` 的 about 组仍按本方案移除。
- **致谢**：`thanks.json` 全部展示（个人 + 社区伙伴），不筛选。
- **捐赠**：只保留空态占位，不加捐赠入口/按钮。
- **图标**：继续用 `/assets/img/icon.png`，等用户提供 LMCL 图标后替换。

---

## 1. 现状（已核对 v3.7 代码）

### 1.1 全局侧栏

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java`

- `CommunitySideBar extends VBox`，`WIDTH = 64`、`BUTTON_SIZE = 44`。
- 按钮顺序：`homeButton`、`downloadButton`、`settingsButton`、spacer、`otherButton`。
- `otherButton` 的动作是 `this::showOtherMenu`。
- `showOtherMenu()` 创建 `VBox menu` + `JFXPopup`，菜单项：
  - 实例 → `Controllers.navigate(Controllers.getGameListPage())`
  - 账户 → `Controllers.navigate(Controllers.getAccountListPage())`
  - 社区设置 → `CommunitySettingsPanel.open(getWindow())`
  - 反馈 → `Controllers.getSettingsPage().showFeedback()` + 导航设置页
  - 关于 → `CommunitySettingsPanel.open(getWindow(), "about")`
- `setActive(Node page)` 目前：
  ```java
  boolean home = page == rootPage || (rootPage != null && page == rootPage.getMainPage());
  boolean download = page instanceof DownloadPage;
  boolean settings = page instanceof LauncherSettingsPage;
  boolean other = !home && !download && !settings;
  ```
  这意味着任何“非主页/下载/设置”的页面都会把“其他”按钮点亮，包括版本页、账户页、实例页。新页面落地后应改为 `page instanceof OtherPage`。

### 1.2 设置页

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`

- `LauncherSettingsPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware`。
- `tab` 字段由 `TabHeader` 管理，tab 列表：
  - `gameTab`（`GameSettingsPage<GameSettings.Preset>`）
  - `javaManagementTab`
  - `settingsTab`（`SettingsPage`）
  - `personalizationTab`
  - `downloadTab`（`DownloadSettingsPage`）
  - `helpTab`（`HelpPage`）
  - `feedbackTab`（`FeedbackPage`）
  - `aboutTab`（`AboutPage`）
- 左侧 `AdvancedListBox`：
  - 游戏、Java 管理；
  - `launcher` 分类：常规、外观、下载；
  - `help` 分类：帮助、反馈、关于；
  - `FXUtils.setLimitWidth(sideBar, 200)`。
- `setLeft(sideBar); setCenter(transitionPane);`
- `showFeedback()` 当前 `tab.select(feedbackTab, false)`。
- `Controllers.java` 的 `onHyperlinkAction("hmcl://settings/feedback")` 当前是两步：
  ```java
  Controllers.getSettingsPage().showFeedback();
  Controllers.navigate(Controllers.getSettingsPage());
  ```
  **这条链必须保留可用**，否则帮助/反馈里的站内链接会失效。
- 注意：如果只把 `LauncherSettingsPage.showFeedback()` 改成导航 `OtherPage`，`Controllers` 第二行的 `navigate(getSettingsPage())` 会立刻把页面切回设置页，链接仍然失效。因此本方案必须同时改 `Controllers` 的这两行，不能只改设置页。

### 1.3 关于页

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/AboutPage.java`

- `AboutPage extends SpinnerPane`。
- 产品卡：`LineButton.createExternalLinkButton(Metadata.PUBLISH_URL)`，标题硬编码 `"Hello Minecraft! Launcher"`，副标题 `Metadata.VERSION`，图标 `/assets/img/icon.png`。
- 作者卡：`huanghongxun` → bilibili。
- 致谢：`loadIconedTwoLineList("/assets/about/thanks.json")`。
- 依赖：`/assets/about/deps.json`。
- 法律：`about.copyright`、`about.claim`、`about.open_source`，链接 `Metadata.EULA_URL` 与 `https://github.com/HMCL-dev/HMCL`。
- 该页不在本步删除，只是不再挂到设置页；保留上游文件可减少侵入，也方便对照。

### 1.4 可复用页面

- `HelpPage extends SpinnerPane`，构造无参，内部 `VBox + ScrollPane + ComponentList`，从 `Metadata.DOCS_URL + "/index.json"` 异步加载帮助分类。
- `FeedbackPage extends SpinnerPane`，构造无参，内部 `ComponentList`，包含 QQ 群、Discord、GitHub issues。
- 两者都是 `SpinnerPane`，可直接作为 `TabHeader.Tab` 的节点。
- `GameListPage`、`AccountListPage` 都是 **完整的 `DecoratorAnimatedPage`**，各自带 200px 左侧导航：
  - `GameListPage` 左侧是游戏目录列表 + 新建实例/导入整合包/全局设置；
  - `AccountListPage` 左侧是登录方式（微软/离线/外置登录）。
  - **不能直接塞进其他页的 tab**，否则会出现“全局 64px + 其他页 200px + 再一层 200px”的三层导航。
  - 第一阶段 OtherPage **不接入**这两个页面；第二阶段需先拆出可复用 Pane，再内嵌为 tab，不做跳转。

### 1.5 致谢数据

`HMCL/src/main/resources/assets/about/thanks.json`：

- JSON 数组，条目字段：
  - `image`（字符串或 `{light, dark}` 对象）
  - `title` / `titleLocalized`
  - `subtitle` / `subtitleLocalized`
  - `externalLink`
- 已包含 HMCL 的开发者、支持者与社区伙伴，例如：
  `yushijinhun`、`bangbang93`、`Glavo`、`ZekerZhayard`、`Zkitefly`、`Burning_TNT`、`ShulkerSakura`、`gamerteam`、`Red_lnn`、`mcmod`、`mcbbs`、`mcim`、`8mi-tech` 等。
- 条目文案走 `about.thanks_to.<name>.statement`，这些 key 在 HMCL 的语言文件中已经存在。
- **不复制名单**：运行时直接读 classpath 资源，上游同步后自动更新。

### 1.6 现有社区设置面板

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsPanel.java`

- 内置分组：
  - `dashboard`（重置布局/进入编辑）
  - `data`（本地根、用户根、打开目录、导入入口）
  - `about`（产品、版本、基线、网站、源码、许可证、更新检查、第三方声明）
- `about` 组当前由 `CommunitySettingsPanel.open(window, "about")` 打开，侧栏“关于”走的就是它。
- 用户要求“设置里的帮助反馈和关于统一移到其他里面”，这里的 `about` 也应视为“关于”的一部分，建议一并迁走，避免出现两个“关于”。

---

## 2. 总体设计

### 2.1 信息架构

```
全局 64px 侧栏
  ├─ 主页
  ├─ 下载
  ├─ 设置          ← 只保留启动器设置：游戏 / Java / 常规 / 外观 / 下载
  └─ 其他          ← 本步新增 OtherPage（第一阶段）
        └─ 支持
             ├─ 帮助      → 内嵌 HelpPage
             ├─ 反馈      → 内嵌 FeedbackPage
             └─ 关于 LMCL → 内嵌 AboutLMCLPage
```

第一阶段**不注册**账户、实例、社区设置入口。目标形态（第二阶段，暂缓）：

```
其他
  ├─ 账户与实例
  │    ├─ 账户 → 内嵌 tab（需从 AccountListPage 拆 Pane）
  │    └─ 实例 → 内嵌 tab（需从 GameListPage 拆 Pane）
  └─ 社区
       └─ 社区设置 → 内嵌 tab（需拆 CommunitySettingsPanel）
```

说明：

- “其他”页本身是 `DecoratorAnimatedPage`，左侧 200px 二级菜单，中间 `TransitionPane`，与 `LauncherSettingsPage` 同构。
- 第一阶段只有“支持”一组、三个 tab；**不做“点击后跳转到完整页”**，避免上线用户已明确否定的形态。
- 账户/实例暂不放入 OtherPage，仍由主页账号 chip、启动卡/实例管理入口到达；第二阶段把 `GameListPage`/`AccountListPage` 的内容拆成可复用 Pane，再内嵌为 tab。
- 社区设置第二阶段才内嵌；第一阶段仍从主页 `[设置]` 打开现有 `CommunitySettingsPanel` 对话框，但本步会删除其 about 组。
- 帮助、反馈、关于是轻量 `SpinnerPane`，直接内嵌 tab，符合“二级菜单里的内容”。
- **注意**：第二阶段拆 `GameListPage`/`AccountListPage`/`CommunitySettingsPanel` 会新增上游侵入，需在动工前重新评估 §5.4 的 9 处预算；本阶段不申请这部分预算。

### 2.2 页面对比

| 维度 | 现状 | 目标 |
|---|---|---|
| 其他入口 | 64px 侧栏按钮弹 popup | 导航到独立 `OtherPage` |
| 其他内容（第一阶段） | 5 个 popup 项，自绘 `JFXButton` | 200px `AdvancedListBox` + “支持”分类 + 帮助/反馈/关于三个 tab |
| 账户/实例/社区设置 | popup 项；账户/实例跳完整页 | 本步不注册；第二阶段内嵌 tab（不跳转） |
| 设置页 | 含帮助/反馈/关于 | 只保留启动器设置 |
| 关于 | 上游 `AboutPage` / 社区面板 about 两处 | 统一为 `AboutLMCLPage`，唯一入口 |
| 反馈 | 设置页 tab；侧栏 popup | 其他页 tab；站内链接仍可用 |
| 帮助 | 设置页 tab | 其他页 tab |
| 视觉 | popup 深色菜单 | 与设置页一致的 `AdvancedListBox`、`ComponentList`、`LineButton` |

---

## 3. `OtherPage` 设计

### 3.1 位置与类型

新增：

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/OtherPage.java`

```java
public final class OtherPage extends DecoratorAnimatedPage
        implements DecoratorPage, PageAware {

    private static @Nullable OtherPage instance;

    public static OtherPage getInstance() { ... } // FX 线程懒加载

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader tab;

    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("communityHelpPage");
    private final TabHeader.Tab<FeedbackPage> feedbackTab = new TabHeader.Tab<>("communityFeedbackPage");
    private final TabHeader.Tab<AboutLMCLPage> aboutTab = new TabHeader.Tab<>("communityAboutPage");

    private OtherPage() { ... }

    public void showHelp() { ... }
    public void showFeedback() { ... }
    public void showAbout() { ... }

    @Override public ReadOnlyObjectProperty<State> stateProperty() { ... }
    @Override public void onPageShown() { tab.onPageShown(); }
    @Override public void onPageHidden() { tab.onPageHidden(); }
}
```

要点：

- 放 `community.ui`，与 `CommunitySideBar`、`CommunitySettingsPanel` 同包；不新增 `ui.main` 平行包。
- 单例，理由：设置页/下载页也是单例；从其他页跳到实例页再返回时保留 tab 选择；避免 `DecoratorController` 导航栈里堆多个同页实例。
- `getInstance()` 只在 FX 线程调用；`Controllers.navigate(OtherPage.getInstance())`。
- 不往 `Controllers` 加 `getOtherPage()`，避免多改一个上游文件。

### 3.2 左侧二级菜单

第一阶段：

```java
AdvancedListBox sideBar = new AdvancedListBox()
        .startCategory(i18n("community.other.category.support"))
        .addNavigationDrawerTab(tab, helpTab, i18n("help"), SVG.HELP, SVG.HELP_FILL)
        .addNavigationDrawerTab(tab, feedbackTab, i18n("contact"), SVG.FEEDBACK, SVG.FEEDBACK_FILL)
        .addNavigationDrawerTab(tab, aboutTab, i18n("community.other.about"), SVG.INFO, SVG.INFO_FILL);

FXUtils.setLimitWidth(sideBar, 200);
setLeft(sideBar);
setCenter(transitionPane);
```

说明：

- 分类标题用 `.toUpperCase(Locale.ROOT)`，与设置页一致。
- 第一阶段只有三个 `addNavigationDrawerTab`；没有 `addNavigationDrawerItem`，也不引用 `Controllers.getGameListPage/getAccountListPage` 或 `CommunitySettingsPanel.open`。
- 第二阶段再增加“账户与实例”“社区”分类；目标都是内嵌 tab，不是 `addNavigationDrawerItem` 跳转。
- `OtherPage` 不 import `GameListPage`/`AccountListPage`/`CommunitySettingsPanel`，避免形成错误依赖，也避免第一阶段出现“能点但跳到完整页”的入口。

### 3.3 页面生命周期

- `OtherPage implements PageAware`：
  - `onPageShown()` → `tab.onPageShown()`；
  - `onPageHidden()` → `tab.onPageHidden()`。
- `TabHeader` 懒创建节点：`HelpPage::new`、`FeedbackPage::new`、`AboutLMCLPage::new`。
- `AboutLMCLPage` 构造只搭骨架；读取 `thanks.json`/donor JSON 走 `Schedulers.io()`，回 `Schedulers.javafx()`；失败只影响该段，不阻塞页面。
- 不能把 `GameListPage` / `AccountListPage` 直接放进 tab；它们是完整页面，第二阶段需先拆出可复用 Pane（见阶段 E）。

### 3.4 返回与激活态

- `CommunitySideBar.setActive`：
  ```java
  boolean other = page instanceof OtherPage;
  ```
  - 在 `OtherPage` 时“其他”高亮；
  - 第一阶段不会从 OtherPage 进入账户/实例/社区设置；这些页面仍可从主页入口到达，四个一级按钮都不高亮是可接受的；
  - 从主页再次进入 OtherPage 时，单例保留上次 tab。
- `CommunitySideBar` 的 `openOther()`：
  ```java
  try {
      Controllers.navigate(OtherPage.getInstance());
  } catch (Throwable e) {
      LOG.warning("Failed to open other page", e);
  }
  ```
- 删除 `JFXPopup`、`showOtherMenu()`、`addMenuItem()`、`openFeedback()`、`getWindow()` 等 popup 专用代码；`CommunityStyles` 仍用于侧栏本身。
- `community.css` 的 `.community-sidebar-menu` / `.community-sidebar-menu-item` 规则不再使用，删除，避免死样式。

---

## 4. `AboutLMCLPage` 设计

### 4.1 位置与类型

新增：

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutLMCLPage.java`

```java
public final class AboutLMCLPage extends SpinnerPane {
    public AboutLMCLPage() { ... }
}
```

可选纯数据帮助类：

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutData.java`

- `record Entry(String title, @Nullable String subtitle, @Nullable String image, @Nullable String link)`
- `record Donor(String name, @Nullable String link, @Nullable String note)`
- `static List<Entry> loadThanks()`：读 `/assets/about/thanks.json`
- `static List<Donor> loadDonors()`：读 `/assets/about/lmcl-donors.json`
- 纯解析、可单测；不依赖 JavaFX。

### 4.2 页面结构

沿用 `AboutPage` 的 `SpinnerPane + VBox + ScrollPane + ComponentList`：

```text
ComponentList.createComponentListTitle("关于 Lumina Minecraft Launcher")

[产品卡]
  图标（暂用 /assets/img/icon.png，正式图标待补）
  标题：Lumina Minecraft Launcher
  副标题：LMCL 3.16.1000 · 基于 HMCL v3.16.4
  点击：官网 CommunityMetadata.WEBSITE

ComponentList.createComponentListTitle("开发人员")
[开发人员]
  指尖的宇宙 kerze
  开发与维护

ComponentList.createComponentListTitle("致谢 · HMCL 项目与贡献者")
[从 /assets/about/thanks.json 读取的条目，按上游顺序]

ComponentList.createComponentListTitle("捐赠者")
[空态：暂无捐赠者，感谢每一份支持；未来从 lmcl-donors.json 读取]

ComponentList.createComponentListTitle("法律与来源")
[版权声明] [附加条款] [上游源码] [GPLv3]
```

### 4.3 产品卡

- `LineButton.createExternalLinkButton(CommunityMetadata.WEBSITE)`；`setLargeTitle(true)`。
- `setLeading(FXUtils.newBuiltinImage("/assets/img/icon.png"))`（**临时**；新图标未提供，正式发布前替换为 LMCL 图标）。
- 标题：`CommunityMetadata.fullName()`。
- 副标题：`i18n("community.about.version_line", CommunityMetadata.version(), CommunityMetadata.baseline())`。
  - 中文建议：`%s · 基于 HMCL %s`；
  - 英文建议：`%s · based on HMCL %s`。
- 点击官网；如果 `WEBSITE` 为空则禁用/去掉链接（当前已配置 `https://www.kerzeftu.top/lmcl`）。

### 4.4 开发人员

- 标题：`community.about.developers`（开发人员 / Developers）。
- 一行 `LinePane`：
  - `setTitle(i18n("community.about.developer.kerze"))`；
  - `setSubtitle(i18n("community.about.developer.role"))`（开发与维护 / Development and maintenance）；
  - 左侧可用 `SVG.PERSON` 图标（24–32px），不伪造头像；
  - 没有可靠链接，**不加 externalLink**，避免编造。
- 文案值：`指尖的宇宙 kerze`（中英三份语言文件保持一致；英文可写 `kerze (指尖的宇宙)`）。

### 4.5 致谢列表（从 HMCL 同步）

原则：**不复制、不硬编码名单**。

- 读取 classpath `/assets/about/thanks.json`，这是 HMCL 上游资源，随每次上游同步自动更新。
- 渲染逻辑与 `AboutPage.loadIconedTwoLineList` 对齐：
  - `image` 字符串 → `FXUtils.newBuiltinImage`；
  - `image` 对象 `{light, dark}` → 监听 `Themes.darkModeProperty()` 切换；
  - `title` / `titleLocalized`；
  - `subtitle` / `subtitleLocalized`；
  - `externalLink` → `LineButton` 尾图标 + `FXUtils.openLink`。
- 因为 `AboutPage` 的该方法是 `private`，方案 A：
  - 在 `AboutData` 里复制约 60 行解析/渲染辅助（推荐，避免改上游 `AboutPage`）；
  - 方案 B：把 `AboutPage.loadIconedTwoLineList` 提为 `public static`（会新增一处上游侵入），不推荐。
- 标题写“致谢 · HMCL 项目与贡献者”，因为 `thanks.json` 里既有个人开发者也有关联项目/社区伙伴；不要笼统称“HMCL 开发者”，避免把组织写成个人。
- 如果资源缺失或解析失败：显示空列表 + `LOG.warning`，不抛异常。
- 不在本步过滤名单；如果用户只想要个人开发者，再单列白名单（但会失去自动同步）。

### 4.6 捐赠者区域

新增资源：

`HMCL/src/main/resources/assets/about/lmcl-donors.json`

初始内容：

```json
[]
```

数据结构：

```json
[
  {
    "name": "捐赠者昵称",
    "link": "https://example.com/optional",
    "note": "可选留言"
  }
]
```

规则：

- `name` 必填；`link`/`note` 可选；空数组合法。
- 解析失败/字段非法：跳过该条，`LOG.warning`，不阻塞页面。
- 空态：`community.about.donors.empty`（“暂无捐赠者，感谢每一份支持。”），放在 `ComponentList` 内，用 `HintPane`/`.subtitle-label`，不要用空白卡片。
- 未来捐赠者按 `lmcl-donors.json` 手动维护；本期**不提供**“捐赠”按钮/支付入口，只预留展示位。
- 隐私：只展示用户同意公开的昵称/链接；不在代码里预置真实姓名。

### 4.7 法律与来源

- 版权：`about.copyright` / `about.copyright.statement`（上游 key）。
- 附加条款：`CommunityMetadata.licenseUrl()` 为空时隐藏；当前 `LICENSE_URL` 由 `SOURCE_URL + "/blob/main/LICENSE-ADDITIONAL-TERMS.md"` 生成，而 `SOURCE_URL` 目前为空串，因此实际隐藏；等 GitHub 仓库建立后自动出现。
- 上游源码：`https://github.com/HMCL-dev/HMCL`（固定上游）。
- LMCL 源码：`CommunityMetadata.sourceUrl()` 非空时才显示。
- GPLv3 声明：新增 `community.about.legal.gpl`，写明“LMCL 基于 HMCL，按 GPLv3 分发；修改部分版权归 LMCL 团队，原版权归 HMCL 作者”。
- 不写“官方认可/合作”等表述。

### 4.8 更新检查

- 从 `CommunitySettingsPanel` 的 about 组迁到本页（底部或产品卡下方）。
- 复用 `CommunityUpdateNotifier`：
  - `isConfigured()` 为 false 时按钮禁用 + `community.update.not_configured`；
  - `checkAsync()` 结果走 `Schedulers.javafx()`；
  - 有更新 → `CommunityUpdateDialog.show(info)`；无 → `Controllers.dialog(i18n("community.update.latest"))`。
- 不下载、不替换文件；与 v1.7 决策一致。

---

## 5. 设置页迁移

### 5.1 删除的 tab

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`：

删除：

- `helpTab`、`feedbackTab`、`aboutTab` 字段；
- 三个 `setNodeSupplier(...)`；
- `TabHeader` 构造参数里的三项；
- 左侧 `startCategory(i18n("help")...)` 和三个 `addNavigationDrawerTab(...)`；
- 相关 import：`HelpPage`、`FeedbackPage`、`AboutPage`；
- `showFeedback()` 方法本身（全仓唯一调用方是 `Controllers`，改由 `Controllers` 直接导航 `OtherPage`）。

保留：

- 游戏、Java 管理、常规、外观、下载。
- 其余 `showGameSettings()` 不变。

### 5.2 站内 feedback 链接

`Controllers.onHyperlinkAction` 当前：

```java
case "hmcl://settings/feedback":
    Controllers.getSettingsPage().showFeedback();
    Controllers.navigate(Controllers.getSettingsPage());
    break;
```

改为：

```java
case "hmcl://settings/feedback":
    OtherPage page = OtherPage.getInstance();
    page.showFeedback();
    Controllers.navigate(page);
    break;
```

这是**第 9 处上游侵入**（`Controllers.java`），原因：

- 只改 `LauncherSettingsPage` 不够——`Controllers` 后面的 `navigate(getSettingsPage())` 会把页面切回设置页；
- 用 `Platform.runLater` 延迟导航属于时序 hack，不采用；
- 直接让 `Controllers` 打开 `OtherPage` 最清晰，且只改两行。

### 5.3 社区设置面板

`CommunitySettingsPanel.java`：

- 删除内置 `about` 分组（`byId.put("about", ...)`）和 `buildAboutSection()`。
- 删除 `CommunityUpdateNotifier`、`CommunityUpdateDialog`、`CommunityMetadata` 在 about 组里的引用（如果别处不用则一并清 import）。
- `open(window, "about")` 的调用方全部删除；`CommunitySideBar` 的 about/community popup 一并删除；`about` 内容迁到 `AboutLMCLPage`。
- 主页 `[设置]` 仍可打开 `CommunitySettingsPanel`（默认 dashboard 或 data 组），它不再承载“关于”。
- `dashboard` 与 `data` 保留；数据导入入口仍由步骤 06 提供；第二阶段再把这部分拆成可内嵌 tab。
- `community.settings.tab.about` 与 `community.settings.about.*` 文案：
  - `community.settings.about.*` 可复用到 About 页的信息行；
  - `community.settings.tab.about` 不再使用，可从三份语言文件删除，或保留待清理（建议删除，避免死 key）。

### 5.4 上游侵入预算

当前 `docs/00-整体要求.md` §4.2 固定 **7 处**代码侵入；本步新增 **2 处**：

1. `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`
   - 删除 help/feedback/about 三个 tab、三个 supplier、左侧 help 分类与“帮助/反馈/关于”三项；
   - 删除 `showFeedback()` 方法；
   - 删除不再使用的 import。
2. `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java`
   - 只改 `onHyperlinkAction` 的 `hmcl://settings/feedback` 分支：直接打开 `OtherPage` 的反馈 tab；
   - 不再先调 `getSettingsPage().showFeedback()` 再 `navigate(getSettingsPage())`。

合计 **第 8、9 处**。需要同步：

- `00` §4.2 表加两行；
- §13 新增决策（帮助/反馈/关于迁移到其他页）；
- `README` 步骤总表/总工期；
- 步骤 21 或新增步骤 22 记录；
- 审查记录追加一节。

不新增：

- `AboutPage.java`（不改，仅不再挂 tab）；
- `HelpPage.java` / `FeedbackPage.java`（不改，复用为 tab 节点）；
- `RootPage.java` / `DecoratorAnimatedPage.java` / `DecoratorController.java` / `MainPage.java`（本步不动）。

---

## 6. 侧栏与导航改造

### 6.1 `CommunitySideBar`

- `otherButton = createButton(SVG.MORE_HORIZ, "community.sidebar.other", this::openOther);`
- 删除：
  - `JFXPopup`、`Window` import；
  - `showOtherMenu()`、`addMenuItem()`、`openFeedback()`、`getWindow()`。
- `setActive` 的 `other` 改为 `page instanceof OtherPage`。
- 不再需要 `CommunityStyles.apply(menu)`；`community.css` 删除 `.community-sidebar-menu*`。
- 侧栏按钮 tooltip 不变；“其他”按钮的 active 只在 OtherPage。

### 6.2 站内链接

- `Controllers.onHyperlinkAction` 的 `hmcl://settings/feedback` 改为：
  ```java
  OtherPage page = OtherPage.getInstance();
  page.showFeedback();
  Controllers.navigate(page);
  ```
- 不再经过 `LauncherSettingsPage.showFeedback()`；该方法随设置页清理删除。
- 这样不会出现“先切 Other 再被 settings 覆盖”的时序问题。
- 其他 `hmcl://` 分支不动。

### 6.3 全局侧栏 active 的边界

- 主页：home。
- 下载：download。
- 设置：settings。
- 其他：other。
- 账户、实例、版本页：四个按钮都不 active。
  - 这是刻意行为：它们不是一级导航，避免“其他”在实例页误亮。
  - 如果用户希望这些页面也亮“其他”，可后续扩展 `setActive` 的页面集合。

---

## 7. 视觉规范

### 7.1 OtherPage

- 左栏：`AdvancedListBox`，`FXUtils.setLimitWidth(sideBar, 200)`。
- 分类标题：`i18n(...).toUpperCase(Locale.ROOT)`，与 `LauncherSettingsPage` 一致。
- 第一阶段图标：`SVG.HELP`/`SVG.HELP_FILL`、`SVG.FEEDBACK`/`SVG.FEEDBACK_FILL`、`SVG.INFO`/`SVG.INFO_FILL`；账户/实例/社区设置的 `SVG.PERSON`、`SVG.STADIA_CONTROLLER`、`SVG.SETTINGS` 到第二阶段再加。
- 中间：`TransitionPane`，tab 内容均使用 `SpinnerPane + VBox + ScrollPane + ComponentList`。
- 不新增自绘深色菜单、不硬编码颜色。

### 7.2 AboutLMCLPage

- 与 `AboutPage` 相同的 `LineButton` 大标题行。
- 每个 section 用 `ComponentList.createComponentListTitle(...)` 分隔。
- 开发者、致谢、捐赠者都用 `LineButton`/`LinePane`，不直接铺 `Label`。
- 空态用 `HintPane` 或 `.subtitle-label` 文本。
- 产品图标暂用 `/assets/img/icon.png`；新图标资产到位后替换，文件名建议 `/assets/img/lmcl.png`。

### 7.3 响应式

- 1280×720 下，64 + 200 + 内容仍可读；内容区最小宽度约 1000。
- OtherPage 内容 tab 自己滚动；左栏固定 200。
- About 页 `ScrollPane.fitToWidth(true)`，长名单可滚动。

---

## 8. 资源与 i18n

### 8.1 新增资源

- `HMCL/src/main/resources/assets/about/lmcl-donors.json`（初始 `[]`）。
- 可选：`HMCL/src/main/resources/assets/img/lmcl.png`（等设计稿；未到位前用 `icon.png`）。

### 8.2 新增 i18n key

三份文件：

- `HMCL/src/main/resources/assets/lang/I18N.properties`
- `I18N_zh.properties`
- `I18N_zh_CN.properties`

Other 页（第一阶段）：

```text
community.other.title
community.other.category.support
community.other.about
```

第二阶段再补（本步不创建）：

```text
community.other.category.account_instance
community.other.category.community
community.other.accounts
community.other.instances
community.other.community_settings
```

About 页：

```text
community.about.developers
community.about.developer.kerze
community.about.developer.role
community.about.thanks
community.about.donors
community.about.donors.empty
community.about.version_line
community.about.legal.gpl
community.about.legal.modified
```

复用：

- `help`、`contact`、`about`（上游 key）；
- `community.settings.about.product/version/baseline/website/source/license/third_party`；
- `community.update.check/checking/latest/not_configured`；
- `about.copyright` / `about.copyright.statement` / `about.claim` / `about.open_source`；
- `about.thanks_to.*`（上游致谢文案）。

### 8.3 删除/停用

- 从设置页移除后，`community.settings.tab.about` 不再使用；建议删除。
- `community.sidebar.other.feedback/about` 可复用为 Other 页 tab 文案；`instances/accounts/community` 本阶段不再有入口，**保留 key 给第二阶段**，不要删除（避免二次翻译）。
- `community.other.category.account_instance`/`community.other.category.community`/`community.other.accounts`/`community.other.instances`/`community.other.community_settings` 本阶段**不创建**。
- `.community-sidebar-menu*` CSS 删除。

---

## 9. 实现步骤（批准后）

### 阶段 A：数据与关于页（约 1.5 人日）

1. 新增 `AboutData`：
   - 读 `thanks.json`，转 `List<Entry>`；
   - 读 `lmcl-donors.json`，转 `List<Donor>`；
   - 资源缺失/损坏返回空列表并 WARNING。
2. 新增 `AboutLMCLPage`：
   - 产品卡、开发人员、HMCL 致谢（全部条目）、捐赠者空态、法律；
   - 更新检查；
   - 所有数据加载走 `Schedulers.io()`，回 FX。
3. 新增 `lmcl-donors.json` 与 i18n key；**不加捐赠入口/按钮**。
4. 单测：donor JSON 合法/空/损坏；thanks 资源存在时条目数 > 0；非法字段跳过。

### 阶段 B：其他页与侧栏（约 1 人日）

1. 新增 `OtherPage` 单例、200px 二级菜单，**仅** `support` 分类和 help/feedback/about 三个 tab；不注册账户/实例/社区设置。
2. `CommunitySideBar`：
   - `otherButton` → `openOther()`；
   - `setActive` 改 `instanceof OtherPage`；
   - 删除 popup 代码与 CSS。
3. `CommunitySettingsPanel` 删除 about 组；`open(window,"about")` 调用方清零；主页 `[设置]` 仍打开 dashboard/data 对话框。
4. 手工验证：从主页/下载/设置点“其他”，再进帮助/反馈/关于，返回。

### 阶段 C：清理设置页（约 0.5 人日）

1. `LauncherSettingsPage` 删除 help/feedback/about tab、供应商、侧栏分类。
2. 删除 `showFeedback()` 方法；`Controllers` 的 feedback 分支改为直接打开 `OtherPage`。
3. 删除无用 import；搜索全仓 `showFeedback`，确认只剩 `OtherPage.showFeedback()`。
4. 确认设置页左侧只剩游戏/Java/常规/外观/下载。

### 阶段 D：验证与文档（约 1 人日）

1. `:HMCL:compileJava`。
2. `:HMCL:checkTranslations`。
3. `:HMCL:test --tests 'org.jackhuang.hmcl.community.*'`。
4. `VERSION_ROOT=3.16 BUILD_NUMBER=1000 ./gradlew :HMCL:makeExecutables`。
5. 文档：
   - 新增/更新 `docs/steps/22-其他页面与关于LMCL.md`（或把本方案转正）；
   - `docs/00-整体要求.md` §4.2 第 8/9 处、§13 决策；
   - `README.md` 步骤表、总工期、版本；
   - `docs/审查记录-v3.16.4源码核对.md` 追加一节；
   - `lmcl/docs-community/` 同步。
6. 打测试包；无 Windows 实机则明确 GUI 未验。

### 阶段 E（暂缓，不计入本步）：账户/实例/社区设置内嵌

1. 从 `GameListPage` 拆出可复用的实例列表 Pane（目录选择、实例列表、新建/导入/全局设置）；`GameListPage` 变成壳 + Pane。
2. 从 `AccountListPage` 拆出可复用的账户列表 Pane（登录方式、账户列表）；`AccountListPage` 变成壳 + Pane。
3. 把 `CommunitySettingsPanel` 的 dashboard/data 组拆成可嵌入 Pane，供 OtherPage tab 和设置对话框共用。
4. OtherPage 增加“账户与实例”“社区”分类，全部用 `addNavigationDrawerTab` 内嵌，**不允许跳完整页**。
5. 重新评估上游侵入预算：会改 `GameListPage.java`、`AccountListPage.java`，预计新增第 10/11 处；社区设置面板本身是社区类，但拆 Pane 可能牵动上游 API。需产品批准后另立步骤 23。

## 10. 文件清单

### 新增

```text
HMCL/src/main/java/org/jackhuang/hmcl/community/ui/OtherPage.java
HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutLMCLPage.java
HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutData.java
HMCL/src/main/resources/assets/about/lmcl-donors.json
```

### 修改

```text
HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java
HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsPanel.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java   ← 第 8 处上游侵入
HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java                ← 第 9 处上游侵入
HMCL/src/main/resources/assets/lang/I18N.properties
HMCL/src/main/resources/assets/lang/I18N_zh.properties
HMCL/src/main/resources/assets/lang/I18N_zh_CN.properties
HMCL/src/main/resources/assets/css/community.css
```

### 不改

```text
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/AboutPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/HelpPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/FeedbackPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/versions/GameListPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/account/AccountListPage.java
```

---

## 11. 验收清单

### 11.1 导航

- [ ] 全局 64px 侧栏点“其他”进入 `OtherPage`，不再出现 popup。
- [ ] 其他页左侧 200px 二级菜单，只有“支持”分类和帮助/反馈/关于；没有账户/实例/社区设置入口。
- [ ] 点“帮助/反馈/关于”在 OtherPage 内切换 tab，不离开页面。
- [ ] 全局侧栏 active：OtherPage 亮“其他”；账户/实例/设置页四个按钮都不亮。
- [ ] 第一阶段不因缺少账户/实例入口而阻断主页既有入口：账号 chip 仍能打开账户，启动卡 [管理] 仍能打开实例列表。

### 11.2 设置页

- [ ] 设置页左侧只剩：游戏、Java 管理、常规、外观、下载。
- [ ] 设置页不再有帮助/反馈/关于 tab。
- [ ] `hmcl://settings/feedback` 仍能打开其他页的反馈 tab（可在帮助/反馈文案里触发）。
- [ ] 设置页其他 tab 功能不受影响。

### 11.3 关于 LMCL

- [ ] 产品卡显示 `Lumina Minecraft Launcher`、`LMCL 3.16.1000`、`基于 HMCL v3.16.4`。
- [ ] 开发人员显示 `指尖的宇宙 kerze`，副标题“开发与维护”，无伪造链接。
- [ ] 致谢列表读取 `/assets/about/thanks.json`，**全部条目**与上游一致（个人开发者 + 社区伙伴都展示）；上游新增条目后重新构建即可出现。
- [ ] 捐赠者区域只有空态文案；`lmcl-donors.json` 为空数组时页面不报错。
- [ ] 页面没有“捐赠”按钮/入口/支付链接。
- [ ] 放入一条测试 donor（临时）能渲染；测试后清空。
- [ ] 官网链接可打开；源码/许可证为空时隐藏，不出现空 href。
- [ ] 更新检查按钮在未配置仓库时禁用；配置后点击不下载文件。
- [ ] 法律区包含 GPLv3/上游来源/修改声明。

### 11.4 UI/质量

- [ ] 1280×720、1920×1080 下 OtherPage 和 About 页可滚动、无横向溢出。
- [ ] 中文/英文 key 齐全，`checkTranslations` 无缺失/占位符不一致。
- [ ] 无 FX 线程阻塞 IO；`thanks.json`/donor 读取走 `Schedulers.io()`。
- [ ] 无新增 CSS 硬编码主题色；使用现有 `.card`/`.title-label`/`.subtitle-label`/`ComponentList`。
- [ ] `git diff --stat` 仅包含文件清单；上游侵入只多 `LauncherSettingsPage.java` 与 `Controllers.java` 两处。

---

## 12. 风险与未决问题

### 12.1 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 账户/实例/社区设置最终必须内嵌，但本步暂缓 | 第一阶段 OtherPage 不提供这些入口；若误做成跳转会被用户否决；第二阶段拆 Pane 工作量和侵入大增 | 第一阶段只做 help/feedback/about，账户/实例仍由主页入口；阶段 E 另立步骤并重估预算 |
| 帮助/反馈页依赖设置页生命周期 | tab 内加载/滚动异常 | OtherPage 实现 `PageAware`，`TabHeader` 代理 `onPageShown/Hidden` |
| 删除设置页 tab 破坏站内 feedback 链接 | 帮助/反馈里的链接失效 | `Controllers` 直接打开 OtherPage；删除 `LauncherSettingsPage.showFeedback()`；编译后搜 `showFeedback` |
| 社区面板 about 删除后仍有调用方 | NPE/空分组 | 全仓 grep `open(...\"about\")`、`buildAboutSection` |
| 致谢列表含组织/伙伴，不全是个人开发者 | 文案不准确 | 标题写“HMCL 项目与贡献者”，不写“开发者列表” |
| `thanks.json` 上游结构变化 | 解析失败、列表空 | 解析失败降级空列表 + WARNING，不崩溃 |
| 新图标缺失 | 产品卡仍显示 HMCL 图标 | 标记资源 TODO；正式发布前补 `/assets/img/lmcl.png` |
| `CommunitySideBar` 删除 popup 后 CSS/key 残留 | 死代码/翻译冗余 | 删除 `.community-sidebar-menu*` 与不再使用的 key |
| 单例 OtherPage 与导航栈 | 返回时状态异常 | 只在 FX 线程创建；导航复用；手工回归返回路径 |

### 12.2 已确认决定（m10063）

1. **账户/实例**：不接受“二级菜单点击后跳转到现有完整页面”；最终必须内嵌 tab，本阶段暂缓，不放入 OtherPage。
2. **社区设置**：最终必须内嵌 tab，本阶段暂缓；仍从主页 `[设置]` 打开现有对话框，其 about 组本步删除。
3. **致谢范围**：`thanks.json` 全部展示（个人开发者 + 社区伙伴），标题写“HMCL 项目与贡献者”。
4. **捐赠**：只保留空态/JSON 占位，**不加捐赠入口**。
5. **关于页图标**：继续用 `/assets/img/icon.png`，等用户提供 LMCL 图标后替换。

---

## 13. 批准后要改的文档

本方案本身不修改现有文档；批准并实现后：

- 新增 `docs/steps/22-其他页面与关于LMCL.md`（可由本方案转正）。
- `docs/00-整体要求.md`：
  - §4.2 代码侵入 7 → 9，新增 `LauncherSettingsPage.java`、`Controllers.java`；
  - §6.1/§6.2 排期加步骤 22（约 4 人日，0.8 周）；
  - §13 新增决策：帮助/反馈/关于统一迁移到其他页；第一阶段 OtherPage 仅三个 tab；账户/实例/社区设置内嵌延后；致谢读上游 `thanks.json` 全部展示；捐赠者 JSON 预留且无捐赠入口。
- `README.md`：版本、步骤总表、总工期、变更记录。
- `docs/steps/21-主页重构与侧边栏移除.md`：侧栏“其他”不再弹 menu，指向 OtherPage。
- `docs/审查记录-v3.16.4源码核对.md`：追加实现记录。
- `lmcl/docs-community/` 同步。

---

## 14. 结论

- 第一阶段把“其他”从 popup 升级为与下载/设置同级的独立页，二级菜单只承载帮助、反馈、关于 LMCL 三个 tab。
- 账户/实例、社区设置**不做点击跳转**；按用户决定暂缓，最终必须内嵌为 tab（阶段 E，需另立步骤并重估上游侵入）。
- “关于 LMCL”成为唯一关于入口：开发人员、HMCL 全部致谢（运行时读取上游 `thanks.json`）、捐赠者空态占位、法律与更新检查；不加捐赠入口，图标暂用上游图标。
- 设置页彻底移除帮助/反馈/关于；`LauncherSettingsPage.showFeedback()` 删除，`Controllers` 的 `hmcl://settings/feedback` 直接打开 OtherPage，站内链接不失效。
- 新增 2 处上游侵入：`LauncherSettingsPage.java`（第 8 处）、`Controllers.java`（第 9 处）；阶段 E 若拆 `GameListPage`/`AccountListPage`，需另计侵入。
- 当前**未改任何代码**；等待批准后按阶段 A→D 实施。

---

## 15. 实施记录（第一阶段，v3.8）

第一阶段已于 2026-10-22 按 m10129 实施并打包。详细文件/签收清单见[步骤 22](steps/22-其他页面与关于LMCL.md)，这里只记录与方案的差异：

### 15.1 已实现

- `OtherPage`：三个 tab（帮助/反馈/关于 LMCL），200 px `AdvancedListBox` 二级菜单，默认选中“关于 LMCL”。
- `AboutLMCLPage`：产品信息、开发人员“指尖的宇宙 kerze”、运行时读取上游 `thanks.json` 全部条目、捐赠者空态、更新检查、法律信息。
- 设置页删除帮助/反馈/关于三个 tab 与左侧支持分组；`LauncherSettingsPage.showFeedback()` 删除。
- `Controllers` 的 `hmcl://settings/feedback` 改为 `OtherPage.getInstance().showFeedback()` 后导航。
- `CommunitySideBar` 删除 `showOtherMenu()`/`JFXPopup`，`openOther()` 直接导航；`setActive` 用 `page instanceof OtherPage`。
- 清理 `.community-sidebar-menu*` 死 CSS、`community.sidebar.other.*`、`community.settings.about.*`、`community.settings.tab.about` 不再使用的 key。

### 15.2 与方案的偏差

1. 关于页致谢标题实际为“HMCL 贡献者致谢”，不是方案 §12.1 写的“HMCL 项目与贡献者”；含义相近，但把社区伙伴也归入贡献者。
2. 没有额外实现“关于页加载失败”的专用错误态；`AboutData` 对单资源失败降级为空列表 + WARNING。
3. 未实现远程头像异步加载；与上游 `AboutPage` 一致，当前 `thanks.json` 以内置资源为主。
4. GUI 未在 Windows 实机验证（WSL2 无 DISPLAY/Wine）。
5. 账户/实例/社区设置内嵌 tab 未开始；仍按用户决定暂缓。

### 15.3 验证与产物

- `:HMCL:compileJava`、`:HMCL:checkTranslations` 通过；community 测试 **135/0/0/0**（新增 `AboutDataTest` 3 个）。
- 使用交叉编译 stub 构建：jar **11,168,252 B**（`f9d599f6…`）、exe **12,369,404 B**（`3bde4127…`）、stub **1,201,152 B**（`89b58fa0…`）；`exe[:len(stub)]==stub`、`exe[len(stub):]==jar`。
- `other1` zip **64,896,960 B**，SHA-256 `6391112eba4774787464752e39d1cf060ba26997feb1bc73785df271f0a36ea0`；320 entries、`testzip=None`。
- 代码提交 `43d689abf`；文档与 `docs-community/` 已同步并提交，详见[审查记录 §34](审查记录-v3.16.4源码核对.md)。
