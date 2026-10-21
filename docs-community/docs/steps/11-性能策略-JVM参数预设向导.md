# 步骤 11 · 性能策略（Ⅱ）· JVM 参数预设向导

> **阶段**：阶段三 · 性能与内存 ｜ **优先级**：P0 ｜ **工期**：1.5–2 周
> **前置**：[步骤 10](10-性能策略-Java发行版默认偏好.md) ｜ **后续**：[步骤 12](12-内存优化P0-跨平台基座.md)
> **代码侵入**：0（只读写上游实例设置 `jvmOptions`，不碰 `DefaultLauncher`；UI 挂首页仪表盘）
> **口径纪律**：不承诺帧数/启动时间提升百分比。向导的价值是"**少填错、少冲突、少一次启动失败**"，不是"让游戏更快"。

---

## 1. 目标

> **v3.10 注记**：本步只生成 JVM 参数；启动游戏前的一次 `System.gc()` 属于步骤 23 的“启动前优化启动器内存”，不复用本步向导，也不写入 `jvmOptions`。

1. 把"调 JVM 参数"从查教程抄参数，变成**选两个选项 + 确认**；
2. 生成的参数只写上游实例设置字段（`jvmOptions`），与手动编辑完全同源，可随时改回；
3. **不与上游已注入的优化打架**：上游默认注入 G1 全套参数，我们绝不重复注入；
4. 冲突检测给出可执行动作（移除参数 / 打开上游开关 / 忽略），而不是一句"可能冲突"；
5. 用户手改过参数后，向导自动进入"自定义"态，不覆盖用户编辑。

完成标志（DoD）：

- [ ] 4 个档位（跟随上游 / G1 堆大小 / ZGC 大内存 / 自定义）可用，默认"跟随上游"；
- [ ] G1 档只写 `-Xms/-Xmx`，**不写** `UseG1GC`、`MaxGCPauseMillis` 等上游已注入项；
- [ ] ZGC 档校验 JDK 版本（≥21）与堆大小（≥12G），不满足则降级并提示原因；
- [ ] `JvmMemoryOptions` 单测覆盖 Java 11/12/17/21/25 × G1/ZGC/Parallel × optIn true/false；
- [ ] 冲突检测覆盖 6 类（GC 选择、堆大小重复、已移除参数、与上游开关冲突、ZGC+小堆、32 位 JVM 大堆）；
- [ ] 写入通过 `GameSettings.Instance.jvmOptionsProperty()` + 上游自动保存通道，重启后保持；
- [ ] 实例设置为只读（schema 不支持）时，向导整体禁用并说明原因；
- [ ] 一键"清除向导生成参数"可回到默认；
- [ ] 上游 `git diff` 为 0。

---

## 2. 上游事实（v3.16.4，必须先理解再设计）

### 2.1 上游默认注入了什么

`HMCLCore/src/main/java/org/jackhuang/hmcl/launch/DefaultLauncher.java`：

```java
if (!options.isNoGeneratedOptimizingJVMArgs()) {                 // :210
    res.addUnstableDefault("UnlockExperimentalVMOptions", true); // :212
    res.addUnstableDefault("UnlockDiagnosticVMOptions", true);   // :213

    if (javaVersion >= 8
            && res.noneMatch(arg -> "-XX:-UseG1GC".equals(arg)
                    || (arg.startsWith("-XX:+Use") && arg.endsWith("GC")))) {  // :216-217
        res.addUnstableDefault("UseG1GC", true);                 // :218
        res.addUnstableDefault("G1MixedGCCountTarget", "5");     // :219
        res.addUnstableDefault("G1NewSizePercent", "20");        // :220
        res.addUnstableDefault("G1ReservePercent", "20");        // :221
        res.addUnstableDefault("MaxGCPauseMillis", "50");        // :222
        res.addUnstableDefault("G1HeapRegionSize", "32m");       // :223
    }

    res.addUnstableDefault("OmitStackTraceInFastThrow", false);  // :226
    // JIT 相关：MaxInlineLevel（java<=8）、DontCompileHugeMethods、MaxNodeLimit、
    //           ReservedCodeCacheSize=400M 等，仅在 64 位且物理内存 > 4G 时注入（:229-247）
    // Java 25/26 额外注入 UseCompactObjectHeaders（:249-252）
}
```

以及之后与 JVM 参数无关的默认项（`-Xss`、`--illegal-access`、`--sun-misc-unsafe-memory-access`、Forge 相关 `-D` 等）。

**两个关键语义**：

1. 用户参数里只要出现 `-XX:+Use*GC`（任意 GC），上游就**整体跳过** G1 参数块——不是"冲突报错"，而是"上游让位给用户"；
2. `noOptimizingJVMOptions=true` 时上游**整套优化参数都不注入**（连 `UnlockExperimentalVMOptions` 也没有）。

这两点决定了向导的设计空间（见 §3.3）。

### 2.2 实例设置模型（我们要写的字段）

`HMCL/src/main/java/org/jackhuang/hmcl/setting/GameSettings.java`：

| 常量 / 方法 | 值 / 签名 | 说明 |
|---|---|---|
| `PROPERTY_JVM_OPTIONS` | `"jvmOptions"` | 用户自定义 JVM 参数字符串（:392） |
| `jvmOptionsProperty()` | `InheritableProperty<String>` | 读写入口（:399） |
| `PROPERTY_NO_JVM_OPTIONS` | `"noJVMOptions"` | 完全不使用任何 JVM 参数（含我们写的） |
| `PROPERTY_NO_OPTIMIZING_JVM_OPTIONS` | `"noOptimizingJVMOptions"` | 不使用上游生成优化参数（:416-427） |
| `PROPERTY_NOT_CHECK_JVM` | `"notCheckJVM"` | 跳过 JVM 合法性检查 |

持久化：`HMCL/src/main/java/org/jackhuang/hmcl/game/HMCLGameRepository.java`

- 实例设置文件：`<实例配置目录>/instance-game-settings.json`（`INSTANCE_GAME_SETTINGS_FILENAME`，:97、:337-338）；
- 读写入口：`getInstanceGameSettingsOrCreate(id)`（:482）——**返回的 `setting` 上 `addListener(a -> saveGameSettings(id))`（:459）**，即 `property.setValue(...)` 会自动保存；
- 只读保护：schema 不受支持时 `isInstanceGameSettingsReadOnly(id)` 为 `true`（:490-498），此时 `setValue` 不应调用；
- 上游 UI 用的也是同一文件与同一 `jvmOptions` 字段 → 与手动编辑天然一致。

### 2.3 内存与 Java 版本（只读复用）

| 用途 | API |
|---|---|
| 实例内存上下限 | `GameSettings.maxMemoryProperty()` / `minMemoryProperty()`（`HMCL/src/main/java/org/jackhuang/hmcl/setting/GameSettings.java:468/476`） |
| 物理内存 | `SystemInfo.getTotalMemorySize()`（`HMCLCore/src/main/java/org/jackhuang/hmcl/util/platform/SystemInfo.java:102`） |
| 最低 Java 版本 | `GameJavaVersion.getMinimumJavaVersion(GameVersionNumber)`（`HMCLCore/src/main/java/org/jackhuang/hmcl/game/GameJavaVersion.java:42`） |
| 当前 Java 位宽/版本 | `JavaRuntime.getBits()`、`getParsedVersion()` |

---

## 3. 设计

### 3.1 向导位置与交互

入口：首页仪表盘 → `java-recommend` 卡片（标题"Java / 运行环境"，[步骤 14](14-首页组件化B-P0组件与接入.md) 提供）→ 顶部 tab「JVM 参数」→ 向导（实例下拉 + 参数编辑）。**不新开上游设置页，也不改实例设置页**；卡片内 tab 切换不新增组件、不改 14 的六个 P0 卡片清单。

```
实例：1.20.4-Fabric（Java 21，64 位，物理内存 16G）
当前：跟随上游（HMCL 已注入 G1 优化）

档位：
  ● 跟随上游（推荐）          不写入自定义参数，使用 HMCL 内置优化
  ○ 固定堆大小（G1）          -Xms4096m -Xmx4096m
  ○ 大内存（ZGC）             -Xms12288m -Xmx12288m -XX:+UseZGC
  ○ 自定义                    手动编辑（仅做冲突检测）

[预览]  -Xms4096m -Xmx4096m
[应用]  [清除向导参数]  [恢复上游默认]
冲突检测：未发现
```

- 实例下拉复用上游实例列表；未选实例时不显示向导；
- "跟随上游"= `jvmOptions` 中**删除由向导写入的片段**（保留用户手填的其他参数——通过精确匹配向导生成串实现，见 §3.5）；
- 应用后展示"最终 JVM 参数"预览（只读）：上游生成参数 + 实例 `jvmOptions` 的合并结果，方便排查；
- 面板底部固定一行小字：**"向导只设置内存与 GC 选择，不改变 HMCL 的其他优化参数。"**

### 3.1.1 包与类（全部新增，不改上游文件）

```java
org.jackhuang.hmcl.community.jvm
├── JvmPresetWizard        // 向导 UI（JavaFX，无参构造；嵌入 14 的 java-recommend 卡片 tab）
├── JvmPreset              // enum：UPSTREAM / G1 / ZGC / CUSTOM
├── JvmArgValidator        // 冲突检测 8 条规则（纯函数，可单测）
├── JvmArgWriter           // 写 GameSettings.jvmOptionsProperty() + 只读/schema 保护
├── JvmWizardStore         // 状态存储：community-settings.json 的 jvmWizard.<instanceId>
└── JvmMemoryOptions       // JEP 346 纯函数：生成/判断 "空闲归还内存" 三项参数（12 复用做验收）
```

> 所有写入只经 `JvmArgWriter`；UI 不得直接 `setValue`（保证"清除向导参数"与冲突检测同源）。

### 3.2 档位规则

| 档位 | 写入 `jvmOptions` 的内容 | 适用条件 | 备注 |
|---|---|---|---|
| 跟随上游 | 不写（清空向导片段） | 所有版本 | 默认 |
| 固定堆大小（G1） | `-Xms<heap>m -Xmx<heap>m` | 所有版本 | **不写任何 G1 子参数**（上游已注入）；可选"高级→空闲时归还内存（JEP 346）"，勾选才追加本步 §3.2.1 `JvmMemoryOptions.generate(...)` 的三项 |
| 大内存（ZGC） | `-Xms<heap>m -Xmx<heap>m -XX:+UseZGC` | Java ≥ 21 且 heap ≥ 12288 且 64 位且物理内存 ≥ heap + 4G | 上游检测到 `-XX:+UseZGC` 会跳过 G1 块，避免混用 |
| 自定义 | 用户自由输入 | 所有版本 | 只检测不修改 |

**ZGC 档的版本/参数纪律**（避免典型翻车点）：

- 只加 `-XX:+UseZGC`，**不加** `-XX:+ZGenerational`（JDK 21 需 `-XX:+UnlockExperimentalVMOptions`，JDK 23+ 该参数被移除；跨版本不可移植）；
- 不把 G1 的任何子参数与 ZGC 同放（上游虽会跳过 G1 块，但用户历史参数里可能残留 `G1ReservePercent` 等，检测器要拦）；
- 堆大小建议值 = `min(物理内存 - 4G, maxMemory 当前值)`，且 ≥ 12G 才允许选 ZGC；
- 32 位 JVM 不允许 ZGC（直接禁用选项并说明）。

#### 3.2.1 高级选项：JEP 346（`JvmMemoryOptions`，纯函数）

```java
/// 生成 JEP 346 参数片段。`userOptIn=false` 时恒返回空（默认不打扰）。
public static GenerationResult generate(int javaMajor, @NotNull String gcName, boolean userOptIn);
public record GenerationResult(@NotNull List<String> args, @Nullable String skipReason);
// skipReason: null / "opt_out" / "java_too_old" / "gc_not_g1"
```

| 条件 | 结果 |
|---|---|
| `userOptIn=false` | 空 |
| `javaMajor < 12` | 空，`skipReason="java_too_old"`（JEP 346 = JDK 12+） |
| GC 非 G1（ZGC/Shenandoah/Parallel 等） | 空，`skipReason="gc_not_g1"` |
| 否则 | `-XX:G1PeriodicGCInterval=600000`、`-XX:MinHeapFreeRatio=5`、`-XX:MaxHeapFreeRatio=25` |

- 不写 `G1PeriodicGCSystemLoadThreshold`（默认 0 = 忽略负载；Windows 的 `getloadavg` 模拟不可移植）；
- 不写 `G1PeriodicGCInvokesConcurrent`、`ShrinkHeapInSteps`（默认行为已正确）；
- 该片段与 ZGC 档互斥；G1 档勾选后写入 `jvmOptions` 尾部，冲突检测器需把它识别为"向导生成片段"（可被 [清除向导参数] 精确移除）。

**-Xms == -Xmx 的解释文案**（必须写）：

> 固定堆大小可减少运行中扩堆造成的卡顿；代价是任务管理器里内存占用会更"满"，这是预期行为，不是泄漏。

### 3.3 与上游两个开关的关系

| 用户当前状态 | 选择 G1 档时向导行为 |
|---|---|
| `noOptimizingJVMOptions=false`（默认） | 只写堆大小；提示"G1 参数由 HMCL 注入" |
| `noOptimizingJVMOptions=true` | 提示"你已关闭 HMCL 优化参数，G1 子参数不会注入"；提供"由向导补齐完整 G1 参数"复选项（勾选才写 §2.1 那 6 个参数） |
| `noJVMOptions=true` | 提示"你已关闭全部 JVM 参数，向导写入不会生效"；禁用应用按钮 |
| 用户参数里已有 `-XX:+Use*GC` | 提示"检测到自定义 GC，HMCL 将跳过内置 G1 参数"（这是上游既定行为，非错误） |

### 3.4 冲突检测规则

输出结构：`List<Conflict{ level(ERROR/WARN/INFO), matchedArg, messageKey, action }>`；ERROR 只警告"可能起不来"，**不阻止应用**（用户永远有最终决定权）。

| # | 规则 | 级别 | 文案/动作 |
|---|---|---|---|
| 1 | `-XX:+UseConcMarkSweepGC` / `-XX:+UseParNewGC` 等**已在新 JDK 移除**的 GC | ERROR | "该参数在较新 JDK 已被移除，会导致无法启动" → 建议移除（按实例 Java 版本判定，不确定则 WARN） |
| 2 | 同时出现两个不同 `-XX:+Use*GC` | ERROR | "只能指定一个 GC" |
| 3 | 向导档位为 ZGC，但参数里残留 `-XX:+UseG1GC` 或 G1 子参数 | ERROR | "ZGC 与 G1 参数混用" → 一键移除 G1 项 |
| 4 | `-Xmx` 出现多次或与向导生成值不一致 | WARN | "后出现的值生效，可能不是你想要的" |
| 5 | `-Xms` > `-Xmx` | ERROR | "初始堆大于最大堆，JVM 无法启动" |
| 6 | 32 位 JVM + `-Xmx` ≥ 1536m | WARN | "32 位 JVM 堆过大易 OOM/无法启动" |
| 7 | `-XX:+PrintGCDetails` 等旧日志参数 | WARN | "新版 JDK 已移除，建议改用 `-Xlog:gc`" |
| 8 | 参数里含 `-XX:+UseG1GC` 而用户选了"跟随上游" | INFO | "你自定义了 GC，HMCL 内置 G1 参数将不再注入"（上游 :216-217 的真实行为） |

> 规则实现为纯函数（输入：参数字符串、Java 版本、位宽、档位 → 输出冲突列表），**可单测**，不依赖 UI/文件系统。

### 3.5 向导状态与"用户手改"判定

`community-settings.json` 新增：

```json
{ "jvmWizard": {
    "<instanceId>": {
      "mode": "upstream|g1|zgc|custom",
      "heapMb": 4096,
      "generated": "-Xms4096m -Xmx4096m",
      "generatedAt": "2026-10-21T13:50:00+08:00"
    }
} }
```

判定逻辑：

- 打开面板时，读 `jvmOptions` 当前值；
  - 与 `generated` 完全相同 → 保持向导档位；
  - 为空且 `mode=upstream` → 正常；
  - 其他情况 → 面板显示"检测到你手动修改过参数"，档位切到"自定义"，**不自动改任何东西**；
- 应用：`setting.jvmOptionsProperty().setValue(generated)`（自动保存）；
- "清除向导参数"：仅当当前值等于 `generated` 时才清空（避免误删用户后加的参数），否则按钮禁用并提示；
- 实例被删除/重命名 → 该条 `jvmWizard` 条目在下次扫描时清理（不报错）。

### 3.6 UI 流程与 i18n

```
[实例下拉] → [档位单选] → (堆大小滑块/输入，仅 g1/zgc) → [预览] → [应用]
                                              ↘ [冲突列表]（可展开，逐条动作）
```

i18n key：

```
community.jvm.title
community.jvm.mode.upstream / g1 / zgc / custom
community.jvm.heap
community.jvm.preview
community.jvm.action.apply / .clear / .refresh
community.jvm.conflict.<ruleId>            # 8 条
community.jvm.note.upstream_g1 / .no_optimizing / .no_jvm
community.jvm.zgc.requires / .heap_too_small / .not_64bit
community.jvm.xms_equals_xmx.note
community.jvm.readonly_instance
```

三份 properties 同步（英文基座 + `zh` + `zh_CN`；其余语言回退基座）。

### 3.7 与 08 / 12 的边界

- 08 体检的 `memory.budget` 只报告"分配超过物理内存"风险，**不自动改堆**；向导是用户主动行为；
- 12（跨平台基座）复用本步骤的 `JvmPreset` 规则、冲突检测纯函数与 `JvmMemoryOptions`（12 在 11 之后实施，单向依赖 11）；
- 15（游玩日历）不读取 JVM 参数。

---

## 4. 实施顺序与工期（1.5–2 周 = 8–10 人日）

| # | 工作 | 人日 |
|---|---|---|
| 1 | `JvmPresetRules` 纯函数（档位生成 + 8 条冲突检测）+ 单测 | 2 |
| 2 | 状态存储 `jvmWizard`（读写/容错/清理） | 1 |
| 3 | 面板 UI（实例下拉、档位、堆滑块、预览、冲突列表） | 2.5 |
| 4 | 应用/清除（写 `jvmOptions`、只读实例保护、手改判定） | 1.5 |
| 5 | "最终参数预览"（合并上游生成参数，只读展示） | 1 |
| 6 | i18n 三份 + 文案评审（口径纪律） | 0.5 |
| 7 | 联调：Java 8/17/21/25、32 位、只读实例、noOptimizing 开关 | 1 |
| 8 | 回归：向导写入后上游实例设置页显示一致、可手动改回 | 0.5 |

---

## 5. 测试矩阵

| 实例 | 档位/输入 | 期望 |
|---|---|---|
| 1.20.4 + Java 21 + 4G | G1 | `-Xms4096m -Xmx4096m`；无 G1 子参数 |
| 1.21 + Java 21 + 16G + 200 mods | ZGC(12G) | `-XX:+UseZGC` 写入；冲突列表为空 |
| 1.12.2 + Java 8 + 4G | ZGC | 选项禁用（Java < 21），提示原因 |
| Java 21 + 8G 物理内存 | ZGC | 禁用（堆 < 12G 或物理不足） |
| 用户手填 `-XX:+UseConcMarkSweepGC` | 跟随上游 | ERROR：已移除参数；INFO：上游将跳过 G1 |
| 用户手填 `-Xms8G -Xmx4G` | 任意 | ERROR：初始堆 > 最大堆 |
| `noOptimizingJVMOptions=true` | G1 | 提示；勾选补齐后才写 G1 子参数 |
| `noJVMOptions=true` | 任意 | 应用按钮禁用 + 原因 |
| 只读实例设置（未来 schema） | 任意 | 面板禁用 + "该实例设置由更高版本创建，暂不可修改" |
| 应用后重启 | — | `instance-game-settings.json` 中 `jvmOptions` 持久化 |
| 手动改参数后重开面板 | — | 自动切"自定义"，不清空、不覆盖 |

---

## 6. 验收清单

- [ ] 向导生成串与 `generated` 完全一致（逐字节比较，含空格）；
- [ ] 冲突检测纯函数单测覆盖表内 8 条 + 边界（空串、多空格、重复参数）；
- [ ] `git diff` 无上游文件改动；
- [ ] 上游实例设置页打开后显示的 JVM 参数与向导一致；
- [ ] 只读实例不产生任何写入（文件 mtime/权限验证）；
- [ ] 文案无性能百分比承诺（review 记录）；
- [ ] i18n key 三份一致（英文基座 + `zh` + `zh_CN`，其余语言回退基座）。

---

## 7. 风险

| 风险 | 等级 | 应对 |
|---|---|---|
| 上游改 `DefaultLauncher` 注入项/条件 → 向导"不重复注入"假设失效 | **高** | 升级 checklist 核对 `DefaultLauncher` 的 `addUnstableDefault` 列表；文档标注 v3.16.4 行号；G1 档始终只写堆大小，天然低耦合 |
| ZGC 在小堆/低核机器上体验更差 | 中 | 强制 ≥12G + ≥4 核（读 `Runtime.availableProcessors()`）+ 文案说明权衡 |
| 用户以为向导会"自动优化所有" | 中 | 面板固定说明 + 文案只承诺"减少参数冲突" |
| 手改判定误判（空格/顺序差异） | 中 | 比较时归一化空白；判定为"手改"只影响 UI 状态，不改参数 |
| 只读实例被写入导致数据损坏 | **高** | 写前检查 `isInstanceGameSettingsReadOnly(id)`；单测覆盖 |
| `-Xms/-Xmx` 单位混用（G 与 m） | 低 | 向导只输出 `m` 单位；解析兼容 `g/G`（1G=1024m） |

---

## 8. 回退

- 卸载向导入口（仪表盘 `enabled=false`）→ 参数仍在 `jvmOptions`，可手动清；
- "清除向导参数"按钮即用户级回退；
- 删除 `community-settings.json` 的 `jvmWizard` 段 → 向导状态丢失，参数保留（安全方向：宁可参数在，不可误删）。

---

## 9. 关联

- 上游注入逻辑：`HMCLCore/src/main/java/org/jackhuang/hmcl/launch/DefaultLauncher.java:210-252`
- 实例设置：`HMCL/src/main/java/org/jackhuang/hmcl/game/HMCLGameRepository.java:97/337/459/474/482/490`
- 内存体检：[步骤 08](08-启动保障-启动前体检.md)
- 跨平台基座：[步骤 12](12-内存优化P0-跨平台基座.md)
- 面板容器：[步骤 13](13-首页组件化A-框架搭建.md)

---

## 10. 实现记录（v2.4，2026-10-21）

### 10.1 提交与验证

| 项 | 值 |
|---|---|
| 代码提交 | `f51e38183 feat(11): JVM preset wizard with conflict detection`（13 files, +2169）+ `a54a05502 feat(11): opt-in full G1 arguments…` |
| 合并 | `35c42aa11 merge 11-jvm-wizard: JVM preset wizard verified locally` |
| 编译 | `:HMCL:compileJava` 通过（仅 3 条既有 JavaFX `--add-exports` 警告） |
| 单测 | `:HMCL:test --tests 'org.jackhuang.hmcl.community.*'` = **82 tests / 0 failures**（新增 28） |
| 构建 | `VERSION_ROOT=3.16 BUILD_NUMBER=1000 :HMCL:shadowJar` → `HMCL/build/libs/HMCL-3.16.1000.jar` **10,711,716 B**；含 `community/jvm/` 6 个顶层类共 15 个 class；3 份语言各 47 个 `community.jvm.*` key；`Main-Class` 仍为 `CommunityBootstrap` |

### 10.2 落盘文件

| 文件 | 内容 |
|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/community/jvm/JvmPreset.java` | 档位枚举 + `storageValue()` / `i18nKey()` / `of(String)`（未知回退 UPSTREAM） |
| `…/jvm/JvmMemoryOptions.java` | JEP 346 纯函数与 `GenerationResult`；常量 `MIN_JAVA_MAJOR=12`、三项参数 |
| `…/jvm/JvmArgValidator.java` | 11 条规则（文档 8 条 + ZGC 的 Java/位宽/小堆/物理内存 4 条）；`Input`/`Conflict`/`Severity`；`parseHeapMb` / `tokenize` 公开可测 |
| `…/jvm/JvmArgWriter.java` | 唯一写入口：`compose` / `apply` / `clear` / `removeArg` / `merge` / `strip`；`Status` 三态 |
| `…/jvm/JvmWizardStore.java` | `jvmWizard` 段落读写、删除、`prune`、`loadAll`；默认堆 4096 |
| `…/jvm/JvmPresetWizard.java` | 无参构造 UI；`setRepository(HMCLGameRepository)`、`refresh()`、`isLoaded()`、`hasRepository()` |
| `HMCL/src/test/java/…/jvm/JvmMemoryOptionsTest.java` | 4 个：opt_out / java_too_old / gc_not_g1 / Java 12 边界生成 |
| `…/jvm/JvmArgValidatorTest.java` | 11 个：堆解析、移除 GC 分级、多 GC、ZGC 混 G1、重复 Xmx、Xms>Xmx、32 位大堆、旧日志参数、上游自定义 G1、ZGC 前置、空串 |
| `…/jvm/JvmWizardStoreTest.java` | 6 个：往返、损坏条目、删除、prune、未知档位回退、loadAll |
| `…/jvm/JvmArgWriterTest.java` | 7 个：compose、JEP 346 追加、merge 保留用户参数、strip、重复 token、单参数移除 |

i18n：三份 `assets/lang/I18N.properties`、`I18N_zh.properties`、`I18N_zh_CN.properties` 各新增 47 个 key
（`community.jvm.*`，含 12 条冲突文案、3 档/自定义、状态与只读提示、`full_g1`）。

### 10.3 关键实现决定

1. **`JvmWizardStore` 独立成类**：文档 §3.1.1 只列了四个纯类，状态读写单独抽出，便于 13/14 与测试复用；schema 是“实例 id → 对象”，不放进 `java` 段。
2. **手改判定用 token 归一化**：`normalize` = `String.join(" ", tokenize(x))`，因此多空格/换行不算手改，顺序变化算手改（保守方向）。
3. **`JvmArgWriter.apply` 先移除旧片段再追加新片段**：`previousGenerated` 来自 store；用户手填的其它参数原样保留。清除只在“当前值与生成值完全一致”时执行。
4. **校验输入是“候选串”**：G1/ZGC 档用 `merge(当前, 旧生成, 新生成)` 生成候选串后再跑规则，这样能提前暴露“残留 G1 参数”“-Xms>-Xmx”等问题；UPSTREAM/CUSTOM 只校验当前串。
5. **`noOptimizingJVMOptions=true` 时提供“补齐完整 G1 参数”勾选**：勾选才追加文档 §2.1 的 6 项上游默认 G1 参数；默认不勾，避免与上游重复注入。
6. **Java 版本/位宽可未知**：`resolveJava` 失败时 `javaMajor=0`、`bits=UNKNOWN`，相关规则降级为 WARN 或跳过，不阻断面板打开。
7. **冲突动作只实现 `remove`**：每条可移除冲突在 UI 上给一个 [移除] 按钮（走 `JvmArgWriter.removeArg`），没有实现“打开上游开关”的跳转（13 的设置面板已有入口）。

### 10.4 相对文档的偏差

| # | 文档 | 实现 | 原因 |
|---|---|---|---|
| 1 | i18n “三份 properties” | 三份（英文基座 + zh + zh_CN） | 与 08/09/10 口径统一，其它语言回退基座 |
| 2 | §3.1.1 只列 4 个类 | 多一个 `JvmWizardStore` | 状态读写独立可测 |
| 3 | “应用后展示上游生成参数 + 实例参数合并预览” | 预览只展示“向导将写入的片段” | 上游参数由 `DefaultLauncher` 运行时按 Java/内存/架构动态生成，静态无法完整复现；避免给出错误预览 |
| 4 | 自定义档“用户自由输入” | 自定义只读检测，编辑在 HMCL 实例设置页 | 不在向导里再造一个参数编辑器，避免与上游设置页双写 |
| 5 | 规则表 8 条 | 11 条（+ZGC Java 版本、32 位、物理内存不足） | DoD 要求 ZGC 前置校验；多出的规则只提示不阻断 |
| 6 | `JvmPresetWizard` 直接可嵌入 | 需要 14 卡片调用 `setRepository(repo)` 并触发 `refresh()` | 无参构造但仓库由容器注入；14 未实现，故当前无宿主入口 |
| 7 | “ZGC 建议堆 = min(物理内存-4G, 当前 maxMemory)” | 推荐堆 = 物理内存一半夹在 1–8 GiB，用户可改；ZGC 前置校验独立 | 简化默认值，避免依赖上游 maxMemory 的迁移状态 |

### 10.5 未完成 / 未验证

- 本机无显示环境：GUI 未做真机点击验证（面板仅编译/构建通过）；
- 14 的 `java-recommend` 卡片未实现，向导当前**没有宿主入口**；14 落地时须在卡片 tab 中 `new JvmPresetWizard()` + `setRepository(repo)`；
- 12 的语义验收尚未做（12 未实现）；
- 只读实例、`noJVMOptions=true`、`noOptimizingJVMOptions=true` 三种降级路径只做了代码分支，未做 GUI 演练；
- 冲突规则中的“移除 GC 后 `-Xms/-Xmx` 不动”行为未做端到端验证（需真实实例）。

### 10.6 下游约束

- 12 复用 `JvmPreset` / `JvmArgValidator` / `JvmMemoryOptions` 时必须保持纯函数签名，不得复制规则；
- 14 的卡片只负责创建/挂载向导，**不得**绕过 `JvmArgWriter` 直接写 `jvmOptionsProperty()`；
- 任何新规则必须同步三份 i18n 的 `community.jvm.conflict.<ruleId>`；
- 13 的设置面板扩展点可挂本向导的入口（可选），但不能成为唯一入口（14 卡片为主入口）。
