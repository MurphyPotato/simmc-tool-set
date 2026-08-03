# simMC 工具组 / simMC Tool Set

面向 simMC 玩家的离线便携工具集合。每个工具使用独立公开仓库；Windows 与 Android 的同版本文件放在同一个 GitHub Release，历史版本保留。
MurphyPotato 制作 · 非官方玩家工具组。

## 最新版直接下载

| 工具 | 功能 | Windows | Android | Fabric (推荐) | 源码与说明 |
| --- | --- | --- | --- | --- | --- |
| 旅行猎手饰品对比工具 | 饰品识别、人工复核、仓库管理、剑/弓最优配装 | [下载 Windows v5 ZIP](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/download/v5/travel-hunter-accessory-tool-v5-win.zip) | [下载 Android v5 APK](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/download/v5/travel-hunter-accessory-tool-v5-android.apk) | [下载 1.21.8 v6 JAR (推荐)](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/download/v6-fabric-mc1.21.8/travel-hunter-accessory-tool-v6-fabric-mc1.21.8.jar) | [工具仓库](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool) · [Fabric v6](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/tag/v6-fabric-mc1.21.8) · [v5 Web](https://github.com/MurphyPotato/simmc-travel-hunter-accessory-tool/releases/tag/v5) |
| 奥术卷轴计算器 v1.1.0 | 卷轴材料配比、全局排除材料与轮换方案 | [下载 Windows ZIP](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/download/v1.1.0/aoshu-scroll-calculator-v1.1.0-win.zip) | [下载 Android APK](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/download/v1.1.0/aoshu-scroll-calculator-v1.1.0-android-debug.apk) | [下载 1.21.8 RC1 JAR (推荐)](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/download/v1.1.1-fabric-mc1.21.8-rc1/aoshu-scroll-calculator-v1.1.1-fabric-mc1.21.8-rc1.jar) | [工具仓库](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator) · [v1.1.0 Release](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/tag/v1.1.0) · [Fabric RC1](https://github.com/MurphyPotato/simmc-arcane-scroll-calculator/releases/tag/v1.1.1-fabric-mc1.21.8-rc1) |

## Fabric 安装 (推荐)

1. 准备 Minecraft 1.21.8、Java 21、Fabric Loader 0.17.3 或更高版本，以及 Fabric API 0.136.1+1.21.8 或兼容版本。
2. 点击表格中的 Fabric 下载链接，将 JAR 放入对应游戏实例的 `.minecraft/mods` 文件夹。
3. 旅行猎手饰品工具默认按主键盘数字行 `0` 打开；奥术卷轴计算器默认按 `O` 打开。两个按键均可在 Minecraft 原生“控制”页面中修改、清除或重置。

两个模组都只需安装在客户端，simMC 服务端无需安装。旅行猎手 Fabric v6 是正式推荐版；奥术卷轴 Fabric RC1 仍是测试候选版。

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

旅行猎手 v5 使用长期生产签名；历史 v2/v4 debug 版需要先卸载再安装 v5。奥术卷轴 v1.1.0 目前仍为项目自带 debug 签名。不要混用来源不明的重签名 APK。

## 隐私与离线边界

MurphyPotato 制作的非官方玩家工具。玩家发行包运行时不收集、上传或向作者传输个人信息、截图、配装/配方数据或设备标识，也不主动连接非本地服务器。

Windows 仅使用 `127.0.0.1` 本机服务，Android 不申请联网权限；Fabric 模组仅在客户端读取游戏已经可见的数据，不包含网络请求、遥测或服务端入口。操作系统、浏览器及用户自行启用的系统备份行为由用户设备设置决定，不属于工具主动通信。源码构建过程可能联网下载公开依赖。

当前公开稳定下载为旅行猎手 Fabric v6、旅行猎手 Windows/Android v5 和奥术卷轴 v1.1.0。

旅行猎手 Fabric v6 适用于 Minecraft 1.21.8，是当前推荐入口。进入世界后可在背包或饰品容器按主键盘数字行 `0` 打开，按键可在原生控制设置中修改；Windows/Android v5 继续保留为兼容下载。

## 安全校验

- Windows/Android Release 使用 `SHA256SUMS.txt`；Fabric Release 在 JAR 旁提供同名 `.jar.sha256` 校验文件。
- 每个工具仓库都提供 `LICENSE` 和 `THIRD_PARTY_NOTICES.md`。
- 下载后可用 PowerShell 执行 `Get-FileHash <文件路径> -Algorithm SHA256`，与 Release 清单比对。
- 如果 Windows SmartScreen 弹出提示，请先确认下载地址属于 `MurphyPotato` 公开仓库并核对 SHA256。

## License

本总览仓库的原创文档使用 [MIT License](LICENSE)。各工具及第三方组件遵循其各自仓库中的许可证与声明。

本项目与 simMC、Mojang Studios 或 Microsoft 无官方关联。
