# LMCL 故障排查（TROUBLESHOOTING）

> 历史排查指南，不承诺持续支持。先对照 [暂停清单](docs/待办总览.md)，再用 [架构说明](ARCHITECTURE.md) 定位。快照空态下的禁用属于预期行为；选中实例后是否仍点不到，未完成实机复现。

---

## 1. 环境与构建

| 现象 | 先查 | 处理 |
| --- | --- | --- |
| `./gradlew` 报 JDK/工具链错误 | `JAVA_HOME` | 用 `.tools/jdk-17.0.13+11`，不要用系统 JDK |
| 依赖下载失败/版本解析失败 | `GRADLE_USER_HOME` | 指向 `.tools/gradle-home`（离线缓存） |
| 打包出的 `LMCL.exe` 图标还是 HMCL | `HMCL_LAUNCHER_EXE` | 必须显式指向 `.tools/stub-build/HMCLauncher.exe` 再构建 |
| Gradle 产物报 `in-progress-results-generic.bin` / `NoSuchFileException` | 是否并行跑了多个 Gradle | 串行 + `--no-daemon`；必要时 `cleanTest` |
| 测试/编译卡住 | 守护进程/并行 | 一律 `--no-daemon`；不要同时起多个构建 |
| 无 `zip`/`unzip` 命令 | 环境 | 用 Python `zipfile` 打包/校验 |

构建命令（在 `lmcl/` 下）：

```bash
export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11
export GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home
export HMCL_LAUNCHER_EXE=/home/kerze/hmclce/.tools/stub-build/HMCLauncher.exe
./gradlew :HMCL:compileJava :HMCL:checkTranslations --no-daemon
./gradlew :HMCL:test --no-daemon
./gradlew :HMCL:makeExecutables --no-daemon
```

---

## 2. 打包/校验

- 产物：`lmcl/HMCL/build/libs/LMCL-1.0.0-any.jar` + `LMCL.exe`（stub + jar 拼接）。
- 必查三项：`exe[:stub] == stub`、`exe[stub:] == jar`、jar 内 `assets/HMCLauncher.exe` 与 stub 同哈希。
- 完整包：328 个文件、`SHA256SUMS` 327 行（327/327 校验通过）、`zip` 内无目录条目、`testzip()` 为 `None`。
- 补丁包：仅 jar + exe + README + SHA256SUMS（4 entries），用于覆盖已解压的旧包；**不能**当完整包发。
- `dist/` 被清理后重建完整包：按 `PUBLISHING.md` §4 从 `.tools/win-jre`、`.tools/javafx-win`、`hmclauncher` 与仓库资产复原。

---

## 3. 主页材质与过渡带（高频）

| 现象 | 可能原因 | 先看/处理 |
| --- | --- | --- |
| 玻璃完全没出现（卡片实色） | `glass=off`/Classic；`M3Host` 未挂样式类；内容根实色 `Background` 未清 | 查 `-Dlmcl.visual.glass`、`M3Host` 的 `lmcl-m3-glass`、`MaterialCardSurface` |
| 三张 1×1 卡没材质 | 内容根铺满 + `.card` 实色覆盖 | 检查 4 类选择器与 `MaterialCardSurface`（v3.17.24 已修） |
| 过渡带像“色带”（比卡片清晰/亮/暗） | 带内是清晰壁纸、未材质化 | v3.17.30 起整带 24px 块平均 + 22% tint；若回退，查 `GlassEdgeFadeImage.materialize` |
| 过渡带外缘发亮 | 清晰壁纸外缘（`EDGE_SHARP_ROWS`/`blendRgb`） | v3.17.31/32 已删；外缘应为材质色 alpha 255 |
| 滚动抖动/闪烁 | 按距离反复重建 `WritableImage`、逐像素 `setArgb` | v3.17.30 起整带一次渲染 + `DISTANCE_STEP=1` + `applyRows()` 缩放 |
| 内容在底部被硬切 + 下方一条清晰壁纸 | 底部带未对齐内容截断，或带高只到框架下沿 | 查 `HomePane.createColumnFrame` 的 `bottomInset` 是否同时作用于 pane 与 bottomFade（v3.17.35） |
| 底部带盖住 footer、模糊看不到 | 带位置/高度与视口内缩不一致 | 右列内缩 56px、视口底边=页底−64、启动按钮顶=页底−57；三者必须同时满足 |
| 底部内容被启动按钮盖住/点不到 | 视口底边低于按钮顶 | 确认 `INSTANCE_VIEWPORT_BOTTOM_INSET=56`、`HOME_PADDING` 底 8；内容底边应比按钮顶高 7px |
| 顶部/底部带外观不一致 | 两列共用同一渲染器与同一几何假设 | 改带高/材质时同时检查两列四条带（左列无内缩） |

关键常量（v3.17.35 基线）：`FADE_HEIGHT=72`、`DISTANCE_STEP=1`、`MATERIAL_BLOCK_SIZE=24`、`MATERIAL_TINT=0.22`、`HOME_PADDING=10/12/8/12`、`INSTANCE_VIEWPORT_BOTTOM_INSET=56`、`launch-pane=230×57`。

---

## 4. 实例快照三个按钮“点不到”（按顺序排查，不要先猜遮挡）

1. **是否禁用**：卡片正文是否为“请先选择实例/还没有快照”？`updateButtons()`：无 `hasSelection` 时三者全禁用；回滚还需 AUTO_START 快照。截图里半透明灰 = 禁用，不是被盖。
2. **是否被裁切**：footer 是否滚到视口底边以下（内容被裁掉一半）？滚到底时底部带应隐藏、footer 完整。
3. **是否被覆盖**：右列内容底边是否高于 `launchPane` 顶（页底−57）？用内缩 56 + 页底 8 保证 7px 间距；若窗口/DPI 导致越界，按 `HomePane` 几何修正。
4. **视觉遮挡 ≠ 点击遮挡**：底部带 `mouseTransparent(true)`，只影响观感；点击拦截只可能来自 `launchPane` 或裁切。
5. **仍不可点**：用选中实例的截图 + 点击位置截图复现；必要时加临时命中日志（`-Dlmcl.debug.pick`），确认点击落到哪个节点，再改代码。

> 历史教训：v3.17.33 曾把按钮移到卡片顶部，用户澄清“置顶=层级不是位置”，v3.17.34 已恢复 footer。不要再用“移动按钮”解决层级问题。

---

## 5. 文档、测试与提交

| 现象 | 处理 |
| --- | --- |
| `checkTranslations` 失败 | 新增 key 必须同时补 `I18N.properties`、`I18N_zh.properties`、`I18N_zh_CN.properties` |
| 文档镜像不一致 | `diff -rq docs lmcl/docs-community/docs` 为空、`cmp README.md lmcl/docs-community/README.md` 通过 |
| 版本号不一致 | `grep -rn "v3.17.xx" README.md docs` 与 `docs/00-整体要求.md` 头部对齐；旧版本段落保留 |
| 改了上游文件 | 检查 `docs/00-整体要求.md` §3 的 18 处侵入点是否需登记 |
| `edit` 报 “file has not been read” | 先用 `ops_read_files`/`ops_inspect_local_file` 读目标文件，再 `edit` |

---

## 6. 已修复问题索引（现象 → 根因 → 版本）

| 版本 | 现象 | 根因 | 修法 |
| --- | --- | --- | --- |
| v3.17.24 | 磨砂材质回归、三卡 1×1 | 快照采样 + `.card` 实色 | 真实背景节点 + `MaterialCardSurface` |
| v3.17.26 | 快照 footer 点不到、图标还是 HMCL | 启动按钮覆盖 + 未嵌入 stub | 64px 安全区；`HMCL_LAUNCHER_EXE` |
| v3.17.27 | 上下生硬截断 | 无过渡带 | `GlassEdgeFade` 72px |
| v3.17.28 | 渐变离每日一言太远、模糊不够 | 间距 12/半径偏小 | spacing 4；半径 192 档 |
| v3.17.29 | “模糊不是高斯毛玻璃” | 饱和度/颗粒/1px 边线 | 撤销三措施 |
| v3.17.30 | 色带、滚动抖动 | 清晰壁纸带 + 每 4px 重建 | 材质化 + 整带一次渲染 + 1px 步长 |
| v3.17.31 | 底部亮边、盖住 footer | 清晰外缘 + 带叠内容 | 删底部带（后被要求恢复） |
| v3.17.32 | 底部模糊没了/间距不对 | 上版删带过猛 | 恢复材质化带、页底 8、短标签 |
| v3.17.33 | “按钮没置顶”被理解为位置 | 理解偏差 | 移到卡片顶部（后被回退） |
| v3.17.34 | 按钮仍报点不到 | 视口未内缩 | 视口底内缩 56px、恢复 footer |
| v3.17.35 | 内容硬切 + 下方清晰壁纸 | 带未随内缩上移 | `bottomFade` 同步 `Insets` |
| 待做 | 底部模糊要向下延伸 | 带只到截断处 | “渐隐+搁板”（见 PRD） |

---

## 7. 排查纪律

- **先文档后代码**：本文件与 `ARCHITECTURE.md` 能回答的，不要重新翻源码。
- **一次验证就停**：按 `PUBLISHING.md` 的标准跑一遍（编译/测试/包校验），确认生效即结束；不要为同一改动反复写脚本、堆断言、多轮交叉核对。
- **证据优先**：视觉问题以用户截图/实机为准；没有证据时先要截图或描述，不猜。
- **改一处、验一次**：避免“同时改多处 + 全量重验”的浪费。
