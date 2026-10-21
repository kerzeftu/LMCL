# LMCL HMCLauncher stub

Step 20 的本地构建产物放置目录。

## 当前状态

- 源码 fork：`/home/kerze/hmclce/hmclauncher`（独立本地 git 仓库，基线 `da0e0c3b4f01fa0edbaf575d9631f093daf70cd1`，版本 `3.16.1000.0`）。
- **尚未提交二进制**：已在 WSL 用 MinGW-w64 i686 交叉构建出 1,201,152 B stub（SHA-256 `89b58fa0…`），但未写入本目录，也未在 Windows 实机验证。
- 已验收的 `dist/LMCL-3.16.1000-win-x64-home3.zip` 仍使用官方 `HMCLauncher.exe`（回退路径）；新 stub 的测试包是 `dist/LMCL-3.16.1000-win-x64-home4-stub.zip`。
- Windows 构建完成后，把 `HMCLauncher.exe` 放到本目录，并在 LMCL 仓库根目录设置：

  ```powershell
  $env:HMCL_LAUNCHER_EXE = (Resolve-Path .\lib\stub\HMCLauncher.exe)
  .\gradlew :HMCL:makeExecutables --no-daemon
  ```

- basename 必须是 `HMCLauncher.exe`，否则 `HMCL/build.gradle.kts` 的 `makeExecutables` 找不到 `assets/HMCLauncher.exe` 而静默回落到官方 stub。
- 构建/验收清单见 `docs-community/docs/steps/20-HMCLauncher启动壳重建.md`，品牌化状态见 fork 仓库的 `BRANDING.md`。

## 发布前

1. 在 Windows 上 `cmake -S . -B build -A Win32` + `cmake --build build --config Release`（或在 WSL 用 MinGW 交叉构建后拿到产物）。
2. 记录 `HMCLauncher.exe` 的 SHA-256。
3. 用该 stub 重新 `:HMCL:makeExecutables`，按步骤 20 §3.6 断言前缀/jar 偏移。
4. 在 Windows 干净环境跑步骤 20 §3.5 的 10 项行为对等验收后，才切换默认包。
