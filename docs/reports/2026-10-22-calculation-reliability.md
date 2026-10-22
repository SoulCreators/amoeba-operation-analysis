# 普通计算版本与零容差门禁整改

日期：2026-10-22。基线：`aa3ac6f`（任务创建时的 `codex/refactor/enterprise-project-structure`）。工作分支：`codex/fix/calculation-reliability`。范围：后端 calculation/version/PVM/结果快照；未修改前端、导入模板、配置维护域或生产 SSO。

## 结果与业务边界

普通计算的 db 路径已接入任务、输入快照、规则标识、追加结果版本、当前指针、逐项勾稽和 Outbox。同批次重算产生 R1、R2…，旧版本的结果和事实可通过带权限的历史接口读取。数据库事务失败会回滚新版本、快照、结果、检查、指针及批次状态，旧结果保持可用。

**这不是三视角完整财务口径验收，也不解除正式发布阻断。** 当前 `JdbcPnlFactProvider` 只按 scope 汇总，事实结构缺少客户/项目等正式 PVM 粒度字段；旧 PVM 实现也没有完整结构因素。每个新版范围明确保存 `PVM_FORMAL_GRAIN=BLOCKED`。因此普通 db 结果可以计算、查询，当前不能确认或发布。不能把范围层面的算术平衡视作 V1.3 正式 PVM 已实现。既有已发布版本没有回写、补造血缘或撤销。

## 实现与追踪

| 事项 | 实现与约束 | 需求/问题 |
|---|---|---|
| 原子计算 | `CalculationRunStore` 端口由 db 适配器以 REQUIRES_NEW、REPEATABLE_READ 执行，同期间数据集 advisory 事务锁及批次行 NOWAIT 锁覆盖快照、事实读取、版本物化和结果提交；API 和重试 Worker 均接线 | FR-CAL-001 / FIN-01 |
| 来源集合 | 冻结实际事实所引用的 import_task 完整记录（任务号、数据集、文件名、object_key、checksum、状态等），按 ID 排序，SHA-256；缺 VALID 任务/文件哈希/存储定位拒绝计算 | FR-CAL-001 / UAT-040 |
| 输入事实 | 快照保留 raw.pnl_fact 及 pnl_fact_trace 当时内容，历史结果回读快照中的事实，不重新读取当前 raw 表；NULL 原值保留在快照中 | FR-AUD-001 |
| 配置上下文 | 冻结现有 mapping_entry、metric_definition 的确定性 JSON。标注 `UPSTREAM_CONTEXT_ONLY`，因为现有普通计算读取已标准化事实，并未执行这些配置。不能解释为上游导入时使用的历史配置，也不补造未落库的汇率/粒度/分摊配置 | FR-CAL-001 / FIN-01 部分 |
| 规则版本 | `SCOPE_PNL_PVM_1` 明确标识现有范围汇总引擎；快照 schema 为 `ORDINARY-1`。没有宣称其等同于财经批准的三视角规则 | FIN-04 保留 |
| 结果版本 | 原有物化服务进入主链路，任务与快照先于结果写入；每个结果头及行有 calculation_version_id，当前指针事务切换，历史金额不被清理 | FIN-01 |
| 生命周期 | 普通版本确认/发布只允许更改生命周期字段，比较所有范围和金额，拒绝覆盖；版本头、结果头和事件同事务更新；数据库额外禁止血缘改写和未通过勾稽的发布 | FR-PUB-001 |
| 零容差 | 持久化来源控制数、ACT/BGT 成本组成、产品毛利、范围收入桥、范围产品毛利桥；保留 expected/actual/difference/status/source_ids。PASS 当且仅当差异精确为 0 | FR-PVM-001 / FIN-05 |
| 精度 | 结果行改用无固定 scale 的 NUMERIC，勾稽也用 NUMERIC，避免数据库结果落盘另行截断；未更改上游 raw NUMERIC(24,8) 或现有单位价 8 位 HALF_UP 规则 | FIN-05 部分 |
| 毛利桥校验 | 改为比较实际返回的销量＋价格－成本因素总和；原逻辑只比较收入 GAP－成本 GAP，可能漏掉因单位价舍入造成的因素差异 | FR-PVM-001 |
| 快照冲突 | 相同 ID、相同内容可幂等保存；不同内容明确拒绝，不再静默返回未保存的新对象。数据库禁止快照和勾稽记录 UPDATE | FIN-01 |

来源的“文件版本”目前以被冻结的任务记录及其 checksum 表达；本任务未创建导入域的 source_file_version 管理系统，未验证对象存储的原始文件长期保留。快照包含事实内容，可复核已保存金额，但没有提供自动从 Excel 重放导入/映射的服务。

任务成功记录为 SUCCEEDED，表示现有计算执行完成，不代表勾稽或发布通过。事务内失败不留下半成品版本；普通计算尚未实现独立事务的失败任务日志/阶段心跳。异常继续向调用方报告，既有 Worker 重试记录仍由其原流程维护。发布后直接重算继续被禁止，新版替代已发布结果的完整业务流程未在本轮扩展。

## API 契约

新增 `GET /api/v1/calculations/{batchNo}/versions/{versionNo}/result`，返回现有 CalculationResult 结构。Principal 是唯一用户来源；需要历史查看权限，同时继续执行结果状态、scope、明细权限。匿名返回 401；无历史/范围权限返回 403；local 不提供伪造历史结果。请求明确指定版本，没有回退到当前版本。版本头列表继续使用已有 `/versions?perspective=...`。

普通计算返回的 versionNo 现在是 R1、R2…，以 batchNo＋versionNo 定位；客户端应将其作为不透明标识，不能依赖旧 `batchNo-R1` 拼接。历史输入快照和检查表没有直接向普通用户开放，避免暴露未授权源文件及配置。

## 迁移、保留和回退

只追加 `V035__ordinary_calculation_evidence.sql`，V001—V034 未改。新增勾稽表、三个不可改写/发布门禁触发器，以及结果行 NUMERIC 精度扩展；不删除、不回填既有结果或快照。扩展字段类型可能持有表锁，应在维护窗口执行并评估真实表大小。

验证 SQL：`database/verification/check_ordinary_calculation_evidence.sql`。回退 SQL：`database/rollback/V035__ordinary_calculation_evidence.sql`。只有不存在 ORDINARY-1 快照时允许撤销结构；已有快照则拒绝回退，应保留证据、修复代码或追加前向迁移。回退不把 NUMERIC 缩回 (24,8)，以免丢失精度。若其他并行分支也新增 V035，集成前需按未执行迁移的合并顺序重新分配编号；不得改写已经部署的编号。

## 验证

后端 946 项测试通过，0 失败、0 错误、0 跳过；35 项迁移及 16 项验证 SQL 通过，`make check` 与 `git diff --check` 通过。最终验证数据见同目录 `2026-10-22-calculation-reliability-validation.json`。使用新建的隔离 PostgreSQL 15.18 数据库、Zulu JDK 8、Maven 离线依赖缓存；没有连接生产财务库。首次默认 Maven 缓存不足且 Central 返回 403，改用本机项目已有缓存后完成验证，没有更改项目依赖或镜像配置。

新增覆盖：版本 R1/R2 及源文件变化、历史金额/事实与范围授权、结果写入后异常回滚、来源证据不足、授权拒绝无写入、独立数据库会话批次锁及跨批次的同期间数据集锁、零量/正式粒度阻断、循环小数非零残差、快照 ID 冲突和数据库防改写、历史 API 主体与匿名拒绝、成本组成微小差异和缺项拒绝。旧快照冲突测试更新为“抛错且原值保留”，未削弱原数据保留断言。

另验证全量迁移、所有验证 SQL，以及 V034 有数据库增量升级至 V035：遗留 NULL 版本结果和 8 位小数金额保留；未产生普通快照时执行回退再升级成功。没有运行前端测试（未修改前端），没有远程 CI、财经黄金样本或生产 UAT。

## 保留阻断与下一步

- YU 定义/单位/来源、事业部经营分析利润完整公式：仍待财经确认；本轮不增加任何公式。
- 零量、新增/退出 PVM：`PVM_ZERO_VOLUME=BLOCKED`，不除零、不将残差塞入因素；待确认后应新增规则版本。
- 正式 PVM 粒度/结构因素、三视角独立损益及材料损耗是否已含材料成本：现有证据不足；保留发布阻断，不能将本轮“成本组成控制检查”解释为已批准新的成本公式。
- 重复小数的单位价精度仍沿用现有实现，非零残差明确 FAIL；未放宽容忍度，也未自行决定尾差归属。FIN-05 尚未全部关闭。
- 历史上游配置/文件版本治理、全链路分摊/累计/调整守恒、独立失败任务日志及真实数据复算验收仍未完成，FIN-01 只能标记主链路已贯通、整体部分完成。
- 现有 local 内存模式仍仅用于开发；本报告的持久化保证限于 db 模式。

## Git 与交付状态

开始时执行 `git push --dry-run origin HEAD:refs/heads/codex/fix/calculation-reliability`，GitHub 明确返回 403：`Permission to SoulCreators/amoeba-operation-analysis.git denied to mengchen4816-dot`。没有推送、创建 PR 或部署；本轮以本地中文 Conventional Commit 交付。提交号由最终回复提供。获得仓库写权限或由有权限维护者接收提交后，才可执行推送及远程 CI。
