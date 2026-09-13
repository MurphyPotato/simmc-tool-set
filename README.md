# simMC Tool Set

面向 simMC 玩家的 Minecraft 1.21.11 Fabric 客户端工具模组。只需安装在客户端，服务器无需安装。

## 当前版本

- **稳定版 v1.1.9**：包含奥术 HUD Mana 闪烁修复、发酵/厨具生命周期修复和稳定的五个工具版块。
- **测试版 v1.1.9-dev.20260913.2 (Beta)**：在稳定版基础上增加锅内结果变化检测、烹饪完成/失败提示、五色状态和彩色锅本体描边；真实目标服务器验收仍待进行。
- **历史稳定版 v1.1.8**：升级到 Minecraft 1.21.11，并移除网页地图/Xaero 模块。

## 下载

- [v1.1.9 稳定版](https://github.com/MurphyPotato/simmc-tool-set/releases/tag/v1.1.9-fabric-mc1.21.11)
- [v1.1.9-dev.20260913.2 Beta](https://github.com/MurphyPotato/simmc-tool-set/releases/tag/v1.1.9-dev.20260913.2)
- [v1.1.8 稳定版](https://github.com/MurphyPotato/simmc-tool-set/releases/tag/v1.1.8-fabric-mc1.21.11)

## 主要功能

- **奥术 HUD**：显示奥术冷却、吟唱/持续状态、公共冷却和法杖 Mana。
- **卷轴计算**：计算卷轴材料配比，支持材料排除、杂质和批量建议。
- **饰品配装**：扫描饰品、人工复核并计算剑套和弓套配装。
- **发酵与厨具**：识别发酵桶和锅具，显示材料、数量、计时与状态。
- **诊断与日志**：本地查看和导出诊断信息。

## 模块截图

### 奥术 HUD

![奥术 HUD](docs/screenshots/arcane-status.png)

### 卷轴计算

![卷轴计算](docs/screenshots/scroll-calculator.png)

### 饰品配装

![饰品配装](docs/screenshots/accessory.png)

### 发酵与厨具

![发酵与厨具](docs/screenshots/cookware.png)

### 诊断与日志

![诊断与日志](docs/screenshots/diagnostics.png)

## 安装

目标环境：Minecraft `1.21.11`、Java 21、Fabric Loader `0.19.5`、Fabric API `0.141.6+1.21.11`。将对应 JAR 放入客户端 `.minecraft/mods`；服务器不安装。

## 网页地图边界

从 v1.1.8 起，Tool Set 不再支持 SIMMC 网页地图、Xaero 适配、地图 HTTP 下载、地图缓存和地图快捷键。

## 许可

原创代码和文档使用 [MIT License](LICENSE)；第三方来源和授权见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
