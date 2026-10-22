# 本地开发与验证

## 工具链

仓库既有基线为 JDK 8、Maven 3.5+、Node 20（与 CI 一致）及 PostgreSQL 16（CI 服务）。本轮本机实际使用 Zulu JDK 8u352、Maven 3.9.9、Node 26.5.0、PostgreSQL 15.18；本机结果不能替代 PostgreSQL 16 和 Node 20 的 CI 结果。

`source deploy/scripts/env-local.sh` 优先保留已有 JAVA_HOME，其次尝试仓库 `.toolchain` 或 macOS 已安装的 JDK 8；不会下载工具。运行 `mvn -version` 确认实际 Java，避免误用机器默认 JDK 23。选择 Java 8 的依据是既有项目兼容要求，后续升级需单独验证。

Node 26 下，本次 happy-dom/Vitest 基线出现原生 localStorage 冲突。使用 Node 20 与 CI 对齐；若临时使用 Node 26，可执行：

```bash
cd frontend
NODE_OPTIONS=--no-experimental-webstorage npm run test:unit
npm run build
```

该参数是本次环境验证办法，不作为生产运行参数。

## 数据库与测试

创建独立的测试数据库，禁止对生产库运行契约测试。使用环境变量注入连接；测试要求 `AMOEBA_DB_URL`、`AMOEBA_DB_USERNAME`、`AMOEBA_DB_PASSWORD` 存在，部分测试要求值非空。

先按文件名顺序执行 `database/migration/V*.sql`，再执行 `database/verification/*.sql`，均使用 `psql -v ON_ERROR_STOP=1`。下面只适用于新建、空的测试库；既有数据库必须按已执行版本增量迁移：

```bash
# PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD 由本地环境配置
for migration in database/migration/V*.sql; do
  psql -v ON_ERROR_STOP=1 -f "$migration" || exit 1
done
for verification in database/verification/*.sql; do
  psql -v ON_ERROR_STOP=1 -f "$verification" || exit 1
done
# 同一测试库对应的 AMOEBA_DB_* 另行设置；JDBC 不读取 PG* 变量。
make check
mvn -B -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-$PWD/.toolchain/m2}" clean verify
cd frontend
npm ci
npm run test:unit
npm run build
```

CI 已检查 PostgreSQL 契约测试不得跳过。无数据库时 `mvn verify` 失败是有效阻断，不应通过跳过测试宣称验收完成。

## API 与浏览器联调

```bash
# 从仓库根目录，完成 Maven 打包后启动；仅绑定本机。
java -jar backend/analysis-api/target/analysis-api-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local --server.address=127.0.0.1
# 另一终端：
cd frontend
npm run dev -- --host 127.0.0.1
# 安装匹配锁文件版本的浏览器后执行：
npx playwright install chromium
npm run test:e2e
```

只有 `local` Profile 接受前端的开发身份。生产必须使用可信认证接线，并禁用 `local`，不能把请求头直接转为可信身份。

## 构建与回滚

`deploy/scripts/build.sh` 从任意目录定位仓库根目录，执行架构检查、后端 clean verify、前端单测与构建；不再默认跳过测试或强制使用公共镜像。公司制品仓库可通过 Maven 用户配置接入，`deploy/config/maven-settings.xml` 仅保留为显式可选配置。

本轮没有数据库 DDL、接口路径、财经公式或依赖版本变更。回滚应还原同一批前端路径与引用，再重建前后端产物；不要只回滚单个入口文件。原版本基线是 `c8ddc820c2806f886edadbaa06619cfc5cd879fb`。
