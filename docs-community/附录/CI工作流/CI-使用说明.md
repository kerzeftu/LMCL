# Lumina Minecraft Launcher（LMCL） CI 配置说明

> **口径提示**：本目录是 v0.1 随开发包提供的**原始 CI 模板与说明**，保留作对照。
> 实施时以 [步骤 02](../../steps/02-CI三工作流落地.md)（三工作流、易错点、Secrets）与 [步骤 18](../../steps/18-内存优化P2-发布准备.md)（签名、SBOM、灰度、放行、产物改名）为准；与本说明冲突处一律以两篇步骤文档为准（例：版本号已改为 `v3.16.1000` 纯数字段，不再用 `-community.N` 后缀）。
> 本目录 yml：构建/上传阶段用 Gradle 内部名 `HMCL-<version>.*`（工程名不改）；发布阶段在 `dist/` 重命名为 `LMCL-<version>-<arch>.<ext>`（`arch`=win-x86/any/all）。口径以步骤 02 §3.4 与步骤 18 §3 为准，产品名已定 **LMCL**（00 §13 #18）。

三个工作流，覆盖「同步上游 → 验证编译 → 构建发布」全链路。

```
.github/workflows/
├── sync-upstream.yml   定时拉取上游，自动合并，成功开 PR / 冲突开 issue
├── ci.yml              每次 push & PR 验证能否编译
└── build-release.yml   打标签后构建并发布到 GitHub Releases
```

## 一、落地步骤

1. 把 `.github` 整个目录复制到 fork 仓库根目录
2. 在仓库 Settings → Actions → General，把 Workflow permissions 设为 **Read and write**
3. 配置 Secrets（见第三节）
4. 手动跑一次 `Sync Upstream` 验证（Actions → Sync Upstream → Run workflow）

## 二、跟哪个上游分支（重要）

**不要跟 `main`。** HMCL 的 `main` 是 beta 开发分支，官方 README 明确说 beta 版包含额外测试功能、可能不稳定。

建议跟踪 release 分支，例如 `release/3.16`（仓库里确实存在此类分支）。
`sync-upstream.yml` 的默认 `upstream_ref` 已设为 `release/3.16`，按你实际需要改。

## 三、需要的 Secrets

| Secret | 必需 | 说明 |
|---|---|---|
| `MICROSOFT_AUTH_ID` | 视构建要求 | Microsoft OAuth App ID，官方 CI 就在使用 |
| `MICROSOFT_AUTH_SECRET` | 视构建要求 | 配套密钥 |
| `CURSEFORGE_API_KEY` | 否 | CurseForge 对接，可用 `-Dhmcl.curseforge.apikey=` 替代 |

`GITHUB_TOKEN` 不用配，Actions 自动注入。

注意：HMCL 有 `-Dhmcl.microsoft.auth.id=` / `-Dhmcl.microsoft.auth.secret=` 这类 JVM 参数可覆盖。若不想自建 OAuth 应用，可确认上游是否有默认值可沿用。

## 四、GPLv3 第 7 条：改名与版本号

HMCL 的附加条款要求分发修改版时必须**合理修改名称或版本号**，且不得移除版权声明。CI 里这样处理：

- **版本号**：`build-release.yml` 用 `-Pversion=` 传入，标签 `v3.16.1000` 会自动转成 Gradle 版本 `3.16.1000`。采用「上游版本线 + 构建号」的纯数字命名法，一眼能看出基线，且不会被上游 `VersionNumber.parse`/配置迁移误判。
- **软件名**：在源码里一次性改好（HMCL 的名称常量集中在 `Metadata.java`）。改一次即可，后续同步上游不会覆盖——除非上游也动了同一处，届时 sync 会报冲突，正好提醒你复查。

## 五、同步策略：merge 还是 patch

当前配置用的是 **merge 策略**（长期分支 + 定期 merge）。适合改动较小的情况。

如果你的改动确实是「固定几处、长期不变」，**更推荐 patch 策略**：

```bash
# 一次性导出你的改动
git diff upstream/release/3.16..main > patches/community.patch

# 每次上游更新时：从干净的上游分支起新分支，重新 apply
git checkout -b build/3.16.1000 upstream/release/3.16
git apply patches/community.patch   # 冲突点明确、可 review
```

优点：不堆积 merge commit，历史干净，冲突集中在固定几处，且改动本身可以作为独立 patch 被社区审查。
缺点：需要额外维护 patch 文件，上游大改导致 patch 失效时要重新生成。

如果你的改动超过 20 处或分散在很多文件，继续用 merge 更省事。

## 六、配额与成本

| 项目 | 说明 |
|---|---|
| Runner | 只用 `ubuntu-latest`（1x 计费）。macOS 是 10x，Windows 是 2x |
| 免费额度 | 私有仓库 2000 分钟/月；公开仓库免费无限 |
| 单次构建 | 冷启约 5–10 分钟，有 Gradle 缓存后约 3–5 分钟 |
| 定时任务 | GitHub 会停用 **60 天无任何提交** 的仓库的 schedule，需保持仓库活跃 |

**省额度技巧**：`ci.yml` 只对 `main` 的 push 和 PR 触发，且忽略 `.md` 改动；日常开发在分支上，别频繁 push 到 main。

## 七、为什么单 runner 能产 Windows 的 exe

HMCL 的 `makeExecutables` 任务逻辑是：从 jar 里读出 `assets/HMCLauncher.exe` 和 `assets/HMCLauncher.sh` 这两个 stub，把整个 jar 内容追加到 stub 后面。这是纯字节拼接，不涉及平台特定的编译或打包工具。

所以 `ubuntu-latest` 一台机器就能产出 jar / exe / sh / deb 全部产物。
若后续需要 macOS 原生 `.dmg` 安装包，再单独加一个 `macos-latest` job。

## 八、常见坑

- **`java-package: jdk+fx` 不能省**：普通 JDK 不带 JavaFX，构建会失败
- **`xvfb-run` 不能省**：无头 Linux 上 JavaFX 任务需要虚拟显示
- **fork 仓库 Actions 默认可能关闭**：首次需到 Actions 页面手动启用
- **定时任务不跑**：检查仓库 60 天内是否有提交；时区按 UTC 理解（cron `17 18 * * *` = 北京时间次日 02:17）
- **`.github` 目录被上游覆盖**：上游也有同名 workflow，首次同步必冲突。建议第一次合并后删除上游原来的 `gradle.yml`，避免两套 CI 重复跑

## 九、可选扩展

- **多架构产物**：LoongArch / RISC-V 等需要对应架构的 runner，GitHub 官方 runner 不提供，需 self-hosted
- **同步到自有存储**：`build-release.yml` 里预留了 COS 上传的注释示例，可换成 rclone / 任意对象存储
- **自动更新源**：HMCL 支持 `-Dhmcl.update_source.override=`，可指向你自己的更新接口，配合 CI 实现启动器内自动更新
