# v1.1.9-dev.20260913.4 (Beta)

## 中文

最新 Beta，接替 `.2` 的测试版下载入口，包含 `.3` 的改动。稳定版仍为 v1.1.9；旧版本产物保留。

### .3

- 发酵桶投放记录绑定当前目标和会话代次，切换桶时清理旧归因窗口。
- 使用烹饪钟校准时关闭旧投放/取出追踪。
- 接受明确的空材料校准结果。

### .4

- 区块卸载时不再仅因展示实体暂时不可见而立即删除发酵桶记录。
- 烹饪钟只返回材料列表时，在最后一条材料消息后等待 500 毫秒提交校准，不再依赖后续发酵状态消息。
- 保留此前的 Mana HUD 修复、厨具结果检测和五色描边。

环境：Minecraft 1.21.11、Java 21、Fabric Loader 0.19.5、Fabric API 0.141.6+1.21.11。

已有本地验证记录：.3 单元测试 74/74、隔离客户端和构建通过；.4 单元测试 74/74、完整构建通过。上述记录不代表目标服务器实测通过，跨桶投放、消息关联、卸载后恢复仍待验证。

## English

This Beta supersedes `.2` as the featured test download and includes `.3`.
Stable v1.1.9 and historical artifacts remain unchanged.

- `.3`: binds fermenter inventory attribution to a target/session, invalidates stale tracking on target changes and cooking-clock calibration, and accepts explicit empty material replies.
- `.4`: retains fermenter records during chunk unloading and commits material-only clock replies after a 500 ms quiet window.
- Includes the earlier Mana HUD fixes, cookware result detection, and colored outlines.

Recorded local checks: `.3` passed 74 unit tests, isolated-client checks and build; `.4` passed 74 unit tests and build. Target-server behavior remains unverified.
