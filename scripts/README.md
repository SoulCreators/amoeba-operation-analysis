# 仓库检查

`node scripts/check-architecture.mjs` 可从任意工作目录执行，不依赖 npm 包。检查六模块 Maven 直接依赖、Java 内部 import 方向、领域层技术依赖、唯一 API/Worker 启动类、前端入口与 shared 边界，以及迁移命名和连续编号。

检查已接入 CI 的 `architecture` job 和 `make check`。这是轻量源码检查：不解析完整 Java/TypeScript AST，不检查反射、动态 import、Spring 运行时接线，也不证明历史 SQL 不可篡改。发布评审仍须检查 SQL diff、事务测试和批准的口径。
