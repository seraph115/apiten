# 征信 API 平台 P7：流水落库 + 幂等 + 全链路视图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 让 `yudao-module-flow`（P0 已建，含 Kafka `FlowEventConsumer` 尽力而为骨架）真正落库：机构流水 `org_flow`（雪花 flowNo）、数据源流水 `ds_flow`（1:N 挂 flowNo）；全链路视图（flowNo 一键展开 认证→路由→数据源节点→计费）；机构幂等 `idempotent_record`（机构+产品+bizSeqNo → 首次结果）+ openapi 接线。

**Architecture:** 流水走 Kafka 异步削峰（§3.3）：openapi 已发 `apiten.org-flow`；本期 adapter 增发数据源节点事件 `apiten.ds-flow`→flow 消费落库。全链路视图=按 flowNo 关联 org_flow(1)+ds_flow(N)+charge_flow(1, 经 billing RPC 只读)。幂等（§4.1.6）：唯一键 机构+产品+bizSeqNo，窗口 24h；openapi 在雪花ID生成前查 idempotent_record，命中→返首次不重复计费。

**决策记录（架构分叉）:**
- **按月分表**：spec 要 ShardingSphere 按月分表+归档。**本期决策**：先落**单表**（create_time 带月份，分区就绪），落库/查询/幂等全 H2 可测；ShardingSphere 按月分表+归档作为收尾独立任务（H2 难测，靠 MySQL 集成验证）或紧邻后续。理由：先跑通「可追踪」，分表是扩展性优化不改语义。
- **幂等存储**：`idempotent_record` DB 表 + flow RPC 校验。备选 Redis——本期用 DB（可持久/审计/H2 可测），高并发再迁 Redis。
- **charge_flow 拼装**：全链路视图计费段经 billing `/rpc-api/billing/flow/{flowNo}` 只读拉取，不跨库直连。

**Tech Stack:** Java 21；Kafka（yudao MQ，`FlowEvent` 已在 apiten-common）；MyBatis-Plus；OpenFeign（flow↔billing 只读、openapi→flow 幂等）；H2 + EmbeddedKafka（P0 已用）。

**Spec:** v1.3 §3.3、§4.1.5、§4.1.6、§6.7、§8.4③⑩、§12.2。

## Global Constraints
- 复用 `yudao-module-flow`（`apiten_flow`；错误码段 `1_025_xxx_xxx`）。
- 新表：`org_flow`（uk flow_no）、`ds_flow`、`idempotent_record`（uk org_id+product_code+biz_seq_no）；DDL 追加 `docker/mysql/init/09-flow-schema.sql`（新建）+ H2。
- `FlowEvent` 扩：`orgId/accountId/dsInterfaceId?/bizSeqNo/chargeAmount/routeSource`；新增 `DsFlowEvent`（flowNo/dataSourceId/dsInterfaceId/rawCode/platformCode/costMs/switchSeq）。
- 每任务一 commit；Kafka 消费 EmbeddedKafka 离线测（同 P0），幂等/查询 H2 测。

## Tasks

### Task 1: org_flow 落库（Kafka 消费）+ 查询
- `OrgFlowDO { Long id; String flowNo; Long orgId; Long accountId; String productCode; String platformCode; Boolean charged; BigDecimal chargeAmount; Long costMs; String routeSource; Long dsInterfaceId; String bizSeqNo; String reqDigest; String respDigest; LocalDateTime requestTime; }`（uk flow_no；idx org_id/create_time）。
- 扩 `FlowEvent`（apiten-common）；openapi `sendFlowEvent` 填充。
- `FlowEventConsumer` 改为 insert org_flow（重复消费：flow_no 唯一，selectByFlowNo 跳过）。
- `OrgFlowService.getByFlowNo` + `getPage`（机构/产品/应答码/时间/flowNo/bizSeqNo 过滤）。
- Test：EmbeddedKafka 发 FlowEvent→消费→org_flow 有记录；重复不重复落。Commit `feat(flow): org_flow 机构流水 Kafka 消费落库 + 查询`。

### Task 2: ds_flow 数据源流水（1:N）
- `DsFlowDO { Long id; String dsFlowNo; String flowNo; Long dataSourceId; Long dsInterfaceId; String providerReqNo; String rawCode; String platformCode; Long costMs; Boolean switched; Integer switchSeq; Boolean costed; BigDecimal costAmount; LocalDateTime requestTime; }`（idx flow_no）。
- adapter 每次真实外调后发 `DsFlowEvent`→`apiten.ds-flow`；flow 消费落 ds_flow。
- Test：EmbeddedKafka DsFlowEvent→ds_flow；`getListByFlowNo`。Commit `feat(flow,adapter): ds_flow 数据源流水(1:N)消费落库`。

### Task 3: 全链路视图
- `FlowTraceService.trace(flowNo)` → `FlowTraceVO{ orgFlow, List<dsFlow>, chargeSegment(billing RPC 只读) }`；HTTP `/flow/trace/{flowNo}`（权限 `flow:trace:query`）。
- billing 补 `/rpc-api/billing/flow/{flowNo}` 只读；flow 加 `BillingFlowClient` Feign。
- Test：造 org_flow+ds_flow(H2)+stub billing→trace 完整。Commit `feat(flow): 全链路视图(flowNo→认证/路由/数据源节点/计费)`。

### Task 4: 幂等 idempotent_record + openapi 接线
- `IdempotentRecordDO { Long id; Long orgId; String productCode; String bizSeqNo; String paramsDigest; String firstFlowNo; LocalDateTime createTime; LocalDateTime expireTime; }`（uk org_id+product_code+biz_seq_no）。
- flow `/rpc-api/flow/idempotent/check`(orgId,productCode,bizSeqNo,paramsDigest) → `IdempotentRespDTO{hit, firstFlowNo, conflict}`：无→写入返 `hit=false`；有且 digest 同→`hit=true,firstFlowNo`；digest 异→`conflict=true`（openapi→`2105`）；过期视作无。
- openapi 读 `X-Biz-Seq-No`；生成 flowNo 前（`ObjectProvider<FlowIdempotentClient>` 可用且 bizSeqNo 非空）调 check：`hit`→返首次 flowNo 结果（本期返轻量「幂等命中」含 firstFlowNo，完整 data 经 org_flow 查，注记）；`conflict`→`2105`。
- 平台码补 `IDEMPOTENT_CONFLICT("2105","幂等冲突")`（apiten-common）。
- Test：check 首次/命中/冲突/过期(H2)；openapi 级 stub。Commit `feat(flow,openapi,common): 幂等 idempotent_record + check RPC + openapi 接线(2105)`。

### Task 5（收尾/可延后）: ShardingSphere 按月分表 + 归档基建
- 引 `shardingsphere-jdbc`，org_flow/ds_flow 按 `create_time` 月分表；归档冷热分离留 XXL-Job（P10）。
- **H2 难测**：靠本地 MySQL 集成 + 报告记录；范围收紧可整体延后为独立分表里程碑。
- Commit `feat(flow): org_flow/ds_flow ShardingSphere 按月分表(基建)`。

## DoD
1. flow 单测全绿（EmbeddedKafka 消费 + H2 查询/幂等）。
2. 端到端：调 query 后 `/flow/trace/{flowNo}` 展开 机构+数据源(+计费)流水；带 `X-Biz-Seq-No` 重复→同 flowNo；改参数同 bizSeqNo→`2105`。
3. 全量构建 SUCCESS。

## 后续
mail_flow（P10 邮件联动）；分表归档冷热分离（P10 XXL-Job）；幂等窗口首次完整 data 回放；决策打分明细/切换链版本写入 org_flow 扩展字段（路由二期）。
