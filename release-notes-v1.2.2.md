# simmc-tool-set-fabric-1.2.2-fabric-mc1.21.11

## 版本介绍

simMC Tool Set 1.2.2 是面向 Minecraft 1.21.11 Fabric 的正式版本，
基于 `1.2.1-dev20261024.1` 转正。它将奥术 HUD、卷轴计算、
饰品配装、发酵与厨具提示和本地诊断整合到一个纯客户端模组中，
服务器无需安装。

## 相比 1.2.1 的更新

- 奥术 HUD、法杖 Mana、发酵与厨具提示新增支持两个服务器入口：
  `110.42.44.191:7777` 和 `simmc.silkysoftware.com`。
- 继续支持原入口 `play.simmc.cn`；两个域名支持带端口形式，
  备用 IP 仅允许端口 `7777`。
- 更新模块状态提示，使其包含三个支持的入口。
- 增加服务器地址识别回归测试，覆盖允许的域名、备用 IP 及非目标地址。
- 保留 1.2.1 的卷轴搜索修复、元素衰减规划、材料排除、手动方案、
  使用记录与预设、饰品配装、厨具五色状态和本地诊断。

本次转正仅调整发布版本号和文档；模块运行代码与
`1.2.1-dev20261024.1` 一致。

## 安装环境

- Minecraft 1.21.11
- Fabric Loader 0.19.5 或兼容的更新版本
- Fabric API 0.141.6+1.21.11 或兼容的更新版本
- Java 21

将 `simmc-tool-set-fabric-1.2.2-fabric-mc1.21.11.jar`
放入客户端的 `mods` 目录，并移除同一目录中的旧版 Tool Set JAR，
避免重复加载。

## 验证范围

发布前已通过 107/107 单元测试、完整 Gradle 构建、仓库隔离客户端测试、
`git diff --check` 和 JAR 元数据审计。正式 JAR 的 215 个运行类与
中间版逐一比对一致；历史版本产物保持不变。

SHA-256：
`FE6400F419BB18FC04D1F15D2BBB24599351A65583B122C93115ACF5491EEB62`

服务器地址白名单仍然保留，不会在所有服务器自动启用奥术 HUD 或
发酵与厨具提示。

本地自动化验证不代替三个服务器入口上的实际联机验收，
本轮不宣称取得新的服务器实测证据。

问题反馈：https://github.com/MurphyPotato/simmc-tool-set/issues

---

## English

simMC Tool Set 1.2.2 is a client-only Fabric utility mod for Minecraft 1.21.11,
promoted from `1.2.1-dev20261024.1`.

This release adds `110.42.44.191:7777` and `simmc.silkysoftware.com` to the
server whitelist shared by the Arcane HUD, wand Mana HUD, fermentation helper
and cookware helper. The original `play.simmc.cn` entry remains supported.
Hostname entries accept optional ports; the backup IP requires port `7777`.

All existing scroll planning, usage history, presets, accessory tools,
cookware states and local diagnostics are retained. Runtime module code is
unchanged from the intermediate build. Local automated checks do not constitute
new live-server acceptance evidence.
