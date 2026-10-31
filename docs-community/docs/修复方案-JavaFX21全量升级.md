# JavaFX 21 全量升级：总览与实施计划

> 状态：**规划（2026-10-28，低优先级）**；用户 m28123 要求在磨砂材质问题处理完后启动，**当前不改代码**。
> 关联：[磨砂材质统一与多层模糊](修复方案-磨砂材质统一与多层模糊.md)（前置任务）、[M3 视觉重构](修复方案-Material3Expressive视觉重构.md)、[待办总览](待办总览.md)。
> 当前基线：v3.17.24（分支 `feature/28-frosted-v2`，实时对齐修复待提交）；Windows 测试包 `m3expressive25`；WSL2 无 Windows GUI。

---

## 1. 结论速览

1. **当前 Windows x64 包已经是 JavaFX 21.0.8，不是 JavaFX 17**。`dist/LMCL-1.0.0-win-x64-m3expressive22/dependencies/windows-x86_64/openjfx/` 里是 `javafx-base/graphics/controls-21.0.8-win.jar`；应用运行时是 Temurin JRE 17.0.20.1。
2. **“升级到 JavaFX 21”真正剩下的不是从 17 跳到 21，而是“全量标准化”**：
   - 把 JavaFX 21 补丁从 **21.0.8** 升到 **21 LTS 最新补丁**（2026-10-28 核查 Maven Central 为 **21.0.13**，实施时以当日最新 21.x 为准）；
   - 删除 **JavaFX 25 modern** 分支（当前 JDK 23+ 构建会编译到 25）；
   - 删除 **JavaFX 19/18/17 legacy** 分支，明确官方支持平台；
   - 让构建、运行时下载清单、测试矩阵、许可与文档都只指向同一套 21.x。
3. **不强制升级 JDK**。JavaFX 21 最低支持 JDK 17，当前随包 JRE 17 可以继续运行；若要把启动器运行时也升到 JDK 21，作为可选后续，不默认并入本任务。
4. **官方 JavaFX 21.0.13 只覆盖 5 个平台/架构**：`win`（Windows x64）、`mac`（macOS x64）、`mac-aarch64`（macOS arm64）、`linux`（Linux x64）、`linux-aarch64`（Linux arm64）。Windows x86、Windows arm64、Linux arm32、LoongArch、RISC-V、FreeBSD 没有官方 21.0.13 产物；需要产品决定“降为不支持”或单独做 21 兼容构建。
5. **本任务优先级低于材质重做**：先交付 `m3expressive25` 并完成材质验收；之后由助手主动提醒用户启动本任务，不自动开工。

---

## 2. 现状取证

### 2.1 构建期版本选择

`buildSrc/src/main/java/org/jackhuang/hmcl/gradle/javafx/JavaFXPlatform.java:27-29`：

```java
public static final String LEGACY_JAVAFX_VERSION = "19.0.2.1";
public static final String CLASSIC_JAVAFX_VERSION = "21.0.8";
public static final String MODERN_JAVAFX_VERSION = "25";
```

`buildSrc/src/main/java/org/jackhuang/hmcl/gradle/javafx/JavaFXUtils.java:79-80`：

```java
if (featureVersion >= 23)
    version = platform.getVersions().getOrDefault(JavaFXVersionType.MODERN,
            platform.getVersions().get(JavaFXVersionType.CLASSIC));
else
    version = platform.getVersions().get(JavaFXVersionType.CLASSIC);
```

- 当前构建 JDK 是 17，因此 `:HMCL:compileJava` 使用 `classic=21.0.8`。
- 如果开发者用 JDK 23+ 构建，会改用 `modern=25`，也就是说同一份源码在不同构建 JDK 下拿到的 JavaFX API 不完全一致。
- `JavaFXUtils.addDependencies()` 给所有子项目加 `compileOnly` / `testImplementation` 的 `javafx-base`、`javafx-graphics`、`javafx-controls`，不含 `web`/`media`。

### 2.2 运行时清单与包

`HMCL/src/main/resources/assets/openjfx-dependencies.json` 当前：

| 平台 | classic | modern |
|---|---|---|
| windows-x86_64 | 21.0.8 `win` | 25 `win` |
| windows-x86 | 19.0.2.1 `win-x86` | 无 |
| macOS x64/arm64 | 21.0.8 `mac` / `mac-aarch64` | 25 对应 classifier |
| linux-x86_64 | 21.0.8 `linux` | 25 `linux` |
| linux-arm64 | 21.0.1 `linux-aarch64` | 25 `linux-aarch64` |
| linux-arm32 | 19.0.2.1 `linux-arm32-monocle` | 无 |
| linux-loongarch64 | 17.0.8 / 19-ea 自定义 | 无 |
| linux-riscv64 | 19.0.2.1 自定义 | 无 |
| windows-arm64 | 18.0.2+1-arm64 自定义 | 无 |
| freebsd-x86_64 | 14.0.2.1 自定义 | 无 |

这个清单同时用于“缺失 JavaFX 时运行时下载”和打包时的 `dependencies/` 目录；只改 Gradle 常量而不改清单，会出现“编译 21、运行时下载 25/19”的错位。

### 2.3 JDK 与运行时

- `HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java:49-51`：`MINIMUM_REQUIRED_JAVA_VERSION=17`、`MINIMUM_SUPPORTED_JAVA_VERSION=17`、`RECOMMENDED_JAVA_VERSION=21`。
- `HMCLBoot/src/main/java/org/jackhuang/hmcl/Main.java`：启动壳最低 Java 17。
- 测试包 `dist/LMCL-1.0.0-win-x64-m3expressive22/jre-x64/release`：`Temurin-17.0.20.1+1`。
- `HMCL/build.gradle.kts:122-123`：`sourceCompatibility = "17"`、`targetCompatibility = "17"`。

结论：JavaFX 21 与 JDK 17 的组合是当前已在使用、已验证可启动的组合；升级 JavaFX 21 补丁不需要同步升级 JDK。

### 2.4 代码侧兼容性

- HMCL 已用 `--add-exports` 打开 JavaFX 内部包（`javafx.base/com.sun.javafx.binding` 等，见 `HMCL/build.gradle.kts:135-150`），升级 21 小版本不应新增包，但必须在 21.0.13 下重跑编译。
- 本地 vendored m3fx（`m3fx/`，上游 commit `96ccd6d`）没有单独声明 JavaFX 版本，沿用根项目注入的 21.0.8；需在 21.0.13 下重编译/测试。
- JFoenix 为本地 `libs:JFoenix`，其内部 API 使用较多；21 小版本通常二进制兼容，但仍要跑控件/皮肤回归。

### 2.5 Maven Central 核查（2026-10-28）

- `org.openjfx:javafx-base` 最新 21.x：**21.0.13**。
- 21.0.13 有 `win`、`mac`、`mac-aarch64`、`linux`、`linux-aarch64` 五个官方 classifier；`win-x86`、`win-aarch64`、`linux-arm32`、`linux-loongarch64`、`linux-riscv64`、`freebsd` 均返回 404。
- 因此“全量 21”不能只改一个版本号，必须同时决定遗留平台怎么办。

---

## 3. “全量升级”的目标定义

本任务完成时，应满足：

1. `JavaFXPlatform` 只有一个 JavaFX 版本常量，值为实施日 Maven Central 上最新的 **21.x LTS 补丁**；本文基线核查为 `21.0.13`。
2. 删除 `MODERN_JAVAFX_VERSION` 与 `JavaFXVersionType.MODERN` 的选择逻辑；JDK 17、21、23+ 构建都编译到同一个 JavaFX 21。
3. `openjfx-dependencies.json` 只保留 JavaFX 21 的官方平台条目；不再出现 25/19/18/17 的 JavaFX 条目。
4. 官方支持平台明确为：Windows x64、macOS x64、macOS arm64、Linux x64、Linux arm64。
5. 构建、测试、运行时下载、随包 `dependencies/`、许可文件、文档都指向同一版本。
6. `m3fx`、JFoenix、社区控件在 JavaFX 21.0.13 + JRE 17 下编译/测试/启动通过。
7. **不**在本任务中把 JRE 17 换成 JRE 21；JDK 21 迁移另立任务。

### 3.1 遗留平台的处理选项

| 选项 | 内容 | 影响 | 建议 |
|---|---|---|---|
| A. 降为不支持 | 从官方支持矩阵和 `openjfx-dependencies.json` 删除 Windows x86/arm64、Linux arm32/LoongArch/RISC-V/FreeBSD 条目 | 这些平台无法再下载 JavaFX / 不再承诺启动；代码可保留“平台不支持”提示 | **推荐**，符合“全量 21”且不引入私有构建 |
| B. 冻结 legacy | 保留 19/18/17 条目，仅主流平台升 21 | 版本分裂仍然存在，不能称“全量升级” | 不推荐 |
| C. 自建 21 兼容包 | 为 Windows arm64/LoongArch 等自行构建 JavaFX 21、签名与分发 | 成本高、许可/维护/测试复杂 | 本任务不做，另立提案 |

如果产品必须继续支持 Windows arm64 或 LoongArch，选 C，但那是“扩展支持”项目，不应阻塞主流平台升 21。该选择需要在开工前由用户确认（见 §8）。

---

## 4. 改造点

### 4.1 构建配置

文件：`buildSrc/src/main/java/org/jackhuang/hmcl/gradle/javafx/JavaFXPlatform.java`、`JavaFXUtils.java`、`JavaFXVersionType.java`。

- 删除 `LEGACY_JAVAFX_VERSION`、`MODERN_JAVAFX_VERSION`，只保留 `JAVAFX_VERSION = "21.0.x"`。
- `JavaFXPlatform.ALL` 只放入 5 个官方平台；删除/迁移遗留平台条目。
- `JavaFXUtils.addDependencies()` 去掉 `Runtime.version().feature() >= 23` 分支，统一取 21。
- `generateOpenJFXDependencies` 仍从 Maven Central 取 SHA-1，但只遍历支持平台。
- 删除 `JavaFXVersionType`（若已无其他引用）或保留空壳会造成两套版本概念，建议删除。

### 4.2 运行时清单

文件：`HMCL/src/main/resources/assets/openjfx-dependencies.json`。

- 由 `generateOpenJFXDependencies` 重新生成，不手改。
- 确认只有 `classic`（或统一改名为 `default`）条目，版本 21.0.x。
- 同步检查 `MissingJavaFX` / `repositories.chooser` / `download.javafx.*` 的文案是否还提旧版本；如无版本字样则无需改。

### 4.3 包与启动

- 重新生成 `dependencies/windows-x86_64/openjfx/`，确认 jar 名为 `*-21.0.13-win.jar`。
- `jre-x64` 暂不换；验证 JRE 17.0.20.1 + JavaFX 21.0.13 启动、渲染、输入、DPI。
- HMCLauncher stub 不依赖 JavaFX，无需改；但需在 Windows 实机验证“无 JavaFX 时下载 → 启动”路径。
- 包内 `THIRD-PARTY-NOTICES.txt`、`LICENSE-OpenJFX.txt` 更新版本号；许可正文不变（GPLv2+CE）。

### 4.4 测试与文档

- `:HMCL:compileJava`、`:HMCL:checkTranslations`、community/全量测试。
- 增加一个轻量运行时探针：从包内 JavaFX 读取 `javafx.runtime.version`，断言 21.0.x；不要只信 Gradle 依赖。
- Windows 手工：主页/下载/设置/管理/其他页、对话框、M3 材质、输入法、HiDPI、窗口透明/标题栏。
- 更新 `README.md` 文档地图与变更记录、`docs/00-整体要求.md` 技术基线、本文件状态、`docs/待办总览.md`。

---

## 5. 实施阶段

> 前置门禁：磨砂材质 v2 + 实时对齐修复（`m3expressive25`）已交付并完成基本验收。未满足前不开始。

| 阶段 | 内容 | 预计 |
|---|---|---|
| J0 | 盘点：确认当前 21.0.8 运行时、平台矩阵、遗留平台决策；锁 21.0.13 | 0.1–0.2 人日 |
| J1 | 构建配置统一：删 modern/legacy、更新平台表、重新生成 JSON | 0.2–0.4 人日 |
| J2 | 编译/测试：JDK 17 与 JDK 21 各跑一次；m3fx/JFoenix 回归 | 0.2–0.4 人日 |
| J3 | 打包与运行时探针：更新 `dependencies/`、licenses、Windows 包 | 0.2–0.3 人日 |
| J4 | Windows 手工验收 + 文档/提交 | 0.2–0.5 人日 |
| 合计 | 不含 Windows 等待与私有平台构建 | **约 0.9–1.8 人日** |

若产品选择保留 Windows arm64/LoongArch 的自建 21 包，另加 3–5 人日与发布链工作，不在本估算内。

---

## 6. 验收清单

### 6.1 自动

- [ ] `JavaFXPlatform` 中不存在 `25`、`19.0.2.1`、`18.0.2`、`17.0.8` 的 JavaFX 版本常量。
- [ ] `openjfx-dependencies.json` 中所有 JavaFX 版本以 `21.` 开头；只有 5 个官方平台。
- [ ] `:HMCL:compileJava` 在 JDK 17 与 JDK 21 下均通过。
- [ ] community 测试与全量测试通过；m3fx 测试通过。
- [ ] 运行时探针输出 `javafx.runtime.version=21.0.x`。
- [ ] 包内 `dependencies/windows-x86_64/openjfx/` 只有 21.0.x jar，无 25/19/18。
- [ ] `:HMCL:checkTranslations` 通过；无新增上游 Java 侵入点。

### 6.2 Windows 手工

- [ ] 用包内 JRE 17 启动，主页 9 卡/账户卡/侧栏材质正常。
- [ ] JavaFX 缺失模拟：清空 `dependencies/` 后启动，能按清单下载 21.0.13 并进入。
- [ ] HiDPI 100/125/150%：字体、圆角、滚动条、标题栏不回归。
- [ ] 对话框、颜色选择器、字体预览、日历、下载进度、M3 波浪进度正常。
- [ ] M3 Expressive / M3 关闭玻璃 / Classic 三主题切换正常。
- [ ] 无 `UnsatisfiedLinkError`、`NoSuchMethodError`、CSS 解析错误。

---

## 7. 风险与回退

| 风险 | 等级 | 缓解 |
|---|---|---|
| 21.0.13 小版本引入渲染/控件回归 | 中 | 保留 21.0.8 回退；先在 JDK17 + 内置壁纸做基线截图 |
| JFoenix 内部 API 与 21.0.13 不兼容 | 中 | 先跑控件/皮肤冒烟；必要时只升到 21.0.8 的最后可用补丁 |
| 删除 modern 25 后 JDK23+ 构建 API 变化 | 低 | 源码 `sourceCompatibility=17`，只允许 21 API；CI 增加 JDK21 构建 |
| 遗留平台用户无法启动 | 中 | 产品明确不支持矩阵；启动器给可读提示；不静默崩溃 |
| 运行时清单未同步，下载到旧版 | 高 | JSON 由任务生成；包内/下载清单做版本断言 |
| 包体积/内存变化 | 低 | 21.0.13 与 21.0.8 体积相近；记录前后 jar/包大小 |

回退：把 `JAVAFX_VERSION` 固定回 `21.0.8`，恢复 `openjfx-dependencies.json` 与包内 `dependencies/`，重出测试包；保留 `m3expressive25` 作为材质基线。

---

## 8. 开工前需确认

1. 遗留平台（Windows x86/arm64、Linux arm32/LoongArch/RISC-V/FreeBSD）是否接受“降为不支持”？推荐 A。
2. 是否只升 JavaFX、JRE 继续 17；还是同时规划 JDK 21 运行时迁移（另立任务）？
3. JavaFX 目标补丁是否按实施日最新 21.x（当前核查 21.0.13），而不是固定 21.0.8？
4. 本任务版本号/包名：建议 `v3.17.25` / `m3expressive26`（材质任务占 v3.17.22 / m3e23，v2 占 v3.17.23 / m3e24，实时对齐修复占 v3.17.24 / m3e25）。

---

## 9. 提醒机制

- 本任务在 `docs/待办总览.md` 的 `VIS-03` 中登记为“材质完成后启动”。
- 材质 `m3expressive25` 交付后的同一轮汇报中，助手必须主动提醒：“JavaFX 21 标准化任务已到启动点，是否开始？”
- 在该提醒发出前，不创建代码分支、不改 `JavaFXPlatform`、不构建新包。
