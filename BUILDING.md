# Building simMC Tool Set

这是 simMC Tool Set 的 Fabric 客户端模组工程。当前 `v1.1.8` 候选目标为 Minecraft `1.21.11`、Java 21、Fabric Loader `0.19.5` 和 Fabric API `0.141.6+1.21.11`。

## Prerequisites

- JDK 21，并确保 Gradle 使用的 Java 运行时也是 JDK 21。
- 项目自带的 Gradle Wrapper。
- 构建不需要 Xaero；网页地图模块已从 Tool Set 移除。

## Unit tests and build

在仓库根目录运行：

```powershell
.\gradlew.bat --no-daemon clean verifyUnitTests build -x test
```

`verifyUnitTests` 通过项目内的 JUnit launcher 运行单元测试；`build` 生成本地构建输出。构建完成后应检查 `build/libs/` 中的 JAR、版本元数据、client-only 声明和资源清单。构建产物不是正式发布，也不能替代真实客户端验收。

## Isolated client smoke test

客户端测试只能使用仓库隔离的 `run/` 目录，不要启动、修改或复用用户的 Minecraft 客户端目录。启用 Fabric API 客户端测试配置并运行：

```powershell
.\gradlew.bat --no-daemon -PclientSmoke runClientGameTest
```

测试运行目录固定为 `run/client-gametest`。这里用于检查五个保留版块的启动、总控页面、快捷键、原生 Screen 返回和不依赖 Xaero 的客户端行为。测试结束后保留必要日志，但不要把用户凭据、截图或本地游戏数据写入源代码仓库。

## Scope checks

完成构建后应确认：

- 不存在网页地图页面、地图快捷键、地图 Mixin、Xaero 适配或地图 HTTP/缓存任务；
- 五个保留版块仍由同一个 client entrypoint 提供；
- 诊断保持本地读写边界，没有遥测或自动上传；
- Simes 授权范围只包含奥术 HUD、法杖魔力、发酵和厨具；
- `docs/NEXT_IMPLEMENTATION_REVIEW.md` 中的待验收项目仍按证据状态记录，不把编译成功写成运行时通过。

## Release boundary

本地构建不会创建标签、GitHub Release、CurseForge 或 Modrinth 发布。只有主线程完成 1.21.11 版本升级、单元测试、隔离客户端、目标服务器和清单复核后，才可以讨论新的候选产物；历史版本和旧产物必须保留。
