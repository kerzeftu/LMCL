# HMCL 社区版 · 开发文档

> 本文档为项目唯一权威依据，自包含全部决策与实施细节。
> 历次讨论中的专项分析作为附件保留，供需要时查阅细节。

**附件**
- `HMCL许可证与命名限制分析.md` —— 条款原文、命名边界、分发 Checklist、FAQ
- `HMCL包名改造方案-Relocate.md` —— relocate 配置、验证命令、14 项改造清单
- `HMCL-vs-Prism-基座选型决策.md` —— 三方加权评分（含美西螈评估）
- `HMCL-首页组件化设计方案.md` —— 组件架构与清单
- `PCL内存优化-原理解析与HMCL移植设计.md` —— 原理、平台实况、服务化设计
- `hmcl-community-ci/` —— CI 工作流配置

---

# 第一部分 · 项目定义

## 1.1 定位

基于 HMCL 做社区版 Minecraft 启动器，具备独立产品身份。

## 1.2 三条核心约束

1. **中国大陆用户可用** —— 保留并可自主配置下载能力
2. **MD3 + 莫奈取色** —— 现代观感
3. **自身改动相对固定** —— 长期只合并上游，不做大规模重构

## 1.3 三条不可动摇的原则

| 原则 | 说明 |
|---|---|
| **GPLv3 合规** | 满足第 7 条附加条款；不得添加新的附加要求 |
| **改动外挂优先** | 新功能放新 package，不碰上游现有文件 |
| **绝不阻断启动** | 任何增强功能失败都必须静默降级，游戏照常启动 |

第三条是硬约束，贯穿所有功能设计。

## 1.4 差异化方向

MD3 动态取色**不再是差异化卖点** —— 上游 `HMCL/build.gradle.kts` 已引入 MonetFX（与 JFoenix 混用，说明 UI 层正在 MD2→MD3 迁移中）。

因此差异化应从"好不好看"转向"好不好用"：**启动保障**与**性能优化向导**。

---

# 第二部分 · 法律与合规

> 本部分为条款梳理，不构成法律意见。涉及商业化或大规模分发前建议咨询专业人士。

## 2.1 许可证构成

| 文件 | 内容 |
|---|---|
| `LICENSE` | GPLv3 全文（35 KB 标准文本） |
| `README.md` § License | **附加条款** |

⚠️ 附加条款写在 README 里，**不在 LICENSE 中**。分发时两部分必须一起带。建议在仓库中单独建 `LICENSE-ADDITIONAL-TERMS.md` 抄录原文。

## 2.2 附加条款原文（中英一致）

> 该程序在 GPLv3 开源协议下发布，同时附有以下附加条款。
> **附加条款（依据 GPLv3 开源协议第七条）**
> 当你分发该程序的修改版本时，你必须以一种合理的方式修改该程序的名称或版本号，以示其与原始版本不同。
> 你不得移除该程序所显示的版权声明。

英文版：

> When you distribute a modified version of the software, you must change the software name or the version number in a reasonable way in order to distinguish it from the original version.
> You must not remove the copyright declaration displayed in the software.

**共两条义务**：① 改名或改版本号；② 不移除版权声明。

## 2.3 条款号标注的一处偏差

README 将"必须改名"标为 7(c)、"保留版权声明"标为 7(b)。对照 GPLv3 原文，二者**同属 7(b)** 允许的类型：

- 7(b) **(0)** 要求保留指定的合理法律声明或作者归属 → 对应"不得移除版权声明" ✅
- 7(b) **(1)** 要求修改版以特定合理方式标记为不同于原版 → 对应"必须改名"

义务本身有效，仅索引不准。

## 2.4 由此推出的重要规则

**GPLv3 7(c)：下游不得添加新的附加要求。**

只能添加"附加许可"（放宽），不能添加"附加要求"（收紧）。因此：

| 可以 | 不可以 |
|---|---|
| 商用、收费 | 添加"禁止商用"条款 |
| 修改、再分发（须同许可证 + 提供源码） | 添加"必须额外署名本项目" |
| 添加放宽性质的许可 | 闭源、更换许可证 |

## 2.5 命名边界

| 方案 | 示例 | 附加条款 | 混淆风险 | 建议 |
|---|---|---|---|---|
| 完全独立名称 | "Kaleido Launcher" | ✅ | 最低 | ⭐ 推荐 |
| 独立名 + 标注来源 | "Nova Launcher——基于 HMCL" | ✅ | 低 | ⭐ 推荐 |
| HMCL + 后缀 | "HMCL-Nova" | ✅ | 中 | 可接受 |
| 仅改版本号 | "HMCL 3.13-community" | ⚠️ 字面合规 | 高 | ❌ |
| 沿用原名 | "HMCL" | ❌ 违反 | 极高 | ❌ |

条款原文是"名称**或**版本号"，只改版本号严格讲字面满足 —— 但这正是条款要避免的情形，**不要钻这个空子**。

社区现有 fork 多用 `HMCL-<标识>`（如 `HMCL-sfc`、`HMCL-iFlute`），规范但搜索仍会与官方混合。

## 2.6 商标：GPL 不管这一块

开源许可证授予**版权**，不授予**名称权**：

| 许可证 | 关于商标 |
|---|---|
| Apache-2.0 | 第 6 条明确不授予商标许可 |
| MPL-2.0 | 第 2.3 条不授予贡献者商标权 |
| **GPL-3.0** | **默认不提**；7(e) 只是允许添加放弃商标权的条款 |
| MIT / BSD-3 | 不提 / 仅"不得用贡献者名背书" |

HMCL 的附加条款中**没有商标条款**（未使用 7(b)(3) 这一类型），即上游未主动主张。但这**不等于可以随意使用其名称** —— 商标权独立于版权存在，且 "HMCL" 的实际注册状态无法从公开渠道确认。

**结论：正因为状况不明，独立名称最稳妥。** 这既是法律避险，也利于建立自身产品认知。

## 2.7 分发 Checklist

发布前逐项确认：

- [ ] 产品名已改变（不使用 "HMCL" / "Hello Minecraft! Launcher"）
- [ ] 版本号独立体系（不沿用 HMCL 版本号）
- [ ] 程序内版权声明未移除（关于页、源码头、第三方组件声明）
- [ ] 随附 GPLv3 全文
- [ ] 随附 HMCL 附加条款原文
- [ ] 修改文件带显著声明（改了什么、修改日期）—— GPLv3 5(a)
- [ ] 提供对应源码（公开仓库即可）
- [ ] 标注"基于 HMCL（GPLv3）开发"及上游仓库地址
- [ ] 未添加任何新的附加要求
- [ ] 崩溃/遥测不发往上游服务器
- [ ] 自更新已禁用或指向自有源
- [ ] 图标已重新设计

---

# 第三部分 · 基座选型

## 3.1 结论：HMCL

加权总分：**HMCL 4.35 / Prism 3.05 / 美西螈 2.70**

| 维度 | 权重 | HMCL | Prism | 美西螈 |
|---|:--:|:--:|:--:|:--:|
| 国内下载可用性 | 25% | **5** | 2 | 4 |
| UI 改造难度（MD3） | 20% | **4** | 1 | 3 |
| CI / 构建复杂度 | 15% | **4** | 1 | 3 |
| 长期同步痛苦度 | 15% | **4** | 3 | **1** |
| 许可证与合规负担 | 10% | **4** | 3 | **1** |
| 运行时性能 | 5% | 3 | **5** | 2 |
| 平台覆盖 | 5% | **5** | 4 | 3 |
| 中文社区与用户基础 | 5% | **5** | 2 | 4 |

## 3.2 决定性理由

- **BMCLAPI 全链路镜像**（MC 本体、库、资源、Forge/Fabric）—— 最难自建的部分，HMCL 白送
- **三模块分层**（HMCLBoot / HMCLCore / HMCL）—— 新功能可完全外挂
- **上游已在引入 MonetFX** —— MD3 方向顺势，非逆流
- **单 runner 出全产物** —— exe 是 stub + jar 字节拼接，不依赖目标平台
- **平台覆盖最广** —— Win/Linux/macOS/FreeBSD，以及 x86/ARM/RISC-V/LoongArch/MIPS

补充：Prism 那 5% 的性能优势其实兑现不了多少 —— 启动器与游戏是两个独立进程，启动器自身内存不影响游戏帧率。

## 3.3 已排除的候选

| 基座 | 排除原因 |
|---|---|
| PCL2 / PCL CE | .NET + WPF，Windows 独占。官方明确不做 Mac 版；PCL CE 的 macOS 仅"交叉编译"，产物无法运行 |
| BakaXL / FluentLauncher | 同上，Windows 独占 |
| MultiMC | 开发基本停滞，要 fork 就 fork Prism |
| **Prism** | Qt Widgets 用不了 Qt 的 M3 style（那属 Qt Quick Controls）；官方 QML 重写已放弃（Issue #980 因冲突过多于 2025-05 关闭）；UI 层 367 文件 / 约 4.4 万行；国内无文件级镜像 |
| **美西螈 / Modrinth App** | 混合许可证（桌面包 GPL-3.0-only，但内含 PCL 自定义许可证的 WikiEntries 快照、MIT 的日志查看器，且同步时不得引回 Modrinth 品牌资产）；两级同步且仓库内无 upstream sync workflow；继承 Tauri 的 WebView 病（Issue #421 内存不回收、#422 Linux 崩溃） |
| XMCL | Electron，内存偏重 |

美西螈实测（2026-10-20）：4,250 文件 / 121.76 MB（约 35MB 为二进制资产）；Rust 496 文件、Vue 531、TS 988；建仓 2026-07-13；551 stars / **95 open issues**。

**要学的是它的产品思路（首页组件化、拖拽识别资源、模组版本回滚），不是 fork 它。**

---

# 第四部分 · 仓库与工程

## 4.1 分支策略

**不要跟 `main`** —— 那是 beta 开发分支。跟 release 分支（如 `release/3.13`）。

## 4.2 改动策略：侵入式 3 处 + 新增式

**侵入式（压到最少）**

| # | 位置 | 改动 |
|---|---|---|
| 1 | `Metadata.java` | 名称 + 版本号（README 指明的官方修改位置） |
| 2 | `MainPage.java` | 挂载 Dashboard（约 5–10 行） |
| 3 | `LauncherHelper` | 启动流程挂体检/优化钩子（约 3–5 行） |

下载源已从侵入式改为新增式（见第六部分），因此从 4 处降为 3 处。

**新增式（全部走这条）**

```
org.jackhuang.hmcl.ui.dashboard      首页组件化
org.jackhuang.hmcl.diagnose          启动保障
org.jackhuang.hmcl.setting.perf      性能策略
org.jackhuang.hmcl.setting.source    下载源配置
```

上游不会动它不知道的文件 —— 这是"改动固定、同步顺畅"的核心保障。

## 4.3 同步方式：patch 策略

```bash
# 一次性导出改动
git diff upstream/release/3.13..main > patches/community.patch

# 上游更新时：从干净上游起新分支，重新 apply
git checkout -b build/3.13.1 upstream/release/3.13
git apply patches/community.patch
```

优点：不堆积 merge commit、历史干净、改动可作为独立 patch 被审查。

若改动超过 20 处或高度分散，改用 merge。

## 4.4 CI

`.github/workflows/` 三个工作流：

| 文件 | 作用 |
|---|---|
| `sync-upstream.yml` | 定时拉上游，自动合并；成功开 PR，冲突开 issue |
| `ci.yml` | 每次 push/PR 验证编译 |
| `build-release.yml` | 打标签后构建并发布 Release |

关键配置（易错点）：

- JDK 17 + `java-package: 'jdk+fx'` —— **不能省**
- `xvfb-run` —— **不能省**，无头 Linux 需虚拟显示
- 只用 `ubuntu-latest`（1x 计费）；macOS 是 10x，Windows 是 2x
- 定时任务的仓库需 60 天内有提交，否则 GitHub 会停用 schedule

## 4.5 产物

官方四种：`.exe` / `.jar` / `.sh` / `.deb`。

exe 约 10.4 MB，是**便携版**（`HMCLauncher.exe` stub 444KB + fat jar 追加其后，免安装），不自带 Java。

启动器按 `jre-arm64` → `jre-x64` → `jre-x86` 顺序查找同级目录，找不到才用系统 JRE。

**想"开箱即用"不必上 jpackage** —— 把绿色版 JRE 放进 `jre-x64`，与 LMCL.exe 一起打 zip 即可，HMCL 原生认这个查找顺序，零改动。

对比 jpackage：只能产当前平台的包（需三平台 matrix），且 macOS 需签名证书否则报"无法验证开发者"。

---

# 第五部分 · 包名与身份改造

## 5.1 三项决策

| 项 | 决策 | 理由 |
|---|---|---|
| **包名** | **改** | 体现为独立程序 |
| **数据目录** | **不动** | 配置连续性 + 同步便利 |
| **崩溃上报** | **关闭**，仅留本地日志 + 弹窗 | 隐私与责任边界 |

## 5.2 包名改造：Shadow Relocate（不改源码）

用构建期字节码重定位，**源码保持 upstream 原样 → merge 零冲突**，仅产物 class 包名改变。

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    relocate("org.jackhuang.hmcl", "com.yourorg.launcher")
    // 只 relocate 本项目包名，不要碰第三方库
}
```

⚠️ 必须与已有的 fat jar 逻辑整合 —— HMCL 现有打包链（`makeExecutables`）产出的 exe 是 stub + jar 拼接，要确认 shadowJar 输出接进这条链，而非产生两套产物。

**已确认不存在的两个大坑**（查过仓库结构）：

| 常见坑 | HMCL 情况 |
|---|---|
| FXML 里硬编码类名 | ✅ **0 个 .fxml 文件**，UI 全代码构建 |
| 资源放在包路径下需同步移动 | ✅ 资源都在 `assets/` 下，不在 `org/jackhuang/hmcl/` |

## 5.3 必须先解决的 4 个前置问题

**① 字符串常量里的包名（最容易踩）**

```java
// CrashReport.shouldBeReport() —— 崩溃归属判断
if (!stackTrace.contains("org.jackhuang")) return false;

// CrashReporter.Hole.SOURCE —— 已知错误匹配
pair("org.jackhuang.hmcl.util.ResourceNotFoundError", i18n("crash.NoClassDefFound")),
```

第一个尤其关键：relocate 后若常量未被正确改写，判断失效，表现为**"崩溃了但不弹窗"**。Shadow 会尝试改写字符串常量，但**必须验证，不能假设**。

**② `META-INF/services`**

```
HMCL/src/main/resources/META-INF/services/java.net.spi.URLStreamHandlerProvider
```

类名未改写会启动报 `ServiceConfigurationError`，且错误在很底层，难排查。

**③ config.json 是否存了类名**

数据目录不动 → 老用户升级会读到旧配置。若存了包名字符串（Gson 序列化某些对象时可能带上），反序列化会失败。需 `grep` 真实 config.json 确认，必要时写迁移逻辑并给 config 加版本号。

**④ 崩溃输出的产品名**

```
---- Hello Minecraft! Crash Report ----
```

建议一并改（在 patch 里）。

## 5.4 崩溃上报：一个重要更正

读实际代码，`CrashReporter.uncaughtException` **全流程无网络请求**：

```java
LOG.error("Uncaught exception in thread " + t.getName(), e);   // ① 本地日志
CrashReport report = new CrashReport(t, e);
if (!report.shouldBeReport()) return;
LOG.error(report.getDisplayText());                             // ② 本地日志（完整）
Platform.runLater(() -> {
    if (checkThrowable(e)) {
        if (showCrashWindow) new CrashWindow(report).show();    // ③ 弹窗
    }
});
SettingsManager.shutdown();
LOG.shutdown();
```

`org.jackhuang.hmcl.countly.CrashReport` 中的 `countly` 是**历史遗留** —— 现在整个 `countly` 包只剩这一个类，只是格式化文本的数据结构，**不发往任何服务器**。

**你要的"仅本地日志 + 弹窗"，上游现状已经就是这个行为，无需改动。**

旧版 README 那条"必须删除 CrashReporter，否则错误会发到我的服务器"已过时。

**仍建议**：用抓包工具（或防火墙阻断）跑一次崩溃路径实测确认，代码分析可能有遗漏（如某处反射加载了遥测模块）。

## 5.5 真正需要主动关闭的：自更新

**这比崩溃上报实际得多。** HMCL 有完整自更新链路：

```
upgrade/UpdateChecker.java       检查更新
upgrade/UpdateHandler.java       处理更新
upgrade/IntegrityChecker.java    完整性校验
upgrade/RemoteVersion.java
assets/hmcl_signature_publickey.der   官方签名公钥
```

保留默认行为 → 用户点"更新" → **被替换成官方 HMCL，社区版消失**。

必须处理：

| 做法 | 说明 |
|---|---|
| **禁用自更新**（推荐首发） | 移除/隐藏更新入口，改为跳转 Release 页面手动下载 |
| 或指向自有更新源 | `-Dhmcl.update_source.override=<你的地址>` |
| **完整性校验一并处理** | 官方公钥校验你的产物必然失败，需 bypass |

调试参数 `-Dhmcl.self_integrity_check.disable=true` 可绕过，但正式版应直接改代码。

## 5.6 数据目录：保留但有共存风险

保留 `~/.hmcl`（Linux）/ `%APPDATA%/HMCL`（Windows）可让用户无缝迁移。

但若用户机器上**同时装着官方 HMCL**，两者会共用同一数据目录 —— 可能文件竞争、配置互相覆盖。

**建议**：提供一次性"导入现有 HMCL 配置"选项，之后切到独立目录。既照顾迁移，又避免长期共用。

若决定完全共用，至少在文档中说明"不建议同时运行两个启动器"。

## 5.7 改造清单（14 项）

```
□ 1. 引入 shadow 插件，配置 relocate
□ 2. 确认 shadowJar 输出接入现有 makeExecutables 链
□ 3. 验证字符串常量：CrashReport.shouldBeReport() 的 "org.jackhuang"
□ 4. 验证字符串常量：CrashReporter.Hole.SOURCE 的 ResourceNotFoundError
□ 5. 验证 META-INF/services 内容被正确改写
□ 6. 检查真实 config.json 是否含包名字符串，必要时写迁移
□ 7. 禁用自更新（UpdateChecker / UpdateHandler / 设置页入口）
□ 8. 处理 IntegrityChecker（或 bypass）
□ 9. 改 CrashReport 输出标题的产品名
□ 10. Metadata.java 改名称 + 版本号
□ 11. 崩溃路径实测：抓包确认无外发请求
□ 12. 数据目录：决定共用 or 一次性导入后独立
□ 13. 图标重新设计
□ 14. 关于页保留 HMCL 版权声明 + 标注"基于 HMCL 开发"
```

**工期约 3–5 天**（配置 1 天 + 逐项验证 2 天 + 自更新处理 1–2 天）。

**退路**：包名改造是纯机械工作，可延后。若觉得每次同步都要重跑验证清单太重，可**先不改包名发第一版，核心功能稳定后再补**。

---

# 第六部分 · 下载源策略

## 6.1 设计：空配置 + 拖入导入

**默认不预置任何下载源。用户首次实际使用下载功能时提示配置，配置文件支持拖入导入。**

定位为**中立工具**：启动器只提供下载能力，不预置镜像地址、不做地区判断。用户自行配置并承担相应责任。

## 6.2 为什么这样设计（关键发现）

原方案要改 `DownloadProviders.java` 的地区分支。但：

> **上游在 2025-11 刚重构了这块**（PR #4856，28 commits）：引入 `DEFAULT_AUTO_PROVIDER_ID`、`DEFAULT_DIRECT_PROVIDER_ID`、`AUTO_PROVIDERS` / `DIRECT_PROVIDERS` 两个 map，删除 `AdaptedDownloadProvider` 与 `BalancedDownloadProvider`，`DownloadProvider` 接口改为返回 URL 列表（`getVersionListURLs()` / `getAssetObjectCandidates()`）。

这是上游**最活跃的区域之一**，改它必然反复冲突。

改为新增式后：**不碰 `DownloadProviders.java` 一个字**，冲突风险从"中"降为"零"。

## 6.3 实现：复用 bmclapi.override

不需要重新实现 provider。HMCL 已有：

```
-Dhmcl.bmclapi.override=<root>   # 默认 https://bmclapi2.bangbang93.com
```

**导入配置 = 设置一个根地址**。天然支持任意 BMCLAPI 兼容镜像（如 `https://download.mcbbs.net`、`https://bsmirror.hkmc.online`）。

用户选"官方源"等价于不设置 override。

## 6.4 配置文件格式

```json
{
  "format": "hmcl-community-source",
  "version": 1,
  "name": "示例镜像",
  "comment": "可选，给用户看的说明",
  "source": {
    "type": "bmclapi-compatible",
    "root": "https://example.com"
  }
}
```

`type` 预留扩展，首发只实现 `bmclapi-compatible`。

## 6.5 文件名：双后缀 `.hmclsource.json`

| 方案 | 区分度 | 双击可查看 | 评价 |
|---|---|---|---|
| `mirror.json` | 低 | ✅ | 极易与整合包、配置备份混淆 |
| `mirror.hmclsource` | 高 | ❌ | 无文件关联，看内容要手动选打开方式 |
| **`mirror.hmclsource.json`** | **高** | **✅** | **推荐** |

**为什么不用纯自定义后缀**：HMCL 是便携版（stub + jar 拼接，无安装器），**注册系统文件关联不现实**。以 `.json` 结尾可借用系统已有的 JSON 关联 —— 配置文件是需要用户**审阅**的东西（它决定所有下载去向），可读性比区分度更重要。

**但判别必须以内容为准**（用户可随意改名）：

```java
for (File f : dropped) {
    if (f.getName().endsWith(".hmclsource.json")) {
        importSourceConfig(f);                    // 快速路径
    } else if (f.getName().endsWith(".json")) {
        // 慢路径：内容嗅探（只读前几百字节，别整文件解析）
        if (readFormatField(f).equals("hmcl-community-source"))
            importSourceConfig(f);
        else
            routeToModpackImport(f);              // 交给上游整合包逻辑
    } else {
        routeToExisting(f);                       // zip/mrpack 走原逻辑
    }
}
```

识别失败给明确反馈，**不要静默忽略**。导出时用 `<name>.hmclsource.json`，鼓励社区统一命名。

## 6.6 弹窗时机

**不要在启动时弹** —— 用户只是打开看看实例，弹窗是打扰。

应在**真正要下载时**弹（点下载游戏、装 Forge、下模组），此时有上下文：

```
用户点击下载
   → 检查是否已配置
   → 未配置 → 弹窗「您尚未设置下载源，请先进行配置」
               ├─ [拖入配置文件] / [手动填写] / [使用官方源]
               └─ 配置完成后继续原操作
```

"使用官方源"作为明确选项保留，让用户知情选择。

## 6.7 安全校验（不可省略）

配置文件里的 URL 决定所有下载请求去向。**2026 年已发现多起通过篡改镜像源注入账号窃取模块的案例**。

必须：

1. **强制 https**（拒绝明文 http，除非用户显式确认本地地址）
2. **URL 格式校验**（防注入、防畸形）
3. **导入前预览确认** —— 显示完整地址，让用户看清要去哪
4. **来源提示** —— 明确告知"配置文件来源需自行判断可信度"

可选增强：配置文件签名（Ed25519），社区发布带签名的推荐源。建议二期。

## 6.8 三个入口

| 入口 | 说明 |
|---|---|
| 拖入导入 | 拖 `.json` 到窗口 → 识别 → 校验 → 预览确认 → 应用 |
| 手动填写 | 设置页直接输入根地址，兜底 |
| 导出配置 | 导出成文件，便于社区分享 |

**"鸡生蛋"问题**（用户从哪拿配置文件）：导出分享（主要途径）+ 手动填写兜底。

可选第三个 —— 内置"已知镜像快捷按钮"，但要谨慎：放地址进去性质上仍是"预置"。建议做成**打开镜像官网链接**而非直接填地址，让用户从官方渠道获取。

## 6.9 拖放基建

HMCL 的 `MainPage` 已接入整合包拖入向导，JavaFX DnD 事件链现成：

```java
node.setOnDragOver(e -> {
    if (e.getGestureSource() != node && e.getDragboard().hasFiles())
        e.acceptTransferModes(TransferMode.COPY);
    e.consume();
});
node.setOnDragEntered(e -> highlight());   // 视觉反馈
node.setOnDragExited(e -> unhighlight());
node.setOnDragDropped(e -> { /* 识别并分发 */ });
```

**工期约 3–5 天** —— 与原方案相当，但长期同步成本从高降到零。

---

# 第七部分 · 功能规格

## 7.1 优先级总表

| 优先级 | 功能 | 工期 |
|---|---|---|
| **P0** | 启动保障三件套 | 4–6 周 |
| **P0** | 性能策略（Zulu + JVM 预设） | 2–3 周 |
| **P1** | 首页组件化 | 6–8 周 |
| **P1** | 内存优化（P0 基座 1 周 + P1 服务化 2–3 周） | 3–4 周 |
| **P2** | MD3 主题完善 | 跟上游进度 |

顺序理由：MD3 上游在做不必抢；组件化是锦上添花；启动保障与性能是硬需求。

## 7.2 启动保障三件套

### ① 启动前体检

点"启动"前几秒自动跑，输出红/黄/绿清单：

- Java 版本 × MC 版本 × 加载器 三者匹配（最高频的坑）
- 内存分配是否超过物理内存安全阈值
- 磁盘剩余空间
- 重复模组（同 modid 多版本共存）
- 缺失前置依赖

HMCL 已内置模组元数据解析引擎，能识别 `mods.toml`、`fabric.mod.json`、`modinfo.json`。要做的是在此基础上**离线构建依赖图并拦截**，而非等崩溃后排查。

### ② 崩溃现场还原

崩溃后自动读最新 crash-report，提取 stack trace 里非 `net.minecraft` / 非 forge 的包名，映射回具体模组 jar，结合"你两小时前刚更新了 X"给出嫌疑排序。

> AI 辅助编码在此处加速有限 —— crash-report 格式与三种 mod 元数据规范是精确领域知识，建议自己主导。

### ③ 实例快照与一键回滚

每次**成功启动**后自动存快照（只记模组文件名 + 版本 + 哈希 + 关键配置，体积可忽略）。更新后起不来 → 一键回到上一个确定能跑的状态。

**③ 最独到**：别的启动器只有单模组版本回滚，没有实例级快照。它让 ①② 从"告诉你哪里错了"升级成"直接帮你退回去"。

## 7.3 性能策略

### ① Java 默认发行版：Zulu

**改动面很小** —— HMCL 的 Java 下载已支持 **Disco API**（foojay 聚合），能列出 Zulu、Temurin、Liberica、Corretto 等几十种发行版。要做的不是"接入新发行版"，而是"在已有列表里设默认偏好"。

相关类：`java/JavaManager.java`、`ui/main/JavaDownloadDialog.java`、`HMCLCore/.../JavaInfo.java`、`HMCLCore/.../GameJavaVersion.java`。

侵入点 2–3 处：新增"默认发行版"设置项、Disco 列表排序时偏好置顶并默认选中、自动下载流程读取偏好。

**定位措辞要说对**：Zulu 的正确理由是**架构覆盖最广**（x86/64、ARM 32/64、MIPS、PPC、SPARC，含嵌入式）—— 正好对应 HMCL 能跑 RISC-V / LoongArch / MIPS 的差异化优势；以及 LTS 周期长（Java 8 支持到 2030）。

**不要用"提高效能"做理由**：

| 发行版 | 启动耗时 | 内存占用 |
|---|---|---|
| Eclipse Temurin | **5.6s** | 230M |
| Amazon Corretto | 5.95s | 220M |
| BellSoft Liberica | 6.05s | 219M |
| Oracle OpenJDK | 6.1s | 211M |
| **Azul Zulu** | **6.2s** | 215M |
| Alibaba Dragonwell | 6.45s | 216M |
| Microsoft OpenJDK | 6.9s | 219M |

8 款 JDK 同环境实测，**Zulu 排第 5，Temurin 最快**；内存占用各家 210–230M 几乎无差别。评测原文结论：差异可能在误差范围内，重测结果可能不同。

真正快 20–50% 的是 Azul **Zing**（Platform Prime，含 Falcon JIT + C4 收集器，**商业产品**）；免费的 Zulu Community 是标准 OpenJDK 发行版，无那些优化。

> 顺带：HMCL 官方 CI 就用 `distribution: 'zulu'` + `java-package: 'jdk+fx'`。

### ② JVM 参数预设

**HMCL 已做的（别重复造）**：

| 已有 | 位置 |
|---|---|
| G1GC 默认优化参数 | `DefaultLauncher.java`：`UseG1GC=true, MaxGCPauseMillis=50, G1NewSizePercent=20, G1ReservePercent=20, G1HeapRegionSize=32m, G1MixedGCCountTarget=5`，通过 `addUnstableDefault` |
| 内存自动分配 | `HMCLGameRepository` 预留系统 + 启动器所需后缩放；界面实时显示"已使用/总内存"与不足警示 |
| Java 版本匹配 | `GameJavaVersion` 完整映射（26.1+→25，1.20.5+→21，1.18+→17，1.17+→16，1.13+→8）；`JavaVersionConstraint` 分强制/建议两类 |
| 其他 | 进程优先级、渲染后端（Zink/Mesa）、Java 自动下载、实时内存状态条 |

**你要做的（真正空白）**：

| 空白 | 要做的 |
|---|---|
| JVM 参数预设 | 分档下拉（内存档位 × MC 版本 × 整合包规模），选中后写入参数框 |
| `-Xms` 默认等于 `-Xmx` | 预设里统一，避免堆运行中反复调整 |
| ZGC 选项 | 大内存档位（16G+）提供一套 ZGC，**整套替换**不与 G1 混用 |
| 整合包规模感知 | 按实例模组数量自动推荐档位 |
| 参数冲突检测 | 检测到用户参数含其他 GC 时，提示关闭默认优化参数 |
| 内存超限自动干预 | 可用内存不足时主动建议下调 |

**一句话定位：HMCL 缺的不是"优化能力"，是"优化向导"。**

> 背景：MC 百科的 Java 8 优化参数教程专门提醒 —— 抄来的 CMS 参数会与启动器自带的 G1GC 冲突导致起不来，必须打开"不添加默认的 JVM 优化参数"。这正是"向导"缺位的代价。

## 7.4 内存优化

### 7.4.1 PCL 原理与真相

PCL 的核心是 `PageOtherTest.MemoryOptimizeInternal`，对**系统内所有进程**调用：

```
EmptyWorkingSet(hProcess)
// 等价 SetProcessWorkingSetSize(hProcess, -1, -1)
```

官方描述："能将目前物理内存占用降低约 1/3，不仅限于 Minecraft！如果使用机械硬盘，这还可能会导致一小段时间的严重卡顿。"

**关键澄清：它并没有真的"释放内存"。** API 做的是把"可换出页面"从工作集移到 **Standby / Modified List**，页面**仍在物理内存里**，只是不再计入进程 Working Set。

明确不受影响：共享 DLL 代码页、`VirtualLock` 锁定内存、.NET 托管堆、**Private Bytes（虚拟提交量不变）**、内核不可分页池。

**真实起作用的三个机制**：
1. 触发 Modified Page Writer 把脏页刷到 pagefile —— 唯一"真"的释放
2. 可用内存计数真实上升，JVM 用 `-Xms` 提交大堆更容易成功
3. Standby 列表扩容，减少运行中的硬缺页

**真正受益**：长时间游戏后内存占用攀升（脏页堆积）。
**不受益**：刚开机、系统本就干净 —— 此时是纯开销。

**安全风险**：跨进程 `OpenProcess` 带 `PROCESS_SET_QUOTA(0x0100)` 被安全社区标记，Sysmon Event ID 10 可捕获，MITRE 归为 **T1562.001（Impair Defenses）**。

### 7.4.2 档位定义（最终版）

| 档位 | 定义 | 平台 |
|---|---|---|
| **L2** | 全量 `EmptyWorkingSet`，含系统进程与已提权进程 | Windows 专属，服务化 |
| **L0+** | JEP 346 周期性归还 + 占用透明化 | 全平台 |

**已取消 L1 中间档** —— 既然要承担服务化与提权的全部成本，中间档没有存在价值。

### 7.4.3 各平台实况

| 平台 | 结论 |
|---|---|
| **Windows** | ✅ `EmptyWorkingSet`；跨进程需 `SeDebugPrivilege` |
| **Linux** | ❌ **没有等价机制，也没有必要**。`malloc_trim` / `madvise` 只作用自身（且 `malloc_trim` 仅对 glibc 有效、best-effort）；`drop_caches` 需 root 且清的是 page cache 不是工作集，官方定位是"基准测试拿干净读数"，明确说"不是修复任何问题的手段，available 几乎不动，之后机器变慢，不该放进定时任务" |
| **macOS** | ⚠️ `sudo purge` 需管理员密码，执行期间可能冻结 2–5 秒；**Apple Silicon 上效果不可靠**（统一内存架构下可能引发 swap thrashing）。不作默认 |
| **FreeBSD** | ❌ 无等价物 |

### 7.4.4 跨平台基座：JEP 346（重点投入）

用 JVM 原生能力 —— **G1 周期性把未使用的堆真正归还操作系统**。全平台一致、零权限、真释放。

```
-XX:+UseG1GC
-XX:G1PeriodicGCInterval=30000        # 空闲多久后周期性 GC
-XX:G1PeriodicGCInvokesConcurrent     # 走并发周期，比 Full GC 温和
-XX:G1PeriodicGCSystemLoadThreshold   # 系统负载低于此值才算"空闲"
-XX:MinHeapFreeRatio=5
-XX:MaxHeapFreeRatio=10
-XX:+ShrinkHeapInSteps                # JDK 13+
```

| GC | 自动归还 OS | 时机 |
|---|---|---|
| G1 | ✅ 有条件 | Full GC 或空闲 region 足够多（配合 JEP 346 可周期性触发） |
| ZGC / Shenandoah | ✅ 自动 | 并发回收后主动释放 |
| Parallel / Serial / CMS | ❌ | 即使 Full GC 也不归还 |

两个用法：
1. **HMCL 自身** —— 游戏启动后就是后台进程，用 JEP 346 把内存还回去。全平台有效、零风险，呼应"启动器不该占着内存"
2. **游戏进程** —— 加 `G1PeriodicGCInterval`，真正改善"长时间游玩后内存占用攀升"

### 7.4.5 服务化架构（已定案）

```
LMCL.exe（普通权限 asInvoker，永不提权）
   │ 条件触发：可用内存 < 需求 × 1.25
   ├─► 命名管道 \\.\pipe\hmcl-memopt-<UserSID>
   │        ▼
   │   HMCLMemOptSvc.exe（Windows 服务）
   │        ├─ 校验调用方（路径 + 签名 + IL）
   │        ├─ 枚举进程 → EmptyWorkingSet
   │        └─ 返回 {processed, skipped, bytes}
   └─► 无论如何继续启动游戏
```

安装**只弹一次 UAC**，之后主程序永远普通权限。

**五个关键设计点**

**① 启动类型必须 Auto，不能 Manual** —— Manual 类型下普通权限进程**无法 `StartService`**。选 Auto（delayed start），服务空闲时只维持管道监听线程。

**② RequiredPrivileges 声明 `SeDebugPrivilege`** —— 跨进程 `OpenProcess` 需要它。注意这等同于管理员权限，正是"服务必须极小化 + 严格校验"的原因。（`RequiredPrivileges` 只能裁剪账户已有权限的子集，不能凭空提权）

**③ 调用方校验（不可省略）**

```rust
client_path.starts_with(hmcl_install_dir())?;   // 路径
verify_authenticode(&client_path, CERT)?;        // 签名
get_integrity_level(client_pid) >= Medium?;      // 完整性级别
```

服务一旦被任意进程调用，等于给本机任何程序开了"以 SYSTEM 裁剪所有进程"的后门。**不做签名校验就是真正的提权漏洞。**

**④ 命令接口必须极窄**

```
请求: {"op":"trim_all","nonce":"..."}
响应: {"processed":148,"skipped":12,"bytes":3758292992}
```

严禁 `{"op":"run","cmd":"..."}` 这类通用执行接口。

**⑤ 管道 ACL** —— 管道名带用户 SID，安全描述符只授权当前用户（读写）+ SYSTEM（完全控制）。

**服务用 Rust**：静态编译无运行时依赖、几百 KB、内存安全，适合高权限组件。源码随项目开源（GPLv3，是衍生作品的一部分）。

主程序侧用 JNA 调 `CreateFileW` 访问管道（Java 标准文件 API 无法可靠访问 Windows 命名管道），**硬超时 3 秒**，失败静默降级。

**必须提供卸载入口** —— 留一个用户找不到的高权限服务是严重的软件卫生问题。

### 7.4.6 风险清单（服务化后）

| 风险 | 等级 | 应对 |
|---|---|---|
| **服务被本地程序滥用** | **极高** | 三重校验 + 单一固定命令 |
| **杀软误报（T1562.001）** | **高** | 常驻 SYSTEM 服务做跨进程 trim 特征明显。**发布前主动向 Defender / 360 / 火绒提交白名单申诉** |
| 高权限进程常驻 | **高** | 服务极小化 + RequiredPrivileges 裁剪 + 明确卸载入口 |
| PPL 进程 trim 失败 | 中 | 静默跳过，计入 `skipped` |
| 机械硬盘卡顿 | 中 | 检测磁盘类型，HDD 上默认关闭 |
| 服务版本不匹配 | 中 | 启动时校验，提示重新部署 |
| 遗留孤儿服务 | 中 | 卸载流程必须含服务删除 |
| 许可证风险 | **高** | **只参考原理，重新实现，不复制任何 PCL 代码**（PCL 用自定义许可证，非 GPL） |

### 7.4.7 能力探测写配置

配置里缓存**实测能力等级**，而不是操作系统（同一 OS 下权限、架构、沙箱差异很大）：

```json
{ "memoryOptimize": {
    "enabled": true,
    "detectedCapability": "L2",
    "userOverride": null,
    "lastResult": "success" } }
```

### 7.4.8 执行顺序

| 阶段 | 内容 | 工期 |
|---|---|---|
| **P0 基座** | JEP 346 归还 + 占用 Top 5 透明化 + 首页组件 | 约 1 周 |
| **P1 服务化** | Rust 服务 + installer + JNA 客户端 + 设置 UI | 约 2–3 周 |
| **P2 发布准备** | 杀软白名单申诉、多环境测试、文档 | 约 1 周 |

**先做 P0** —— 零风险、全平台有效、独立可用。万一 P1 卡在杀软或权限阻力，功能不至于归零。

**后路**：把 IPC 层抽象出来，若评估觉得常驻 SYSTEM 风险过大，可换**计划任务变体**（高权限进程只在需要时存在几秒然后退出，主程序侧几乎不用改）。

## 7.5 首页组件化

### 7.5.1 可行性（实测）

| 事实 | 数值 |
|---|---|
| `MainPage.java` | **20 KB** |
| `RootPage.java` | 10 KB |
| `ui/construct/` | **59 个现成组件** |
| `SVG.java` | 39 KB，**已接入 Monet 颜色系统** |
| `ui/` Java 文件 | 243 个 / 2.19 MB |

对比 Prism 重写 UI 需动 4.4 万行，HMCL 首页组件化只需新增代码 + 极小侵入点。

### 7.5.2 架构

```
org.jackhuang.hmcl.ui.dashboard
├── DashboardPane.java        容器（自适应网格）
├── DashboardWidget.java      组件抽象基类
├── WidgetRegistry.java       注册表
├── WidgetDescriptor.java     元数据
├── DashboardConfig.java      持久化（独立 JSON）
├── DashboardEditor.java      编辑模式
└── widgets/ ...
```

```java
public abstract class DashboardWidget extends VBox {
    protected abstract Node buildContent();  // 构建静态 UI
    public abstract void refresh();          // 必须异步
    public void dispose() {}                 // 必须解绑监听
}
```

持久化用**独立 JSON**（`~/.hmcl/dashboard.json`），**不要往 HMCL 的 `Settings` 里塞字段**，那会增加冲突面。

### 7.5.3 可复用资产

`TwoLineListItem`（标准列表项）、`AdvancedListBox`（分组侧栏）、`RipplerContainer`（MD 波纹）、`SpinnerPane`（加载态）、`ContainerAnimations`、`TransitionPane`、`TaskListPane`、`FXUtils`。

特别是 **SVG 已走 Monet 颜色** —— 组件化首页能自动跟随动态取色，与 MD3 方向天然一致。

### 7.5.4 组件清单

**P0（首发）**：`LaunchWidget`(2天)、`PinnedInstancesWidget`(3天)、`GreetingWidget`(0.5天)、`UpdateNoticeWidget`(1天)

**P1**：`RecentWorldsWidget`(3天)、`StorageStatusWidget`(2天)、`ActivityCalendarWidget`(4天，需自建游玩记录基础设施)

**P2**：`HealthCheckWidget`(3天)、`SnapshotWidget`(2天) —— 与启动保障联动

### 7.5.5 两个必须注意的坑

1. **内存泄漏** —— 美西螈 Issue #421 就是"切页面内存不回收"。JavaFX 里监听器持有强引用是常见来源，`dispose()` 必须实现。开发期用 ScenicView 或 JFR 周期性检查首页反复进出后的堆占用
2. **首页卡顿** —— 所有 I/O 必须异步；组件数据缓存 + 增量刷新；大量实例时参考上游 GP-5174 的做法

### 7.5.6 执行建议

**先做零侵入版** —— 把 Dashboard 做成 RootPage 侧栏的新导航项（完全不动 MainPage），验证框架 + 4 个 P0 组件（约 3–4 周）。收集反馈后再决定是否替换首页。这样即使不替换，产出也完整可用。

---

# 第八部分 · 开发规范

## 8.1 包结构约定

新增功能一律放新 package，命名见 4.2。

## 8.2 侵入点纪律

侵入式改动**只允许增加，不允许扩散**。一旦从 3 处扩散到十几处，同步成本会急剧上升。

> 教训来自 Prism：官方 QML 重写的 Issue #980 关闭原因就是"冲突数量已增长到 impractical"。

## 8.3 错误降级规范

所有增强功能必须遵守：

```java
try {
    result = optimizer.optimize();
} catch (Throwable t) {
    LOG.warn("Memory optimize failed, skipping", t);
    // 静默降级，绝不阻断
}
```

## 8.4 AI 辅助编码的收益边界

**能省 50–70%**：组件骨架、JSON 持久化与迁移、CSS、数据模型类、JavaFX 布局嵌套、单元测试、文档。

**几乎帮不上甚至添乱**：
- 理解 HMCL 现有架构（`Profiles`、HMCLCore API 边界）—— 必须自己读源码
- 侵入点选择（架构决策）
- 异步与内存泄漏处理 —— AI 爱写"能跑但泄漏"的代码
- JavaFX 拖拽排序 DnD 细节
- merge 冲突
- **崩溃分析（crash-report 格式）与依赖图（三种 mod 元数据规范）**

**限制**：JavaFX 是小众栈，HMCL 特有的 JFoenix/MonetFX/Decorator 更是。会遇到幻觉 API、Swing/JavaFX 混用、JFoenix 旧写法。

**缓解**：先把相关类源码喂给它 —— `MainPage.java`、`RootPage.java`、`TwoLineListItem`、`SVG`、`FXUtils`、`Navigator`。

**合规**：生成后必须 review，别混进 MIT/Apache 片段，别引入 HMCL 没有的第三方依赖。

**工期重估**（首页组件化原估 6–8 周）：

| 用法 | 实际 |
|---|---|
| 分层使用（自己定架构 + AI 写实现） | **5–6 周** |
| 上下文喂得足 | 4–5 周 |
| 盲目信任、不看源码 | **可能更久** |

---

# 第九部分 · 排期

```
阶段一（约 6 周）
├─ 仓库 fork + 身份改造（包名/自更新/图标/改名）   3–5 天
├─ CI 跑通                                        1 周
├─ 下载源配置（拖入导入 + 空配置提示）             3–5 天
└─ 启动保障三件套 · 体检 + 崩溃还原                4 周

阶段二（约 5 周）
├─ 性能策略：Zulu 默认 + JVM 预设分档              2–3 周
└─ 内存优化 P0（JEP 346 + 占用透明化）             1 周

阶段三（约 6 周）
└─ 首页组件化（先做零侵入版）                      6 周

阶段四（约 4 周）
├─ 实例快照回滚                                    2 周
└─ 内存优化 P1 服务化 + P2 发布准备                2–3 周

阶段五
└─ MD3 主题完善（跟上游 MonetFX 进度）
```

**总计约 20–22 周（单人业余投入）**。

**关键路径是"改动外挂化"** —— 侵入点一旦扩散，后续同步成本会急剧上升。

---

# 第十部分 · 附录

## 10.1 关键类速查

| 类 | 路径 | 用途 |
|---|---|---|
| `Controllers` | `ui/Controllers.java` | 页面注册、导航、对话框 |
| `DecoratorController` / `Decorator` / `DecoratorSkin` | `ui/decorator/` | 自定义窗口装饰 + 栈式导航 |
| `RootPage` | `ui/main/RootPage.java` | 根页面，200px 侧栏 |
| `MainPage` | `ui/main/MainPage.java` | 首页（20 KB） |
| `Navigator` | `ui/construct/Navigator.java` | 栈式页面管理 |
| `TransitionPane` / `ContainerAnimations` | `ui/animation/` | 切换动画 |
| `TwoLineListItem` | `ui/construct/` | 标准列表项 |
| `AdvancedListBox` | 侧栏用 | 分组展示 |
| `RipplerContainer` | `ui/construct/` | MD 波纹 |
| `SpinnerPane` | `ui/construct/` | 加载态 |
| `SVG` | `ui/SVG.java` | 图标，已接入 Monet |
| `FXUtils` | `ui/FXUtils.java` | `runInFX` / `onChange` |
| `MemoryStatusBar` | `ui/` | 内存状态条 |
| `WindowsNativeUtils` | `ui/` | Windows 原生封装（新能力可挂旁边） |
| `CrashReporter` | `ui/../util/` | 未捕获异常处理（仅本地日志 + 弹窗） |
| `CrashWindow` / `GameCrashWindow` | `ui/` | 崩溃窗口 |
| `LauncherHelper` | `game/` | 启动流程，Stage 划分清晰 |
| `VersionSetting` | `setting/` | 版本设置 |
| `HMCLGameRepository` | `game/` | 内存自动分配逻辑 |
| `GameJavaVersion` | HMCLCore | MC → Java 版本映射 |
| `JavaVersionConstraint` | HMCLCore | Java 约束（强制/建议） |
| `DefaultLauncher` | HMCLCore `launch/` | 默认 JVM 参数生成 |
| `CrashReportAnalyzer` | HMCLCore `game/` | 崩溃报告分析（三件套②可用） |
| `DownloadProviders` | HMCLCore | 官方源 / BMCLAPI / 均衡（**2025-11 刚重构，勿改**） |
| `UpdateChecker` / `UpdateHandler` / `IntegrityChecker` | `ui/../upgrade/` | **自更新链路，需禁用** |
| `Metadata` | `HMCL/.../Metadata.java` | 名称与版本号 |

## 10.2 调试参数

环境变量 / JVM 参数（来自 `docs/Contributing.md`）：

| 参数 | 作用 |
|---|---|
| `-Dhmcl.home=` | 覆盖 HMCL 数据文件夹路径 |
| `-Dhmcl.dir=` | 仅覆盖工作路径下的 `.hmcl`（不推荐） |
| `-Dhmcl.version.override=` | 覆盖版本号 |
| `-Dhmcl.update_source.override=` | 覆盖 HMCL 更新源 |
| `-Dhmcl.bmclapi.override=` | **覆盖 BMCLAPI 根地址**（下载源配置的实现基础） |
| `-Dhmcl.self_integrity_check.disable=true` | 检查更新时不检查本体完整性 |
| `-Dhmcl.font.override=` | 覆盖字族 |
| `-Dhmcl.native.encoding=` | 覆盖原生编码 |
| `-Dhmcl.microsoft.auth.id=` / `.secret=` | 覆盖微软 OAuth |
| `-Dhmcl.curseforge.apikey=` | 覆盖 CurseForge API Key |
| `-Dhmcl.openjfx.repo=` | 添加自定义 OpenJFX 仓库 |
| `-Dhmcl.authlibinjector.location=` | 指定 authlib-injector 路径 |
| `HMCL_JAVA_HOME` / `HMCL_JAVA_OPTS` | 指定启动 HMCL 的 Java / 附加参数（仅 exe/sh） |
| `HMCL_FORCE_GPU` | 是否强制 GPU 加速渲染 |
| `HMCL_ANIMATION_FRAME_RATE` | 动画帧率（默认 60） |
| `HMCL_LANGUAGE` | 默认界面语言 |
| `HMCL_NATIVE_DECORATION` / `-Dhmcl.nativeDecoration=` | 是否使用系统原生窗口装饰 |

⚠️ 以上属内部调试特性，官方不保证稳定性，可能随时修改或移除。

## 10.3 包名改造验证命令

```bash
# 1. class 包名是否已改
unzip -l HMCL.jar | grep -c "com/yourorg/launcher"
unzip -l HMCL.jar | grep -c "org/jackhuang/hmcl"    # 应为 0 或仅剩资源

# 2. services 文件是否被改写
unzip -p HMCL.jar META-INF/services/java.net.spi.URLStreamHandlerProvider

# 3. 字符串常量是否被改写
javap -c -p -cp HMCL.jar com.yourorg.launcher.countly.CrashReport | grep "org.jackhuang"

# 4. 崩溃路径抓包：防火墙阻断或 Wireshark 观察，触发一次崩溃确认无外发
```

## 10.4 数据来源与时效

本文档中的量化数据来自以下时点的实测/检索：

- Prism Launcher 代码规模：GitHub API 递归 tree 统计，2026-10-20
- 美西螈仓库数据：GitHub API 递归 tree 统计，2026-10-20
- HMCL UI 文件规模：GitHub API 递归 tree 统计
- JDK 发行版性能对比：8 款 JDK 同环境实测
- HMCL 上游 DownloadProviders 重构：PR #4856，2025-11-30 merged

上游处于活跃开发中，**实施前建议复核关键假设**（尤其是类名、依赖版本、许可证条款）。
