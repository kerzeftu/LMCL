# LMCL 维护手册（MAINTENANCE）

> 项目处于半弃坑状态，不安排例行维护、发版或响应时间。以下是原开发纪律，供日后恢复或衍生开发参考：先查文档、必要验证做一次、无实机证据不宣称通过。留档规范以 [文档入口](DOCS.md) 为准。

---

## 1. 工作流：先文档，后代码

```
遇到问题 / 提需求
  → TROUBLESHOOTING.md（已知现象与排查顺序）
  → ARCHITECTURE.md（改动落在哪一层、关键类）
  → USER-UNDERSTANDING.md（用户真正要什么、历史误解）
  → 仍无法判断：看代码（最后一步）
```

- **禁止**“先 review 代码再说”。历史问题多数在文档里有结论（如按钮禁用态、过渡带对齐、启动按钮安全区）。
- 用户说“先写 md”时：先出 PRD（`.harness/prd/`），确认后再改代码。

---

## 2. 文档维护台账

| 触发 | 必须更新 |
| --- | --- |
| 新增/移动关键类、改主页渲染链、改材质参数 | `ARCHITECTURE.md` |
| 用户提出新诉求、纠正理解、新增验收口径 | `USER-UNDERSTANDING.md` |
| 修复用户可见问题、新增已知行为/坑 | `TROUBLESHOOTING.md` §6 与相关小节 |
| 改构建命令、打包资产、校验标准、版本命名 | `PUBLISHING.md` |
| 改流程/验证纪律/清理策略 | `MAINTENANCE.md`、必要时 `DOCS.md` |
| **每个版本** | `README.md` 顶部、`docs/00-整体要求.md` 修订块、`docs/steps/21-主页重构与侧边栏移除.md` 新 §、`docs/审查记录-v3.16.4源码核对.md` 新 §、`docs/待办总览.md` |
| 每个 Run | `structure.md`、`subagent.md`、`.harness/runs/<id>/state.json`/`report.md` |
| 新增/调整文档本身 | `DOCS.md` 索引 |

规则：
- 文档与代码**同批**提交，不做“以后补”。
- 版本号只增不改：旧段落保留，新版本置顶。
- 镜像：改 `docs/` 或根 `README.md` 后立即同步 `lmcl/docs-community/`。

---

## 3. 版本节奏（每版清单）

1. 代码/测试完成 → 2. 完整构建（`PUBLISHING.md` §3）→ 3. 打包（完整包或补丁）→ 4. 校验一次（§5）→ 5. 文档五件套 + 镜像 → 6. `git commit` → 7. 交付话术（含未实机声明）→ 8. 更新本组文档（如涉及行为变化）。

---

## 4. 代码维护规则

- 上游侵入点共 18 处（`docs/00-整体要求.md` §3）；新增/移动必须登记，能不加就不加。
- Java：`///` Javadoc、显式 `@Nullable`、`@Unmodifiable`（`lmcl/AGENTS.md`）。
- i18n：三份 `I18N*.properties` 同步；短标签 + tooltip 优先，避免省略号。
- CSS：玻璃作用域 4 类选择器；不要跨作用域硬覆盖 `-fx-` 变量。
- 不新增依赖；不做自动更新/包名 relocate/macOS dmg（已定案）。
- 改动尽量收敛：一次只解决一个用户可见问题；同类参数（半径/权重/tint/带高）改一处要检查另一处。

---

## 5. 验证纪律（用户明确要求）

> **不要过度防御、过度校验。** 不要为了“确保正确”反复写验证脚本、堆大量 assert、对同一处改动做多轮交叉核对。

- 按 `PUBLISHING.md` §5 的标准**验证一次**，通过即停；不要为同一产物换脚本再验一遍。
- 测试、编译、包校验各自只跑一次；失败才重跑，并记录原因。
- 不新增“防御性”断言/日志/守卫，除非对应一个真实复现的问题。
- 视觉问题以用户截图/实机为准：没有证据先要证据，不猜、不试错多轮。
- 只有出现**新证据**（新截图、新报错、用户新描述）时才重新验证。

---

## 6. 清理与磁盘

- `dist/`：保留最近 1–2 个包（完整包优先）；旧包目录 + zip 一起清；清理前 `realpath` 核对路径。
- `.tools/`：保留（JDK/Gradle 缓存/stub/win-jre/javafx-win）；不要清 `win-jre` 与 `javafx-win`（完整包依赖）。
- `.harness/`：保留 PRD 与最近 Run；过期 Run 可按配置清理（24h 阈值）。
- `lmcl/HMCL/build`、`lmcl/build`、`lmcl/.gradle`：可随时清，重建即可。
- 完整包资产（`run-classic.bat`、许可、示例 JSON）被删后需按 `PUBLISHING.md` §4 复原，建议在 `docs/附录/` 存模板。

---

## 7. 提交与分支

- 分支：`feature/28-frosted-v2`；无 `origin`，不推送。
- 提交信息：`<type>(ui): <summary> (v3.17.x)`（type：feat/fix/docs/refactor/test）。
- 一次提交对应一个可回退的改动；文档与代码同提交。
- 不重写历史；回退用新提交。

---

## 8. Run 台账（子代理编排）

- 进入 DISPATCH 前创建 `.harness/.lock`；Run 结束删除。
- `state.json` 是唯一事实源（`schema_version: 1`），完成时写 `report.md`。
- 小改动（1–2 文件、单点修复）**不启用**子代理编排，直接改、按文档验证一次。
- 文档类工作（如本组文档）直接执行，不进 Run。

---

## 9. 当前开放项（维护视角）

| 项 | 说明 | 下一步 |
| --- | --- | --- |
| 快照按钮选中态可点性 | 空状态禁用已确认；选中态待截图 | 用户实机确认或按 `TROUBLESHOOTING.md` §4 排查 |
| 底部“渐隐+搁板” | 用户要求模糊向下延伸 | PRD 已写，待确认后实施 |
| 完整绿色包 | 用户要求下次用完整包 | 按 `PUBLISHING.md` §4 组装 |
| Windows 实机验收 | REL-01，所有视觉/点击的最终依据 | 用户实机 |
| 顶部带外缘材质化 | v3.17.32 起的已披露偏差 | 实机确认是否可接受 |

---

## 10. 文档索引

见 `DOCS.md`。核心五件套：`ARCHITECTURE.md`、`USER-UNDERSTANDING.md`、`TROUBLESHOOTING.md`、`PUBLISHING.md`、`MAINTENANCE.md`；项目长文档仍在 `docs/`（总纲/步骤/修复方案/审查/待办）。
