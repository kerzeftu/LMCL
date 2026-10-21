# 基座选型决策：HMCL vs Prism Launcher vs 美西螈

目标：做一个社区版启动器，核心约束是——
① 中国大陆用户可用；② 采用 MD3 + 莫奈取色；③ 自身改动相对固定，长期只合并上游。

---

## 一、结论

**选 HMCL。**

加权总分：**HMCL 4.35 / Prism 3.05 / 美西螈 2.70**

决定性差异不在"哪个项目更好"，而在**哪个更匹配上面三条约束**——HMCL 在权重最高的三项（国内可用性、UI 改造难度、CI 与同步成本）上全面领先，且优势集中在最难自建的部分。

| 维度 | 权重 | HMCL | Prism | 美西螈 | 说明 |
|---|:--:|:--:|:--:|:--:|---|
| 国内下载可用性 | 25% | **5** | 2 | 4 | HMCL 内置 BMCLAPI 全链路；Prism 的 meta 镜像不含 assets；美西螈面向国内但走外部 API |
| UI 改造难度（MD3） | 20% | **4** | 1 | 3 | HMCL 上游已有 MonetFX；Prism 是 Qt Widgets；美西螈是 Vue 3 需自建 MD3 |
| CI / 构建复杂度 | 15% | **4** | 1 | 3 | HMCL 单 runner 出全产物；Prism 是 7 配置矩阵 + vcpkg；美西螈需 Node+Rust 双工具链 |
| 长期同步痛苦度 | 15% | **4** | 3 | **1** | HMCL 单级同步；美西螈是两级同步且上游每周多版 |
| 许可证与合规负担 | 10% | **4** | 3 | **1** | 美西螈混合许可，含 PCL 限制分发内容 + Modrinth 品牌约束 |
| 运行时性能 | 5% | 3 | **5** | 2 | Qt 原生最轻；美西螈 Tauri 有已知内存与崩溃问题 |
| 平台覆盖 | 5% | **5** | 4 | 3 | HMCL 多出 FreeBSD、RISC-V、LoongArch；美西螈 Linux 有崩溃 issue |
| 中文社区与用户基础 | 5% | **5** | 2 | **4** | 美西螈中文生态好，但项目太年轻 |

---

## 二、逐维度依据

### 1. 国内下载可用性（决定性）

**HMCL（5）**：`DownloadProviders.java` 内置三套策略 —— 官方源 / BMCLAPI 镜像 / 均衡模式，默认按 `LocaleUtils.IS_CHINA_MAINLAND` 自动选择。BMCLAPI（bangbang93 公益源，默认 `https://bmclapi2.bangbang93.com`）覆盖 MC 本体、库、资源、Forge/Fabric 等全链路。

**Prism（2）**：支持在设置 → API → 元数据服务器里填自定义 meta 地址，社区也做了 BMCLAPI 的 meta 镜像。但该项目 README 自述两条硬限制：

> Assets 并不跟随 meta，所以只能加速一部分资源
> BMCLAPI 的 Quilt 下载部分尚未完全建立，Quilt 将保持使用官方源

**元数据加速 ≠ 文件下载加速。** 真正耗时的 jar / assets / libraries 不走 meta，只能直连海外。对一个面向国内用户的社区版，这是致命短板——你得自己造一整套文件级镜像适配。

> 叠加背景：HMCL 正准备停用大陆 MC 下载（PCL 2.99 已实装同类调整）。这恰好是社区版的立身之本，而**改造 HMCL 已有的镜像层，远比在 Prism 上从零建一套容易**。

### 2. UI 改造难度（MD3 + 莫奈取色）

这一项与直觉相反，值得单独强调。

**Prism（1）**：代码库是 **C++20 + Qt 6 Widgets**（非 Qt Quick Controls）。Qt 6.5 更新的 Material 3 风格——圆角 scale、elevation 阴影、filled/outlined 容器、state layer 等完整 M3 语义——属于 **Qt Quick Controls**，Widget 应用无法使用。

> ⚠️ 更正：此前曾说"Qt 主题系统做 MD3 比 JavaFX 舒服"，**该判断有误**，特此更正。Qt Widgets 做 MD3 需手写 QSS 样式表与自绘；动态取色还要自行把 material-color-utilities 移植到 C++（官方无 C++ 参考实现，仅 Rust/C 的 WIP 绑定）。

**HMCL（4）**：`HMCL/build.gradle.kts` 的依赖已包含 **MonetFX（dynamic coloring）**，与 JFoenix（MD2）、fxsvgimage 并列。上游 UI 层正处于 MD2 → MD3 迁移中——莫奈取色是现成的，且你的方向是顺势而非逆流。

代价：HMCL 现在是 JFoenix + MonetFX 混用状态，说明迁移未完成，你要做的是把它做完、做彻底。

### 3. CI / 构建复杂度

**HMCL（4）**：JDK 17（含 JavaFX）+ `xvfb-run ./gradlew build`。关键点——`makeExecutables` 是把 fat jar 追加到 `HMCLauncher.exe` stub（444KB）后面，纯字节拼接、不依赖目标平台，**所以单台 ubuntu runner 就能同时产出 jar / exe / sh / deb**，无需为 macOS（10× 计费）和 Windows（2× 计费）付费。

**Prism（1）**：官方 CI 是 **7 配置构建矩阵**：

| OS | Preset | 架构 |
|---|---|---|
| ubuntu-24.04 | linux | x86_64 |
| ubuntu-24.04-arm | linux aarch64 | aarch64 |
| windows-2022 | windows_mingw | x86_64 (Clang64) |
| windows-11-arm | windows_mingw | arm64 (ClangARM64) |
| windows-2022 | windows_msvc | amd64 |
| windows-11-arm | windows_msvc | arm64 |
| macos-26 | macos_universal | Universal |

依赖由 vcpkg 管理（vcpkg.json + 自定义 triplets + CMakePresets.json），macOS 还需签名证书，否则用户遇到无法验证开发者。构建产物还带 debug 信息，体积偏大。

### 4. 长期同步痛苦度（对应"改动固定"诉求）

**HMCL（4）**：`HMCLBoot` / `HMCLCore` / `HMCL` 三模块分离。你要做的体检、崩溃分析、快照等功能可以**整体放进新 package（如 `org.jackhuang.hmcl.diagnose`）+ 新页面**，不碰上游任何现有文件。上游不会动它不知道的文件，冲突概率极低。

侵入式改动压到三处即可：`Metadata.java`（改名/版本号，GPLv3 第 7 条要求，一次性）、下载源地区分支、启动流程挂一个钩子。

**Prism（3）**：C++ 项目改 UI 通常需要改多处 Widget 代码，外挂性不如 Java package。但 Prism 官方明确表态欢迎 fork——"我们不会对那些想 fork、再分发、重新打包的人抱有敌意，只要遵守许可条款"——这是加分项。

### 5. 许可证与合规负担

**HMCL（4）**：GPLv3 + 第 7 条附加条款 —— ① 分发修改版必须合理修改名称或版本号以区别于原版；② 不得移除版权声明。规则清晰，改 `Metadata.java` 一次性满足。

**Prism（3）**：GPL-3.0-only（代码）+ CC BY-SA 4.0（品牌资产），无附加条款。但有额外工程负担：官方要求改代码再分发时，必须**替换或清空 CMakeLists.txt 里的 API keys**（Microsoft OAuth + CurseForge API），否则等同于接受微软身份平台与 CurseForge 第三方 API 的条款。

注意：置空后**功能会静默失效而非编译报错**，属于难发现的问题类型。另外打包需设置 `Launcher_BUILD_PLATFORM`。

### 6. 运行时性能（Prism 赢）

**Prism（5）**：Qt 原生渲染，无 JVM、无 WebView、无 Electron。这批候选里性能最好。

**HMCL（3）**：有 JVM 开销，但 JavaFX 是原生渲染 —— 不存在美西螈那种"DOM 内存收不回"或"WebKitGTK 在 NVIDIA 驱动下段错误"的问题。且启动器内存占用**不影响游戏帧率**（游戏是独立 JVM 进程）。

这一项 Prism 明显赢，但权重只有 5%——因为启动器自身性能不是用户痛点，国内能不能顺畅下载才是。

### 7. 平台覆盖

**HMCL（5）**：Windows / Linux / macOS / FreeBSD；x86 / ARM / RISC-V / MIPS / LoongArch。

**Prism（4）**：Windows / macOS（含 Apple Silicon）/ Linux（AppImage、Flatpak、原生包）。无 FreeBSD，无 RISC-V / LoongArch。

### 8. 中文社区与用户基础

**HMCL（5）**：中文社区最大，内置 Mod 中文名映射、国内镜像源、陶瓦联机等本土化能力。

**Prism（2）**：海外项目为主，中文生态弱。

---

## 三、什么情况下应该改选 Prism

如果出现以下任一情况，重新评估：

1. **目标用户主要在海外**，国内下载不再是核心诉求 → Prism 的性能与实例隔离优势放大
2. **你或团队有 Qt / C++ 背景**，且愿意投入做 MD3 自绘 → UI 改造难度这一项的差距缩小
3. **你愿意自建国内文件级镜像**并长期维护 → 抹平最大短板
4. **极度在意启动器自身资源占用**（如在低配设备上常驻）→ Prism 的 Qt 原生优势变成决定性

## 四、美西螈（Axolotl Launcher）单独评估

结论：**不推荐作为 fork 基座**（甚至比 Prism 更不适合）。

### 4.1 实测规模

数据取自 GitHub API 对 `Mystic-Stars/Axolotl` main 分支的递归 tree 统计（2026-10-20）：

| 项目 | 数值 |
|---|---|
| 文件总数 | 4,250 |
| 仓库总体积 | 121.76 MB（含大量二进制资产） |
| Rust | 496 文件 / 9.91 MB |
| Vue | 531 文件 / 6.01 MB |
| TypeScript | 988 文件 / 5.36 MB |
| 创建时间 | 2026-07-13 |
| 最近推送 | 2026-10-20 |
| Stars / Forks / Open Issues | 551 / 66 / **95** |

体积构成中约 35 MB 是二进制资产（Minecraft_AE.ttf 15.4 MB、Monaco 编辑器 13 MB、彩蛋游戏 6.3 MB），真实代码量约 21 MB。

### 4.2 三个致命问题

#### ① 许可证是混合的，且含限制分发内容（最严重）

顶层 `COPYING.md` 原文要点：

> Packages retain their respective upstream licenses. Axolotl's desktop packages are **GPL-3.0-only**...
> Modrinth trademarks, logos, cover images... **must not be reintroduced by upstream synchronization**.
> The desktop launcher's Chinese content search includes an unmodified `WikiEntries.txt` snapshot from Plain Craft Launcher... distributed under the **PCL Limited Distribution License**.
> The UI package's log viewer is adapted from LogShare-Web-UI... **MIT License**.

这意味着 fork 后的合规负担：

- **桌面包 GPL-3.0-only**，但各子包"保留各自上游许可证"——需逐包审计
- 内置 **PCL 的 WikiEntries.txt 中文名映射快照**，受 PCL 自定义许可证（非 GPL）约束，分发前必须确认该许可证是否允许
- 日志查看器是 **MIT** 改编作品
- **同步上游时不得重新引入 Modrinth 品牌资产**——即同步流程本身带合规约束，不能无脑 merge
- GitHub 因混合许可判定为 `NOASSERTION`

对比：HMCL 是干净的 GPLv3 + 明确的第 7 条附加条款，一次改名即可满足。

#### ② 两级同步 + 上游狂奔

美西螈本身是 **Modrinth App 的 fork**。你 fork 美西螈后形成两级：

```
Modrinth App  ──►  美西螈  ──►  你的社区版
```

更麻烦的是：仓库的 `.github/workflows/` 里**没有 upstream sync workflow**（只有 `sync-cnb-source.yml` 同步 CNB 源、`sync-lobehub-models.yml` 同步模型列表）。即美西螈同步 Modrinth App 的机制不在仓库内，你无法直接复用。

叠加时间线：2026-07-13 建仓，到 10-20 已发布 1.10.0-beta.3，三个月数十个版本。**你 fork 一个每周多版的项目，改动会被持续冲垮。**

对比：HMCL 是单级同步，且有明确的 release 分支可跟。

#### ③ 继承 Tauri 的架构病

前几轮已确认的问题（Issue #421 / #422）：Linux 上切换界面内存 500MB → 1.2GB 不回收；NVIDIA 驱动下 WebKitGTK DMA-BUF 崩溃；上游 Modrinth App 有 873MB 内存泄漏报告。

这些是 WebView 渲染模型的问题，**与 Rust 后端无关**——你 fork 后原样继承。

### 4.3 美西螈的真实优点（值得学习）

公平地说，它有三点值得借鉴：

- **功能设计亮眼**：首页组件化、拖拽自动识别资源类型、模组版本回滚、种子地图、投影工坊——产品思路值得抄
- **中文生态好**：内置中文搜索、Mod 中文名映射，面向国内用户
- **社区响应快**：作者听劝，迭代速度惊人

**要学的是产品思路，不是 fork 它。**

### 4.4 什么情况下可以考虑

仅当你符合以下全部条件：

1. 目标就是做 Vue/Tauri 技术栈的启动器，且团队熟悉这套
2. 愿意做前置的许可证审计，并接受可能需要剥离 PCL 的 WikiEntries 内容
3. 有能力自建两级同步流程，或干脆**放弃跟上游**只做一次性快照

若第 3 条成立，那性质就不是"fork 后持续同步"，而是"以它为起点另起项目"——此时不如直接基于 Modrinth App 本身，或少一层中间商。

---

## 五、选 HMCL 后的执行要点

1. **不要跟 `main`**，跟 release 分支（如 `release/3.13`）——`main` 是 beta 开发分支
2. **改动分两类**：侵入式压到最少（3 处），新增式全部放新 package
3. **名称与版本号**：在 `Metadata.java` 一次性改好，满足 GPLv3 第 7 条
4. **产物**：exe 是便携版（stub + jar 拼接，免安装）；想"开箱即用"不必上 jpackage，把绿色版 JRE 放同级 `jre-x64` 目录即可 —— HMCL 原生按 `jre-arm64` → `jre-x64` → `jre-x86` 顺序查找
5. **差异化方向**：MD3 已不新鲜（上游在做），应转向"启动保障"类实用功能（启动前体检、崩溃现场还原、实例快照回滚）

---

## 附：已排除的候选

| 基座 | 排除原因 |
|---|---|
| PCL2 / PCL CE | .NET + WPF，Windows 独占。官方明确不做 Mac 版；PCL CE 的 macOS 仅"交叉编译"，产物无法运行 |
| BakaXL / FluentLauncher | 同上，Windows 独占 |
| MultiMC | 开发基本停滞，要 fork 就 fork 其继任者 Prism |
| 美西螈 / Modrinth App | Rust + Tauri：WebView 内存不回收、Linux WebKitGTK 崩溃；且美西螈为混合许可证（含 PCL 限制分发内容）、两级同步、95 open issues。详见第四节 |
| XMCL | Electron，内存偏重 |
