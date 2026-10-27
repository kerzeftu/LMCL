# LMCL 应用图标

本目录保存 LMCL 自有应用图标的源文件与 Windows ICO，替换上游 HMCL 图标后用于：

- `HMCL/src/main/resources/assets/img/icon*.png`：启动器窗口、关于页、下载页、主题包页等 JavaFX 图标。
- `HMCL/src/main/resources/assets/img/icon-mac.png`：AWT Taskbar 图标。
- `HMCL/src/main/resources/assets/img/icon-title*.png`：主页标题栏图标。
- `hmclauncher/HMCL.ico`：Windows 启动壳 `.exe` 资源（文件名保留 `HMCL.ico`，资源 ID 仍为 `IDI_HMCL`）。
- `hmclauncher` 构建时由 `windres` 嵌入 `HMCLauncher.exe`。

## 文件

| 文件 | 尺寸/格式 | 用途 |
| --- | --- | --- |
| `LMCL-icon.png` | 970×970 RGBA | 源图，透明背景 |
| `LMCL.ico` | 16/24/32/48/64/128/256，32-bit | Windows exe/快捷方式 |
| `../HMCL/src/main/resources/assets/img/icon.png` | 32×32 | JavaFX 小图标 |
| `.../icon@2x.png` | 64×64 | 对话框/页面图标 |
| `.../icon@4x.png` | 128×128 | 关于页等 |
| `.../icon@8x.png` | 256×256 | 设置/主题包列表 |
| `.../icon-mac.png` | 512×512 | Taskbar |
| `.../icon-title.png` | 24×24 | 主页标题栏 |
| `.../icon-title@2x.png` | 48×48 | 主页标题栏 HiDPI |

## 重新生成

当前图标由临时 Java 工具按双三次缩放生成：读取 `LMCL-icon.png`，输出上述 PNG 与 `LMCL.ico`。源图透明背景保留；生成后不要直接改 `assets/img/icon*.png`，应替换源图并重新生成，避免各尺寸不一致。

## 上游致谢图标

上游 HMCL 的“用户”致谢条目原先引用 `/assets/img/icon.png`。为避免把 HMCL 的用户致谢错标为 LMCL 图标，旧 32×32 图标已保留为 `HMCL/src/main/resources/assets/img/hmcl.png`，并在 `assets/about/thanks.json` 中把该条目的 `image` 改为 `hmcl.png`。
