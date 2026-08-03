# Adding a Tool

Each tool must use its own public repository under `MurphyPotato` and provide:

- A beginner-oriented Chinese README
- A direct link to the latest downloadable package
- Versioned releases that do not overwrite older packages
- A source license and third-party notices
- Build and verification instructions
- No credentials, user data, private screenshots, game/mod JARs, or machine-local caches

Update the table in `README.md` only after the tool repository and its first public release are accessible.

## 发布约定

- 每个工具使用独立仓库，避免版本和依赖互相污染。
- 新功能迭代使用新版本号；为现有同版本补齐平台安装包时，Windows 与 Android 合并到同一个 Release。
- 玩家发行包必须离线可用，并公开源码、构建方式、许可证和第三方声明。
