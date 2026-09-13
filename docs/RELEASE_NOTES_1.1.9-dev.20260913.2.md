# simMC Tool Set v1.1.9-dev.20260913.2

## 中文

这是基于 v1.1.9 的中间测试构建，不是正式 Release。

- 接入厨具烹饪结果识别：通过锅内 `ItemDisplayEntity` 内容快照变化判断结果返回。
- 检测到结果后立即停止本地预计计时，并显示“烹饪完成”。
- 煎锅烹饪结果变为木炭时显示“烹饪失败”。
- 为非工作、准备中、烹饪中、完成、失败五种状态提供不发光、蓝、黄、绿、红五种状态颜色。
- 内容物种类、数量、组件或锅盖状态变化后刷新状态。
- 使用客户端渲染状态覆盖显示锅本体描边，不修改服务器实体数据。
- 修正 `ItemStack` 数量读取，保留展示实体携带的真实数量。
- 保留 v1.1.9-dev.20260912.1/.2 的奥术 HUD Mana 闪烁修复。

验证：74/74 单元测试、隔离客户端测试和完整构建通过。真实服务器厨具验收仍待进行。

## English

This is an intermediate test build based on v1.1.9, not a formal release.

- Detects cookware results from stable `ItemDisplayEntity` content snapshot changes.
- Stops the local estimate immediately and shows “Cooking complete” when a result appears.
- Shows “Cooking failed” when a skillet result becomes charcoal.
- Adds five visual states: idle/no outline, ready/blue, cooking/yellow, completed/green, failed/red.
- Refreshes state when cookware contents, counts, components, or lid state change.
- Uses a client-side render-state outline override without modifying server entity data.
- Preserves the actual `ItemStack` count carried by display entities.
- Includes the Arcane HUD Mana flicker fixes from v1.1.9-dev.20260912.1/.2.

Verification: 74/74 unit tests, isolated-client tests, and the full build passed. Target-server cookware acceptance is still pending.
