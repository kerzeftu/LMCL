# 步骤 04 · 包名策略与 Relocate 门禁（首发 No-Go）

> **阶段**：阶段一 · 工程底座 ｜ **优先级**：P1 ｜ **工期**：3–5 天（AI 辅助 1–2 天）
> **前置**：[步骤 03](03-产品身份与合规改造.md) ｜ **后续**：[步骤 05](05-禁用自动更新与更新提示.md)、[步骤 20](20-HMCLauncher启动壳重建.md)
> **侵入点**：构建/资源（不占代码 7 处名额）；**默认结论 No-Go** —— 若要 relocate 必须新增第 8 处代码侵入 `CrashReport.java`（§3.2、00 §13 #9）

---

## 1. 目标

**本步骤的默认交付不是"执行 relocate"，而是"把 relocate 验证成可决策的门禁"，并按 No-Go 落地。**

交付物（按优先级）：

1. **No-Go 分支（首发默认）**：① 无 relocate 的构建链跑通（基线 jar/exe 可启动）；② 记录 `CrashReport.shouldBeReport()` 的实测证据（relocate 后崩溃窗口会静默失效）；③ 在 00 §13 #9 与本节 §7 回填"No-Go + 复核日期"；④ relocate 配置以注释/独立脚本形式留档，**不接入默认构建**；⑤ 在 CI 加一条断言：默认产物的 `Main-Class` 不是 relocated 包名（防止误开）。
2. **Go 分支（仅在产品书面批准第 8 处侵入后执行）**：把 `org.jackhuang.hmcl` 前缀改成本项目前缀（如 `com.yourorg.launcher`），用 Shadow `relocate` 在打包期完成、**源码一行不动**；同时改 `CrashReport.java` 的前缀判断（第 8 处侵入）并登记 00 §4.2。

若 Go，收益是：主类名/崩溃日志/依赖隔离维度上的独立产品、给第三方 SDK 留干净命名空间、与官方版同机共存更彻底（数据目录另见[步骤 06](06-数据目录与用户迁移.md)）。

> 一句话判断：收益是长期的品牌与隔离干净度；代价是**必须多付 1 处侵入**。因此默认 No-Go，本步骤设为 P1 且带 Go/No-Go 门（§6）。

---

## 2. 背景与决策依据（v3.16.4 实测）

### 2.1 上游构建链的事实

| 事实 | 出处 | 含义 |
|---|---|---|
| 使用 **`com.gradleup.shadow` 9.3.1**（不是旧的 `com.github.johnrengelman.shadow`） | `HMCL/build.gradle.kts` 插件别名 + `gradle/libs.versions.toml` | 不要照抄网上 8.x 的写法，配置语法以 9.x 为准 |
| `tasks.jar { enabled = false; dependsOn(shadowJar) }` | `HMCL/build.gradle.kts` | **正式产物就是 `shadowJar` 的 jar**；没有"另一套 jar"可分叉 |
| `shadowJar` 已设置 `manifest.Main-Class = "org.jackhuang.hmcl.Main"`、`Add-Opens`、`Enable-Native-Access` 等 | 同上 | relocate 后 manifest 的 `Main-Class` 必须一起改，否则双击/`java -jar` 找不到主类 |
| `makeExecutables` 从 shadow jar 里取 `assets/HMCLauncher.exe|.sh`，与 jar 字节拼成 exe/sh | 同上 | relocate 只改 jar；stub 的图标/名称/版本资源属于[步骤 20](20-HMCLauncher启动壳重建.md) |
| 0 个 `.fxml`（UI 全代码构建） | `HMCL/src/main/java`、`resources` | 不存在 `fx:controller` 写死包名的经典坑 |
| 语言资源是 `assets/lang/I18N*.properties`，ResourceBundle 基名 `assets.lang.I18N` | `I18n.java` + resources 目录 | 资源路径与包名无关，relocate 不影响 i18n 加载 |
| 签名/校验：`attachSignature` 只在上游发版环境有私钥时执行；`IntegrityChecker` 可在步骤 05 整体禁用 | `HMCL/build.gradle.kts`、`IntegrityChecker.java:48` | **本步骤不需要改公钥、不需要重新签名**（旧文档这条是错的） |

### 2.2 技术路线

```kotlin
// HMCL/build.gradle.kts 的 shadowJar 配置中追加
val oldPackage = "org.jackhuang.hmcl"
val newPackage = "com.yourorg.launcher"

tasks.shadowJar {
    relocate(oldPackage, newPackage)
    // manifest 的 Main-Class 不会被可靠地自动改写，显式覆盖（值用计算出来的，别手写两处）
    manifest {
        attributes["Main-Class"] = "$newPackage.Main"
    }
    mergeServiceFiles()
}
```

三个要点：

1. **`relocate` 只作用于 `org.jackhuang.hmcl` 及其子包**，不要 relocate 第三方依赖；
2. **不要给 `shadowJar` 加 `archiveClassifier`**：上游 `jarPath`/`makeExecutables` 依赖现有产物名，改了会让打包链找不到 jar；
3. 配置**收敛在独立块**（可直接写在 `HMCL/build.gradle.kts`，也可抽成 `gradle/community-relocate.gradle.kts` 由一行 `apply(from=…)` 引入），减少下次同步上游时的冲突面。本步骤只做 relocate；**Main-Class 最终会由步骤 05 的 `CommunityBootstrap` 接管**，届时这里的 manifest 设置要跟着调整（见 §3.6）。

---

## 3. 详细实施

### 3.1 第 0 步：先跑通"不改包名"的构建链

在没有 JDK 的机器上无法本地执行，以下命令在 **CI 或装有 JDK 17+ 的开发机**上跑：

```bash
./gradlew :HMCL:shadowJar
./gradlew :HMCL:makeExecutables

java -jar HMCL/build/libs/HMCL-*.jar   # 能起窗口 = 基线 OK
```

这一步只验证构建链，不引入 relocate。若失败，先单独解决"上游构建脚本 vs 本机 JDK/Gradle 版本"的问题（Gradle wrapper 为 9.6.1），不要和 relocate 混在一起排查。

### 3.2 第 1 步：加 relocate 并做结构验证

```bash
OLD="org/jackhuang/hmcl"; NEW="com/yourorg/launcher"
JAR=$(ls HMCL/build/libs/HMCL-*.jar | grep -v sources | head -1)

# ① class 是否整体迁移
unzip -l "$JAR" | grep -c "$NEW"
unzip -l "$JAR" | grep -c "$OLD"        # 期望 0（刻意保留项除外，需登记）
# ② 主类是否可解析
unzip -p "$JAR" META-INF/MANIFEST.MF | grep "Main-Class"
# ③ class 内残留的旧包名字符串（崩溃分类等逻辑依赖）
unzip -p "$JAR" '*.class' | strings | grep -c "org\.jackhuang" || true
```

**已实测到的"逻辑依赖"（v3.16.4，必须先看这一条）**：

`HMCL/src/main/java/org/jackhuang/hmcl/countly/CrashReport.java:44-46` 的 `shouldBeReport()`：

```java
public boolean shouldBeReport() {
    if (!stackTrace.contains("org.jackhuang"))   // ← 字面量，Shadow 会改写它，但语义随之失效
        return false;
    ...
}
```

含义：**一旦 relocate，堆栈里不再有 `org.jackhuang`，崩溃窗口将永远不弹出**（`CrashReporter.java:89` 依据该返回值决定是否显示 `CrashWindow`）。这不是日志字符串，是**功能开关**。

结论（写进 §6 门禁）：

- 要做 relocate，就必须改 `CrashReport.shouldBeReport()`（或改为按产品前缀判断）——这是**第 13 处代码侵入**，突破当前“固定 12 处”预算；
- 不动它 → 崩溃窗口静默失效，违反"绝不阻断/可诊断"原则；
- 因此**默认结论是 No-Go（首发不做 relocate）**，除非产品明确同意把侵入预算改为 5 处。

其余字符串用 `javap` 定位，而不是猜：

```bash
javap -c -p -cp "$JAR" com.yourorg.launcher.countly.CrashReport | grep -n "org\.jackhuang"
```

判定标准：

- 只是日志/展示字符串 → 无害，登记后放过；
- 参与 `contains(...)`、`equals(...)`、`Class.forName(...)`、`getResource(...)` 等逻辑 → **必须改源码**，此时侵入点 +1，先登记再改。

### 3.3 第 2 步：`META-INF/services` 与反射点

`ServiceLoader` 依赖 `META-INF/services/<接口全名>` 文件名；**Shadow 的 relocate 不会改写文件名**，只会（配合 `mergeServiceFiles()`）合并与改写文件**内容**。

```bash
# 列出所有 service 注册
unzip -l "$JAR" | grep "META-INF/services/"
# 逐个检查：文件名中的接口是否属于旧包
unzip -l "$JAR" | grep "META-INF/services/org.jackhuang" || echo "无包名型 service 文件名"
```

处理规则：

- 文件名含旧包名 → 在构建后处理（或 `shadowJar.doLast`）重命名为新包名，并验证 `ServiceLoader.load` 能拿到实现；
- 只有内容含旧类名 → `mergeServiceFiles()` 可处理；
- 同时 grep 反射入口：

```bash
grep -rn "Class\.forName(\s*\"org\.jackhuang\|getResource(\s*\"org/jackhuang\|getResourceAsStream(\s*\"/org/jackhuang" \
  --include=*.java HMCL HMCLCore HMCLBoot
```

> 说明：HMCL 的类加载以显式 import/new 为主，但**不能假设为零**；本步骤以命令输出为准，文档只记录结论。

### 3.4 第 3 步：配置文件与数据兼容

两类风险，分开查：

```bash
# ① 源码/资源中序列化了 FQCN
grep -rn "org\.jackhuang" --include=*.json --include=*.properties --include=*.yml --include=*.txt \
  HMCL/src HMCLCore/src HMCLBoot/src | grep -v build

# ② 真实数据目录（先运行一次再查更容易暴露）
grep -rn "org\.jackhuang" ~/.hmcl ~/.local/share/hmcl 2>/dev/null | head
```

- 有命中 → 需要"旧值兼容读入 + 新值写出"的迁移（一般落在[步骤 06](06-数据目录与用户迁移.md) 的 `LegacyConfigMigrator`/schema 迁移里）；
- 无命中 → 在本文档 §7 回填"已确认无 FQCN 序列化"，避免后人重复排查。

> 注意：v3.16.4 的设置文件是 `launcher-settings.json` / `user-settings.json` / `launcher-state.json` 等（见步骤 06），**不是旧的单体 `config.json`**。旧文档里针对 `config.json` 的描述已作废。

### 3.5 第 4 步：产物与运行验证（必做，不可用"应该没问题"代替）

```bash
# ① exe/sh 是否由新 jar 生成（时间戳/大小变化）
ls -l HMCL/build/libs/HMCL-*.exe HMCL/build/libs/HMCL-*.sh 2>/dev/null
# ② 直接跑 jar（Java 17+）
java -jar "$JAR"
# ③ 起窗口后：登录页/首页/设置页/下载页各点一次；触发一次崩溃看窗口标题
```

Windows 上还**必须**做一次"裸机双击 exe"：把 `HMCL-<version>.exe` 单独拷到无 Java 环境（或仅系统 JRE）的 Windows 10/11 虚机双击，确认 stub 能找到主类并启动（stub 行为见步骤 20；这也是 relocate 与 stub 的联调点）。

### 3.6 与步骤 05（Bootstrap）的衔接

本步骤先把 `Main-Class` 指向 `$newPackage.Main`，保证 relocate 独立可验证；步骤 05 会做两件事：

1. 在 jar 中打入 `CommunityBootstrap`（我们的包，例如 `com.yourorg.launcher.community.CommunityBootstrap`，**不参与 relocate**）；
2. 把 `shadowJar.manifest.Main-Class` 改为这个 Bootstrap 类，由它在 `main()` 里：
   - 设置 `hmcl.update_source.override`、`hmcl.self_integrity_check.disable`、`hmcl.home`/`hmcl.dir`（下载源由步骤 07 运行时替换 provider，不依赖 `hmcl.bmclapi.override`） 等系统属性；
   - 反射/直接调用 `com.yourorg.launcher.EntryPoint.main(args)`（注意是 **relocate 后的**类名）。

衔接验收：`unzip -p ... META-INF/MANIFEST.MF` 的 `Main-Class` 与本步骤当时的预期一致；两个步骤合并后 `java -jar` 仍能启动。

### 3.7 与 stub（步骤 20）的边界

| 归谁 | 内容 |
|---|---|
| 本步骤 | jar 内类路径、manifest `Main-Class`、service 文件、i18n 资源（路径不变） |
| 步骤 20 | stub 的图标/版本资源/产品名、stub 如何定位 jar 与主类、Windows 双击体验 |

**不要**用 `rcedit`/`wrestool` 去改预编译 stub 的图标：v3.16.4 的 stub 来自 `org.glavo.hmcl:HMCLauncher:3.7.0.1`（Maven 依赖，jar 内只有 `assets/HMCLauncher.exe` 二进制，没有源码），直接改二进制既不可维护，也带来 GPL 对应源码义务问题。正解是[步骤 20](20-HMCLauncher启动壳重建.md) 用 **`HMCL_LAUNCHER_EXE` 注入自建 stub**（上游构建脚本原生支持该环境变量）。

### 3.8 完整改造清单

| # | 项 | 做什么 | 难度 |
|---|---|---|---|
| 1 | 包名 relocate | `shadowJar.relocate` + 显式 `Main-Class` | 低 |
| 2 | 构建链接入 | 确认 `makeExecutables` 用的还是同一个 shadow jar | 中 |
| 3 | manifest | `Main-Class`/`Add-Opens` 等属性完整保留 | 中 |
| 4 | 字符串常量 | 扫描 + 逐处判定（逻辑依赖才改） | 中 |
| 5 | `META-INF/services` | `mergeServiceFiles()` + 文件名检查 | 中 |
| 6 | 反射/资源读取 | `Class.forName`/`getResource` 扫描 | 中 |
| 7 | 配置 FQCN | 源码 + 数据目录双向 grep | **高** |
| 8 | 崩溃报告 | 标题/分类逻辑回归（分类逻辑与包名相关） | 中 |
| 9 | stub | 只做联调验证；重建在步骤 20 | 中 |
| 10 | 外部 URL 抓包 | 确认无上游遥测/更新请求 | 低 |
| 11 | 更新/完整性 | 步骤 05 整体禁用，本步骤不碰签名 | 低 |
| 12 | 上游同步 | relocate 配置尽量集中、可 patch | 中 |

---

## 4. 验收 Checklist

- [ ] `./gradlew :HMCL:shadowJar :HMCL:makeExecutables` 成功，产物名与上游一致（无 classifier 变化）
- [ ] `unzip -l` 中旧包名路径计数为 0（刻意保留项须在本文档登记原因）
- [ ] `META-INF/MANIFEST.MF` 的 `Main-Class` 指向新包名（或步骤 05 的 Bootstrap，按当前阶段）
- [ ] `META-INF/services` 无旧包名文件名；内容类名已改写
- [ ] class 内旧包名字符串清零或已判定无害并登记
- [ ] `config/*.json` 与数据目录 grep 结论已回填（有/无 FQCN）
- [ ] `java -jar` 能起窗口，登录/首页/设置/下载各点一次
- [ ] 人为崩溃：崩溃窗口正常、日志正常、崩溃分类（`CrashReportAnalyzer`）不因改包名失效
- [ ] Windows 裸机双击 exe 启动成功（stub + relocate 联调）
- [ ] 抓包无指向 `hmcl.huangyuhui.net` 等上游服务端的请求
- [ ] 若决定首发不做：在[整体要求 §12](../00-整体要求.md) 回填"跳过 relocate"的决定、理由与重做窗口

---

## 5. 风险与应对

| 风险 | 等级 | 应对 |
|---|---|---|
| `META-INF/services` 文件名漏改 → 启动即 `ServiceConfigurationError` | **高** | §3.3 命令必跑；失败不允许合入 |
| `Main-Class` 未随 relocate 改写 → exe/`java -jar` 报"找不到主类" | **高** | 显式设置 manifest；§4 强制检查 |
| 字符串常量残留 → 崩溃分类/自诊断逻辑静默失效 | 中 | §3.2 扫描 + 一次真实崩溃回归 |
| 配置/数据中存 FQCN → 老用户配置读取异常 | 中 | §3.4 双向 grep；有则写迁移 |
| 与上游 `build.gradle.kts` 冲突频繁 | 中 | 配置集中一个 apply 块；同步上游时优先保留结构、只重放 relocate 段 |
| 依赖 Shadow 自动改写 manifest／字符串的假设不成立 | 中 | 一切以 `unzip`/`javap` 实测为准，本文档不写"应该会" |
| **默认路径**：首发不做 relocate（No-Go） | — | 合法（GPL 不要求改包名），且保住"4 处侵入"预算与崩溃窗口可用性。代价：品牌不干净、未来重做成本上升。**决定与理由已写入 00 §13 #9，复核日期回填 §7** |

---

## 6. Go / No-Go 门（2 天验证期）

按顺序做，任一项失败即暂缓，先记录原因再继续主线：

1. 无 relocate 的 `shadowJar` + `java -jar` 能启动 → 构建链 OK；
2. 加 relocate 后 jar 能启动（`java -jar`）→ 类路径/manifest OK；
3. `META-INF/services`、字符串残留、反射扫描均有明确结论 → 兼容性 OK；
4. Windows 裸机双击 exe 能启动 → stub 联调 OK；
5. 崩溃回归通过 → 自诊断 OK。

5 项全过 **且产品书面同意把代码侵入预算从 3 处改为 4 处（`CrashReport.java`）** = Go；任一项失败或未获批 = No-Go（回填决定，按 §5 退路处理）。

---

## 7. 实测结论回填（执行时填写）

| 检查 | 结论 | 日期/执行人 |
|---|---|---|
| 旧包名 class 残留数 | | |
| `META-INF/services` 是否含包名 | | |
| 逻辑依赖的字符串常量 | **已确认静态证据**：`CrashReport.java:44-46` 的 `shouldBeReport()` 用 `stackTrace.contains("org.jackhuang")` 作功能开关，relocate 后恒 false（`CrashReporter.java:89` 据此决定弹窗） | 2026-10-21 / 源码核对 |
| 配置/数据目录 FQCN | | |
| exe 裸机启动 | | |
| 崩溃分类回归 | | |
| Go / No-Go 决定 | **No-Go（首发）**：除非产品书面批准第 8 处侵入 `CrashReport.java`，否则不启用 relocate（构建脚本保留注释/独立脚本，CI 断言默认 Main-Class 未变） | 2026-10-21 / 方案定案 |

---

## 8. 关联文档

- 改动预算与登记纪律：[整体要求 §4.2](../00-整体要求.md)
- 构建与产物结构（stub + jar、`HMCL_LAUNCHER_EXE`）：[整体要求 §4.4](../00-整体要求.md)、[步骤 01](01-仓库建立与上游同步机制.md)
- 启动引导与属性注入（Bootstrap 接管 `Main-Class`）：[步骤 05](05-禁用自动更新与更新提示.md)
- stub 重建（图标/名称/定位主类）：[步骤 20](20-HMCLauncher启动壳重建.md)
- 数据兼容与迁移：[步骤 06](06-数据目录与用户迁移.md)
