# 征信 API 平台 P9：配置发布/回滚 + 快照热更新 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development（涉热更新/缓存一致性）. Steps use checkbox (`- [ ]`).

**Goal:** 落地配置版本化发布/回滚（§5.4）：管理面改配置「草稿」→「发布」生成带版本号的**配置快照**→推送刷新各节点本地缓存（秒级）→可回滚任意历史版本；调用链读配置从**本地快照缓存**（替代 P5/P6b 每调用 DB/RPC），补 P5 终审的韧性缺口（热路径 SPOF/无缓存）。

**Architecture:** config_release 存版本快照（发布人/时间/diff/影响范围/序列化 blob）。发布：把「当前生效配置」序列化为快照→写 config_release(版本+1)→经 **Redis Pub/Sub**广播版本号→各消费节点（openapi/adapter/route/billing）Caffeine 本地缓存监听→拉快照热更新。调用链读配置零 DB/零跨服务 RPC（纯缓存 §3.2）。回滚=选历史版本重新广播。

**决策记录（架构分叉）:**
- **快照载体**：本期 **Redis**（`config:snapshot:<domain>:<version>` JSON blob + `config:current:<domain>` 当前版本 + Pub/Sub `config-refresh`）。备选 Nacos——Redis 更轻、已依赖；生产可换，注记。
- **消费者缓存**：Caffeine + Pub/Sub 监听 + 启动全量拉 + 短 TTL 兜底（漏消息最终一致）。
- **纳入范围（本期）**：route_config、charge_code_rule、billing_template(+tier)——即 P5/P6 引入的热点。选源/切换/分流/应答码映射/限流参数留各自里程碑接入。
- **审批**：敏感对象发布记审计，审批流复用 yudao bpm 留后续。
- **离线可测边界**：快照序列化/版本递增/回滚选版 纯逻辑 H2/单测；Redis Pub/Sub + Caffeine 热更新靠集成测（EmbeddedRedis 或本地）+ 报告。

**Tech Stack:** Java 21；Redis（Redisson，快照 + Pub/Sub）；Caffeine（本地缓存）；Jackson；MyBatis-Plus；H2 + EmbeddedRedis（可选）。

**Spec:** v1.3 §3.2、§5.4、§6.6、§7 config_release、§9.2。

## Global Constraints
- **决策**：新建 `yudao-module-config` 控制面模块（`apiten_config`、错误码段 `1_026_xxx_xxx`）承载 config_release + 发布/回滚 + 快照编排；消费模块接入 `ConfigSnapshotCache`。备选并入 route——否决（跨多域，独立更清晰）。
- apiten-common 新增 `cn.apiten.common.config.{ConfigSnapshotDTO, ConfigRefreshEvent}`。
- 每任务一 commit；纯逻辑离线测，热更新集成验证。

## Tasks（骨架——基建重，执行时按决策细化）

### Task 1: yudao-module-config 模块 + apiten_config + config_release CRUD
- `ConfigReleaseDO { Long id; String domain; Integer version; String snapshotJson; String publisher; String changeDiff; String scope; Integer status(0草稿 1已发布 2已回滚); LocalDateTime publishTime; }`（uk domain+version）。
- CRUD + `getCurrent(domain)`（最大已发布版本）+ `getHistory(domain)`。DDL `10-config-schema.sql` + H2。
- Commit `chore/feat(config): 新建 yudao-module-config + config_release 版本表 CRUD`。

### Task 2: 快照编排 + 发布/回滚服务
- `SnapshotService.publish(domain)`：从源模块拉当前配置（route_config/charge_code_rule/billing_template 经只读 RPC 或 dump）→序列化→version+1→写 config_release(已发布)→写 Redis→Pub/Sub 广播。
- `rollback(domain, version)`：选历史版本→重写 `config:current`→广播→记审计。
- Test：publish 版本递增+快照内容+rollback 选版（H2 + mock Redis）。Commit `feat(config): 配置快照发布/回滚编排 + Redis 载体 + Pub/Sub`。

### Task 3: ConfigSnapshotCache 消费端
- `@Component ConfigSnapshotCache`：启动全量拉 Redis→Caffeine；订阅 `config-refresh`→拉新版本热更新；`get(domain,key)`；漏消息短 TTL 兜底重拉。
- Test：命中/版本变更热更新/兜底重拉（EmbeddedRedis 或 stub）。Commit `feat(config): ConfigSnapshotCache 消费端(Caffeine+Pub/Sub+兜底)`。

### Task 4: 接入 route/billing 读缓存（替换每调用 DB/RPC）
- **route** `RouteResolver`：route_config 改读 `ConfigSnapshotCache`（未命中兜底查 DB）；**billing** `ChargeCalculator`：charge_code_rule/template 改读缓存。补 P5 终审韧性缺口（route 本地缓存命中减跳/减 SPOF）。保留 DB 兜底（缓存未就绪降级+告警）。
- Test：缓存命中 + DB 兜底（H2）。Commit `feat(route,billing): 路由/计费配置改读快照缓存(DB兜底)`。

## DoD
1. config 单测全绿（发布/回滚/序列化 + 消费端缓存 离线覆盖）。
2. 端到端：改 route_config→发布→秒级生效；回滚→恢复；Redis 挂时凭本地缓存快照继续调用（§9.2）。
3. 全量构建 SUCCESS。

## 后续
选源/切换/分流/应答码映射/限流参数接入同机制；敏感发布审批流（bpm）；发布 diff 可视化+影响范围；Nacos 载体切换；org_flow 记录命中配置版本号（已留字段）。
