# 步骤 20 · HMCLauncher 启动壳重建（C++20 fork）

> **阶段**：阶段一 · 工程底座 ｜ **优先级**：P1 ｜ **工期**：2–5 天
> **前置**：[步骤 01](01-仓库建立与上游同步机制.md)（镜像/同步方式已定）、[步骤 03](03-产品身份与合规改造.md)（产品名、图标、版本号已定） ｜ **后续**：[步骤 04](04-包名Relocate改造.md)（产物拼接验证）、[步骤 18](18-内存优化P2-发布准备.md)（发布签名）
> **侵入点**：HMCL 仓库 **0 处**；新增独立 fork 仓库 `HMCLauncher`，通过 `HMCL_LAUNCHER_EXE` 替换构建期 stub
> **实现状态（2026-10-22）**：源码 fork/品牌字段/数据目录/CI 与 **MinGW i686 交叉构建**已完成（本地 `hmclauncher` 仓库，`c580531` + `802853c`）；**v3.16.8 起 stub 资源版本与启动器产品版本对齐为 `1.0.0.0`**；**Windows/MSVC 构建与 10 项行为验收待执行**；`home4-stub` 与 `m3spike8` 已删除，当前测试包为 `m3spike9`，已验收的 home3 保留。见 §7。

---

## 1. 目标

做完这一步，应当满足：

1. 我们拥有一个**自己构建**的 `HMCLauncher.exe`（Windows x86，32 位），产品名/图标/版本资源为社区版品牌，**版权声明保留**。
2. 该 exe 的行为与官方 `3.7.0.1` 对等：能找到 Java、能启动自身内嵌的 jar、能透传环境变量（`LMCL_JAVA_OPTS`，兼容 `HMCL_JAVA_OPTS`）、找不到 Java 时给出可操作提示（上游不转发命令行参数，见 §2.3）。
3. `HMCL/build.gradle.kts` 的 `makeExecutables` 能把它拼进 `HMCL-<version>.exe`（发行时改名 `LMCL-<version>-win-x86.exe`），裸机双击可启动。
4. 源码（含构建脚本）公开在我们自己的仓库，满足 GPLv3 §7 附加条款与对应源码义务。
5. 官方 stub 保留为**回退开关**（`HMCL_LAUNCHER_EXE` 置空即回到 `org.glavo.hmcl:HMCLauncher:3.7.0.1`）。

---

## 2. 背景与决策依据

### 2.1 为什么不用官方 exe 直接改名

- 官方 `HMCLauncher.exe`（454,144 B）的资源里写死了 `ProductName` / `FileDescription` / `LegalCopyright` 以及产品图标；改名/换图标必须改二进制资源。
- 用 `rcedit` 改预编译二进制的资源，属于"修改并分发"，同样触发 GPLv3；而且一旦上游升到 3.8.x，资源布局变化会静默失效。
- 真实 GPL 风险：**如果只分发改过资源的二进制而不提供其源码**，违反 GPL。我们既然要公开源码，为什么不直接编译自己的？

结论：**fork 源码、编译自己的 stub**（方案 C）。它同时解决品牌（图标/名称/版本资源）与合规（源码可公开、可复现构建）。

### 2.2 为什么不是 Rust 重写（方案 D，暂不采用）

Rust 重写的真正价值是"一份源码编三端"，但实测约束让它变成二期项目：

| 约束 | 影响 |
|---|---|
| Windows 需保持 **32 位**（官方 stub 就是 x86，以兼容 32 位 JRE/旧系统） | Rust 需 `i686-pc-windows-msvc` target，且要自写清单/资源与对话框 |
| Linux 的 `.sh` 启动壳（188 行）已能工作 | 换 Rust 的收益主要是体验统一，不是功能 |
| macOS 分发需要签名/公证；Windows 需签名 | Rust 只省语言，不省证书与流程 |
| 上游 stub 仍在演进（3.7.0.1） | fork 后可直接 merge；重写则永久双维护 |

**决策**：一期用 C++ fork（2–5 天、风险可控、可回退）；二期若要做跨平台统一引导，再评估 Rust 重写，并把 C++ stub 作为回退。

### 2.3 上游源码事实（2026-10-21 核对）

- 仓库与基线：`HMCL-dev/HMCLauncher`。clone 实测 HEAD `da0e0c3b4f01fa0edbaf575d9631f093daf70cd1`（2025-10-15，提交标题"将最低所需 Java 版本提升至 17 (#14)"），对应 Maven 制品 `org.glavo.hmcl:HMCLauncher:3.7.0.1`（`gradle/libs.versions.toml:2`、`HMCL/build.gradle.kts:71`）。
- 语言与工具链（`CMakeLists.txt` 逐行）：`cmake_minimum_required(VERSION 3.16)`；`project(HMCLauncher LANGUAGES CXX VERSION 3.7.0.1)`；`CMAKE_CXX_STANDARD 20` + `CMAKE_CXX_STANDARD_REQUIRED ON`；`CMAKE_WIN32_EXECUTABLE ON`（Win32 GUI 子系统）；`set(HMCL_EXPECTED_JAVA_MAJOR_VERSION 17)`（写进 `config.h`，低于 17 视为"没有可用 Java"）；`WINVER=0x0601 _WIN32_WINNT=0x0601`（**目标 Win7**）；MSVC `/utf-8 /W4 /MT` + `/ENTRY:wWinMainCRTStartup`；链接 `shlwapi version`；`configure_file` 生成 `HMCL.rc`/`config.h` 并 `COPYONLY` 复制根目录 `HMCL.ico`。
- 源码清单（实际，非"7 个"）：6 个 `.cpp` —— `main.cpp`、`java.cpp`、`platform.cpp`、`path.cpp`、`i18n.cpp`、`debug.cpp`，各配同名 `.h`；另有 2 个模板 `HMCL/HMCL.rc.in`、`HMCL/config.h.in`。
- 许可：GPLv3 + README 附加条款（与 HMCL 同源要求）：
  - 分发修改版必须**修改名称或版本号**以示不同（7(c) 类）；
  - **不得移除版权声明**（7(b)(0) 类）；
  - 分发二进制必须提供对应源码。
- 行为：
  - 取自身路径（`GetModuleFileNameW`），把**自身 exe 当作 jar 容器**读取尾部 jar；
  - 找 Java 的**真实**顺序（`HMCL/main.cpp` :56-181，2026-10-21 逐段核对）：
    1. `HMCL_JAVA_HOME`：设置了就必须用它；路径无效 → 直接弹错退出（**不再回退**）；
    2. 同目录捆绑 JRE：`jre-x86` / `jre-x64` / `jre-arm64`（按进程架构选一个）；
    3. 仍无 → **收集候选后按版本排序取最高**（≥17 才算"可用"）：`<exe目录>/.lmcl/java/windows-<arch>`、`JAVA_HOME`、`%APPDATA%/LMCL/java/windows-<arch>`、`PATH` 各目录（跳过 `Common Files\Oracle\Java`，issue #4079）、`Program Files` 厂商目录（Java / Microsoft / BellSoft / Zulu / Eclipse Foundation / AdoptOpenJDK / Semeru）、注册表 `SOFTWARE\JavaSoft\JDK` 与 `JRE` 的 `JavaHome`；上游为 `.hmcl`，**LMCL fork 已改为 `.lmcl`/`LMCL`**（见 §7.2）
    4. 一个都没有 → 弹框给下载页（`docs.hmcl.net/downloads/windows/<arch>.html`）；
  - 可执行文件名：`AttachConsole(ATTACH_PARENT_PROCESS)` 成功（从终端/父进程带控制台启动）→ `java.exe`，否则 → `javaw.exe`；
  - 透传环境变量 `LMCL_JAVA_OPTS`（兼容 `HMCL_JAVA_OPTS`；未设置时默认 `-Xmx1G -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15`）；**不转发命令行参数**（`wWinMain` 的 `lpCmdLine` 从未使用，`HLLaunchJVM` 只拼 `java -jar <自身>`）；
  - 找不到 Java 时弹对话框并提供下载页链接；
  - **不做**自更新、不做签名校验。
- 品牌资源落点（只改这些，不改逻辑；位置与字段名以实测源码为准）：
  - `HMCL/HMCL.rc.in`：`VS_VERSION_INFO` :31；版本四段取 `@PROJECT_VERSION_MAJOR@…@PROJECT_VERSION_TWEAK@`（:32-33）；字符串块 `080404b0`（:42，zh-CN/Unicode）内 `CompanyName`（:44）、`FileDescription`（:45）、`FileVersion`/`ProductVersion`（:46/:51）、`InternalName`/`OriginalFilename`（:47/:49）、`LegalCopyright`（:48）、`ProductName`（:50）；`IDI_HMCL ICON`（:23）；`Translation 0x804,1200`（:56）。
  - `HMCL/config.h.in`：`HMCL_LAUNCHER_VERSION`、`HMCL_EXPECTED_JAVA_MAJOR_VERSION`（含字符串版）——版本号经 CMake 注入，不必改任何 `.cpp`。
  - 图标：仓库根 `HMCL.ico`（CMake `configure_file ... COPYONLY` 复制到构建目录，再被 `.rc` 引用）。
  - 窗口标题、错误对话框文案、下载页链接在 `HMCL/main.cpp`/`HMCL/i18n.cpp`（改前先 `git grep`）。
- 官方产物实测结构（`HMCL-3.16.4.exe`，10,907,431 B，2026-10-21 下载核对）：
  - 前 **454,144 B** = stub（与 `HMCL-3.16.4.jar` 10,453,287 B 的差值一致）；偏移 454,144 处正是 `PK\x03\x04`；stub 区间内 `PK\x03\x04` 计数为 0；stub 尾部补零。**拼接是纯字节前缀**，我们的 stub 大小不必等于 454,144，校验脚本按实际 `len(stub)` 比对前缀。
  - 内嵌 manifest 仅 `<requestedExecutionLevel level='asInvoker' uiAccess='false'/>`（约 444 KB 处），**没有** `longPathAware`/`supportedOS`/DPI 声明；PE `machine=0x14c`（i386，32 位）、6 个 section；`VS_VERSIONINFO` 字符串以 UTF-16LE 存储。
- 上游 CI 事实（`.github/workflows/build.yml`，`push`/`pull_request`/`tag` 均触发）：
  1. checkout 本仓库；
  2. 再 checkout **`HMCL-Dev/HMCL` 的 `main`** 到 `HMCL-Build`（用于出完整 exe）；
  3. `cmake -A Win32 -B build` + `cmake --build build --config Release`；`Get-FileHash` 生成 `HMCLauncher.exe.sha256`；
  4. 设 `HMCL_LAUNCHER_EXE` 指向刚构建的 stub，在 `HMCL-Build` 内跑 `./gradlew makeExecutables --no-daemon`；
  5. artifacts 同时上传完整 `HMCL-*.exe(.sha256)` 与 `HMCLauncher.exe(.sha256)`；**tag 时 Release 只附 `HMCLauncher.exe` + `.sha256`**。
  - ⚠️ fork 改造点：必须把第 2 步的 `repository:`/`ref:` 换成我们的 HMCL fork（出包分支），或删掉第 2/4 步只产 stub；否则 CI 会拿**上游 main** 的 jar 拼包（与我们的 Bootstrap/版本号完全不一致）。
- HMCL 侧接入：`HMCL/build.gradle.kts:224-228` 在 `HMCL_LAUNCHER_EXE` 非空时按**文件名**把该文件打进 jar 的 `assets/`（`from(file(launcherExe))`），而 `makeExecutables` 只找 `assets/HMCLauncher.exe` → **注入文件的 basename 必须是 `HMCLauncher.exe`**（从 fork Release 下载后改名或直接下载为 `HMCLauncher.exe`，放在仓库固定路径如 `lib/stub/HMCLauncher.exe`）；`makeExecutables` 从中取 stub 字节 + shadow jar 字节拼接为 `HMCL-<name>.exe`。本地打包命令示例：`HMCL_LAUNCHER_EXE=/abs/path/HMCLauncher.exe ./gradlew makeExecutables`。

### 2.4 放弃的方案

| 方案 | 放弃原因 |
|---|---|
| 官方 exe 直接改名分发 | 资源写死、改二进制仍需开源、上游升级易失效 |
| `rcedit` 改官方 exe 资源 | 同上，且不可复现构建 |
| 只发 `.jar` + `.bat` | 用户双击体验倒退，Windows 主分发形态需要 exe |
| 自研 jpackage 安装器 | 三平台 matrix + macOS 签名/公证成本高，收益低（见总纲 §4.4） |
| Rust 重写（一期） | 见 2.2，排期与风险不划算 |

---

## 3. 详细实施

### 3.1 建 fork 仓库并锁定基线

```bash
# 1) 在 GitHub 上 fork HMCL-dev/HMCLauncher 到 <org>/HMCLauncher（或用 gh repo fork）
# 2) 本地克隆（走现有 ghfast 镜像重写）
git clone --filter=blob:none https://github.com/<org>/HMCLauncher.git
cd HMCLauncher

# 3) 锁定与官方 3.7.0.1 对应的源码基线
git tag -l | grep -i 3.7        # 若无 tag，用 main 并记录 commit
UPSTREAM=$(git rev-parse HEAD)
echo "$UPSTREAM" > UPSTREAM_COMMIT
```

在 fork 仓库 README 顶部署名：

```markdown
本仓库是 Lumina Minecraft Launcher（LMCL）的启动壳（基于 HMCL-dev/HMCLauncher，GPLv3）。
上游基线：<commit / tag>，日期：2026-10。
修改内容：产品名称、版本资源、图标、界面文案；未改变启动行为。
对应源码：本仓库。构建：见 .github/workflows/build.yml。
```

> 若上游无 3.7.0.1 的 tag：不要伪造 tag，改为记录 commit SHA，并在 `UPSTREAM_COMMIT` 与 README 中写明"对应 Maven 制品 3.7.0.1 的提交（以制品日期 2026 前后为准，构建前核对）。

### 3.2 本地构建环境

| 项 | 要求 |
|---|---|
| 系统 | Windows 10/11 x64（GitHub Actions 用 `windows-2022`）；**目标兼容 Win7**（源码 `WINVER=0x0601`） |
| 工具 | Visual Studio 2022 Build Tools（含 MSVC v143 + Windows 10/11 SDK）；C++20（`std::format` 需 VS 2022 17.x） |
| CMake | 上游 `cmake_minimum_required(VERSION 3.16)`；本机/CI 建议 **≥ 3.21** |
| 架构 | **必须 `-A Win32`**（与官方一致，保证 32 位） |

```powershell
# 在 "x64 Native Tools Command Prompt" 或任意 cmd 中
cmake -S . -B build -A Win32 -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
# 产物：build\Release\HMCLauncher.exe
```

首次构建常见问题：

| 现象 | 原因 | 处理 |
|---|---|---|
| `error C2039: 'format': is not a member of 'std'` | MSVC 版本过旧 | 升级到 VS 2022 17.x，或按上游 README 使用支持的编译器 |
| `LNK1112: module machine type 'x64' conflicts with target machine type 'x86'` | 用了 x64 生成器 | 重新执行 `cmake -A Win32`，删掉 build 目录 |
| 链接不到 `shlwapi.lib`/`version.lib` | 未装 Windows SDK | 安装 Windows 10/11 SDK |

### 3.3 品牌改造（只改资源与文案，不改逻辑）

先定位（资源与文案集中在少数文件，2026-10-21 实测）：

```bash
git grep -n "VERSIONINFO\|ProductName\|FileDescription\|LegalCopyright\|IDI_HMCL" -- . ':!*.md'
```

| 文件:行 | 字段 | 社区版取值 |
|---|---|---|
| `HMCL/HMCL.rc.in:44` | `CompanyName` | `<组织名或"个人开发者">`（**不要写 HMCL-dev**） |
| `HMCL/HMCL.rc.in:45` | `FileDescription` | `LMCL`（资源管理器/任务管理器里显示的名字） |
| `HMCL/HMCL.rc.in:50` | `ProductName` | `LMCL` |
| `HMCL/HMCL.rc.in:48` | `LegalCopyright` | **保留** `Copyright (C) 2025 huangyuhui`，再追加一行 `Modifications Copyright (C) 2026 <组织名>` |
| `HMCL/HMCL.rc.in:47/:49` | `InternalName` / `OriginalFilename` | 改为 `LMCL.exe`（与磁盘文件名无关；fork 模板同步改，保持资源表自洽） |
| `HMCL/HMCL.rc.in:46/:51` | `FileVersion` / `ProductVersion` | 由 `project(... VERSION 1.0.0.0)` 注入（**必须 4 段**：`FILEVERSION` 用 `PROJECT_VERSION_TWEAK` 拼第 4 段，写 3 段会生成 `1,0,0,` 非法值）：显示为 `1.0.0.0`，与 HMCL 产品版本 `1.0.0` 对齐（见步骤 03）；上游基线 `v3.16.4` 单独记录在 `hmcl.community.baseline` |
| `HMCL/HMCL.rc.in:23` + 仓库根 `HMCL.ico` | 图标 | 社区版图标（含 16/32/48/256 多尺寸；CMake `configure_file` 自动复制） |
| `HMCL/i18n.cpp:8-23` | 三条错误文案（取路径失败 / `LMCL_JAVA_HOME` 无效 / 找不到 Java） | 把 "HMCL" 换成产品名；保留 "Java 17+" 与操作指引；中文分支仅在 `GetUserDefaultUILanguage()==2052` 时生效；代码接受 `LMCL_JAVA_HOME`，兼容 `HMCL_JAVA_HOME` |
| `HMCL/main.cpp:209-213` | Java 下载页链接 | 换成自有页面，或保留 HMCL 官方页（见[步骤 03](03-产品身份与合规改造.md) 决策） |

> 没有"窗口标题"可改：stub 只是 `CreateProcess` 拉起 Java，自身不建窗口。

**不要动**：Java 查找顺序与"取最高版本"选择、jar 尾部读取方式、`HMCL_JAVA_OPTS` 处理、`javaw.exe`/`java.exe` 选择（控制台判定）、进程创建参数。这些是行为对等的根基；命令行参数上游本就不转发，保持不转发即对等（若要新增转发，须登记为社区版差异并补验收）。

### 3.4 版本资源、manifest 与兼容项

- **manifest**：官方 exe 内嵌的是链接器默认最小 manifest，内容只有 `trustInfo/requestedPrivileges/requestedExecutionLevel level='asInvoker' uiAccess='false'`；**没有** `longPathAware`、`supportedOS`、DPI 声明。→ **不要新增/修改 manifest**（新增 `longPathAware` 属行为差异，须登记并补测；也不要用 `requireAdministrator`）。
- **版本注入**：只改 `CMakeLists.txt` 的 `project(HMCLauncher LANGUAGES CXX VERSION 1.0.0.0)` 一处（**必须 4 段**，否则 `@PROJECT_VERSION_TWEAK@` 为空、`FILEVERSION` 非法）——`HMCL.rc.in` 的 `FILEVERSION`/`PRODUCTVERSION` 与 `config.h` 的 `HMCL_LAUNCHER_VERSION` 都由它生成；不要手改构建产物，保持可复现。
- **最低 Java**：`set(HMCL_EXPECTED_JAVA_MAJOR_VERSION 17)` 决定"找不到 Java"的判定与提示文案；它应与 HMCL 的 Java 底线一起变化（不是本步能单独拍的产品决策）。
- 不新增加壳/压缩（exe 需保持"可直接读尾部 jar"的结构——HMCL 拼接逻辑只依赖 stub 前缀，但 stub 自己要读自身尾部；加壳会破坏该读取）。
- 若签名：**签 stub 还是签最终 exe** 由[步骤 18](18-内存优化P2-发布准备.md) 决定；本期先不签名，接受 SmartScreen 首次提示。
- 编译产物应保持：`Machine=x86`、Win32 GUI 子系统、manifest 如上游、无 `PK` 段（与 §2.3 实测结构一致）。

### 3.5 行为对等验证清单（只验这些，不重复实现）

构建完成后在干净 Windows 虚拟机逐项实测：

| # | 场景 | 期望 |
|---|---|---|
| 1 | 仅复制 `LMCL.exe`（内含 jar），系统有 JDK 17+ | 双击直接出界面 |
| 2 | 无任何 Java | 弹对话框，文案含产品名 + 可操作链接，不静默退出 |
| 3 | 目录含中文/空格/长路径（>260 字符） | 正常启动 |
| 4 | 设置 `LMCL_JAVA_HOME` 指向 JDK 21（兼容 `HMCL_JAVA_HOME`） | 使用该 JDK（日志 `Java` 行可验证） |
| 5 | 同目录放 `jre-x64`（绿色 JRE） | 优先使用内置 JRE |
| 6 | 从 terminal 启动 exe（带控制台） | 用 `java.exe` 启动、日志可见；双击（无控制台）用 `javaw.exe` |
| 7 | 设置 `LMCL_JAVA_OPTS=-Xmx2G`（兼容 `HMCL_JAVA_OPTS`） | Java 进程参数生效（日志/任务管理器可见） |
| 8 | 32 位系统 / 32 位 JRE | 可启动（这是保留 x86 构建的意义） |
| 9 | 英文系统 + 中文系统 | 对话框编码正常、不乱码 |
| 10 | 与官方 `HMCLauncher.exe` 同机对比 | 启动耗时、参数、提示行为无明显差异 |

### 3.6 接入 HMCL 构建

```bash
# 用我们构建的 stub 出包
export HMCL_LAUNCHER_EXE=/abs/path/HMCLauncher.exe
./gradlew :HMCL:makeExecutables
# 产物：HMCL/build/libs/HMCL-<version>.exe（makeExecutables 输出在 shadow jar 同目录）
# 参考：官方 v3.16.4 实测 = stub 454,144 B + jar 10,453,287 B = exe 10,907,431 B
python3 - <<'PY'
stub = open('/abs/path/HMCLauncher.exe','rb').read()
exe  = open('HMCL/build/libs/HMCL-1.0.0.exe','rb').read()
assert exe[:len(stub)] == stub, 'prefix mismatch'
assert stub.count(b'PK\x03\x04') == 0, 'stub unexpectedly contains zip entries'
assert exe[len(stub):len(stub)+4] == b'PK\x03\x04', 'jar not at stub end'
print('ok: stub', len(stub), 'B; exe', len(exe), 'B; jar starts at', len(stub))
PY
```

再执行一次"裸机双击启动"（验收 Checklist 第 7 项），确认不是只有 jar 能跑。

### 3.7 CI 与发布

先决定 fork 仓库 CI 的职责（**推荐 A**：职责单一，不会误用上游 jar）：

- **A（推荐）只产 stub**：fork 仓库 `build.yml` 只做 `cmake -A Win32` → `sha256` → tag 时发布 `HMCLauncher.exe` + `.sha256`；完整 exe 由我们 HMCL 仓库的 CI 用 `lib/stub/HMCLauncher.exe` 拼（与 §3.6 一致）。
- **B 保留上游"完整 exe"流水线**：可行，但必须把上游 build.yml 第 2 步的 `repository: HMCL-Dev/HMCL` / `ref: main` 换成 `<org>/HMCL` + 出包分支；否则会用**上游 main** 的 jar 出包（版本号、Bootstrap 属性全错，且构建成功不报错）。
- 上游 build.yml 事实（可直接照抄结构）：`windows-2022`、`cmake -A Win32 -B build`、`Get-FileHash` 生成 `HMCLauncher.exe.sha256`、设 `HMCL_LAUNCHER_EXE` 后 `./gradlew makeExecutables --no-daemon`、artifacts 同时含完整 exe 与 stub；**tag 时 Release 只附 stub + sha256**。
- 发布时：tag（如 `v1.0.0-stub.1`）+ Release 附 exe、源码链接、上游基线 commit。
- HMCL 仓库：把 stub 的 `sha256` 写进 step-20 的发布记录；`HMCL_LAUNCHER_EXE` 指向仓库内 `lib/stub/HMCLauncher.exe`（**优先固定路径，避免构建依赖网络**）。
- 建议同时保留 `docs/附录/CI工作流/` 中的构建说明，便于审计"对应源码"。

---

## 4. 验收 Checklist

- [x] fork 仓库已建，README 写明上游基线 commit、修改内容、许可证与构建方式
- [ ] `cmake -A Win32` 构建成功，产物为 32 位 exe（`dumpbin /headers` 或 Python `pefile` 确认 Machine=x86）
- [x] `HMCL.rc.in` 的 8 个资源字段逐项核对：`CompanyName`/`FileDescription`/`ProductName`/`LegalCopyright`（保留上游行）/`InternalName`/`OriginalFilename`/`FileVersion`+`ProductVersion`（MinGW 交叉构建产物静态核对）
- [ ] manifest 未被改动：提取核对只有 `asInvoker`，无 `longPathAware`/`supportedOS`/`requireAdministrator`（`sigcheck -m` 或 PowerShell）
- [ ] 3.5 的 10 项行为对等全部实测通过（尤其无 Java 提示与中文路径）
- [x] `HMCL_LAUNCHER_EXE=... ./gradlew makeExecutables` 出包，且 §3.6 的断言脚本通过（前缀 = stub、stub 内无 `PK`、jar 起始于 `len(stub)`；本机 MinGW 交叉构建版）
- [ ] 干净 Windows 虚拟机双击 `LMCL-*.exe` 成功进入主界面
- [x] 官方 stub 回退可用：home3 包仍为官方 stub；`unset HMCL_LAUNCHER_EXE` 的构建路径未改
- [x] fork CI 未引用 `HMCL-Dev/HMCL` 的 main（只产 stub，`tools/check_branding.py` 门禁通过）
- [ ] 源码公开地址、构建说明、sha256 已记录在仓库文档

---

## 5. 风险与应对

| 风险 | 等级 | 应对 |
|---|---|---|
| 新 stub 启动失败（最坏：用户看不到界面） | **高** | 保留官方 stub 回退开关；3.5 十项清单必须全绿才切换默认产物 |
| 32 位构建被误配成 x64 | 高 | CI 固定 `-A Win32`；验收用 `dumpbin` 检查 Machine |
| fork CI 误用上游 HMCL main 出包 | 中 | 按 §3.7 改 `repository`/`ref` 或只产 stub；出包后核对版本号与 Bootstrap 属性 |
| 资源改动触发杀软误报（改资源=改二进制） | 中 | 只改资源不动代码逻辑；发布前 VirusTotal 多引擎自查；保留未签名/已签名两版对比 |
| 忘记保留版权声明 → GPL 违约 | **高** | 资源检查写入 Checklist；release 前人工核对 `LegalCopyright` |
| 上游 stub 升级（3.7.0.1 → 3.8+）行为变化 | 中 | 记录基线 commit；每季度 rebase 一次并重跑 3.5 清单 |
| Win7 兼容（上游支持，我们可能破坏） | 中 | 不改动平台相关代码；如需支持 Win7，保持 `/SUBSYSTEM:WINDOWS,6.01` 与 API 集合不变 |
| exe 被 Windows 标记"未知发布者" | 中 | 步骤 18 统一做 Authenticode 签名；本期明确告知用户 |

---

## 6. 关联文档

- [步骤 01 · 仓库建立与上游同步机制](01-仓库建立与上游同步机制.md)（fork 与同步方式）
- [步骤 03 · 产品身份与合规改造](03-产品身份与合规改造.md)（名称、图标、版本号、许可证）
- [步骤 04 · 包名 Relocate 改造](04-包名Relocate改造.md)（产物拼接与签名）
- [步骤 18 · 内存优化 P2 · 发布准备](18-内存优化P2-发布准备.md)（签名与白名单）
- [总纲 §4.4 构建与产物](../00-整体要求.md#44-构建与产物v3164-实测)、[§4.6 启动引导与属性注入](../00-整体要求.md#46-启动引导与属性注入)
- [总纲 §3 法律与合规要求](../00-整体要求.md#3-法律与合规要求硬约束)

---

## 7. 实现记录（2026-10-22，源码 fork + 交叉构建）

### 7.1 当前状态

**源码侧与 MinGW i686 交叉构建完成；Windows/MSVC 构建与实机行为验收未完成**。本机为 WSL2，已用 apt 下载的 MinGW-w64 GCC 13.2 交叉编译出 PE32/i386 stub，并成功拼出带新 stub 的 `LMCL.exe`；没有 Windows GUI 环境，未双击运行，也未执行 §3.5 的 10 项行为对等。

| 项 | 状态 |
|---|---|
| 本地 fork 仓库 | ✅ `/home/kerze/hmclce/hmclauncher`（独立 git，`main`） |
| 上游基线 | ✅ `da0e0c3b4f01fa0edbaf575d9631f093daf70cd1`（对应 Maven `3.7.0.1`），写入 `UPSTREAM_COMMIT` |
| 版本注入 | ✅ `CMakeLists.txt` `VERSION 1.0.0.0`（四段，v3.16.8 对齐产品版本） |
| 资源品牌字段 | ✅ `HMCL/HMCL.rc.in` 六项 + 版权保留 |
| 错误文案品牌 | ✅ `HMCL/i18n.cpp` 英文/中文产品名改 LMCL；`LMCL_JAVA_HOME` 优先，保留 `HMCL_JAVA_HOME` 兼容别名 |
| 数据目录 | ✅ Java 搜索改 `.lmcl/java`、`%APPDATA%/LMCL/java`（上游 `.hmcl`） |
| 环境变量 | ✅ 优先 `LMCL_JAVA_OPTS`/`LMCL_LAUNCHER_VERBOSE_OUTPUT`，保留 `HMCL_*` 兼容别名 |
| 调试日志 | ✅ `logs/lmcl-launcher.log`，前缀 `[LMCL]` |
| 自有图标 | ⏳ 待补；当前仍用上游 `HMCL.ico`（占位） |
| Java 下载页 | ⏳ 待补；当前保留上游 `docs.hmcl.net` |
| 启动逻辑 | ✅ 未改（Java 查找/参数/自读 jar 尾部） |
| CI | ✅ 只产 stub，`windows-2022`、`-A Win32`、PE machine 断言、无 ZIP 头断言、tag Release |
| MinGW 交叉构建 | ✅ PE32/i386/GUI，1,201,152 B，SHA-256 `ded29e28…`（v3.16.8 重建） |
| Windows/MSVC + 10 项行为验收 | ⏳ 本机无法执行 |

### 7.2 文件与改动

fork 仓库提交：

- `8559bfb brand(LMCL): fork HMCLauncher stub, version 3.16.1000.0`（历史）
- `c580531 brand(LMCL): use .lmcl/LMCL java dirs and LMCL env vars`
- `802853c brand(LMCL): align stub version with 1.0.0.0`（v3.16.8）

| 文件 | 改动 |
|---|---|
| `CMakeLists.txt` | `project(... VERSION 1.0.0.0)`（v3.16.8；历史 `3.16.1000.0`） |
| `HMCL/HMCL.rc.in` | `CompanyName=LMCL Team`；`FileDescription=Lumina Minecraft Launcher`；`InternalName`/`OriginalFilename=LMCL.exe`；`ProductName=LMCL`；`LegalCopyright` 保留上游并追加 `Modifications Copyright (C) 2026 LMCL Team`；`STRINGTABLE` 改 `LMCL` |
| `HMCL/i18n.cpp` | 错误提示产品名改 LMCL；`LMCL_JAVA_HOME` |
| `HMCL/main.cpp` | 兼容读取 `LMCL_*`/`HMCL_*` 环境变量；Java 搜索 `.lmcl/java`、`%APPDATA%/LMCL/java`；日志标题改 LMCL |
| `HMCL/debug.cpp` | `logs/lmcl-launcher.log`、`[LMCL]` 前缀 |
| `README.md`/`BRANDING.md`/`UPSTREAM_COMMIT` | 基线、修改说明、品牌清单、GPLv3 附加条款 |
| `.github/workflows/build.yml` | 只产 stub；不 checkout 上游 HMCL；PE/ZIP 断言；tag Release |
| `tools/check_branding.py` | 静态品牌/CI 门禁，已通过 |

### 7.3 LMCL 仓库接入

- 新增 `lmcl/lib/stub/README.md`：fork 路径、`HMCL_LAUNCHER_EXE` 用法、basename 必须 `HMCLauncher.exe`、发布前验收。
- **未提交二进制**：`lmcl/lib/stub/` 只有说明；`HMCL/build.gradle.kts` 的官方 stub 回落逻辑不变。
- 采用新 stub 的测试包放在 `dist/LMCL-3.16.1000-win-x64-home4-stub.zip`（历史，已删除）；当前测试包为 `dist/LMCL-1.0.0-win-x64-m3spike9.zip`，**仅供测试**；已验收的 home3（官方 stub）保留为回退。

### 7.4 MinGW 交叉构建（本机实测）

工具链：apt 包解压到 `/home/kerze/hmclce/.tools/mingw32`（GCC 13.2 `i686-w64-mingw32-g++-posix`、binutils 2.45 `windres`、`libisl23`/`libmpc3`），`LD_LIBRARY_PATH=.tools/mingw32/usr/lib/x86_64-linux-gnu`；按上游 CMake 的 6 个 `.cpp` + 生成 `config.h`/`HMCL.rc` 手工编译：

```bash
i686-w64-mingw32-windres -I build -I HMCL -DUNICODE -D_UNICODE -i build/HMCL.rc -o build/HMCL.rc.o
i686-w64-mingw32-g++-posix -std=c++20 -Os -s -municode -mwindows   -ffunction-sections -fdata-sections -DUNICODE -D_UNICODE   -DWINVER=0x0601 -D_WIN32_WINNT=0x0601 -I build -I HMCL   main.cpp platform.cpp i18n.cpp java.cpp path.cpp debug.cpp build/HMCL.rc.o   -o build/HMCLauncher.exe -static -static-libgcc -static-libstdc++   -Wl,--gc-sections -lshlwapi -lversion
```

结果：

- `HMCLauncher.exe` 1,201,152 B，PE32 GUI Intel i386，machine `0x14c`，stub 内 `PK\x03\x04` 计数 0。
- v3.16.8 重建（`1.0.0.0`）SHA-256 `ded29e28971755afc83041f91dec9fce7ff4f4a130c0bb07983b4a42f8f3370b`；历史 `3.16.1000.0` 版本为 `89b58fa0…`。
- UTF-16 资源字符串含 `LMCL`、`Lumina Minecraft Launcher`、`Copyright (C) 2025 huangyuhui; Modifications Copyright (C) 2026 LMCL Team`；无 `.hmcl` 搜索目录。
- DLL 依赖仅 `ADVAPI32`/`KERNEL32`/`SHELL32`/`USER32`/`VERSION`/`msvcrt`（Win7 可用）。
- `HMCL_LAUNCHER_EXE` 指向该文件后 `:HMCL:makeExecutables` 成功：jar **11,156,135 B**（SHA-256 `0626a033…`）、exe **12,357,287 B**（SHA-256 `2a9e0e7a…`）；`exe[:len(stub)] == stub`、`exe[len(stub):len(stub)+4] == b'PK\x03\x04'` 均通过。
- **限制**：交叉编译只证明 PE/资源/拼接正确；没有 Windows 运行时，GUI 启动、Java 查找、中文路径、`LMCL_JAVA_HOME` 等仍需 Windows 实机验收。
- **manifest 差异**：MinGW 产物未内嵌 manifest；官方 MSVC 产物带 `asInvoker` trustInfo。默认 UAC 行为仍是 asInvoker，但高 DPI 感知不同；正式发布前应在 MSVC 构建中复核，必要时补 manifest 资源。

### 7.5 待 Windows 执行的验收

1. 在 Windows 2022 跑 CI 的 `cmake -A Win32`/MSVC 构建，或在实机跑上述 MinGW 构建。
2. 确认 `HMCLauncher.exe` 为 PE32/i386，且不含 `PK\x03\x04`。
3. 用新 stub 执行 `:HMCL:makeExecutables`，断言前缀/jar 偏移。
4. 按 §3.5 跑 10 项行为对等（尤其无 Java 提示、中文路径、`LMCL_JAVA_HOME`/兼容别名、内置 JRE、32 位、英文/中文对话框）。
5. 干净 Windows 虚拟机双击当前 `m3spike9` 的 `LMCL.exe`；若失败，回退 home3（官方 stub）。
6. 记录 stub SHA-256，更新 `BRANDING.md` 的图标/Java 链接状态，再决定是否替换默认包。

### 7.6 v3.16.8 stub 版本对齐（当前）

启动器产品版本改为 `1.0.0` 后，stub 的资源版本同步对齐，避免 Windows 文件属性仍显示 `3.16.1000.0`：

- `hmclauncher/CMakeLists.txt`：`project(HMCLauncher LANGUAGES CXX VERSION 1.0.0.0)`（提交 `802853c`）。
- 生成 `config.h` 的 `HMCL_LAUNCHER_VERSION` 为 `L"1.0.0.0"`；生成 `HMCL.rc` 的 `FILEVERSION`/`PRODUCTVERSION` 为 `1,0,0,0`，`FileVersion`/`ProductVersion` 为 `1.0.0.0`。
- MinGW i686 重新编译：`HMCLauncher.exe` 1,201,152 B，SHA-256 `ded29e28971755afc83041f91dec9fce7ff4f4a130c0bb07983b4a42f8f3370b`；PE32 GUI i386，`strings` 含 `1.0.0.0`、不含 `3.16.1000.0`。
- 用该 stub 重建 `HMCL-1.0.0.exe`：exe 13,050,225 B（`4dcb508f…`），jar 11,849,073 B（`c1d264c7…`）；`exe[:stub]==stub`、`exe[stub:]==jar`。
- 同步更新了 `README.md` 的品牌/版本说明与 `BRANDING.md` 的待办状态；Windows/MSVC 构建与 10 项行为验收仍未执行。

### 7.7 风险与回退

- **未实机启动**：`home4-stub` 只作测试，不替换已验收的 home3；`HMCL_LAUNCHER_EXE` 置空即回官方 stub。
- **MinGW CRT 差异**：本 stub 用 MinGW 静态 CRT，体积 1.2 MB（官方约 454 KB），依赖面已压到 Win7 系统 DLL；MSVC 正式构建仍应作为发布基线。
- 图标/Java 下载页仍是上游占位，发布前必须处理。
- fork 仓库尚无远端；公开源码义务在发布二进制前必须补远端地址与 Release 源码包。
