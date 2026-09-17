# 部署目录

存放API、Worker、前端、Nginx、数据库迁移、环境变量模板、备份恢复和监控配置。

生产密钥只能通过公司密钥管理平台或环境变量注入，不得写入配置文件或提交到GitHub。

## 本地工具链

本项目支持用户级工具链，不要求管理员权限。准备好 `.toolchain/` 后，在项目根目录执行：

```bash
source deploy/scripts/env-local.sh
./deploy/scripts/build.sh
```

`.toolchain/` 仅用于本机，不提交到 Git；CI 使用工作流中声明的 JDK 8、Node.js 20。
Maven 依赖缓存也固定在 `.toolchain/m2`，避免修改用户目录。
