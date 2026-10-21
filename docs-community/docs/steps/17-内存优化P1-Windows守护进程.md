# 步骤 17 · 内存优化（P1）· Windows 后台守护（L2）

> **阶段**：阶段五 · 快照与发布 ｜ **优先级**：P1 ｜ **工期**：2–3 周
> **前置**：[步骤 12](12-内存优化P0-跨平台基座.md)（`MemoryMetrics`/L0+ 基座）、[步骤 06](06-数据目录与用户迁移.md)（数据根与启动器目录探测） ｜ **后续**：[步骤 18](18-内存优化P2-发布准备.md)（签名、SBOM、分发）
> **代码侵入**：0（纯新增模块 + 首页"内存"卡片入口，不碰上游文件）
> **平台**：仅 Windows x86/x64；其它平台本步不可用（12 的 L0+ 照常工作）
> **默认**：**关闭**；用户显式启用后才安装守护（`00 §5.3`、`§13 #4/#5` 已定案：HKCU 登录后守护，**不装服务、不提权**）

---

## 1. 目标

在 Windows 上，用"登录后启动的极小守护进程 + 同用户工作集裁剪"实现 L2 内存优化：在游戏启动前/运行期间，把当前用户可操作进程的工作集归还给系统，降低"物理内存不足 → 换页 → 卡顿/启动失败"的概率。

明确的能力边界（**诚实交付，不夸大**）：

| 能做 | 不能做 |
|---|---|
| 裁剪**当前用户、非提权、非系统**进程的工作集 | 裁剪 SYSTEM/其他用户/受保护进程的工作集（不提权，直接跳过并计数） |
| 启动器在线时由主程序发起；登录后/游戏运行中由守护发起 | 杀进程、改优先级、改页文件大小（L2 只做 `EmptyWorkingSet`） |
| 失败静默降级（主程序内联兜底 → 不执行） | 保证释放量、保证游戏帧数提升 |

完成标志（DoD）：

- [ ] 启用/禁用幂等：启用写入 HKCU `Run` 并落盘守护配置；禁用停止守护、删 `Run`、删文件，**不留孤儿**；
- [ ] 登录后守护静默启动，空闲 CPU ≈0%、常驻内存 ≤ 8 MiB，无窗口、无托盘、无网络；
- [ ] 游戏启动前 1 次裁剪（条件不满足则跳过），结果有日志与 UI 可查；
- [ ] 游戏运行中按间隔（默认 15 分钟）裁剪**后台进程**，**不裁正在运行的游戏 JVM**（避免换页抖动）；
- [ ] 守护不可用/超时/被杀 → 主程序内联兜底或直接跳过，**启动流程零阻断**；
- [ ] 调用方校验：管道 ACL 仅当前用户 + 客户端镜像在安装目录内 + 祖先进程为 `LMCL.exe` + 客户端镜像哈希符合清单；不满足则拒绝并提示"守护需更新"，回退内联；
- [ ] 便携目录移动后，下次启动自动修正 `Run` 路径；安装目录不可写/被杀软锁定等异常均有降级路径；
- [ ] 卸载可逆：`Run` 项、守护进程、配置、日志全部清理；
- [ ] 14 个测试用例通过（见 §6）。

---

## 2. 决策与依据

### 2.1 形态：HKCU 登录守护，不装系统服务

`00 §5.3`（L347）与 `§13` 决策 #4/#5 已定案。依据：

| 方案 | 否决/采用理由 |
|---|---|
| Windows 服务（LocalSystem） | 需要管理员权限安装；便携版（zip + exe，无安装器）根本无法装；服务以 SYSTEM 运行会放大攻击面；**否决** |
| 计划任务（登录触发） | 需要额外 COM/XML 操作，UI 上不可见、卸载易残留；价值与 HKCU Run 相同；**否决** |
| **HKCU `Run` + 后台守护** | 零提权、随用户登录启动、卸载=删注册表项+删文件；**采用** |

### 2.2 语义边界：同用户工作集裁剪

不提升权限 ⇒ 只能操作当前用户、未提权进程。因此：

- 术语统一：L2 = "**当前用户进程工作集裁剪**"，文档与 UI 都不写"释放全系统内存"；
- 提权进程（任务管理器"以管理员身份运行"的应用）与 SYSTEM 进程：`OpenProcess` 失败或 SID 不符 → 计入 `skipped`；
- 释放量 = Σ(裁剪前工作集 − 裁剪后工作集)，下限 0；只作为参考数字，UI 文案"约"。

### 2.3 为什么仍要独立守护（而不是只在启动器里内联）

同用户裁剪**本可以**在启动器 JVM 内用 JNA 完成（主程序也确实实现了内联兜底），但守护解决了三个内联做不到的场景：

1. **启动器已退出、游戏仍在跑**：用户把"启动器可见性"设为"启动后关闭"时，主程序不在，周期裁剪需要一个常驻执行者；
2. **登录后、启动器启动前**：系统刚登录时后台进程多，先做一次按需整理能改善启动器与游戏的冷启动；
3. **攻击面隔离**：把所有 `OpenProcess/EmptyWorkingSet` 调用关进一个小二进制里，JVM 侧只保留"发一条固定命令"的能力；守护不联网、不解析路径、不接受任意 PID。

> 因此守护是"**被动的执行器**"，不是"常驻监控器"：无定时全网扫描，只在启动前/会话期间按需触发。

### 2.4 触发策略（避免"为了省内存而制造卡顿"）

| 触发 | 条件 | 动作 |
|---|---|---|
| 启动前（08 `onLaunchRequested`） | L2 已启用 且 `available < 实例最大堆 + 1.5 GiB`（可配） | 裁剪全部同用户非提权进程（游戏尚未启动，无抖动风险） |
| 游戏运行中（守护 watch） | 收到 `watch` 后每 `intervalSeconds`（默认 900 s）；`available < 实例最大堆 + 1.5 GiB`；距上次 ≥ 5 分钟 | 裁剪除**被监视游戏 PID**外的同用户进程 |
| 手动 | 用户在"内存优化"对话框点"立即整理" | 同启动前规则；节流 5 分钟 |
| 登录后 | 守护启动后 5 分钟，若 `available < total × 20%` | 一次轻量整理（只裁剪空闲进程，跳过前台进程） |

- 阈值、间隔、开关都在 `community-settings.json`（08 定义的文件，17 只追加 `memOpt` 段），**默认关闭**；
- 裁剪总耗时上限 10 s，超时返回部分结果（不阻塞启动）；
- 单飞：同一时刻只允许一次裁剪；守护重启后保留上次时间戳（限流不失效）。

### 2.5 安全模型

| 攻击面 | 措施 |
|---|---|
| 其他用户调用管道 | 命名管道 DACL 仅授予当前用户 SID；守护校验客户端进程 Token 用户 = 自身用户 |
| 同用户的其他进程调用 | 校验客户端镜像路径在启动器目录内、祖先进程为 `LMCL.exe`、镜像 SHA-256 在清单内；仍不合规则拒绝 |
| 任意 PID 定向操作 | 协议**没有**"按 PID 裁剪"命令；`watch` 只用于**排除**游戏进程，不改变裁剪集合 |
| 提权 | 守护以普通用户权限运行，无 `SeDebugPrivilege`、无服务控制、无计划任务；不写 HKLM |
| 网络/外部输入 | 零网络代码（静态审计 + 依赖仅 `windows-sys`）；只接受本机管道、固定 4 条命令、单行 ≤ 4 KiB |
| 自启劫持 | `Run` 值指向安装目录内文件；启动时校验一致性并自愈；用户可一键卸载 |

> 同用户恶意进程可以调用 `trim`（后果只是让同用户进程换页变慢），**没有提权收益**；此模型下"同用户威胁不设防"，在文档与审查记录中明示。防的是"跨用户/网络/持久化滥用"。

### 2.6 依赖豁免：Rust 守护是 `00 §4.5` 的唯一例外

`00 §4.5`（L294）要求"不引入 HMCL 没有的第三方依赖"，并把 Rust 守护列为唯一例外。理由：

- 守护必须在**无 JVM** 时工作（启动器已退出），不能用 Java；
- C#/Node 需要运行时；Go 产物 2–4 MiB 且启动 >100 ms；Rust + `windows-sys` 产出约 300–600 KiB、静态 CRT、冷启动 <50 ms；
- 依赖仅 `windows-sys`（MIT/Apache-2.0，与 GPLv3 兼容），**不引入 serde**（协议是固定小 JSON，手写解析/序列化，见 §5.4）；
- 源码随仓库发布，满足 GPLv3 对应源码义务；许可证与 SBOM 在 [步骤 18](18-内存优化P2-发布准备.md) 登记。

---

## 3. 上游事实（v3.16.4，已核对）

| 事实 | 位置 |
|---|---|
| JNA 已在核心依赖中（`api(libs.jna)`，5.18.1），可直接用 | `HMCLCore/build.gradle.kts:27`、`gradle/libs.versions.toml:17/54` |
| 平台判定：`OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS` | `HMCLCore/src/main/java/org/jackhuang/hmcl/util/platform/OperatingSystem.java:42/96` |
| 物理内存：`SystemInfo.getPhysicalMemoryStatus()`；`record PhysicalMemoryStatus(long total, long available)` | `HMCLCore/src/main/java/org/jackhuang/hmcl/util/platform/SystemInfo.java:93`；`.../platform/hardware/PhysicalMemoryStatus.java:20` |
| 上游**没有**工作集裁剪实现（grep `EmptyWorkingSet`/`SetProcessWorkingSetSize` 无结果）→ 全部新增，0 侵入 | 仓库全局 |
| 内存状态自动刷新可复用：`MemoryStatusBar.memoryStatusProperty()`（`UpdateMemoryStatus` 线程，属性多实例共享） | `HMCL/src/main/java/org/jackhuang/hmcl/ui/MemoryStatusBar.java:46/53、173-191` |
| 数据根：`Metadata.HMCL_LOCAL_HOME`（`hmcl.dir`，`<启动器目录>/.lmcl`，不可写时降级 `%APPDATA%/LMCL/local`） | `HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java:66-90`；[步骤 06](06-数据目录与用户迁移.md) §3.1 |
| 启动器目录探测（jar/exe 两种场景）已有约定 | [步骤 06](06-数据目录与用户迁移.md) §4.2 第 1 条 |
| 启动前钩子与事件（16 已用） | [步骤 08](08-启动保障-启动前体检.md) §3.1（`CommunityLaunchHooks.onLaunchRequested`） |
| 实例最大堆（触发阈值来源）：`GameSettings.maxMemoryProperty` | `HMCL/src/main/java/org/jackhuang/hmcl/setting/GameSettings.java:476`（`GameSettings.Instance`） |
| 启动器侧进程枚举可用 JDK `ProcessHandle.allProcesses()`（需 Java 9+；基线 17） | JDK API |
| 上游产物是 stub + jar（`LMCL.exe` 启动 `java`）→ 守护的"祖先进程"校验有稳定锚点 | [步骤 20](20-HMCLauncher启动壳重建.md) |

---

## 4. 架构

```
┌──────────────────────────┐        named pipe        ┌────────────────────────────┐
│ LMCL 主程序（Java）      │  \\.\pipe\lmcl-memopt  │ lmcl-memopt-guardian.exe   │
│  MemOptFacade            │  JSON lines, 2s timeout  │  (Rust, 静态 CRT, 无窗口)   │
│  ├─ MemOptClient (JNA)   │ ───────────────────────► │  ├─ pipe server (ACL=SID)  │
│  ├─ InlineTrimmer (JNA)  │ ◄─────────────────────── │  ├─ trim engine (Toolhelp) │
│  └─ 卡片/对话框 (14)      │   ok/trimmed/skipped     │  ├─ watch 调度 + 限流       │
└──────────────────────────┘                          │  └─ 日志/单实例 mutex       │
                                                      └────────────────────────────┘
     ▲ 登录自启：HKCU\Software\Microsoft\Windows\CurrentVersion\Run
       "LMCL-MemOpt" = "\"<启动器目录>\memopt\lmcl-memopt-guardian.exe\" --autostart"
```

### 4.1 文件与目录

| 路径 | 内容 | 生命周期 |
|---|---|---|
| `<启动器目录>/memopt/lmcl-memopt-guardian.exe` | 守护二进制（随发行包提供，见 18） | 卸载时删除；被占用则下次启动清理 |
| `<Metadata.HMCL_LOCAL_HOME>/community/memopt/guardian.json` | 客户端清单/令牌/安装根（见 §4.2） | 启用时写；卸载时删 |
| `<Metadata.HMCL_LOCAL_HOME>/community/memopt/guardian.log` | 守护日志（1 MiB × 2 轮转，UTF-8） | 滚动 |
| `community-settings.json` → `memOpt` 段 | `enabled/intervalSeconds/minAvailableReserveMb/installed/installedAt/installedPath/runKeyName` | 06/08 定义的文件，本步追加字段 |

### 4.2 `guardian.json`（launcher 写、guardian 只读）

```json
{
  "version": 1,
  "token": "9f2c…（32 hex，启用时随机生成）",
  "installRoot": "C:/Games/LMCL",
  "clients": [
    {"path": "C:/Games/LMCL/LMCL.exe", "sha256": "…"},
    {"path": "C:/Games/LMCL/jre-17.0.12/bin/java.exe", "sha256": "…"}
  ],
  "updatedAt": 1761057840000
}
```

- 守护启动时读取一次；文件缺失/损坏 → 记日志后以"仅 ACL 校验"降级（仍可用，日志 WARNING）；
- 清单由启动器在**启用时**与**每次启动校验失败时**重写（哈希按文件 size+mtime 缓存，避免每次启动算 30 MB 的 JRE）；
- `installRoot` 用于祖先校验：客户端镜像必须在其下。

### 4.3 类与职责（Java 侧，包 `org.jackhuang.hmcl.community.memopt`）

| 类 | 职责 |
|---|---|
| `MemOptSettings` | 读写 `community-settings.json` 的 `memOpt` 段（字段缺失用默认值，损坏不影响启动） |
| `MemOptFacade` | 门面：`isSupported()`、`isEnabled()`、`install()/uninstall()`、`beforeLaunch(versionId)`、`trimNow()`、`status()`、`startWatch(pid,createTime)/stopWatch(pid)` |
| `MemOptInstaller` | 复制二进制、写/删 HKCU `Run`（`reg.exe` 隐藏子进程，检查退出码）、写/删 `guardian.json`、停止正在运行的守护 |
| `MemOptClient` | JNA 命名管道客户端：`CreateFileW/WriteFile/ReadFile/WaitNamedPipeW`；2 s 超时、单行 JSON |
| `InlineTrimmer` | JNA 内联兜底：`ProcessHandle.allProcesses()` 枚举 → `OpenProcess/EmptyWorkingSet`；与守护同一套跳过规则 |
| `MemOptProtocol` | 协议编解码（手写最小 JSON，字段缺失容错） |
| `ui/MemOptDialog` | 开关 + 状态 + 立即整理 + 卸载 + 首次启用说明；由 14 的 memory 卡片打开 |

上游侵入 0：卡片入口来自 14，`beforeLaunch` 用 08 的钩子回调，不改 `LauncherHelper`。

---

## 5. 实现

### 5.1 启用流程（用户显式操作，默认关闭）

```
用户在与"内存优化"对话框中勾选"启用 Windows 内存优化（L2）"
 1. 版本/平台检查：非 Windows 或 JRE 版本不符 → 按钮禁用并说明
 2. 展示说明（安装位置、大小、无管理员权限、登录自启、随时可卸载）→ 确认
 3. 校验二进制存在且 SHA-256 与发行清单一致（清单随包，防被替换）
 4. 复制到 <启动器目录>/memopt/（目录可写？不可写 → 告知"仅内联模式"，不启用守护）
 5. 生成 token → 写 guardian.json（含 LMCL.exe/java.exe 的哈希清单）
 6. reg add HKCU\...\Run /v LMCL-MemOpt /t REG_SZ /d "\"<exe>\" --autostart"
    └─ 失败（组策略/权限）→ 回滚步骤 4/5，提示"系统策略禁止开机启动，仅内联模式"
 7. 启动守护（无参，后台）→ ping 成功 → 状态置"运行中"
 8. 写 memOpt.enabled=true, installed=true, installedPath, installedAt
```

- 全部操作幂等；重复启用走"校验 + 修正"；
- 卸载：`stop` → 删除 `Run` → 删 `guardian.json`/日志 → 删 exe（占用则登记 `pendingDelete`，下次启动删）→ 置 `enabled=false, installed=false`；
- 关闭开关但保留文件（"暂停使用"）与"完全卸载"分开，UI 两个按钮。

### 5.2 触发与调用时序

```java
// 启动前（08 onLaunchRequested 回调内，异步，最多等 1.5 s，失败不影响启动）
if (settings.enabled) {
    long heap = instanceMaxHeapBytes(versionId);
    if (available() < heap + reserve()) {
        if (!client.trim("pre-launch").await(1500)) inline.trim(); // 守护不可用 → 内联
    }
}
// 游戏进程创建后（08 onProcessCreated）
if (settings.enabled && client.ping()) client.watch(pid, processCreateTime(), intervalSeconds, heap);
// 进程退出（08 onProcessExit）
client.unwatch(pid);
```

- 主程序退出（LauncherVisibility.CLOSE）时若 watch 仍在：`watch` 状态持久化到 `guardian.json` 同目录的 `watch.json`，守护每 30 s 复核 `pid + createTime + imagePath` 是否一致，不一致即清除（防 PID 复用）；
- 游戏退出后守护停止裁剪，不做全机周期整理。

### 5.3 守护裁剪引擎（Rust）

```
trim_all(excluding: set<pid>, light: bool):
 1. 单飞 mutex；距上次 < 5 min → 返回 throttled
 2. 取自身 SID；打开当前进程 Token 对照
 3. Toolhelp32Snapshot(TH32CS_SNAPPROCESS) 枚举
 4. 对每个 PID（跳过 0/4/自身/excluding）:
      OpenProcess(QUERY_LIMITED_INFORMATION | SET_QUOTA)
      失败 → skipped++
      QueryFullProcessImageNameW → 空 → skipped++
      OpenProcessToken + GetTokenInformation(TokenUser)  → SID != 自身 → skipped++
      TokenIntegrityLevel >= High → skipped++
      GetProcessMemoryInfo(WorkingSetSize) 记 before
      EmptyWorkingSet(h) → before/after 差计入 freed
      累计耗时 > 10 s → 中断，返回 partial=true
 5. 返回 {trimmed, skipped, freedBytes, elapsedMs, partial}
```

- 日志每次一行：时间、触发原因、进程总数、裁剪数、跳过数、释放量、耗时；
- `light=true`（登录后整理）：跳过有可见窗口的前台进程（`GetForegroundWindow` 所属 PID 及同进程组）——先做最小实现，后续可细化。

### 5.4 管道协议 v1（UTF-8 单行 JSON，≤ 4 KiB，2 s 超时）

| op | 请求 | 响应 |
|---|---|---|
| `ping` | `{"v":1,"op":"ping","token":"…"}` | `{"ok":true,"version":"1.0.0","watching":0}` |
| `trim` | `{"v":1,"op":"trim","token":"…","reason":"pre-launch"|"manual"|"login"}` | `{"ok":true,"trimmed":87,"skipped":3,"freedBytes":123456789,"elapsedMs":421,"partial":false}` |
| `watch` | `{"v":1,"op":"watch","token":"…","pid":1234,"createTime":1729…,"imagePath":"…/java.exe","intervalSeconds":900,"heapBytes":4294967296}` | `{"ok":true}` |
| `unwatch` | `{"v":1,"op":"unwatch","token":"…","pid":1234}` | `{"ok":true}` |
| `stop` | `{"v":1,"op":"stop","token":"…"}` | `{"ok":true}` 后退出 |

- 校验顺序：ACL（内核已完成）→ token 常量时间比较 → 客户端镜像路径与哈希（§5.5）→ 参数范围（`intervalSeconds` 60–86400、`heapBytes` > 0）→ 执行；
- 错误码：`bad_request`、`bad_token`、`too_many_watch`（>1）、`throttled`、`internal`；启动器只记日志，不弹错（保证零阻断）；
- 无 `ping` 响应 → 视为不可用；每次主程序启动最多尝试启动守护 1 次（若 `enabled` 且未运行）。

### 5.5 调用方校验（解决旧稿 `WinVerifyTrust(java.exe)` 的死结）

旧稿要求对"调用方 exe"做 Authenticode 校验，而真正发请求的是 `jre-*/bin/java.exe`（JRE 厂商签名，不是我们签名的产物）→ 要么恒失败，要么放行任意有效签名的 `java.exe`。改为**信任根 + 路径 + 哈希**：

1. 客户端 PID → `QueryFullProcessImageNameW` 取镜像路径，规范化（大小写不敏感、展开短路径到长路径）；
2. 路径必须在 `guardian.json.installRoot` 之下；
3. 在 `clients` 清单中按路径找到 SHA-256，与文件实际哈希（size+mtime 缓存）比较；
4. 沿父进程链向上 ≤ 3 层，必须出现镜像路径 == `installRoot/LMCL.exe` 的进程（`20` 的 stub）；
5. 任一不满足 → 拒绝（`bad_client`），启动器收到后把状态置"需修复"，UI 提供"重新安装守护"（重写清单）并回退内联。

若用户用系统 JVM（不在安装目录内）→ 第 2 条不满足 → 守护不可用；这是**有意为之**（否则等于允许任意 `java.exe` 调用）。

### 5.6 降级矩阵（任何一项失败都不得阻断启动，`00 §5.1`）

| 场景 | 行为 |
|---|---|
| 非 Windows / 非 x86/x64 | L2 不可用，UI 隐藏入口，L0+ 正常 |
| 守护未安装（用户未启用） | 不裁剪（尊重默认关闭）；`beforeLaunch` 直接返回 |
| 已启用但 exe 被杀软删除/隔离 | `ping` 失败 → 内联兜底 → 状态"异常"；提供"重新安装" |
| 管道超时 / 守护崩溃 | 记日志（每会话 1 次）→ 内联兜底 → 不重试 |
| 组策略禁止 HKCU Run | 启用失败 → 仅内联模式（每次启动仍可裁剪一次） |
| 安装目录只读（Program Files 绿色包） | 守护无法放置 → 仅内联模式，UI 说明 |
| 客户端哈希不符（启动器刚更新） | 拒绝 + UI"守护需更新"→ 用户点"修复"重写清单 |
| 便携目录移动 | 启动时发现 `Run` 指向旧路径 → 重写；文件不存在 → 状态"异常" |

---

## 6. 测试

| # | 用例 | 期望 |
|---|---|---|
| 1 | 默认状态（新用户） | UI 显示"未启用"，无 `Run` 项、无守护进程、无文件 |
| 2 | 启用 → 注销重登 | 守护自动启动，空闲 CPU 0、常驻 ≤ 8 MiB、无窗口 |
| 3 | 启动游戏（可用内存充足） | 不触发裁剪（日志"skip: enough memory"），启动时间无回退 |
| 4 | 构造低可用内存（占用工具） | 启动前裁剪执行，返回 `trimmed>0`，日志有释放量 |
| 5 | 提权进程（管理员运行的记事本） | 计入 `skipped`，不崩溃 |
| 6 | 游戏运行中 | 每 15 分钟裁剪后台；**被监视游戏 PID 不在裁剪集合**（日志验证） |
| 7 | 游戏退出 | watch 停止；不再周期裁剪 |
| 8 | LauncherVisibility=CLOSE，启动器退出后游戏运行 | 周期裁剪仍生效；游戏退出后停止 |
| 9 | 结束守护进程 | 下次 `beforeLaunch` ping 失败 → 内联裁剪成功；启动不失败 |
| 10 | 卸载 | `Run` 删除、进程退出、文件删除；重登无残留 |
| 11 | 移动便携目录后启动 | `Run` 自动修正为新路径；守护可用 |
| 12 | 程序目录只读 | 启用被拒/仅内联；游戏启动正常 |
| 13 | 伪造客户端（自写脚本连管道） | 被拒（路径/哈希/祖先校验）；日志有 `bad_client` |
| 14 | `community-settings.json` 损坏 | 守护按默认（关闭）处理，启动正常 |

---

## 7. 工期拆分（2–3 周，10–15 人日）

| 天 | 内容 | 产出 |
|---|---|---|
| 1–2 | Rust 骨架：单实例、日志、pipe server（ACL+SID 校验）、`ping/stop` | 可安装、可连通的空守护 |
| 3–4 | 裁剪引擎：Toolhelp 枚举、Token SID/完整性判断、`EmptyWorkingSet`、统计与限流 | `trim` 命令可用 |
| 5 | `watch` 调度 + `watch.json` 持久化 + PID 复用复核 | 游戏期间周期裁剪 |
| 6–7 | Java 侧：`MemOptClient`（JNA pipe）、`MemOptProtocol`、`MemOptFacade`、`InlineTrimmer` | 主程序可调用、可兜底 |
| 8–9 | `MemOptInstaller`：复制、HKCU Run、清单/哈希、卸载/自愈；异常路径 | 启用/禁用/修复闭环 |
| 10–11 | UI：对话框（开关/状态/立即整理/卸载/说明）、与 14 memory 卡片联动、i18n | 用户可用 |
| 12 | 与 08 钩子集成：beforeLaunch/watch/unwatch；退出清理 | 生命周期打通 |
| 13 | 安全加固：校验顺序、错误码、日志脱敏、静态审计（无网络/无任意 PID） | 通过 §2.5 检查表 |
| 14–15 | 测试 14 例 + 300 进程压力 + 低配机验证 + 文档 | 可发布 |

> Rust 工具链：CI 增加 `cargo build --release --target x86_64-pc-windows-msvc`（i686 同）；产物放 `<启动器目录>/memopt/`；构建脚本与校验和记录在 [步骤 18](18-内存优化P2-发布准备.md)。

---

## 8. 风险

| 风险 | 等级 | 应对 |
|---|---|---|
| 杀软误报/隔离（注入式系统 API 行为） | **高** | 18 对守护与 stub 同证书签名；规模最小化（无网络、无持久化钩子）；文档说明；提供"仅内联"降级 |
| 同用户进程被无关脚本调用（DoS 级影响） | 中 | 文档明示威胁模型；限流；只影响工作集不杀进程；不开任意 PID 接口 |
| 裁剪造成游戏/系统卡顿 | 中 | 运行中排除被监视 PID；最小间隔 5 分钟；可用内存充足时不触发 |
| `EmptyWorkingSet` 在新 Windows 版本行为变化 | 中 | 用受支持 API 组合；失败计入 skipped；每年随上游核对 |
| HKCU Run 被清理软件删除 | 中 | 每次启动自愈；状态可见；提供"修复" |
| 便携目录移动导致自启失效 | 低 | 启动校验并重写；状态提示 |
| 与 18 的签名/清单流程脱节 | 中 | 17 定义"发行清单"格式（exe 相对路径+SHA-256），18 生成；收尾一致性检查对齐 |
| 旧稿"服务/提权"表述残留 | 中 | 本文件为唯一准绳；README/00/12/16/18 已改链接名；收尾再全文 grep "服务" |

---

## 9. 回退

1. UI 关闭"启用"→ 停止守护 + 删 `Run`（保留二进制与配置，状态"已停用"）；
2. "完全卸载" → 删二进制、配置、日志、`Run`；
3. 删 `memopt/` 目录 + `community-settings.json` 的 `memOpt` 段 → 功能视为未安装，启动器自动回退内联；
4. 若守护进程残留且无法停止 → 记录 PID，下次启动 `taskkill /PID`（仅限自身清单内路径，避免误杀）。

---

## 10. 与旧稿的差异（审查记录，供追溯）

| 旧稿 | 本版 |
|---|---|
| 标题"Windows 服务化"，LocalSystem 服务、`sc create/delete`、`SeDebugPrivilege`、命名管道 + 计划任务变体 | HKCU 登录守护，普通用户权限，无服务/无提权；文件已改名 `17-内存优化P1-Windows守护进程.md` |
| 三重校验含 `WinVerifyTrust(client_exe)`（实际调用方是 JRE 的 `java.exe`） | 安装根 + 客户端哈希清单 + 祖先进程为 `LMCL.exe`（§5.5） |
| 裁剪所有进程（含系统） | 仅同用户非提权进程；游戏运行中排除被监视 PID |
| UI 在设置页 | 由 14 的 memory 卡片打开对话框（0 上游侵入） |
| 未定义与 18 的衔接 | §8、§10 定义发行清单、签名、SBOM 交接；18 的"服务与主程序版本双校验"文案在收尾一致性检查中改为"守护协议版本/清单校验" |

---

## 11. 关联

- L0+ 基线与 `MemoryMetrics`：[步骤 12](12-内存优化P0-跨平台基座.md)
- 启动钩子与事件：[步骤 08](08-启动保障-启动前体检.md)；首页卡片与对话框容器：[步骤 14](14-首页组件化B-P0组件与接入.md)
- 数据根与启动器目录探测：[步骤 06](06-数据目录与用户迁移.md)；发行包/签名/SBOM：[步骤 18](18-内存优化P2-发布准备.md)
- **回填状态（v1.1 已收口）**：18 已无"服务与主程序版本双校验"表述，改为"守护协议 v1 + 客户端清单 + 主程序版本"校验（见 [18](18-内存优化P2-发布准备.md) §3.3/§9）；README/00 的 17 行说明与 HKCU 定案一致。
