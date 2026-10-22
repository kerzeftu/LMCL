# Lumina Minecraft Launcher（LMCL） · 开发文档

> 基于 [HMCL](https://github.com/HMCL-dev/HMCL)（GPLv3）的社区版 Minecraft 启动器开发文档库。
> 面向中国大陆用户，独立产品身份；核心差异化是**启动保障**（能不能玩上）与**性能优化向导**（玩得顺不顺）。
>
> **文档版本 v3.16.7（2026-10-22）**：基线 **v3.16.4（release/3.16，commit `fbcc1747`）**。已按源码实读重写架构边界与排期，并完成两轮源码核对；HMCLauncher 源码与发布二进制的实测结论见[步骤 20 §2.3](docs/steps/20-HMCLauncher启动壳重建.md) 与[审查记录 §9](docs/审查记录-v3.16.4源码核对.md)。**v2.0 起步骤 07 改为“默认不内置镜像 + 自定义主源/≤5 镜像”**（用户 m03806），见[步骤 07](docs/steps/07-下载源配置.md)；**v2.1 起步骤 08 已实现**（启动钩子契约 + 五项体检 + 体检对话框），实现记录见[步骤 08 §10](docs/steps/08-启动保障-启动前体检.md)；**v2.2 起步骤 09 已实现**（崩溃现场归档 + 历史索引 + 诊断导出面板），实现记录见[步骤 09 §10](docs/steps/09-启动保障-崩溃现场还原.md)；**v2.3 起步骤 10 已实现**（Java 发行版偏好 + 推荐面板 + 设置分组；镜像未部署时按官方 API 运行），实现记录见[步骤 10 §10](docs/steps/10-性能策略-Java发行版默认偏好.md)；**v2.4 起步骤 11 已实现**（JVM 档位向导 + 冲突检测 + 唯一写入口 + 状态存储），实现记录见[步骤 11 §10](docs/steps/11-性能策略-JVM参数预设向导.md)；**v2.5 起步骤 12 已实现**（跨平台内存回收基座 + 设置面板「内存」分组），实现记录见[步骤 12 §10](docs/steps/12-内存优化P0-跨平台基座.md)；**v2.6 起步骤 14 已实现**（六张 P0 卡片 + WidgetProvider 注册 + 启动失败事件修复），实现记录见[步骤 14 §13](docs/steps/14-首页组件化B-P0组件与接入.md)；**v2.7 起步骤 15 已实现**（最近存档/存储/游玩日历/体检四卡 + 游玩打点数据层），实现记录见[步骤 15 §10](docs/steps/15-首页组件化C-P1P2组件与打磨.md)；**v2.8 起步骤 16 已实现**（成功启动自动快照 + 内容寻址对象库 + manifest-only 降级 + 回滚前 pre-restore + 首页快照卡/管理/对比），实现记录见[步骤 16 §10](docs/steps/16-启动保障-实例快照回滚.md)。**v2.9 修复首个 Windows 测试包的启动误迁移**：`CommunityBootstrap` 不再调用 `Main.main`，改为 `checkDirectoryPath() → EntryPoint.main`，消除 `UpdateHandler.isNestedApplication()` 误判导致的 `Signature is missing`，见[步骤 05 §4.1](docs/steps/05-禁用自动更新与更新提示.md)。**v3.0 接管上游“设置 → 下载”页**：`DownloadSettingsPage` 的三档选择器替换为社区主源/镜像编辑器（第 4 处代码侵入，产品 2026-10-22 批准），插件源保留，见[步骤 07](docs/steps/07-下载源配置.md) 与[00 §13 #21](docs/00-整体要求.md#13-已定决策记录)。**v3.1 主页重构**：移除 `RootPage` 的 200 px 文本侧栏，`HomePane` 以顶部导航 + 主列/右栏接管首页（第 5/6 处代码侵入，用户 m07850 批准），见[步骤 21](docs/steps/21-主页重构与侧边栏移除.md)。**v3.2 视觉收口**：新增 `community.css` / `CommunityStyles`，11 个社区根节点接入 5 档类型、状态 pill 与日历 5 档色阶，0 上游 CSS 侵入，见[步骤 19 §10](docs/steps/19-MD3主题完善.md)。**v3.3 交互修复与主页改版**：修复公告空壳吞事件/仪表盘置底/卡片裁切；新增全局 64px 图标侧栏（主页/下载/设置/其他），主页改为左账号·游玩信息栏 + 右实例栏并整页滚动；`DecoratorController` 为第 7 处代码侵入，见[步骤 21 §10](docs/steps/21-主页重构与侧边栏移除.md)。**v3.4 步骤 20 源码 fork**：HMCLauncher 源码本地 fork 完成品牌资源/版本/数据目录/CI，并用 MinGW i686 交叉构建出 1,201,152 B 新 stub（SHA-256 `89b58fa0…`）；`home4-stub` 仅供测试，Windows 实机与 10 项行为验收待执行，见[步骤 20 §7](docs/steps/20-HMCLauncher启动壳重建.md)。**v3.5 主页视觉/布局**：纯色 surface 背景、内容上下贴边、滚动条最右、右下启动按钮保持最上层，移除“启动器内存”组件卡；换用交叉编译 stub 的 `home5` 测试包，见[步骤 21 §11](docs/steps/21-主页重构与侧边栏移除.md)。**v3.6 左右独立滚动与黑底修复**：`HomePane` 拆成左右两个独立 `ScrollPane`，回退纯色背景/透明 viewport 样式，`MainPage` 去掉 20px padding；`home6` 测试包，见[步骤 21 §12](docs/steps/21-主页重构与侧边栏移除.md)。**v3.7 侧栏菜单对比度与主页留白**：左侧“其他”弹出菜单改为显式深色背景 + 白色文字（`CommunitySideBar` 给 popup 根节点挂社区样式表，CSS 用字面色，item 再设内联白色兜底）；`HomePane` 四周加 10/12/12/12 px 留白；`home7` 测试包，见[步骤 21 §13](docs/steps/21-主页重构与侧边栏移除.md)。**v3.8 其他页与关于 LMCL（第一阶段）**：全局侧栏“其他”由 popup 改为独立 `OtherPage`（200px 二级菜单：帮助/反馈/关于 LMCL）；设置页删除帮助/反馈/关于三个 tab；`AboutLMCLPage` 运行时读取上游 `thanks.json` 全量致谢，开发人员“指尖的宇宙 kerze”，捐赠者空态且无入口；`LauncherSettingsPage`/`Controllers` 为第 8/9 处代码侵入；`other1` 测试包，见[步骤 22](docs/steps/22-其他页面与关于LMCL.md)。**v3.9 阶段 E**：`OtherPage` 扩为账户/实例/社区设置/帮助/反馈/关于六 tab，账户与实例内嵌可操作列表并隐藏上游 200px 左侧导航，社区设置内嵌；主页移除 storage/memory 两卡（9 卡）；`other2` 测试包，见[步骤 22 §6A](docs/steps/22-其他页面与关于LMCL.md)。**v3.10 管理页与启动内存优化**：左侧 64px 侧栏新增“管理”（下载下、设置上），`ManagementPage` 含账户/实例两 tab；“其他”页收窄为内存优化/帮助/反馈/关于四 tab；主页账户卡→管理·账户，启动卡/最近存档→管理·实例，网络卡→设置·下载；`GameSettings` 新增 `memoryOptimizationOnLaunch`（默认 true，可继承），`GameSettingsPage` 增加“启动前优化启动器内存”，启动任务 order 150 调 `MemoryReclaimer.optimizeForLaunch()`；`GameSettings.java`/`GameSettingsPage.java` 为第 10/11 处代码侵入；6 个主页弹窗统一 `DialogPane`；`manage1` 测试包，见[步骤 23](docs/steps/23-管理页与启动内存优化.md)。**v3.11 Java 管理与主页自定义入口**：设置页删除 Java 管理 tab，Java 管理迁入“管理”页第三 tab；主页 Java 卡的 [管理 Java] 跳管理·Java；主页 [自定义] 改为跳“设置 > 外观”并滚动到内嵌的 `HomeCustomizationPane`，保存后当前主页即时重建；`PersonalizationPage.java` 为第 12 处代码侵入，见[步骤 23 §10](docs/steps/23-管理页与启动内存优化.md)。**v3.12 下载源零内置与设置导入/导出**：源码删除内置 BMCLAPI/Mojang 兜底（`DownloadProviders.java` 为第 13 处代码侵入），未配置源时提供空版本列表与下载入口提示；侧栏、设置页、首页启动器统一应用配置，保存后断言 `CommunityDownloadGuard.isConfigured()`，不生效即报错；新增 `.lmcl.json` 5 类设置导出/导入（其他 → 工具 → 设置导入/导出、窗口拖入、预览对话框、逐段失败不回滚）；`settings1` 测试包并附 BMCLAPI 样例，见[步骤 07](docs/steps/07-下载源配置.md) 与[步骤 22 §6B](docs/steps/22-其他页面与关于LMCL.md)。**v3.13 下载源平级化与自动测速**：下载源改为最多 5 个平级源（schema 2），删除主源/镜像；`CommunityDownloadRanker` 在进入下载入口/保存/安装时并行探测（5 秒超时、5 分钟 TTL），按可用+延迟升序重建候选，失败源置后；设置页新增 [测速排序]（只改 UI，保存落盘）；v1 `primary`+`mirrors` 自动迁移；`OtherPage`/`ManagementPage` 去掉与侧栏重复的“返回主界面”按钮；`settings2` 测试包附 3 个 BMCLAPI 兼容源，见[步骤 07](docs/steps/07-下载源配置.md) 与[审查记录 §40](docs/审查记录-v3.16.4源码核对.md)。**v3.14 导入刷新与守卫跳转**：导入 `.lmcl.json` 下载源段后，设置页按 `CommunityDownloadSources.revision()` 自动重读并强制测速，无需再点保存；未配置弹窗 [去设置] 改为跳转 设置 → 下载；`settings3` 测试包，见[步骤 07 §9](docs/steps/07-下载源配置.md) 与[审查记录 §41](docs/审查记录-v3.16.4源码核对.md)。**v3.15 下载源自动保存/下载前测速/排序不跳页**：修改地址、增删源、单项测试、导入都会防抖自动保存并应用；新增 `RankingDownloadProvider` 等待闸门，下载入口先静默测速排序并应用、最多等 12 秒再开始文件下载；[测速排序] 原地交换行并恢复滚动位置；`settings4` 测试包，见[步骤 07 §10](docs/steps/07-下载源配置.md) 与[审查记录 §42](docs/审查记录-v3.16.4源码核对.md)。**v3.16 M3 Expressive 阶段 0 spike**：新增 m3fx 临时依赖（JitPack `com.github.Glavo:m3fx:2481f88fd6`）、`community/ui/m3/CommunityM3` 子树主题桥接与 `M3SpikePane`（按钮/卡片/波浪进度/加载/输入控件），主页仅在 `-Dlmcl.m3.spike=true` 时显示 `[M3 Spike]` 调试入口；现有界面未改，阶段 0 的 Windows 视觉/性能验收仍未完成。**v3.16.1 修复 m3spike1 点击 `[M3 Spike]` 的 NPE**：`FontManager.getFont()` 在无自定义字体且默认字体探测失败时返回 `null`，`CommunityM3` 直接调用 `font.family()` 崩溃；现在字体为 null 时不写 inline font 样式、继续继承组件字体，并补 `CommunityM3Test` 覆盖 null/转义。修复版 jar **11,783,754 B**（`39f25911…`）、exe **12,984,906 B**（`b2dece72…`）、`m3spike2` zip **66,266,200 B**（`dbd7d1b5…`），见[方案 §5](docs/修复方案-Material3Expressive视觉重构.md) 与[审查记录 §43](docs/审查记录-v3.16.4源码核对.md)。**v3.16.2 M3 spike 进度条规范尺寸与内置插件端点**：`M3SpikePane` 的波浪进度改为轨道 10 px / 间隙 4 px / 终止点 4 px / 波长 40 px，仅保留 Wavy 2.5 与 Flat 0 两条对照；新增 `CommunityAddonSources`，`MissingDownloadProvider` 只放行 Modrinth/CurseForge 的 4 个官方 HTTPS 主机（api/cdn.modrinth.com、api.curseforge.com、edge.forgecdn.net），游戏本体/加载器/资源索引仍严格零内置；新增 4 个插件地址测试；community **153/0**、全量 **263/0/2 skipped**；jar **11,785,366 B**（`e0ed2f50…`）、exe **12,986,518 B**（`82ce6ac8…`）、`m3spike3` zip **66,003,199 B**（`07fca89c…`），代码提交 `6ccf011c3`；见[步骤 07 §11](docs/steps/07-下载源配置.md) 与[审查记录 §44](docs/审查记录-v3.16.4源码核对.md)。**v3.16.3 m3spike4 进度条粗细与连续波形**：m3spike3 的 10 px 线宽被用户判定过粗，恢复上一版 4 px；m3fx 的 indeterminate 动画从 0.18 位置开始循环，每轮会在左侧露出半截进度段，改为 `M3SpikePane.ContinuousWavyBar` 的传送带式连续波形（周期位移等于轨道宽、活动段 0.58 轨道宽、首尾图案一致），不再跳回；`m3-community.css` 只为新波形补轨道/活动段颜色；community **153/0**、全量 **263/0/2 skipped**；jar **11,788,701 B**（`6b1ffcee…`）、exe **12,989,853 B**（`dafe151c…`）、`m3spike4` zip **66,010,237 B**（`c33b55b3…`），代码提交 `539449b31`；见[方案 §5](docs/修复方案-Material3Expressive视觉重构.md) 与[审查记录 §45](docs/审查记录-v3.16.4源码核对.md)。**v3.16.4 m3spike5 波形上下起伏**：m3spike4 的不确定进度虽不再跳回，但波形相位固定、只横向平移；`ContinuousWavyBar` 增加 `wavePhase = offset * 2π * 2`，每循环走 2 个完整波，终点相位 4π，首尾一致；community **153/0**、全量 **263/0/2 skipped**；jar **11,788,756 B**（`c740ba6d…`）、exe **12,989,908 B**（`7cec96b1…`）、`m3spike5` zip **66,010,554 B**（`b4c19498…`），代码提交 `b456f2130`；见[方案 §5](docs/修复方案-Material3Expressive视觉重构.md) 与[审查记录 §46](docs/审查记录-v3.16.4源码核对.md)。
**v3.16.5 主页组件左右栏与排序（m3spike6）**：`DashboardItem`/`DashboardConfig` 增加可选 `column` 字段（`left`/`right`）；`HomePane` 按配置排序并分左右栏重建，账户卡固定左栏；设置 → 外观 → 主页组件可按栏切换、同栏上移/下移、显隐与恢复默认，保存即时重建；community **155/0**、全量 **265/0/2 skipped**；jar **11,793,195 B**（`b1c81aec…`）、exe **12,994,347 B**（`f9ab6a26…`）、`m3spike6` zip **66,017,607 B**（`c1f94afb…`）；见[步骤 21 §15](docs/steps/21-主页重构与侧边栏移除.md) 与[审查记录 §47](docs/审查记录-v3.16.4源码核对.md)。

**v3.16.6 关于页开发人员/设置导出勾选/导入预览分组（m3spike7）**：关于 LMCL 的“开发人员”新增“木子呆、法棍与仰望星空派”，小字“图标设计与绘制”；设置导入/导出的选项行去掉右侧重复文字，勾选框统一右对齐；导入预览按设置类目分组为可展开/折叠的 `ComponentSublist`，标题显示类目与变更条数，取消原来 50 条的截断；community **155/0**、全量 **265/0/2 skipped**；jar **11,793,778 B**（`14d2e80e…`）、exe **12,994,930 B**（`8dbd2ee1…`）、`m3spike7` zip **66,039,211 B**（`5f10a29c…`）；见[步骤 22 §6B.8](docs/steps/22-其他页面与关于LMCL.md) 与[审查记录 §48](docs/审查记录-v3.16.4源码核对.md)。

**v3.16.7 关于页开发人员头像与主页左上角按钮（m3spike8）**：两名开发人员改用本地头像 `assets/about/avatars/kerze.jpg`、`muzidai.jpg`（`ImageContainer` 32px，资源缺失回退 `SVG.PERSON`）；从其他页面回到主页时，标题栏左上角的 HOME 图形按钮不再显示，其他页面的返回箭头保持不变；改动在既有第 7 处 `DecoratorController.java` 内，不新增代码侵入点；community **155/0**、全量 **265/0/2 skipped**；jar **11,848,943 B**（`4f301110…`）、exe **13,050,095 B**（`5243673b…`）、`m3spike8` zip **66,148,447 B**（`edfdba1d…`）；见[步骤 21 §16](docs/steps/21-主页重构与侧边栏移除.md)、[步骤 22 §6B.9](docs/steps/22-其他页面与关于LMCL.md) 与[审查记录 §49](docs/审查记录-v3.16.4源码核对.md)。

---

## 1. 文档地图

```
├── README.md                      ← 本文件：索引、步骤总表、进度、文档约定
└── docs/
    ├── 00-整体要求.md              ← 总纲（顶层约束，与它冲突的实施细节无效）
    ├── 审查记录-v3.16.4源码核对.md  ← 二轮审查：问题清单、源码证据、已定决策
    ├── steps/
    │   ├── 01-仓库建立与上游同步机制.md
    │   ├── 02-CI三工作流落地.md
    │   ├── 03-产品身份与合规改造.md
    │   ├── 04-包名Relocate改造.md
    │   ├── 05-禁用自动更新与更新提示.md
    │   ├── 06-数据目录与用户迁移.md
    │   ├── 07-下载源配置.md
    │   ├── 08-启动保障-启动前体检.md
    │   ├── 09-启动保障-崩溃现场还原.md
    │   ├── 10-性能策略-Java发行版默认偏好.md
    │   ├── 11-性能策略-JVM参数预设向导.md
    │   ├── 12-内存优化P0-跨平台基座.md
    │   ├── 13-首页组件化A-框架搭建.md
    │   ├── 14-首页组件化B-P0组件与接入.md
    │   ├── 15-首页组件化C-P1P2组件与打磨.md
    │   ├── 16-启动保障-实例快照回滚.md
    │   ├── 17-内存优化P1-Windows守护进程.md   ← v1.1 起内容为 HKCU 后台守护，非系统服务
    │   ├── 18-内存优化P2-发布准备.md
    │   ├── 19-MD3主题完善.md
    │   ├── 20-HMCLauncher启动壳重建.md      ← v1.1 新增（fork stub 源码，见步骤 20）
    │   ├── 21-主页重构与侧边栏移除.md       ← v3.1 新增 / v3.3 主页改版 / v3.16.5 左右栏与排序（见步骤 21）
    │   ├── 22-其他页面与关于LMCL.md         ← v3.9 六 tab（历史；v3.10 收窄为四 tab）
    │   └── 23-管理页与启动内存优化.md       ← v3.10 新增，v3.11 扩展（管理页 / 主页跳转 / 启动内存优化 / Java 管理 / 主页自定义）
    ├── 修复方案-home2-主界面交互与裁切.md
    ├── 修复方案-其他页面与关于LMCL.md
    ├── 修复方案-管理页与内存优化入口.md   ← v4.0 方案（已实施，记录见步骤 23）
    ├── 修复方案-下载源强制配置与设置导入导出.md   ← v3.12 方案（已批准；第 13 处侵入是 DownloadProviders）
    ├── 修复方案-Material3Expressive视觉重构.md   ← v3.16 方案（阶段 0 spike 已实现，Windows 实机待验收）
    └── 附录/
        ├── 关键类速查与调试参数.md   ← 实施时的高频查表
        ├── CI工作流/                 ← ci.yml / build-release.yml / sync-upstream.yml 原文 + 说明
        └── 原始资料/                 ← 6 份原始调研稿（只读，用于溯源）
```

**效力层级**：`00-整体要求.md`（总纲）> `steps/*.md`（执行）> `附录/原始资料/`（历史记录，冲突以总纲为准）。

> **迁移状态**：v3.16.7（v1.1 全量重写 → v1.2/v1.3 一致性收口 → v1.4 第二轮源码核对 → v1.5–v1.9 定名与 03/05/06/13 落地 → v2.0 下载源重设计 → v2.1 步骤 08 落地 → v2.2 步骤 09 落地 → v2.3 步骤 10 落地 → v2.4 步骤 11 落地 → v2.5 步骤 12 落地 → v2.6 步骤 14 落地 → v2.7 步骤 15 落地 → v2.8 步骤 16 落地 → v2.9 启动误迁移修复 → v3.0 下载设置页接管 → v3.1 主页重构/侧栏移除 → v3.2 视觉收口 → v3.3 交互修复/全局侧栏/主页改版 → v3.4 步骤 20 源码 fork/MinGW 交叉构建 → v3.5 主页纯色背景/贴边/滚动条/移除内存卡 → v3.6 左右独立滚动/黑底修复/背景回退 → v3.7 侧栏菜单对比度/主页留白 → v3.8 其他页/关于 LMCL/设置页精简 → v3.9 阶段 E 六 tab/主页 9 卡 → v3.10 管理页/启动内存优化/视觉收口 → v3.11 Java 管理迁管理页/主页自定义入口内嵌设置·外观 → v3.12 下载源零内置/设置导入导出 → v3.13 下载源平级化/自动测速 → v3.14 导入刷新/守卫跳转 → v3.15 下载源自动保存/下载前测速/排序不跳页 → v3.16 M3 阶段 0 spike → v3.16.1 m3spike2 字体 NPE 修复 → v3.16.2 m3spike3 进度条/插件端点 → v3.16.3 m3spike4 4px 连续波形 → v3.16.4 m3spike5 波形上下起伏 → v3.16.5 主页卡片左右栏/排序 → v3.16.6 关于页开发人员/设置导出勾选/导入预览分组 → v3.16.7 关于页头像/主页左上角按钮）——总纲、README、23 篇步骤、附录均已按 v3.16.4 源码对齐（阶段/优先级/工期/侵入点以本文件第 3 节与总纲 §4.2 为准）。产品名已定：**Lumina Minecraft Launcher（LMCL）**；本地 git 仓库 `/home/kerze/hmclce/lmcl` 已提交 03/05/06/07/08/09/10/11/12/13/14/15/16/21/22/23 的实现（推送前补 `origin`）；已验收的 Windows 稳定包为 `dist/LMCL-3.16.1000-win-x64-home3.zip`（64,446,081 B，SHA-256 `52d9dd6d…`）；v3.15 UI 测试包为 `dist/LMCL-3.16.1000-win-x64-settings4.zip`（64,986,547 B，SHA-256 `ede1ddb3…`，附 3 个 BMCLAPI 兼容源样例 `.lmcl.json`，jar 11,220,705 B / exe 12,421,857 B）；最新测试包为 `dist/LMCL-3.16.1000-win-x64-m3spike8.zip`（66,148,447 B，SHA-256 `edfdba1d…`，jar 11,848,943 B / exe 13,050,095 B，含开发人员本地头像、主页左上角按钮移除与左右栏/排序，**未在 Windows 实机验证**）；旧 m3spike7 已删除；custom1 旧包已删除，settings1–settings3 已被 settings4 取代并删除，manage1、home4–home7、other1/other2 也已删除。发布前阻塞项仅剩更新域名/托管、Windows 实机验收与商标复核（见 [总纲 §12](docs/00-整体要求.md#12-开放问题)）。

---

## 2. 阅读顺序

| 角色 | 建议路径 |
|---|---|
| **第一次接触项目** | 本文件 → [整体要求 §1 项目定义](docs/00-整体要求.md#1-项目定义) → [§1.4 差异化方向](docs/00-整体要求.md#14-差异化方向) → 步骤 01 |
| **准备动手写代码** | [整体要求 §3 法律合规](docs/00-整体要求.md#3-法律与合规要求硬约束) + [§4 架构边界](docs/00-整体要求.md#4-技术架构与工程边界) + [§5 非功能硬约束](docs/00-整体要求.md#5-非功能硬约束) → 目标步骤 → [附录 · 关键类速查](docs/附录/关键类速查与调试参数.md) |
| **维护上游同步** | [§4.1 分支策略](docs/00-整体要求.md#41-上游分支策略) → [§4.3 同步方式](docs/00-整体要求.md#43-上游同步方式) → 步骤 01 / 02 |
| **发布前** | [§3.7 分发 Checklist](docs/00-整体要求.md#37-分发-checklist每次发版逐项确认) → [§8 验收总标准](docs/00-整体要求.md#8-验收总标准definition-of-done) |
| **想了解某个决策为什么这么定** | 对应步骤的"§2 背景与决策依据"，或翻 [审查记录](docs/审查记录-v3.16.4源码核对.md) / [附录 · 原始资料](docs/附录/原始资料/) |

---

## 3. 步骤总表

> 状态标记：☐ 未开始 ｜ ◐ 进行中 ｜ ☑ 已完成。**改动后请手动更新本表。**
> 阶段/优先级/工期与总纲 §6 完全一致；若与某篇步骤文档冲突，以本表与总纲为准（并修正该步骤文档）。

| # | 步骤 | 阶段 | 优先级 | 工期 | 交付物（一句话） | 状态 |
|:--:|---|:--:|:--:|:--:|---|:--:|
| 01 | [仓库建立与上游同步机制](docs/steps/01-仓库建立与上游同步机制.md) | 一 | P0 | 3–5 天 | fork + 跟 `release/3.16` + patch 同步跑通 | ☐ |
| 02 | [CI 三工作流落地](docs/steps/02-CI三工作流落地.md) | 一 | P0 | 5 天 | 构建 / 发布 / 上游同步三条流水线绿 | ☐ |
| 03 | [产品身份与合规改造](docs/steps/03-产品身份与合规改造.md) | 一 | P0 | 3–5 天 | 独立名称、版本号、图标、许可证文件齐全 | ◐ 代码已落地，图标待补 |
| 04 | [包名策略与 Relocate 门禁](docs/steps/04-包名Relocate改造.md) | 一 | P1 | 3–5 天 | 验证并记录 Go/No-Go；**首发 No-Go**（崩溃窗口依赖包名字符串） | ☐ |
| 05 | [禁用自动更新与更新提示](docs/steps/05-禁用自动更新与更新提示.md) | 一 | P0 | 2–3 天 | 自动更新不可用；新版本只弹窗提示并跳仓库，绝不下载 | ◐ 启动误迁移已修（fix1），GUI 未实机 |
| 06 | [数据目录与用户迁移](docs/steps/06-数据目录与用户迁移.md) | 一 | P1 | 3–5 天 | 独立数据目录 + 一次性导入旧配置 | ◐ 路径注入/检测/导入/设置面板已编译，提示条与 GUI 未验 |
| 07 | [下载源配置](docs/steps/07-下载源配置.md) | 一 | P0 | 3–5 天 | 零内置（无镜像、无官方兜底）；最多 5 个平级源 + 进入下载入口自动测速排序（5 分钟 TTL）；设置页修改/测试/导入防抖自动保存并应用；下载前 `RankingDownloadProvider` 等待测速完成（最多 12 秒）再开始；[测速排序] 保持滚动位置；未配置时版本列表/下载入口提示；保存后断言生效；接管上游“设置 → 下载”页（第 4 处）+ 源码移除兜底（第 13 处） | ◐ 代码/测试/打包完成（settings4），GUI 未实机 |
| 20 | [HMCLauncher 启动壳重建](docs/steps/20-HMCLauncher启动壳重建.md) | 一 | P1 | 2–5 天 | fork stub（C++20）重建自有品牌 exe | ◐ 源码/品牌/交叉构建完成；Windows 实机待验 |
| 08 | [启动保障 · 启动前体检](docs/steps/08-启动保障-启动前体检.md) | 二 | P0 | 2 周 | 启动钩子契约 + 五项只读体检（Java/内存/磁盘/重复模组/缺依赖），失败也照常启动；提供 `SURVIVED_THRESHOLD` 事件供 09/15/16 消费 | ◐ |
| 09 | [启动保障 · 崩溃现场还原](docs/steps/09-启动保障-崩溃现场还原.md) | 二 | P0 | 2 周 | 崩溃现场留存 + 崩溃历史 + 诊断导出（复用上游分析，不重写规则） | ◐ 归档/历史/面板已编译（[§10](docs/steps/09-启动保障-崩溃现场还原.md)），卡片已接（14），GUI 未验 |
| 10 | [性能策略 · Java 发行版偏好](docs/steps/10-性能策略-Java发行版默认偏好.md) | 三 | P0 | 1 周 | 默认偏好 Zulu，可一键改回；推荐面板 + 设置分组 | ◐ 已编译（[§10](docs/steps/10-性能策略-Java发行版默认偏好.md)），镜像未部署、卡片已接（14）、GUI 未验 |
| 11 | [性能策略 · JVM 参数预设向导](docs/steps/11-性能策略-JVM参数预设向导.md) | 三 | P0 | 1.5–2 周 | 分档预设 + 冲突检测 | ◐ |
| 12 | [内存优化 P0 · 跨平台基座](docs/steps/12-内存优化P0-跨平台基座.md) | 三 | P1 | 1 周 | 跨平台内存回收基座（`System.gc()` + 自适应）+ 指标 + 设置面板「内存」 | ◐ 回收器/设置分组已编译（[§10](docs/steps/12-内存优化P0-跨平台基座.md)），卡片已接（14），三平台与 GUI 未验 |
| 13 | [首页组件化 A · 框架](docs/steps/13-首页组件化A-框架搭建.md) | 四 | P1 | 2–3 周 | 组件注册表 + 布局持久化（挂载进 `MainPage`） | ◐ 框架/设置面板/更新提示已编译（[§10](docs/steps/13-首页组件化A-框架搭建.md)），P0 卡片已接（14）、P1/P2 已接（15），GUI 未验 |
| 14 | [首页组件化 B · P0 组件](docs/steps/14-首页组件化B-P0组件与接入.md) | 四 | P1 | 2–3 周 | 启动 / 置顶 / 崩溃 / Java / 网络 五卡接入（v3.5 移除内存卡） | ◐ 原六卡/Provider/失败复位已编译（[§13](docs/steps/14-首页组件化B-P0组件与接入.md)），v3.5 移除内存卡，GUI 未验 |
| 15 | [首页组件化 C · P1P2 组件](docs/steps/15-首页组件化C-P1P2组件与打磨.md) | 四 | P2 | 1.5–2 周 | 最近世界 / 活动日历 / 体检三卡 + 打磨（v3.9 移除存储卡） | ◐ 三卡/数据层/Provider 已编译（[§10](docs/steps/15-首页组件化C-P1P2组件与打磨.md)），v3.9 移除存储卡，GUI 未验 |
| 16 | [启动保障 · 实例快照回滚](docs/steps/16-启动保障-实例快照回滚.md) | 五 | P0 | 2 周 | 成功启动自动快照 + 首页快照卡 + 一键回滚 | ◐ 快照服务/卡片/管理/回滚已编译（[§10](docs/steps/16-启动保障-实例快照回滚.md)），GUI 与 Windows 文件占用未验 |
| 17 | [内存优化 P1 · Windows 后台守护](docs/steps/17-内存优化P1-Windows守护进程.md) | 五 | P1 | 2–3 周 | 极窄接口的登录后台守护（HKCU，默认关闭） | ☐ |
| 18 | [内存优化 P2 · 发布准备](docs/steps/18-内存优化P2-发布准备.md) | 五 | P1 | 1–1.5 周 | 白名单/签名/灰度/回滚预案（或决定不发） | ☐ |
| 19 | [MD3 主题完善](docs/steps/19-MD3主题完善.md) | 六 | P2 | 1–2 周 | 我们新增界面的视觉收口（零 CSS 侵入） | ◐ `community.css`/11 调用点/日历色阶已落地；GUI 主题矩阵未验 |
| 21 | [主页改版与全局侧栏](docs/steps/21-主页重构与侧边栏移除.md) | 四（补丁） | P1 | 1–1.5 周 | 全局 64px 图标侧栏；`HomePane` 左账号·游玩信息栏 + 右实例栏；v3.6 左右列独立滚动、背景回退、移除内存卡；v3.9 移除存储卡（主页 9 卡）；v3.10 侧栏加“管理”，账户卡/实例跳转改管理页；v3.16.7 移除主页左上角 HOME 快捷按钮 | ◐ 代码/编译/测试/打包完成；v3.10 管理页与内存优化另见步骤 23；GUI 待 Windows 验收 |
| 22 | [其他页与关于 LMCL](docs/steps/22-其他页面与关于LMCL.md) | 四（补丁） | P1 | 1 周 | “其他”独立页五 tab（内存优化/设置导入导出/帮助/反馈/关于 LMCL）；设置页三 tab 迁移；关于页开发人员/上游致谢/捐赠者空态；`.lmcl.json` 5 类设置导入导出 + 拖入（v3.12）；v3.13 样例附 3 个源；v3.16.7 开发人员本地头像 | ◐ 代码/测试/打包完成（settings4），GUI 待 Windows 验收 |
| 23 | [管理页与启动内存优化](docs/steps/23-管理页与启动内存优化.md) | 四（补丁） | P1 | 约 1 周（5.0–5.5 人日） | 侧栏“管理”一级入口；管理页账户/实例/Java；其他页四 tab + 内存优化入口；主页账户/实例/Java/下载跳转统一；`GameSettings` 启动内存优化（第 10/11 处侵入）；v3.11 主页自定义内嵌设置·外观（第 12 处） | ◐ 代码/测试/打包完成（custom1 历史包已删；当前测试包 settings4），GUI 与真实启动日志待 Windows 验收 |

**总工期**：关键路径约 **26.5–30.5 周**（单人业余；逐项串行累加约 26.8–36.1 周，仅用于核对），AI 辅助折算约 **20–24 周**。关键路径是"改动外挂化"——推导见 [§6.2 阶段排期](docs/00-整体要求.md#62-阶段排期)。

---

## 4. 关键决策速览

新读者只需记住这 10 条，其余细节都在步骤文档里：

1. **基座选 HMCL（基线 v3.16.4 / release/3.16）**：BMCLAPI 全链路镜像 + 三模块分层 + 上游已提供 MonetFX token。详见 [§2](docs/00-整体要求.md#2-基座选型已定案)、[§4.1](docs/00-整体要求.md#41-上游分支策略)。
2. **独立产品名 + 明确标注"基于 HMCL"**：商标状态不明，独立名是唯一零风险解。详见 [步骤 03](docs/steps/03-产品身份与合规改造.md)。
3. **跟 release 分支，不跟 `main`**：`main` 是 beta 开发分支。详见 [§4.1](docs/00-整体要求.md#41-上游分支策略)。
4. **代码侵入固定 13 处**：`Metadata.java` / `MainPage.java` / `LauncherHelper.java` / `DownloadSettingsPage.java` / `RootPage.java` / `DecoratorAnimatedPage.java` / `DecoratorController.java` / `LauncherSettingsPage.java` / `Controllers.java` / `GameSettings.java` / `GameSettingsPage.java` / `PersonalizationPage.java` / `DownloadProviders.java`；构建脚本与资源（i18n、图标、about）单独登记，其余一律新增 package。详见 [§4.2](docs/00-整体要求.md#42-改动策略代码侵入固定-13-处--分级登记)。
5. **启动引导零源码侵入**：新增 `CommunityBootstrap` 作 `Main-Class`，在 `Metadata` 初始化前注入更新源、数据目录、完整性开关；下载源不走系统属性，由步骤 07 在首页挂载时运行时替换 `DownloadProviderWrapper.setProvider(...)`（用户可在设置里配置主源与镜像）。详见 [§4.6](docs/00-整体要求.md#46-启动引导与属性注入) 与[步骤 07](docs/steps/07-下载源配置.md)。
6. **首页 `HomePane` + 全局 64px 图标侧栏**：`MainPage` 只挂载 `CommunityDashboard`；`DecoratorController` 把 `navigator` 包进 `HBox(sidebar, navigator)`，侧栏为主页/下载/设置/其他；`HomePane` 左栏账号·游玩记录、右栏实例等，整页滚动。`RootPage` 不再渲染 200 px 文本侧栏。详见 [步骤 21](docs/steps/21-主页重构与侧边栏移除.md) 与 [§13 #23](docs/00-整体要求.md#13-已定决策记录)。
7. **包名首发不改（No-Go）**：`org.jackhuang.hmcl.*` 保持；relocate 只作为未来 Go 分支，且需新增第 13 处侵入（`CrashReport.java`；当前预算 12 处已由设置/管理/外观占用）。详见 [步骤 04](docs/steps/04-包名Relocate改造.md)。
8. **Windows 启动壳 fork 重建**：`HMCLauncher` 源码 C++20（32 位，GPLv3+附加条款），用 `HMCL_LAUNCHER_EXE` 换入自有 stub；官方 exe 留作回退。详见 [步骤 20](docs/steps/20-HMCLauncher启动壳重建.md)。
9. **绝不阻断启动**：任何增强失败都静默降级；L2 内存优化默认关闭，失败即回落 L0；L2 采用 HKCU 登录后守护，不装系统服务。详见 [§5.1](docs/00-整体要求.md#51-绝不阻断启动)、[§5.3](docs/00-整体要求.md#53-权限与安全)。
10. **不做自动更新，只做提示**：上游更新链路因未签名在联网前必失败；自有 `CommunityUpdateNotifier` 只读 GitHub/Gitee Releases，弹窗只开浏览器、不下载任何文件；仓库未建时留空 = 功能静默。详见 [步骤 05](docs/steps/05-禁用自动更新与更新提示.md)。

---

## 5. 步骤文档模板

每篇步骤文档结构固定，便于对照与审阅：

```markdown
# 步骤 NN · 标题

> **阶段** ｜ **优先级** ｜ **工期** ｜ **前置** ｜ **后续** ｜ **侵入点**

## 1. 目标
（完成标志：可验证的 2–5 条）

## 2. 背景与决策依据
（表格：为什么这么做、放弃的方案及原因、上游现状）

## 3. 详细实施
### 3.1 …（含代码 / 命令 / 关键 API）

## 4. 验收 Checklist
- [ ] …

## 5. 风险与应对
（表格：风险 | 等级 | 应对）

## 6. 关联文档
（相对链接）
```

> 允许在 §5 风险 与「关联文档」之间插入额外章节（如 [步骤 02](docs/steps/02-CI三工作流落地.md) 的「可选扩展」），此时「关联文档」顺延为最后一节。

**写法要求**：

- 决策必须有"为什么"，否则半年后无法判断能否改；
- 命令与代码可直接复制执行，路径写全；
- 风险表必须给应对，不只列问题；
- 不确定的地方标"（待实测）"，不要写成结论；
- **头部元信息（阶段/优先级/工期/侵入点）必须与总纲 §4.2、§6 及本文件步骤总表一致**，不得各自为政；
- 涉及代码的步骤必须给出**上游类名 + 方法签名 + 插入位置**，不能只写"在启动流程中加钩子"。

---

## 6. 文档约定

| 规则 | 说明 |
|---|---|
| 总纲优先 | 与 `00-整体要求.md` 冲突的步骤细节一律无效，先改总纲 |
| 编号连续 | 新增步骤接在 19 之后（v1.1 新增 20），编号不复用、不跳号 |
| 相对链接 | 文档间一律用相对路径链接，便于仓库迁移与离线阅读 |
| 原始资料只读 | `docs/附录/原始资料/` 是历史记录，不修改；结论修正写进总纲/步骤 |
| 数据标时点 | 引用上游数据（文件数、PR、版本）必须写清时点 |
| 开放问题闭环 | 总纲 §12 的问题在实施中回填结论并注明归属步骤 |
| 决策留痕 | 每个"拍板"结论写入总纲 §13 或审查记录，避免反复讨论 |

---

## 7. 进度追踪方式

1. 开工：把本文件第 3 节的状态改为 `◐ 进行中`，并在步骤文档中记录起始日期；
2. 完成：按步骤文档"§4 验收 Checklist"逐项勾选，全部通过后状态改 `☑ 已完成`；
3. 阻塞：在步骤文档开头加 `> ⚠️ 阻塞：<原因>`，并在总纲 §12 登记开放问题；
4. 发布：回填 [§3.7 分发 Checklist](docs/00-整体要求.md#37-分发-checklist每次发版逐项确认)。

---

## 8. 数据时效与复核要求

本文档中的量化数据（代码规模、PR 编号、版本号、性能实测、分支名）来自 **2026-10** 的调研；其中基线部分已于 2026-10-21 用 `v3.16.4` 源码实读复核（见 [审查记录](docs/审查记录-v3.16.4源码核对.md)）。上游 HMCL 处于活跃开发中，实施前仍需复核：

- 类名 / 方法签名是否仍存在（先 `grep`，再看源码）；
- 依赖版本、Gradle/Shadow 插件版本（当前：Gradle 9.6.1 / Shadow 9.3.1 / JDK 17）；
- 许可证与附加条款是否变动；
- 上游分支名（`release/3.x` 的 `x` 会变）；
- CI 引用的 Action 版本（`uses: xxx@vN`）。

复核结论若与文档不符：**先更新文档，再写代码**。

---

## 9. 变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-10-22 | v3.16.7 | **关于页开发人员头像与主页左上角按钮（m3spike8）**：两名开发人员改用 `assets/about/avatars/kerze.jpg`、`muzidai.jpg`（`ImageContainer` 32px，缺失回退 `SVG.PERSON`）；`DecoratorController.onNavigated` 识别 `RootPage`，强制 `canClose=false`、`showCloseAsHome=false`，其他页面返回按钮不变；community 155/0、全量 265/0/2 skipped；jar 11,848,943 B（`4f301110…`）、exe 13,050,095 B（`5243673b…`）、`m3spike8` zip 66,148,447 B（`edfdba1d…`）。 | 步骤 21 §16、步骤 22 §6B.9、审查记录 §49 |
| 2026-10-22 | v3.16.5 | **主页组件左右栏与排序（m3spike6）**：`DashboardItem` 增加可选 `column`（left/right），`DashboardConfig` 读写/导出该字段并兼容旧配置；`HomePane` 按 `order` 排序、按 `column` 分左右栏重建，账户卡固定左栏，旧配置无 column 时活动/最近存档默认左栏、其余默认右栏；设置 → 外观 → 主页组件新增左右栏切换与同栏上移/下移，保存立即重建；community **155/0**、全量 **265/0/2 skipped**；jar **11,793,195 B**（`b1c81aec…`）、exe **12,994,347 B**（`f9ab6a26…`）、`m3spike6` zip **66,017,607 B**（`c1f94afb…`）。 | 步骤 21 §15、审查记录 §47 |
| 2026-10-22 | v3.16.2 | **M3 spike 进度条规范尺寸 + 内置插件端点（m3spike3）**：`M3SpikePane` 进度条改为 thickness 10 / gap 4 / stop 4 / wavelength 40，只保留 Wavy 65%（振幅 2.5）与 Flat 65%（振幅 0），移除振幅 4.0；新增 `CommunityAddonSources` 与 `CommunityAddonSourcesTest` 4 个测试，`MissingDownloadProvider` 只放行 Modrinth/CurseForge 的 4 个官方 HTTPS 端点，Minecraft 下载仍零内置；community **153/0**、全量 **263/0/2 skipped**；jar **11,785,366 B**（`e0ed2f50…`）、exe **12,986,518 B**（`82ce6ac8…`）、`m3spike3` zip **66,003,199 B**（`07fca89c…`）；待 Windows 实机验证 | 步骤 07 §11、审查记录 §44 |
| 2026-10-22 | v3.16.3 | **M3 spike 进度条恢复 4 px + 连续 indeterminate 波形（m3spike4）**：10 px 线宽被用户判定过粗，恢复 4 px / gap 4 / stop 4 / wavelength 40；m3fx indeterminate 从 0.18 开始循环导致每轮在左侧露出半截，改为 `ContinuousWavyBar`（周期位移=轨道宽、活动段 0.58 轨道宽、首尾图案一致）；community **153/0**、全量 **263/0/2 skipped**；jar **11,788,701 B**（`6b1ffcee…`）、exe **12,989,853 B**（`dafe151c…`）、`m3spike4` zip **66,010,237 B**（`c33b55b3…`）；待 Windows 实机验证 | 方案 §5、审查记录 §45 |
| 2026-10-22 | v3.16.6 | **关于页开发人员/设置导出勾选/导入预览分组（m3spike7）**：开发人员新增“木子呆、法棍与仰望星空派”（图标设计与绘制）；导出选项去掉右侧重复文字、勾选框右对齐；导入预览按类目 `ComponentSublist` 分组并可展开，移除 50 条截断；community 155/0、全量 265/0/2 skipped；jar 11,793,778 B（`14d2e80e…`）、exe 12,994,930 B（`8dbd2ee1…`）、`m3spike7` zip 66,039,211 B（`5f10a29c…`）。 | 步骤 22 §6B.8、审查记录 §48 |
| 2026-10-22 | v3.16.4 | **M3 spike 波形上下起伏（m3spike5）**：波形相位固定导致只横向平移；`wavePhase = offset * 2π * 2`，每循环 2 个完整波，首尾相位一致；community **153/0**、全量 **263/0/2 skipped**；jar **11,788,756 B**（`c740ba6d…`）、exe **12,989,908 B**（`7cec96b1…`）、`m3spike5` zip **66,010,554 B**（`b4c19498…`）；待 Windows 实机验证 | 方案 §5、审查记录 §46 |
| 2026-10-22 | v3.16.1 | **修复 m3spike1 点击 [M3 Spike] 崩溃（m3spike2）**：`FontManager.getFont()` 在 Windows 无自定义字体且默认字体探测失败时返回 null，`CommunityM3.install` 调用 `font.family()` 触发 NPE；抽出 `CommunityM3.fontStyle(@Nullable FontReference)`，null 时返回 null、不写 inline font 样式，继续继承组件字体；新增 `CommunityM3Test` 2 个测试（null、引号/反斜杠转义）；community **149/0**；jar **11,783,754 B**（`39f25911…`）、exe **12,984,906 B**（`b2dece72…`）、`m3spike2` zip **66,266,200 B**（`dbd7d1b5…`）；待 Windows 实机复测 | 方案 §5、审查记录 §43.6 |
| 2026-10-22 | v3.16 | **M3 Expressive 阶段 0 spike（m3spike1，已被 m3spike2 取代）**：临时接入 JitPack `com.github.Glavo:m3fx:2481f88fd6`；`CommunityM3` 用 `M3Theme.fromColorScheme(EXPRESSIVE_2025, Themes.getColorScheme())` + `M3ThemeManager.install(root, theme)` 只装子树；`M3SpikePane` 展示 5 种按钮、3 种卡片、amplitudes 0/2.5/4 进度条、indeterminate、loading/circular、switch/slider/textfield；`HomePane` 仅 `-Dlmcl.m3.spike=true` 显示调试入口；jar 内 376 个 m3fx class、`META-INF/LICENSE-m3fx.txt`；community **147/0**、全量 **257/0/2 skipped**、`checkTranslations` 通过；jar **11,783,508 B**（`3744363a…`）、exe **12,984,660 B**（`534a3623…`）、`m3spike1` zip **66,265,358 B**（`bb733763…`）；本机 WSL2 无 X11，Windows 实机视觉/启动增量待验收 | 方案 §5、审查记录 §43 |
| 2026-10-22 | v3.15 | **下载源自动保存 / 下载前测速 / 排序不跳页（settings4）**：设置页对地址修改、增删源、单项测试、导入做 0.7 s 防抖自动保存并应用（空白占位行不保存）；`CommunityDownloadRanker.refreshAsync` 返回 `CompletableFuture`，新增 `markDownloadEntry()`/`awaitBeforeDownload()`（下载入口强制刷新、最多等 12 s、provider 空闲 2 min 触发新会话）；新增 `RankingDownloadProvider`，`CommunityDownloadInstaller` 对非空源包一层等待闸门；测速完成后在设置页不可见时按结果写回 `downloadSources` 顺序；`[测速排序]` 原地交换行并恢复 ScrollPane 位置、自动保存；community **147/0**、全量 **257/0/2 skipped**；jar **11,220,705 B**（`8b82fd19…`）、exe **12,421,857 B**（`8948a785…`）、`settings4` zip **64,986,547 B**（`ede1ddb3…`）；settings3 已删 | 步骤 07 §10、审查记录 §42 |
| 2026-10-22 | v3.14 | **导入刷新与守卫跳转（settings3）**：`DownloadSettingsPage implements PageAware`，`onPageShown()` 调 `CommunityDownloadSettingsPane.reloadIfChanged()`；`CommunityDownloadSources.revision()` 作为重读版本号；`SettingsImportDialog` 在应用 `downloadSources` 后 `refreshVisible()`；导入器与手动保存强制 `CommunityDownloadRanker.refreshAsync(true)`；`CommunityDownloadGuard` 的 [去设置] 跳到 设置 → 下载；community **146/0**、全量 **256/0/2 skipped**；jar **11,214,625 B**（`bc0429f3…`）、exe **12,415,777 B**（`185e9710…`）、`settings3` zip **64,974,146 B**（`543b73c4…`） | 步骤 07 §9、审查记录 §41 |
| 2026-10-22 | v3.13 | **下载源平级化与自动测速（settings2）**：`CommunityDownloadSources` 改 schema 2 `sources[]`（`MAX_SOURCES=5`，v1 `primary`+`mirrors` 迁移）；新增 `CommunityDownloadRanker`（5 分钟 TTL、并行探测、可用+延迟升序、失败置后）；`DownloadSourceTester.Result` 带延迟；进入下载入口/保存/安装触发刷新；设置页 5 行平级源 + [测速排序] + [清除所有源]；网络卡改「源/计数」；`OtherPage`/`ManagementPage` 去掉左上“返回主界面”按钮；community **146/0**、全量 **256/0/2 skipped**；jar **11,213,267 B**（`d19b9c69…`）、exe **12,414,419 B**（`1a674ed4…`）、`settings2` zip **65,005,183 B**（`5a28c4b3…`）；样例 3 源 | 步骤 07 v5.0、审查记录 §40 |
| 2026-10-22 | v3.12 | **下载源零内置与设置导入/导出（settings1）**：`DownloadProviders` 初始 wrapper 改 `MissingDownloadProvider`，删除 BMCLAPI/Mojang 兜底（第 13 处侵入）；`CommunityDownloadProvider`/`MissingVersionList`/`CommunityDownloadGuard`；侧栏、设置页保存、首页启动器统一应用并断言生效；`.lmcl.json` 5 类导出器 + 导入预览 + 全局拖入 + 其他→工具入口；`community/config`、`ui/transfer`；community **143/0**、全量 **253/0/2 skipped**；jar **11,204,163 B**（`f99d0a03…`）、exe **12,405,315 B**（`37709fd6…`）、`settings1` zip **64,975,921 B**（`91c71d60…`） | 步骤 07 v4.0、步骤 22 §6B、审查记录 §38 |
| 2026-10-22 | v3.10 | **管理页与启动内存优化（manage1）**：`CommunitySideBar` 新增管理键（下载下、设置上）；新增 `ManagementPage`（账户/实例）与 `MemoryOptimizationView`；`OtherPage` 收窄为内存优化/帮助/反馈/关于；主页账户卡、启动卡、最近存档、网络卡跳转统一；`GameSettings.memoryOptimizationOnLaunch`（默认 true）+ `GameSettingsPage` 继承复选框 + `MemoryLaunchOptimizationTask`（order 150）；`GameSettings`/`GameSettingsPage` 为第 10/11 处侵入；6 个主页弹窗改 `DialogPane`；社区+设置测试 **151/0**、`checkTranslations` 通过；jar **11,173,040 B**（`a15ce828…`）、exe **12,374,192 B**（`f33a218b…`）；`manage1` zip **65,172,891 B**，SHA-256 `dfa769b5…`；GUI 未实机验证 | 步骤 23、审查记录 §36 |
| 2026-10-22 | v3.11 | **Java 管理与主页自定义入口（custom1）**：设置页删除 Java 管理 tab，`ManagementPage` 增加 Java 第三 tab 与 `showJava()`；主页 Java 卡 [管理 Java] → 管理·Java；主页 [自定义] 导航设置·外观并滚动到内嵌 `HomeCustomizationPane`；`PersonalizationPage.java` 为第 12 处侵入；保存后 `HomePane` 通过静态弱引用重载 `dashboard.json`；`compileJava`/`checkTranslations` 通过，community + 设置测试 **151/0**；jar **11,177,650 B**（`96152028…`）、exe **12,378,802 B**（`6931fd69…`）、`custom1` zip **64,915,005 B**（`96771468…`） | 步骤 23 §10、审查记录 §37 |
| 2026-10-22 | v3.9 | 阶段 E：`OtherPage` 六 tab；`AccountsPane`/`InstancesPane`/`CommunitySettingsView` 内嵌；`HomePane` 移除 storage/memory（9 卡）；`CommunityWidgetsProvider` 9 卡；新包 `other2` 65,157,579 B / `f66c993f…`；测试 135/0 | 步骤 22 §6A、21/15 注记、审查记录 §35 |
| 2026-10-22 | v3.8 | **其他页与关于 LMCL（第一阶段，other1）**：全局侧栏“其他”由 `JFXPopup` 改为独立 `OtherPage`（200px 二级菜单：帮助/反馈/关于 LMCL）；设置页删除帮助/反馈/关于三个 tab 及左侧支持分组；`AboutLMCLPage` 运行时读取上游 `thanks.json` 全部致谢，开发人员“指尖的宇宙 kerze”，捐赠者 `lmcl-donors.json` 空态且无入口；账户/实例/社区设置内嵌延后；`LauncherSettingsPage`/`Controllers` 为第 8/9 处代码侵入；community 测试 **135/0/0/0**、`checkTranslations` 通过；jar **11,168,252 B**、exe **12,369,404 B**（stub 1,201,152 B）；`other1` zip **64,896,960 B**，SHA-256 `6391112eba4774787464752e39d1cf060ba26997feb1bc73785df271f0a36ea0`；GUI 未实机验证 |
| 2026-10-22 | v3.7 | **侧栏菜单对比度与主页留白（home7）**：`CommunitySideBar.showOtherMenu()` 对 popup 根节点调用 `CommunityStyles.apply(menu)`，菜单 CSS 改为字面色（背景 `#26272b`、文字 `#ffffff`、hover 半透明白），菜单项再加内联 `-fx-text-fill:#ffffff` 兜底，修复“其他”菜单黑底灰字；`HomePane` 新增 `HOME_PADDING=new Insets(10,12,12,12)`，卡片/滚动条不再紧贴窗口边框；community 测试 **132/0**、`checkTranslations` 通过；jar **11,156,486 B**、exe **12,357,638 B**（交叉编译 stub 1,201,152 B）；`home7` zip **65,152,713 B**，SHA-256 `27f8a2e5…`；GUI 未实机验证 |
| 2026-10-22 | v3.6 | **左右独立滚动与黑底修复（home6）**：`HomePane` 由整页一个 `ScrollPane` 拆为左信息栏 `infoScroll`、右实例栏 `instanceScroll` 两个独立 `ScrollPane`（各自垂直滚动、无横向滚动）；删除 `.community-home` 纯色背景与 `.community-home-scroll` 透明 viewport 规则，回退上游默认背景，修复组件交互区域黑底；`MainPage` 在挂载社区首页时 `setPadding(Insets.EMPTY)`，主页贴住四边；`launchPane.toFront()` + `pickOnBounds=false` 保留；community 测试 **132/0**、`checkTranslations` 通过；jar **11,156,256 B**、exe **12,357,408 B**（交叉编译 stub 1,201,152 B）；`home6` zip **65,152,267 B**，SHA-256 `13df3f19…`；GUI 未实机验证 |
| 2026-10-22 | v3.5 | **主页视觉/布局（home5）**：`HomePane` 背景改主题纯色 `-monet-surface`、`ScrollPane` 视口透明；`MainPage` 去掉 `(56,0,96,0)` margin，内容上下贴边；`hbarPolicy=NEVER`、`community-home-scroll` 让垂直滚动条固定在页面最右；`HomePane.setMaxSize(MAX_VALUE,MAX_VALUE)` 保证纯色背景铺满；`launchPane.toFront()` + `pickOnBounds=false` 保持右下启动按钮最上层；`CommunityWidgetsProvider` 注销 `memory` 卡（11→10），`MemoryWidget` 源码保留但不再注册；community 测试 **132/0**、`checkTranslations` 通过；jar **11,156,294 B**、exe **12,357,446 B**（交叉编译 stub 1,201,152 B）；`home5` zip **65,152,232 B**，SHA-256 `d8f4dc6e…`；GUI 未实机验证 |
| 2026-10-22 | v3.4 | **步骤 20 源码 fork**：本地仓库 `/home/kerze/hmclce/hmclauncher`（基线 `da0e0c3b…`，提交 `8559bfb`/`c580531`），`CMakeLists.txt` 版本 `3.16.1000.0`；`HMCL.rc.in` 品牌字段/版权保留、`i18n.cpp` 产品名、Java 搜索目录 `.lmcl`/`%APPDATA%/LMCL`、`LMCL_*` 环境变量优先（兼容 `HMCL_*`）、只产 stub 的 CI + PE/ZIP 断言；MinGW i686 交叉构建 stub **1,201,152 B**（SHA-256 `89b58fa0…`），拼接 exe **12,357,287 B**（`2a9e0e7a…`）、jar **11,156,135 B**（`0626a033…`）；`home4-stub` zip **64,878,002 B**（`1752054a…`）仅供测试；Windows 实机与 10 项行为验收待执行 |
| 2026-10-22 | v3.3 | **交互修复与主页改版（home3）**：修复公告空壳吞事件（`MainPage` 首页提到最上层、隐藏 `announcementPane`、`updatePane`/`launchPane` 置前）、仪表盘置底与卡片裁切（`HomePane` 只设最小高度、内容自适应、整页 `ScrollPane` 可滚动）；新增全局 64px `CommunitySideBar`（主页/下载/设置/其他 + 其他菜单），`DecoratorController` 包 `HBox(sidebar, navigator)` 成为第 7 处代码侵入；主页改为左信息栏（账号/活动/最近世界）+ 右实例栏（启动/置顶/存储/Java/内存/快照/崩溃/体检/网络）；三份 i18n 各 9 个 `community.sidebar.*`；`:HMCL:compileJava`、`:HMCL:checkTranslations`、community 测试 **132 全绿**、`:HMCL:shadowJar` 10,924,834 B、`:HMCL:makeExecutables` exe 11,378,978 B（stub 454,144 B）；测试包 `dist/LMCL-3.16.1000-win-x64-home3.zip` **64,446,081 B**，SHA-256 `52d9dd6d19fdfd5f9acabc94b05b9ff9681abc3124e19f956c8cad81276eb96e`；GUI 未实机验证 |
| 2026-10-22 | v3.2 | **视觉收口（步骤 19）**：新增 `community.ui.CommunityStyles`（节点级样式表、幂等、资源缺失只 WARNING）与 `assets/css/community.css`（headline/title/body/label/metric、status pill、日历 5 档、估算虚线框）；11 个社区根节点接入；`HomePane` 问候语改用 `community-headline`、运行状态加语义 pill；日历 class 从 `-community-activity-level-*` 统一为 `community-activity-level-*`。`compileJava`/`checkTranslations`/community 测试 **132 全绿**、`shadowJar`/`makeExecutables` 通过；jar **10,920,997 B**、exe **11,375,141 B**；测试包 `dist/LMCL-3.16.1000-win-x64-home2.zip` **64,173,728 B**，SHA-256 `d5b8f283…`；GUI 主题矩阵未实机验证；home1/fix1/fix2 旧包已删除 |
| 2026-10-22 | v3.1 | **主页重构与侧栏移除（home1）**：删除 `RootPage.Skin` 的 200 px 文本侧栏，`DecoratorAnimatedPageSkin` 在 `left` 为空时不限宽；新增 `HomePane`（顶部导航 + 主列/右栏 + 账户卡 + 运行状态）接管首页，`DashboardPane` 保留不再挂载；入口收进主页（实例/下载/设置/更多/账户/Terracotta/反馈）；更新提示迁入 `HomePane`。第 5/6 处代码侵入（`RootPage.java`、`DecoratorAnimatedPage.java`，用户 m07850 批准）。community 测试 **132 全绿**、`checkTranslations` 通过；jar **10,918,523 B**、exe **11,372,667 B**；测试包 `dist/LMCL-3.16.1000-win-x64-home1.zip` **64,432,996 B**，SHA-256 `c6759cb1…`；GUI 未实机验证 |
| 2026-10-22 | v3.0 | **接管上游下载设置页（fix2）**：`DownloadSettingsPage` 删除三档 `DownloadSource` 选择器，改为嵌入 `CommunityDownloadSettingsPane`（主源/镜像/测试/保存），保留 `defaultAddonSource`；这是第 4 处代码侵入（产品批准，00 §4.2/§13 #21）。`compileJava`、`checkTranslations`、community 测试 **132 全绿**；jar 10,907,727 B、exe 11,361,871 B；新包 `dist/LMCL-3.16.1000-win-x64-fix2.zip`（64,147,064 B，SHA-256 `bdae2cb4…`）；`DownloadSettingsPage.class` 已含社区编辑器引用 |
| 2026-10-21 | v2.9 | **Windows 测试包启动误迁移修复**：首包双击报 `java.io.IOException: Signature is missing`（`IntegrityChecker.verifyJar:89`）。根因是 `CommunityBootstrap` 调用 `Main.main`，被上游 `UpdateHandler.isNestedApplication()`（`UpdateHandler.java:265-275`）误判为旧启动器嵌套调用，进而 `performMigration()` 尝试自更新；未签名 jar 校验失败。修复：删除 `Main.main` 调用，复刻 `checkDirectoryPath()` 后直接调 `EntryPoint.main`；`1923b2e0f`。重新构建 jar 10,908,455 B / exe 11,362,599 B，community 测试 **132 全绿**，`checkTranslations` 通过；Linux 无 DISPLAY 冒烟不再出现迁移日志。新包 `dist/LMCL-3.16.1000-win-x64-fix1.zip`；旧包作废 |
| 2026-10-21 | v2.8 | **步骤 16 已落地**：新增 `community.snapshot`（`SnapshotStore` 内容寻址对象库 + 原子 manifest/锁/保留策略/GC；`SnapshotScanner` 白名单与强制复制；`SnapshotCapture` 预算内硬链接/复制、超 2 GiB 降级 manifest-only；`SnapshotRestore` pre-restore + diff + 逐文件校验覆盖 + 失败汇总；`SnapshotService` 唯一 UI 入口；`SnapshotHashCache` LRU 5000；`SnapshotCaptureListener` 消费 08 退出事件，存活 ≥ 阈值才采集）；`community.dashboard.builtin` 新增 `snapshot` 2×1/order 45（Provider 共 11 卡）与 `SnapshotWidget`/`SnapshotManagerDialog`/`SnapshotDiffDialog`；`CommunityBootstrap` 启动清理；3 份语言各约 40 个 key；`:HMCL:compileJava`、`:HMCL:checkTranslations`、community 测试 **132 全绿**、`shadowJar` 10,907,366 B；文档 16 §10/README/审查记录 §24 同步 |
| 2026-10-21 | v2.7 | **步骤 15 已落地**：新增 `community.play`（`PlayRecordStore` 数据层：schema 1、400 天裁剪、12 h 截断、原子写/损坏隔离；`PlayRecordTracker`：08 事件串行链、阈值判定、残留会话估算恢复）与 `community.dashboard.builtin` 四卡（recent-worlds / storage / activity-calendar / health-check + Provider 扩到 10 个 descriptor）；`ActivityCalendarWidget` 5 档 CSS 类与 `community-activity-estimated`（CSS 归 19）、`HealthCheckWidget` 只读 `last.json`/复查、`StorageWidget` 复用 `MemoryStatusBar`、`RecentWorldsWidget` quick-play；`DashboardPane.appendMissingDefaults()` 仅对无 `updatedAt` 布局追加；3 份语言各 47 个新 key；`:HMCL:compileJava`、`:HMCL:checkTranslations`、community 测试 **120 全绿**、`shadowJar` 10,830,395 B；文档 15 §10/14 §5.2/19 CSS 同步 |
| 2026-10-21 | v2.6 | **步骤 14 已落地**：新增 `community.dashboard.builtin`（`BuiltinWidgets` + 启动/置顶/内存/崩溃/Java/网络六卡 + `CommunityWidgetsProvider` + `META-INF/services`）；`DashboardConfig.firstRun` 修复“用户清空后回升默认布局”；启动卡只调 `Versions.launch` 并只信 08 事件；崩溃卡按高度显示 2/4 条；内存卡紧凑单行指标；41 个 i18n key ×3；修复启动任务失败 session 残留（08 §10.7：`LAUNCH_FAILED` + `LauncherHelper` 锚点 E）；`:HMCL:compileJava`、`:HMCL:checkTranslations`、community 测试 **108 全绿**、`shadowJar` 10,775,759 B；文档 14 §13/08 §10.7/00 §13 #11 同步 |
| 2026-10-21 | v2.5 | **步骤 12 已落地**：新增 `community.memory`（`MemoryReclaimer` 单例守护调度、`MemoryMetrics`、`MemoryBaseSettings`、`CommunityMemorySettingsPane`、`CommunityMemorySettingsProvider`）；6 个触发条件（开关/游戏稳定运行/间隔/可归还量/启动保护/`DisableExplicitGC`）、慢回收 ×3 降频（上限 60 分钟）、连续两次 >1s 自动停用（`autoDisabled` + `pause_too_long`）；手动归还 60s 冷却；设置存 `community-settings.json` 的 `memoryBase` 段并保留未知字段；Bootstrap 注册回收器，设置面板新增「内存」分组（ORDER=16）；3 份语言各 21 个 key。`compileJava` 通过，community 测试 **102 个全绿**（本步新增 20），`shadowJar` 10,733,144 B 且 `Main-Class` 未变；`ec7c55e6f` → `1c16cc329`。12 §10 记录 7 条偏差、限制与 DoD 状态（三平台/GUI 未验） |
| 2026-10-21 | v2.4 | **步骤 11 已落地**：新增 `community.jvm`（`JvmPreset`/`JvmMemoryOptions`/`JvmArgValidator`/`JvmArgWriter`/`JvmWizardStore`/`JvmPresetWizard`），11 条冲突规则（文档 8 条 + ZGC 版本/位宽/小堆/物理内存）、唯一写入口走 `GameSettings.Instance.jvmOptionsProperty()`、状态存 `community-settings.json` 的 `jvmWizard` 段；`noOptimizingJVMOptions=true` 时提供“补齐完整 G1 参数”勾选；3 份语言各 47 个 `community.jvm.*` key。`compileJava` 通过，community 测试 **82 个全绿**，`shadowJar` 10,711,716 B 且 `Main-Class` 未变；`f51e38183`/`a54a05502` → `35c42aa11`。11 §10 记录 API、7 条偏差与下游约束 |
| 2026-10-21 | v2.3 | **步骤 10 已落地**：新增 `community.CommunityConfig`（`DISCO_API_OVERRIDE` 空串=镜像未部署）、`community.java`（`CommunityJavaMirror/JavaChoice/JavaPreference/JavaRecommendPane/CommunityJavaSettingsPane/Provider`），设置面板新增 Java 分组（`ORDER=25`），`CommunityBootstrap` 调 `CommunityJavaMirror.install()`；3 份语言各 25 个 key。偏好存 `community-settings.json` 的 `java` 段（`preferredDistribution`，缺省/未知→ZULU，`ANY`→null）；面板只抓取偏好 + 其余 2 个发行版，安装走 `Controllers.taskDialog` + `JavaManager.getInstallJavaTask`，重名中止。`compileJava` 通过，community 测试 **54 个全绿**，`shadowJar` 10,672,731 B 且 `Main-Class` 未变；`eb421e004` → `115e9f932`。10 §10 记录 API、7 条偏差与下游约束 |
| 2026-10-21 | v1.5 | 产品定名 **Lumina Minecraft Launcher（LMCL）**：03 身份字段、02/18 发行命名、17 守护二进制名、20 VERSIONINFO 字段全部回填；新增 00 §13 #18（定名决策）与 §9 商标复核风险行；确立命名分层——Gradle 内部产物仍为 `HMCL-<version>.*`（不改构建脚本），发行时在 `dist/` 重命名为 `LMCL-<version>-<arch>.<ext>`（`win-x86`/`any`/`all`），附录 `build-release.yml` 已加改名步骤；本地 git 仓库 `lmcl/` 从 v3.16.4 初始化（`main` 分支 + `patches/BASELINE.txt`，origin 暂缺、upstream 指向官方） |
| 2026-10-21 | v1.6 | **步骤 03 已落地并本地构建验证**（提交 `cf3ff6ecb`/合并 `722e646e5`）：`Metadata` 四字段改 LMCL、`hmcl.community.baseline{,.commit}` 注入 `assets/hmcl.properties`、新增 `community/CommunityMetadata` 与 `LICENSE-ADDITIONAL-TERMS.md`；实测 `HMCL-3.16.1000.jar` 的 `Main-Class` 仍为 `org.jackhuang.hmcl.Main`（05 才换 Bootstrap）。修正 CI 版本注入口径：上游只认 `VERSION_ROOT`+`BUILD_NUMBER` 环境变量（`-Pversion` 无效），`build-release.yml` 解析步骤同步注入 `UPSTREAM_BASELINE*`（改自 `patches/BASELINE.txt`） |'
| 2026-10-21 | v2.1 | **步骤 08 已落地**（用户 m04150）：新增 `community.launch`（`LaunchEventKind/LaunchEvent/LaunchEventListener/LaunchStartupTask/LaunchContext/LaunchTracker/CommunityLaunchHooks`）与 `community.preflight`（`Severity/CheckResult/PreflightReport/PreflightConfig/PreflightChecks/ModScanner/PreflightService/PreflightStartupTask`）、`ui/PreflightDialog`、ServiceLoader 任务注册与 22 个 i18n key；`LauncherHelper` 4 处调用 + 1 处链上组合（+13 行）。`compileJava` 通过，community 测试 36 个全绿，`shadowJar` 10,600,707 B 且 `Main-Class` 仍为 `CommunityBootstrap`；`effb0b4c4` → `3e440326e`。08 §10 记录 API、10 条偏差与下游消费约束 |
| 2026-10-21 | v2.2 | **步骤 09 已落地**：新增 `community.crash`（`CrashArchiver/CrashAnalysis/CrashHistory/CrashArchiveListener/CrashMeta/CrashEntry/LaunchPhase`）与 `community.ui.crash.CrashHistoryPane`；`CommunityLaunchHooks` 增补 `LaunchSession/sessionOf`；`CommunityBootstrap` 注册归档监听器；3 份语言各 34 个 `community.crash.*` key。归档 10 份/90 天/200 MiB、单文件 8 MiB 截断、索引 20 条且损坏自动备份；导出 zip、详情、删除、回滚（16 未落地时反射探测）。`compileJava` 通过，community 测试 **49 个全绿**，`shadowJar` 10,646,647 B 且 `Main-Class` 未变；`0f9dd2ada` → `8861fa751`。09 §10 记录 API、11 条偏差与下游消费约束 |
| 2026-10-21 | v2.0 | **步骤 07 重做并落地**（用户 m03806：默认不内置下载源 + 设置内配 1 主源/≤5 镜像）：新增 `community.download`（`CommunityDownloadSource/Sources/Installer/Tester`）、设置分组 `download`（ServiceLoader）、`CommunitySettings.section/putSection`、`CommunityDashboard.mount` 覆盖安装、24 个 i18n key、8 个单测；22 tests 通过、`shadowJar` 含 6 个类，`9f6ff2208` → `5f8a843dd`。文档 07 重写为 v2.0；13 增 `CommunitySettingsPanel.open(Window, sectionId)`；14 下载源卡改读社区配置；00 增 §13 #20 与 `community.download` 包；05/04 的 BMCLAPI 口径同步 |
| 2026-10-21 | v1.9 | **步骤 06 已实现**：`CommunityPaths`（`LMCL`/`.lmcl` 双根注入，显式覆盖与降级）、`OfficialDataLocator`（三平台候选，Windows 点目录 `.hmcl` 优先）、`OfficialDataImporter`（receipt 幂等、token 剥离、`private/**` 不读、原子写回滚）、设置面板数据组可扫描/导入；`CommunityPathsTest`+`OfficialDataImporterTest` 共 14 测试通过，`shadowJar` 验证；`eca4938f0` → `949437ef2`。遗留：首页一次性提示条、重启引导、GUI 验证 |
| 2026-10-21 | v1.8 | **步骤 13 框架已实现**：`community.dashboard`（布局引擎/配置/组件契约/网格/面板）、`CommunitySettingsPanel`（三组+扩展点）、`CommunityUpdateDialog`、`MainPage` 挂载、i18n 与布局单测；`1bdaee0a1` → `d13a00bec`。编译/单测/shadowJar 通过（33 个 `community/` 条目，`Main-Class` 不变）；无组件提供者时首页保持上游原样，14 合入后再回归。13 §10 记录实现状态与偏差（数据组暂不依赖 06） |
| 2026-10-21 | v1.7 | **官网与更新策略定案**：`PUBLISH_URL=https://www.kerzeftu.top/lmcl`；不做自动更新（00 §13 #19），新增 `community.update.CommunityUpdateNotifier`（只读 Releases API、只提示跳仓库，仓库留空即静默）；05 重写为《禁用自动更新与更新提示》；`CommunityBootstrap` 与 `Main-Class` 已实现并编译验证（jar `Main-Class: org.jackhuang.hmcl.community.CommunityBootstrap`）；13 增加"首次启动更新提示 + [检查更新]"UI 契约；03/18/00 的 URL 与阻断项同步 |
| 2026-10-21 | v1.4.1 | 引用复核收口：脚本两轮校验 31 个 md 的 `文件:行` 引用（0 越界），修正 `StyleSheets` 行号区间（`:48-68/161-188/190` 等）；`EntryPoint.java:48`/`Launcher.java:158` 消歧义为完整模块路径；`HMCL/...` 简写全部展开；补记审查记录 §10 |
| 2026-10-21 | v1.4 | 第二轮源码核对：clone HMCLauncher 逐段核对 stub 行为（Java 查找、`AttachConsole` 选 java/javaw、**不转发命令行参数**、资源行号、`project(... VERSION)` 注入、manifest 仅 `asInvoker`）并实测官方发布二进制（exe 10,907,431 B = stub 454,144 B + jar，jar 恰从偏移 454,144 开始）；引用审计确认 0 虚构 API；修正 `Profiles`→`SettingsManager` 等类名/路径，并复核启动钩子行号（`launchingLatch.countDown()` 为 `:981`，同时修正 `INTERRUPTED` 判断 `:982–983`）；重写步骤 20 §2.3/§3.2–3.7/§4/§5、00 §4.4，新增审查记录 §9 |
| 2026-10-21 | v1.3 | 设置入口收口：新增 13 §3.10 `CommunitySettingsPanel`（dashboard/about/data 三组 + `SettingsSectionProvider` 扩展）；03 增 `CommunityMetadata`/基线构建属性；06 补 `OfficialDataLocator.scan()/lastScan()`；`JvmMemoryOptions` 归属从 12 移到 11；19 类名与归属修正；CI 增加 `build/*` 触发；排期算术修正为逐项 23.8–32.1 周（关键路径 24–28 不变） |
| 2026-10-21 | v1.2 | 全量一致性收口：每步头部与总表口径统一；08 启动钩子/体检报告、09 崩溃历史、10 Java 偏好、11 JVM 向导、12 内存 API、13 仪表盘 schema、14 六卡契约、15/16 卡片归属全部对齐；排期改为关键路径 24–28 周（逐项 23.8–32.1）；**包名 relocate 定为首发 No-Go**（00 §13 #9）；补 03 版本号事实核对、04 门禁证据、06/08/12/14/16/17/18 回填闭环；链接与 U+FFFD 全量校验通过 |
| 2026-10-21 | v1.1 | 基线改为 v3.16.4（release/3.16）；重写总纲 §4（代码侵入固定 3 处 + 构建/资源分级登记 + Bootstrap 属性注入）、§5.3（L2 改 HKCU 守护）、§6（阶段/优先级/工期全部对齐，总工期改为关键路径 24–28 周、逐项累加 23–32 周）；新增步骤 20（HMCLauncher 重建）、审查记录；README 步骤表与决策速览同步更新 |
| 2026-10-21 | v1.0 | 依据《HMCL社区版开发包》重建完整文档：总纲 + 19 篇步骤 + 附录（关键类/调试参数/CI 原文/原始资料） |

---

> 本项目基于 HMCL（GPLv3）开发。分发时须遵守 GPLv3 及其附加条款：修改版本须修改名称或版本号、不得移除版权声明、不得添加新的附加要求。完整义务见 [§3 法律与合规要求](docs/00-整体要求.md#3-法律与合规要求硬约束)。
