# 后端工程

后端按模块化单体设计：`common`、`domain`、`application`、`infrastructure`、`api`、`worker`。一期先以可替换的内存适配器跑通批次、三视角和经营总览链路，数据库迁移脚本已经同步建立生产落地所需的批次、数据集、指标、结果和权限表。

详细类级目录参见：

`/Users/aydenchen-mac/Documents/自我/经营分析平台/经营分析平台_JDK8_完整代码目录设计_20260916.md`

## 本地构建

```bash
source deploy/scripts/env-local.sh
mvn -Dmaven.repo.local="$PWD/.toolchain/m2" clean test
```

## 启动 API

```bash
source deploy/scripts/env-local.sh
mvn -Dmaven.repo.local="$PWD/.toolchain/m2" -pl backend/analysis-api -am spring-boot:run
```

默认端口为 `8080`。

## 已实现的一期接口

### 健康检查

`GET /api/v1/health`

### 创建分析批次

`POST /api/v1/batches`

```json
{
  "batchNo": "202609-BASE-001",
  "period": "2026-09",
  "perspective": "BASE",
  "createdBy": "demo"
}
```

`perspective` 支持 `BASE`、`PRODUCT_LINE`、`BUSINESS_UNIT`。

### 查询批次和推进状态

- `GET /api/v1/batches/{batchNo}`
- `POST /api/v1/batches/{batchNo}/status`

```json
{
  "status": "READY_FOR_CALCULATION"
}
```

### 查询经营总览

`GET /api/v1/overview?period=2026-09&perspective=BASE&scope=ALL`

当前返回可替换的演示结果，后续将由 Excel 导入、规则计算和数据库结果适配器替换，不改变前端接口契约。

### 查询当前视角数据集目录

`GET /api/v1/datasets?perspective=BASE`

目录将基地数据集与产品线/事业部数据集分开返回，但使用同一批次和导入任务接口。

### 登记月度数据导入任务

`POST /api/v1/batches/{batchNo}/imports`

```json
{
  "datasetCode": "BASE_ACT_INC_COST",
  "fileName": "基地实际收入成本明细_2026-09.xlsx",
  "checksum": "sha256:...",
  "createdBy": "finance"
}
```

查询和推进校验状态：

- `GET /api/v1/imports/{taskNo}`
- `POST /api/v1/imports/{taskNo}/status`
- `POST /api/v1/imports/{taskNo}/validate`（multipart字段名：`file`）

```json
{
  "status": "VALID",
  "issueCount": 0
}
```

当前已支持首个工作表首行字段标题、必填字段缺失/空值、重复字段和空文件校验；错误明细已通过仓储接口落入任务链路，当前使用内存实现，数据库持久化和对象存储适配将在下一步接入。配置类导入不会复用月度业务数据入口。

### 基础配置导入

基础配置使用独立入口，不进入月度业务批次：

`POST /api/v1/config/imports`

```json
{
  "type": "SALES_PROJECT_MAPPING",
  "fileName": "销售项目清单.xlsx",
  "checksum": "sha256:...",
  "createdBy": "finance"
}
```

支持的配置类型包括销售项目映射、库存映射、费用分类、费用分摊、汇率、基地粒度、用户权限和指标定义。

### 映射关系维护

- `GET /api/v1/mappings?type=SALES_PROJECT&includeDisabled=false`
- `POST /api/v1/mappings`
- `POST /api/v1/mappings/disable`

映射使用组合键和映射值，支持更新与失效，不使用生效期间和版本字段。

### 指标库维护

- `GET /api/v1/metrics?perspective=BASE`
- `POST /api/v1/metrics`

指标公式以确定性表达式保存，经营总览可按 `ALL` 或指定视角配置。

### 三视角损益计算

- `POST /api/v1/calculations`，请求体 `{ "batchNo": "202609-BASE-001" }`
- `GET /api/v1/calculations/{batchNo}`

计算服务按批次视角执行统一损益骨架：收入、销售成本、毛利、闲置费用、基地费用、研发费用、减值、其他收益、净利润和净利率，并同时输出预算值及实际-预算 GAP。Excel校验通过后，当前由文件事实适配器读取已登记的导入文件；`raw.pnl_fact` 已预留为后续数据库事实层。预算收入成本使用 `BASE_BUD_INC_COST` 或 `PLBU_BUD_INC_COST` 数据集，与实际数据按同一分析范围汇总。

## 下一步实现顺序

1. Excel 模板注册、月度批次导入与完整性校验；
2. 基础映射、指标库和用户权限的配置接口；
3. 基地 / 产品线 / 事业部各自的损益结果与分析接口；
4. PVM、指标树、AI 分析和发布审计流程；
5. 用真实样表数据完成 UAT，并替换内存适配器。
