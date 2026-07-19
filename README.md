# simMC 工具组 / simMC Tool Set

面向 simMC 玩家的离线便携工具集合。每个工具使用独立公开仓库；Windows 与 Android 的同版本文件放在同一个 GitHub Release，历史版本继续保留。

## 最新版直接下载

| 工具 | 功能 | Windows | Android | 源码与说明 |
| --- | --- | --- | --- | --- |
| 旅行猎手饰品对比工具 v4 | 饰品 OCR、人工复核、仓库管理、剑/弓最优配装 | [下载 Windows ZIP](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/download/v4/travel-hunter-accessory-tool-v4-win.zip) | [下载 Android APK](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/download/v4/travel-hunter-accessory-tool-v4-android-debug.apk) | [工具仓库](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool) · [v4 Release](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/tag/v4) |
| 奥术卷轴计算器 v1.1.0 | 卷轴材料配比、全局排除材料与轮换方案 | [下载 Windows ZIP](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/download/v1.1.0/aoshu-scroll-calculator-v1.1.0-win.zip) | [下载 Android APK](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/download/v1.1.0/aoshu-scroll-calculator-v1.1.0-android-debug.apk) | [工具仓库](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator) · [v1.1.0 Release](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/tag/v1.1.0) |

## Windows 安装

1. 点击表格中的“下载 Windows ZIP”。
2. 把 ZIP 完整解压到普通文件夹，不要直接在压缩软件内运行。
3. 双击文件夹里的 `启动工具.bat`。
4. 使用期间保持黑色启动窗口打开。

Windows 包自带便携 Node 运行时，不需要安装 Node、npm 或开发工具。

## Android 安装

1. 点击表格中的“下载 Android APK”。
2. 红米 K50 / 澎湃 OS 等设备按系统提示，为下载 APK 的浏览器或文件管理器允许“安装未知应用”。
3. 安装后直接打开工具。当前 APK 均不申请联网权限。

Android 附件目前是项目自带 debug 签名的离线 APK。更新同一工具时应直接覆盖安装，不要混用来源不明的重签名 APK。

## 安全校验

- 每个工具 Release 都附带 `SHA256SUMS.txt`、`LICENSE` 和 `THIRD_PARTY_NOTICES.md`。
- 下载后可用 PowerShell 执行 `Get-FileHash <文件路径> -Algorithm SHA256`，与 Release 清单比对。
- 如果 Windows SmartScreen 弹出提示，请先确认下载地址属于 `MurphyPotato` 公开仓库并核对 SHA256。

## 发布原则

- 每个工具使用独立仓库，避免版本和依赖互相污染。
- 新功能迭代使用新版本号；为现有同版本补齐平台安装包时，Windows 与 Android 合并到同一个 Release。
- 玩家发行包必须离线可用，并公开源码、构建方式、许可证和第三方声明。

## License

本总览仓库的原创文档使用 [MIT License](LICENSE)。各工具及第三方组件遵循其各自仓库中的许可证与声明。

本项目与 simMC、Mojang Studios 或 Microsoft 无官方关联。
