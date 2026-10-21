# 步骤 10 · 性能策略（Ⅰ）· Java 获取策略与发行版偏好

> **阶段**：阶段三 · 性能与内存 ｜ **优先级**：P0 ｜ **工期**：1 周
> **前置**：[步骤 07](07-下载源配置.md) ｜ **后续**：[步骤 11](11-性能策略-JVM参数预设向导.md)
> **代码侵入**：0（Bootstrap 注入 `hmcl.discoapi.override`；推荐面板挂首页仪表盘）
> **口径纪律**：本步骤任何文案**不得**出现"更快 / 提速 / 性能提升 XX%"——没有可复现的基准数据，这类话术一旦被社区拆穿会直接损害可信度。只讲"**能不能下得动、支不支持你的平台、支持到哪年**"。

---

## 1. 目标

1. **大陆网络可用**：Java 下载列表走社区镜像，避免 `api.foojay.io` 在国内的慢/超时导致"缺 Java 却装不上"；
2. **选择不迷路**：给出"该装哪个 Java"的明确建议（架构 + LTS + 包类型），并提供一键安装；
3. **偏好可配置**：默认推荐发行版（Zulu）可在社区设置中改（Temurin / Liberica / Corretto / 不指定），**只影响社区推荐面板**；
4. 不对上游 `JavaDownloadDialog`/自动安装逻辑做任何修改，在文档中如实写明限制。

完成标志（DoD）：

- [ ] Bootstrap 在未显式指定 `-Dhmcl.discoapi.override` 时注入社区镜像地址（步骤 05 的属性注入表更新）；
- [ ] 镜像端点可用性由部署侧保证，且**不缓存安装包本体**（只做 302 重定向 + 短 TTL 元数据缓存）；
- [ ] 镜像故障时行为与"上游 API 不可用"一致（有错误提示，不崩溃、不静默卡死）；
- [ ] 社区"Java 推荐"面板：按平台列出可安装发行版，标出偏好项、LTS 支持年限、包类型（JRE/JDK）；
- [ ] 一键安装调用上游公开 API，安装完成后注册到上游 Java 列表（与手动安装结果一致）；
- [ ] 偏好设置存 `community-settings.json`，缺字段默认 `ZULU`；
- [ ] 全项目文案审查：无未经验证的性能宣称（步骤 18 发布 checklist 复查）；
- [ ] 上游代码 `git diff` 为 0。

---

## 2. 事实核对（v3.16.4）

### 2.1 Java 下载有两条完全不同的链路

| 链路 | 触发方式 | 实现 | 能否被 `discoapi.override` 影响 |
|---|---|---|---|
| **A. Disco（foojay）** | 用户点"安装 Java" → `JavaDownloadDialog`：Mojang 官方 Java 或各发行版 | `DiscoFetchJavaListTask` → `https://api.foojay.io/disco/v3.0` | ✅ 是（`API_ROOT` 读系统属性 `hmcl.discoapi.override`） |
| **B. Mojang Java 自动安装** | 实例缺 Java 时上游自动补 | `HMCLJavaRepository.getDownloadJavaTask(...)` → `MojangJavaDownloadTask` | ❌ 否（走 Mojang 元数据，与 Disco 无关） |

关键源码：

```java
// HMCLCore/src/main/java/org/jackhuang/hmcl/download/java/disco/DiscoFetchJavaListTask.java:37
public static final String API_ROOT =
        System.getProperty("hmcl.discoapi.override", "https://api.foojay.io/disco/v3.0");
```

- `API_ROOT` 是 `static final`，**必须在类初始化前**设置属性 → 只能由 Bootstrap（步骤 05）在调用 `EntryPoint.main` 前注入；
- `DiscoJavaDistribution` 是 **public enum**，包含 `TEMURIN / LIBERICA / ZULU / GRAALVM / SEMERU / CORRETTO / ...`，每个枚举自带 `isSupport(platform)` 与支持的架构集合（`HMCLCore/src/main/java/org/jackhuang/hmcl/download/java/disco/DiscoJavaDistribution.java`）；
- `JavaDownloadDialog.showDialogAction(...)` 用 `DiscoJavaDistribution.values()` 过滤当前平台后全部展示，**上游没有"偏好/排序"概念**（`HMCL/src/main/java/org/jackhuang/hmcl/ui/main/JavaDownloadDialog.java:79-98`）；
- 自动安装链路 B 固定下载 Mojang 提供的 Java，**不受任何发行版偏好影响** —— 这是硬限制，本步骤不修改。

### 2.2 上游公开、可复用的安装 API

| API | 签名 | 用途 |
|---|---|---|
| `JavaManager.getInstallJavaTask` | `Task<JavaRuntime> getInstallJavaTask(Platform platform, String name, Map<String,Object> update, Path archiveFile)` | 从已下载的压缩包安装并注册 Java（`HMCL/src/main/java/org/jackhuang/hmcl/java/JavaManager.java:241`） |
| `JavaManager.getAllJava` / `getAllJavaProperty` | `Collection<JavaRuntime>` | 读取已安装 Java 列表 |
| `JavaManager.findSuitableJava` | `JavaRuntime findSuitableJava(GameVersionNumber, Version)` | 与上游一致的"合适 Java"判定（:318） |
| `DiscoJavaDistribution` | `boolean isSupport(Platform)`、`getSupportedArchitectures()` | 平台适配判断 |
| `DiscoFetchJavaListTask` | 抓取列表 | 只读调用，需遵守上游请求封装 |

> 我们自己的下载器**不重写 Disco 协议解析**：优先复用 `DiscoFetchJavaListTask`/`DiscoJavaRemoteVersion`（公共类），若签名不适用则退化为轻量 HTTP 调用（接口地址来自 `API_ROOT`，格式为 foojay disco v3 的公开 JSON）。

---

## 3. 设计

### 3.1 社区 Java 镜像（部署 + 注入）

**部署形态**：`https://java.<community-domain>/disco/v3.0` 反向代理 `https://api.foojay.io/disco/v3.0`

- **只做重定向**：列表 JSON 可缓存（TTL 5–10 分钟，按路径 + query 缓存）；`/ids/.../download` 之类返回文件流的端点 **一律 302 到上游 CDN**，不落盘、不缓存安装包；
- 压缩传输、HTTP/2、连接复用；上游 5xx 时返回 502 并带 `Retry-After`（上游客户端会走既有错误处理）；
- 若 foojay 的使用条款不允许代理（**上线前必须确认**），退路：镜像只代理**列表元数据**、下载链接保持官方 CDN；仍不允许则默认关闭镜像，仅保留"用户可自填镜像"入口；
- 运维监控：`GET /disco/v3.0/distributions` 健康检查 + 5xx 率告警（步骤 18 的发布 checklist 纳入）。

**注入**（Bootstrap，实际实现见 §10）：

```java
// CommunityBootstrap.main 内，早于 Main.main
CommunityJavaMirror.install();   // 镜像地址已配置且用户未显式 -D 时才 setProperty
```

- 镜像地址集中在 `CommunityConfig.DISCO_API_OVERRIDE` 常量，便于随域名迁移；
- **当前取值为空串 = 镜像尚未部署**：`install()` 为 no-op，行为与上游一致（走官方 `api.foojay.io`）。这是有意的：不能把用户导向一个不存在的端点。部署侧反向代理就绪后只需回填该常量；
- 用户显式 `-Dhmcl.discoapi.override=...` 时**不覆盖**（便于排查与自建镜像）；`CommunityJavaMirror.effectiveApiRoot()` 供面板显示实际接口地址；
- 该属性只影响链路 A；链路 B 的 Mojang Java 仍走步骤 07 的下载源策略。

### 3.2 发行版偏好（社区设置，默认 Zulu）

`community-settings.json` 新增：

```json
{ "java": { "preferredDistribution": "ZULU", "mirrorEnabled": true } }
```

- `preferredDistribution` 取值 = `DiscoJavaDistribution` 枚举名 + `"ANY"`；缺字段/未知值 → `ZULU`；
- `mirrorEnabled` 仅作为**展示与说明**开关（属性在 Bootstrap 已注入，运行期无法撤销；置 false 时推荐面板提示"镜像已启用，需重启并加 `-D` 覆盖"）——**不要假装能热切换**；
- **默认 Zulu 的理由（只写这几条，不多写）**：
  1. `DiscoJavaDistribution.ZULU` 覆盖 Windows（x64/x86/ARM64）、Linux（x64/x86/ARM64/ARM32/RISCV64/PPC64LE）、macOS（x64/ARM64），**桌面全平台齐**；
  2. 同时提供 JDK / JRE / JDKFX / JREFX 四种包型（`EnumSet.of(JDK, JRE, JDKFX, JREFX)`），JRE 可减少安装体积；
  3. Azul 官方长期提供 Java 8/11/17/21 等 LTS 构建，支持周期明确可查；
  4. **不声称更快**（理由见页首）。
- 用户可选 `TEMURIN`（Adoptium，社区最常用）、`LIBERICA`、`CORRETTO`、`ANY`（按上游枚举原序，选第一个可用项）。

**对外 API（步骤 14 的 `java-recommend` 卡片只读这里，签名固定）**：

```java
public final class org.jackhuang.hmcl.community.java.JavaPreference {
    public static Preference get();                                   // 独立实例（读默认路径），读失败返回默认
    public static Preference get(CommunitySettings settings);         // 指定设置实例（测试/注入用）
    public static void setDistribution(@Nullable DiscoJavaDistribution distribution);              // 写默认路径
    public static void setDistribution(CommunitySettings settings, @Nullable DiscoJavaDistribution distribution);
}

// distribution 为 null 表示 "ANY"；mirrorEnabled 由 CommunityConfig.isJavaMirrorConfigured() 决定
public record Preference(@Nullable DiscoJavaDistribution distribution, boolean mirrorEnabled) {}
```

> 14 不得直接解析 `community-settings.json`；写偏好统一走 `setDistribution`，由本类负责段内字段合并（`CommunitySettings.update`）。

### 3.3 "Java 推荐"面板（挂在步骤 14 的 `java-recommend` 卡片里）

入口：首页仪表盘 → "Java 推荐"卡片（容器/网格由[步骤 13](13-首页组件化A-框架搭建.md) 提供，卡片接入在[步骤 14](14-首页组件化B-P0组件与接入.md)）。

```
你的平台：Windows · x86_64
推荐：Zulu 21 (LTS, JRE)          [安装]   ← 偏好命中
其他可安装：
  Temurin 21 (LTS, JDK)           [安装]
  Liberica 21 (LTS, JRE)          [安装]
  Temurin 17 (LTS, JRE)           [安装]
说明：列表来自社区镜像；"启动器自动补 Java"仍使用 Mojang 官方 Java，不受此偏好影响。
```

- 列表项字段：发行版、大版本、LTS 标记（`DiscoJavaRemoteVersion` 的 `termOfSupport`/`releaseStatus`）、包类型、架构、下载大小；
- 默认勾选 = 偏好发行版中"版本最高且 `isSupport(platform)` 且 `packageType` 为 JRE 优先"的一项；
- **一键安装**：下载压缩包到临时目录（`DownloadProvider` 已有下载能力 + 进度 UI）→ `JavaManager.getInstallJavaTask(Platform.SYSTEM_PLATFORM, name, update, archiveFile)` → 成功后在面板刷新已安装列表；
- 若偏好发行版无当前平台构建 → 面板顶部提示"偏好发行版不支持当前平台，已回退"，默认选中第一个可用项；
- **不修改**上游 `JavaDownloadDialog` 的默认选中/排序：该限制在面板底部以小字说明（诚实优先，避免用户以为全局生效）。

### 3.4 与步骤 08 体检的联动

- 体检的 `java.compat` 检查只做"已选 Java 是否满足版本要求"，**不推荐发行版**（避免启动路径耦合网络）；
- 若体检发现"无合适 Java"，提示文案附带按钮"打开 Java 推荐面板"：点击后滚动定位/展开 14 的 `java-recommend` 卡片（不新增根导航、不改 `RootPage`）；
- 不在启动链上发起任何 Java 列表网络请求。

### 3.5 新增 i18n key

```
community.java.title
community.java.platform
community.java.recommended
community.java.installed
community.java.action.install
community.java.action.refresh
community.java.preference
community.java.preference.zulu/temurin/liberica/corretto/any
community.java.mirror.note
community.java.limit.upstream_dialog
community.java.error.list_failed / .install_failed
```

三份 `assets/lang/I18N*.properties` 同步（英文基座 + `zh` + `zh_CN`，其余语言回退基座；同 08/09 口径）。

---

## 4. 实施顺序与工期（1 周 = 5 人日）

| # | 工作 | 人日 |
|---|---|---|
| 1 | 镜像部署（反代 + 缓存 + 302 + 监控） | 1 |
| 2 | Bootstrap 注入 + 配置项 + 单测（不覆盖显式 -D） | 0.5 |
| 3 | 推荐面板 UI（列表/偏好/回退） | 1.5 |
| 4 | 一键安装（下载 + `getInstallJavaTask` + 进度/错误） | 1 |
| 5 | i18n + 文档口径审查（4 份 properties） | 0.5 |
| 6 | 联调：镜像不可用、无偏好平台构建、已安装重复检测 | 0.5 |

---

## 5. 测试计划

| 用例 | 期望 |
|---|---|
| 正常网络 | 列表来自镜像；`Zulu 21 JRE` 默认选中 |
| 屏蔽 `api.foojay.io` 模拟国内 | 镜像可用时列表正常（证明注入生效） |
| 镜像返回 502 | 面板显示"列表加载失败，请检查网络"；不崩溃；可重试 |
| 用户显式 `-Dhmcl.discoapi.override=http://127.0.0.1:1` | Bootstrap 不覆盖；请求发往用户指定地址 |
| 偏好 `GRAALVM`（Windows ARM32） | 无构建 → 自动回退到可用项 + 提示 |
| 一键安装 | `getInstallJavaTask` 成功，上游 Java 列表出现该 Java，可被实例选用 |
| 重复安装同版本 | 提示已存在（按 `JavaRuntime` 路径/版本判定），不重复下载 |
| 安装包损坏 | 安装失败提示 + 临时文件清理 |

---

## 6. 验收清单

- [ ] `DiscoFetchJavaListTask.API_ROOT` 在启动日志中打印为社区镜像地址（加一行 debug 日志，0 侵入）；
- [ ] 面板所有发行版信息来自 `DiscoJavaDistribution`/`DiscoJavaRemoteVersion`，无硬编码版本号；
- [ ] 偏好切换后默认选中项变化，且**不写上游配置**（只写 `community-settings.json`）；
- [ ] 一键安装产物与上游手动安装的目录结构一致（`java/` 下可被识别）；
- [ ] 文案审查通过：无"更快/加速/性能提升"（`grep -rn "更快\|加速\|性能提升" docs` 仅在"禁止用语"说明中出现）；
- [ ] 镜像条款确认记录（上线前）+ 故障降级演练记录。

---

## 7. 风险

| 风险 | 等级 | 应对 |
|---|---|---|
| foojay 条款不允许代理 | **高** | 上线前书面确认；退路 = 只代理元数据 / 默认关闭镜像 |
| 镜像成为单点故障 | 中 | 上游 5xx 透传 + 重试指引；保留 `-D` 覆盖能力；监控告警 |
| 上游改 `API_ROOT` 属性名/删除 Disco | 中 | 升级 checklist 检查 `hmcl.discoapi.override`；失效时面板降级为"仅 Mojang Java"说明 |
| 偏好 Zulu 被视为"恰饭" | 中 | 文案只列事实（覆盖/包型/支持周期）+ 其他发行版同权重展示 + 可改 |
| 一键安装的大文件下载在弱网失败 | 中 | 复用上游 `DownloadProvider` 的断点/重试能力；失败清理临时文件 |
| 安全：镜像被劫持返回恶意 Java 包 | **高** | 镜像强制 HTTPS + 上游透传 sha256 校验（`DiscoJavaRemoteVersion` 带 size/校验字段，安装前校验）；拒绝自签证书 |

---

## 8. 回退

- 不注入属性（删 Bootstrap 一行）→ 回到官方 Disco API；
- 面板入口在仪表盘 `enabled` 开关关闭 → UI 消失；
- 偏好设置项保留不读 → 无影响。

---

## 9. 关联

- 属性注入机制：[步骤 05](05-禁用自动更新与更新提示.md)、[步骤 06](06-数据目录与用户迁移.md)
- 下载源（Mojang Java 链路）：[步骤 07](07-下载源配置.md)
- 面板容器/卡片：[步骤 13](13-首页组件化A-框架搭建.md)、[步骤 14](14-首页组件化B-P0组件与接入.md)
- 下一步：[步骤 11 · JVM 参数预设向导](11-性能策略-JVM参数预设向导.md)

---

## 10. 实现记录（2026-10-21）

### 10.1 提交与规模

- 分支 `feature/10-java-preference` → 提交 `eb421e004`（feat(10)）→ 合并 `main` = `115e9f932`（merge 10-java-preference）。
- 13 files changed，+1158 行（含测试 104 行）。
- 本步**没有**改任何上游 `HMCL/` 既有文件（新增包 + `CommunityBootstrap` 一处调用 + 三份语言文件 + 两份 `META-INF/services`）。

### 10.2 落盘文件

| 文件 | 职责 |
|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/community/CommunityConfig.java` | 部署常量：`UPSTREAM_DISCO_API`、`PROP_DISCO_API="hmcl.discoapi.override"`、`DISCO_API_OVERRIDE`（空串=未部署）、`isJavaMirrorConfigured()`、`javaMirrorUrl()` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/CommunityJavaMirror.java` | `install()`（幂等、不覆盖显式 `-D`）、`effectiveApiRoot()`、`isInjected()`、`explicitOverride()` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/JavaChoice.java` | 面板/设置下拉的枚举：`ZULU/TEMURIN/LIBERICA/CORRETTO/ANY`，`distribution()`、`i18nKey()`、`of(DiscoJavaDistribution)` |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/JavaPreference.java` | 偏好读写：`SECTION="java"`、`KEY_DISTRIBUTION`、`KEY_MIRROR_ENABLED`、`VALUE_ANY="ANY"`、`DEFAULT_DISTRIBUTION=ZULU`；`record Preference(@Nullable DiscoJavaDistribution distribution, boolean mirrorEnabled)` + `storageValue()`/`choice()`；`get()`/`get(CommunitySettings)`、`setDistribution(...)` 两个重载 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/JavaRecommendPane.java` | 推荐面板：`refresh()`、`isLoaded()`、`selectedPreference()`、`selectPreference(JavaChoice)`、`install(Candidate)`；列表按偏好发行版优先、每发行版取最新匹配项 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/CommunityJavaSettingsPane.java` | 设置面板“Java”分组：偏好下拉 + 镜像地址说明 + 上游“下载 Java”对话框不受影响的说明 |
| `HMCL/src/main/java/org/jackhuang/hmcl/community/java/CommunityJavaSettingsProvider.java` | `SettingsSectionProvider`：`SECTION_ID="java"`、`I18N_KEY="community.settings.tab.java"`、`ORDER=25` |
| `HMCL/src/main/resources/META-INF/services/org.jackhuang.hmcl.community.ui.SettingsSectionProvider` | 追加 `CommunityJavaSettingsProvider`（与 07 的 download provider 并列） |
| 三份 `HMCL/src/main/resources/assets/lang/I18N*.properties` | 各追加 25 个 key（`community.settings.tab.java` + `community.java.*`） |
| `HMCL/src/test/java/org/jackhuang/hmcl/community/java/JavaPreferenceTest.java` | 5 个用例：默认 Zulu、`ANY`→null、未知值回退 Zulu、写后读回且落盘、写偏好保留 `mirrorEnabled` 字段 |

`CommunityBootstrap.main` 在 `CrashArchiveListener.install()` 之前调用 `CommunityJavaMirror.install()`，包 try/catch，失败只 WARNING、不阻塞启动。

### 10.3 对外契约（回填给 14/08）

```java
JavaPreference.Preference JavaPreference.get();                    // 读默认设置文件
void JavaPreference.setDistribution(@Nullable DiscoJavaDistribution distribution);
record JavaPreference.Preference(@Nullable DiscoJavaDistribution distribution, boolean mirrorEnabled)

JavaRecommendPane pane = new JavaRecommendPane();   // 无参；构造不联网
pane.refresh();                                     // 手动触发加载（幂等）
boolean loaded = pane.isLoaded();
JavaChoice choice = pane.selectedPreference();
```

- 14 的 `java-recommend` 卡片：先 `new JavaRecommendPane()`，首次可见时调 `refresh()`；卡片宽度变化由容器负责，面板内部 `ListView` 已 `setPrefWidth(0)` 自适应。
- 面板内“安装”不跳上游 `JavaDownloadDialog`：直接 `Controllers.taskDialog(...)` 执行“列表项 → 临时文件 → `JavaManager.getInstallJavaTask`”。上游 `JavaDownloadDialog` 保持原样（DoD）。
- 08 体检的“打开 Java 推荐面板”只需滚动/展开 14 的卡片，不新增根导航。

### 10.4 与设计稿的偏差（重要）

1. **镜像未部署**：`CommunityConfig.DISCO_API_OVERRIDE` 为空串，`install()` 不注入任何属性 → 当前实际行为=上游官方 `api.foojay.io`。这是有意的降级（避免指向不存在端点），也意味着“大陆可用”这一目标**尚未兑现**，需等反向代理部署后回填常量。
2. `mirrorEnabled` 不是用户可切换项：由 `CommunityConfig.isJavaMirrorConfigured()` 派生；设置面板不提供开关（避免“假装能热切换”）。
3. 面板构造不自动加载；由业务方（14）调用 `refresh()`。文档 §3.3 的“默认勾选/自动回退”已实现为 `selectCandidate` 的候选排序与 `fallback` 提示，但**没有**自动安装/自动选中列表项。
4. 安装路径不做上游的 `JavaInstallPage` 向导（命名/更新选项），直接以 `<apiParameter>-<javaVersion>-<packageType>` 作为注册名；重名时提示 `community.java.error.exists` 并中止。
5. 语言文件按 08/09 口径只同步 3 份，不是设计稿早期写的 4 份。
6. 只抓取偏好 + 其余最多 2 个发行版的列表（共 ≤3 个请求），避免一次加载全部发行版；失败时显示 `community.java.error.list_failed`、保留“刷新”按钮。
7. `JavaRecommendPane` 尚未被 14 引用（14 未实现），因此本轮**无 GUI 验证**（环境无 DISPLAY）。

### 10.5 验证

| 项 | 结果 |
|---|---|
| `:HMCL:compileJava --no-daemon` | BUILD SUCCESSFUL（仅 3 条既有 JavaFX `--add-exports` 警告） |
| `:HMCL:test --tests 'org.jackhuang.hmcl.community.*'` | **54 tests / 0 failures**（原 49 + Java 偏好 5） |
| `VERSION_ROOT=3.16 BUILD_NUMBER=1000 :HMCL:shadowJar` | BUILD SUCCESSFUL，`HMCL/build/libs/HMCL-3.16.1000.jar` **10,672,731 B** |
| jar 内容 | 新增 12 项：`community/CommunityConfig.class`、`community/java/` 下 9 个 class（含 `JavaRecommendPane$1`、`JavaRecommendPane$Candidate`）、`META-INF/services/...SettingsSectionProvider` |
| manifest | `Main-Class: org.jackhuang.hmcl.community.CommunityBootstrap`、`Implementation-Version: 3.16.1000` 未变 |
| `assets/hmcl.properties` | `hmcl.version=3.16.1000`、`hmcl.community.baseline=v3.16.4`、`hmcl.community.baseline.commit=fbcc1747…` 未变 |
| i18n | `assets/lang/I18N.properties` 中 `community.java.*` = 24 个（另有 `community.settings.tab.java`） |

固定环境：`export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11 GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home`。

### 10.6 未完成 / 依赖外部

- 镜像反向代理部署（域名 `<community-domain>` 未定）→ 回填 `CommunityConfig.DISCO_API_OVERRIDE`；
- foojay 使用条款确认（是否允许代理列表/下载重定向）；
- 14 的卡片接入与 GUI 验收（含“屏蔽 foojay 后列表仍可用”的注入生效验证——需镜像先部署）；
- 一键安装在真实网络下的端到端验证（本轮仅编译/单测/打包验证）；
- 上游升级时核对 `hmcl.discoapi.override` 与 `DiscoFetchJavaListTask` 是否变更（列入升级 checklist）。
