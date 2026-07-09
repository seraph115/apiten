# 征信 API 平台 P8：限流限额（并发 + 调用量）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 在 openapi 调用链的**前置检查**（§8.4②，五重鉴权之后、路由之前）落地限流限额：机构/账号/产品三级最大并发（信号量）+ 日/月累计调用量上限（账本）+ 80%/95% 预警 + 超限阻断（并发 `2102` / 日 `2103` / 月 `2104`）。限额值复用 P4 已存的 org_account/org_product 字段，经网关注入。

**Architecture:** 限流分层（§12.3）：本期实现 **openapi 内嵌前置检查**（网关 Sentinel 层与 adapter 上游配额留后续/二期）。限额来源：网关五重鉴权已加载 org_account/org_product → 扩 `OrgAuthVerifyRespDTO` 返回并发/日/月限额 → 网关注入 `X-Concurrency-Limit`/`X-Daily-Limit`/`X-Monthly-Limit`（同 P6b 计费上下文注入模式）→ openapi 前置检查读取。并发用信号量、调用量用计数账本，均抽象为接口（内存实现离线可测、Redisson/Redis 生产）。

**决策记录:**
- **存储抽象**：`ConcurrencyGuard`（信号量）+ `VolumeLedger`（日/月计数）为接口；内存实现（`Semaphore`/`ConcurrentHashMap`，单实例+单测）默认，Redisson/Redis（多实例共享）留配置切换——同 P4 `NonceStore` 范式。多实例前必须切 Redis，文档标注。
- **限额来源**：网关注入（纯缓存）。备选 openapi 再查 org_product——否决。
- **维度键**：并发 `orgId:accountId:productCode`；调用量 `orgId:productCode:yyyyMMdd`（日）/`yyyyMM`（月）。
- **超限行为**：本期「阻断」（2102/2103/2104）；排队/降级/转审批留后续（策略预留）。

**Tech Stack:** Java 21；`Semaphore`（内存并发）/`ConcurrentHashMap`+过期（内存账本）；Redisson（生产切换）；H2 + 内存实现单测。

**Spec:** v1.3 §5.3、§6.8、§8.4②、§12.3。

## Global Constraints
- 逻辑落 openapi（数据面前置检查）；`threshold_alert_record` 落 `apiten_openapi`。平台码加 apiten-common：`CONCURRENCY_EXCEEDED("2102")`/`DAILY_VOLUME_EXCEEDED("2103")`/`MONTHLY_VOLUME_EXCEEDED("2104")`。
- 限额复用 org_account（账号级）+ org_product（产品级），本期取**产品级**为主（账号级增强，注记）。
- 每任务一 commit；内存实现全离线覆盖。

## Tasks

### Task 1: apiten-common 平台码 + OrgAuthVerifyRespDTO 扩限额
- `PlatformErrorCode` 加 `CONCURRENCY_EXCEEDED("2102",...)`/`DAILY_VOLUME_EXCEEDED("2103",...)`/`MONTHLY_VOLUME_EXCEEDED("2104",...)`。
- `OrgAuthVerifyRespDTO` 加 `Integer concurrencyLimit; Integer dailyLimit; Integer monthlyLimit;`（verify 填充）。
- Test：平台码注册 + DTO 往返。Commit `feat(common): 限流平台码2102-2104 + 鉴权响应扩限额`。

### Task 2: ConcurrencyGuard（信号量）
- `interface ConcurrencyGuard { boolean tryAcquire(String key, int limit); void release(String key); }`；`InMemoryConcurrencyGuard`（`ConcurrentHashMap<String,Semaphore>`；limit<=0 不限）。
- Test：达上限第 N+1 次 false；release 后可再获。Commit `feat(openapi): ConcurrencyGuard 并发信号量(内存+接口)`。

### Task 3: VolumeLedger（日/月调用量账本）
- `interface VolumeLedger { long incrAndGet(String key, long ttlSeconds); long get(String key); }`；`InMemoryVolumeLedger`（`ConcurrentHashMap` 计数+到期）。
- Test：incr 累加、到期重置、get。Commit `feat(openapi): VolumeLedger 调用量账本(内存+接口)`。

### Task 4: RateLimitService 前置检查 + openapi 接线
- `@Component RateLimitService.check(orgId, accountId, productCode, concurrencyLimit, dailyLimit, monthlyLimit)` → `RateLimitResult{allowed, platformCode?, token?}`：
  1. 日累计 >= dailyLimit → `2103`（>=80%/95% 记 threshold_alert_record + 日志占位）。
  2. 月累计 >= monthlyLimit → `2104`。
  3. 并发 `tryAcquire` 失败 → `2102`。三关过→incr 日/月账本 + 返 allowed + 并发 token。
- openapi 读 `X-Account-Id`/`X-Concurrency-Limit`/`X-Daily-Limit`/`X-Monthly-Limit`；`QueryOrchestrator` 路由前 `check`：不过→返 2xxx（不调路由/adapter）；过→`try{...}finally{release}`。降级：limit<=0 不限。
- `threshold_alert_record { Long id; Long orgId; String productCode; String limitType; Long current; Long limit; Integer levelPct; LocalDateTime time; }`（仅 insert）。
- Test：日超限→2103 不调 adapter；并发超限→2102；未超放行且 finally 释放。Commit `feat(openapi,gateway,org): 前置限流检查+网关注入限额+2102-2104`。

## DoD
1. openapi 单测全绿（并发/账本/前置检查覆盖）。
2. 端到端：org_product 日限额=2，第 3 次→`2103`；并发超 limit→`2102`。
3. 全量构建 SUCCESS。

## 后续
账号级叠加、排队/降级/转审批、Redisson 多实例共享、网关 Sentinel（机构/账号）、adapter 上游配额（ds_quota_ledger 接线）、80/95% 预警对接监控告警、账期维度调用量。
