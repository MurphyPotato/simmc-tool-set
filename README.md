# simMC Tool Set

![simMC Tool Set icon](src/main/resources/icon.png)

面向 simMC 玩家的 Fabric 客户端工具模组。Tool Set 将五个常用工具集中在一个模组、一个 Mod ID 和一个 JAR 中，不需要安装到服务器。

当前最新测试版本为 `v1.1.9-dev.20260913.2`（Beta），目标环境为 Minecraft `1.21.11`、Java 21、Fabric Loader `0.19.5` 和 Fabric API `0.141.6+1.21.11`。这是中间测试版本，不是正式 v1.1.9 Release；真实目标服务器厨具验收仍待完成。

## 主要功能

| 版块 | 功能 |
| --- | --- |
| 奥术 HUD | 显示奥术冷却、吟唱/持续状态、公共冷却和法杖魔力；支持 Simes HUD 与原版 Action Bar 显示模式。 |
| 卷轴计算 | 根据目标元素和启用材料计算卷轴配比，支持材料排除、杂质与目标元素约束，以及批量制作轮换建议。 |
| 饰品配装 | 扫描背包和饰品容器，人工复核异常数据，保存饰品库，并计算剑套与弓套配装和评分。 |
| 发酵与厨具 | 在目标服务器识别发酵桶和厨具，记录可见食材，显示服务器校准信息和明确标注的本地时间估算。 |
| 诊断与日志 | 在客户端本地查看诊断记录，并按需导出；用于定位配置、生命周期和运行时问题。 |

奥术 HUD、发酵和厨具中的 Simes 衍生实现只覆盖已授权范围：奥术冷却/状态、法杖魔力、发酵助手和厨具助手。Simes 的市场、估值、余额和自动消息功能不在 Tool Set 中。检测到外置 Simes 时，内置 Simes 功能不初始化，由外置模组接管。

## 快捷键

默认按键由 `ToolSetKeyRouter` 注册。`\` 是前缀键；按住前缀后在短时间内按下次键即可打开对应版块。所有 Tool Set 专属次键都可以在“工具组按键”页面重新绑定、取消或恢复默认值。

| 默认按键 | 操作 |
| --- | --- |
| `\` + `1` | 奥术 HUD |
| `\` + `2` | 卷轴计算 |
| `\` + `3` | 饰品配装 |
| `\` + `4` | 发酵与厨具 |
| `\` + `` ` `` | 诊断与日志 |
| 单独按下并释放 `\` | 打开 Tool Set 总览 |
| 主键盘 `0` | 直接打开饰品配装 |
| `O` | 未安装外置 Simes 时打开 Simes 设置 |

快捷键路由不会替代 Minecraft 原生鼠标点击、滚轮、拖拽物品、文本输入、聊天输入、容器交互或 `Esc` 返回。文本框获得焦点时，组合键不会吞掉输入。

## 安装

1. 安装 Minecraft `1.21.11`、Java 21、Fabric Loader `0.19.5` 和 Fabric API `0.141.6+1.21.11`。
2. 将对应的 Tool Set JAR 放入该实例的 `.minecraft/mods` 文件夹。
3. 只在客户端安装即可；simMC 服务器不需要安装 Tool Set。

最新 Beta 下载见 [v1.1.9-dev.20260913.2 GitHub Release](https://github.com/MurphyPotato/simmc-tool-set/releases/tag/v1.1.9-dev.20260913.2)。历史版本、分支和正式发布记录仍保留在 [GitHub Releases](https://github.com/MurphyPotato/simmc-tool-set/releases)；源码位于 [MurphyPotato/simmc-tool-set](https://github.com/MurphyPotato/simmc-tool-set)。

## 网页地图边界

从 `v1.1.8` 起，Tool Set 不再支持 SIMMC 网页地图：地图页面、地图快捷键、Xaero 适配、HTTP 下载、地图缓存和地图相关 Mixin 均已移除。用户现有的 `simmc-tool-set-map-cache`、`xaero` 文件夹以及 Xaero 数据不会被 Tool Set 删除或修改；如需清理，请由用户自行决定。

这项移除不影响五个保留版块，也不代表旧版网页地图的线程、纹理或同步问题已经在旧版本中得到修复。

## 隐私与运行边界

- Tool Set 不包含遥测、自动上传、凭据收集或向作者发送玩家数据的功能。
- 诊断、截图、饰品数据和卷轴数据不会自动上传；诊断导出由玩家主动触发并写入本地配置目录。
- 网页地图移除后，Tool Set 不主动请求地图服务器，也不初始化地图网络客户端、调度器或缓存。
- 这是非官方玩家工具，与 simMC、Mojang Studios 或 Microsoft 无官方关联。

## 许可

原创 Tool Set 代码和文档使用 [MIT License](LICENSE)。集成模块与 Simes 衍生部分的许可、来源和授权边界见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 开发与验收

构建命令、单元测试、隔离客户端测试和当前未完成项目见 [BUILDING.md](BUILDING.md) 与 [v1.1.9 清单复核](docs/V1.1.9_CHECKLIST_REVIEW.md)。`v1.1.9-dev.20260913.2` 已通过 74/74 单元测试、隔离客户端测试和完整构建；构建成功不等于真实目标服务器验收通过。
