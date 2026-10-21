# 包名改造方案：Shadow Relocate

> 决策：改包名（体现为独立程序）／数据目录不动／崩溃上报关闭仅留本地日志与弹窗。
> 本文给出可执行的改造路径，并列出必须先解决的前置问题。

---

## 一、先说一个更正：崩溃上报已经没有网络行为了

我读了 `CrashReporter.java` 的实际代码，**结论与预期不同**。

当前 `uncaughtException` 的完整流程：

```java
LOG.error("Uncaught exception in thread " + t.getName(), e);   // ① 本地日志
CrashReport report = new CrashReport(t, e);
if (!report.shouldBeReport()) return;
LOG.error(report.getDisplayText());                             // ② 本地日志（详细）
Platform.runLater(() -> {
    if (checkThrowable(e)) {
        if (showCrashWindow) new CrashWindow(report).show();    // ③ 弹窗
    }
});
SettingsManager.shutdown();
LOG.shutdown();
```

**全程没有任何网络请求。**

`org.jackhuang.hmcl.countly.CrashReport` 这个类名里的 `countly` 是**历史遗留**（早期可能接入过 Countly SDK）。现在整个 `countly` 包只剩 `CrashReport.java` 一个类，它只是个格式化文本的数据结构，`getDisplayText()` 拼接版本/时间/线程/堆栈，**不发往任何服务器**。

所以你要的三件事，上游**现在已经就是**这个行为：

| 你要的 | 现状 |
|---|---|
| 本地日志 | ✅ `LOG.error` 写了两次（简要 + 完整崩溃报告） |
| 弹窗提示 | ✅ `CrashWindow` + 已知错误的 `Alert` |
| 关闭上报 | ✅ 本就无网络行为 |

**旧版 README 那条"必须删 CrashReporter，否则错误会发到我的服务器"已经过时** —— 新版代码里没有上报逻辑。

### 仍然建议做的两件事

1. **实测验证**：用抓包工具（或用防火墙阻断）跑一次崩溃路径，确认无外发请求。代码分析有遗漏可能（比如某处反射加载了遥测模块）
2. **明确关闭自更新**（见第五节，这个是真问题）

---

## 二、改包名：推荐 Shadow Relocate，而不是改源码

你的诉求是"运行时体现为两个程序"。这**不需要改源码包名**——用构建期的字节码重定位即可。

### 方案对比

| 方案 | 同步上游 | 工作量 | 风险 |
|---|---|---|---|
| **A. Shadow relocate（推荐）** | **零冲突** | 配置 + 验证，约 2–3 天 | 中（需逐项验证） |
| B. 源码批量改名 | **每次 merge 全量冲突** | 改一次 1 天，之后每次同步都痛 | 高 |
| C. 两者结合 | 同 B | 最大 | 最高 |

**选 A。** 源码保持上游原样（merge 零冲突），构建产物里的 class 包名变成新的——运行时就是两个独立程序，满足你的诉求。

### 配置方式

HMCL 用 Gradle（Kotlin DSL）。在 `HMCL/build.gradle.kts` 加 Shadow 插件：

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    relocate("org.jackhuang.hmcl", "com.yourorg.yourlauncher")
    // 注意：只 relocate 本项目包名，不要碰第三方库
}
```

⚠️ **必须与已有的 fat jar 逻辑整合** —— HMCL 现在已经有打包 fat jar 的任务（`makeExecutables`），且 exe 是 stub + jar 字节拼接。要确认 shadowJar 的输出接进这条链，而不是产生两套产物。

### 几个好消息（降低了风险）

查了仓库结构，几个常见的大坑**不存在**：

| 常见坑 | HMCL 情况 |
|---|---|
| FXML 里硬编码类名 | ✅ **0 个 .fxml 文件**，UI 全代码构建 |
| 资源放在包路径下（需同步移动） | ✅ 资源都在 `assets/` 下，不在 `org/jackhuang/hmcl/` |
| 大量反射 `Class.forName("...")` | 需检查，但 UI 既非 FXML，反射面应该很小 |

---

## 三、必须先解决的 4 个前置问题

按风险从高到低：

### ① 字符串常量里的包名（**最容易踩**）

代码里包名以**字符串形式**出现，relocate 未必改写，改写了也可能改错：

```java
// CrashReport.shouldBeReport()
if (!stackTrace.contains("org.jackhuang"))   // 判断崩溃是否属于本程序
    return false;

// CrashReporter.Hole.SOURCE
pair("org.jackhuang.hmcl.util.ResourceNotFoundError", i18n("crash.NoClassDefFound")),
```

第一个尤其关键：它是**崩溃归属判断**。若 relocate 后堆栈里是新包名而常量还是旧的（或反之），这个判断会失效——表现为"崩溃了但不弹窗"。

**处理**：relocate 后手动确认这两处字符串被正确改写；若未改写，在 patch 里显式改掉。

> Shadow 默认会尝试改写字符串常量（不只是类名引用），但**必须验证，不能假设**。

### ② ServiceLoader（`META-INF/services`）

```
HMCL/src/main/resources/META-INF/services/java.net.spi.URLStreamHandlerProvider
```

Shadow 会重写 services 文件内容，但**要验证**。若类名没被改写，启动时报 `ServiceConfigurationError`，且错误可能在很底层，难排查。

**处理**：构建后用 `unzip -p HMCL.jar META-INF/services/*` 检查内容。

### ③ config.json 里是否存了类名

数据目录不动，意味着**老用户升级会读到旧配置**。若 config.json 里存了包名相关字符串（Gson 序列化某些对象时可能带上），会反序列化失败。

**处理**：
- `grep` 一份真实的 config.json 看有无 `org.jackhuang`
- 若有，写迁移逻辑（读到旧值时做一次映射）
- 保险起见给 config 加版本号字段

### ④ 日志与崩溃输出里的标识

`CrashReport.getDisplayText()` 输出：
```
---- Hello Minecraft! Crash Report ----
  Version: ...
```

这行标题是**产品名**，与附加条款相关，建议一并改（在 patch 里）。它不影响包名，但既然要区分，就改彻底。

---

## 四、数据目录不动：确认是对的，但要做两件事

保留 `~/.hmcl`（Linux）/`%APPDATA%/HMCL`（Windows）的好处很实在——用户从官方 HMCL 迁移过来，实例、账户、设置全在。

**但要处理**：

1. **配置迁移检查**（见上 ③）
2. **与官方 HMCL 共存问题** —— 如果用户的机器上同时装着官方 HMCL 和你的社区版，**两者会共用同一个数据目录**。这可能造成：
   - 同时启动时的文件竞争
   - 版本不同导致配置互相覆盖

   建议：提供一次性的"导入现有 HMCL 配置"选项，之后**切到独立目录**（如 `~/.yourlauncher`）。这样既照顾迁移，又避免长期共用。

   如果你确实想完全共用（让用户无缝切换），那至少要在文档里说明"不建议同时运行两个启动器"。

---

## 五、真正需要主动关闭的：自更新

**这是比崩溃上报更实际的问题。**

HMCL 有完整的自更新链路：

```
upgrade/UpdateChecker.java      检查更新
upgrade/UpdateHandler.java      处理更新
upgrade/IntegrityChecker.java   完整性校验
upgrade/RemoteVersion.java
assets/hmcl_signature_publickey.der   签名公钥
```

如果保留默认行为，用户点了"更新"，**会被更新成官方 HMCL** —— 你的社区版就消失了。

**必须处理**：

| 做法 | 说明 |
|---|---|
| **移除/禁用更新入口** | 设置页与更新提示都要处理 |
| 或指向你自己的更新源 | `-Dhmcl.update_source.override=<你的地址>` |
| 完整性校验要一并处理 | `hmcl_signature_publickey.der` 是官方签名公钥，用它校验你的产物必然失败 |

推荐：**首发阶段直接禁用自更新**，改为跳转到你的 Release 页面提示手动下载。等发布流程稳定后再做自建更新源。

同时 `IntegrityChecker` 相关逻辑也要 bypass，否则可能误报"文件被篡改"。

> 调试参数 `-Dhmcl.self_integrity_check.disable=true` 可绕过完整性检查，但这是调试用的，正式版应直接改代码。

---

## 六、改造清单

```
□ 1. 引入 shadow 插件，配置 relocate("org.jackhuang.hmcl" → "com.yourorg.launcher")
□ 2. 确认 shadowJar 输出接入现有 makeExecutables 链（exe 是 stub+jar 拼接）
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

**工期估算**：约 **3–5 天**（配置 1 天 + 逐项验证 2 天 + 自更新处理 1–2 天）。

---

## 七、一个提醒

改包名带来的最大风险**不是改造本身，而是它让"同步上游"从纯文本 merge 变成了需要重新验证的过程**。

也就是说，即使 relocate 让源码零冲突，**每次同步上游后仍建议跑一遍验证清单**（尤其是 ③④⑤ 三项）。这三个如果出问题，都是运行时才暴露的。

如果后面觉得这个成本太高，退路是：**先不改包名发布第一个版本，等核心功能稳定后再做**。包名改造是纯机械工作，随时可以补。

---

## 附：验证方法

```bash
# 1. class 包名是否已改
unzip -l HMCL.jar | grep -c "com/yourorg/launcher"
unzip -l HMCL.jar | grep -c "org/jackhuang/hmcl"    # 应为 0 或仅剩资源

# 2. services 文件是否被改写
unzip -p HMCL.jar META-INF/services/java.net.spi.URLStreamHandlerProvider

# 3. 字符串常量是否被改写（反编译抽查）
javap -c -p -cp HMCL.jar com.yourorg.launcher.countly.CrashReport | grep "org.jackhuang"

# 4. 崩溃路径抓包
#    用防火墙阻断或 Wireshark 观察，触发一次崩溃，确认无外发
```
