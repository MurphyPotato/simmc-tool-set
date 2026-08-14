# simMC Tool Set 1.0.0-fabric 测试方案

## 1. 测试目标

验证第一版单一客户端模组在 Minecraft 1.21.8、Fabric Loader 0.17.3 及以上环境下：

- 总控页面、六个子模块入口和鼠标操作可用；
- 默认组合键不会拦截原生 Screen、聊天、容器、滚轮或拖动物品；
- mod 3 饰品配装和 mod 4 卷轴计算的 ObjectShare 桥接按版本正确接管；
- Simes 授权功能只在 `play.simmc.cn` 激活，并在离服后清理状态；
- SIMMC 网页地图在 Xaero 已验证版本下运行，在缺失、不兼容或运行失败时给出警告且不影响其他模块；
- 配置、日志、诊断导出和客户端停止清理符合本版约束；
- 发布 JAR 不包含 Xaero 或 Mod Menu 外部 JAR，不包含网络上传或遥测运行时。

本方案把“构建通过”和“真实客户端通过”分开记录。只有所有发布阻断项通过，才能标记为可发布版。

## 2. 测试基线

### 2.1 软件与文件

| 项目 | 基线 |
|---|---|
| Minecraft | 1.21.8 |
| Fabric API | 0.136.1+1.21.8 |
| Fabric Loader | 最低 0.17.3；另测 0.19.3 |
| Java | JDK 21；优先使用项目已验证的完整 JDK，而不是 Java 8 |
| Tool Set JAR | `build/libs/simmc-tool-set-fabric-1.0.0-fabric.jar` |
| Xaero World Map | 1.39.13 Fabric 1.21.8 |
| Xaero Minimap | 25.2.16 Fabric 1.21.8 |
| mod 3 外置维护版 | `6.1.0-fabric`，提供 `simmc_travel_hunter_accessory_tool:screen_opener_v1` |
| mod 4 外置维护版 | `2.1.0-fabric`，提供 `simmc_arcane_scroll_calculator:screen_opener_v1` |
| Simes 目标服务器 | `play.simmc.cn` |

### 2.2 测试配置目录

每个测试矩阵使用独立 Minecraft 实例或备份后的实例目录，避免旧配置影响结果。测试前删除或备份：

- `config/simmc-tool-set/settings.properties`
- `config/simmc-tool-set/diagnostics/`
- 地图缓存目录 `simmc-tool-set-map-cache`
- 旧版 mod 3、mod 4 JAR（除非当前用例明确测试外置兼容性）

## 3. 自动化与静态验证

### T01：工作区与构建复现

**前置条件：** 使用完整 JDK 21；Gradle 使用已缓存的 9.5.0；网络依赖可通过现有代理解析。

**执行：**

```powershell
$javaHome = 'I:\mc smc服\simmc moster hunter 2.0\AAAsimmc aoshu jisuanqi\aoshu-scroll-calculator-v1.1.1\tools\android-env\jdk-21'
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:GRADLE_USER_HOME = 'I:\mc smc服\simmc moster hunter 2.0\github-repos\simmc-tool-set\.gradle-local'
$env:TEMP = 'I:\mc smc服\simmc moster hunter 2.0\github-repos\simmc-tool-set\.loom-tmp'
$env:TMP = $env:TEMP
\.gradlew.bat --no-daemon -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 clean build checkXaeroBuildConfiguration
```

**预期：** `BUILD SUCCESSFUL`；同时出现 `Xaero map build: ENABLED`；`build/libs/` 生成主 JAR 和 sources JAR。

**失败处理：** 编译失败先记录完整 Gradle 输出；不要把“仅 `compileJava` 成功”标记为通过。

### T02：无 Xaero 降级构建

**执行：**

```powershell
\.gradlew.bat --no-daemon -Pxaero_lib_dir=.xaero-empty clean compileJava processResources checkXaeroBuildConfiguration
```

**预期：** `BUILD SUCCESSFUL`，输出 `Xaero map build: DISABLED`；非地图模块仍可编译。

### T03：JAR 内容审计

**执行：**

```powershell
$jar = 'build/libs/simmc-tool-set-fabric-1.0.0-fabric.jar'
Get-FileHash -Algorithm SHA256 $jar
& "$javaHome\bin\jar.exe" tf $jar | Select-String 'META-INF/jars/jsoup-1.18.3.jar|xaero/|com/terraformersmc|fabric.mod.json|simmc_tool_set.mixins.json'
```

**预期：**

- 存在 `META-INF/jars/jsoup-1.18.3.jar`；
- 不存在 `xaero/`、`com/terraformersmc/` 等外部 Xaero/Mod Menu 类；
- 存在唯一客户端入口和整合资源；
- SHA-256 被记录到本次测试报告。

### T04：客户端专用与网络边界审计

**执行：** 对 JAR 解包后检查 `fabric.mod.json`、Mixin 配置和运行时类；使用文本搜索检查危险类名时只按实际类/包路径判断，不把隐私说明文本当作命中。

**预期：** `environment` 为 `client`；不出现服务端入口；没有自动上传、遥测或运行时 WebView 类；地图 HTTP 仅用于公开地图数据读取。

## 4. Minecraft 启动与总控 UI

### T05：干净实例启动

**组合：** 仅 Tool Set、Minecraft 1.21.8、Fabric API、Loader 0.17.3。

**步骤：** 启动到标题界面，进入一个本地世界，再退出世界和客户端。

**预期：** 无崩溃；日志没有 Tool Set 初始化异常；退出时没有未关闭线程、执行器或明显资源泄漏。

### T06：总控入口与导航

**步骤：** 在标题界面、世界内、背包界面分别打开总控；依次点击“总览、奥术 HUD、卷轴计算、饰品配装、发酵与厨具、SIMMC 网页地图、快捷键、诊断与日志”。

**预期：** 每个页面均能打开；标题、说明、开关和按钮不重叠；Esc 和返回按钮回到进入前的父 Screen；鼠标点击不依赖快捷键。

### T07：分辨率与 GUI 缩放

**组合：** GUI 缩放 1、2、最大值；窗口 1280x720、1920x1080；窗口化和全屏各一次。

**预期：** 总控侧栏、内容区、底部诊断入口和子页面均不溢出；长警告可读；按钮文字不截断。

## 5. 快捷键与原生输入

默认映射：`\\+1` 奥术 HUD、`\\+2` 卷轴计算、`\\+3` 饰品配装、`\\+4` 发酵与厨具、`\\+5` SIMMC 网页地图、`\\+`` ` `` 诊断与日志；主键盘 `0` 为饰品直达。

### T08：默认组合键

**步骤：** 按住 `\\`，在 750ms 内按各次键；分别测试按下、释放、快速重复、超过 750ms 后再按次键。

**预期：** 只有有效组合打开对应页面；单独按下并释放 `\\` 打开快捷键页；超时、未知次键和失焦均清除前缀状态；`0` 单独打开饰品工具。

### T09：鼠标并行操作

**步骤：** 在总控、背包、容器、地图页面进行左键点击、右键点击、滚轮、拖动物品、点击按钮；期间不按组合键。

**预期：** 所有原生鼠标行为保持可用；快捷键路由不改变点击、滚动或拖拽结果。

### T10：文本输入旁路

**组合：** 聊天、命令输入、书、告示牌、总控中的文本框或其它 TextFieldWidget。

**步骤：** 聚焦文本框后输入 `\\12345`、方向键、退格和回车；再失焦后重复组合键。

**预期：** 聚焦文本框时组合键完全不打开模块、不吞字符；失焦后恢复正常快捷键。

### T11：改键边界

**步骤：** 在 Minecraft 控制设置中将各次键改到 F1-F12、Insert 区域、SysRq、小键盘和方向键；重新测试组合键和鼠标。

**预期：** 默认值不使用这些区域；改绑后可用；冲突由 Minecraft 控制设置显示或处理；不影响鼠标入口。

## 6. 子模块功能测试

### T12：奥术 HUD

**组合：** 独立 Tool Set；目标服务器 `play.simmc.cn`；非目标服务器；离服/切服。

**步骤：** 打开总控奥术 HUD 开关；在目标服务器触发已知奥术冷却/施法消息；离服后再进入非目标服务器。

**预期：** 只在目标服务器显示 HUD；开关关闭后不显示；离服立即清空；非目标服务器不显示；其他模块仍可用。

### T13：发酵与厨具

**步骤：** 在目标服务器触发已知发酵、厨具相关消息和交互；分别关闭/开启发酵与厨具开关；离服并重新进服。

**预期：** 默认开启；提示内容只在目标服务器显示；关闭后停止显示和相关状态；离服清空；鼠标交互不被 HUD 处理器吞掉。

### T14：内置卷轴计算

**步骤：** 不安装外置 mod 4；打开卷轴计算；测试正常输入、材料排除、求解、取消、帮助、关于和返回。

**预期：** 计算结果稳定；取消不会卡死；子页面返回计算器；从总控进入后关闭/ESC 恢复总控父 Screen；`O` 键和 `/aoshuscroll` 原有独立入口仍可用。

### T15：内置饰品配装

**步骤：** 不安装外置 mod 3；在背包和容器中用主键盘 `0` 打开工具；测试扫描、来源复核、编辑、保存、方案计算、评分、帮助和返回。

**预期：** 容器和玩家物品栏可扫描；异常来源需确认；损坏库不会被静默覆盖；从总控进入后关闭/ESC 恢复父 Screen；鼠标点击、拖动物品和滚动正常。

### T16：SIMMC 网页地图正常运行

**前置条件：** Xaero World Map 1.39.13 和 Minimap 25.2.16；目标服务器及可访问公开地图数据。

**步骤：** 进入目标服务器，打开 `\\+5` 或点击地图入口；测试世界地图、背景、小地图背景、图层、搜索、收藏、在线玩家、刷新、全图和路径点按钮；切换世界/维度后再返回。

**预期：** 地图页面可打开；公开数据能显示时正确渲染；按钮状态持久化；切服/离服清空运行状态；地图线程关闭不影响退出；Xaero 原有地图功能仍可用。

## 7. 桥接与兼容矩阵

### T17：mod 3/mod 4 外置桥接

分别使用以下组合，并在总控点击对应入口：

| Tool Set | 外置 mod 3 | 外置 mod 4 | 预期 |
|---|---|---|---|
| 安装 | 无 | 无 | 使用内置饰品和卷轴实现 |
| 安装 | `6.1.0-fabric` | 无 | 饰品由外置桥接接管，内置饰品不初始化 |
| 安装 | 无 | `2.1.0-fabric` | 卷轴由外置桥接接管，内置卷轴不初始化 |
| 安装 | `6.1.0-fabric` | `2.1.0-fabric` | 两者均由外置桥接接管 |
| 安装 | 旧版或无 `screen_opener_v1` | 旧版或无 `screen_opener_v1` | 显示兼容警告，点击失败时回到诊断页；其他模块正常 |

**每个组合检查：**

- 总控状态文字准确反映内置、外置桥接或不兼容；
- 外置版本存在时内置控制器不注册事件、不启动线程、不读写对应配置；
- 外置根页面关闭、返回和 Esc 恢复同一父 Screen；
- 外置模块独立入口和 Tool Set 入口都可用；
- 未安装外置版本时不出现桥接异常。

### T18：Xaero 兼容矩阵

| World Map | Minimap | `mapExperimentalEnabled` | 预期 |
|---|---|---:|---|
| 缺失 | 缺失 | 任意 | 显示缺失提示；地图实现不启动；其他模块正常 |
| 缺失 | 25.2.16 | 任意 | 显示缺少 World Map；地图实现不启动 |
| 1.39.13 | 缺失 | 任意 | 显示缺少 Minimap；地图实现不启动 |
| 两者已验证版本 | false | 地图启用并正常运行 |
| 两者其他版本 | false | 显示版本不完全兼容、可能启动失败并可能导致崩溃的警告；仍尝试运行地图；失败后仅停用地图 |
| 两者其他版本 | true | 显示实验性兼容提示；按重启要求测试；仍尝试运行地图 |
| 独立 SIMMC Map 已安装 | 任意 | 独立地图接管；整合包不加载内部 Xaero 地图实现 |

不兼容版本提示必须明确包含：

> 版本不完全兼容，可能启动失败，且可能导致游戏崩溃；如遇上述情况，请确保 Xaero World Map 1.39.13 + Xaero Minimap 25.2.16。

## 8. 配置、日志与退出清理

### T19：设置持久化

**步骤：** 修改 HUD、发酵和地图显示开关，退出客户端，重新启动并检查；删除配置后再次启动。

**预期：** 设置写入 `config/simmc-tool-set/`；重启后恢复；删除后回到默认值；保存失败写入诊断日志而不是崩溃。

### T20：诊断日志导出

**步骤：** 打开诊断页，确认有初始化/模块状态记录；点击导出本地诊断日志；重复导出；检查文件内容和目录。

**预期：** 文件写入 `config/simmc-tool-set/diagnostics/`；只在玩家点击后导出；最多保留内存中的 120 行；没有自动上传；日志中的异常消息不包含完整敏感路径或凭据。

### T21：生命周期清理

**步骤：** 在地图、Simes、卷轴求解、饰品扫描正在运行时退出世界、切换服务器、关闭客户端。

**预期：** Simes 状态、地图快照、在线玩家和运行线程被清空；卷轴求解取消；客户端退出不挂起；重新进服不会继承上一个服务器状态。

## 9. 回归与发布门槛

### 9.1 必须全部通过

- T01、T02、T03、T04；
- T05、T06、T08、T09、T10；
- T14、T15；
- T17 中四种桥接组合；
- T18 中缺失、已验证、不兼容三类核心场景；
- T19、T20、T21。

### 9.2 阻断问题

以下任一项失败，禁止发布：

- Minecraft 启动崩溃或退出崩溃；
- 鼠标点击、容器拖拽、聊天输入被快捷键拦截；
- 外置模块存在时内置模块仍重复初始化；
- 不兼容 Xaero 版本未显示警告，或地图失败导致其他模块失效；
- 父 Screen 返回错误、Esc 返回错误或进入死循环；
- 诊断日志自动上传、保存凭据或写入不受控位置；
- JAR 错误打包 Xaero/Mod Menu，或缺少 `jsoup` 嵌套依赖；
- 构建无法在基线 JDK21 和 Loader 0.17.3 下复现。

### 9.3 建议记录格式

每个用例记录：

```text
用例 ID：Txx
日期：YYYY-MM-DD
客户端：Minecraft 1.21.8 / Loader x / Java x
模组组合：...
结果：PASS / FAIL / BLOCKED
复现步骤：...
实际结果：...
日志/截图：绝对路径
严重级别：Blocker / High / Medium / Low
```

测试结束后保留：客户端 `latest.log`、崩溃报告（如有）、Tool Set 诊断导出、JAR SHA-256、使用的模组清单和每个矩阵的结果。
