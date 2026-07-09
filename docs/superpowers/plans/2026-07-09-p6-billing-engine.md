# 征信 API 平台 P6：计费引擎（计费判定与金额计算）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `yudao-module-billing` 控制面模块，落地计费引擎的**纯计算**部分：计费模板（billing_template）、阶梯区间（template_tier）、计费应答码（charge_code_rule）三表 CRUD，以及 `ChargeCalculator`——按平台应答码判定是否计费、按收费模式（按次/阶梯滑档/免费额度）计算金额。本期**不动钱、不改调用链**（无 billing_account、无扣减、无 openapi 接线——留 P6b 账务扣费）。

**Architecture:** 计费里程碑拆为「计算」（本期 P6）与「账务扣费」（P6b）两个子里程碑，边界干净：P6 只做「该收多少钱」的纯计算（DB 读模板/区间/应答码 + 账期累计量作为**入参**），全 H2 离线可测、零运行时风险、可独立合并；P6b 再做「把钱扣掉」。沿用 P1/P3/P4/P5 的 yudao 标准 CRUD 纵切范式。`ChargeCalculator` 为 billing 模块内部 `@Component`，ChargeContext/ChargeResult 为模块内 POJO（不入 apiten-common，P6b 需跨模块契约时再提取）。

**Tech Stack:** Java 21 + Spring Boot 3.5.15；MyBatis-Plus（`BaseDO`/`BaseMapperX`/`LambdaQueryWrapperX`）、yudao 框架、`java.math.BigDecimal`（金额 scale=4 HALF_UP）、H2 内存库单测（`BaseDbUnitTest`）、JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§5.2.1 计费判定、§5.2.2 收费模式（按次/阶梯滑档/免费额度/最低消费）、§6.4.4 计费模板、§6.4.5 计费应答码、§7 机构域（billing_template/template_tier/charge_code_rule）、§12.2 yudao-module-billing 控制面。

## Global Constraints

- JDK 21；Maven 前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`；模块测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-billing -am test -q`，必须离线通过（H2，不依赖中间件）。
- **扁平模块范式**：新模块 `yudao-module-billing`，包根 `cn.iocoder.yudao.module.billing`；错误码 `cn.iocoder.yudao.module.billing.enums.ErrorCodeConstants`，本域占 `1_024_xxx_xxx`（billing_template=`1_024_001_xxx`、template_tier=`1_024_002_xxx`、charge_code_rule=`1_024_003_xxx`）。
- **表命名**：`billing_template`、`template_tier`、`charge_code_rule`；均含 `BaseDO` 五审计列，单租户（extends `BaseDO` + `@TenantIgnore`）。
- **运行库**：`apiten_billing`（MySQL `127.0.0.1:23306`）；单测 H2。
- **编码规则**：模板 `T`+6 位，服务端 id 派生（临时 UUID 占位 → 回填 `T%06d`，`@Transactional`），H2 DDL 编码列唯一约束。template_tier / charge_code_rule 无业务编码。
- **金额精度**：一律 `BigDecimal`，scale=4，`RoundingMode.HALF_UP`；DDL `decimal(12,4)`。
- **模块依赖**：billing pom 依赖 `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant,test}` + `apiten-common`。**不需 OpenFeign**（本期无跨模块调用）。不依赖 org/product 模块代码。
- **服务坐标**：`billing-server`，端口 **48096**（现有 48090-48095 已占）；datasource 指向 `apiten_billing`。
- **权限串**：`billing:<kebab-domain>:{create|update|delete|query}`。
- **DDL 双写**：MySQL `docker/mysql/init/08-billing-schema.sql`（新建）；H2 `yudao-module-billing/src/test/resources/sql/create_tables.sql` + `clean.sql`。
- 每任务一 commit（仅 add 改动文件）。
- **不在本期（→ P6b）**：billing_account、实时扣减、charge_flow 幂等、finance_flow、recharge/adjust、冲正、**最低消费结算**（字段本期存、enforce 留 P6b）、日终对账、openapi 扣费接线、product_cost 成本、毛利。

---

### Task 1: yudao-module-billing 模块与持久化基建

**Files:** 模块 pom / `BillingServerApplication` / `application{,-local,-dev}.yaml` / 单测资源 / `DbHarnessSmokeTest`；`docker/mysql/init/08-billing-schema.sql`；根 `pom.xml`。
**Interfaces:** Consumes 无；Produces `BaseDbUnitTest` 基座 + `apiten_billing` 库。

- [ ] **1** pom 照搬 `yudao-module-route/pom.xml`（**去掉 OpenFeign 依赖**，artifactId 改 `yudao-module-billing`）；根 pom `<modules>` 在 route 后追加。
- [ ] **2** `08-billing-schema.sql` 顶部 `CREATE DATABASE IF NOT EXISTS apiten_billing ... USE apiten_billing;`；`docker exec` 建库（容器停则跳过并报告）。`application*.yaml` 照搬 route，改 `billing-server`/`48096`/DB `apiten_billing`；**不加** feign。`BillingServerApplication` 照搬 route（**不加** `@EnableFeignClients`）。
- [ ] **3** 单测资源照搬 route；smoke 表 `billing_db_harness_smoke`。
- [ ] **4** `DbHarnessSmokeTest` 照搬 route（手动 `new JdbcTemplate(dataSource)`）。
- [ ] **5** `mvn -pl yudao-module-billing -am test -q` → `Tests run: 1, Failures: 0`，离线。
- [ ] **6** Commit `chore(billing): 新建 yudao-module-billing 模块与 H2 单测基座，apiten_billing 库`（add `yudao-module-billing docker/mysql/init/08-billing-schema.sql pom.xml`）。

---

### Task 2: 计费模板 billing_template CRUD

**Interfaces:**
- `class BillingTemplateDO extends BaseDO { Long id; String templateCode; String name; Integer chargeMode; BigDecimal unitPrice; Integer freeQuota; BigDecimal minCharge; Integer settlePeriod; Integer status; String version; String remark; }`（`chargeMode`：1按次 2阶梯 3免费额度 4最低消费；`settlePeriod`：1自然月 2账期日）
- `interface BillingTemplateService { Long createTemplate(BillingTemplateSaveReqVO); void updateTemplate(...); void deleteTemplate(Long); BillingTemplateDO getTemplate(Long); BillingTemplateDO getByCode(String); PageResult<BillingTemplateDO> getTemplatePage(...); List<BillingTemplateDO> getSimpleList(); }`
- HTTP `/billing/template/{create,update,delete,get,page,simple-list}`；权限 `billing:template:*`
- `ErrorCodeConstants.BILLING_TEMPLATE_NOT_EXISTS = new ErrorCode(1_024_001_000, "计费模板不存在")`

**MySQL DDL（追加 `08-billing-schema.sql`）:**
```sql
CREATE TABLE `billing_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(32) NOT NULL COMMENT '模板编码 T+序号',
  `name` varchar(128) NOT NULL COMMENT '模板名称',
  `charge_mode` tinyint NOT NULL DEFAULT 1 COMMENT '收费模式：1按次 2阶梯 3免费额度 4最低消费',
  `unit_price` decimal(12,4) NULL DEFAULT NULL COMMENT '按次单价/指定单价',
  `free_quota` int NULL DEFAULT NULL COMMENT '免费额度(账期计费次数)',
  `min_charge` decimal(12,4) NULL DEFAULT NULL COMMENT '最低消费(账期结算,P6b enforce)',
  `settle_period` tinyint NOT NULL DEFAULT 1 COMMENT '结算周期：1自然月 2账期日',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `version` varchar(16) NOT NULL DEFAULT 'v1',
  `remark` varchar(512) NULL DEFAULT '',
  `creator` varchar(64) NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费模板表';
```
**H2 DDL**（双引号，`decimal(12,4)`，`CONSTRAINT "uk_billing_template_code" UNIQUE ("template_code")`）；`clean.sql` 加 `DELETE FROM "billing_template";`。

**Steps（六步 TDD，照搬 P4 Task2/3）:**
- [ ] **1** ErrorCode + DDL 三处。
- [ ] **2** `BillingTemplateServiceImplTest`（6 用例：create 派生 `T\d{6}`、getByCode、update 不存在→`BILLING_TEMPLATE_NOT_EXISTS`、delete→null、删后重建不复用码、page 按 name/chargeMode 过滤）。
- [ ] **3** 验证失败。
- [ ] **4** 实现：`createTemplate` id 派生 `T%06d`（`@Transactional`，UUID 占位）；`updateTemplate` 校验存在后 `setTemplateCode(null)`；VO/Mapper/Controller 照搬 P4 `OrgXxx`。
- [ ] **5** 验证通过。
- [ ] **6** Commit `feat(billing): 计费模板 billing_template CRUD（收费模式+单价+免费额度+最低消费+结算周期）`。

---

### Task 3: 阶梯区间 template_tier CRUD

**Interfaces:**
- `class TemplateTierDO extends BaseDO { Long id; Long billingTemplateId; Long tierFrom; Long tierTo; BigDecimal unitPrice; Integer sort; }`（`billingTemplateId` 松耦合，create/update 校验模板存在 → `BILLING_TEMPLATE_NOT_EXISTS`；`tierTo` null=∞）
- `interface TemplateTierService { Long createTier(...); void updateTier(...); void deleteTier(Long); TemplateTierDO getTier(Long); PageResult<TemplateTierDO> getTierPage(...); List<TemplateTierDO> getListByTemplate(Long templateId); }`——`getListByTemplate` 按 `tierFrom` 升序（供滑档）。
- HTTP `/billing/tier/{create,update,delete,get,page,list-by-template}`；权限 `billing:tier:*`
- `ErrorCodeConstants.TEMPLATE_TIER_NOT_EXISTS = new ErrorCode(1_024_002_000, "阶梯区间不存在")`

**MySQL DDL:**
```sql
CREATE TABLE `template_tier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `billing_template_id` bigint NOT NULL COMMENT '计费模板ID(松耦合)',
  `tier_from` bigint NOT NULL DEFAULT 0 COMMENT '区间起始累计量(含)',
  `tier_to` bigint NULL DEFAULT NULL COMMENT '区间结束累计量(含,空=无上限)',
  `unit_price` decimal(12,4) NOT NULL COMMENT '该档单价',
  `sort` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_template_id` (`billing_template_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费模板阶梯区间表';
```
H2 DDL 同风格（无唯一约束）；`clean.sql` 加 DELETE。

**Steps（照搬 P3 `ds_interface_param` 从表 CRUD）:**
- [ ] **1** ErrorCode + DDL。
- [ ] **2** `TemplateTierServiceImplTest`（先经 `BillingTemplateMapper` 造真实父模板：create 持久化、父模板不存在→`BILLING_TEMPLATE_NOT_EXISTS`、update 不存在→`TEMPLATE_TIER_NOT_EXISTS`、`getListByTemplate` 按 tierFrom 升序、page 过滤）。
- [ ] **3–5** 失败→实现（注入 `BillingTemplateMapper` 校验；`getListByTemplate` `orderByAsc(tierFrom)`）→通过。
- [ ] **6** Commit `feat(billing): 阶梯区间 template_tier CRUD（松耦合模板+区间/单价+按起始升序）`。

---

### Task 4: 计费应答码 charge_code_rule CRUD

**Interfaces:**
- `class ChargeCodeRuleDO extends BaseDO { Long id; String platformCode; Boolean charge; Integer scopeType; Long scopeId; String description; Integer status; }`（`platformCode` 如 `"0000"`；`charge` 是否计费；`scopeType`：0默认 1机构 2产品 3模板；`scopeId`：scopeType>0 对应 orgId/productId/templateId，默认级 null）
- `interface ChargeCodeRuleService { Long createRule(...); void updateRule(...); void deleteRule(Long); ChargeCodeRuleDO getRule(Long); PageResult<...> getRulePage(...); }`
- `ChargeCodeRuleMapper.selectMatched(String platformCode, Long orgId, Long productId, Long templateId)`——返回该应答码在四 scope（模板/产品/机构/默认）下启用规则集合，供 Task 5 就近取用。
- HTTP `/billing/charge-code/{create,update,delete,get,page}`；权限 `billing:charge-code:*`
- `ErrorCodeConstants.CHARGE_CODE_RULE_NOT_EXISTS = new ErrorCode(1_024_003_000, "计费应答码规则不存在")`

**MySQL DDL:**
```sql
CREATE TABLE `charge_code_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform_code` varchar(8) NOT NULL COMMENT '平台应答码',
  `charge` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否计费',
  `scope_type` tinyint NOT NULL DEFAULT 0 COMMENT '范围：0默认 1机构 2产品 3模板',
  `scope_id` bigint NULL DEFAULT NULL COMMENT '范围ID(scopeType>0)',
  `description` varchar(256) NULL DEFAULT '',
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_platform_scope` (`platform_code`, `scope_type`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费应答码规则表';
```
H2 DDL 同风格；`clean.sql` 加 DELETE。

`selectMatched`（`default` 方法）：`eq(platformCode) AND eq(status,0) AND ( (scopeType=3 AND scopeId=templateId) OR (scopeType=2 AND scopeId=productId) OR (scopeType=1 AND scopeId=orgId) OR scopeType=0 )`（用 `.and(w->...)` 分组；templateId/productId/orgId 为 null 时对应 OR 分支跳过——用 if 拼装，参考 P5 `selectMatched` 的 null 分支写法）。返回 List，Task 5 按 scopeType 降序取第一条。

**Steps:**
- [ ] **1** ErrorCode + DDL。
- [ ] **2** `ChargeCodeRuleServiceImplTest`（create/update-not-exists/page + `selectMatched` 就近：造默认+模板两条同码规则，`selectMatched(code,...,templateId)` 含两条）。
- [ ] **3–5** 失败→实现→通过。
- [ ] **6** Commit `feat(billing): 计费应答码 charge_code_rule CRUD（平台码+scope覆盖+就近匹配查询）`。

---

### Task 5: ChargeCalculator（计费判定 + 按次/阶梯滑档/免费额度）

**Files:** `.../billing/service/calc/{ChargeContext,ChargeResult,ChargeCalculator}.java` + `ChargeCalculatorTest.java`。

**Interfaces:**
- `class ChargeContext { String platformCode; Long billingTemplateId; BigDecimal unitPrice; Long orgId; Long productId; long periodChargedCount; int callQuantity=1; }`（`unitPrice` 来自 org_product 单价，template 无单价时兜底；`periodChargedCount` 账期已计费次数，调用方传入——真实来源=charge_flow 汇总，属 P6b）
- `class ChargeResult { boolean charged; BigDecimal amount; String mode; boolean chargeCodeMatched; }`（`mode`：`NOT_CHARGED`/`PER_CALL`/`TIER`/`FREE_QUOTA`）
- `@Component class ChargeCalculator { ChargeResult computeCharge(ChargeContext ctx); }`

**computeCharge 逻辑：**
1. **是否计费**：`selectMatched(platformCode, orgId, productId, templateId)` → 按 scopeType 降序取第一条 → `charge`；无匹配 → 默认口径（`"0000".equals(platformCode)` → true，否则 false）。不计费 → `{charged=false, amount=ZERO.setScale(4), mode="NOT_CHARGED", chargeCodeMatched=matched}`。
2. **计费**：`billingTemplateId==null` → 按次 `ctx.unitPrice`（null 兜 ZERO），mode `PER_CALL`。否则加载模板：
   - **免费额度**（template.freeQuota!=null）：`periodChargedCount < freeQuota` → `{charged=false, amount=ZERO, mode="FREE_QUOTA"}`；超额度按 template.unitPrice 按次。
   - **阶梯**（`chargeMode==2`）：`getListByTemplate(templateId)`（tierFrom 升序），对 `callQuantity` 个单位从「第 `periodChargedCount+1` 次」起**滑档**：每单位落 `tierFrom<=n<=tierTo`（tierTo null=∞）的档，累加该档 `unitPrice`。amount=Σ，mode `TIER`。
   - **按次**（`chargeMode==1`/其它）：`amount = template.unitPrice ?: ctx.unitPrice ?: ZERO`，mode `PER_CALL`。
3. amount `setScale(4, HALF_UP)`；返回 `{charged=true, amount, mode, chargeCodeMatched}`。

- [ ] **Step 1: 写失败测试（H2 多表造数）** —— `ChargeCalculatorTest`（`@Import(ChargeCalculator.class)` + 注入三 Mapper 造数），8 用例：
  - `notCharged_failureCode`（无规则 `"3001"` → false/0/NOT_CHARGED）
  - `charged_successDefault`（无规则 `"0000"` + 无模板 + unitPrice 1.5 → 1.5000/PER_CALL）
  - `rule_overridesDefault`（造默认规则 `"0001"` charge=true → 无数据也计费）
  - `perCall_templatePriceWins`（模板 chargeMode1 unitPrice2.0 vs ctx.unitPrice9 → 2.0000）
  - `tier_slidesAcrossTiers`（tiers [0..9]=1.0,[10..∞]=0.5；count=8,qty=4 → 第9,10次=1.0×2 + 第11,12次=0.5×2 = 3.0000/TIER；实现者对齐「第 n 次计费单位」口径并在报告写明）
  - `freeQuota_within_notCharged`（freeQuota100 count50 → false/0/FREE_QUOTA）
  - `freeQuota_exceeded_perCall`（freeQuota100 count150 unitPrice1.0 → 1.0000/PER_CALL）
  - `rule_templateScope_beatsDefault`（默认 charge=true + 模板级 charge=false 同码 → not charged）
- [ ] **Step 2:** 验证失败。
- [ ] **Step 3:** 实现 ChargeContext/ChargeResult（`@Data`）+ ChargeCalculator（注入三 Mapper；滑档循环；BigDecimal scale4）。
- [ ] **Step 4:** 验证通过（8 用例）。
- [ ] **Step 5:** Commit `feat(billing): ChargeCalculator 计费判定+按次/阶梯滑档/免费额度金额计算`。

---

## P6 完成定义（DoD）

1. `mvn -pl yudao-module-billing -am test` 全绿，离线（H2）。
2. 三表 CRUD + 编码 id 派生（T 码）+ 归属校验 + `selectMatched`/`getListByTemplate` 供计算。
3. `ChargeCalculator` 覆盖：应答码判定（就近覆盖 + 默认口径）、按次（模板价/单价兜底）、阶梯滑档（逐档累加、非返算）、免费额度（额度内不计费）；金额 BigDecimal scale4 HALF_UP。
4. MySQL DDL 并入 `08-billing-schema.sql`，`apiten_billing` 可承载。
5. 全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续（P6b 账务扣费，见 2026-07-09-p6b-billing-accounting.md）

billing_account + 实时扣减（乐观锁+同事务）+ charge_flow（flowNo 幂等）+ finance_flow + recharge/adjust + 冲正 + 最低消费结算 + 日终三方对账；`ChargeCalculator` 提为 `/rpc-api/billing/charge` 契约（ChargeContext/ChargeResult 入 apiten-common），openapi OpenFeign 同步扣费落 `charged`/`amount`；成本侧 product_cost + 毛利。
