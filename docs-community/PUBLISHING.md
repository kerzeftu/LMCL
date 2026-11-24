# LMCL 发布与打包（PUBLISHING）

> 以下是历史 Windows 测试构建手册，不是当前发行计划或下载承诺。本次只留档源码与文档，不制作新的完整绿色包。路径、依赖和包名是原开发时的记录；本地工具缓存和分发二进制不随 Git 提交，复现前需自行准备对应依赖并核对许可。原则仍是按记录的必要标准验证一次，不重复验证同一件事。

---

## 1. 版本与命名

| 项 | 规则 |
| --- | --- |
| 产品版本 | `v3.17.x`（功能/修复递增；补丁也递增小版本） |
| 包标识 | `m3expressiveNN`（NN 从 22 起连续递增）；补丁包加 `-patch` 后缀 |
| 包名 | `LMCL-1.0.0-win-x64-<包标识>.zip` |
| 目录 | `dist/LMCL-1.0.0-win-x64-<包标识>/` 与同名 `.zip` |
| 记录 | `README.md` 顶部新版本段 + `docs/00-整体要求.md` 修订块 + 步骤 21/审查记录/待办各一节 |

当前基线（v3.17.35）：jar 12,391,112 B（`89bdadb5…`）、exe 13,708,502 B（`8b3ff3d7…`）、stub 1,317,390 B（`e06151cc…`）、测试 community 43 类/248、全量 66 类/358/0/0/2 skipped、补丁 zip 23,774,355 B（`765d7b8c…`）。

---

## 2. 两种交付物

| 类型 | 内容 | 用途 |
| --- | --- | --- |
| **完整绿色包** | jar + exe + `jre-x64/` + `dependencies/windows-x86_64/openjfx/` + `run-classic.bat` + `THIRD-PARTY-NOTICES.txt` + `LMCL-download-source-sample.lmcl.json` + `README-测试说明.txt` + `SHA256SUMS` | 新用户/实机验收（用户要求**下次必须用完整包**） |
| **补丁包** | jar + exe + `README-测试说明.txt` + `SHA256SUMS` | 覆盖已解压的旧包，快速验证单个修复 |

> 用户明确要求：**下次打包完整绿色包**。补丁包只用于临时验证。

---

## 3. 标准构建

在 `lmcl/` 下（串行，`--no-daemon`）：

```bash
export JAVA_HOME=/home/kerze/hmclce/.tools/jdk-17.0.13+11
export GRADLE_USER_HOME=/home/kerze/hmclce/.tools/gradle-home
export HMCL_LAUNCHER_EXE=/home/kerze/hmclce/.tools/stub-build/HMCLauncher.exe

./gradlew :HMCL:compileJava :HMCL:checkTranslations --no-daemon
./gradlew :HMCL:test --no-daemon
./gradlew :HMCL:makeExecutables --no-daemon
```

预期：`compileJava`/`checkTranslations` 退出 0（仅 3 条既有 `--add-exports` 警告）；community 43 类/248 用例 0/0/0；全量 66 类/358 用例 0/0/2 skipped（仅 `GameCrashWindowTest`、`SVGTest`）；`makeExecutables` 产出 `HMCL/build/libs/LMCL-1.0.0-any.jar` 与 `LMCL.exe`。

---

## 4. 完整绿色包组装

来源（`.tools/`，清理 `dist` 时未删除）：

| 资产 | 来源 | 说明 |
| --- | --- | --- |
| JRE | `.tools/win-jre/jre17-win-x64.zip`（43.8 MB，380 entries，`jdk-17.0.20.1+1-jre`） | 解压为包内 `jre-x64/` |
| JavaFX 21.0.8 | `.tools/javafx-win/javafx-{base,graphics,controls}-21.0.8-win.jar` | 放入 `dependencies/windows-x86_64/openjfx/` |
| 启动壳 | `.tools/stub-build/HMCLauncher.exe`（1,317,390 B） | 构建期由 `HMCL_LAUNCHER_EXE` 嵌入 `LMCL.exe`，包内 `assets/HMCLauncher.exe` 同哈希 |
| 启动脚本 | `run-classic.bat` | 旧包内被删；按 `docs/steps/21-主页重构与侧边栏移除.md` §35.5 与历史包 README 复原，建议存一份模板到 `docs/附录/` |
| 许可/示例 | `THIRD-PARTY-NOTICES.txt`、`LMCL-download-source-sample.lmcl.json` | 同上，随完整包发布 |
| 说明/校验 | `README-测试说明.txt`、`SHA256SUMS` | 每次发版重写；SHA 逐文件 |

步骤：
1. 新建 `dist/LMCL-1.0.0-win-x64-<id>/`，放入 jar、exe。
2. 解压 JRE 到 `jre-x64/`；复制 JavaFX 三个 jar 到 `dependencies/windows-x86_64/openjfx/`。
3. 放入 `run-classic.bat`、许可、示例 JSON、`README-测试说明.txt`。
4. 生成 `SHA256SUMS`（`sha256sum` 每行相对路径，327 行对应 327 个非 SHA 文件；总 328 文件）。
5. 用 Python `zipfile` 打包（`compresslevel=9`，相对路径、无目录条目）：
   ```python
   import os, zipfile
   root = "dist/LMCL-1.0.0-win-x64-<id>"
   with zipfile.ZipFile(root + ".zip", "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
       for base, _, files in os.walk(root):
           for name in sorted(files):
               path = os.path.join(base, name)
               z.write(path, os.path.relpath(path, "dist"))
   ```
6. 校验一次（§5），通过即停。

---

## 5. 发布前校验（一次）

```bash
# 1) 分段：exe = stub + jar
head -c 1317390 LMCL.exe | sha256sum          # 应等于 stub
tail -c +1317391 LMCL.exe | sha256sum         # 应等于 jar

# 2) 包内 jar/exe 哈希与构建产物一致；assets/HMCLauncher.exe 与 stub 同哈希
# 3) SHA256SUMS：327 行、327/327 通过
cd dist/LMCL-...-<id> && sha256sum -c SHA256SUMS | tail -1
# 4) zip：328 entries、0 目录条目、testzip() 为 None
python3 - <<'PY'
import zipfile; z=zipfile.ZipFile("dist/...zip")
print(len(z.infolist()), sum(1 for i in z.infolist() if i.is_dir()), z.testzip())
PY
```

- 补丁包只做 1)–2) 与 SHA 校验。
- **不要**为同一产物反复跑多套脚本；以上通过即视为完成。

---

## 6. 文档与提交（与发布同批）

1. `README.md`：顶部追加本版说明（改动、未改、测试数、jar/exe/zip 哈希、未实机项、链接）。
2. `docs/00-整体要求.md`：版本头 + 修订块 + 复验记录。
3. `docs/steps/21-主页重构与侧边栏移除.md`：新 §（触发/实现/验证/未实机/文档同步）。
4. `docs/审查记录-v3.16.4源码核对.md`：新 §（日期/命令/结果/未做）。
5. `docs/待办总览.md`：基线行、VIS-01、相关条目。
6. `lmcl/docs-community/`：镜像同步（`diff -rq` 空、`cmp` 通过）。
7. 提交：`git add -A && git commit -m "<type>(ui): <summary> (v3.17.x)"`；分支 `feature/28-frosted-v2`；无 `origin`，不推送。

---

## 7. 交付话术（给用户）

- 包路径 + 大小 + SHA-256；git commit 短哈希。
- 改动摘要（≤5 行）+ 未改项（材质参数/启动按钮/侵入点）。
- 测试数（community/全量）。
- **明确写“Windows 实机未验收”**，列出需用户重点看的 2–3 项。
- 完整包与补丁包的区别（补丁必须覆盖旧包）。

---

## 8. 回退方案

- 材质掉帧：先回退五层半径 **160/104/64/40/20**，仍不足回退 128/88/56/32/16，或整包回退 `m3expressive28`。
- 底部带问题：保留上一版 `jar` 作为回退（`dist/` 清理前应保留最近 1–2 个包）。
- 发布错误：不删除已交付包；用新版本号修正，README 记录。

---

## 9. 常见发布错误

| 错误 | 原因 | 处理 |
| --- | --- | --- |
| exe 图标仍是 HMCL | 未设 `HMCL_LAUNCHER_EXE` | 重新构建 |
| `SHA256SUMS` 行数不对 | 漏文件/多文件 | 重新生成并逐行校验 |
| zip 里含目录条目 | `os.walk` 直接写目录 | 只写文件、用相对路径 |
| 补丁包被当完整包 | 未说明/未检查 | 交付话术注明“覆盖已解压包” |
| 版本号三处不一致 | README/00/包内说明未同步 | 按 §6 全量替换后 `grep` 一次 |
