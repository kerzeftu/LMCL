# HMCL 品牌残留清理与账户登录修复方案

> 状态：**暂停（2026-10-23）**。方案稿已完成，未修改代码；当前优先做 M3 UI 重构。  
> 暂停原因：P0 微软登录依赖 LMCL 自己的 Entra 应用与 Minecraft AppID 审核，注册前置的租户问题尚未解决（详见 §2.4.2）。  
> 基线：LMCL 1.0.0，基于 HMCL v3.16.4；文档基线 v3.16.13。  
> 触发：用户 m18469——微软账户、第三方认证服务器登录时提示“非官方构建的 HMCL”，设置 → 外观 → 启动器字体等位置仍出现 HMCL。  
> 目标：把用户可见的产品身份统一为 LMCL，修复账户登录被误判/被禁用的问题；同时保留 GPL 归因、上游兼容标识和技术契约。  
> 边界：本方案只做品牌、账户提示、链接、文案和兼容层；不做 M3 视觉重构，不改 Java 包名，不擅自更换开源许可证。

---

## 1. 结论摘要

这不是单纯“替换几个字符串”的问题。当前有三条独立的原因链：

1. **微软登录被构建配置禁用**  
   构建脚本从环境变量 `MICROSOFT_AUTH_ID` 读取 OAuth client id，LMCL 没有配置自己的值，生成的 jar manifest 里 `hmcl.microsoft.auth.id` 为空。`OAuthServer.getClientId()` 返回空字符串后，微软登录页直接禁用登录，并显示上游的“非官方构建的 HMCL”提示。  
   **只删提示不能恢复登录，必须注册 LMCL 自己的 Microsoft Entra 应用并提供 client id。**

2. **第三方认证服务器被 `IntegrityChecker.isOfficial()` 误伤**  
   LMCL 的 jar 没有上游官方签名，`isOfficial()` 恒为 false。`CreateAccountPane` 对所有非离线账户工厂（包括 authlib-injector 第三方服务器）显示 `unofficial.hint`。这不是第三方服务器不安全，而是构建身份判断与产品身份混用了。  
   **应移除这段上游官方构建警告，改为 LMCL 自己的中性安全提示；不要把 `isOfficial()` 全局改成 true。**

3. **产品文案、帮助/反馈、更新控件仍大面积引用 HMCL**  
   包括“设置 → 外观 → 启动器字体”的示例文字、帮助页抓取 `docs.hmcl.net`、反馈页的 HMCL QQ/Discord/GitHub、崩溃窗口联系链接、设置页的上游更新区、启动器 nightly 公告、主题描述、OAuth 回调网页标题等。  
   **需要按“用户可见品牌 / 链接 / 内部标识 / 法律归因”四类分别处理，不能全局无差别替换。**

### 严重度总览

| 级别 | 问题 | 用户影响 | 处理阶段 |
|---|---|---|---|
| P0 | 微软登录因 client id 为空被禁用 | 无法登录微软账户；提示“非官方 HMCL” | 阶段 A（发布阻塞） |
| P0 | 第三方 authlib 服务器登录显示“非官方构建” | 误以为服务器/启动器不安全 | 阶段 A |
| P0 | 用户协议链接指向 HMCL EULA | 法律文本错误 | 阶段 A |
| P0 | OAuth 回调网页标题为 HMCL | 浏览器登录页品牌错误 | 阶段 A |
| P1 | 外观 → 启动器字体示例为 “Hello Minecraft! Launcher” | 用户直接可见 | 阶段 B |
| P1 | 帮助页只认 `docs.hmcl.net`，无兜底 | 断网/上游改版时空白或异常 | 阶段 B |
| P1 | 反馈页只有 HMCL QQ/Discord/GitHub | LMCL 用户无处反馈 | 阶段 B |
| P1 | 设置页仍显示上游更新通道/预览/自动弹窗 | 与“关闭自动更新”的产品决策冲突 | 阶段 B |
| P1 | nightly 公告每次启动出现，含 HMCL 链接 | 品牌错误且无法永久关闭 | 阶段 B |
| P1 | 崩溃窗口/日志帮助指向 HMCL | 求助路径错误 | 阶段 B |
| P2 | `Metadata` 文档/联系/更新/Java 下载 URL 仍为 hmcl.net | 间接跳转到 HMCL | 阶段 C |
| P2 | 日志标签、崩溃报告头、进程监听前缀、User-Agent 为 HMCL | 日志和网络身份不一致 | 阶段 C |
| P2 | 主题描述/作者为 HMCL | 主题列表可见 | 阶段 C |
| P3 | 环境变量、系统属性、`hmcl://`、包名、modpack ID | 兼容性契约 | 阶段 D，保留并补 LMCL 别名 |
| P3 | GPL 版权头、上游贡献者、deps/thanks | 法律归因 | 永久保留 |

---

## 2. 账户问题根因链

### 2.1 微软账户登录

调用链：

```text
MicrosoftAccountLoginPane
  → Accounts.OAUTH_CALLBACK.getClientId()
  → OAuthServer.getClientId()
  → System.getProperty("hmcl.microsoft.auth.id",
        JarUtils.getAttribute("hmcl.microsoft.auth.id", ""))
```

关键位置：

| 文件 | 行为 |
|---|---|
| `HMCL/build.gradle.kts:32` | `val microsoftAuthId = System.getenv("MICROSOFT_AUTH_ID") ?: ""` |
| `HMCL/build.gradle.kts:166` | 把 `hmcl.microsoft.auth.id` 写入构建属性/manifest |
| `HMCL/src/main/java/org/jackhuang/hmcl/game/OAuthServer.java:221-225` | `getClientId()` 先读系统属性，再读 jar manifest |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane.java:144-149` | client id 为空时显示 `account.methods.microsoft.snapshot`，禁用登录并 return |
| 同文件 `:153-157` | `!IntegrityChecker.isOfficial()` 时显示 `unofficial.hint` |
| `HMCL/src/main/java/org/jackhuang/hmcl/setting/Accounts.java:578` | 抛出 `MicrosoftAuthenticationNotSupportedException` 时映射到 `account.methods.microsoft.snapshot` |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/AccountListItemSkin.java:127` | client id 为空时禁用微软账户刷新，显示 `.snapshot.tooltip` |

当前构建事实：

- `HMCL/build/hmcl.properties:6` 为 `hmcl.microsoft.auth.id=`（空）。
- `HMCL/build/libs/HMCL-1.0.0.jar` 的 `META-INF/MANIFEST.MF` 没有 `hmcl.microsoft.auth.id` 属性。
- 因此微软登录在 LMCL 构建里不是“偶发失败”，而是被主动禁用。

### 2.2 第三方认证服务器（authlib-injector）

`CreateAccountPane.java:355-360` 的条件近似为：

```java
if (!IntegrityChecker.isOfficial()
        && !(factory instanceof OfflineAccountFactory)) {
    // 显示 unofficial.hint
}
```

LMCL 未签名，`isOfficial()` 恒 false，所以：

- 微软账户：被 `.snapshot` + `unofficial.hint` 双重拦截；
- 第三方 authlib 服务器：账户可以创建，但页面顶部出现“你正在使用非官方构建的 HMCL”；
- 离线账户：因为是 `OfflineAccountFactory`，反而没有警告。

`IntegrityChecker.isOfficial()` 的定义（`HMCL/src/main/java/org/jackhuang/hmcl/upgrade/IntegrityChecker.java:142`）近似为：

```java
return isSelfVerified()
        || (Metadata.GITHUB_SHA != null
            && Metadata.BUILD_CHANNEL.equals("nightly"));
```

LMCL 是社区构建，既没有上游签名，也不是 nightly，因此结果必然为 false。

### 2.3 为什么不能只删警告或把 `isOfficial()` 改成 true

- 只删 `unofficial.hint`：微软登录仍被空 client id 禁用，刷新仍被 `AccountListItemSkin` 禁用。
- 把 `isOfficial()` 改成 true：会篡改“官方构建”的完整性语义，未来任何依赖它的校验都会被误导；这是错误的安全模型。
- 复用 HMCL 官方 client id：不可行，也不应做。OAuth client id 与重定向 URI、应用注册主体绑定，冒用会造成安全和合规问题。

正确做法：

1. 为 LMCL 注册自己的 Microsoft Entra 应用，配置 `MICROSOFT_AUTH_ID`；
2. 在账户 UI 中用 LMCL 自己的构建身份判断替代 `isOfficial()` 警告；
3. `IntegrityChecker` 保持原语义，不全局改 true。

### 2.4 微软 OAuth 应用注册要求

根据当前实现（`OAuthServer.java`、`OAuth.java`、`MicrosoftService.java`）：

- 授权码流程使用 `client_id`、`redirect_uri`、`scope`、PKCE S256；
- 回环重定向地址为 `http://localhost:<port>/auth-response`；
- 设备码流程使用同一 client id；
- scope 为 `XboxLive.signin offline_access`；
- 授权端点使用 `login.live.com`，设备码/令牌端点使用 `login.microsoftonline.com/consumers`。

因此 Entra 应用至少需要：

> 注意：当前实现走的是 `login.live.com` 的 Microsoft 账户 OAuth 端点，不是纯 Entra v2 端点。注册后必须先用测试账户跑通授权码流程和设备码流程；如果新应用无法使用 `login.live.com`，就需要同步评估改用 Entra v2 端点并修改 OAuth 实现，不能假设“填了 client id 就一定能登录”。

- 支持个人 Microsoft 账户；
- 注册为 public client（不要客户端密钥）；
- 允许 device code / public client flows；
- 回环重定向注册 `http://localhost/auth-response`。Entra 对 localhost 匹配时忽略端口、区分路径，因此 `http://localhost:<port>/auth-response` 可以命中；可同时加 `http://localhost`；
- 不申请与登录无关的敏感权限。

#### 2.4.1 注册步骤（2026-10 核对）

> 结论先行：Entra 应用只解决 Microsoft/Xbox OAuth 的前半段。新建 Azure 应用还必须通过 Minecraft AppID 审核，否则 `https://api.minecraftservices.com/authentication/login_with_xbox` 会返回 **403 Invalid app registration**。两步都完成，微软登录才能进游戏。

**第 1 步：创建 Entra 应用**

1. 打开 [Microsoft Entra 管理中心](https://entra.microsoft.com/)，进入 **Applications → App registrations → New registration**。按官方 [quickstart](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)，需要一个有有效 Azure 订阅、能创建应用注册的账号；应用注册本身不产生资源费用。
2. 名称填 **`Lumina Launcher`** 或 **`LMCL`**。不要用 `Lumina Minecraft Launcher`：Minecraft AppID 审核明确要求新应用名称不得包含 Mojang、Minecraft、Microsoft、Live、Xbox、Discord、Hypixel。
3. Supported account types 选 **Personal Microsoft accounts only**（对应 `consumers` 租户；`XboxLive.signin` 不能用组织租户或 `common`）。
4. 注册后到 **Overview** 复制：
   - **Application (client) ID**：填进 LMCL 的 `MICROSOFT_AUTH_ID`；
   - **Directory (tenant) ID**：AppID 审核表要填。
5. 不需要创建 client secret。LMCL 是 public client，使用 PKCE + 回环回调。

> 常见前置问题：用个人 Microsoft 账户登录 `entra.microsoft.com` / `portal.azure.com` 时若提示“所选用户帐户在租户 Microsoft Services 中不存在……应用程序 74658136-14ec-4630-ad9b-26e160ff0fc6”，说明这个账户还没有加入任何可管理的 Entra 租户，门户落到了 Microsoft 自己的 `Microsoft Services` 租户。`74658136-...` 是 Azure 门户前端（ADIbizaUX）的应用 ID，不是 LMCL 要注册的应用；不要把个人账户加进这个租户。解决办法是先注册一个免费 Azure 账户/创建自己的租户，或改用已有租户的工作/学校账户，再用 `https://portal.azure.com/<租户域名>` 直接进入。创建租户的官方步骤见 [Quickstart: Create a new tenant](https://learn.microsoft.com/en-us/entra/fundamentals/create-new-tenant)；免费/试用租户不能在 Entra 管理中心再建新租户，需先注册 [Azure 免费账户](https://azure.microsoft.com/pricing/purchase-options/azure-account)。

**第 2 步：配置 Authentication**

1. **Authentication → Add a platform → Mobile and desktop applications**。
2. Redirect URI 填 **`http://localhost/auth-response`**；如果门户允许，再加一条 `http://localhost`。规则见 [Redirect URI restrictions](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url)：localhost 允许 HTTP，匹配时忽略端口、区分路径，不能带 query/fragment。
3. 展开 **Advanced settings**，把 **Allow public client flows** 设为 **Yes**（设备码流程需要）；Save。
4. 参考官方 [桌面应用配置](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration)。

**第 3 步：API 权限**

- LMCL 运行时请求的 scope 是 `XboxLive.signin offline_access`；`offline_access` 用于 refresh token。
- 一般不需要在 **API permissions** 里额外添加 Microsoft Graph 权限，也不要在门户里找“Minecraft API”勾选项；真正的 Minecraft API 授权是第 4 步的人工 allowlist。
- 如果门户的 **APIs my organization uses** 里能添加 Xbox Live 的 `XboxLive.signin`，可以加；但它不能替代 AppID 审核。

**第 4 步：提交 Minecraft AppID 审核**

1. 打开 [Minecraft AppID review](https://aka.ms/mce-reviewappid)。
2. 按表填写：
   - 有效邮箱：必须与 Azure Portal 的联系人信息可交叉核对，审核结果也发到这里；
   - Application Name：与 Entra 注册名完全一致，且不含上述禁用词；
   - Application (Client) ID：Entra 的 GUID；
   - Tenant ID：Directory (tenant) ID；
   - 官网/域名：`https://www.kerzeftu.top/lmcl`；
   - 用途说明：说明 LMCL 是 GPLv3 开源 Minecraft Java 版启动器，只用 Microsoft OAuth 登录并校验游戏所有权，不收集密码、不绕过所有权检查；附仓库/官网；
   - 确认 EULA 与 Usage Guidelines。
3. 审核是人工、通常按周处理，不要重复提交；被拒时按邮件要求补充材料，不要假设“再交一次就过”。
4. 官方背景：[Minecraft 帮助文章](https://help.minecraft.net/hc/en-us/articles/16254801392141)、[Minecraft Wiki: Microsoft authentication](https://minecraft.wiki/w/Microsoft_authentication)。第三方报告（如 [Microsoft Q&A 5768276](https://learn.microsoft.com/en-us/answers/questions/5768276/how-to-get-xboxlive-signin-permission-for-azure-ap)）也显示：即使 OAuth/Xbox/XSTS 都成功，未 allowlist 时 Minecraft Services 仍 403。

**第 5 步：接入与验证**

```bash
export MICROSOFT_AUTH_ID='<client id>'
VERSION_ROOT=1.0 BUILD_NUMBER=0 ./gradlew :HMCL:makeExecutables
```

- 用个人 Microsoft 账户（拥有 Minecraft Java 版）分别测试授权码流程和设备码流程。
- 成功标志：`login_with_xbox` 返回 200，随后 `entitlements/mcstore` 与 `minecraft/profile` 正常。
- 常见错误：
  - `AADSTS50011`：redirect URI 不匹配，检查 `http://localhost/auth-response`；
  - `AADSTS7000218`：没有开启 public client flows；
  - `403 Invalid app registration`：AppID 未 allowlist，或 client id/租户不对；
  - XSTS `2148916238`：儿童账户需要加入家庭组，不是应用配置错误。
- 旧 HMCL 账户的 refresh token 绑定旧 client id，LMCL 首次使用需要重新授权一次。

**未通过审核前**

- 不显示“看起来能点、点了却提示官方 HMCL”的按钮；
- 隐藏/禁用微软登录入口，文案写明“微软登录待授权”；
- 不链接到 HMCL 官方下载页，也不承诺微软登录可用。

#### 2.4.2 搁置记录（2026-10-23）

> User said (m18878): “这个先搁置，你要记下来，然后继续看重构UI这件事”。

**状态：暂停，等待用户完成 Azure/Entra 租户准备。** 在用户提供新的 `MICROSOFT_AUTH_ID` 之前，不实施 P0 的微软登录开关改造，也不把品牌清理的 P0 部分当成可发布状态。

**已确认的阻塞点**

- 用户用个人 Microsoft 账户登录 `entra.microsoft.com` / `portal.azure.com` 时返回：
  “所选的用户帐户在租户‘Microsoft Services’中不存在，且无法访问该租户中的应用程序‘74658136-14ec-4630-ad9b-26e160ff0fc6’。需要先将该帐户添加为该租户的外部用户。请使用其他帐户。”
- 该账户目前没有可管理的 Entra 租户；门户落到了微软内部 `Microsoft Services` 租户。
- `74658136-14ec-4630-ad9b-26e160ff0fc6` 是 Azure 门户前端（ADIbizaUX）的应用 ID，不是 LMCL 应用，也不应把个人账户加进该租户。

**恢复时必须完成的清单**

1. 注册 [Azure 免费账户](https://azure.microsoft.com/pricing/purchase-options/azure-account) 或使用已有租户，获得可管理的 Entra 目录；
2. 进入正确的租户（`https://portal.azure.com/<tenant-domain>`，或在 **Directories + subscriptions** 切换），确认 **Tenant ID**；
3. 按 §2.4.1 创建 Entra 应用：**Personal Microsoft accounts only**、redirect URI `http://localhost/auth-response`、**Allow public client flows = Yes**、不创建 client secret；
4. 记录 **Application (client) ID** 与 **Directory (tenant) ID**；
5. 提交 [Minecraft AppID 审核](https://aka.ms/mce-reviewappid)，等待 allowlist 结果；
6. 把 client id 交给开发侧，设置 `MICROSOFT_AUTH_ID` 并重新构建，分别验证授权码流程与设备码流程。

**搁置期间的产品行为**

- 不设置 `MICROSOFT_AUTH_ID`，不发布带微软登录承诺的包；
- 微软登录入口保持隐藏/禁用，文案说明“微软登录待授权”，不要复用 HMCL 的“非官方构建”提示；
- 第三方 authlib-injector 提示的修复（P0 第二条）不依赖 OAuth，可以独立实施，但当前也随品牌方案一起暂停；
- 其他 UI/视觉工作不受影响，继续按 M3 方案推进。

**下次恢复入口**

- 本文件 §2.4.1 的注册步骤；
- 用户提供 client id / tenant id 后，从阶段 A 的 `MicrosoftAccountLoginPane`、`CreateAccountPane` 开始实施；
- 构建断言：`unzip -p HMCL/build/libs/HMCL-1.0.0.jar META-INF/MANIFEST.MF | grep '^hmcl.microsoft.auth.id: '`。

建议的构建接入：

```bash
# 发布/测试构建
export MICROSOFT_AUTH_ID='<LMCL 自己的 client id>'
VERSION_ROOT=1.0 BUILD_NUMBER=0 ./gradlew :HMCL:makeExecutables
```

并在构建后断言：

```bash
unzip -p HMCL/build/libs/HMCL-1.0.0.jar META-INF/MANIFEST.MF \
  | grep '^hmcl.microsoft.auth.id: '
```

如果暂时没有 client id：

- 不显示一个“看起来能点、点了却提示官方 HMCL”的按钮；
- 用 LMCL 专属说明：“此构建未配置微软登录，请使用离线账户或第三方认证服务器”，并隐藏/禁用微软入口；
- 不链接到 HMCL 官方下载页。

### 2.5 旧账户迁移风险

HMCL 官方构建创建的微软账户，其 refresh token 可能绑定原 client id。LMCL 使用新 client id 后：

- 旧账户可能无法直接刷新，需要重新登录；
- 不应在未验证的情况下承诺“无缝迁移”；
- 发布说明要写清楚：微软账户需要重新授权一次。

---

## 3. 全量审计结果

审计范围：

```text
HMCL/src/main/java
HMCL/src/main/resources/assets/lang
HMCL/src/main/resources/assets/themes
HMCL/src/main/resources/assets/microsoft_auth.html
HMCL/src/main/resources/assets/about
HMCLCore/src/main/java
HMCLBoot/src/main/java
HMCLBoot/src/main/resources
```

扫描方式：

```bash
grep -RIn --include='*.java' -E 'HMCL|Hello Minecraft' \
  HMCL/src/main/java HMCLCore/src/main/java HMCLBoot/src/main/java

grep -nE '^[^#]*=.*(HMCL|Hello Minecraft|hmcl)' \
  HMCL/src/main/resources/assets/lang/I18N*.properties

grep -RIn -E 'hmcl\.huangyuhui\.net|docs\.hmcl\.net|github\.com/HMCL-dev' \
  HMCL/src/main HMCLCore/src/main HMCLBoot/src/main
```

### 3.1 P0：账户、协议、OAuth 页面

| # | 文件/资源 | 现状 | 处理 |
|---|---|---|---|
| A1 | `MicrosoftAccountLoginPane.java:144-149` | client id 空 → `account.methods.microsoft.snapshot` + 禁用登录 | 配置自有 client id；无 id 时改用 LMCL 专属说明 |
| A2 | `MicrosoftAccountLoginPane.java:153-157` | `!isOfficial()` → `unofficial.hint` | 移除该上游警告；需要时改为中性安全提示 |
| A3 | `CreateAccountPane.java:355-360` | authlib 等非离线工厂也显示 `unofficial.hint` | 移除；不按构建签名拦截账户类型 |
| A4 | `Accounts.java:578` | `MicrosoftAuthenticationNotSupportedException` → `.snapshot` | 保留异常处理，但文案改为 LMCL 构建未配置 |
| A5 | `AccountListItemSkin.java:127` | client id 空 → 禁用刷新 + `.snapshot.tooltip` | client id 配置后自然恢复；文案同步替换 |
| A6 | `Controllers.java:495` | 首次启动协议链接 `Metadata.EULA_URL` = HMCL EULA | 换成 LMCL 条款；未就绪时不得把 HMCL EULA 当 LMCL 协议 |
| A7 | `assets/microsoft_auth.html:24` | `<title>Hello Minecraft! Launcher</title>` | 只改 `<title>` 为 LMCL；文件头 GPL 版权注释保留 |
| A8 | `HMCL/build.gradle.kts:32/166` | 空 `MICROSOFT_AUTH_ID` | 接入 LMCL 自有 client id；构建断言 |

### 3.2 P1：用户直接可见的品牌文案/页面

| # | 文件/资源 | 现状 | 处理 |
|---|---|---|---|
| B1 | `PersonalizationPage.java:1227` | 外观 → 启动器字体预览写死 `Hello Minecraft! Launcher` | 改为 `Metadata.FULL_NAME` / 社区产品名 |
| B2 | `HelpPage.java` | 抓取 `Metadata.DOCS_URL + "/index.json"`，即 `docs.hmcl.net`；无兜底 | 用 LMCL 帮助页替代；或明确标注“上游 HMCL 文档”并做失败兜底 |
| B3 | `FeedbackPage.java:50/54/64` | HMCL B 站群、HMCL Discord、HMCL GitHub issue | 改为 LMCL 官网/邮箱/仓库；没有仓库时隐藏 issue 入口 |
| B4 | `DecoratorSkin.java:263` | 帮助按钮打开 `Metadata.CONTACT_URL`（HMCL 文档） | 指向 LMCL 帮助/反馈 |
| B5 | `CrashWindow.java:56`、`GameCrashWindow.java:469` | 联系按钮打开 HMCL 文档 | 指向 LMCL 反馈 |
| B6 | `MainPage.java:130-170` | `Metadata.isNightly()` 对社区构建为 true，显示 nightly 公告；含 HMCL 下载/反馈/Bilibili 链接；关闭按钮只对 dev 持久化 | 社区构建跳过该公告；LMCL 更新走已有 `CommunityUpdateNotifier` |
| B7 | `SettingsPage.java:90-160` | 仍显示上游更新通道、预览、自动显示更新对话框 | 社区构建隐藏该组；把“检查更新”放到关于 LMCL/其他页 |
| B8 | `CommunitySettingsPanel` / 关于 LMCL | 部分法律/上游链接 | 保留上游归因；LMCL 条款单独提供 |
| B9 | 主题 manifest | `HMCL Default`、`Classic HMCL launcher themes`、作者 `HMCL` | 描述改 LMCL 或“上游 HMCL 经典外观”；id 保留 |
| B10 | i18n 主翻译 | 大量 HMCL 文案/链接；见附录 A | 分批替换；见第 5 节 |
| B11 | `logwindow.help`、`logwindow.export_dump.no_dependency` | 指向 HMCL 社区/QQ/Discord | 改为 LMCL 反馈/官网 |
| B12 | `launcher.april_fools.*` | 愚人节文案含 HMCL | 社区构建停用或改 LMCL |
| B13 | `launcher.crash.*`、`exception.*`、`fatal.*` | 含 HMCL 与 `docs.hmcl.net` | 改 LMCL 文案/链接 |
| B14 | `modpack.*`、`settings.*.unsupported` | 含 HMCL 兼容说明 | 保留技术含义，改产品名；明确“上游 HMCL”语义 |

### 3.3 P2：链接、网络身份、日志、引导

| # | 文件/资源 | 现状 | 处理 |
|---|---|---|---|
| C1 | `Metadata.java:50-60` | `DOCS_URL=https://docs.hmcl.net`、`CONTACT_URL`、`CHANGELOG_URL`、`EULA_URL`、`GROUPS_URL` 均指上游 | 优先在消费点改用社区常量；或集中替换为 LMCL 链接 |
| C2 | `Metadata.getSuggestedJavaDownloadLink()` | `docs.hmcl.net/downloads/...` | 改 LMCL 文档或 Adoptium 官方下载 |
| C3 | `HMCLBoot/src/main/java/org/jackhuang/hmcl/Main.java:35` | `DOWNLOAD_PAGE=https://hmcl.huangyuhui.net/download/` | 改 LMCL 官网下载/说明 |
| C4 | `EntryPoint.java:48` | `http.agent = "HMCL/" + VERSION` | 改 `LMCL/<version>`；确认无服务端白名单依赖 |
| C5 | `NetworkUtils.java:46` | 默认 User-Agent fallback `"HMCL"` | 改 LMCL；保留属性覆盖 |
| C6 | `CrashReport.java:55` | `---- Hello Minecraft! Crash Report ----` | 改 LMCL |
| C7 | `Launcher.java:354-357` | 日志标签 `HMCL User Home/...` | 改 LMCL |
| C8 | `LauncherHelper.java:985` | `[HMCL ProcessListener]` | 改 LMCL |
| C9 | `Logger.java:250/254` | 线程名 `HMCL Logger Thread` | 改 LMCL |
| C10 | `UpdateHandler.java:291` | 输出名 `HMCL-<version>.jar` | 自动更新已关闭；如保留手动更新，改 LMCL 并验证 |
| C11 | `IntegrityChecker.java:127` | 错误文案 `Failed to find current HMCL location` | 改 LMCL；仅日志/错误提示 |
| C12 | `YggdrasilServer.java:68-69` | `serverName`/`implementationName` 为 `HMCL` | 可改 LMCL；属协议元数据，需第三方服务器兼容测试 |
| C13 | `ModpackInfoPage.java:195` | `Metadata.DOCS_URL + "/modpack/serverpack.html"` | 改 LMCL 文档或移除链接 |
| C14 | `UpgradeDialog.java:53` | `CHANGELOG_URL + channel...` | LMCL 更新已停用；改 LMCL 更新说明或隐藏 |
| C15 | `TerracottaMetadata.java:113` | 联机反馈指向 `docs.hmcl.net` | 改 LMCL 反馈/说明 |

### 3.4 P3：内部标识、兼容契约、法律归因（不清理或只加别名）

| 类别 | 例子 | 结论 |
|---|---|---|
| Java 包名 | `org.jackhuang.hmcl.*` | 保留；relocate 已定为首发 No-Go |
| 数据/属性 | `hmcl.home`、`hmcl.dir`、`hmcl.version.override`、`hmcl.update_source.override` | 保留；新增 `LMCL_*`/`lmcl.*` 别名时应保证旧名仍可用 |
| 环境变量 | `HMCL_USER_HOME`、`HMCL_LOCAL_HOME`、`HMCL_DEPENDENCIES_DIR`、`HMCL_FONT`、`HMCL_JRES`、`HMCL_LANGUAGE`、`HMCL_FORCE_GPU`、`HMCL_ANIMATION_FRAME_RATE`、`HMCL_UI_SCALE` | 保留兼容；推荐新增 LMCL 别名，不直接删除旧名 |
| URI scheme | `hmcl://` | 保留；可另注册 `lmcl://` 别名，但不要破坏旧链接 |
| modpack 格式 | `HMCLModpackProvider` 的 `"HMCL"` | 保留；这是格式标识，不是产品文案 |
| 主题 id/schema | `hmcl.default`、`hmcl.classic`、manifest schema | 保留 id；只改显示名/作者 |
| 启动脚本 | `assets/HMCLauncher.sh` 的 `_HMCL_*` 变量 | 内部兼容层，不改 |
| GPL 归因 | 源文件版权头 `huangyuhui`、`about/thanks.json`、`about/deps.json`、上游 GitHub 链接、`about.copyright.statement` | 保留；不得删除或伪装成 LMCL 原创 |
| 迁移语义 | `community.settings.data.*` 的“官方 HMCL 数据导入” | 保留并明确“上游 HMCL”来源 |
| 上游 JVM 参数 | `community.jvm.*` 的 `Follow HMCL` | 改为“跟随上游 HMCL 参数/上游兼容”，保留技术含义 |

---

## 4. 建议的品牌分层

### 4.1 新增/扩展社区常量

不建议继续把 LMCL 的链接散落在上游 `Metadata` 里。建议在 `CommunityMetadata` 或新的 `CommunityBranding` 中集中定义：

```java
public static final String PRODUCT_NAME = "LMCL";
public static final String PRODUCT_FULL_NAME = "Lumina Minecraft Launcher";
public static final String WEBSITE = "https://www.kerzeftu.top/lmcl";
public static final String DOCS_URL = "";        // 官网文档就绪后填入，空则隐藏入口
public static final String FEEDBACK_URL = "";    // 官网反馈/邮箱就绪后填入
public static final String TERMS_URL = "";       // LMCL 用户协议/免责声明
public static final String CHANGELOG_URL = "";   // 发布说明
public static final String UPSTREAM_URL = "https://github.com/HMCL-dev/HMCL";
public static final String UPSTREAM_DOCS_URL = "https://docs.hmcl.net";
```

原则：

- **空值 = 不显示入口**，不要回退到 HMCL 链接；
- 上游链接只用于“基于 HMCL / 上游文档 / 开源归因”的明确标注；
- 页面标题、帮助、反馈、崩溃联系等消费 `CommunityMetadata`，不再直接读 `Metadata.DOCS_URL`。

### 4.2 构建身份判断

新增：

```java
public static boolean isCommunityBuild() {
    return true; // LMCL 构建
}

public static boolean showUpstreamBuildWarning() {
    return false; // 不再对 LMCL 用户显示“非官方 HMCL”
}
```

只替换两处账户 UI 的 `!IntegrityChecker.isOfficial()` 分支，不改 `IntegrityChecker` 本身。

### 4.3 账户提示策略

| 场景 | 当前 | 目标 |
|---|---|---|
| 已配置 client id，微软登录 | 可用，但顶部有 `unofficial.hint` | 可用，无“非官方 HMCL”警告 |
| 未配置 client id，微软登录 | 显示 `.snapshot` 并禁用，诱导下载 HMCL | LMCL 专属“未配置微软登录”说明；隐藏/禁用入口，不链 HMCL |
| 第三方 authlib 登录 | 显示 `unofficial.hint` | 无构建警告；可选中性“仅连接可信服务器”提示 |
| 微软账户刷新 | client id 空时禁用 | 配置后恢复；旧 token 可能需重新登录 |
| 离线账户 | 无警告 | 保持无警告 |

---

## 5. 文件级改造清单

### 5.1 构建与 OAuth

| 文件 | 改动 |
|---|---|
| `HMCL/build.gradle.kts:32` | 保留 `MICROSOFT_AUTH_ID` 环境变量；可增加 Gradle 属性回退，便于本地构建 |
| `HMCL/build.gradle.kts:166` | 继续写入 `hmcl.microsoft.auth.id`；发布构建必须非空断言 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane.java` | 改文案/警告逻辑；无 client id 时 LMCL 专属说明 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/CreateAccountPane.java` | 移除 `!isOfficial()` 的 authlib 警告；保留中性安全提示（可选） |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/AccountListItemSkin.java` | 文案同步；client id 配置后不再禁用刷新 |
| `HMCL/src/main/resources/assets/microsoft_auth.html` | 只改 `<title>` 为 LMCL；GPL 版权注释保留；可把产品名做成占位符，由 `OAuthServer` 注入 |
| `HMCL/src/main/java/org/jackhuang/hmcl/setting/Accounts.java` | `.snapshot` 异常文案同步；不改异常语义 |

### 5.2 外观与首页

| 文件 | 改动 |
|---|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/PersonalizationPage.java:1227` | `new Label(Metadata.FULL_NAME)`，不要写死 HMCL |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java:130-170` | 社区构建跳过 nightly 公告；保留上游逻辑供上游构建 |
| 首页 `HomePane` / `CommunityUpdateNotifier` | 继续作为 LMCL 更新提示入口；不显示 HMCL 下载/反馈链接 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/SettingsPage.java:90-160` | 隐藏上游 `updatePane`、`previewPane`、`disableAutoShowUpdateDialogPane`；可替换为 LMCL 更新说明 |

### 5.3 帮助、反馈、关于

优先**新建社区页面**，而不是继续改上游 `HelpPage`/`FeedbackPage`：

| 新文件 | 职责 |
|---|---|
| `community/ui/help/CommunityHelpPage.java` | 静态 LMCL 帮助分类；官网/文档未配置时显示“建设中”，不请求 `docs.hmcl.net/index.json` |
| `community/ui/help/CommunityFeedbackPage.java` | 官网、邮箱/反馈表单、GitHub/Gitee（仓库就绪时）；无入口则隐藏；不出现 HMCL QQ/Discord |
| `OtherPage` | `helpTab`/`feedbackTab` 的 supplier 改指向社区页面 |

关于页：

- `AboutLMCLPage` 保留“基于 HMCL v3.16.4”和上游 GitHub 归因；
- 法律卡不再使用 `Metadata.EULA_URL`，改用 LMCL 条款；
- 上游 `AboutPage` 是死代码，可保留/删除，不影响用户；如删除，属于上游文件清理，单独提交。

### 5.4 链接、日志、协议

| 文件 | 改动 |
|---|---|
| `Metadata.java` | 集中替换 `DOCS_URL`/`CONTACT_URL`/`CHANGELOG_URL`/`EULA_URL`/`GROUPS_URL` 前，先列出所有消费点；优先改消费点，避免路径不兼容 |
| `DecoratorSkin.java:263`、`CrashWindow.java:56`、`GameCrashWindow.java:469` | 联系/帮助链接改 LMCL |
| `Controllers.java:495` | 协议链接改 LMCL 条款 |
| `ModpackInfoPage.java:195`、`UpgradeDialog.java:53`、`TerracottaMetadata.java:113` | 链接改 LMCL 或隐藏 |
| `HMCLBoot/src/main/java/org/jackhuang/hmcl/Main.java:35` | 启动失败引导改 LMCL 官网 |
| `EntryPoint.java:48`、`NetworkUtils.java:46` | User-Agent 改 LMCL |
| `CrashReport.java:55` | 崩溃报告头改 LMCL |
| `Launcher.java:354-357`、`LauncherHelper.java:985`、`Logger.java:250/254` | 日志标签/线程名改 LMCL |
| `YggdrasilServer.java:68-69` | 评估后改 LMCL；做第三方服务器兼容测试 |
| 主题 manifest | 描述/作者改 LMCL 或“上游 HMCL 经典外观”；id 不变 |

### 5.5 i18n

三份主翻译：

- `HMCL/src/main/resources/assets/lang/I18N.properties`
- `HMCL/src/main/resources/assets/lang/I18N_zh.properties`
- `HMCL/src/main/resources/assets/lang/I18N_zh_CN.properties`

其他语言（`I18N_ar/es/ja/lzh/ru/uk`）建议对**产品名与 URL**做机械替换，新增文案只保证 en/zh/zh_CN，其他语言回退英文；不要假装已人工翻译。

必须处理的高优先级 key：

```text
account.methods.microsoft.snapshot
account.methods.microsoft.snapshot.tooltip
account.methods.microsoft.snapshot.website
unofficial.hint
account.storage.read_only
account.injector.server.storage.read_only
account.move_to_portable
logwindow.help
logwindow.export_dump.no_dependency
launcher.crash.*
launcher.update_java
launcher.april_fools.*
exception.*
fatal.*
game.crash.reason.*
launch.advice.*
modpack.type.manual.warning
settings.game.*.unsupported
settings.launcher.allow_auto_agent*
settings.file.force_write.confirm
update.channel.*
update.preview*
warning.*
contact.chat.qq_group.statement
download.curseforge.unavailable
sponsor.hmcl
```

保留/改写为“上游 HMCL”语义的 key：

```text
community.settings.data.*        # 导入官方 HMCL 数据
community.jvm.*                  # 跟随上游 HMCL 参数
community.about.*                # 基于 HMCL / GPL 归因
about.copyright.statement
about.thanks_to.*
about.dependency.*
```

替换规则：

| 原文 | 目标 |
|---|---|
| `Hello Minecraft! Launcher` | `Lumina Minecraft Launcher` |
| 产品名 `HMCL`（用户可见） | `LMCL` |
| `https://hmcl.huangyuhui.net/download` | LMCL 官网/下载页；未就绪则移除链接 |
| `https://docs.hmcl.net/help.html` | LMCL 帮助/官网；未就绪则显示“文档建设中” |
| `https://docs.hmcl.net/eula/hmcl.html` | LMCL 条款；未就绪不得冒充 |
| `https://docs.hmcl.net/changelog/` | LMCL 发布说明 |
| `https://github.com/HMCL-dev/HMCL/issues/...` | LMCL 反馈入口；仓库未建则隐藏 |
| HMCL QQ 群 / Discord / B 站群 | 删除或改 LMCL 社区入口 |
| 上游 GitHub（归因） | 保留，明确“上游 HMCL” |

---

## 6. 分阶段实施

### 阶段 A：账户与法律阻塞（P0）

1. 注册 LMCL Microsoft Entra 应用，拿到 client id；
2. 接入 `MICROSOFT_AUTH_ID`，构建 manifest 断言；
3. 替换微软登录页的 `.snapshot`/`unofficial.hint` 逻辑；
4. 移除 authlib 的 `!isOfficial()` 警告；
5. OAuth 回调网页标题/注释改 LMCL；
6. 协议链接改 LMCL 条款（或先做本地条款页，不能再指 HMCL EULA）。

验收：

- 新构建 manifest 含 client id；
- 微软登录可打开授权页并完成回环回调；
- 设备码流程可用；
- 第三方 authlib 服务器登录无“非官方 HMCL”；
- 离线账户不受影响；
- 旧微软账户重新授权提示明确。

### 阶段 B：用户可见 UI（P1）

1. 外观字体示例改 LMCL；
2. 帮助/反馈换成社区页面；
3. 首页 nightly 公告对社区构建关闭；
4. 设置页隐藏上游更新区；
5. 崩溃/日志帮助链接改 LMCL；
6. 主题描述/作者；
7. 三份主 i18n 文案清理。

### 阶段 C：链接与网络身份（P2）

1. `Metadata` URL 消费点清理；
2. Boot 下载页、Java 下载建议；
3. User-Agent、日志标签、崩溃头、进程前缀；
4. OAuth/更新/联机相关链接；
5. 其他语言机械替换。

### 阶段 D：兼容与归因（P3）

1. 新增 `LMCL_*`/`lmcl.*` 别名（保留 HMCL 旧名）；
2. `YggdrasilServer` 元数据改名并兼容测试；
3. 主题 id/schema、modpack ID、包名、`hmcl://` 保持；
4. GPL 头、thanks/deps、上游链接保留；
5. 清理死代码/无引用 key（可选）。

### 6.1 侵入预算与批准点

当前 `docs/00-整体要求.md` §4.2 的固定预算是 **13 处上游 `.java` 侵入**。本方案中：

**复用已有侵入点**

| 文件 | 现有编号 | 本方案用途 |
|---|---:|---|
| `HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java` | #1 | 产品 URL/文档/联系/协议常量 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java` | #2 | 社区构建跳过 nightly 公告 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/LauncherSettingsPage.java` | #8 | 设置 tab 接入（不新增 tab） |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/Controllers.java` | #9 | `hmcl://settings/feedback` 目标 |
| `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/PersonalizationPage.java` | #12 | 字体预览示例文字 |

**P0/P1 最小新增侵入（需书面批准后登记）**

| 候选编号 | 文件 | 改动 | 是否可避免 |
|---:|---|---|---|
| #14 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane.java` | 去掉 `!isOfficial()` 警告；空 client id 改 LMCL 文案 | 否；这是登录页本身 |
| #15 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/account/CreateAccountPane.java` | 去掉 authlib 的 `!isOfficial()` 警告 | 否；这是账户创建页 |
| #16 | `HMCL/src/main/java/org/jackhuang/hmcl/ui/main/SettingsPage.java` | 社区构建隐藏上游更新组 | 可用新页面替代，但会复制大量设置区，维护成本更高 |
| #17 | `HMCLBoot/src/main/java/org/jackhuang/hmcl/Main.java` | 启动失败下载页改 LMCL；顺带解决既有候选 #15 的旧 Java 链接 | 可与旧 Java 链接修复合并为同一处 |

> 若四项全部批准，预算从 13 增至 **17**。执行前必须同步修改 `docs/00-整体要求.md` §4.2、README 决策表、审查记录，并在提交说明中写明“用户批准”。

**P2 内部标识（建议单独批准，不混在本次 UI 修复）**

`EntryPoint.java`、`NetworkUtils.java`、`CrashReport.java`、`Launcher.java`、`LauncherHelper.java`、`Logger.java`、`UpdateHandler.java`、`IntegrityChecker.java`、`YggdrasilServer.java` 等若全部改名，会再增加约 9 处侵入，且多数只在日志/网络头中出现。建议：

- 先完成 P0/P1 用户可见部分；
- P2 作为独立“内部标识与日志”批次，逐项批准；
- 若预算不允许，保留 HMCL 内部标识并在文档中说明“上游引擎兼容标识”，不把它们当作产品品牌展示。

**不计入 `.java` 侵入的资源改动**

- `assets/lang/I18N*.properties`；
- `assets/themes/*/manifest.json`；
- `assets/microsoft_auth.html`；
- `assets/about/*`；
- 新增社区 Help/Feedback 页面与 `OtherPage` 接线。

这些文件仍需在变更记录中登记，但不占用 13 处 Java 侵入预算。

### 工期估算

| 阶段 | 工期 | 前置 |
|---|---|---|
| A 账户/法律 | 1–2 人日 + Entra 审核等待 | 自有 client id、条款文本 |
| B UI 文案/页面 | 2–3 人日 | 帮助/反馈内容 |
| C 链接/网络身份 | 1–2 人日 | 官网/文档 URL |
| D 兼容/归因/清理 | 1 人日 + 回归 | 无 |
| 合计 | 5–8 人日 | 不含法务审阅与官网内容制作 |

---

## 7. 验收标准

### 7.1 静态验收

- `grep -RIn 'Hello Minecraft' HMCL/src/main` 只剩 GPL/归因白名单；
- 用户可见 i18n 中 `HMCL` 只剩明确标注“上游 HMCL”的条目；
- 不再出现 `docs.hmcl.net`、`hmcl.huangyuhui.net`、HMCL QQ/Discord 作为 LMCL 求助入口；
- `:HMCL:checkTranslations` 通过；
- 构建 manifest 断言 `hmcl.microsoft.auth.id` 非空（发布构建）。

### 7.2 手工验收（Windows）

1. 全新 `%APPDATA%/LMCL` 启动，首页/侧栏/标题均为 LMCL；
2. 微软账户：打开授权页、完成登录、重启后刷新、退出/重新登录；
3. 设备码登录；
4. 第三方 authlib 服务器登录，无“非官方 HMCL”；
5. 离线账户；
6. 设置 → 外观 → 启动器字体预览为 LMCL；
7. 设置页无 HMCL 更新通道/预览/自动弹窗；
8. 帮助/反馈/关于：链接可打开或明确“建设中”，无 HMCL 社区入口；
9. 崩溃窗口/日志窗口帮助入口指向 LMCL；
10. 无 Java 启动引导、首次协议对话框；
11. 主题列表描述/作者；
12. 断网启动：帮助页不崩、不空白转圈。

### 7.3 回归

- `:HMCL:compileJava`
- `:HMCL:test`（社区测试 + 全量）
- `:HMCL:checkTranslations`
- `:HMCL:makeExecutables`
- 包内 `SHA256SUMS` 校验
- `Main-Class` 仍为 `CommunityBootstrap`
- exe stub 前缀 + jar 尾部结构不变

---

## 8. 风险与回退

| 风险 | 影响 | 缓解 |
|---|---|---|
| Entra 应用注册延迟/审核 | 微软登录不能发布 | 先 hide 微软入口并明确说明；client id 到位后开启 |
| 旧微软账户 token 绑定旧 client id | 刷新失败 | 发布说明要求重新登录；捕获错误给 LMCL 文案 |
| 移除 `unofficial.hint` 被误解为“官方认证” | 安全误导 | 保留中性安全提示，不声称 Mojang 官方 |
| 帮助页改静态内容后信息不足 | 用户找不到答案 | 提供“上游 HMCL 文档（仅供参考）”次级链接，明确非 LMCL |
| 批量替换误伤 GPL/兼容标识 | 法律/兼容问题 | 白名单 + 代码审查；包名/格式/协议字段不动 |
| `YggdrasilServer` 改名影响第三方服务器 | 登录异常 | 先测试主流 authlib 服务；必要时保留 HMCL 字段 |
| 用户协议未就绪 | 法律风险 | 发布前必须提供 LMCL 条款；期间不把 HMCL EULA 当 LMCL 协议 |
| 其他语言机械替换产生语法问题 | 文案质量 | 只保证 en/zh/zh_CN；其他语言标注未人工校对 |

回退策略：

- 账户逻辑改动集中在两处 UI + 构建变量，可按 commit 回退；
- 帮助/反馈新页面独立，回退只需改 `OtherPage` supplier；
- 品牌常量集中在 `CommunityMetadata`，可逐项关闭入口；
- 不修改包名/格式/协议字段，避免不可逆兼容风险。

---

## 9. 待确认

1. **Microsoft Entra client id**：是否已有 LMCL 应用？没有则先注册；这是 P0 前置。
2. **LMCL 官网文档/条款/反馈 URL**：是否已有页面？没有则先隐藏入口，不能回退到 HMCL。
3. **帮助内容**：是否接受先放“LMCL 快速上手 + 上游 HMCL 文档（明确标注）”？
4. **反馈渠道**：官网表单、邮箱、GitHub/Gitee 仓库何时可用？仓库为空时隐藏 issue。
5. **用户协议**：LMCL 自己的条款由谁提供？发布前必须完成。
6. **主题命名**：显示为“LMCL 默认/经典”还是“上游 HMCL 经典外观（LMCL 适配）”？建议保留上游归因。
7. **第三方服务器安全提示**：移除官方构建警告后，是否保留中性“仅连接可信服务器”？
8. **其他语言**：是否接受机械替换产品名/URL、其余文案回退英文？

---

## 附录 A：zh_CN 主翻译中的 HMCL 相关 key（审计索引）

> 行号基于当前 `I18N_zh_CN.properties`。英文/繁体对应文件同样需要处理；“保留”表示保留但明确上游语义。

### A.1 账户与构建警告（P0/P1）

| 行 | key | 处理 |
|---|---|---|
| 86 | `account.storage.read_only` | 改 LMCL 文案 |
| 94 | `account.injector.server.storage.read_only` | 改 LMCL 文案 |
| 136 | `account.methods.microsoft.snapshot` | 改为 LMCL 构建未配置登录，不诱导 HMCL |
| 137 | `account.methods.microsoft.snapshot.tooltip` | 同上 |
| 138 | `account.methods.microsoft.snapshot.website` | 改 LMCL 官网或删除 |
| 153 | `account.move_to_portable` | 改 LMCL 文案 |
| 1570 | `unofficial.hint` | 社区构建不再使用；若保留，改中性安全提示 |

### A.2 崩溃、异常、启动建议（P1/P2）

| 行 | key 组 | 处理 |
|---|---|---|
| 251–252 | `crash.NoClassDefFound`、`crash.user_fault` | 文档链接改 LMCL |
| 396 | `download.curseforge.unavailable` | 无引用；删除或改 LMCL |
| 418 | `download.javafx.notes` | 改 LMCL 文案 |
| 425–469 | `exception.*`、`fatal.*` | 产品名/文档链接改 LMCL |
| 504–553 | `game.crash.reason.*` | 产品名/文档链接改 LMCL |
| 572 | `help.doc` | 改 LMCL 帮助入口 |
| 668–686 | `launch.advice.*` | 改 LMCL 文案 |
| 718–719 | `launcher.april_fools.*` | 社区构建停用或改 LMCL |
| 747–750 | `launcher.crash*`、`launcher.update_java` | 改 LMCL 文案/Java 下载 |
| 757–765 | `logwindow.*` | 帮助/QQ/Discord 改 LMCL |
| 831–841 | `modpack.*` | 改 LMCL 文案；保留兼容说明 |
| 1034 | `world.icon...` | 如含 HMCL 改 LMCL |
| 1086 | `repositories.chooser` | 改 LMCL 文案 |
| 1299–1300 | `settings.game.*.unsupported` | 保留“上游 HMCL 不支持”语义或改 LMCL |
| 1331 | `settings.game_directories.read_only` | 改 LMCL 文案 |
| 1334–1335 | `settings.launcher.allow_auto_agent*` | 改 LMCL 文案 |
| 1426 | `settings.file.force_write.confirm` | 改 LMCL 文案 |
| 1449 | `sponsor.hmcl` | 无引用；删除或改 LMCL |
| 1460–1505 | `terracotta.*` | 改 LMCL 文案/链接 |
| 1558 | `theme_pack.file` | 改 LMCL 文案 |
| 1576–1583 | `update.channel.*` | 社区构建隐藏上游更新区；文案不显示 HMCL |
| 1588 | `update.disable_auto_show_update_dialog.subtitle` | 隐藏或改 LMCL |
| 1599–1600 | `update.preview*` | 隐藏或改 LMCL |
| 1655–1656 | `warning.*` | 改 LMCL 文案 |

### A.3 应保留的上游语义（P3）

| 行/范围 | key 组 | 原因 |
|---|---|---|
| 22–53 | `about.*` | GPL 版权/致谢/依赖归因 |
| 1695–1708 | `community.settings.data.*` | “导入官方 HMCL 数据”是迁移语义 |
| 1837–1875 | `community.jvm.*` | “跟随上游 HMCL 参数”是技术语义 |
| 2077–2088 | `community.about.*` | “基于 HMCL”、版权、上游链接 |

### A.4 其他语言

| 文件 | 当前品牌值数量（约） | 处理 |
|---|---:|---|
| `I18N.properties` | 91 | 主英文，完整处理 |
| `I18N_zh.properties` | 96 | 繁体，完整处理 |
| `I18N_zh_CN.properties` | 102 | 简体，完整处理 |
| `I18N_ar.properties` | 60 | 机械替换产品名/URL |
| `I18N_es.properties` | 42 | 机械替换产品名/URL |
| `I18N_ja.properties` | 30 | 机械替换产品名/URL |
| `I18N_lzh.properties` | 56 | 机械替换产品名/URL |
| `I18N_ru.properties` | 27 | 机械替换产品名/URL |
| `I18N_uk.properties` | 43 | 机械替换产品名/URL |

---

## 附录 B：关键代码/资源路径

### 账户与 OAuth

```text
HMCL/src/main/java/org/jackhuang/hmcl/ui/account/MicrosoftAccountLoginPane.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/account/CreateAccountPane.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/account/AccountListItemSkin.java
HMCL/src/main/java/org/jackhuang/hmcl/setting/Accounts.java
HMCL/src/main/java/org/jackhuang/hmcl/game/OAuthServer.java
HMCL/src/main/java/org/jackhuang/hmcl/upgrade/IntegrityChecker.java
HMCL/src/main/resources/assets/microsoft_auth.html
HMCL/build.gradle.kts
```

### 产品文案/页面

```text
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/PersonalizationPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/MainPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/SettingsPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/HelpPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/FeedbackPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/main/AboutPage.java        # 死代码
HMCL/src/main/java/org/jackhuang/hmcl/ui/decorator/DecoratorSkin.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/CrashWindow.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/GameCrashWindow.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/export/ModpackInfoPage.java
HMCL/src/main/java/org/jackhuang/hmcl/ui/UpgradeDialog.java
```

### 元数据/网络/日志

```text
HMCL/src/main/java/org/jackhuang/hmcl/Metadata.java
HMCLCore/src/main/java/org/jackhuang/hmcl/util/io/NetworkUtils.java
HMCL/src/main/java/org/jackhuang/hmcl/EntryPoint.java
HMCL/src/main/java/org/jackhuang/hmcl/Launcher.java
HMCL/src/main/java/org/jackhuang/hmcl/countly/CrashReport.java
HMCL/src/main/java/org/jackhuang/hmcl/game/LauncherHelper.java
HMCLCore/src/main/java/org/jackhuang/hmcl/util/logging/Logger.java
HMCLCore/src/main/java/org/jackhuang/hmcl/auth/offline/YggdrasilServer.java
HMCLBoot/src/main/java/org/jackhuang/hmcl/Main.java
```

### 资源

```text
HMCL/src/main/resources/assets/lang/I18N.properties
HMCL/src/main/resources/assets/lang/I18N_zh.properties
HMCL/src/main/resources/assets/lang/I18N_zh_CN.properties
HMCL/src/main/resources/assets/themes/hmcl.default/manifest.json
HMCL/src/main/resources/assets/themes/hmcl.classic/manifest.json
HMCL/src/main/resources/assets/microsoft_auth.html
HMCL/src/main/resources/assets/about/thanks.json
HMCL/src/main/resources/assets/about/deps.json
HMCLBoot/src/main/resources/assets/lang/boot.properties
```

---

## 附录 C：建议新增/修改的 i18n key

```text
community.branding.microsoft_not_configured
community.branding.microsoft_not_configured.tooltip
community.branding.third_party_notice
community.help.title
community.help.empty
community.help.upstream_notice
community.feedback.title
community.feedback.website
community.feedback.email
community.feedback.repository
community.feedback.empty
community.about.terms
community.about.upstream_docs
community.update.check
community.update.note
community.update.current_version
community.settings.update.disabled
```

要求：

- 三份主语言同步；
- 英文基座必须有值；
- 中文 zh/zh_CN 文案一致；
- 占位符数量一致；
- 不把 HMCL 链接写进新 key。

---

## 附录 D：建议的提交拆分

```text
feat(branding): configure LMCL Microsoft OAuth client id
fix(account): remove upstream unofficial-build warnings for LMCL
fix(branding): use LMCL product name in appearance font preview
feat(help): add community help and feedback pages
fix(settings): hide upstream update controls for LMCL builds
fix(meta): replace HMCL documentation and contact links
chore(branding): update user-agent, logs and crash report headers
chore(compat): add LMCL_* aliases while keeping HMCL_* compatibility
docs: record branding audit and release checklist
```

每个提交都应附：

- 变更文件；
- 用户可见效果；
- 测试/构建结果；
- 是否新增上游侵入点；
- 回退方式。
