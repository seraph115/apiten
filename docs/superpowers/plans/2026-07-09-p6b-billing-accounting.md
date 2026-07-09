# 征信 API 平台 P6b：账务扣费（账户/扣减/计费流水/冲正/接线）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development（本里程碑涉钱+事务+幂等+冲正，务必双评审）. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 在 `yudao-module-billing` 上叠加账务扣费：billing_account（余额/冻结/信用/预警）+ 充值/财务流水/账务调整，实时扣减（乐观锁 + 计费流水与余额同事务），charge_flow 按机构流水号 flowNo 幂等（一个 flowNo 至多一条计费流水），失败自动冲正；将 P6 `ChargeCalculator` 提为 `/rpc-api/billing/charge` 契约，openapi 调用链在 adapter 返回后同步扣费（OpenFeign），落 `charged`/`amount`。

**Architecture:** 扣费权威在 billing-server 本地强事务（§12.3「主调用链不引入分布式事务」：本地事务 + 最终失败自动冲正补偿）。幂等键 = flowNo（网关生成的机构流水号，openapi 透传）。计费上下文（accountId/billingTemplateId/unitPrice）由**网关五重鉴权时一并加载**（P4 verify 已读 org_account + org_product）→ 扩展 `OrgAuthVerifyRespDTO` + 网关注入 `X-Account-Id`（已有）/`X-Billing-Template-Id`/`X-Unit-Price` → openapi 透传给 billing，调用链零 DB 查（纯缓存口径）。ChargeReq/RespDTO 入 apiten-common 作跨模块契约。

**决策记录（架构分叉）:**
- **扣费一致性**：预付费实时扣余额，`billing_account` 用 MyBatis-Plus `@Version` 乐观锁；charge_flow 与余额变动**同一本地事务**；余额不足按机构配置：拒绝(`2101`)或透支至 `credit_limit`。备选 Seata——否决，遵 §12.3。
- **幂等**：`charge_flow.flow_no` 唯一键；charge(flowNo) 先查 flow_no，命中→返首次结果不重复扣。
- **冲正**：openapi「已扣费但调用最终失败」分支调 billing `reverse(flowNo)`→反向 finance_flow + 回补余额 + charge_flow 标记；日终对账补偿留 P10。
- **计费上下文来源**：网关注入（上）。备选再 Feign 查 org_product——否决（多跳+违纯缓存）。

**Tech Stack:** Java 21；MyBatis-Plus `@Version` 乐观锁 + 本地 `@Transactional`；OpenFeign（openapi→billing）；BigDecimal scale4 HALF_UP；H2 单测（乐观锁/幂等/冲正 H2 可测）。

**Spec:** v1.3 §5.2.3、§6.4.x、§6.5、§8.4⑧、§8.5、§12.3。

## Global Constraints
- 复用 P6 `yudao-module-billing`（`apiten_billing`；错误码本期 `1_024_004_xxx`~`1_024_008_xxx`）。billing 暴露被调 RPC（无需 OpenFeign 依赖；openapi 加 BillingClient）。
- 新表：`billing_account`/`recharge_record`/`finance_flow`/`adjust_record`/`charge_flow`（DDL 追加 `08-billing-schema.sql` + H2）。
- 金额 BigDecimal scale4；余额 `@Version` 乐观锁；`charge_flow.flow_no` 唯一。
- apiten-common 新增 `cn.apiten.common.billing.{ChargeReqDTO,ChargeRespDTO}`；`OrgAuthVerifyRespDTO` 扩 `billingTemplateId`/`unitPrice`。
- 每任务一 commit；扣费/幂等/冲正逻辑 H2 全测；OpenFeign 调用离线不测（DoD curl 冒烟）。

## Tasks（CRUD/从表照搬 P4/P6 范式，重点在扣费/幂等/冲正/接线）

### Task 1: apiten-common 契约 + 鉴权响应扩计费上下文
- `ChargeReqDTO { String flowNo; Long orgId; Long productId; String productCode; String platformCode; BigDecimal unitPrice; Long billingTemplateId; Long accountId; long periodChargedCount; int callQuantity; }`；`ChargeRespDTO { boolean charged; BigDecimal amount; String mode; String platformCode; boolean idempotentHit; }`（手写 getter/setter）。
- `OrgAuthVerifyRespDTO` 加 `Long billingTemplateId; BigDecimal unitPrice;` + getter/setter。
- Test：DTO 往返 + 既有 apiten-common 测试绿。Commit `feat(common): 计费扣费契约 + 鉴权响应扩计费上下文`。

### Task 2: billing_account CRUD + 乐观锁扣减服务
- `BillingAccountDO extends BaseDO { Long id; String accountNo; Long orgId; Integer accountType; BigDecimal balance; BigDecimal frozenAmount; BigDecimal creditLimit; BigDecimal warnThreshold; Integer status; BigDecimal totalRecharge; BigDecimal totalConsume; LocalDateTime lastConsumeTime; @Version Integer version; }`（accountNo `A`+id 派生）。
- CRUD（照搬 P4 OrgXxx）+ `deduct(accountId, amount, allowCredit)`：`@Transactional`，读→校验 `balance(+creditLimit if allowCredit) >= amount`（否则 `BALANCE_INSUFFICIENT`→`2101`）→`balance-=amount`/`totalConsume+=`/`lastConsumeTime`→`updateById`（乐观锁，失败有限重试）。`refund(accountId, amount)` 回补。
- DDL `billing_account`（`version int NOT NULL DEFAULT 0`；uk account_no；idx org_id）+ H2。错误码 `1_024_004_000 ACCOUNT_NOT_EXISTS`/`_001 BALANCE_INSUFFICIENT`。
- Test（H2）：扣减成功/余额不足抛/透支信用/refund/乐观锁并发（手动改 version 模拟）。Commit `feat(billing): billing_account CRUD + 乐观锁扣减/回补`。

### Task 3: charge_flow（flowNo 幂等）+ finance_flow
- `ChargeFlowDO { Long id; String flowNo; Long orgId; Long productId; String platformCode; Boolean charged; BigDecimal amount; String mode; Long accountId; Integer reversed; LocalDateTime chargeTime; }`（uk `flow_no`）；`ChargeFlowMapper.selectByFlowNo`。
- `FinanceFlowDO { Long id; Integer flowType(1充值 2消费 3退款 4调整 5冻结 6解冻 7冲正); Long orgId; Long accountId; BigDecimal amount; BigDecimal balanceAfter; String refFlowNo; String refOrderNo; String remark; LocalDateTime time; }`（仅 insert+查询）。
- DDL 两表 + H2；错误码 `1_024_005_xxx`/`1_024_006_xxx`。Test：insert + selectByFlowNo 幂等查、finance 记账查。Commit `feat(billing): charge_flow(flowNo幂等)+finance_flow`。

### Task 4: BillingChargeService（判定→扣减→双流水，同事务+幂等）+ 冲正
- `charge(ChargeReqDTO)`：**先 `selectByFlowNo`**——命中→`{charged,amount,idempotentHit=true}`。未命中→`@Transactional`：`ChargeCalculator.computeCharge(ctx)`；计费→`deduct` + `charge_flow` insert（flow_no 唯一键冲突→并发已处理，回查 selectByFlowNo 返回）+ `finance_flow` insert(消费,balanceAfter)；不计费→仅 charge_flow(charged=false)。
- `reverse(flowNo)`：查 charge_flow；已计费未冲正→`@Transactional` `refund` + finance_flow(冲正) + `reversed=1`；幂等。
- Test（H2 扣费一致性全测）：首次计费扣余额+双流水、同 flowNo 幂等不重复扣、失败码仅落 charge_flow、余额不足→2101、reverse 回补+冲正流水+标记、reverse 幂等。Commit `feat(billing): BillingChargeService 判定→扣减→双流水(同事务+幂等)+冲正`。

### Task 5: /rpc-api/billing/charge + /reverse RPC
- `BillingChargeRpcController`（`@PermitAll`+`@TenantIgnore`）：`POST /rpc-api/billing/charge`、`POST /rpc-api/billing/reverse`。Test：controller 直调 service（H2）。Commit `feat(billing): 计费/冲正 RPC 端点`。

### Task 6: 网关注入计费上下文 + openapi 接线扣费
- **org**：`OrgAuthServiceImpl.verify` 填 `resp.billingTemplateId/unitPrice`（org_product 已在 check#8 加载）。
- **网关** `OpenApiAuthFilter`：pass 注入 `X-Billing-Template-Id`/`X-Unit-Price`（`.set`）。
- **openapi**：`BillingClient` Feign(`billing-server`,`/rpc-api/billing`)；`QueryController` 读 `X-Account-Id`/`X-Billing-Template-Id`/`X-Unit-Price`；`QueryOrchestrator` adapter 返回后（`ObjectProvider<BillingClient>` 可用）调 `charge(...)`，用 `charged`/`amount` 覆盖 resp；上游失败但已扣费分支调 `reverse(flowNo)`。`periodChargedCount` 本期传 0（真实账期汇总留 P10，注记）。BillingClient 不可用→降级保持现有 hardcoded charged。
- Test：`QueryOrchestrator` 级 stub BillingClient（覆盖 resp / 降级）。Commit `feat(openapi,gateway,org): 网关注入计费上下文 + openapi 同步扣费/冲正接线`。

## DoD
1. billing 单测全绿（扣费/幂等/冲正/乐观锁 H2 覆盖）。
2. 端到端 curl：充值→带签名调 `/api/v1/Pxxx/query`→`charged=true`、余额减、charge_flow/finance_flow 各一条；余额不足→`2101`。
3. 全量构建 SUCCESS。

## 后续
最低消费账期结算、日终三方对账（P10 联动）、product_cost 成本 + 毛利、后付费账单、`periodChargedCount` 真实账期汇总（依赖 charge_flow 按账期统计，P10）。
