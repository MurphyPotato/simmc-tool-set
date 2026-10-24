# simMC Tool Set 1.2.1-dev20261024.1

在 `1.2.1-dev20260920.1` 基础上，将用户指定的
`simmc.silkysoftware.com` 加入奥术 HUD、发酵与厨具模块共用的服务器白名单。
支持裸域名和带端口形式；原有 `play.simmc.cn` 和
`110.42.44.191:7777` 入口继续保留。

目标环境：Minecraft 1.21.11、Fabric Loader 0.19.5、
Fabric API 0.141.6+1.21.11、Java 21。

本地验证：`verifyUnitTests` 107/107 通过，`build -x test` 和
隔离客户端 `-PclientSmoke runClientGameTest` 通过。
JAR 版本、客户端专用元数据、Mixin、重复 ZIP 条目和测试类排除审计通过；
`git diff --check` 通过。

新域名真实联机后的奥术、发酵和厨具行为仍待服务器实测。
本次仅构建本地中间版本，保留旧产物，不发布线上 Release。

产物：`release/simmc-tool-set-fabric-1.2.1-dev20261024.1-fabric-mc1.21.11.jar`。
SHA-256：`95CDA520776B03F5D8711F1DEADF7BE667C5136733C6F9E0436FA8AF4079B37B`。
