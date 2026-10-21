# 步骤 22 · 其他页与“关于 LMCL”（第一阶段）

> **阶段** 四（补丁） ｜ **优先级** P1 ｜ **工期** 0.5 周（约 4 人日） ｜ **前置** [21](21-主页重构与侧边栏移除.md) ｜ **后续** 阶段 E（账户/实例/社区设置内嵌，待另立步骤） ｜ **侵入点** `LauncherSettingsPage.java`（第 8 处）、`Controllers.java`（第 9 处）

## 1. 目标

把全局侧栏的“其他”从 `JFXPopup` 菜单升级为与下载、设置同级的独立页面；第一阶段只承载“帮助 / 反馈 / 关于 LMCL”三个 tab，并把设置页里的帮助、反馈、关于统一迁移过去。

完成标志：

1. 点击左侧 64 px 侧栏“其他”进入 `OtherPage`，不再弹菜单；
2. `OtherPage` 左侧 200 px 二级菜单按 HMCL 设置页样式分组，右 tab 内容可切换；
3. 设置页只剩游戏 / Java / 设置 / 个性化 / 下载 五个 tab，原帮助、反馈、关于入口不再存在；
4. `hmcl://settings/feedback` 等站内链接仍能打开反馈 tab，不出现死链；
5. “关于 LMCL”包含产品信息、开发人员“指尖的宇宙 kerze”、上游 HMCL 全部致谢条目、捐赠者空态、更新检查与法律信息；
6. 关于页读取 `thanks.json`、`lmcl-donors.json` 走 IO 线程，不阻塞 FX；
7. 编译、翻译检查与 community 测试通过；GUI 需在 Windows 实机补验。

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
| 第一阶段范围 | 只做帮助 / 反馈 / 关于 LMCL | 用户明确不接受“点击跳转完整页”，账户/实例/社区设置需先拆 Pane，暂缓 |
| 二级菜单 | 200 px `AdvancedListBox` + `TabHeader` | 与设置页/下载页一致，HMCL 原生视觉 |
| 关于页数据 | 运行时读上游 `/assets/about/thanks.json`，不复制名单 | 上游新增贡献者后重新构建即可同步；避免双份真相 |
| 捐赠者 | `assets/about/lmcl-donors.json` 空数组 + 空态文案，无按钮 | 用户要求“预留位置”，暂不加入口 |
| 图标 | 暂用 `/assets/img/icon.png` | 用户后续提供 LMCL 图标再替换 |
| 设置页 | 删除 help/feedback/about 三个 tab 和左侧支持分组 | 统一入口，避免两套关于页 |
| 站内反馈链接 | `Controllers` 直接 `OtherPage.getInstance().showFeedback()` 后导航 | 只改设置页会被第二次 `navigate(getSettingsPage())` 覆盖 |
| 账户/实例/社区设置 | 本阶段不做 | 最终必须内嵌 tab，需另立阶段 E |

## 3. 实现

### 3.1 `OtherPage`

`HMCL/src/main/java/org/jackhuang/hmcl/community/ui/OtherPage.java`

- `@NotNullByDefault public final class OtherPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware`；
- 单例 `getInstance()`，只在 FX 线程创建；
- 三个 `TabHeader.Tab`：`helpTab`（`HelpPage`）、`feedbackTab`（`FeedbackPage`）、`aboutTab`（`AboutLMCLPage`）；
- `tab.select(aboutTab)` 作为默认 tab；
- 左侧 `AdvancedListBox`：`startCategory("支持")`，三个 `addNavigationDrawerTab(...)`，`FXUtils.setLimitWidth(sideBar, 200)`；
- `setLeft(sideBar); setCenter(transitionPane);`
- `showHelp()/showFeedback()/showAbout()` 供站内链接/外部调用；
- `onPageShown()/onPageHidden()` 转发给 `TabHeader`，保证 tab 生命周期。

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

三份语言各新增 17 个 key：

```
community.other.title
community.other.category.support
community.other.tab.help / feedback / about
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
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutLMCLPage.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/about/AboutData.java`
- `HMCL/src/main/resources/assets/about/lmcl-donors.json`
- `HMCL/src/test/java/org/jackhuang/hmcl/community/ui/about/AboutDataTest.java`

修改：

- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySideBar.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java`
- `HMCL/src/main/java/org/jackhuang/hmcl/community/ui/CommunitySettingsPanel.java`（删除 about 分组及构造入口）
- 三份 `HMCL/src/main/resources/assets/lang/I18N*.properties`
- `HMCL/src/main/resources/assets/css/community.css`（删除已废弃 popup 菜单规则）

不改：`HelpPage`、`FeedbackPage`、`AboutPage`、`GameListPage`、`AccountListPage`。

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
| `HMCL/build/libs/HMCL-3.16.1000.jar` | 11,168,252 B | `f9d599f6de461910ac03200e28740b5e11413bcaffaac6419f57c5bedf41c8ff` |
| `HMCL/build/libs/HMCL-3.16.1000.exe` | 12,369,404 B | `3bde4127ee3c42eae26ea944faa2f5ba5af65df02391543299d3f3d532ef257b` |
| stub | 1,201,152 B | `89b58fa02a12363051ec05ae344527b27734787582f2049c8d953482aa22b617` |

断言：`exe[:len(stub)] == stub`、`exe[len(stub):] == jar`、`Main-Class=org.jackhuang.hmcl.community.CommunityBootstrap`。

测试包：`dist/LMCL-3.16.1000-win-x64-other1.zip`，64,896,960 B，SHA-256 `6391112eba4774787464752e39d1cf060ba26997feb1bc73785df271f0a36ea0`。

### 5.3 手工验收（Windows 实机，待补）

- [ ] 双击 `LMCL.exe` 启动，Java 查找正常；
- [ ] 左侧“其他”进入独立页，不弹菜单；主页/下载/设置 active 状态正确；
- [ ] 三个 tab 切换，帮助/反馈内容可滚动；
- [ ] 设置页只有五个 tab，默认 tab 正常；
- [ ] `hmcl://settings/feedback` 或帮助页内的反馈链接打开“其他 → 反馈”；
- [ ] 关于页：产品信息、开发人员、致谢全量、捐赠者空态、更新检查、法律信息；
- [ ] 1280×720 与 1920×1080 无横向溢出；
- [ ] 深色/浅色主题下头像与文字可读；
- [ ] 返回主页/下载/设置后再次进入“其他”，tab 状态正常。

## 6. 实现偏差与已知问题

1. 关于页致谢标题实际为“HMCL 贡献者致谢”，未使用方案里的“HMCL 项目与贡献者”；含义相近，但把社区伙伴也归入“贡献者”。
2. 未在 Windows 实机验证；WSL2 无 DISPLAY/Wine，因此 GUI 层全部未点验。
3. 图标仍为上游 `/assets/img/icon.png`；用户提供 LMCL 图标后需替换。
4. 账户、实例、社区设置的内嵌 tab 未实现；本阶段不提供点击跳转，入口仍由主页与既有页面承担。
5. `AboutLMCLPage` 的远程头像仍可能在 FX 线程 `new Image(url)`（与上游 `AboutPage` 相同）；当前 `thanks.json` 以内置资源为主，若未来出现远程头像需改为异步加载。
6. `CommunitySettingsPanel` 删除 about 分组后，社区设置对话框不再提供源码/许可证入口；关于信息统一在 `OtherPage`。

## 7. 风险与回退

| 风险 | 等级 | 应对 |
|---|---|---|
| 删除设置页 tab 破坏站内深链 | 中 | `Controllers` 同时改；编译后 grep `showFeedback`、`AboutPage`、`HelpPage`，当前仅 `OtherPage` 使用 |
| 关于页长列表导致渲染卡顿 | 低 | `ScrollPane` + 每次全量 `ComponentList`；上游 `AboutPage` 同规模已验证 |
| `thanks.json` 结构变化导致列表为空 | 低 | 解析失败降级空列表 + WARNING，不影响其它分组 |
| 远程头像阻塞 FX | 低 | 当前资源以内置路径为主；后续如需远程头像，改为 `Schedulers.io()` 预加载 |
| 设置页默认 tab 改变 | 低 | 仍默认 game；实机确认 |

回退：删除 `OtherPage`/`about` 包与 `lmcl-donors.json`，恢复 `CommunitySideBar.showOtherMenu()` 与设置页三个 tab，删除 `Controllers` 的反馈改道；代码侵入回到 7 处。测试包可回退到 `dist/LMCL-3.16.1000-win-x64-home3.zip`。

## 8. 关联

- [步骤 21](21-主页重构与侧边栏移除.md)：全局 64px 侧栏与 `HomePane`；
- [步骤 13 §3.10](13-首页组件化A-框架搭建.md)：`CommunitySettingsPanel` 扩展点（本步删除 about 分组）；
- [步骤 19](19-MD3主题完善.md)：`CommunityStyles`；本页使用 HMCL 原生组件风格，未强制挂 `community.css`；
- 方案：[修复方案-其他页面与关于LMCL.md](../修复方案-其他页面与关于LMCL.md)；
- 代码提交与验证记录见[审查记录 §34](../审查记录-v3.16.4源码核对.md)。
