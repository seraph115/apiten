# 征信 API 平台 P5：路由域（静态三级路由）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `yudao-module-route` 控制面模块，落地静态三级路由：`route_config` CRUD + `RouteResolver`（按 productCode+orgId 解析出 `dsInterfaceId`，路由表未命中时回落产品默认绑定）+ `/rpc-api/route/resolve` RPC；并在 product-server 暴露产品默认绑定 RPC、在 openapi 接线，使真实 API 调用经路由解析后走 P2 真实 HTTP 引擎（而非 MockProvider），打通 productCode 端到端主链路。

**Architecture:** 控制面新模块 `yudao-module-route`，沿用 P1/P3/P4 的 yudao 标准 CRUD 纵切范式。路由解析权威在 route-server：`openapi → route-server /rpc-api/route/resolve(productCode,orgId)`（1 跳）；route_config 未命中时 route-server `→ product-server /rpc-api/product/resolve-default(productCode)` 取产品默认绑定公底（Feign）。product 域现有 `ProductInterfaceResolver`（@Component）通过新增 RPC 端点暴露，不改其逻辑。route_config 对 product/ds_interface 的引用均为**松耦合**（只存 code/id 快照，无跨库 FK、无模块代码依赖）。openapi 用 `ObjectProvider<RouteClient>` 降级模式（单测无 route-server 时走 mock），与现有 `AdapterClient`/`KafkaTemplate` provider 模式一致。

**Tech Stack:** Java 21 + Spring Boot 3.5.15；MyBatis-Plus（`BaseDO`/`BaseMapperX`/`LambdaQueryWrapperX`）、yudao 框架（`CommonResult`/`PageResult`/`PageParam`/`BeanUtils.toBean`/`ServiceExceptionUtil.exception`）、OpenFeign（`@FeignClient`，跨服务 RPC）、H2 内存库单测（`BaseDbUnitTest`）、JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§5.1 路由决策模型第一层（静态路由匹配：机构产品 > 机构 > 产品默认）、§6.6 路由管理、§7 路由域（route_config）、§8.4 步骤⑤路由决策、§12.2 yudao-module-route 控制面。

## Global Constraints

- JDK 21；Maven 命令前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`；route 模块测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-route -am test -q`，必须离线通过（H2，不依赖中间件）。
- **扁平模块范式**：新模块 `yudao-module-route`，包根 `cn.iocoder.yudao.module.route`；模块数字错误码 `cn.iocoder.yudao.module.route.enums.ErrorCodeConstants`，本域占 `1_023_xxx_xxx` 段（route_config=`1_023_001_xxx`）。
- **平台字符串码**（对外 API 响应/解析结果）用 apiten-common `PlatformErrorCode`；本期新增 `ROUTE_NO_TARGET("3005","未匹配到可用数据源接口")`。
- **表命名**：`route_config`；含 `BaseDO` 五审计列，单租户（extends `BaseDO` + `@TenantIgnore`，无 `tenant_id`）。
- **运行库**：`apiten_route`（MySQL `127.0.0.1:23306` 本地 compose）；单测用 H2。
- **编码规则**（§7）：路由 `R`+6 位序号，**服务端由自增 id 派生**（临时 UUID 占位插入 → 据 id 回填 `R%06d`，`@Transactional`），H2 DDL 对编码列加唯一约束。
- **模块依赖**：route 模块 pom 依赖 `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant,test}`（同 product/org）+ `apiten-common`。**不依赖** product/org 模块代码——跨模块只经 Feign RPC + 松耦合 id/code 快照。
- **服务坐标**：`spring.application.name=route-server`，端口 **48095**（现有：openapi 48090 / adapter 48091 / flow 48092 / product 48093 / org 48094）；datasource `master.url` 指向 `apiten_route`。
- **权限串**：`@ss.hasPermission('route:config:{create|update|delete|query}')`（菜单落库留待前端里程碑）。RPC 端点内部用，路径 `/rpc-api/**`，`@PermitAll`（本仓库 yudao 版本用 `jakarta.annotation.security.PermitAll`——已在 P4 核实）+ `@TenantIgnore`，不需要登录态。
- **DDL 双写**：MySQL DDL 写到 `docker/mysql/init/07-route-schema.sql`（新建，供 apiten_route 运行库）；H2 DDL 到 `yudao-module-route/src/test/resources/sql/create_tables.sql` + `clean.sql`。
- 每任务一个 `git commit`（仅 add 改动文件，不用 `-A`）。
- **不在本期范围**：SPLIT（权重分流组）/CHAIN（切换链）/DYNAMIC（动态选源打分）目标类型（列预留，仅实现 SINGLE）；指标采集与多因子打分；附加匹配条件（请求参数/区域码/产品功能/账号/时间段）；org-wide 机构级中间层（本期折叠为 机构产品 / 产品默认 两级 + priority）；配置快照版本化发布/回滚、路由模拟器、分流命中写流水；前端页面与 sys_menu 菜单。

---

### Task 1: yudao-module-route 模块与持久化基建

**Files:**
- Create: `yudao-module-route/pom.xml`
- Create: `yudao-module-route/src/main/java/cn/iocoder/yudao/module/route/RouteServerApplication.java`
- Create: `yudao-module-route/src/main/resources/application.yaml`、`application-local.yaml`、`application-dev.yaml`
- Create: `yudao-module-route/src/test/resources/application-unit-test.yaml`、`logback.xml`、`sql/create_tables.sql`、`sql/clean.sql`
- Create: `yudao-module-route/src/test/java/cn/iocoder/yudao/module/route/DbHarnessSmokeTest.java`
- Create: `docker/mysql/init/07-route-schema.sql`（建库 + 后续任务追加建表）
- Modify: 根 `pom.xml`（`<modules>` 增加 `yudao-module-route`）

**Interfaces:**
- Consumes: 无（新模块）。
- Produces: 可离线运行的 `BaseDbUnitTest` 持久化基座；运行库 `apiten_route`。

- [ ] **Step 1: 建模块 pom + 注册根 pom**

`yudao-module-route/pom.xml`：parent 与依赖照搬 `yudao-module-org/pom.xml` 现状（`sed -n '/<parent>/,/<\/dependencies>/p' yudao-module-org/pom.xml` 抄坐标，把 `artifactId` 改为 `yudao-module-route`、`name`/`description` 改为路由模块；含 `apiten-common` + `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant}` + `test` starter）。**额外确认** pom 含 OpenFeign 依赖：若 org 模块 pom 未含 `spring-cloud-starter-openfeign`，追加之（Task 5 需 Feign 调 product-server；坐标照抄 `yudao-module-openapi/pom.xml` 里引 openfeign 的那一段）。根 `pom.xml` `<modules>` 在 `yudao-module-org` 之后追加 `<module>yudao-module-route</module>`。

- [ ] **Step 2: 运行库 + 服务配置**

`docker/mysql/init/07-route-schema.sql` 顶部：

```sql
-- apiten 路由域建表（apiten_route 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_route` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_route`;
```

对运行容器立即建库（容器未运行则跳过并在报告标注，离线 H2 单测是真实门）：`docker exec apiten-mysql-1 mysql -uroot -papiten123 -e "CREATE DATABASE IF NOT EXISTS \`apiten_route\` DEFAULT CHARACTER SET utf8mb4;"`

`application.yaml`/`application-local.yaml`/`application-dev.yaml`：照搬 `yudao-module-org` 对应文件，改三处——`spring.application.name: route-server`；`server.port: 48095`；`spring.datasource.dynamic.datasource.master.url` 库名改 `apiten_route`。**不要**带 org 的 `apiten.org.*` 配置段。若 org 的 `application.yaml` 开启了 `feign`/`spring.cloud` 相关配置则一并照搬（保持可注册 Nacos + Feign 可用）。

`RouteServerApplication.java`：照搬 `OrgServerApplication`，改包名与类名。**若 Task 5 的 Feign 需要**，在此类加 `@EnableFeignClients`（本任务可先不加，Task 5 补；或此处直接加 `@EnableFeignClients(basePackages = "cn.iocoder.yudao.module.route")` 预置）。

- [ ] **Step 3: 单测资源（H2 基座）**

照搬 `yudao-module-org/src/test/resources/` 的 `application-unit-test.yaml`（`yudao.info.base-package` 改 `cn.iocoder.yudao.module.route`）、`logback.xml`；`sql/create_tables.sql` 先放 smoke 表 `route_db_harness_smoke`（`"id" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "name" varchar(64)`）；`sql/clean.sql` 放 `DELETE FROM "route_db_harness_smoke";`。

- [ ] **Step 4: 冒烟测试**

`DbHarnessSmokeTest.java`：照搬 `yudao-module-org` 的 `DbHarnessSmokeTest`（`extends BaseDbUnitTest`；手动 `new JdbcTemplate(dataSource)`），插入/计数 `route_db_harness_smoke`。

- [ ] **Step 5: 运行验证**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-route -am test -q`
Expected：补齐后 `Tests run: 1, Failures: 0`，离线。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-route docker/mysql/init/07-route-schema.sql pom.xml
git commit -m "chore(route): 新建 yudao-module-route 模块与 MyBatis-Plus/H2 单测基座，apiten_route 库"
```

---

### Task 2: apiten-common 扩充——路由解析 DTO + ROUTE_NO_TARGET 平台码

**Files:**
- Create: `apiten-common/src/main/java/cn/apiten/common/route/RouteResolveRespDTO.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/route/ProductDefaultRespDTO.java`
- Modify: `apiten-common/src/main/java/cn/apiten/common/api/PlatformErrorCode.java`
- Test: `apiten-common/src/test/java/cn/apiten/common/route/RouteDtoTest.java`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `class RouteResolveRespDTO { Long dsInterfaceId; String source; String platformCode; + getters/setters + static RouteResolveRespDTO of(Long dsInterfaceId, String source); + static RouteResolveRespDTO noTarget(); }`——`source` 取值 `"ROUTE_CONFIG"`/`"PRODUCT_DEFAULT"`/`"NONE"`；`noTarget()` 设 `dsInterfaceId=null, source="NONE", platformCode=PlatformErrorCode.ROUTE_NO_TARGET.getCode()`。route→openapi 契约。
  - `class ProductDefaultRespDTO { Long dsInterfaceId; String dsInterfaceCode; + getters/setters }`——product→route 契约（产品默认绑定；无绑定则 `dsInterfaceId=null`）。
  - `PlatformErrorCode.ROUTE_NO_TARGET("3005","未匹配到可用数据源接口")`（插入 `CHAIN_EXHAUSTED("3003",...)` 之后、`SYSTEM_ERROR` 之前）。
- 与 P4 一致：apiten-common 零框架依赖，手写 getter/setter，无 Lombok。

- [ ] **Step 1: 写失败测试**

`RouteDtoTest.java`：

```java
package cn.apiten.common.route;

import cn.apiten.common.api.PlatformErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RouteDtoTest {

    @Test
    void of_setsFields() {
        RouteResolveRespDTO r = RouteResolveRespDTO.of(100L, "ROUTE_CONFIG");
        assertThat(r.getDsInterfaceId()).isEqualTo(100L);
        assertThat(r.getSource()).isEqualTo("ROUTE_CONFIG");
        assertThat(r.getPlatformCode()).isNull();
    }

    @Test
    void noTarget_carriesRouteNoTargetCode() {
        RouteResolveRespDTO r = RouteResolveRespDTO.noTarget();
        assertThat(r.getDsInterfaceId()).isNull();
        assertThat(r.getSource()).isEqualTo("NONE");
        assertThat(r.getPlatformCode()).isEqualTo("3005");
    }

    @Test
    void routeNoTargetCode_registered() {
        assertThat(PlatformErrorCode.ROUTE_NO_TARGET.getCode()).isEqualTo("3005");
    }

    @Test
    void productDefault_holdsRef() {
        ProductDefaultRespDTO d = new ProductDefaultRespDTO();
        d.setDsInterfaceId(200L);
        d.setDsInterfaceCode("IF000002");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L);
        assertThat(d.getDsInterfaceCode()).isEqualTo("IF000002");
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test -q`
Expected：编译失败（类/枚举值不存在）。

- [ ] **Step 3: 最小实现**

`PlatformErrorCode.java`：在 `CHAIN_EXHAUSTED("3003", "切换链耗尽"),` 之后追加：

```java
    ROUTE_NO_TARGET("3005", "未匹配到可用数据源接口"),
```
（保持 `SYSTEM_ERROR("3999", "系统异常")` 在最后；其余枚举值不动。）

`RouteResolveRespDTO.java`：

```java
package cn.apiten.common.route;

import cn.apiten.common.api.PlatformErrorCode;

public class RouteResolveRespDTO {
    private Long dsInterfaceId;
    private String source;
    private String platformCode;

    public static RouteResolveRespDTO of(Long dsInterfaceId, String source) {
        RouteResolveRespDTO r = new RouteResolveRespDTO();
        r.dsInterfaceId = dsInterfaceId;
        r.source = source;
        return r;
    }

    public static RouteResolveRespDTO noTarget() {
        RouteResolveRespDTO r = new RouteResolveRespDTO();
        r.source = "NONE";
        r.platformCode = PlatformErrorCode.ROUTE_NO_TARGET.getCode();
        return r;
    }

    public Long getDsInterfaceId() { return dsInterfaceId; }
    public void setDsInterfaceId(Long dsInterfaceId) { this.dsInterfaceId = dsInterfaceId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
}
```

`ProductDefaultRespDTO.java`：`private Long dsInterfaceId; private String dsInterfaceCode;` + 手写 getter/setter（同 P4 DTO 风格）。

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test -q`
Expected：`RouteDtoTest` 4 用例全过；既有 crypto/api/id 测试保持绿。

- [ ] **Step 5: Commit**

```bash
git add apiten-common/src
git commit -m "feat(common): 路由解析 DTO(RouteResolveResp/ProductDefaultResp) + 平台码 ROUTE_NO_TARGET(3005)"
```

---

### Task 3: 路由配置 route_config CRUD

**Files:**
- Create: `.../route/dal/dataobject/route/RouteConfigDO.java`
- Create: `.../route/dal/mysql/route/RouteConfigMapper.java`
- Create: `.../route/controller/admin/route/vo/{RouteConfigSaveReqVO,RouteConfigPageReqVO,RouteConfigRespVO}.java`
- Create: `.../route/service/route/{RouteConfigService,RouteConfigServiceImpl}.java`
- Create: `.../route/controller/admin/route/RouteConfigController.java`
- Create: `.../route/enums/ErrorCodeConstants.java`
- Test: `.../route/service/route/RouteConfigServiceImplTest.java`
- Modify: `docker/mysql/init/07-route-schema.sql`、`.../test/resources/sql/create_tables.sql`、`clean.sql`
- （`.../route/...` = `yudao-module-route/src/main/java/cn/iocoder/yudao/module/route/...`）

**Interfaces:**
- Consumes: Task 1 基座。
- Produces:
  - `class RouteConfigDO extends BaseDO { Long id; String routeCode; String name; String productCode; Long orgId; String targetType; Long targetDsInterfaceId; Integer priority; Integer status; String remark; }`（`orgId` nullable：null=产品默认级，非空=机构产品级；`targetType`：`SINGLE`（本期唯一实现）/`SPLIT`/`CHAIN`/`DYNAMIC` 预留；`targetDsInterfaceId` 为 SINGLE 目标，adapter 数据源接口的松耦合引用，不校验存在性）
  - `interface RouteConfigService { Long createRouteConfig(RouteConfigSaveReqVO); void updateRouteConfig(RouteConfigSaveReqVO); void deleteRouteConfig(Long id); RouteConfigDO getRouteConfig(Long id); PageResult<RouteConfigDO> getRouteConfigPage(RouteConfigPageReqVO); List<RouteConfigDO> getListByProductCode(String productCode); }`
  - HTTP：`/route/config/{create,update,delete,get,page,list-by-product}`；权限串 `route:config:*`
  - `ErrorCodeConstants.ROUTE_CONFIG_NOT_EXISTS = new ErrorCode(1_023_001_000, "路由配置不存在")`

- [ ] **Step 1: ErrorCode + DDL**

`.../route/enums/ErrorCodeConstants.java`：

```java
package cn.iocoder.yudao.module.route.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** route 模块错误码，占用 1-023-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 路由配置 1-023-001-xxx ==========
    ErrorCode ROUTE_CONFIG_NOT_EXISTS = new ErrorCode(1_023_001_000, "路由配置不存在");
}
```

MySQL DDL 追加到 `docker/mysql/init/07-route-schema.sql`：

```sql
CREATE TABLE `route_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `route_code` varchar(32) NOT NULL COMMENT '路由编码 R+序号',
  `name` varchar(128) NOT NULL COMMENT '路由名称',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码(松耦合快照)',
  `org_id` bigint NULL DEFAULT NULL COMMENT '机构ID(空=产品默认级,非空=机构产品级)',
  `target_type` varchar(16) NOT NULL DEFAULT 'SINGLE' COMMENT '目标类型：SINGLE(本期)/SPLIT/CHAIN/DYNAMIC(预留)',
  `target_ds_interface_id` bigint NULL DEFAULT NULL COMMENT 'SINGLE 目标数据源接口ID(松耦合)',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级(越小越优先)',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_route_code` (`route_code`),
  KEY `idx_product_code` (`product_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由配置表';
```

H2 DDL 追加到 `create_tables.sql`：

```sql
CREATE TABLE IF NOT EXISTS "route_config" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "route_code" varchar(32) NOT NULL,
    "name" varchar(128) NOT NULL,
    "product_code" varchar(32) NOT NULL,
    "org_id" bigint DEFAULT NULL,
    "target_type" varchar(16) NOT NULL DEFAULT 'SINGLE',
    "target_ds_interface_id" bigint DEFAULT NULL,
    "priority" int NOT NULL DEFAULT 0,
    "status" tinyint NOT NULL DEFAULT 0,
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_route_config_code" UNIQUE ("route_code")
) COMMENT '路由配置表';
```

`clean.sql` 追加：`DELETE FROM "route_config";`

- [ ] **Step 2: 写失败测试**

`RouteConfigServiceImplTest.java`（结构照搬 P4 `OrgServiceImplTest`）：

```java
package cn.iocoder.yudao.module.route.service.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigSaveReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.route.enums.ErrorCodeConstants.ROUTE_CONFIG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(RouteConfigServiceImpl.class)
class RouteConfigServiceImplTest extends BaseDbUnitTest {

    @Resource private RouteConfigServiceImpl service;

    private RouteConfigSaveReqVO req(String productCode, Long orgId, Long dsIfId, int priority) {
        RouteConfigSaveReqVO vo = new RouteConfigSaveReqVO();
        vo.setName("路由");
        vo.setProductCode(productCode);
        vo.setOrgId(orgId);
        vo.setTargetType("SINGLE");
        vo.setTargetDsInterfaceId(dsIfId);
        vo.setPriority(priority);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesRouteCode() {
        Long id = service.createRouteConfig(req("P000001", null, 100L, 0));
        RouteConfigDO db = service.getRouteConfig(id);
        assertThat(db.getRouteCode()).matches("R\\d{6}");
        assertThat(db.getTargetDsInterfaceId()).isEqualTo(100L);
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        Long id2 = service.createRouteConfig(req("P000001", 5L, 200L, 1));
        String c2 = service.getRouteConfig(id2).getRouteCode();
        service.deleteRouteConfig(id2);
        Long id3 = service.createRouteConfig(req("P000002", null, 300L, 0));
        assertThat(service.getRouteConfig(id3).getRouteCode()).isNotEqualTo(c2).matches("R\\d{6}");
    }

    @Test
    void update_notExists_throws() {
        RouteConfigSaveReqVO upd = req("P000001", null, 100L, 0);
        upd.setId(99999L);
        assertServiceException(() -> service.updateRouteConfig(upd), ROUTE_CONFIG_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createRouteConfig(req("P000001", null, 100L, 0));
        service.deleteRouteConfig(id);
        assertThat(service.getRouteConfig(id)).isNull();
    }

    @Test
    void listByProductCode_filters() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        service.createRouteConfig(req("P000001", 5L, 200L, 1));
        service.createRouteConfig(req("P000002", null, 300L, 0));
        assertThat(service.getListByProductCode("P000001")).hasSize(2);
    }

    @Test
    void page_filtersByProductCode() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        service.createRouteConfig(req("P000002", null, 300L, 0));
        RouteConfigPageReqVO q = new RouteConfigPageReqVO();
        q.setProductCode("P000001");
        PageResult<RouteConfigDO> page = service.getRouteConfigPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败** — 编译失败。

- [ ] **Step 4: 最小实现**

`RouteConfigDO.java`：`@TableName("route_config")` + `@KeySequence("route_config_seq")` + `@Data` + `@TenantIgnore`，字段见 Interfaces（写法照搬 P4 `OrgDO`）。

`RouteConfigMapper.java`（`@Mapper extends BaseMapperX<RouteConfigDO>`；`selectPage` 按 `productCode(eq)`/`orgId`/`targetType`/`status` 过滤 + `orderByDesc(id)`；`selectListByProductCode(String)` = `selectList(RouteConfigDO::getProductCode, code)`；**新增** `selectMatched(String productCode, Long orgId)` 供 Task 5 解析用，**按 orgId 是否为 null 分支**：

```java
default List<RouteConfigDO> selectMatched(String productCode, Long orgId) {
    LambdaQueryWrapperX<RouteConfigDO> w = new LambdaQueryWrapperX<RouteConfigDO>()
            .eq(RouteConfigDO::getProductCode, productCode)
            .eq(RouteConfigDO::getStatus, 0);
    if (orgId == null) {
        w.isNull(RouteConfigDO::getOrgId);
    } else {
        w.and(x -> x.eq(RouteConfigDO::getOrgId, orgId).or().isNull(RouteConfigDO::getOrgId));
    }
    return selectList(w);
}
```
无 `selectMaxId`）。

VO 三件：`RouteConfigSaveReqVO`（`id`；`name` `@NotEmpty`；`productCode` `@NotEmpty`；`orgId`(可空)；`targetType`（可空，默认 SINGLE）；`targetDsInterfaceId`；`priority`；`status` `@NotNull`；`remark`）、`RouteConfigPageReqVO extends PageParam`（`productCode`/`orgId`/`targetType`/`status`）、`RouteConfigRespVO`（镜像 DO + createTime）。

`RouteConfigServiceImpl`：`createRouteConfig` 用 id 派生 `R%06d`（临时 UUID 占位 → 回填，`@Transactional`；照搬 P4 `OrgServiceImpl.createOrg`），若 `targetType` 空则默认 `"SINGLE"`；`updateRouteConfig` 校验存在（`ROUTE_CONFIG_NOT_EXISTS`）后 `setRouteCode(null)` 再 `updateById`；`getListByProductCode`/`getRouteConfigPage` 调 mapper。

`RouteConfigController.java`：`/route/config` 六端点（含 `list-by-product`），权限串 `route:config:*`，写法照搬 P4 `OrgController`。

- [ ] **Step 5: 运行验证通过** — `RouteConfigServiceImplTest` 6 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-route docker/mysql/init/07-route-schema.sql
git commit -m "feat(route): 路由配置 route_config CRUD（三级字段+R码id派生+targetType预留）"
```

---

### Task 4: product-server 暴露产品默认绑定 RPC

**Files:**
- Create: `yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/controller/rpc/ProductResolveRpcController.java`
- Test: `yudao-module-product/src/test/java/cn/iocoder/yudao/module/product/controller/rpc/ProductResolveRpcControllerTest.java`

**Interfaces:**
- Consumes: 现有 `cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver`（`resolveDefault(String productCode) → ResolvedInterface`，已存在，不改）；apiten-common `ProductDefaultRespDTO`（Task 2）。
- Produces:
  - HTTP `GET /rpc-api/product/resolve-default?productCode=` → `ProductDefaultRespDTO`（内部端点，`@PermitAll` + `@TenantIgnore`）。逻辑：调 `resolver.resolveDefault(productCode)`；非空 → 填 `dsInterfaceId`/`dsInterfaceCode`；产品不存在（`resolveDefault` 抛 `PRODUCT_NOT_EXISTS`）或无默认绑定 → 返回 `dsInterfaceId=null` 的空 DTO（不把异常抛给调用方——路由层据 null 走 no-target）。

**实现要点：** 用 try/catch 包住 `resolveDefault`，捕获 `ServiceException`（产品不存在）返回空 DTO；`resolveDefault` 返回 null（无绑定）也返回空 DTO。`@PermitAll` 用与 P4 `OrgAuthRpcController` **相同的导入**（`jakarta.annotation.security.PermitAll`），`@TenantIgnore` 用 `cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore`。

- [ ] **Step 1: 写失败测试（H2，多表造数）**

`ProductResolveRpcControllerTest.java`（用 `BaseDbUnitTest` + `@Import` resolver 与 controller，经真实 mapper 造产品/功能/绑定；参考现有 `ProductInterfaceResolverTest` 的造数辅助法）：

```java
package cn.iocoder.yudao.module.product.controller.rpc;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@Import({ProductResolveRpcController.class, ProductInterfaceResolver.class})
class ProductResolveRpcControllerTest extends BaseDbUnitTest {

    @Resource private ProductResolveRpcController controller;
    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    private Long product(String code) {
        ProductDO p = new ProductDO();
        p.setProductCode(code); p.setName("产品"); p.setProductType(1); p.setStatus(0);
        productMapper.insert(p); return p.getId();
    }
    private Long func(Long productId, String code) {
        ProductFunctionDO f = new ProductFunctionDO();
        f.setFuncCode(code); f.setName("功能"); f.setProductId(productId); f.setStatus(0);
        functionMapper.insert(f); return f.getId();
    }
    private void bind(Long funcId, Long ifId, String ifCode, int priority, boolean isDefault) {
        FuncInterfaceDO b = new FuncInterfaceDO();
        b.setProductFunctionId(funcId); b.setDsInterfaceId(ifId); b.setDsInterfaceCode(ifCode);
        b.setPriority(priority); b.setIsDefault(isDefault); b.setStatus(0);
        bindMapper.insert(b);
    }

    @Test
    void resolveDefault_returnsDefaultBinding() {
        Long pid = product("P000001");
        Long fid = func(pid, "F000001");
        bind(fid, 100L, "IF000001", 1, false);
        bind(fid, 200L, "IF000002", 2, true); // 默认优先
        ProductDefaultRespDTO d = controller.resolveDefault("P000001");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L);
        assertThat(d.getDsInterfaceCode()).isEqualTo("IF000002");
    }

    @Test
    void resolveDefault_noBinding_returnsEmpty() {
        product("P000002");
        ProductDefaultRespDTO d = controller.resolveDefault("P000002");
        assertThat(d.getDsInterfaceId()).isNull();
    }

    @Test
    void resolveDefault_productNotExists_returnsEmpty_notThrow() {
        ProductDefaultRespDTO d = controller.resolveDefault("NOPE");
        assertThat(d.getDsInterfaceId()).isNull();
    }
}
```

- [ ] **Step 2: 运行验证失败** — `mvn -pl yudao-module-product -am test -q` 编译失败。

- [ ] **Step 3: 最小实现**

`ProductResolveRpcController.java`：

```java
package cn.iocoder.yudao.module.product.controller.rpc;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import jakarta.annotation.security.PermitAll;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver;
import cn.iocoder.yudao.module.product.service.resolve.ResolvedInterface;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/rpc-api/product")
public class ProductResolveRpcController {

    @Resource
    private ProductInterfaceResolver resolver;

    @GetMapping("/resolve-default")
    @PermitAll
    @TenantIgnore
    public ProductDefaultRespDTO resolveDefault(@RequestParam("productCode") String productCode) {
        ProductDefaultRespDTO dto = new ProductDefaultRespDTO();
        try {
            ResolvedInterface ri = resolver.resolveDefault(productCode);
            if (ri != null) {
                dto.setDsInterfaceId(ri.getDsInterfaceId());
                dto.setDsInterfaceCode(ri.getDsInterfaceCode());
            }
        } catch (ServiceException ignore) {
            // 产品不存在 → 返回空 DTO，交路由层据 null 处理，不抛给调用方
        }
        return dto;
    }
}
```

> 注：`@PermitAll` 与 P4 `OrgAuthRpcController` 一致用 `jakarta.annotation.security.PermitAll`；`ServiceException` 全名 `cn.iocoder.yudao.framework.common.exception.ServiceException`。实现者若发现 P4 用的导入不同，以 P4 实际为准并在报告确认。

- [ ] **Step 4: 运行验证通过** — `ProductResolveRpcControllerTest` 3 用例全过；product 模块既有测试保持绿。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-product/src
git commit -m "feat(product): 暴露产品默认绑定 RPC /rpc-api/product/resolve-default（供路由公底）"
```

---

### Task 5: RouteResolver + /rpc-api/route/resolve（route_config 查找 → product 公底）

**Files:**
- Create: `.../route/client/ProductResolveClient.java`
- Create: `.../route/service/resolve/RouteResolver.java`
- Create: `.../route/controller/rpc/RouteResolveRpcController.java`
- Modify: `.../route/RouteServerApplication.java`（加 `@EnableFeignClients`，若 Task 1 未加）
- Test: `.../route/service/resolve/RouteResolverTest.java`

**Interfaces:**
- Consumes: Task 3 `RouteConfigMapper.selectMatched(productCode, orgId)`；Task 2 `RouteResolveRespDTO`/`ProductDefaultRespDTO`；product-server `/rpc-api/product/resolve-default`（Task 4，经 Feign）。
- Produces:
  - `@FeignClient(name="product-server", path="/rpc-api/product") interface ProductResolveClient { @GetMapping("/resolve-default") ProductDefaultRespDTO resolveDefault(@RequestParam("productCode") String productCode); }`
  - `@Component class RouteResolver { RouteResolveRespDTO resolve(String productCode, Long orgId); }`——逻辑见下。**Feign 客户端在单测不可用**，故 `RouteResolver` 字段注入 `ProductResolveClient`，单测用 `@TestConfiguration` 提供手写 stub bean（不连真实 product-server）。
  - HTTP `GET /rpc-api/route/resolve?productCode=&orgId=`（orgId 可选）→ `RouteResolveRespDTO`（`@PermitAll` + `@TenantIgnore`）。

**RouteResolver.resolve 逻辑：**
1. `List<RouteConfigDO> matched = routeConfigMapper.selectMatched(productCode, orgId)`（已过滤 status=0 且 orgId 分支）。
2. 内存取最优：仅 `targetType=="SINGLE"` 且 `targetDsInterfaceId!=null`；**机构行（orgId!=null）优先于产品默认级行（orgId==null）**，同层按 priority 升序。取首个 → `RouteResolveRespDTO.of(dsInterfaceId, "ROUTE_CONFIG")`。
3. 无 SINGLE 命中 → `productResolveClient.resolveDefault(productCode)`；`dsInterfaceId!=null` → `of(dsInterfaceId, "PRODUCT_DEFAULT")`。
4. 仍无 → `RouteResolveRespDTO.noTarget()`。

- [ ] **Step 1: 写失败测试（H2 + 手写 ProductResolveClient stub）**

`RouteResolverTest.java`：

```java
package cn.iocoder.yudao.module.route.service.resolve;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.route.client.ProductResolveClient;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.dal.mysql.route.RouteConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@Import({RouteResolver.class, RouteResolverTest.StubConfig.class})
class RouteResolverTest extends BaseDbUnitTest {

    @Resource private RouteResolver resolver;
    @Resource private RouteConfigMapper mapper;

    /** 手写 stub：对任意 productCode 返回 dsInterfaceId=stubDefault（模拟产品公底） */
    static Long stubDefault = 900L;

    @TestConfiguration
    static class StubConfig {
        @Bean ProductResolveClient productResolveClient() {
            return productCode -> {
                ProductDefaultRespDTO d = new ProductDefaultRespDTO();
                d.setDsInterfaceId(stubDefault);
                return d;
            };
        }
    }

    private void route(String productCode, Long orgId, String type, Long dsIfId, int priority, int status) {
        RouteConfigDO r = new RouteConfigDO();
        r.setRouteCode("R" + (System.nanoTime() % 1000000));
        r.setName("r"); r.setProductCode(productCode); r.setOrgId(orgId);
        r.setTargetType(type); r.setTargetDsInterfaceId(dsIfId); r.setPriority(priority); r.setStatus(status);
        mapper.insert(r);
    }

    @Test
    void orgSpecificWins_overProductDefaultLevel() {
        route("P000001", null, "SINGLE", 100L, 0, 0); // 产品默认级
        route("P000001", 5L, "SINGLE", 200L, 9, 0);   // 机构产品级(priority 更大但 org 命中优先)
        RouteResolveRespDTO r = resolver.resolve("P000001", 5L);
        assertThat(r.getDsInterfaceId()).isEqualTo(200L);
        assertThat(r.getSource()).isEqualTo("ROUTE_CONFIG");
    }

    @Test
    void productLevelUsed_whenNoOrgMatch() {
        route("P000001", null, "SINGLE", 100L, 0, 0);
        route("P000001", 5L, "SINGLE", 200L, 0, 0);
        RouteResolveRespDTO r = resolver.resolve("P000001", 999L); // orgId 不匹配任何机构行
        assertThat(r.getDsInterfaceId()).isEqualTo(100L); // 回落 orgId=null 产品默认级行
    }

    @Test
    void priorityBreaksTie_withinSameLevel() {
        route("P000001", null, "SINGLE", 100L, 5, 0);
        route("P000001", null, "SINGLE", 300L, 1, 0); // priority 更小
        RouteResolveRespDTO r = resolver.resolve("P000001", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(300L);
    }

    @Test
    void disabledRoute_ignored_fallsBackToProductDefault() {
        route("P000001", null, "SINGLE", 100L, 0, 1); // 停用
        RouteResolveRespDTO r = resolver.resolve("P000001", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(900L); // 无启用 route_config → 产品公底
        assertThat(r.getSource()).isEqualTo("PRODUCT_DEFAULT");
    }

    @Test
    void fallsBackToProductDefault_whenNoRouteConfig() {
        RouteResolveRespDTO r = resolver.resolve("P999999", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(900L);
        assertThat(r.getSource()).isEqualTo("PRODUCT_DEFAULT");
    }

    @Test
    void noTarget_whenNoRouteAndNoProductDefault() {
        stubDefault = null; // 产品也无默认绑定
        try {
            RouteResolveRespDTO r = resolver.resolve("P888888", null);
            assertThat(r.getDsInterfaceId()).isNull();
            assertThat(r.getSource()).isEqualTo("NONE");
            assertThat(r.getPlatformCode()).isEqualTo("3005");
        } finally {
            stubDefault = 900L; // 复原，避免污染其它用例
        }
    }
}
```

> 注：`ProductResolveClient` 单方法接口，stub 用 lambda 实现。`noTarget_when...` 修改静态 `stubDefault` 后 finally 复原，避免顺序依赖。

- [ ] **Step 2: 运行验证失败** — 编译失败。

- [ ] **Step 3: 最小实现**

`ProductResolveClient.java`：

```java
package cn.iocoder.yudao.module.route.client;

import cn.apiten.common.route.ProductDefaultRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-server", path = "/rpc-api/product")
public interface ProductResolveClient {
    @GetMapping("/resolve-default")
    ProductDefaultRespDTO resolveDefault(@RequestParam("productCode") String productCode);
}
```

`RouteResolver.java`：

```java
package cn.iocoder.yudao.module.route.service.resolve;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.module.route.client.ProductResolveClient;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.dal.mysql.route.RouteConfigMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;

@Component
public class RouteResolver {

    @Resource private RouteConfigMapper routeConfigMapper;
    @Resource private ProductResolveClient productResolveClient;

    public RouteResolveRespDTO resolve(String productCode, Long orgId) {
        List<RouteConfigDO> matched = routeConfigMapper.selectMatched(productCode, orgId);
        RouteConfigDO best = matched.stream()
                .filter(r -> "SINGLE".equals(r.getTargetType()) && r.getTargetDsInterfaceId() != null)
                // 机构行(orgId!=null)优先于产品默认级(orgId==null)；同层 priority 升序
                .min(Comparator
                        .comparing((RouteConfigDO r) -> r.getOrgId() == null) // false(机构行) 排前
                        .thenComparingInt(r -> r.getPriority() == null ? 0 : r.getPriority()))
                .orElse(null);
        if (best != null) {
            return RouteResolveRespDTO.of(best.getTargetDsInterfaceId(), "ROUTE_CONFIG");
        }
        ProductDefaultRespDTO def = productResolveClient.resolveDefault(productCode);
        if (def != null && def.getDsInterfaceId() != null) {
            return RouteResolveRespDTO.of(def.getDsInterfaceId(), "PRODUCT_DEFAULT");
        }
        return RouteResolveRespDTO.noTarget();
    }
}
```

`RouteResolveRpcController.java`：

```java
package cn.iocoder.yudao.module.route.controller.rpc;

import cn.apiten.common.route.RouteResolveRespDTO;
import jakarta.annotation.security.PermitAll;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.route.service.resolve.RouteResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/rpc-api/route")
public class RouteResolveRpcController {

    @Resource
    private RouteResolver routeResolver;

    @GetMapping("/resolve")
    @PermitAll
    @TenantIgnore
    public RouteResolveRespDTO resolve(@RequestParam("productCode") String productCode,
            @RequestParam(value = "orgId", required = false) Long orgId) {
        return routeResolver.resolve(productCode, orgId);
    }
}
```

`RouteServerApplication.java`：确保类上有 `@EnableFeignClients`（`org.springframework.cloud.openfeign.EnableFeignClients`，扫描到 `cn.iocoder.yudao.module.route.client`）。

- [ ] **Step 4: 运行验证通过** — `RouteResolverTest` 6 用例全过（离线，Feign 被 stub 替换）。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-route
git commit -m "feat(route): RouteResolver 三级解析(route_config→产品公底) + /rpc-api/route/resolve + Feign→product"
```

---

### Task 6: openapi 接线——路由解析 dsInterfaceId 走真实 HTTP 引擎

**Files:**
- Create: `yudao-module-openapi/.../client/RouteClient.java`
- Modify: `yudao-module-openapi/.../controller/QueryController.java`（读 `X-Org-Id` 头）
- Modify: `yudao-module-openapi/.../service/QueryOrchestrator.java`（路由解析 → 设 dsInterfaceId / ROUTE_NO_TARGET）
- Modify: `yudao-module-openapi/.../OpenApiServerApplication.java`（确认 `@EnableFeignClients` 覆盖 client 包，若已启用则不动）
- Test: `yudao-module-openapi/.../controller/QueryControllerTest.java`（补路由解析用例）

**Interfaces:**
- Consumes: Task 2 `RouteResolveRespDTO`、`PlatformErrorCode.ROUTE_NO_TARGET`；Task 5 route-server `/rpc-api/route/resolve`；现有 `AdapterClient`（`ProviderRequest.dsInterfaceId` 已存在）。
- Produces:
  - `@FeignClient(name="route-server", path="/rpc-api/route") interface RouteClient { @GetMapping("/resolve") RouteResolveRespDTO resolve(@RequestParam("productCode") String productCode, @RequestParam(value="orgId",required=false) Long orgId); }`
  - `QueryController.query` 增加 `@RequestHeader(value="X-Org-Id", required=false) Long orgId`，传 `orchestrator.query(productCode, params, flowNo, orgId)`。
  - `QueryOrchestrator.query(String productCode, Map params, String flowNo, Long orgId)`：真实路径（adapter client 可用）先经 `ObjectProvider<RouteClient>` 解析：解析到 dsInterfaceId → 设 `ProviderRequest.dsInterfaceId` 后调 adapter（走 P2 HTTP 引擎）；无目标（`dsInterfaceId==null`）→ 直接返回 `ApiResponse.of(flowNo, productCode, ROUTE_NO_TARGET, false, costTime, null)`（不调 adapter）。RouteClient 不可用（单测/降级）→ 保持原行为（不设 dsInterfaceId，adapter mock 兜底）。保留旧 2/3 参签名重载委托新签名。

- [ ] **Step 1: 写失败测试**

在 `QueryControllerTest.java` 追加（`QueryOrchestrator` 现有单测用手动 `new` 构造 + `ObjectProvider`，参考 P0 Task5/8 笔记）：

```java
    // 路由解析到 dsInterfaceId：stub AdapterClient 捕获入参，断言 ProviderRequest.dsInterfaceId 透传
    @Test
    void query_resolvesRoute_setsDsInterfaceIdOnProviderRequest() {
        // stub RouteClient.resolve → RouteResolveRespDTO.of(555L,"ROUTE_CONFIG")
        // stub AdapterClient.invoke 记录 req.getDsInterfaceId()，返回 platformCode="0000"
        // 断言捕获到的 dsInterfaceId == 555，且 resp.getCode()=="0000"
    }

    // 路由无目标：返回 3005，不调 adapter
    @Test
    void query_routeNoTarget_returns3005_withoutCallingAdapter() {
        // stub RouteClient.resolve → RouteResolveRespDTO.noTarget()
        // 断言 resp.getCode()=="3005"，且 stub AdapterClient.invoke 从未被调用
    }
```

> 实现者把 `QueryOrchestrator` 构造器扩展为再加 `ObjectProvider<RouteClient> routeClientProvider`；测试用 `new QueryOrchestrator(adapterProvider, kafkaProvider, routeProvider)` 手动构造，provider 用 lambda/stub（`ObjectProvider` 可用一个返回固定实例的匿名实现，或 Mockito `mock(ObjectProvider.class)` + `getIfAvailable()`）。stub `AdapterClient` 实现 `invoke` 记录收到的 `ProviderRequest` 并返回 `platformCode="0000"` 的 `ProviderResponse`。保留既有 flowNo/mock 用例全绿。

- [ ] **Step 2: 运行验证失败** — 编译失败。

- [ ] **Step 3: 最小实现**

`RouteClient.java`：

```java
package cn.iocoder.yudao.module.openapi.client;

import cn.apiten.common.route.RouteResolveRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "route-server", path = "/rpc-api/route")
public interface RouteClient {
    @GetMapping("/resolve")
    RouteResolveRespDTO resolve(@RequestParam("productCode") String productCode,
            @RequestParam(value = "orgId", required = false) Long orgId);
}
```

`QueryOrchestrator.java`：构造器加 `ObjectProvider<RouteClient> routeClientProvider`（存字段）。`query` 新签名：

```java
public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params,
        String flowNo, Long orgId) {
    long start = System.currentTimeMillis();
    String usedFlowNo = (flowNo != null && !flowNo.isBlank()) ? flowNo : idGen.nextIdStr();
    AdapterClient client = adapterClientProvider.getIfAvailable();
    ApiResponse<Map<String, Object>> resp;
    if (client == null) { // 单测/降级：保持 P0 mock 分支
        Map<String, Object> data = Map.of("mock", true, "echo", params);
        resp = ApiResponse.of(usedFlowNo, productCode, PlatformErrorCode.SUCCESS, true,
                System.currentTimeMillis() - start, data);
    } else {
        Long dsInterfaceId = null;
        RouteClient routeClient = routeClientProvider.getIfAvailable();
        if (routeClient != null) {
            RouteResolveRespDTO route = routeClient.resolve(productCode, orgId);
            if (route == null || route.getDsInterfaceId() == null) {
                return ApiResponse.of(usedFlowNo, productCode, PlatformErrorCode.ROUTE_NO_TARGET,
                        false, System.currentTimeMillis() - start, null);
            }
            dsInterfaceId = route.getDsInterfaceId();
        }
        ProviderRequest req = new ProviderRequest();
        req.setProductCode(productCode);
        req.setParams(params);
        req.setDsInterfaceId(dsInterfaceId); // 非空 → adapter 走 P2 HTTP 引擎；空 → mock 兜底
        ProviderResponse providerResp = client.invoke(req);
        PlatformErrorCode ec = "0000".equals(providerResp.getPlatformCode())
                ? PlatformErrorCode.SUCCESS : PlatformErrorCode.UPSTREAM_ERROR;
        resp = ApiResponse.of(usedFlowNo, productCode, ec, ec == PlatformErrorCode.SUCCESS,
                System.currentTimeMillis() - start, providerResp.getData());
    }
    sendFlowEvent(resp, productCode, start);
    return resp;
}

public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params, String flowNo) {
    return query(productCode, params, flowNo, null);
}
public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
    return query(productCode, params, null, null);
}
```

新增 `import cn.iocoder.yudao.module.openapi.client.RouteClient;` `import cn.apiten.common.route.RouteResolveRespDTO;`。构造器三 provider，Spring 自动注入（`RouteClient` 是 Feign bean）。

`QueryController.java`：

```java
    @PostMapping("/{productCode}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String productCode,
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "X-Flow-No", required = false) String flowNo,
            @RequestHeader(value = "X-Org-Id", required = false) Long orgId) {
        return orchestrator.query(productCode, params, flowNo, orgId);
    }
```

`OpenApiServerApplication.java`：确认有 `@EnableFeignClients`（现有 `AdapterClient` 已是 Feign，应已启用；若 basePackages 限定，确保覆盖 `cn.iocoder.yudao.module.openapi.client`）。无需改则不动。

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-openapi -am test -q`
Expected：新增 2 路由用例 + 既有 flowNo/mock 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-openapi/src
git commit -m "feat(openapi): 接线路由解析(RouteClient+X-Org-Id)→设 dsInterfaceId 走真实HTTP引擎，无目标返回3005"
```

---

## P5 完成定义（DoD）

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test`、`-pl yudao-module-route -am test`、`-pl yudao-module-product -am test`、`-pl yudao-module-openapi -am test` 全绿，离线（H2，Feign 被 stub，不连中间件）。
2. `route_config` 表具备完整 CRUD + R 码 id 派生 + 三级字段（product_code / org_id nullable / priority）+ targetType 预留。
3. `RouteResolver` 按 productCode+orgId 三级解析：机构产品行（orgId 命中）优先于产品默认级（orgId=null），同层 priority 升序；未命中经 Feign 回落 product-server 产品默认绑定；仍无 → `ROUTE_NO_TARGET(3005)`。
4. product-server 暴露 `/rpc-api/product/resolve-default`（产品不存在/无绑定返回空 DTO 不抛）；route-server 暴露 `/rpc-api/route/resolve`。
5. openapi 真实路径经路由解析设 `ProviderRequest.dsInterfaceId` → adapter 走 **P2 真实 HTTP 引擎**；无目标短路返回 3005；RouteClient 不可用时降级不影响既有单测。
6. **端到端 curl 冒烟**（本地起 gateway+org+product+route+adapter+openapi+mysql 后手动，记录到执行报告）：配好 ds_interface（P1/P2 联调可用接口）+ product 功能绑定 + route_config（或仅靠产品默认公底）+ org 开通 P00000x；带 AK/SK 签名请求 `POST /api/v1/P00000x/query`，预期命中真实上游、`code=0000`、`data` 来自上游映射；删掉所有绑定与路由后同请求预期 `3005`。
7. MySQL DDL 并入 `docker/mysql/init/07-route-schema.sql`，运行库 `apiten_route` 可承载。
8. 每任务一个 commit；全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续计划（本计划不含）

- **路由二期**：SPLIT 权重分流组、CHAIN 切换链（switch_rule/switch_chain/chain_node + 失败切换执行）、DYNAMIC 动态选源（指标采集 dynamic_strategy 多因子打分 + 熔断/保底/人工锁定/决策留痕）；附加匹配条件（参数/区域/功能/账号/时间段）；org-wide 机构级中间层；路由模拟器；分流/选源命中写入机构流水扩展字段。
- **配置里程碑**：config_release 配置快照版本化发布/回滚 + Nacos/Redis Pub-Sub 热更新，openapi/adapter/route 本地缓存快照（替换本期直查 DB + 每调用 RPC）。
- **计费/流水/限流里程碑**：见 P4 后续计划。
- 前端页面（路由管理三级视图）与 sys_menu 菜单/按钮权限落库。
