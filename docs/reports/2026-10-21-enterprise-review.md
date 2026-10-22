# 阿米巴经营分析平台：代码分析与企业级目录优化报告

> 本文保留第一阶段评审时点。后续 FIN-02/03 的修复、验证与仍未实现的能力见 [第二阶段整改报告](2026-10-21-financial-display-remediation.md)。第一阶段的 931/39/4 项测试计数属于当时验证，不覆盖后续改动。

评审日期：2026-10-21
来源：[SoulCreators/amoeba-operation-analysis](https://github.com/SoulCreators/amoeba-operation-analysis)
拉取基线：`c8ddc820c2806f886edadbaa06619cfc5cd879fb`（main）
本地工作分支：`codex/refactor/enterprise-project-structure`
范围：代码拉取、核心业务链路审查、工程目录优化、现有自动化验证和后续整改建议。

## 1. 结论

项目已经具备可继续演进的模块化单体基础：六个后端模块、独立 API/Worker、三视角事实与损益计算、权限过滤、导入追溯、导出快照、人工修订、重试和事件投递。它并非仅有演示页面或空工程骨架。

本轮已完成第一阶段工程目录优化，保持 Maven 模块、数据库迁移、接口契约及财务公式稳定，统一应用入口，清理废弃骨架，将前端传输、契约、页面、导航、样式分开，整理项目文档，并增加可执行的工程边界检查。

**尚不能据此认定项目已达到企业级财经生产上线条件。** 关键差距是普通计算与版本快照的贯通、前端 FC/累计口径表达、生产身份认证、迁移治理、实际数据黄金样本及运维验收。测试通过只能证明已覆盖的行为，没有覆盖到的业务链路仍需整改和验证。

本次只在本地修改，未提交、推送、创建 PR 或部署。未接触真实财务数据库。

## 2. 评审方法与证据边界

- 读取根 POM、六个模块 POM、Spring 启动与 Profile、控制器、核心应用服务、领域模型和 JDBC/POI 适配器。
- 追踪导入→事实→计算→确认/发布→查询/导出，以及独立版本、人工修订、重试和 Outbox 的调用关系。
- 核查前端实际入口与 API 客户端，不将独立 `demo/index.html` 视为正式应用。
- 在隔离 PostgreSQL 15.18 测试库执行迁移和校验，并运行 JDK 8 全模块测试；使用构造测试数据。
- 执行前端已有单测、构建和浏览器验证，记录环境差异与失败原因。

源码审查属于有重点的技术评审，不是全量安全审计，也不是财经核算口径签字。未完成真实样表 UAT、渗透测试、负载测试、灾备恢复和 GitHub 分支保护核验。本文的问题等级用于整改排序，不等同于外部监管合规结论。

## 3. 项目现状

### 3.1 技术与规模

| 项目 | 当前实现 |
|---|---|
| 后端 | Java 8、Spring Boot 2.7.18、Maven 六模块 |
| 前端 | Vue 3、TypeScript、Vite、Axios；声明了 Pinia/Router/ECharts，但主工作台仍集中编排 |
| 数据访问 | 实际主线是 Spring JDBC/JdbcTemplate；声明 MyBatis starter 不等于主线使用 Mapper |
| 文件解析 | Apache POI 5.2.5 |
| 持久化 | PostgreSQL；可选 filesystem 和内存适配器 |
| 业务视角 | BASE / PRODUCT_LINE / BUSINESS_UNIT |
| 数据库 | 34 个迁移 SQL、15 个校验 SQL、16 个回滚 SQL |
| Java 文件 | 优化后 285 个生产类文件、243 个测试类文件；移除 6 个旧骨架文件 |
| 已执行测试 | 后端 931 项；前端单测 39 项；其他验证见第 8 节 |

以上数量来自本次工作树文件与测试报告，不是需求完成率。领域模块没有独立测试目录中的测试类，其行为主要由 application 等模块的测试覆盖；不应将其解释为领域逻辑完全未测或完全覆盖。

### 3.2 后端模块关系

| 模块 | 当前责任 | 优化判断 |
|---|---|---|
| common | 通用响应模型 | 保持精简，避免成为业务杂物目录 |
| domain | 批次、事实、结果、权限、版本、生命周期及仓储契约 | 保留内层地位，后续按业务域拆包 |
| application | 导入、计算、权限、查询、导出、修订和重试用例 | 服务集中，逐步拆领域服务与编排 |
| infrastructure | JDBC、Excel、文件、内存、AI 适配器 | 保持实现内层端口的方向 |
| api | HTTP、DTO、异常映射、认证主体读取 | 唯一入口已明确；生产认证仍需接线 |
| worker | 导出生成/清理、计算重试、生命周期事件投递 | 保留独立进程，维持显式 Spring 导入 |

Maven 直接内部依赖：`api/worker → infrastructure → application → domain → common`。当前规模适合保留模块化单体；没有证据表明现在拆微服务能解决主要问题。

## 4. 代码逻辑

### 4.1 导入和数据质量

`BatchController` 创建带期间和视角的批次。月度导入与配置导入是不同入口和任务类型。月度数据经过数据集目录、POI 模板校验后，保存原文件、问题列表、标准 `PnlFact` 和来源行信息。

`ImportTaskServiceImpl.validate()` 带事务；VALID 时成套调用事实读取、来源读取、事实替换和来源替换，异常时尝试恢复原文件快照。JDBC 事实按任务替换，减少重复校验造成的金额叠加。完整性和质量服务汇总必需数据集、VALID 状态和问题数。

这是一条已有实质实现的链路，但数据库事务不自动覆盖文件系统。补偿失败、进程中断、跨机共享存储和长期保留仍需单独验收。

证据：[导入服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/ImportTaskServiceImpl.java)、[事实来源写入](../../backend/analysis-infrastructure/src/main/java/com/sunwoda/evb/finance/analysis/infrastructure/jdbc/JdbcPnlFactSourceWriter.java)。

### 4.2 损益计算

`CalculationServiceImpl.calculate()` 校验操作者、批次状态和涉及的 scope 权限，将批次推进至 CALCULATING，读取标准事实，按 scope 汇总为损益结果。金额使用 BigDecimal；结果保存后推进 CALCULATED，执行失败保留 FAILED 轨迹。

当前通用骨架的主要公式为：

```text
毛利 = 收入 − 销售成本
其他损失 = 资产减值 + 信用减值
净利润 = 毛利 − 闲置费用 − 基地费用 − 研发费用 − 其他损失 + 其他收益
净利率 = 净利润 / 收入（当前收入为零时返回零）
GAP = 实际 − 预算
```

预算使用对应字段计算。当前三视角共用这段汇总公式，PVM 通过视角返回不同逻辑标识；这并不证明三套独立财经口径已经全部实现。正式口径需逐项对照批准的样表。

证据：[计算服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/CalculationServiceImpl.java) 的 `calculateInternal` / `MutablePnl.toLines`、[PVM 服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/PvmServiceImpl.java)。

### 4.3 确认、发布与版本

结果生命周期为 CALCULATED → CONFIRMED → PUBLISHED。确认和发布服务带事务，批次状态与结果状态在同一数据库事务内更新。发布前调用 PVM 勾稽校验；已确认/已发布批次不允许直接重算。

仓库已有 `CalculationVersion`、`CalculationSnapshot`、人工修订、Outbox、租约与幂等适配器。然而普通计算入口仍创建 `batchNo + "-R1"`；`CalculationVersionMaterializationService` 是独立物化服务，当前控制器到普通计算服务的调用链没有调用它。因此“具备版本类和历史表”与“每次计算都能完整复现”之间仍有差距。

JDBC 结果仓储有版本头时按该版本保存；没有版本头时清理旧的 NULL 版本结果。这说明目前保留了兼容路径，不能宣称每次重算自动追加不可覆盖的历史版本。

证据：[版本物化服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/CalculationVersionMaterializationService.java)、[结果仓储](../../backend/analysis-infrastructure/src/main/java/com/sunwoda/evb/finance/analysis/infrastructure/jdbc/JdbcCalculationResultRepository.java)、[计算控制器](../../backend/analysis-api/src/main/java/com/sunwoda/evb/finance/analysis/api/controller/CalculationController.java)。

### 4.4 权限与身份

控制器使用 Principal 提取操作人，不把请求体中的工号当作权限依据。`PermissionServiceImpl` 区分范围、明细、导出、配置编辑、计算、确认和发布。总部财经写权限仍检查视角和 scope；ADMIN 全局读取能力与写操作隔离，不能简单描述成“管理员拥有全部写权限”。

本地 `LocalPrincipalFilter` 仅在 `local` Profile 接收 `X-User-Id` 并构造 Principal。它是开发适配器，不是正式 SSO。代码中未看到完整的生产身份验证链路；公司网关、会话/Token 校验和应用端信任边界需联调验收。仅转发一个普通 HTTP 用户头不会自动成为可信 Principal。

证据：[权限服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/PermissionServiceImpl.java)、[本地身份过滤器](../../backend/analysis-api/src/main/java/com/sunwoda/evb/finance/analysis/api/config/LocalPrincipalFilter.java)。

### 4.5 报表、导出与 AI

总览、指标树、PVM 和 V1.3 服务读取用户可见结果。FC 有独立版本及快照；单位效率、风险和部分专题会返回待数据/待口径状态。导出服务保存请求版本及授权范围快照，并在下载时重新核验权限；Worker 负责文件生成、到期清理和任务处理。

AI 当前 `RuleBasedLocalAiGateway` 生成六段规则摘要。已有草稿和发布流程，但它不是实际大模型网关；未来模型接入需记录模型/提示词版本、超时、可见范围和人工审核，不应参与账务数字计算。

证据：[导出服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/ExportServiceImpl.java)、[V1.3 服务](../../backend/analysis-application/src/main/java/com/sunwoda/evb/finance/analysis/application/service/V13AnalysisServiceImpl.java)、[规则摘要实现](../../backend/analysis-infrastructure/src/main/java/com/sunwoda/evb/finance/analysis/infrastructure/ai/RuleBasedLocalAiGateway.java)。

## 5. 企业级财经差距与整改优先级

P0 表示正式使用相关功能前必须解决或明确下线；P1 表示生产化重要整改；P2 表示后续可维护性增强。以下除标注“本轮已处理”外均为待办。

| 编号/级别 | 已核验事实与影响 | 建议及验收条件 |
|---|---|---|
| FIN-01 / P0 | 普通计算固定 R1；版本物化未进入该主链路，NULL 版本结果会被替换 | 将输入/配置/公式版本、结果、当前指针、生命周期事件贯通；两次重算产生独立版本，旧结果可查询/复现；失败时不留下半成品 |
| FIN-02 / P0 | 工作台 `scenarioPair()` 默认用 `budget + (actual-budget)*0.72` 构造 FC；`applyMatrixPayload()` 把实际值放入第三个 FC 位置，矩阵请求又未发送 scenario | 删除合成预测的业务路径，使用已发布 FC 版本的服务端结果；缺 FC 明确阻断。测试缺版本、部分覆盖和 ACT_FC/FC_BGT 切换，不能将 ACT 当 FC |
| FIN-03 / P0 | `periodMode` 提供累计/自定义文案，但 API 客户端没有对应月份集合或聚合方式契约；V1.3 主要按 batchNo 请求 | 明确期间集合、批次合并和口径；实现后端授权聚合及版本记录，或下线未实现的筛选；黄金样本验证跨月金额、比率重算、缺月状态 |
| SEC-01 / P0 | 正式认证尚未在仓库形成完整接线；local 接受可由调用方提供的身份头 | 接入可信认证，部署禁止 local；验证伪造头、匿名、过期身份、禁用用户、跨 scope 请求均受控 |
| FIN-04 / P1 | 三视角目前共用 MutablePnl；PVM 视角标识不同但核心算法共享 | 财经逐项批准差异规则、单位、符号、币种、分摊、零量和舍入；为三视角建立独立验收样本，不以改逻辑标识代替公式实现 |
| FIN-05 / P1 | PVM 单价按 8 位舍入，勾稽使用严格零判断；重复小数可能留下微小残差 | 先由财经决定精度和允许差异/残差归属，再实施。样本覆盖 100/3 等循环小数、零量、负值和大额；不得直接随意放宽发布阈值 |
| OPS-01 / P1 | SQL 有递增迁移，但没有执行历史/校验和管理；已有回滚文件未覆盖全部 34 个迁移 | 明确迁移工具/执行台账、检查和备份策略；在既有版本上增量升级与恢复演练。不能简单反向执行所有回滚 SQL |
| OPS-02 / P1 | Dockerfile 未设置非 root 用户；仅有基础容器定义，缺少完整生产运行约束和恢复证据 | 按企业环境补齐 UID、可写挂载、探针、资源、日志脱敏、告警、备份恢复和发布回退；API/Worker 共享文件与任务状态需联合验收 |
| GOV-01 / P1 | `.github/CODEOWNERS` 仍为 `@project-owner` 占位；旧清单把已实现审计/导出等列为待开发 | 配置真实团队所有者和分支保护；按需求编号维护实现/测试/UAT/发布矩阵。本轮已修正文档主入口，但保留旧清单作为历史状态 |
| ENG-01 / P1 | 工作台仍聚集多个业务视图和状态；类型检查未作为独立 CI 门禁 | 逐功能抽取页面和组合函数，引入组件级类型检查；以完整用户流程测试保护拆分，保留权限与错误状态 |
| ENG-02 / P1 | application 内仍存在 memory/db 适配实现，Java 横向 service/model 包较集中 | 按 ingestion/calculation/forecast 等领域渐进拆包；先固定依赖和行为，再迁移适配器到 infrastructure，验证 Spring 接线 |
| AI-01 / P2 | 当前 AI 为规则摘要，正式模型能力未落地 | 若需要模型，单独接入并验证范围继承、提示词/模型版本、失败处理及人工发布；不阻断确定性财务规则建设 |

FIN-02、FIN-03 是静态代码路径发现，本轮没有业务数据复现测试，因而不宣称已量化错误发生频率。它们明确指向上线前的专项测试，而不是以现有空批次浏览器测试通过代替验证。

另外，确认与发布当前允许同一人，源码对此有明确说明。本报告不擅自改为强制双人。是否需要制单/复核职责分离，应由企业财经内控负责人决定并形成版本化规则。

## 6. 本轮目录优化

### 6.1 已实施的变更

| 原位置/行为 | 当前实现 | 目的 |
|---|---|---|
| `00_项目管理/` | `docs/governance/` | 将章程、排期与分支规则归入统一治理目录 |
| 根目录 `01_需求与业务规则.md` | `docs/requirements/business-rules.md` | 收敛需求入口 |
| `docs/待开发清单_20261002.md` | `docs/planning/待开发清单_20261002.md` | 区分计划与当前核验结果 |
| 20260920 历史清单 | `docs/archive/` | 与当前计划分开，保留历史语义 |
| main.ts 直接挂载 AppFinal | `main.ts → App.vue → WorkbenchPage.vue` | 单一、稳定的前端入口 |
| AppFinal 内的导航和样式 | `features/workbench/navigation.ts` / `workbench.css` | 独立维护导航与原样式 |
| 单文件 `src/api.ts` | `shared/api/contracts.ts` / `http.ts` / `index.ts` | 分离 DTO、传输、接口调用 |
| `frontend/tests/*.spec.ts` | `frontend/tests/unit/` | 与 E2E 测试分层，现有断言不删除 |
| 六个旧 amoeba 骨架类 | 删除无引用骨架，保留实际 Worker 包 | 避免误启动旧 API 和重复模型误用 |
| 空架构提纲、失效个人绝对路径 | 架构说明、ADR、本地开发文档、核验报告 | 让仓库独立可理解、可验证 |
| 构建默认跳过测试、强制公共镜像 | 路径无关的 build.sh，执行测试，不强制镜像 | 避免构建绕过已有门禁 |
| 无可执行目录规范 | `scripts/check-architecture.mjs`、Makefile、CI architecture job | 持续校验边界和启动入口 |
| 运行产物保护不完整 | `.gitignore`、`.editorconfig`、`.gitattributes`、前端 `.nvmrc` | 降低运行数据误提交和环境差异 |

保留 `backend/`、`frontend/`、`database/`、`deploy/`、`templates/` 和原 Maven 模块坐标。没有引入空的微服务目录，也没有在本轮批量改 Java 主业务包。

### 6.2 已建立的约束

仓库检查验证内部 Maven 依赖、Java import 方向、domain 不直接依赖 Spring/POI/SQL/JPA、一个 API 与一个 Worker 启动类、前端共享层不反向依赖页面、迁移命名和连续编号。

另外用隔离目录验证了反例：反向领域依赖和重复迁移编号均被拒绝。该脚本使用文本规则，不是完整语法解析器，不覆盖反射、全限定类名、动态导入和运行时接线。生产验收仍依赖 Spring 上下文、数据库和用户流程测试。

目录全景见 [架构文档](../architecture/README.md)，决策取舍见 [ADR-0001](../architecture/decisions/0001-enterprise-repository-layout.md)。

### 6.3 明确未做的改动

- 未修改财务计算公式、发布策略、API 路径、数据库迁移或依赖版本。
- 未将六模块改为微服务，未大规模改动 Java package。
- 未接入 SSO、真实对象存储或正式 AI 模型。
- 未将工程治理完成等同于 FIN/SEC/OPS 问题已关闭。

## 7. 建议的后续实施顺序

| 阶段 | 工作包 | 依赖与验收 |
|---|---|---|
| A：当前成果评审 | 合并目录整理；确定代码所有者和必要 CI | 审核本报告、路径迁移及完整测试证据；主分支保护实际生效 |
| B：正式数字边界 | FIN-02/03 + SEC-01 | 修正 FC/期间语义或隐藏未实现入口；可信身份接线；验证无数据、无权限、无版本都不能展示正式数字 |
| C：可复现计算 | FIN-01 + FIN-04/05 | 财经批准公式与精度；建立输入/配置/公式/结果版本链；并发重算、失败回滚、旧版查询与复算一致 |
| D：生产运行 | OPS-01/02 | 迁移历史、灾备演练、最小权限容器、文件生命周期、监控和容量测试；明确 RPO/RTO 并测量 |
| E：业务域维护 | ENG-01/02 + 专题增量 | 每次只拆一个领域，配套行为测试、ADR 和接口文档；无跨域直写与反向依赖 |

建议责任角色：财经负责人批准口径和验收样本；后端负责人负责版本/事务/权限；前端负责人负责状态语义和展示；DBA/SRE 负责迁移、恢复、资源和告警。未掌握团队人数、工作量与交付窗口，本报告不编造具体工期。

企业级财经验收至少应覆盖：同一快照可复算、发布版本不可静默覆盖、报表与导出同版本同权限、金额与来源勾稽、比率按汇总分子/分母重算、缺数据不补假数、审计能够关联操作人/批次/版本，以及备份可恢复。

## 8. 验证结果

### 8.1 已完成

| 验证项 | 结果 | 环境及边界 |
|---|---|---|
| 仓库拉取 | 成功 | main 基线 c8ddc82，独立本地重构分支 |
| 架构检查 | 通过 | 6 模块、2 启动类、前端边界、34 迁移编号 |
| 全量 SQL 迁移 | 34/34 通过 | 新建的一次性 PostgreSQL 15.18 测试库 |
| SQL 校验 | 15/15 通过 | 同上，无生产数据 |
| 后端 clean verify | 931 项通过，0 失败/错误/跳过 | JDK 8u352、Maven 3.9.9；API/Worker 均打包成功 |
| 前端基线与重构后单测 | 均为 39/39 通过 | 7 个测试文件；Node 26 采用下述兼容参数 |
| 前端生产构建 | 通过 | Vite 成功输出 dist；无依赖锁变更 |
| 浏览器 E2E | 4/4 通过 | Playwright 1.48.2、Chromium 130、Node 24.19；其中 1 项 multipart 使用 stub 后端，其余使用 local API/故障注入 |

后端分布：application 318、infrastructure 309、api 278、worker 26。详细计数来自 Surefire XML，见 [验证摘要](2026-10-21-validation.json)。

### 8.2 测试过程中发现的环境问题

1. 首次 Maven 执行缺少 `AMOEBA_DB_*`，数据库契约正确失败/跳过，不能算通过。建立隔离库、执行迁移并配置连接后，全模块重新 clean verify，最终零跳过。
2. 本机默认 Maven 使用 JDK 23，验证时显式选择已安装 JDK 8。新环境辅助脚本不再无条件指向不存在的 `.toolchain/jdk8`。
3. Node 26 的原生 localStorage 与当前 happy-dom 环境冲突，原始前端基线即出现 26 项失败。使用 `NODE_OPTIONS=--no-experimental-webstorage` 后，基线与重构后均通过 39 项；CI 仍按既有 Node 20，前端 `.nvmrc` 与之对齐。
4. Playwright 1.48.2 在本机 Node 26 安装浏览器时出现解压阶段挂起，改用应用自带 Node 24 进行兼容验证；没有修改锁文件或 E2E 业务断言。

本地 PostgreSQL 为 15.18，仓库 CI 服务为 16；本轮未触发远程 CI，不能宣称已通过 GitHub 的 PostgreSQL 16/Node 20 组合。也未执行生产容器部署、压测、真实样表和恢复演练。

## 9. 交付与回滚

交付包括本地完整仓库、目录整理、架构检查、更新后的说明和本报告。旧骨架与原路径可通过 Git 基线追溯，未复制成第二套可执行代码。

目录变更需要整体回滚前端路径、import 和测试引用，并重建产物；后端旧启动骨架如需恢复也必须作为同一变更评审。本轮没有数据库结构变化，不需要执行任何数据库 rollback SQL。现有测试数据库与本轮启动的服务在验证后关闭。

下一次评审应优先确认 FIN-01～03 和 SEC-01，而不是仅检查目录名称是否“像企业项目”。

## 10. 外部设计依据

本报告的实现结论以本地源码和实测为准。以下官方资料仅支持工程建议，不证明本项目已满足对应要求。

- Spring Boot 2.7.18 文档说明其 Java 最低版本为 8；本轮保留既有 JDK 8 基线，技术栈升级应另立兼容与依赖安全评估。[Spring Boot 系统要求](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements)
- 默认拒绝、最小权限和每次请求验证授权，是生产身份与范围控制的设计依据。[OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- 数据库备份应明确采用逻辑备份、文件系统备份或连续归档，并验证恢复；有回滚 SQL 不能替代恢复演练。[PostgreSQL 备份与恢复](https://www.postgresql.org/docs/current/backup.html)
