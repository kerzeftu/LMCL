# LMCL 文档入口（DOCS）

> LMCL 处于半弃坑状态。先读 [作者的话](README.md#作者的话) 和 [暂停时的状态清单](docs/待办总览.md)，再按需查技术文档。
> [旧开发日志](README-history.md)、总纲、步骤、审查、附录和各专项方案均为历史资料。原文件中的计划、路径、包名和验证结论不表示现在仍有发行或支持。上游 HMCL 的文档与许可按归属保留。

## 1. 先读哪本

| 场景 | 先看 |
| --- | --- |
| 用户报 bug / 现象排查 | [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) |
| 不知道改动落在哪一层、找类/入口 | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| 不确定用户要什么、历史误解 | [`USER-UNDERSTANDING.md`](USER-UNDERSTANDING.md) |
| 构建、打包、发版、校验 | [`PUBLISHING.md`](PUBLISHING.md) |
| 文档怎么维护、验证纪律、清理策略 | [`MAINTENANCE.md`](MAINTENANCE.md) |
| 版本发布说明、产品总纲、步骤细节 | [`README.md`](README.md)、[`docs/00-整体要求.md`](docs/00-整体要求.md)、[`docs/steps/`](docs/steps/) |
| 未完成事项、验收状态 | [`docs/待办总览.md`](docs/待办总览.md) |
| 源码核对与历次审查 | [`docs/审查记录-v3.16.4源码核对.md`](docs/审查记录-v3.16.4源码核对.md) |
| 具体方案（材质/实时渲染/下载/JavaFX21 等） | [`docs/修复方案-*.md`](docs/) |

## 2. 核心五件套

| 文件 | 一句话 |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 目录、分层、主页材质与过渡带管线、改动入口 |
| [`USER-UNDERSTANDING.md`](USER-UNDERSTANDING.md) | 用户画像、术语、历史诉求与验收口径 |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | 已知现象 → 根因 → 修法/版本，排查顺序 |
| [`PUBLISHING.md`](PUBLISHING.md) | 版本命名、构建、完整包/补丁包、校验一次 |
| [`MAINTENANCE.md`](MAINTENANCE.md) | 文档台账、版本节奏、验证纪律、清理与提交 |

## 3. 工作纪律

1. 先查文档，**不先 review 代码**；文档能回答的不要重新翻源码。
2. 文档与代码**同批**更新；行为变化当次写进对应文档。
3. **不过度防御、不过度校验**：按 `PUBLISHING.md` 标准验证一次，确认生效就停；不重复写脚本、不堆断言、不搞多轮交叉核对。
4. 视觉结论必须有 Windows 实机证据（用户截图/确认）；WSL2 下只写“代码已改、未实机”。
5. 文档镜像：`lmcl/docs-community/` 与 `docs/`、根 `README.md` 保持一致。
