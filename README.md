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

- 显示奥术冷却、吟唱/持续状态、公共冷却和法杖 Mana

- 支持模块化自定义HUD位置和大小

![奥术 HUD](docs/screenshots/arcane-status.png)
<img width="2662" height="1464" alt="7db44bbe64191a60cc39eac7f6cbfeff" src="https://github.com/user-attachments/assets/62c49dae-8831-409a-96a2-6640592ca266" />


### 卷轴计算

- 计算卷轴材料配比

- 支持材料排除、杂质和批量建议

![卷轴计算](docs/screenshots/scroll-calculator.png)

### 饰品配装

- 扫描饰品，无论其在饰品栏，物品栏，背包，还是容器中

- 自动入库被扫描的饰品，并自动计算伤害评分

- 自动按最佳期望伤害计算剑套和弓套配装，并提供配装引导，帮助玩家定位最佳饰品位置
  - 红框代表剑套最佳饰品，蓝框代表弓套最佳饰品

- 当前支持旅猎套装

![饰品配装](docs/screenshots/accessory.png)

### 发酵与厨具

- 本地化记录发酵桶的放入物品，当前发酵剩余时间（需要通过烹饪时钟同步）

- 本地化记录（御三锅）煎锅、蒸锅、煮锅的放入物品，以高亮发光区别显示当前烹饪状态
  - 不发光：啥都没放
  - 红光：你菜变碳了
  - 蓝光：放入材料但还没开始烹饪
  - 黄光：正在烹饪
  - 绿光：烹饪完成

![发酵与厨具](docs/screenshots/cookware.png)
<img width="1639" height="1031" alt="1d325f42f7be10d996b993b2544f700a" src="https://github.com/user-attachments/assets/9a9bde0e-88c4-4290-a9c2-c65c87e279cf" />


### 诊断与日志

- 出bug时请导出mod诊断日志与游戏log，然后发送issue，不胜感激。

![诊断与日志](docs/screenshots/diagnostics.png)

## 安装

目标环境：Minecraft `1.21.11`、Java 21、Fabric Loader `0.19.5`、Fabric API `0.141.6+1.21.11`。将对应 JAR 放入客户端 `.minecraft/mods`。
本mod为客户端mod。

## 网页地图

从 v1.1.8 起，Tool Set 不再支持 SIMMC 网页地图、Xaero 适配、地图 HTTP 下载、地图缓存和地图快捷键。

如需要原功能，请去simmc官方kook 规则帮助与资源下载 频道，下载 simmc-map-addon 模组。

## 许可

原创代码和文档使用 [MIT License](LICENSE)；第三方来源和授权见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
