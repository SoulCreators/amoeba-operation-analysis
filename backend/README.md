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

## 下一步实现顺序

1. Excel 模板注册、月度批次导入与完整性校验；
2. 基础映射、指标库和用户权限的配置接口；
3. 基地 / 产品线 / 事业部各自的损益结果与分析接口；
4. PVM、指标树、AI 分析和发布审计流程；
5. 用真实样表数据完成 UAT，并替换内存适配器。
