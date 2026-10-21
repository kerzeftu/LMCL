# PCL 内存优化功能：原理解析与 HMCL 移植设计

---

## 一、PCL 这个功能到底是什么

### 1.1 用户侧描述（官方语言文件原文）

> 内存优化能将目前物理内存占用降低约 1/3，不仅限于 Minecraft！
> 如果使用机械硬盘，这还可能会导致一小段时间的严重卡顿。
> 使用 `--memory` 参数启动 PCL 可以静默执行内存优化。

入口有三个：
1. 设置 → 启动设置 → 「启动游戏前进行内存优化」（全局开关）
2. 实例设置里独立开关（覆盖全局）
3. 更多 → 百宝箱 → 「内存优化」（手动执行，不启动游戏）

### 1.2 真实机制

核心方法是 `PageOtherTest.MemoryOptimizeInternal`，本质是对**系统内所有进程**调用 Windows 的：

```
EmptyWorkingSet(hProcess)
// 等价于
SetProcessWorkingSetSize(hProcess, (SIZE_T)-1, (SIZE_T)-1)
```

流程大概是：

```
1. 提权（启用令牌中被禁用的权限）
2. EnumProcesses 枚举所有进程 PID
3. 逐个 OpenProcess(PROCESS_SET_QUOTA | PROCESS_QUERY_INFORMATION)
4. 调用 EmptyWorkingSet / SetProcessWorkingSetSize(-1, -1)
5. 关闭句柄
```

`PROCESS_SET_QUOTA` = `0x0100`。

### 1.3 它到底有没有真的"释放内存"？——关键澄清

**没有。这是理解这个功能的关键。**

`EmptyWorkingSet` 做的事是：扫描进程工作集，把"可换出页面"（已写回磁盘的 EXE/DLL 代码页、未锁定的 Mapped File 视图页、Clean 数据页）**从工作集中移除，移入 Standby 或 Modified List**。

这些页面**仍然在物理内存里**，只是不再计入该进程的 Working Set，于是任务管理器显示的"内存使用量"下降。

明确不受影响的内存类型：

| 内存类型 | 是否受影响 | 原因 |
|---|---|---|
| 共享 DLL 代码页 | ❌ | 工作集归属所有引用进程，单进程无法独占释放 |
| `VirtualLock()` 锁定内存 | ❌ | 标记为 Non-pageable |
| .NET 托管堆 | ❌ 几乎无效 | 工作集收缩不触发 GC，不释放托管对象 |
| **Private Bytes（私有提交内存）** | ❌ 不降低 | 只影响物理驻留状态，虚拟提交量不变 |
| 内核态不可分页池 | ❌ | Ring 0，用户态 API 无权干预 |

Win10/11 开启内存压缩后，被 trim 的页面多半进入**压缩存储**（MemCompression 进程）而非 page file。

### 1.4 那为什么实测有效？

既然没真释放，为什么实测（如 16G 机器上游戏占用 11G→13G，优化后降到 6G）确实消除了掉帧？

三个真实起作用的机制：

1. **触发 Modified Page Writer 把脏页刷到 pagefile** —— 这是唯一"真"的释放。长期运行后大量脏页堆积在 Modified List，刷出去后系统的 **可用内存计数** 真实上升
2. **提升"可用内存"计数** —— 后续大块内存申请（JVM 用 `-Xms` 一次性提交大堆）更容易成功，避免启动期的内存压力
3. **Standby 列表扩容** —— 系统有了更多可立即调配的干净页，减少游戏运行中的硬缺页

**真正受益的场景**：长时间游戏后内存占用持续攀升（脏页堆积）。这正是实测视频里的情形——打开游戏 11G，半小时后 13G，掉帧明显。

**不受益的场景**：刚开机、系统本来就干净。此时 trim 只是把页面挪到 Standby，再访问就 soft fault 回来，纯开销。

### 1.5 代价

- **机械硬盘严重卡顿**：页面被换出后要重新从磁盘读入。这是 PCL 自己在 UI 上写明的
- TLB 与 CPU 缓存失效，后续 page fault 率陡增
- 频繁调用会与内核 `MmTrimAllSystemPagableMemory()` 路径争用，**拖慢全局内存回收**
- NUMA 架构下引发跨节点内存迁移

### 1.6 安全风险（移植时必须重视）

跨进程 `OpenProcess` 带 `PROCESS_SET_QUOTA` 是一个**被安全社区标记的行为**：

- Sysmon Event ID 10 可直接捕获
- MITRE ATT&CK 归类为 **T1562.001（Impair Defenses）**——攻击者常用它把杀软的扫描缓冲区、YARA 规则表挤出工作集，降低实时扫描效率

也就是说：**一个启动器对所有进程做工作集裁剪，行为特征上与攻击工具相似。** 这会带来杀软误报风险，对 HMCL 这种用户基数大、注重安全口碑的项目是需要严肃权衡的。

---

## 二、移植到 HMCL：可行性评估

### 2.1 最大的障碍是跨平台，不是技术难度

HMCL 支持 Windows / Linux / macOS / FreeBSD，以及 x86 / ARM / RISC-V / LoongArch。而 `EmptyWorkingSet` 是 **Windows 独有**。

| 平台 | 等价能力 | 说明 |
|---|---|---|
| Windows | ✅ `EmptyWorkingSet` / `SetProcessWorkingSetSize` | 需 `PROCESS_SET_QUOTA`，跨进程需提权 |
| Linux | ⚠️ 极其有限 | `malloc_trim(0)` 只作用于自身；`posix_madvise(MADV_DONTNEED)` 只作用自身；`drop_caches` 需 root 且清的是 page cache 不是工作集。**无法对其他进程操作** |
| macOS | ❌ 基本没有 | 无公开等价 API |
| FreeBSD | ❌ 基本没有 | 同上 |

**结论：这个功能在 HMCL 上只能是"Windows 增强特性"，必须做平台能力探测 + 优雅降级。**

这其实也符合 HMCL 的现状——它已有 `ui/WindowsNativeUtils.java`（约 9KB）作为 Windows 原生能力的封装层，说明这种"Windows 专属增强"在架构上是有先例的。

### 2.2 技术依赖已经具备

好消息：HMCL 不需要引入任何新依赖。

| 能力 | 现状 |
|---|---|
| JNA（native access） | ✅ 已在 `libs.versions.toml` 核心依赖中 |
| Windows 原生封装 | ✅ 已有 `ui/WindowsNativeUtils.java` |
| 启动流程钩子 | ✅ `LauncherHelper` 有明确的 Stage 划分（Java 选择 → 依赖校验 → 启动） |
| 内存状态显示 | ✅ 已有 `MemoryStatusBar`，能读物理内存总量/已用 |
| 百宝箱式手动入口 | 需新增，但可挂在首页组件或设置页 |

### 2.3 合规前提：不能复制代码

**PCL 的许可证是自定义的「PCL Limited Distribution License」，不是 GPL。** 而你的 HMCL 社区版是 GPLv3。

因此：**只能根据原理重新实现，不得复制 PCL 的 VB.NET 源码。**

（顺带印证前几轮的判断——美西螈正是因为内置了 PCL 的 `WikiEntries.txt` 快照，才在许可证上背了额外负担。）

---

## 三、移植方案设计

### 3.1 设计原则

针对你说的"内存不足时优先运行，也可手动运行"，我建议做成**三层**，而不是照搬 PCL 的无条件全局 trim：

```
L0 自身释放     全平台  —— HMCL 自己收缩堆，零风险，始终执行
L1 温和清理     Windows —— 只 trim 白名单外的普通用户进程，排除系统关键进程
L2 深度清理     Windows —— 全量 trim（等同 PCL 行为），需用户显式确认
```

### 3.2 触发逻辑：条件触发，而非无条件

这是与 PCL 最重要的设计差异，也是"内存不足时优先运行"的正确实现：

```java
public boolean shouldOptimize() {
    if (!isSupported()) return false;              // 非 Windows 直接跳过
    MemoryStatus s = MemoryStatus.query();
    long needMb = versionSetting.getMaxMemory();    // 本次启动要分配的内存
    long availMb = s.getAvailable();

    // 触发条件：可用内存不足以舒适容纳本次分配
    // （留 1.25 倍余量，因为 JVM 除堆外还有 metaspace、线程栈、直接内存）
    return availMb < needMb * 1.25;
}
```

**只在真的不够时才做**，避免"系统本来就干净还硬 trim 一遍"的纯开销。

### 3.3 进程排除清单（必须有）

不做排除会引发严重问题（系统进程被 trim 后 DWM、explorer 卡顿）。必须硬编码排除：

```
smss.exe, csrss.exe, lsass.exe, services.exe, svchost.exe,
winlogon.exe, wininit.exe, dwm.exe, explorer.exe, fontdrvhost.exe,
ctfmon.exe, MsMpEng.exe（杀软，trim 它会触发告警）,
以及自身进程 java.exe / javaw.exe / HMCL*.exe
```

建议策略：**默认只 trim 当前用户会话下的进程**（用 `ProcessIdToSessionId` 过滤），并且**默认跳过杀软与系统服务**。

### 3.4 平台降级

| 平台 | 行为 |
|---|---|
| Windows（有权限） | L0 + L1，条件触发 |
| Windows（无权限/被拦截） | 仅 L0，UI 提示"需要更高权限" |
| Linux | 仅 L0（HMCL 自身 `System.gc()` + JVM 堆归还） |
| macOS / FreeBSD | 仅 L0 |

**绝不因为不支持就报错或阻断启动**——这是硬约束。

### 3.5 手动入口

参照你说的"也可手动运行"，两个入口：

1. **首页组件**：`MemoryOptimizeWidget`（接上前面设计的组件化首页）—— 显示当前内存状态 + "一键优化"按钮
2. **设置页**：放在 Java 管理或启动设置里，作为开关（"启动前智能优化"）

手动执行时不判断内存是否充足，但**仍走 L0+L1**，不做 L2。

### 3.6 命令行静默执行（可选）

PCL 有 `--memory` 参数。HMCL 已有完善的命令行入口（`EntryPoint.java` 处理参数），可加一个 `--optimize-memory`，执行后退出。

---

## 三·补、平台分派策略：检测"能力"而非"系统"

### 问题：不能只写 OS 到配置

设想的做法是"首次启动判断系统，写进配置文件，Windows 就执行 L2"。方向对，但**只存 OS 不够**——同一 OS 下能力差异很大：

| 变量 | 影响 |
|---|---|
| 是否管理员 / UAC 是否放行 | Windows 上决定能否 `OpenProcess` 大部分进程 |
| Apple Silicon vs Intel | macOS 上 `purge` 效果完全不同（见下） |
| Flatpak / Snap 沙箱 | Linux 上连 `/proc/...` 都读不全 |
| 杀软是否拦截 | Windows 上部分进程 `OpenProcess` 直接失败 |
| macOS 版本 | `purge` 行为随版本变化 |

结论：**配置里应缓存"实测到的能力等级"，而不是"操作系统"**。首次启动时做一次能力探测，失败时自动降级，并把结果写入配置供后续复用（避免每次启动重复探测开销）。

HMCL 已有 `OperatingSystem` 枚举（`OperatingSystem.CURRENT_OS`），系统判断是现成的，不需要额外基建。

### 建议的配置结构

```json
{
  "version": 1,
  "memoryOptimize": {
    "enabled": true,
    "detectedCapability": "L2",
    "detectedAt": "2026-10-20T...",
    "userOverride": null,
    "lastResult": "success"
  }
}
```

- `detectedCapability`：首次探测结果（L0 / L1 / L2）
- `userOverride`：用户在设置里手动改的档位，优先级高于探测值
- 探测失败不阻断启动，静默降级到 L0

### Windows → L2 的三个前置门

如果决定 Windows 上对齐 PCL（L2），必须加三道门，否则会变成"开着开关但没权限，用户不知道发生了什么"：

1. **权限门**：启动时检测能否提权；不能则降级 L1 → L0，并在 UI 明示当前档位
2. **知情门**：首次触发 L2 时弹一次说明（"将清理系统内进程的工作集，可能短暂影响其他程序"），提供"不再提示"
3. **回退门**：任何异常（OpenProcess 大面积失败、超时）立即降级，且**绝不阻断游戏启动**

### Linux：诚实结论——不存在等价机制

| 手段 | 作用域 | 能否跨进程 | 评价 |
|---|---|---|---|
| `malloc_trim(0)` | 自身 glibc ptmalloc 堆 | ❌ | 仅 glibc 有效，tcmalloc/jemalloc 不认；best-effort，返回 0 表示什么都没释放 |
| `madvise(MADV_DONTNEED)` | 自身映射区间 | ❌ | 需页对齐；对仍在使用的数据是负面优化 |
| `drop_caches` | 全局 page cache / dentries / inodes | ❌ 不是工作集 | **需 root**；官方文档定位是"基准测试拿干净读数"，明确说明"不是修复任何问题的手段，available 几乎不动，之后机器会变慢"，且"不该放进 cron" |

即：**Linux 上没有"整理其他进程工作集"的接口，也没有必要。** Linux 内核的 `kswapd` 后台回收 + 直接回收 + OOM Killer 已经自动处理，`free` 里看到的 buff/cache 本身就是可回收的。

Linux 上的正确做法 = **L0 + 透明提示**（告诉用户谁在占内存），不做系统级清理。

### macOS：`purge` 存在但代价明确

- `sudo purge`：要求**管理员密码**，作用是 flush 磁盘缓存 + 回收 inactive 内存，模拟"刚开机"的内存状态
- 执行期间系统可能冻结 2–5 秒
- **Apple Silicon 上效果不可靠**：统一内存架构下 CPU/GPU 共享同一内存池，purge 可能"看起来无效，或导致额外的压缩与 swap 活动"；反复 purge 会引发 **swap thrashing**（数据在内存与存储间反复搬移），增加写入量并降低响应性
- 权威建议：purge 应作为**诊断实验**而非日常维护；且"应由内存压力触发，而非定时器触发"

结论：macOS 上可以支持 `purge`，但应**默认关闭**、标注风险，且 Apple Silicon 上建议提示"收益有限"。

### 真正的跨平台答案：JEP 346（推荐重点投入）

与其各平台找系统级 hack，不如用 JVM 原生能力——**G1 周期性归还未使用内存**，全平台一致、零权限要求、且是"真"释放。

JEP 346（JDK 12+）：应用空闲时 G1 周期性触发并发周期或 Full GC，把未使用的堆**真正归还操作系统**。

```
-XX:+UseG1GC
-XX:G1PeriodicGCInterval=30000        # 空闲多久后考虑周期性 GC（毫秒），0 = 关闭
-XX:G1PeriodicGCInvokesConcurrent     # 默认开启：走并发周期（比 Full GC 温和）
-XX:G1PeriodicGCSystemLoadThreshold   # 系统负载低于此值才算"空闲"
-XX:MinHeapFreeRatio=5
-XX:MaxHeapFreeRatio=10               # 堆空闲超过此比例时收缩
-XX:+ShrinkHeapInSteps                # JDK 13+，分步归还
```

各 GC 的归还行为对比：

| GC | 是否自动归还 OS | 时机 |
|---|---|---|
| **G1** | ✅ 有条件 | Full GC，或空闲 region 足够多时（配合 JEP 346 可周期性触发） |
| **ZGC** | ✅ 自动 | 并发回收后主动释放未用 page，无需 Full GC |
| **Shenandoah** | ✅ 自动 | 并发 compact 后释放 region |
| Parallel / Serial / CMS | ❌ | 即使 Full GC 也不归还 |

两个落地用法：

1. **HMCL 自身**：启动器在游戏启动后就是后台进程，用 JEP 346 让它把内存还回去。这直接呼应"启动器不该占着内存不放"，且**全平台有效、零风险**
2. **游戏进程**：给游戏 JVM 加上 `G1PeriodicGCInterval`——这是真正能改善"长时间游玩后内存占用攀升"的手段，且是 MC 官方参数之外的增量

### 修订后的平台分派表（已按决策调整为二档）

| 平台 | 档位 | 实现 | 备注 |
|---|---|---|---|
| Windows | **L2（触发即全量）** | 服务化 helper + `EmptyWorkingSet` | 服务常驻高权限，主程序普通权限；条件触发 |
| Linux | **L0+** | JEP 346 周期性归还 | 无系统级清理；改为展示占用 Top 5 |
| macOS | **L0+** | JEP 346 周期性归还 | `purge` 不作默认；Apple Silicon 上提示收益有限 |
| FreeBSD / 其他 | **L0+** | JEP 346 周期性归还 | 同上 |

**档位定义（最终版，取消 L1）：**

- **L2**：全量 `EmptyWorkingSet`，含系统进程与已提权进程 —— Windows 专属，需服务化
- **L0+**：JEP 346 周期性归还 + 占用透明化 —— 全平台通用基座

取消 L1 的理由（已确认）：既然已经要为 Windows 承担服务化与提权的全部成本，中间档没有存在价值——要么不做，要么做彻底。

---

## 三·补2、提权方案：服务化（已定案）

**决策：取消 L1 中间档，Windows 上触发即 L2 全量，采用服务化架构。**

### 架构总览

```
LMCL.exe（普通权限 asInvoker，永不提权）
   │
   │  ① 条件触发：可用内存 < 需求 × 1.25
   │
   ├─► 命名管道 \\.\pipe\hmcl-memopt-<UserSID>
   │        │
   │        ▼
   │   HMCLMemOptSvc.exe（Windows 服务，LocalSystem 或专用服务账户）
   │        ├─ 校验调用方：进程路径 + 数字签名 + 完整性级别
   │        ├─ 枚举进程 → EmptyWorkingSet
   │        └─ 返回统计（处理数 / 跳过数 / 释放量估算）
   │
   └─► 无论成功与否，继续启动游戏
```

安装流程（**只弹一次 UAC**）：

```
设置里勾选「启用智能内存优化」
   → 弹 UAC（runas 拉起 installer）
   → 创建服务 HMCLMemOptSvc，启动类型 Auto（或 Manual，见下）
   → 之后主程序永远普通权限，不再弹窗
```

### 关键设计决策

#### ① 服务启动类型：Auto 还是 Manual？

这是个容易踩的坑：**Manual 类型下，普通权限进程无法 `StartService`**（需要 SC_MANAGER_CONNECT + 服务启动权限）。

| 类型 | 主程序能否拉起 | 常驻高权限进程 | 建议 |
|---|---|---|---|
| Auto | ✅ 直接连管道 | 是 | **推荐**：简单、可靠 |
| Auto (delayed) | ✅ | 是，但错开开机高峰 | 可用 |
| Manual | ❌ 需额外权限，做不到 | 否 | 不推荐 |

选 **Auto（delayed start）**。服务空闲时几乎不占资源（只维持一个管道监听线程），用 delayed 避免拖慢开机。

#### ② RequiredPrivileges：只声明 SeDebugPrivilege

用 `ChangeServiceConfig2(SERVICE_CONFIG_REQUIRED_PRIVILEGES_INFO)` 声明最小权限集合，让 SCM 裁剪令牌。

实际上做 `EmptyWorkingSet` 跨进程需要 **`SeDebugPrivilege`**——它能打开任意进程句柄。**注意这是等同于管理员的高危权限**，这正是"服务必须做得极小、且严格校验调用方"的原因。

> 提醒：`RequiredPrivileges` 只能裁剪账户已有权限的子集，不能凭空提权。所以服务仍需以具备该权限的账户（如 LocalSystem）运行。

#### ③ 调用方校验（不可省略）

服务一旦被任意进程调用，就等于**给本机任何程序开了一个"以 SYSTEM 身份裁剪所有进程"的后门**。必须做三重校验：

```rust
// 伪代码
let client_pid = GetNamedPipeClientProcessId(pipe);
let client_path = query_process_image_path(client_pid)?;

// 1. 路径校验：必须位于 HMCL 安装目录
ensure!(client_path.starts_with(hmcl_install_dir()))?;

// 2. 签名校验：必须是本项目签发的二进制
ensure!(verify_authenticode(&client_path, EXPECTED_CERT))?;

// 3. 完整性级别：不得低于 Medium（防止被低完整性沙箱进程复用）
ensure!(get_integrity_level(client_pid) >= Medium)?;
```

第 1、2 条是核心。第 3 条可选但建议做。

**若不做签名校验**：任何能写 HMCL 安装目录的程序（或用户自己放的 jar/exe）都能驱动 SYSTEM 级服务——这是真正的提权漏洞。

#### ④ 命令接口必须极窄

服务只接受一个固定操作，**不接受任何参数化的命令、路径或脚本**：

```
请求:  { "op": "trim_all", "nonce": "<random>" }
响应:  { "processed": 148, "skipped": 12, "bytes": 3_758_292_992 }
```

严禁设计成 `{ "op": "run", "cmd": "..." }` 这类通用执行接口——那就等于写了个 SYSTEM 后门。

#### ⑤ 管道 ACL

管道名带用户 SID，安全描述符只授权：
- 当前用户（读/写）
- SYSTEM（完全控制）

不授权其他任何主体。用 `CreateNamedPipe` 时传入自定义 `SECURITY_ATTRIBUTES`。

### 服务用什么写

| 语言 | 优劣 |
|---|---|
| **Rust** | 推荐。静态编译无运行时依赖，二进制几百 KB；`windows` crate 或 `windows-sys` 覆盖所需 API；内存安全，适合高权限组件 |
| Go | 可用。静态编译，但二进制偏大（~2MB+），带 GC 与运行时 |
| C / C++ | 可用但需手动管理内存，高权限组件不推荐 |
| C# | ❌ 依赖 .NET 运行时，且需处理 JIT 与程序集加载 |

**选 Rust。** 服务源码随 HMCL 一起开源（GPLv3 要求，它是衍生作品的一部分）。

### 主程序侧（Java / JNA）

HMCL 已有 JNA，调用命名管道需注意：**Java 的标准文件 API 无法可靠访问 Windows 命名管道**，要用 JNA 调 `CreateFileW`：

```java
HANDLE h = Kernel32.INSTANCE.CreateFile(
    "\\\\.\\pipe\\hmcl-memopt-" + userSid,
    GENERIC_READ | GENERIC_WRITE, 0, null, OPEN_EXISTING, 0, null);
// 超时保护：服务不可用立即跳过
```

要点：
- **全程后台线程**，不阻塞 FX 线程
- **硬超时（建议 3 秒）**：服务未安装、被禁用、被杀软拦截时立即放弃
- 失败静默降级到 L0，UI 上标注"本次未执行系统级优化"

### 安装 / 卸载 / 升级

| 场景 | 处理 |
|---|---|
| 安装 | 设置里开启 → runas 拉 installer → 创建服务（Auto delayed） |
| 卸载 | 设置里关闭 → 可选"同时卸载服务"；HMCL 卸载时提示 |
| 升级 | HMCL 更新后，检测服务版本，不匹配则 runas 重新部署 |
| 服务缺失/被删 | 自动降级，不报错，不阻断 |

**服务必须提供明确的卸载入口**——留一个用户找不到的高权限服务是严重的软件卫生问题。

### 三条硬约束

1. **任何环节失败都不得阻断游戏启动**。服务未安装、被禁用、管道超时、签名校验失败——一律静默跳过
2. **绝不静默绕 UAC**。`__COMPAT_LAYER=RUNASINVOKER`、关 `EnableLUA`、改组策略一律不用
3. **服务必须极小化**。只做进程枚举 + `EmptyWorkingSet`；不操作文件、不联网、不解析外部输入、不接受参数化命令

### 风险再评估（服务化后）

服务化解决了"反复弹 UAC"的体验问题，但把风险形态换了一种：

| 风险 | 服务化前（runas） | 服务化后 | 说明 |
|---|---|---|---|
| UAC 弹窗疲劳 | 高 | **无** | ✅ 解决 |
| 高权限进程常驻 | 无 | **有** | ❌ 新增，需靠极小化 + 签名校验对冲 |
| 杀软误报（T1562.001） | 中 | **高** | 常驻 SYSTEM 服务做跨进程 trim，特征更明显。**建议提前与主流杀软提交白名单** |
| 被本地程序滥用 | 低 | **高** | 必须做签名校验（见 ③） |

**最需要提前准备的是杀软白名单。** 一个 SYSTEM 服务对所有进程调 `EmptyWorkingSet`，行为特征与攻击工具高度重合。建议在首个公开版本发布前，向 Windows Defender、360、火绒等提交误报申诉材料，并准备好解释文档。

### 工作量估算

| 项目 | 工期 |
|---|---|
| Rust 服务主体（进程枚举 + trim + 管道 + 校验） | 3–4 天 |
| installer / uninstaller（服务注册、ACL） | 2–3 天 |
| Java 侧 JNA 管道客户端 + 超时/降级 | 2 天 |
| 设置 UI（开关、状态显示、卸载入口） | 1–2 天 |
| 首页组件 `MemoryOptimizeWidget` | 1 天 |
| 测试（权限、降级、多用户、杀软共存） | 3–4 天 |
| **合计** | **约 2–3 周** |

### 更轻的替代：计划任务变体

如果评估后觉得"常驻 SYSTEM 服务"风险过大，有个**更轻的方案能达到同样的"只弹一次 UAC"效果**：

```
安装时创建一个计划任务：
  - 触发器：手动（无自动触发）
  - 勾选「使用最高权限运行」
主程序需要优化时：schtasks /run /tn "HMCLMemOpt"（普通权限即可触发）
```

差别在于：高权限进程**只在需要时存在几秒，然后退出**，不常驻。攻击面显著更小，且 `schtasks /run` 不需要管理员权限。

代价：`schtasks` 是外部进程调用，起停开销约数百毫秒，且依赖计划任务服务可用。

**建议**：先按真服务实现（性能好、可控），但把 IPC 层抽象出来——这样后续若切换到计划任务变体，主程序侧几乎不用改。

---

## 四、代码实现骨架

### 4.1 平台抽象

```java
public interface MemoryOptimizer {
    boolean isSupported();
    Result optimize();      // 已取消 Scope：Windows 上触发即全量 L2
}

public final class MemoryOptimizers {
    public static MemoryOptimizer get() {
        return switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> new WindowsMemoryOptimizer();   // 走服务 IPC
            case LINUX   -> new LinuxMemoryOptimizer();     // L0+：JEP 346 + 占用透明化
            default      -> new NoopMemoryOptimizer();
        };
    }
}
```

### 4.2 Windows 实现：服务客户端，而非直接调 API

决策变更后，Java 侧**不再直接调 `EmptyWorkingSet`**（主程序是普通权限，调不动系统进程）。改为通过命名管道向服务发请求：

```java
final class WindowsMemoryOptimizer implements MemoryOptimizer {

    @Override
    public Result optimize() {
        // 1. 硬超时 3 秒
        // 2. Kernel32.CreateFile("\\\\.\\pipe\\hmcl-memopt-" + sid, ...)
        // 3. 写 {"op":"trim_all","nonce":...}
        // 4. 读 {"processed":N,"skipped":M,"bytes":B}
        // 5. 任何异常 → 返回 Result.unavailable()，调用方静默继续
    }
}
```

实际的 native 部分（进程枚举 + `EmptyWorkingSet`）在 Rust 服务里：

```rust
for pid in enum_processes()? {
    let h = open_process(PROCESS_SET_QUOTA | PROCESS_QUERY_INFORMATION, pid);
    // 失败静默跳过（PPL 保护进程、已退出进程）
    if let Ok(h) = h {
        empty_working_set(h);
        close_handle(h);
    }
}
```

要点：

- `PROCESS_SET_QUOTA = 0x0100`
- **Java 侧**全程后台线程 + 硬超时，绝不阻塞 FX 线程与启动流程
- **服务侧** `OpenProcess` 失败静默跳过，不中断整体流程
- 服务侧也加总耗时上限（建议 10 秒），避免极端环境卡死

### 4.3 挂载点（侵入点）

| # | 位置 | 改动 |
|---|---|---|
| 1 | `LauncherHelper` 的 Java 选择之后、依赖校验之前 | 条件触发优化（约 3–5 行调用） |
| 2 | 设置项 | 新增开关（复用已有设置框架） |
| 3 | 首页组件 | 新增 `MemoryOptimizeWidget`（纯新增，零侵入） |

第 1 点是唯一侵入点，但 `LauncherHelper` 的 Stage 划分很清晰，插入一个可选步骤风险可控。

**更保守的做法**：不挂 `LauncherHelper`，而是做成首页组件 + 设置页开关，只在用户明确开启时才介入启动流程。

---

## 五、风险清单与应对（服务化后更新）

| 风险 | 等级 | 应对 |
|---|---|---|
| **服务被本地程序滥用（SYSTEM 后门）** | **极高** | 三重校验：进程路径 + Authenticode 签名 + 完整性级别；命令接口固定为单一 `trim_all`，不接受参数 |
| **杀软误报（T1562.001）** | **高** | 常驻 SYSTEM 服务做跨进程 trim 特征明显。**发布前主动向 Defender / 360 / 火绒提交白名单申诉**；准备解释文档 |
| 高权限进程常驻 | **高** | 服务极小化（仅枚举 + trim）；`RequiredPrivileges` 裁剪令牌；提供明确卸载入口 |
| PPL 进程 trim 失败 | 中 | 服务侧静默跳过，统计到 `skipped` 字段 |
| 机械硬盘严重卡顿 | 中 | 检测磁盘类型，HDD 上默认关闭并提示 |
| 跨平台不一致的预期落差 | 中 | UI 明确标注"系统级优化仅 Windows；其他平台提供内存占用分析" |
| 服务版本与 HMCL 版本不匹配 | 中 | 启动时校验版本，不匹配则提示重新部署 |
| 遗留孤儿服务 | 中 | 卸载流程必须包含服务删除；设置页提供"卸载服务"按钮 |
| 上游 merge 冲突 | 低 | 侵入点仅 `LauncherHelper` 1 处，新增式为主 |
| 许可证风险（PCL 非 GPL） | **高** | **只参考原理，重新实现，不复制任何 PCL 代码**；Rust 服务源码随项目开源 |

---

## 六、执行顺序（已按服务化决策调整）

| 阶段 | 内容 | 工期 |
|---|---|---|
| **P0 — 全平台基座** | JEP 346 周期性归还 + 内存占用 Top 5 透明化 + 首页组件 | 约 1 周 |
| **P1 — Windows 服务化** | Rust 服务 + installer + JNA 客户端 + 设置 UI | 约 2–3 周 |
| **P2 — 发布准备** | 杀软白名单申诉、多用户/多环境测试、文档 | 约 1 周 |

**建议先做 P0**：它是零风险、全平台有效、且独立可用的。做完 P0 后用户已经能拿到"内存占用分析 + 自身释放"的价值，P1 是在此之上的平台增强——万一 P1 遇到杀软或权限阻力，也不至于整个功能归零。

---

## 七、与整体路线的关系

这个功能归入「启动保障三件套」的**启动前体检**：

```
启动前体检
├── Java 版本 × MC 版本 × 加载器 匹配
├── 内存分配合理性（已有 MemoryStatusBar）
├── 磁盘空间
├── 模组冲突 / 缺失依赖
└── 【新增】系统可用内存不足
        ├── 列出占用 Top 5（全平台）
        ├── HMCL 自身释放（全平台，JEP 346）
        └── 系统级 trim（Windows，服务化 L2，条件触发）
```

检测 → 透明化 → 一键优化，形成闭环，比单独做一个"优化按钮"更有价值。
