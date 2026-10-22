# 步骤 22 · 其他页与“关于 LMCL”（v3.12 五 tab）

> **阶段** 四（补丁） ｜ **优先级** P1 ｜ **工期** 1 周（第一阶段 4 人日 + 阶段 E 约 2 人日） ｜ **前置** [21](21-主页重构与侧边栏移除.md) ｜ **后续** — ｜ **侵入点** `LauncherSettingsPage.java`（第 8 处）、`Controllers.java`（第 9 处）；阶段 E 无新增上游侵入
>
> **状态**：v3.12 五 tab（内存优化 / 设置导入导出 / 帮助 / 反馈 / 关于 LMCL）已实现并编译验证；v3.10 起账户、实例迁到步骤 23 的 `ManagementPage`，社区设置项删除。§3.1A 的账户、实例、社区设置内嵌为 v3.9 历史；v3.12 新增的第五 tab 见 §9。GUI 未在 Windows 实机验证。
>
> **v3.12 变更（2026-10-22）**：新增“设置导入 / 导出”tab（`ui/transfer/SettingsTransferPane`），对应 `.lmcl.json` 的 5 类 `SettingsSectionExporter`；`OtherPage` 左侧“工具”分类变为“内存优化 / 设置导入-导出”，“支持”分类不变；拖入识别仍挂在 `DecoratorController`（已有第 7 处侵入，无新增）。

## 1. 目标

把全局侧栏的“其他”从 `JFXPopup` 菜单升级为与下载、设置同级的独立页面；第一阶段只承载“帮助 / 反馈 / 关于 LMCL”三个 tab，并把设置页里的帮助、反馈、关于统一迁移过去。

完成标志：

1. 点击左侧 64 px 侧栏“其他”进入 `OtherPage`，不再弹菜单；
2. `OtherPage` 左侧 200 px 二级菜单按 HMCL 设置页样式分组，右 tab 内容可切换；
3. 设置页只剩游戏 / Java / 设置 / 个性化 / 下载 五个 tab，原帮助、反馈、关于入口不再存在；
4. `hmcl://settings/feedback` 等站内链接仍能打开反馈 tab，不出现死链；
5. “关于 LMCL”包含产品信息、开发人员“指尖的宇宙 kerze”、上游 HMCL 全部致谢条目、捐赠者空态、更新检查与法律信息；
6. 关于页读取 `thanks.json`、`lmcl-donors.json` 走 IO 线程，不阻塞 FX；
7. ~~六 tab 均可切换：账户、实例、社区设置、帮助、反馈、关于 LMCL；~~（v3.10 被步骤 23 取代：四 tab，账户/实例在管理页）
8. ~~账户/实例内嵌可操作列表~~（v3.10 迁到步骤 23 的管理页）；
9. ~~社区设置以 `CommunitySettingsPanel.createInlineView(...)` 内嵌~~（v3.10 移除社区设置项）；
10. 主页固定槽位移除 storage/memory，共 9 卡；
11. 编译、翻译检查与 community 测试通过；GUI 需在 Windows 实机补验。

## 2. 背景与用户决定

### 2.1 用户需求

User said (m09949)：

> 其他也单独做成一个页面，像下载和设置这样的，然后里面的内容再做在二级菜单里，关于lmcl这边写开发人员和致谢列表，开发人员写个指尖的宇宙kerze，致谢列表先写hmcl的开发者（从hmcl那边同步信息），再预留捐赠者的位置。设置里的帮助反馈和关于统一移到其他里面就好了，其他选项里的其他内容也用hmcl的视觉风格重构，先写md

User said (m10063)：

> 账户/实例不接受“二级菜单点击后跳转到现有完整页面”，但是这部分开发可以暂缓。社区设置要内嵌tab，但是可以暂缓。致谢列表全部展示，暂不加入捐赠入口。关于页等我做出来图标再替换

User said (m10129)：

> 第一阶段，开工，这一阶段做好先打包

### 2.2 决定

| 问题 | 决定 | 理由 |
|---|---|---|
| 第一阶段范围 | 帮助 / 反馈 / 关于 LMCL | 用户 m10129 批准先交付第一阶段 |
| 阶段 E（v3.9） | 账户、实例、社区设置内嵌为 tab；内嵌页隐藏上游 200px 左侧导航 | 用户 m10461 要求继续完成；用户不接受“点击跳转完整页” |
| 二级菜单 | 200 px `AdvancedListBox` + `TabHeader` | 与设置页/下载页一致，HMCL 原生视觉 |
| 关于页数据 | 运行时读上游 `/assets/about/thanks.json`，不复制名单 | 上游新增贡献者后重新构建即可同步；避免双份真相 |
| 捐赠者 | `assets/about/lmcl-donors.json` 空数组 + 空态文案，无按钮 | 用户要求“预留位置”，暂不加入口 |
| 图标 | 暂用 `/assets/img/icon.png` | 用户后续提供 LMCL 图标再替换 |
| 设置页 | 删除 help/feedback/about 三个 tab 和左侧支持分组 | 统一入口，避免两套关于页 |
| 站内反馈链接 | `Controllers` 直接 `OtherPage.getInstance().showFeedback()` 后导航 | 只改设置页会被第二次 `navigate(getSettingsPage())` 覆盖 |
| 账户/实例/社区设置 | v3.9 内嵌 tab | 见 §3.1A；`AccountListPage`/`GameListPage` 只嵌入，不修改上游源码 |

## 3. 实现

### 3.1 `OtherPage`（v3.12 现行结构）

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/OtherPage.java`

- `@NotNullByDefault public final class OtherPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware`；
- 单例 `getInstance()`，只在 FX 线程创建；
- 五个 `TabHeader.Tab`，顺序固定：
  1. `memoryTab`（id `communityMemoryOptimizationPage`，`MemoryOptimizationView`）
  2. `transferTab`（id `communitySettingsTransferPage`，`SettingsTransferPane`）
  3. `helpTab`（`HelpPage`）
  4. `feedbackTab`（`FeedbackPage`）
  5. `aboutTab`（`AboutLMCLPage`）
- `tab.select(memoryTab)` 作为默认 tab；
- 左侧 `AdvancedListBox`（200 px）两个分类：
  - 工具：内存优化（`SVG.TUNE`）、设置导入 / 导出（`SVG.SAVE`）；
  - 支持：帮助、反馈、关于 LMCL；
- `FXUtils.setLimitWidth(sideBar, 200); setLeft(sideBar); setCenter(transitionPane);`
- `showMemoryOptimization()/showSettingsTransfer()/showHelp()/showFeedback()/showAbout()` 供侧栏、站内链接与主页调用；
- `onPageShown()/onPageHidden()` 转发给 `TabHeader`，保证 tab 生命周期。

> v3.9 的六 tab（账户/实例/社区设置/帮助/反馈/关于）与 `showAccounts()/showInstances()/showCommunitySettings()` 已删除；账户、实例入口现在只在 `ManagementPage`（步骤 23）。v3.12 的 transfer tab 细节见 §9。

### 3.1A 内嵌账户、实例、社区设置（v3.9 阶段 E）

#### `AccountsPane`

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/AccountsPane.java`

- `public final class AccountsPane extends VBox`；`private final AccountListPage page = new AccountListPage();`
- 隐藏上游页面自带导航：`page.getLeft().setManaged(false); page.getLeft().setVisible(false);`；列表填满：`page.setMaxSize(MAX_VALUE, MAX_VALUE); VBox.setVgrow(page, ALWAYS);`
- 绑定上游模型：`page.accountsProperty().bindContent(Accounts.getAccounts())`、`page.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty())`、`page.authServersProperty().bindContentBidirectional(SettingsManager.getAuthlibInjectorServers())`；
- 顶部工具栏（`FXUtils.newBorderButton`）：Microsoft 登录、离线登录、认证服务器 `MenuButton`；
- 认证服务器菜单在 `setOnShowing` 时重建：每个服务器对应 `CreateAccountPane(server)`，分隔线，`account.injector.add` 打开 `AddAuthlibInjectorServerPane`；
- 写保护：账户只读时走 `Controllers.confirmBackupAndOverwrite`；认证服务器只读时提示 `account.injector.readonly`。

#### `InstancesPane`

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/InstancesPane.java`

- `public final class InstancesPane extends VBox`；`private final GameListPage page = new GameListPage();`
- 同样隐藏 `page.getLeft()`（游戏目录列表）并让 `GameList` 填满；
- 顶部工具栏四个按钮：
  - `install.new_game` → `Versions::addNewGame`；
  - `install.modpack` → `Versions::importModpack`；
  - `game_directory.manage` → `Controllers.navigate(new GameDirectoryPage(null))`；
  - `settings.type.global.manage` → `page::modifyGlobalGameSettings`。

#### `CommunitySettingsView`

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsView.java`

- `public final class CommunitySettingsView extends VBox`；
- 标题 `community.settings.title` + `CommunitySettingsPanel.createInlineView(null)`；
- `VBox.setVgrow(body, ALWAYS)`；不包 `DialogPane`，不弹窗。

#### 支撑改动

`CommunitySettingsPanel` 把原 `buildRoot` 的分组切换区抽成 `buildBody(List<SettingsSection>, @Nullable String initialSectionId)`，新增：

```java
public static Parent createInlineView(@Nullable String sectionId)
```

`createInlineView` 只在 FX 线程调用，执行 `collectSections()`、`CommunityStyles.apply(root)`、`buildBody`，返回可直接嵌入的 `HBox`。

### 3.2 “关于 LMCL”

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutLMCLPage.java`

内容顺序固定：

1. 产品信息：图标 `/assets/img/icon.png`、`CommunityMetadata.fullName()`、`version_line`（版本 + 上游基线）；官网链接可用时使用 `LineButton.createExternalLinkButton`；
2. 开发人员：`指尖的宇宙 kerze`，副标题“开发与维护”；
3. 上游致谢：`AboutData.load()` 读取 `thanks.json`，**全部条目**按原顺序展示；头像支持字符串与 `{light,dark}` 两种格式；有 `externalLink` 的条目点击 `FXUtils.openLink`；
4. 捐赠者：读取 `lmcl-donors.json`；空数组显示 `community.about.donors.empty`；**没有捐赠按钮/入口**；
5. 更新检查：`CommunityUpdateNotifier.isConfigured()` 为 false 时按钮禁用并显示 `community.update.not_configured`；否则 `checkAsync()`，有新版本弹 `CommunityUpdateDialog`，无新版本 `Controllers.dialog(community.update.latest)`；不下载、不替换文件；
6. 法律信息：`about.copyright` + `community.about.copyright.statement`、`about.claim`、上游 GitHub 链接。

线程：构造函数 `showSpinner()`，`Schedulers.io().execute(() -> AboutData.load())`，回 `Schedulers.javafx()` 渲染；异常由 `AboutData` 内部降级为空列表并 WARNING。

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutData.java`

- 常量 `THANKS_RESOURCE = "/assets/about/thanks.json"`、`DONORS_RESOURCE = "/assets/about/lmcl-donors.json"`；
- `record Entry(@Nullable JsonElement image, @Nullable String title, @Nullable String titleLocalized, @Nullable String subtitle, @Nullable String subtitleLocalized, @Nullable String externalLink)`；
- `record Donor(@NotNull String name, @Nullable String note, @Nullable String link)`；
- `load()`：两个资源独立读取，单个失败只返回空列表；
- `parseThanks(JsonArray)` / `parseDonors(JsonArray)` 包级可见，供单测；
- 跳过非对象、无标题致谢、空名称捐赠者。

资源：`HMCL/src/main/resources/assets/about/lmcl-donors.json`，内容 `[]`。

### 3.3 设置页迁移（第 8 处侵入）

`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`

- 删除 `helpTab`、`feedbackTab`、`aboutTab` 字段与 `HelpPage/FeedbackPage/AboutPage` 的 supplier；
- `TabHeader` 只保留 game/java/settings/personalization/download 五个 tab；
- 删除左侧“支持”分类及帮助/反馈/关于三项；
- 删除 `showFeedback()`；
- 保留 `getTab()` 等既有外部 API 的其余行为。

### 3.4 站内反馈链接（第 9 处侵入）

`HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java`

原 `hmcl://settings/feedback` 分支：

```java
getSettingsPage().showFeedback();
navigate(getSettingsPage());
```

改为：

```java
OtherPage.getInstance().showFeedback();
navigate(OtherPage.getInstance());
```

保留 `hmcl://settings/*` 其它分支不变。

### 3.5 侧栏

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java`

- 删除 `showOtherMenu()`、`JFXPopup`、菜单内容与相关 import；
- `otherButton` 动作改为 `openOther()`：`Controllers.navigate(OtherPage.getInstance())`，异常只 WARNING；
- `setActive(Node page)`：`other = page instanceof OtherPage`，不再用“非主页/下载/设置即其他”的兜底判断；
- 删除 `.community-sidebar-menu*` 死 CSS 与 `community.sidebar.other.*`、`community.settings.about.*`、`community.settings.tab.about` 不再使用的 key。

### 3.6 i18n

三份语言各新增 21 个 key：

```
community.other.title
community.other.category.support
community.other.tab.help / feedback / about
community.other.category.manage
community.other.tab.accounts / instances / community
community.about.title
community.about.version_line
community.about.developers
community.about.developer.role
community.about.thanks
community.about.thanks.empty
community.about.donors
community.about.donors.empty
community.about.update
community.about.copyright.statement
community.about.upstream
community.about.upstream.statement
```

英文基座 `I18N.properties`、繁中 `I18N_zh.properties`、简中 `I18N_zh_CN.properties` 同步；`CheckTranslations` 要求三份 key 齐全且占位符一致。

## 4. 文件清单

新增：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/OtherPage.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/AccountsPane.java`（v3.9）
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/InstancesPane.java`（v3.9）
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsView.java`（v3.9）
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutLMCLPage.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutData.java`
- `HMCL/src/main/resources/assets/about/lmcl-donors.json`
- `HMCL/src/test/java/org/jackhuang/hmcl/community/ui/about/AboutDataTest.java`

修改：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsPanel.java`（删除 about 分组；v3.9 抽出 `buildBody` 并新增 `createInlineView`）
- `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/HomePane.java`（v3.9：移除 storage 槽位；账户卡打开 `OtherPage.showAccounts()`）
- `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/CommunityWidgetsProvider.java`（v3.9：注销 storage，9 卡）
- 删除 `HMCL/src/main/java/org/jackhuang/hmcl/community/dashboard/builtin/StorageWidget.java`、`MemoryWidget.java`
- 三份 `HMCL/src/main/resources/assets/lang/I18N*.properties`
- `HMCL/src/main/resources/assets/css/community.css`（删除已废弃 popup 菜单规则）

不改：`HelpPage`、`FeedbackPage`、`AboutPage`、`GameListPage`、`AccountListPage`（内嵌只隐藏 left，不修改源码）。

## 5. 验证

### 5.1 自动验证

```bash
export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11
export GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home
./gradlew :HMCL:compileJava :HMCL:checkTranslations --no-daemon
./gradlew :HMCL:test --tests 'org.jackhuang.hmcl.community.*' --no-daemon
```

结果：

- `compileJava` 通过（仅 3 条既有 JavaFX `--add-exports` 警告）；
- `checkTranslations` 通过；
- community 测试 **135 / 0 failures / 0 errors / 0 skipped**（新增 `AboutDataTest` 3 个）。

### 5.2 构建与包

```bash
VERSION_ROOT=3.16 BUILD_NUMBER=1000 \
HMCL_LAUNCHER_EXE=/home/kerze/hmclce/.tools/stub-build/HMCLauncher.exe \
./gradlew :HMCL:makeExecutables --no-daemon
```

产物：

| 文件 | 大小 | SHA-256 |
|---|---|---|
| `HMCL/build/libs/HMCL-3.16.1000.jar` | 11,165,823 B | `433f4fdae5d2e7bc5a5c11791a77e3cd4ac64fea06ffcdd37add0de8579c02d2` |
| `HMCL/build/libs/HMCL-3.16.1000.exe` | 12,366,975 B | `4a34eef1411914137b932c136c691b317ad7479043402b02e7a3a32e5a24cdbd` |
| stub | 1,201,152 B | `89b58fa02a12363051ec05ae344527b27734787582f2049c8d953482aa22b617` |

断言：`exe[:len(stub)] == stub`、`exe[len(stub):] == jar`、`Main-Class=org.jackhuang.hmcl.community.CommunityBootstrap`。

测试包：`dist/LMCL-3.16.1000-win-x64-other2.zip`，65,157,579 B，SHA-256 `f66c993f2a4d40e84d9511060fca876bb59e6feb1a7efe71235d14587d7b9c88`；旧 `other1` 已删除。

### 5.3 手工验收（Windows 实机，待补）

- [ ] 双击 `LMCL.exe` 启动，Java 查找正常；
- [ ] 左侧“其他”进入独立页，不弹菜单；主页/下载/设置 active 状态正确；
- [ ] v3.9 六 tab 切换（历史；v3.10 起账户/实例迁到管理页，v3.11 起 OtherPage 仅保留内存优化/帮助/反馈/关于）；账户列表可添加 Microsoft/离线账户，认证服务器菜单可打开；
- [ ] 实例页可切换游戏目录、新建实例、导入整合包、打开全局游戏设置；
- [ ] 内嵌账户/实例页在 1280×720 与 1920×1080 下无横向溢出，隐藏的 200px 导航不占位；
- [ ] 社区设置 tab 内嵌显示 dashboard/data/download/java/memory 分组，不弹独立对话框；
- [ ] v3.9 时设置页五个 tab（历史；v3.11 起 Java 管理迁到管理页，设置页只剩游戏/设置/个性化/下载），默认 tab 正常；
- [ ] `hmcl://settings/feedback` 或帮助页内的反馈链接打开“其他 → 反馈”；
- [ ] 关于页：产品信息、开发人员、致谢全量、捐赠者空态、更新检查、法律信息；
- [ ] 主页只有 9 卡，无 storage/memory；账户卡点击打开“其他 → 账户”；
- [ ] 深色/浅色主题下头像与文字可读；
- [ ] 返回主页/下载/设置后再次进入“其他”，tab 状态正常。

## 6A. v3.9 阶段 E 实现记录（2026-10-22，m10461）

### 6A.1 交付

- `OtherPage` 六 tab：账户、实例、社区设置、帮助、反馈、关于 LMCL；默认账户；200 px 菜单分“管理/支持”。
- `AccountsPane`：内嵌 `AccountListPage`（隐藏 left），绑定账户/选中账户/认证服务器；工具栏 Microsoft/离线/认证服务器菜单。
- `InstancesPane`：内嵌 `GameListPage`（隐藏 left）；工具栏新建实例/导入整合包/游戏目录/全局游戏设置。
- `CommunitySettingsView`：内嵌 `CommunitySettingsPanel.createInlineView(null)`。
- `CommunitySettingsPanel`：抽出 `buildBody(List<SettingsSection>, @Nullable String)`，`buildRoot` 与 `createInlineView` 共用。
- 主页：`HomePane` 删除 storage 槽位（9 卡），首页账户卡打开 `OtherPage.showAccounts()`；`CommunityWidgetsProvider` 注销 storage；删除 `StorageWidget.java`/`MemoryWidget.java`。
- i18n：三份各新增 `community.other.category.manage`、`community.other.tab.accounts/instances/community`。

### 6A.2 验证

- `:HMCL:compileJava` 通过（仅 3 条既有 JavaFX `--add-exports` 警告）；
- `:HMCL:checkTranslations` 通过；
- community 测试 **135 / 0 failures / 0 errors / 0 skipped**；
- `:HMCL:makeExecutables` 成功：jar **11,165,823 B**（`433f4fd…`）、exe **12,366,975 B**（`4a34eef1…`）、stub **1,201,152 B**（`89b58fa0…`）；`exe[:len(stub)]==stub`、`exe[len(stub):]==jar`；
- `other2` zip **65,157,579 B**，SHA-256 `f66c993f…`；320 entries、`testzip=None`。

### 6A.3 偏差

1. `AccountListPage`/`GameListPage` 未改源码，只隐藏其 `left`；依赖上游 private skin 与固定 center 结构，上游升级需重新验证。
2. 实例页目录菜单在 `selectedRepositoryProperty` 变化时重建；新建目录后需重新打开菜单刷新。
3. 账户页认证服务器菜单每次 `setOnShowing` 重建，未做搜索/分组。
4. 独立设置对话框 `CommunitySettingsPanel.open(...)` 仍保留，供侧栏齿轮/下载源按钮使用；内嵌视图与其共享 `buildBody`。
5. GUI 未在 Windows 实机验证。

## 6B. v3.12 设置导入 / 导出（第五 tab）

### 6B.1 目标与边界

- 把“可迁移的启动器设置”打包成一个 `.lmcl.json` 文件，支持其他页面的“设置导入 / 导出”tab 与拖入窗口两条入口；
- 首批固定 5 类：`downloadSources`（下载源）、`appearance`（外观）、`home`（主页布局）、`memory`（内存优化）、`java`（Java 偏好）；
- 不包含：账号、实例/世界、日志、缓存、背景图片文件、更新通道、绝对路径；这些要么涉及隐私，要么与当前机器绑定；
- 导入是“合并/覆盖当前设置”，不是恢复出厂；应用后不回滚已成功的段，失败逐段显示；
- 0 新增上游侵入：拖入识别复用 `DecoratorController`（第 7 处），导出入口在 `OtherPage`（已有）。

### 6B.2 文件格式

```json
{
  "format": "lmcl.settings",
  "version": 1,
  "product": "LMCL",
  "productVersion": "3.16.1000",
  "exportedAt": "2026-10-22T05:00:00Z",
  "sections": {
    "downloadSources": {
      "schema": 2,
      "sources": [
        { "name": "BMCLAPI", "url": "https://bmclapi2.bangbang93.com" },
        { "name": "BMCLAPI 主控", "url": "https://openbmclapi.bangbang93.com" },
        { "name": "BMCLAPI 备用", "url": "https://bmclapi.bangbang93.com" }
      ]
    }
  }
}
```

- `CommunitySettingsBundle.FORMAT = "lmcl.settings"`、`VERSION = 1`、`FILE_SUFFIX = ".lmcl.json"`、`MAX_BYTES = 2 MiB`、时间字段 `exportedAt`（ISO-8601）；
- `sections` 只允许已注册的 5 个 id；未知段保留在内存中但只记 `warnings`，不参与导入；
- 没有任何受支持段时抛 `JsonParseException`；`format` 不匹配或 `version > 1` 同样拒绝；
- 写文件使用 UTF-8、原子写；导出文件名 `LMCL-settings-<yyyyMMdd-HHmmss>.lmcl.json`（`SettingsExportService.suggestedFileName()`）。

### 6B.3 服务 API

`HMCL/src/main/java/org/jackhuang/hmcl/community/config/`：

| 类型 | 关键签名 | 说明 |
|---|---|---|
| `CommunitySettingsBundle` | `read(Path)`、`parse(String)`、`create(Map<String,JsonObject>)`、`toJson()`、`write(Path)`、`sections()`、`warnings()` | 格式与解析；`read` 检查后缀/大小 |
| `SettingsSectionExporter` | `id()`、`titleKey()`、`exportNow()`、`preview(JsonObject)`（默认字段级 diff）、`apply(JsonObject)` | 每个实现只在 FX 线程调用 |
| `SettingsChange` | `record(String section, String key, String oldValue, String newValue)` + `display(JsonElement)` | 预览行；`display` 对 `null` 返回空串 |
| `SettingsExportService` | `exporters()`、`find(String)`、`export(Set<String>)`、`write(Path, Set<String>)`、`suggestedFileName()` | 固定 5 个导出器，顺序即应用顺序 |
| `SettingsImportService` | `preview(bundle)`、`apply(bundle)` | 逐段 try/catch；失败不中断后续段 |
| `SettingsImportService.ImportPreview` | `record(int sectionCount, List<SettingsChange> changes, List<String> warnings, List<String> errors)` | 预览 |
| `SettingsImportService.ImportResult` | `record(List<String> applied, List<SectionFailure> failures)` + `success()` | 应用结果 |
| `CommunityConfigDnD` | `install(...)` | 只识别 `.lmcl.json` 后缀，`TransferMode.COPY` |

导出器实现位于 `config/section/`：

- `DownloadSourcesSectionExporter`：解析 → `save()` → `CommunityDownloadInstaller.apply()`（触发一次测速，完成后按延迟重排候选）；导入后立即生效，无需重启；段非法时整体拒绝，保留旧配置；v3.13 起 schema 2 `sources[]`，v1 `primary`+`mirrors` 仍可导入并迁移；
- `AppearanceSectionExporter`：19 个白名单键（`language`、`selectedTheme`、`themeAppearanceOverrides`、`customThemeColor`、`themeBrightnessMode`、`builtinBackgroundId`、`logFontFamily`、`launcherFontFamily`、`titleBarTransparent`、`windowTransparent`、`animationDisabled`、`backgroundOpacity`、`logFontSize`、`themeColorType`、`themeColorStyle`、`backgroundType`、`networkBackgroundImageCachePolicy`、`backgroundFallbackType`、`backgroundLoadPolicy`）；先完整解析，任一键非法则不写入（全有或全无）；
- `HomeSectionExporter`：`DashboardConfig.load(Metadata.HMCL_LOCAL_HOME/config/dashboard.json)` → 只读则拒绝 → `replaceFromJson` → `save()` → `HomePane.notifyConfigurationChanged()`；
- `MemorySectionExporter`：`MemoryBaseSettings` + 默认 `GameSettings.Preset` 的 `memoryOptimizationOnLaunchProperty()`；
- `JavaSectionExporter`：`JavaPreference.get()/set()`。

### 6B.4 UI

- `ui/transfer/SettingsTransferPane`（`extends VBox`）：标题、说明、5 个 `CheckBox`（默认勾选下载源/外观/主页，其余不勾）、[全选]/[全不选]、[导入…]/[导出…]、状态 label；
- 导出：`FileChooser`（`*.lmcl.json`）→ `Schedulers.io()` 写盘 → 回 FX 显示“已导出 N 类设置”；
- 导入：`FileChooser` → `Schedulers.io()` 读文件 + `SettingsImportService.preview` → 回 FX `SettingsImportDialog`；
- `SettingsImportDialog extends DialogPane`（宽 680）：段数、变更摘要（最多 50 条）、警告、预览错误；`[导入]` 调 `apply`，失败列出 `sectionId: message`；`[取消]` 不写任何设置；
- 拖入：任意页面把 `.lmcl.json` 拖到窗口 → 同一个 `SettingsImportDialog`；非该后缀不接收，不干扰 authlib 拖入；
- i18n：`community.settings.transfer.*` 共 23 个 key（标题/说明/5 类/按钮/状态/预览/错误），三份语言文件一致。

### 6B.5 安全与降级

- 只接受 `.lmcl.json` 后缀且 ≤2 MiB，解析失败不应用任何段；
- 不写可执行文件、不写绝对路径、不写背景图片路径、不写账号令牌；
- 单段失败继续后续段，但 UI 必须显示失败段；退出码不因此中断启动器；
- 主页布局 `schema > 1` 时拒绝导入并提示版本过新；下载源段为空表示“零内置”，导入后仍由守卫提示配置；
- 外观应用后部分控件立即生效，标题栏/窗口透明等下次启动生效，界面需提示“部分设置需重启”。

### 6B.6 文件清单

新增：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/config/CommunitySettingsBundle.java`
- `.../config/SettingsSectionExporter.java`
- `.../config/SettingsChange.java`
- `.../config/SettingsExportService.java`
- `.../config/SettingsImportService.java`
- `.../config/CommunityConfigDnD.java`
- `.../config/section/DownloadSourcesSectionExporter.java`
- `.../config/section/AppearanceSectionExporter.java`
- `.../config/section/HomeSectionExporter.java`
- `.../config/section/MemorySectionExporter.java`
- `.../config/section/JavaSectionExporter.java`
- `.../ui/transfer/SettingsTransferPane.java`
- `.../ui/transfer/SettingsImportDialog.java`
- `HMCL/src/test/java/org/jackhuang/hmcl/community/config/CommunitySettingsBundleTest.java`（5 个）

修改：

- `.../community/ui/OtherPage.java`（第五 tab 与工具分类）
- `.../community/dashboard/DashboardConfig.java`（`toExportJson()`、`replaceFromJson()`）
- `.../community/java/JavaPreference.java`（`set(Preference)`）
- `.../community/ui/CommunitySideBar.java`（构造时 `CommunityDownloadInstaller.install()`）
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorController.java`（拖入识别，复用第 7 处）
- `HMCL/src/test/java/.../dashboard/DashboardConfigTest.java`（新增 2 个测试）
- 三份 `HMCL/src/main/resources/assets/lang/I18N*.properties`
- `docs-community` 同步、README/00/审查记录

### 6B.7 验证与偏差

- `:HMCL:compileJava`、`:HMCL:checkTranslations` 通过；community **143/0/0/0**，全量 `:HMCL:test` **253/0/0/2 skipped**；
- `settings2` 包内附 `LMCL-download-source-sample.lmcl.json`（schema 2，BMCLAPI / openbmclapi / bmclapi 备用共 3 个源），手工验收步骤见[步骤 07 §5.2](07-下载源配置.md)；
- 未做：GUI 实机（WSL2 无 DISPLAY）、跨机器导入、导出后重新导入的完整回环、`CommunityConfigDnD` 单测；
- 偏差：`SettingsImportDialog` 最多显示 50 条变更，不显示逐段勾选；导入按文件中的段全量应用，不支持只导入某一段（导出支持选择）；
- 注意：`CommunitySettings` 是独立文件，不是上游 `LauncherSettings`，导入不会触碰账号/上游设置。

## 6. 实现偏差与已知问题

1. 关于页致谢标题实际为“HMCL 贡献者致谢”，未使用方案里的“HMCL 项目与贡献者”；含义相近，但把社区伙伴也归入“贡献者”。
2. 未在 Windows 实机验证；WSL2 无 DISPLAY/Wine，因此 GUI 层全部未点验。
3. 图标仍为上游 `/assets/img/icon.png`；用户提供 LMCL 图标后需替换。
4. 账户、实例、社区设置的内嵌 tab 已在 v3.9 实现；`AccountListPage`/`GameListPage` 未修改源码，内嵌时只隐藏其 left 导航。
5. 内嵌页依赖上游页面的私有 skin 与构造行为；上游升级若把 left 改为懒创建或改变 center 结构，需重新验证（当前基线 v3.16.4）。
6. 实例页的目录切换菜单在 `GameDirectoryManager.selectedRepositoryProperty()` 变化时重建；新建目录后需重新打开菜单才刷新。
7. `AboutLMCLPage` 的远程头像仍可能在 FX 线程 `new Image(url)`（与上游 `AboutPage` 相同）；当前 `thanks.json` 以内置资源为主，若未来出现远程头像需改为异步加载。
8. `CommunitySettingsPanel` 删除 about 分组后，社区设置对话框不再提供源码/许可证入口；关于信息统一在 `OtherPage`。

## 7. 风险与回退

| 风险 | 等级 | 应对 |
|---|---|---|
| 删除设置页 tab 破坏站内深链 | 中 | `Controllers` 同时改；编译后 grep `showFeedback`、`AboutPage`、`HelpPage`，当前仅 `OtherPage` 使用 |
| 关于页长列表导致渲染卡顿 | 低 | `ScrollPane` + 每次全量 `ComponentList`；上游 `AboutPage` 同规模已验证 |
| `thanks.json` 结构变化导致列表为空 | 低 | 解析失败降级空列表 + WARNING，不影响其它分组 |
| 远程头像阻塞 FX | 低 | 当前资源以内置路径为主；后续如需远程头像，改为 `Schedulers.io()` 预加载 |
| 设置页默认 tab 改变 | 低 | 仍默认 game；实机确认 |

回退：删除 `OtherPage`/`about` 包、`AccountsPane`/`InstancesPane`/`CommunitySettingsView` 与 `lmcl-donors.json`，恢复 `CommunitySideBar.showOtherMenu()` 与设置页三个 tab，删除 `Controllers` 的反馈改道；代码侵入回到 7 处。测试包可回退到 `dist/LMCL-3.16.1000-win-x64-home3.zip`。

## 8. 关联

- [步骤 21](21-主页重构与侧边栏移除.md)：全局 64px 侧栏与 `HomePane`；
- [步骤 13 §3.10](13-首页组件化A-框架搭建.md)：`CommunitySettingsPanel` 扩展点（本步删除 about 分组）；
- [步骤 19](19-MD3主题完善.md)：`CommunityStyles`；本页使用 HMCL 原生组件风格，未强制挂 `community.css`；
- 方案：[修复方案-其他页面与关于LMCL.md](../修复方案-其他页面与关于LMCL.md)；
- 代码提交与验证记录见[审查记录 §34/§35](../审查记录-v3.16.4源码核对.md)。
