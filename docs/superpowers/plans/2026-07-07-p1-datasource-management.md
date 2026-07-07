# 征信 API 平台 P1：数据源管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 yudao-module-adapter 模块内落地「数据源域」的持久化与管理端 CRUD：数据源、数据源接口、数据源应答码（含四标记与未映射码查询）、数据源接口参数映射定义，为后续 HTTP 适配引擎（P2）提供可配置的接入元数据。

**Architecture:** 沿用本仓库 P0 确立的**扁平单模块**范式（adapter 不拆 -api/-server），给 adapter 模块接入 MyBatis-Plus 持久化基建（`yudao-spring-boot-starter-mybatis` + H2 单元测试基座 `BaseDbUnitTest`），按 yudao 标准 CRUD 纵切（DO/Mapper/VO/Service/Controller/ErrorCode/DDL/Test）实现四张表。数据源域独立于 HTTP 适配引擎交付——本计划只做「配置管理」，不做「运行时调用」（运行时消费这些配置是 P2 的工作）。

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus（`BaseDO`/`BaseMapperX`/`LambdaQueryWrapperX`）、yudao 框架（`CommonResult`/`PageResult`/`PageParam`/`BeanUtils.toBean`/`ServiceExceptionUtil.exception`）、H2 内存库单测（`BaseDbUnitTest`）、JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§4.2 数据源适配服务、§6.2 数据源管理、§7 数据模型（数据源域）、§8.1 数据源接入流程。

## Global Constraints

- JDK 21；Maven 命令统一前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`。
- **扁平模块范式**：所有代码位于 `yudao-module-adapter`（无 -api/-server 拆分）；业务包根 `cn.iocoder.yudao.module.adapter`；错误码常量置于 `cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants`。
- **表命名**：yudao 模块前缀约定，四表为 `adapter_data_source`、`adapter_ds_interface`、`adapter_ds_response_code`、`adapter_ds_interface_param`；均含 `BaseDO` 五审计列（`creator/create_time/updater/update_time/deleted`）；单租户（不加 `tenant_id`，DO 继承 `BaseDO` + `@TenantIgnore`）。
- **运行库**：adapter 运行时使用独立库 `apiten_adapter`（MySQL `127.0.0.1:23306`，本地 compose）；单元测试用 H2 内存库（不依赖任何中间件，必须离线通过）。
- **编码规则**（§7）：数据源 `DS`+6 位序号（DS000001）、接口 `IF`+6 位序号；由服务端生成，全局唯一。
- **应答码映射四标记**（§4.2.2/§6.2.3）：是否成功、是否计费、是否可重试、是否触发切换；`platformCode` 取值对齐 apiten-common `PlatformErrorCode`（字符串码，如 `0000`/`3001`）；未映射原始码归一 `3001` 由运行时（P2）处理，本期只做「未映射码查询」列表。
- **权限串**：`@ss.hasPermission('adapter:<kebab-domain>:{create|update|delete|query}')`（超级管理员运行时自动放行；菜单/按钮 sys_menu 落库留待前端里程碑）。
- **错误码区间**：adapter 模块占用 `1-020-xxx-xxx` 段，各实体分组：data_source=`1_020_001_xxx`、ds_interface=`1_020_002_xxx`、ds_response_code=`1_020_003_xxx`、ds_interface_param=`1_020_004_xxx`。
- **DDL 双写**：每张表同时提供 MySQL DDL（追加到 `sql/mysql/ruoyi-vue-pro.sql`，运行库用 docker exec 应用）与 H2 DDL（`yudao-module-adapter/src/test/resources/sql/create_tables.sql` + `clean.sql` 的 `DELETE`）。
- 每个任务结束必须 `git commit`（仅 `git add` 改动文件，不用 `-A`）；测试命令 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`。
- **不在本期范围**（留待后续里程碑）：联调测试台真实外调、健康检查探活、配额账本（P7）、路由/分流/切换链（P4）、成本/计费（P5）、前端页面与菜单、HTTP 适配引擎运行时（P2）。

---

### Task 1: adapter 模块持久化基建（MyBatis-Plus + H2 单测基座 + apiten_adapter 库）

**Files:**
- Modify: `yudao-module-adapter/pom.xml`（加 mybatis starter + test starter）
- Modify: `docker/mysql/init/01-create-databases.sql`（加 `apiten_adapter` 库）
- Modify: `yudao-module-adapter/src/main/resources/application.yaml`、`application-local.yaml`、`application-dev.yaml`（数据源配置）
- Create: `yudao-module-adapter/src/test/resources/application-unit-test.yaml`
- Create: `yudao-module-adapter/src/test/resources/sql/create_tables.sql`（先建一张 smoke 表）
- Create: `yudao-module-adapter/src/test/resources/sql/clean.sql`
- Create: `yudao-module-adapter/src/test/resources/logback.xml`
- Create: `yudao-module-adapter/src/test/java/cn/iocoder/yudao/module/adapter/DbHarnessSmokeTest.java`

**Interfaces:**
- Consumes: P0 的 adapter 模块（现有 `DataSourceProvider`/`MockProvider`/`InvokeController` 保持不变）。
- Produces: adapter 模块具备 DB 持久化能力与可离线运行的 `BaseDbUnitTest` 测试基座；运行库 `apiten_adapter` 就绪；后续任务的 DO/Mapper/Service 直接落在此基座上。

- [ ] **Step 1: adapter pom 增加持久化与测试依赖**

先确认坐标：`grep -n 'yudao-spring-boot-starter-mybatis\|yudao-spring-boot-starter-test' yudao-module-infra/yudao-module-infra-server/pom.xml`，照抄其 groupId（可能是 `cn.iocoder.boot` 或 `cn.iocoder.cloud`）。在 `yudao-module-adapter/pom.xml` 的 `<dependencies>` 内、现有依赖之后追加：

```xml
    <dependency>
      <groupId>cn.iocoder.boot</groupId>
      <artifactId>yudao-spring-boot-starter-mybatis</artifactId>
    </dependency>
    <dependency>
      <groupId>cn.iocoder.boot</groupId>
      <artifactId>yudao-spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 2: 新增 apiten_adapter 运行库**

编辑 `docker/mysql/init/01-create-databases.sql`，追加一行：

```sql
CREATE DATABASE IF NOT EXISTS `apiten_adapter` DEFAULT CHARACTER SET utf8mb4;
```

对已在运行的容器立即应用（init 脚本仅在空卷首次启动执行）：

```bash
docker exec apiten-mysql-1 mysql -uroot -papiten123 -e "CREATE DATABASE IF NOT EXISTS \`apiten_adapter\` DEFAULT CHARACTER SET utf8mb4;"
```

- [ ] **Step 3: adapter 运行时数据源配置**

先看基座结构：`sed -n '/datasource/,/dynamic/p' yudao-module-infra/yudao-module-infra-server/src/main/resources/application-local.yaml`，照搬 `spring.datasource.dynamic` 键层级。`application-local.yaml` 追加（仅本地默认中间件指向）：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:mysql://127.0.0.1:23306/apiten_adapter?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
          username: root
          password: apiten123
```

`application-dev.yaml` 追加同结构，`url` 主机端口用 `127.0.0.1:3306`（dev 占位，与 P0 dev profile 约定一致）。若基座实际是 Nacos 下发共享 datasource，则在 `application.yaml` 的 `spring.datasource.dynamic` 里补 `apiten_adapter` 的 master 数据源。

- [ ] **Step 4: 单元测试资源（H2 基座）**

`src/test/resources/application-unit-test.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MYSQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=value;
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      schema-locations: classpath:/sql/create_tables.sql
mybatis-plus:
  type-aliases-package: ${yudao.info.base-package}.dal.dataobject
  global-config:
    db-config:
      id-type: AUTO
yudao:
  info:
    base-package: cn.iocoder.yudao.module.adapter
```

`src/test/resources/sql/create_tables.sql`（先放一张 smoke 表，后续任务往此文件追加真实表）：

```sql
CREATE TABLE IF NOT EXISTS "adapter_db_harness_smoke" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "name" varchar(64) NOT NULL,
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '持久化基座冒烟表';
```

`src/test/resources/sql/clean.sql`：

```sql
DELETE FROM "adapter_db_harness_smoke";
```

`src/test/resources/logback.xml`（照抄 `yudao-module-infra-server/src/test/resources/logback.xml`；若无则用最小配置）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 5: 写冒烟测试验证 DB 基座可离线启动**

`src/test/java/cn/iocoder/yudao/module/adapter/DbHarnessSmokeTest.java`：

```java
package cn.iocoder.yudao.module.adapter;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.Resource;
import static org.assertj.core.api.Assertions.assertThat;

class DbHarnessSmokeTest extends BaseDbUnitTest {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void h2Schema_isLoaded_andWritable() {
        jdbcTemplate.update("INSERT INTO \"adapter_db_harness_smoke\" (\"name\") VALUES (?)", "ok");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"adapter_db_harness_smoke\"", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 6: 运行测试验证失败→通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected（未加依赖前先跑）：`BaseDbUnitTest` 不存在 → 编译失败；补齐 Step 1 依赖与资源后再跑：`Tests run: 2, Failures: 0`（含 P0 的 `MockProviderTest` 1 个 + 本冒烟 1 个），且不连任何中间件。

- [ ] **Step 7: Commit**

```bash
git add yudao-module-adapter/pom.xml yudao-module-adapter/src/main/resources yudao-module-adapter/src/test docker/mysql/init/01-create-databases.sql
git commit -m "chore(adapter): 接入 MyBatis-Plus 持久化与 H2 单测基座，新增 apiten_adapter 库"
```

---

### Task 2: 数据源信息 data_source CRUD

**Files:**
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/dal/dataobject/datasource/DataSourceDO.java`
- Create: `.../adapter/dal/mysql/datasource/DataSourceMapper.java`
- Create: `.../adapter/controller/admin/datasource/vo/DataSourceSaveReqVO.java`
- Create: `.../adapter/controller/admin/datasource/vo/DataSourcePageReqVO.java`
- Create: `.../adapter/controller/admin/datasource/vo/DataSourceRespVO.java`
- Create: `.../adapter/service/datasource/DataSourceService.java`
- Create: `.../adapter/service/datasource/DataSourceServiceImpl.java`
- Create: `.../adapter/controller/admin/datasource/DataSourceController.java`
- Create: `.../adapter/enums/ErrorCodeConstants.java`
- Test: `yudao-module-adapter/src/test/java/cn/iocoder/yudao/module/adapter/service/datasource/DataSourceServiceImplTest.java`
- Modify: `sql/mysql/ruoyi-vue-pro.sql`、`yudao-module-adapter/src/test/resources/sql/create_tables.sql`、`.../sql/clean.sql`
- （下文 `.../adapter/...` 均指 `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/...`）

**Interfaces:**
- Consumes: Task 1 的持久化基座。
- Produces:
  - `class DataSourceDO extends BaseDO { Long id; String dsCode; String name; String supplierName; Integer sourceType; String contactPerson; String contactPhone; Integer status; Integer envType; String serviceAddr; Integer authType; Integer timeoutMs; Integer maxConcurrency; Integer retryCount; Boolean routable; Integer protocolType; String protocolExtConfig; String remark; }`
  - `interface DataSourceService { Long createDataSource(DataSourceSaveReqVO); void updateDataSource(DataSourceSaveReqVO); void deleteDataSource(Long id); DataSourceDO getDataSource(Long id); PageResult<DataSourceDO> getDataSourcePage(DataSourcePageReqVO); List<DataSourceDO> getSimpleList(); }`
  - HTTP：`/adapter/data-source/{create,update,delete,get,page,simple-list}`
  - `ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS = new ErrorCode(1_020_001_000, "数据源不存在")`、`DATA_SOURCE_CODE_DUPLICATE = new ErrorCode(1_020_001_001, "数据源编码已存在")`

- [ ] **Step 1: ErrorCodeConstants + DDL**

`.../adapter/enums/ErrorCodeConstants.java`：

```java
package cn.iocoder.yudao.module.adapter.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** adapter 模块错误码，占用 1-020-xxx-xxx 段 */
public interface ErrorCodeConstants {

    // ========== 数据源 1-020-001-xxx ==========
    ErrorCode DATA_SOURCE_NOT_EXISTS = new ErrorCode(1_020_001_000, "数据源不存在");
    ErrorCode DATA_SOURCE_CODE_DUPLICATE = new ErrorCode(1_020_001_001, "数据源编码已存在");
}
```

MySQL DDL 追加到 `sql/mysql/ruoyi-vue-pro.sql` 末尾：

```sql
CREATE TABLE `adapter_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ds_code` varchar(32) NOT NULL COMMENT '数据源编码 DS+序号',
  `name` varchar(128) NOT NULL COMMENT '数据源名称',
  `supplier_name` varchar(128) NULL DEFAULT '' COMMENT '供应商名称',
  `source_type` tinyint NOT NULL DEFAULT 1 COMMENT '类型：1供应商 2内部',
  `contact_person` varchar(64) NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(32) NULL DEFAULT '' COMMENT '联系方式',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `env_type` tinyint NOT NULL DEFAULT 1 COMMENT '环境：1生产 2测试',
  `service_addr` varchar(512) NULL DEFAULT '' COMMENT '服务地址',
  `auth_type` tinyint NOT NULL DEFAULT 0 COMMENT '认证方式',
  `timeout_ms` int NOT NULL DEFAULT 3000 COMMENT '超时毫秒',
  `max_concurrency` int NOT NULL DEFAULT 50 COMMENT '最大并发',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `routable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否参与路由',
  `protocol_type` tinyint NOT NULL DEFAULT 1 COMMENT '接入协议：1HTTP 2RPC 3DB 4FILE',
  `protocol_ext_config` varchar(2048) NULL DEFAULT NULL COMMENT '协议扩展参数JSON',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_ds_code` (`ds_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源信息表';
```

H2 DDL 追加到 `yudao-module-adapter/src/test/resources/sql/create_tables.sql`：

```sql
CREATE TABLE IF NOT EXISTS "adapter_data_source" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "ds_code" varchar(32) NOT NULL,
    "name" varchar(128) NOT NULL,
    "supplier_name" varchar(128) DEFAULT '',
    "source_type" tinyint NOT NULL DEFAULT 1,
    "contact_person" varchar(64) DEFAULT '',
    "contact_phone" varchar(32) DEFAULT '',
    "status" tinyint NOT NULL DEFAULT 0,
    "env_type" tinyint NOT NULL DEFAULT 1,
    "service_addr" varchar(512) DEFAULT '',
    "auth_type" tinyint NOT NULL DEFAULT 0,
    "timeout_ms" int NOT NULL DEFAULT 3000,
    "max_concurrency" int NOT NULL DEFAULT 50,
    "retry_count" int NOT NULL DEFAULT 0,
    "routable" bit NOT NULL DEFAULT TRUE,
    "protocol_type" tinyint NOT NULL DEFAULT 1,
    "protocol_ext_config" varchar(2048),
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '数据源信息表';
```

`.../sql/clean.sql` 追加：`DELETE FROM "adapter_data_source";`

- [ ] **Step 2: 写失败测试**

`DataSourceServiceImplTest.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DataSourceServiceImpl.class)
class DataSourceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DataSourceServiceImpl service;

    private DataSourceSaveReqVO newReq(String name) {
        DataSourceSaveReqVO vo = new DataSourceSaveReqVO();
        vo.setName(name);
        vo.setSourceType(1);
        vo.setStatus(0);
        vo.setEnvType(1);
        vo.setProtocolType(1);
        return vo;
    }

    @Test
    void create_generatesDsCode_andPersists() {
        Long id = service.createDataSource(newReq("百行征信"));
        DataSourceDO db = service.getDataSource(id);
        assertThat(db).isNotNull();
        assertThat(db.getName()).isEqualTo("百行征信");
        assertThat(db.getDsCode()).matches("DS\\d{6}");
    }

    @Test
    void update_thenGet_reflectsChange() {
        Long id = service.createDataSource(newReq("源A"));
        DataSourceSaveReqVO upd = newReq("源A改名");
        upd.setId(id);
        service.updateDataSource(upd);
        assertThat(service.getDataSource(id).getName()).isEqualTo("源A改名");
    }

    @Test
    void update_notExists_throws() {
        DataSourceSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateDataSource(upd), DATA_SOURCE_NOT_EXISTS);
    }

    @Test
    void delete_thenGet_null() {
        Long id = service.createDataSource(newReq("待删"));
        service.deleteDataSource(id);
        assertThat(service.getDataSource(id)).isNull();
    }

    @Test
    void page_filtersByName() {
        service.createDataSource(newReq("阿里数据"));
        service.createDataSource(newReq("腾讯数据"));
        DataSourcePageReqVO q = new DataSourcePageReqVO();
        q.setName("阿里");
        PageResult<DataSourceDO> page = service.getDataSourcePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getName()).isEqualTo("阿里数据");
    }

    @Test
    void create_sameName_dsCodeStillUnique() {
        Long id1 = service.createDataSource(newReq("同名"));
        Long id2 = service.createDataSource(newReq("同名"));
        assertThat(service.getDataSource(id1).getDsCode())
                .isNotEqualTo(service.getDataSource(id2).getDsCode());
    }
}
```

> 注：`assertServiceException` 静态导入路径以基座实际为准。运行前先 `grep -rn 'import static.*assertServiceException' yudao-module-infra` 确认导入（yudao 常见于 `cn.iocoder.yudao.framework.test.core.util.AssertUtils`）。

- [ ] **Step 3: 运行测试验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败（DO/VO/Service 不存在）。

- [ ] **Step 4: 最小实现**

`DataSourceDO.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_data_source")
@KeySequence("adapter_data_source_seq")
@Data
@TenantIgnore
public class DataSourceDO extends BaseDO {
    private Long id;
    private String dsCode;
    private String name;
    private String supplierName;
    private Integer sourceType;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private Integer envType;
    private String serviceAddr;
    private Integer authType;
    private Integer timeoutMs;
    private Integer maxConcurrency;
    private Integer retryCount;
    private Boolean routable;
    private Integer protocolType;
    private String protocolExtConfig;
    private String remark;
}
```

`DataSourceMapper.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSourceMapper extends BaseMapperX<DataSourceDO> {

    default PageResult<DataSourceDO> selectPage(DataSourcePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DataSourceDO>()
                .likeIfPresent(DataSourceDO::getName, reqVO.getName())
                .eqIfPresent(DataSourceDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(DataSourceDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DataSourceDO::getProtocolType, reqVO.getProtocolType())
                .orderByDesc(DataSourceDO::getId));
    }

    default DataSourceDO selectByDsCode(String dsCode) {
        return selectOne(DataSourceDO::getDsCode, dsCode);
    }

    default Long selectMaxId() {
        DataSourceDO one = selectOne(new LambdaQueryWrapperX<DataSourceDO>()
                .orderByDesc(DataSourceDO::getId).last("LIMIT 1"));
        return one == null ? 0L : one.getId();
    }
}
```

`DataSourceSaveReqVO.java`：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源创建/修改 Request VO")
@Data
public class DataSourceSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "数据源名称不能为空")
    private String name;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "类型：1供应商 2内部", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "类型不能为空")
    private Integer sourceType;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系方式")
    private String contactPhone;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "环境：1生产 2测试", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "环境不能为空")
    private Integer envType;

    @Schema(description = "服务地址")
    private String serviceAddr;

    @Schema(description = "认证方式")
    private Integer authType;

    @Schema(description = "超时毫秒")
    private Integer timeoutMs;

    @Schema(description = "最大并发")
    private Integer maxConcurrency;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "是否参与路由")
    private Boolean routable;

    @Schema(description = "接入协议：1HTTP 2RPC 3DB 4FILE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "接入协议不能为空")
    private Integer protocolType;

    @Schema(description = "协议扩展参数JSON")
    private String protocolExtConfig;

    @Schema(description = "备注")
    private String remark;
}
```

`DataSourcePageReqVO.java`：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourcePageReqVO extends PageParam {

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "类型")
    private Integer sourceType;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "接入协议")
    private Integer protocolType;
}
```

`DataSourceRespVO.java`：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源 Response VO")
@Data
public class DataSourceRespVO {
    private Long id;
    private String dsCode;
    private String name;
    private String supplierName;
    private Integer sourceType;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private Integer envType;
    private String serviceAddr;
    private Integer authType;
    private Integer timeoutMs;
    private Integer maxConcurrency;
    private Integer retryCount;
    private Boolean routable;
    private Integer protocolType;
    private String protocolExtConfig;
    private String remark;
    private LocalDateTime createTime;
}
```

`DataSourceService.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DataSourceService {
    Long createDataSource(@Valid DataSourceSaveReqVO reqVO);
    void updateDataSource(@Valid DataSourceSaveReqVO reqVO);
    void deleteDataSource(Long id);
    DataSourceDO getDataSource(Long id);
    PageResult<DataSourceDO> getDataSourcePage(DataSourcePageReqVO reqVO);
    List<DataSourceDO> getSimpleList();
}
```

`DataSourceServiceImpl.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;

@Service
@Validated
public class DataSourceServiceImpl implements DataSourceService {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    public Long createDataSource(DataSourceSaveReqVO reqVO) {
        DataSourceDO ds = BeanUtils.toBean(reqVO, DataSourceDO.class);
        ds.setId(null);
        ds.setDsCode(generateDsCode());
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    @Override
    public void updateDataSource(DataSourceSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        DataSourceDO ds = BeanUtils.toBean(reqVO, DataSourceDO.class);
        ds.setDsCode(null); // 编码不可改
        dataSourceMapper.updateById(ds);
    }

    @Override
    public void deleteDataSource(Long id) {
        validateExists(id);
        dataSourceMapper.deleteById(id);
    }

    @Override
    public DataSourceDO getDataSource(Long id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    public PageResult<DataSourceDO> getDataSourcePage(DataSourcePageReqVO reqVO) {
        return dataSourceMapper.selectPage(reqVO);
    }

    @Override
    public List<DataSourceDO> getSimpleList() {
        return dataSourceMapper.selectList(new LambdaQueryWrapperX<DataSourceDO>()
                .orderByDesc(DataSourceDO::getId));
    }

    private DataSourceDO validateExists(Long id) {
        DataSourceDO ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw exception(DATA_SOURCE_NOT_EXISTS);
        }
        return ds;
    }

    private String generateDsCode() {
        long next = dataSourceMapper.selectMaxId() + 1;
        return String.format("DS%06d", next);
    }
}
```

`DataSourceController.java`：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.service.datasource.DataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据源")
@RestController
@RequestMapping("/adapter/data-source")
@Validated
public class DataSourceController {

    @Resource
    private DataSourceService dataSourceService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:create')")
    public CommonResult<Long> create(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        return success(dataSourceService.createDataSource(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        dataSourceService.updateDataSource(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:data-source:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        dataSourceService.deleteDataSource(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<DataSourceRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dataSourceService.getDataSource(id), DataSourceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源分页")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<PageResult<DataSourceRespVO>> page(@Valid DataSourcePageReqVO reqVO) {
        PageResult<DataSourceDO> page = dataSourceService.getDataSourcePage(reqVO);
        return success(BeanUtils.toBean(page, DataSourceRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得数据源精简列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<List<DataSourceRespVO>> simpleList() {
        return success(BeanUtils.toBean(dataSourceService.getSimpleList(), DataSourceRespVO.class));
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`DataSourceServiceImplTest` 6 个用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-adapter/src sql/mysql/ruoyi-vue-pro.sql
git commit -m "feat(adapter): 数据源信息 data_source 管理 CRUD（编码自动生成+分页查询）"
```

---

### Task 3: 数据源接口 ds_interface CRUD

**Files:**
- Create: `.../adapter/dal/dataobject/datasource/DsInterfaceDO.java`
- Create: `.../adapter/dal/mysql/datasource/DsInterfaceMapper.java`
- Create: `.../adapter/controller/admin/datasource/vo/DsInterfaceSaveReqVO.java`、`DsInterfacePageReqVO.java`、`DsInterfaceRespVO.java`
- Create: `.../adapter/service/datasource/DsInterfaceService.java`、`DsInterfaceServiceImpl.java`
- Create: `.../adapter/controller/admin/datasource/DsInterfaceController.java`
- Modify: `.../adapter/enums/ErrorCodeConstants.java`
- Test: `.../adapter/service/datasource/DsInterfaceServiceImplTest.java`
- Modify: `sql/mysql/ruoyi-vue-pro.sql`、`yudao-module-adapter/src/test/resources/sql/create_tables.sql`、`clean.sql`

**Interfaces:**
- Consumes: Task 2 的 `DataSourceMapper`（校验 `dataSourceId` 存在）；`DATA_SOURCE_NOT_EXISTS`。
- Produces:
  - `class DsInterfaceDO extends BaseDO { Long id; String ifCode; String name; Long dataSourceId; String uri; String method; Integer msgFormat; Integer signType; Integer encryptType; String authParams; String version; Integer status; Integer timeoutMs; Integer retryCount; Boolean cacheEnabled; Integer cacheTtl; String cacheKey; String remark; }`
  - `interface DsInterfaceService { Long createDsInterface(DsInterfaceSaveReqVO); void updateDsInterface(DsInterfaceSaveReqVO); void deleteDsInterface(Long id); DsInterfaceDO getDsInterface(Long id); PageResult<DsInterfaceDO> getDsInterfacePage(DsInterfacePageReqVO); List<DsInterfaceDO> getListByDataSourceId(Long dataSourceId); }`
  - HTTP：`/adapter/ds-interface/{create,update,delete,get,page,list-by-data-source}`
  - `ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS = new ErrorCode(1_020_002_000, "数据源接口不存在")`

- [ ] **Step 1: ErrorCode + DDL**

`ErrorCodeConstants` 追加：

```java
    // ========== 数据源接口 1-020-002-xxx ==========
    ErrorCode DS_INTERFACE_NOT_EXISTS = new ErrorCode(1_020_002_000, "数据源接口不存在");
```

MySQL DDL（追加到 `sql/mysql/ruoyi-vue-pro.sql`）：

```sql
CREATE TABLE `adapter_ds_interface` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `if_code` varchar(32) NOT NULL COMMENT '接口编码 IF+序号',
  `name` varchar(128) NOT NULL COMMENT '接口名称',
  `data_source_id` bigint NOT NULL COMMENT '所属数据源ID',
  `uri` varchar(512) NULL DEFAULT '' COMMENT '接口URI',
  `method` varchar(8) NOT NULL DEFAULT 'POST' COMMENT '请求方式',
  `msg_format` tinyint NOT NULL DEFAULT 1 COMMENT '报文格式：1JSON 2XML 3FORM',
  `sign_type` tinyint NOT NULL DEFAULT 0 COMMENT '签名方式',
  `encrypt_type` tinyint NOT NULL DEFAULT 0 COMMENT '加密方式',
  `auth_params` varchar(2048) NULL DEFAULT NULL COMMENT '认证参数JSON',
  `version` varchar(16) NOT NULL DEFAULT 'v1' COMMENT '接口版本',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `timeout_ms` int NOT NULL DEFAULT 3000 COMMENT '超时毫秒',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `cache_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否缓存',
  `cache_ttl` int NOT NULL DEFAULT 0 COMMENT '缓存TTL秒',
  `cache_key` varchar(256) NULL DEFAULT '' COMMENT '缓存键模板',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_if_code` (`if_code`),
  KEY `idx_data_source_id` (`data_source_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源接口表';
```

H2 DDL（追加到 `create_tables.sql`）：

```sql
CREATE TABLE IF NOT EXISTS "adapter_ds_interface" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "if_code" varchar(32) NOT NULL,
    "name" varchar(128) NOT NULL,
    "data_source_id" bigint NOT NULL,
    "uri" varchar(512) DEFAULT '',
    "method" varchar(8) NOT NULL DEFAULT 'POST',
    "msg_format" tinyint NOT NULL DEFAULT 1,
    "sign_type" tinyint NOT NULL DEFAULT 0,
    "encrypt_type" tinyint NOT NULL DEFAULT 0,
    "auth_params" varchar(2048),
    "version" varchar(16) NOT NULL DEFAULT 'v1',
    "status" tinyint NOT NULL DEFAULT 0,
    "timeout_ms" int NOT NULL DEFAULT 3000,
    "retry_count" int NOT NULL DEFAULT 0,
    "cache_enabled" bit NOT NULL DEFAULT FALSE,
    "cache_ttl" int NOT NULL DEFAULT 0,
    "cache_key" varchar(256) DEFAULT '',
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '数据源接口表';
```

`clean.sql` 追加：`DELETE FROM "adapter_ds_interface";`

- [ ] **Step 2: 写失败测试**

`DsInterfaceServiceImplTest.java`（因 Impl 校验 `dataSourceId` 存在，测试先经 `DataSourceMapper` 造一条真实数据源）：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsInterfaceServiceImpl.class)
class DsInterfaceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsInterfaceServiceImpl service;
    @Resource
    private DataSourceMapper dataSourceMapper;

    private int dsSeq = 0;

    private Long insertDataSource() {
        DataSourceDO ds = new DataSourceDO();
        ds.setDsCode(String.format("DS%06d", ++dsSeq));
        ds.setName("测试源");
        ds.setSourceType(1);
        ds.setStatus(0);
        ds.setEnvType(1);
        ds.setProtocolType(1);
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    private DsInterfaceSaveReqVO newReq(String name, Long dsId) {
        DsInterfaceSaveReqVO vo = new DsInterfaceSaveReqVO();
        vo.setName(name);
        vo.setDataSourceId(dsId);
        vo.setMethod("POST");
        vo.setMsgFormat(1);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesIfCode() {
        Long dsId = insertDataSource();
        Long id = service.createDsInterface(newReq("企业工商查询", dsId));
        DsInterfaceDO db = service.getDsInterface(id);
        assertThat(db.getIfCode()).matches("IF\\d{6}");
        assertThat(db.getDataSourceId()).isEqualTo(dsId);
    }

    @Test
    void update_interfaceNotExists_throws() {
        Long dsId = insertDataSource();
        DsInterfaceSaveReqVO upd = newReq("x", dsId);
        upd.setId(88888L);
        assertServiceException(() -> service.updateDsInterface(upd), DS_INTERFACE_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long dsId = insertDataSource();
        Long id = service.createDsInterface(newReq("待删", dsId));
        service.deleteDsInterface(id);
        assertThat(service.getDsInterface(id)).isNull();
    }

    @Test
    void listByDataSourceId_filters() {
        Long ds1 = insertDataSource();
        Long ds2 = insertDataSource();
        service.createDsInterface(newReq("A", ds1));
        service.createDsInterface(newReq("B", ds1));
        service.createDsInterface(newReq("C", ds2));
        List<DsInterfaceDO> list = service.getListByDataSourceId(ds1);
        assertThat(list).hasSize(2);
    }

    @Test
    void page_filtersByName() {
        Long dsId = insertDataSource();
        service.createDsInterface(newReq("工商查询", dsId));
        service.createDsInterface(newReq("司法查询", dsId));
        DsInterfacePageReqVO q = new DsInterfacePageReqVO();
        q.setName("工商");
        PageResult<DsInterfaceDO> page = service.getDsInterfacePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 4: 最小实现**

`DsInterfaceDO.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_interface")
@KeySequence("adapter_ds_interface_seq")
@Data
@TenantIgnore
public class DsInterfaceDO extends BaseDO {
    private Long id;
    private String ifCode;
    private String name;
    private Long dataSourceId;
    private String uri;
    private String method;
    private Integer msgFormat;
    private Integer signType;
    private Integer encryptType;
    private String authParams;
    private String version;
    private Integer status;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean cacheEnabled;
    private Integer cacheTtl;
    private String cacheKey;
    private String remark;
}
```

`DsInterfaceMapper.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsInterfaceMapper extends BaseMapperX<DsInterfaceDO> {

    default PageResult<DsInterfaceDO> selectPage(DsInterfacePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsInterfaceDO>()
                .likeIfPresent(DsInterfaceDO::getName, reqVO.getName())
                .eqIfPresent(DsInterfaceDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsInterfaceDO::getStatus, reqVO.getStatus())
                .orderByDesc(DsInterfaceDO::getId));
    }

    default List<DsInterfaceDO> selectListByDataSourceId(Long dataSourceId) {
        return selectList(DsInterfaceDO::getDataSourceId, dataSourceId);
    }

    default Long selectMaxId() {
        DsInterfaceDO one = selectOne(new LambdaQueryWrapperX<DsInterfaceDO>()
                .orderByDesc(DsInterfaceDO::getId).last("LIMIT 1"));
        return one == null ? 0L : one.getId();
    }
}
```

VO 三件——`DsInterfaceSaveReqVO`（字段：`id`；`name` `@NotEmpty`；`dataSourceId` `@NotNull`；`uri`；`method` `@NotEmpty`；`msgFormat` `@NotNull`；`signType`；`encryptType`；`authParams`；`version`；`status` `@NotNull`；`timeoutMs`；`retryCount`；`cacheEnabled`；`cacheTtl`；`cacheKey`；`remark`）、`DsInterfacePageReqVO extends PageParam`（`@EqualsAndHashCode(callSuper=true)`；字段 `name`/`dataSourceId`/`status`）、`DsInterfaceRespVO`（镜像 DO 全字段 + `LocalDateTime createTime`）。三者的类注解与写法与 Task 2 的三个 VO 完全一致，仅字段替换：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源接口创建/修改 Request VO")
@Data
public class DsInterfaceSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "接口名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "接口名称不能为空")
    private String name;

    @Schema(description = "所属数据源ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属数据源不能为空")
    private Long dataSourceId;

    @Schema(description = "接口URI")
    private String uri;

    @Schema(description = "请求方式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请求方式不能为空")
    private String method;

    @Schema(description = "报文格式：1JSON 2XML 3FORM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "报文格式不能为空")
    private Integer msgFormat;

    @Schema(description = "签名方式")
    private Integer signType;

    @Schema(description = "加密方式")
    private Integer encryptType;

    @Schema(description = "认证参数JSON")
    private String authParams;

    @Schema(description = "接口版本")
    private String version;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "超时毫秒")
    private Integer timeoutMs;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "是否缓存")
    private Boolean cacheEnabled;

    @Schema(description = "缓存TTL秒")
    private Integer cacheTtl;

    @Schema(description = "缓存键模板")
    private String cacheKey;

    @Schema(description = "备注")
    private String remark;
}
```

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源接口分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DsInterfacePageReqVO extends PageParam {

    @Schema(description = "接口名称")
    private String name;

    @Schema(description = "所属数据源ID")
    private Long dataSourceId;

    @Schema(description = "状态")
    private Integer status;
}
```

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源接口 Response VO")
@Data
public class DsInterfaceRespVO {
    private Long id;
    private String ifCode;
    private String name;
    private Long dataSourceId;
    private String uri;
    private String method;
    private Integer msgFormat;
    private Integer signType;
    private Integer encryptType;
    private String authParams;
    private String version;
    private Integer status;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean cacheEnabled;
    private Integer cacheTtl;
    private String cacheKey;
    private String remark;
    private LocalDateTime createTime;
}
```

`DsInterfaceService.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsInterfaceService {
    Long createDsInterface(@Valid DsInterfaceSaveReqVO reqVO);
    void updateDsInterface(@Valid DsInterfaceSaveReqVO reqVO);
    void deleteDsInterface(Long id);
    DsInterfaceDO getDsInterface(Long id);
    PageResult<DsInterfaceDO> getDsInterfacePage(DsInterfacePageReqVO reqVO);
    List<DsInterfaceDO> getListByDataSourceId(Long dataSourceId);
}
```

`DsInterfaceServiceImpl.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;

@Service
@Validated
public class DsInterfaceServiceImpl implements DsInterfaceService {

    @Resource
    private DsInterfaceMapper dsInterfaceMapper;
    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    public Long createDsInterface(DsInterfaceSaveReqVO reqVO) {
        validateDataSourceExists(reqVO.getDataSourceId());
        DsInterfaceDO dif = BeanUtils.toBean(reqVO, DsInterfaceDO.class);
        dif.setId(null);
        dif.setIfCode(generateIfCode());
        dsInterfaceMapper.insert(dif);
        return dif.getId();
    }

    @Override
    public void updateDsInterface(DsInterfaceSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateDataSourceExists(reqVO.getDataSourceId());
        DsInterfaceDO dif = BeanUtils.toBean(reqVO, DsInterfaceDO.class);
        dif.setIfCode(null);
        dsInterfaceMapper.updateById(dif);
    }

    @Override
    public void deleteDsInterface(Long id) {
        validateExists(id);
        dsInterfaceMapper.deleteById(id);
    }

    @Override
    public DsInterfaceDO getDsInterface(Long id) {
        return dsInterfaceMapper.selectById(id);
    }

    @Override
    public PageResult<DsInterfaceDO> getDsInterfacePage(DsInterfacePageReqVO reqVO) {
        return dsInterfaceMapper.selectPage(reqVO);
    }

    @Override
    public List<DsInterfaceDO> getListByDataSourceId(Long dataSourceId) {
        return dsInterfaceMapper.selectListByDataSourceId(dataSourceId);
    }

    private DsInterfaceDO validateExists(Long id) {
        DsInterfaceDO dif = dsInterfaceMapper.selectById(id);
        if (dif == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
        return dif;
    }

    private void validateDataSourceExists(Long dataSourceId) {
        if (dataSourceMapper.selectById(dataSourceId) == null) {
            throw exception(DATA_SOURCE_NOT_EXISTS);
        }
    }

    private String generateIfCode() {
        long next = dsInterfaceMapper.selectMaxId() + 1;
        return String.format("IF%06d", next);
    }
}
```

`DsInterfaceController.java`：五端点写法与 Task 2 的 `DataSourceController` 完全一致（替换类型/服务/RespVO），`@RequestMapping("/adapter/ds-interface")`，权限串 `adapter:ds-interface:{create|update|delete|query}`；额外一个：

```java
    @GetMapping("/list-by-data-source")
    @Operation(summary = "按数据源获得接口列表")
    @Parameter(name = "dataSourceId", description = "数据源ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:query')")
    public CommonResult<List<DsInterfaceRespVO>> listByDataSource(@RequestParam("dataSourceId") Long dataSourceId) {
        return success(BeanUtils.toBean(dsInterfaceService.getListByDataSourceId(dataSourceId), DsInterfaceRespVO.class));
    }
```

- [ ] **Step 5: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`DsInterfaceServiceImplTest` 全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-adapter/src sql/mysql/ruoyi-vue-pro.sql
git commit -m "feat(adapter): 数据源接口 ds_interface 管理 CRUD（归属数据源校验+编码生成）"
```

---

### Task 4: 数据源应答码 ds_response_code CRUD（四标记 + 未映射码查询）

**Files:**
- Create: `.../adapter/dal/dataobject/datasource/DsResponseCodeDO.java`
- Create: `.../adapter/dal/mysql/datasource/DsResponseCodeMapper.java`
- Create: `.../adapter/controller/admin/datasource/vo/DsResponseCodeSaveReqVO.java`、`DsResponseCodePageReqVO.java`、`DsResponseCodeRespVO.java`
- Create: `.../adapter/service/datasource/DsResponseCodeService.java`、`DsResponseCodeServiceImpl.java`
- Create: `.../adapter/controller/admin/datasource/DsResponseCodeController.java`
- Modify: `.../adapter/enums/ErrorCodeConstants.java`
- Test: `.../adapter/service/datasource/DsResponseCodeServiceImplTest.java`
- Modify: `sql/mysql/ruoyi-vue-pro.sql`、`create_tables.sql`、`clean.sql`

**Interfaces:**
- Consumes: Task 2/3。
- Produces:
  - `class DsResponseCodeDO extends BaseDO { Long id; Long dataSourceId; Long dsInterfaceId; String rawCode; String rawDesc; Boolean success; Boolean charge; Boolean retryable; Boolean triggerSwitch; String platformCode; }`（`dsInterfaceId` 默认 0 表示数据源级通用应答码）
  - `interface DsResponseCodeService { Long createDsResponseCode(DsResponseCodeSaveReqVO); void updateDsResponseCode(DsResponseCodeSaveReqVO); void deleteDsResponseCode(Long id); DsResponseCodeDO getDsResponseCode(Long id); PageResult<DsResponseCodeDO> getDsResponseCodePage(DsResponseCodePageReqVO); List<DsResponseCodeDO> getUnmappedList(Long dataSourceId); }`
  - HTTP：`/adapter/ds-response-code/{create,update,delete,get,page,unmapped-list}`
  - `ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS = new ErrorCode(1_020_003_000, "数据源应答码不存在")`、`DS_RESPONSE_CODE_DUPLICATE = new ErrorCode(1_020_003_001, "同接口下原始应答码已存在")`

- [ ] **Step 1: ErrorCode + DDL**

`ErrorCodeConstants` 追加：

```java
    // ========== 数据源应答码 1-020-003-xxx ==========
    ErrorCode DS_RESPONSE_CODE_NOT_EXISTS = new ErrorCode(1_020_003_000, "数据源应答码不存在");
    ErrorCode DS_RESPONSE_CODE_DUPLICATE = new ErrorCode(1_020_003_001, "同接口下原始应答码已存在");
```

MySQL DDL：

```sql
CREATE TABLE `adapter_ds_response_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `data_source_id` bigint NOT NULL COMMENT '所属数据源ID',
  `ds_interface_id` bigint NOT NULL DEFAULT 0 COMMENT '所属接口ID，0为数据源级通用',
  `raw_code` varchar(64) NOT NULL COMMENT '原始应答码',
  `raw_desc` varchar(256) NULL DEFAULT '' COMMENT '应答描述',
  `success` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否成功',
  `charge` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否计费',
  `retryable` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否可重试',
  `trigger_switch` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否触发切换',
  `platform_code` varchar(8) NULL DEFAULT '' COMMENT '映射平台统一码',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_ds_if` (`data_source_id`, `ds_interface_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源应答码映射表';
```

H2 DDL：

```sql
CREATE TABLE IF NOT EXISTS "adapter_ds_response_code" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "data_source_id" bigint NOT NULL,
    "ds_interface_id" bigint NOT NULL DEFAULT 0,
    "raw_code" varchar(64) NOT NULL,
    "raw_desc" varchar(256) DEFAULT '',
    "success" bit NOT NULL DEFAULT FALSE,
    "charge" bit NOT NULL DEFAULT FALSE,
    "retryable" bit NOT NULL DEFAULT FALSE,
    "trigger_switch" bit NOT NULL DEFAULT FALSE,
    "platform_code" varchar(8) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '数据源应答码映射表';
```

`clean.sql` 追加：`DELETE FROM "adapter_ds_response_code";`

- [ ] **Step 2: 写失败测试**

`DsResponseCodeServiceImplTest.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsResponseCodeServiceImpl service;

    private DsResponseCodeSaveReqVO newReq(String rawCode, String platformCode) {
        DsResponseCodeSaveReqVO vo = new DsResponseCodeSaveReqVO();
        vo.setDataSourceId(1L);
        vo.setDsInterfaceId(0L);
        vo.setRawCode(rawCode);
        vo.setSuccess("0000".equals(platformCode));
        vo.setCharge(false);
        vo.setRetryable(false);
        vo.setTriggerSwitch(false);
        vo.setPlatformCode(platformCode);
        return vo;
    }

    @Test
    void create_persistsFourFlags() {
        DsResponseCodeSaveReqVO vo = newReq("A00", "0000");
        vo.setCharge(true);
        vo.setTriggerSwitch(true);
        Long id = service.createDsResponseCode(vo);
        DsResponseCodeDO db = service.getDsResponseCode(id);
        assertThat(db.getSuccess()).isTrue();
        assertThat(db.getCharge()).isTrue();
        assertThat(db.getRetryable()).isFalse();
        assertThat(db.getTriggerSwitch()).isTrue();
        assertThat(db.getPlatformCode()).isEqualTo("0000");
    }

    @Test
    void create_duplicateRawCodeSameScope_throws() {
        service.createDsResponseCode(newReq("DUP", "0000"));
        assertServiceException(() -> service.createDsResponseCode(newReq("DUP", "0001")),
                DS_RESPONSE_CODE_DUPLICATE);
    }

    @Test
    void update_notExists_throws() {
        DsResponseCodeSaveReqVO upd = newReq("X", "0000");
        upd.setId(77777L);
        assertServiceException(() -> service.updateDsResponseCode(upd), DS_RESPONSE_CODE_NOT_EXISTS);
    }

    @Test
    void unmappedList_returnsOnlyBlankPlatformCode() {
        service.createDsResponseCode(newReq("MAPPED", "3001"));
        service.createDsResponseCode(newReq("UNMAPPED1", ""));
        service.createDsResponseCode(newReq("UNMAPPED2", null));
        List<DsResponseCodeDO> unmapped = service.getUnmappedList(1L);
        assertThat(unmapped).extracting(DsResponseCodeDO::getRawCode)
                .containsExactlyInAnyOrder("UNMAPPED1", "UNMAPPED2");
    }

    @Test
    void page_filtersByRawCode() {
        service.createDsResponseCode(newReq("ERR_TIMEOUT", "3001"));
        service.createDsResponseCode(newReq("OK", "0000"));
        DsResponseCodePageReqVO q = new DsResponseCodePageReqVO();
        q.setRawCode("ERR");
        PageResult<DsResponseCodeDO> page = service.getDsResponseCodePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 4: 最小实现**

`DsResponseCodeDO.java`（`@TableName("adapter_ds_response_code")` + `@KeySequence("adapter_ds_response_code_seq")` + `@Data` + `@TenantIgnore`；字段见 Interfaces，Java 字段 `success/charge/retryable/triggerSwitch` 映射列 `success/charge/retryable/trigger_switch`，均 `Boolean`）：

```java
package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_response_code")
@KeySequence("adapter_ds_response_code_seq")
@Data
@TenantIgnore
public class DsResponseCodeDO extends BaseDO {
    private Long id;
    private Long dataSourceId;
    private Long dsInterfaceId;
    private String rawCode;
    private String rawDesc;
    private Boolean success;
    private Boolean charge;
    private Boolean retryable;
    private Boolean triggerSwitch;
    private String platformCode;
}
```

`DsResponseCodeMapper.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsResponseCodeMapper extends BaseMapperX<DsResponseCodeDO> {

    default PageResult<DsResponseCodeDO> selectPage(DsResponseCodePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eqIfPresent(DsResponseCodeDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsResponseCodeDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .likeIfPresent(DsResponseCodeDO::getRawCode, reqVO.getRawCode())
                .eqIfPresent(DsResponseCodeDO::getPlatformCode, reqVO.getPlatformCode())
                .orderByDesc(DsResponseCodeDO::getId));
    }

    default DsResponseCodeDO selectByScopeAndRawCode(Long dataSourceId, Long dsInterfaceId, String rawCode) {
        return selectOne(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eq(DsResponseCodeDO::getDataSourceId, dataSourceId)
                .eq(DsResponseCodeDO::getDsInterfaceId, dsInterfaceId)
                .eq(DsResponseCodeDO::getRawCode, rawCode));
    }

    default List<DsResponseCodeDO> selectUnmapped(Long dataSourceId) {
        return selectList(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eq(DsResponseCodeDO::getDataSourceId, dataSourceId)
                .and(w -> w.isNull(DsResponseCodeDO::getPlatformCode)
                        .or().eq(DsResponseCodeDO::getPlatformCode, ""))
                .orderByDesc(DsResponseCodeDO::getId));
    }
}
```

VO 三件：`DsResponseCodeSaveReqVO`（`id`；`dataSourceId` `@NotNull`；`dsInterfaceId`；`rawCode` `@NotEmpty`；`rawDesc`；`success` `@NotNull`；`charge` `@NotNull`；`retryable` `@NotNull`；`triggerSwitch` `@NotNull`；`platformCode`）；`DsResponseCodePageReqVO extends PageParam`（`dataSourceId`/`dsInterfaceId`/`rawCode`/`platformCode`）；`DsResponseCodeRespVO`（镜像 DO + `createTime`）。写法同前。

`DsResponseCodeService.java` / `DsResponseCodeServiceImpl.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsResponseCodeService {
    Long createDsResponseCode(@Valid DsResponseCodeSaveReqVO reqVO);
    void updateDsResponseCode(@Valid DsResponseCodeSaveReqVO reqVO);
    void deleteDsResponseCode(Long id);
    DsResponseCodeDO getDsResponseCode(Long id);
    PageResult<DsResponseCodeDO> getDsResponseCodePage(DsResponseCodePageReqVO reqVO);
    List<DsResponseCodeDO> getUnmappedList(Long dataSourceId);
}
```

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS;

@Service
@Validated
public class DsResponseCodeServiceImpl implements DsResponseCodeService {

    @Resource
    private DsResponseCodeMapper dsResponseCodeMapper;

    @Override
    public Long createDsResponseCode(DsResponseCodeSaveReqVO reqVO) {
        normalizeInterfaceId(reqVO);
        validateDuplicate(reqVO.getDataSourceId(), reqVO.getDsInterfaceId(), reqVO.getRawCode(), null);
        DsResponseCodeDO code = BeanUtils.toBean(reqVO, DsResponseCodeDO.class);
        code.setId(null);
        dsResponseCodeMapper.insert(code);
        return code.getId();
    }

    @Override
    public void updateDsResponseCode(DsResponseCodeSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        normalizeInterfaceId(reqVO);
        validateDuplicate(reqVO.getDataSourceId(), reqVO.getDsInterfaceId(), reqVO.getRawCode(), reqVO.getId());
        dsResponseCodeMapper.updateById(BeanUtils.toBean(reqVO, DsResponseCodeDO.class));
    }

    @Override
    public void deleteDsResponseCode(Long id) {
        validateExists(id);
        dsResponseCodeMapper.deleteById(id);
    }

    @Override
    public DsResponseCodeDO getDsResponseCode(Long id) {
        return dsResponseCodeMapper.selectById(id);
    }

    @Override
    public PageResult<DsResponseCodeDO> getDsResponseCodePage(DsResponseCodePageReqVO reqVO) {
        return dsResponseCodeMapper.selectPage(reqVO);
    }

    @Override
    public List<DsResponseCodeDO> getUnmappedList(Long dataSourceId) {
        return dsResponseCodeMapper.selectUnmapped(dataSourceId);
    }

    private DsResponseCodeDO validateExists(Long id) {
        DsResponseCodeDO code = dsResponseCodeMapper.selectById(id);
        if (code == null) {
            throw exception(DS_RESPONSE_CODE_NOT_EXISTS);
        }
        return code;
    }

    private void normalizeInterfaceId(DsResponseCodeSaveReqVO reqVO) {
        if (reqVO.getDsInterfaceId() == null) {
            reqVO.setDsInterfaceId(0L);
        }
    }

    private void validateDuplicate(Long dsId, Long ifId, String rawCode, Long selfId) {
        DsResponseCodeDO exist = dsResponseCodeMapper.selectByScopeAndRawCode(dsId, ifId, rawCode);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw exception(DS_RESPONSE_CODE_DUPLICATE);
        }
    }
}
```

`DsResponseCodeController.java`：五端点（写法同 Task 2）+ `unmapped-list`：

```java
    @GetMapping("/unmapped-list")
    @Operation(summary = "获得未映射平台码的原始应答码列表")
    @Parameter(name = "dataSourceId", description = "数据源ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:query')")
    public CommonResult<List<DsResponseCodeRespVO>> unmappedList(@RequestParam("dataSourceId") Long dataSourceId) {
        return success(BeanUtils.toBean(dsResponseCodeService.getUnmappedList(dataSourceId), DsResponseCodeRespVO.class));
    }
```

`@RequestMapping("/adapter/ds-response-code")`，权限串 `adapter:ds-response-code:*`。

> 批量导入/导出（Excel，§6.2.3）留待 Task 6 统一处理，便于独立评审。

- [ ] **Step 5: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`DsResponseCodeServiceImplTest` 全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-adapter/src sql/mysql/ruoyi-vue-pro.sql
git commit -m "feat(adapter): 数据源应答码 ds_response_code CRUD（四标记+查重+未映射码查询）"
```

---

### Task 5: 数据源接口参数映射 ds_interface_param CRUD

**Files:**
- Create: `.../adapter/dal/dataobject/datasource/DsInterfaceParamDO.java`
- Create: `.../adapter/dal/mysql/datasource/DsInterfaceParamMapper.java`
- Create: `.../adapter/controller/admin/datasource/vo/DsInterfaceParamSaveReqVO.java`、`DsInterfaceParamPageReqVO.java`、`DsInterfaceParamRespVO.java`
- Create: `.../adapter/service/datasource/DsInterfaceParamService.java`、`DsInterfaceParamServiceImpl.java`
- Create: `.../adapter/controller/admin/datasource/DsInterfaceParamController.java`
- Modify: `.../adapter/enums/ErrorCodeConstants.java`
- Test: `.../adapter/service/datasource/DsInterfaceParamServiceImplTest.java`
- Modify: `sql/mysql/ruoyi-vue-pro.sql`、`create_tables.sql`、`clean.sql`

**Interfaces:**
- Consumes: Task 3（`DsInterfaceMapper` 校验接口存在）；`DS_INTERFACE_NOT_EXISTS`。
- Produces:
  - `class DsInterfaceParamDO extends BaseDO { Long id; Long dsInterfaceId; Integer paramDirection; String platformField; String providerField; Integer dataType; Boolean required; String transformFn; String defaultValue; String jsonPath; String remark; }`（`paramDirection`：1入参 2出参；`jsonPath` 供出参抽取；`transformFn` 供入参转换函数名，如 MD5/AES/DATE_FMT）
  - `interface DsInterfaceParamService { Long createDsInterfaceParam(DsInterfaceParamSaveReqVO); void updateDsInterfaceParam(DsInterfaceParamSaveReqVO); void deleteDsInterfaceParam(Long id); DsInterfaceParamDO getDsInterfaceParam(Long id); PageResult<DsInterfaceParamDO> getDsInterfaceParamPage(DsInterfaceParamPageReqVO); List<DsInterfaceParamDO> getListByInterface(Long dsInterfaceId, Integer paramDirection); }`
  - HTTP：`/adapter/ds-interface-param/{create,update,delete,get,page,list-by-interface}`
  - `ErrorCodeConstants.DS_INTERFACE_PARAM_NOT_EXISTS = new ErrorCode(1_020_004_000, "数据源接口参数不存在")`

- [ ] **Step 1: ErrorCode + DDL**

`ErrorCodeConstants` 追加：

```java
    // ========== 数据源接口参数 1-020-004-xxx ==========
    ErrorCode DS_INTERFACE_PARAM_NOT_EXISTS = new ErrorCode(1_020_004_000, "数据源接口参数不存在");
```

MySQL DDL：

```sql
CREATE TABLE `adapter_ds_interface_param` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `ds_interface_id` bigint NOT NULL COMMENT '所属接口ID',
  `param_direction` tinyint NOT NULL COMMENT '方向：1入参 2出参',
  `platform_field` varchar(128) NOT NULL COMMENT '平台标准字段',
  `provider_field` varchar(128) NULL DEFAULT '' COMMENT '供应商字段',
  `data_type` tinyint NOT NULL DEFAULT 1 COMMENT '数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
  `transform_fn` varchar(64) NULL DEFAULT '' COMMENT '转换函数名',
  `default_value` varchar(256) NULL DEFAULT '' COMMENT '默认值',
  `json_path` varchar(256) NULL DEFAULT '' COMMENT '出参JSONPath抽取路径',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_if_direction` (`ds_interface_id`, `param_direction`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源接口参数映射表';
```

H2 DDL：

```sql
CREATE TABLE IF NOT EXISTS "adapter_ds_interface_param" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "ds_interface_id" bigint NOT NULL,
    "param_direction" tinyint NOT NULL,
    "platform_field" varchar(128) NOT NULL,
    "provider_field" varchar(128) DEFAULT '',
    "data_type" tinyint NOT NULL DEFAULT 1,
    "required" bit NOT NULL DEFAULT FALSE,
    "transform_fn" varchar(64) DEFAULT '',
    "default_value" varchar(256) DEFAULT '',
    "json_path" varchar(256) DEFAULT '',
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '数据源接口参数映射表';
```

`clean.sql` 追加：`DELETE FROM "adapter_ds_interface_param";`

- [ ] **Step 2: 写失败测试**

`DsInterfaceParamServiceImplTest.java`（先经 `DsInterfaceMapper` 造真实接口）：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_PARAM_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsInterfaceParamServiceImpl.class)
class DsInterfaceParamServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsInterfaceParamServiceImpl service;
    @Resource
    private DsInterfaceMapper dsInterfaceMapper;

    private int ifSeq = 0;

    private Long insertInterface() {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode(String.format("IF%06d", ++ifSeq));
        dif.setName("接口");
        dif.setDataSourceId(1L);
        dif.setMethod("POST");
        dif.setMsgFormat(1);
        dif.setStatus(0);
        dif.setVersion("v1");
        dsInterfaceMapper.insert(dif);
        return dif.getId();
    }

    private DsInterfaceParamSaveReqVO newReq(Long ifId, int direction, String platformField) {
        DsInterfaceParamSaveReqVO vo = new DsInterfaceParamSaveReqVO();
        vo.setDsInterfaceId(ifId);
        vo.setParamDirection(direction);
        vo.setPlatformField(platformField);
        vo.setDataType(1);
        vo.setRequired(true);
        return vo;
    }

    @Test
    void create_inParam_persists() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO vo = newReq(ifId, 1, "idNo");
        vo.setProviderField("cert_no");
        vo.setTransformFn("MD5");
        Long id = service.createDsInterfaceParam(vo);
        DsInterfaceParamDO db = service.getDsInterfaceParam(id);
        assertThat(db.getParamDirection()).isEqualTo(1);
        assertThat(db.getProviderField()).isEqualTo("cert_no");
        assertThat(db.getTransformFn()).isEqualTo("MD5");
    }

    @Test
    void create_outParam_withJsonPath() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO vo = newReq(ifId, 2, "companyName");
        vo.setJsonPath("$.data.entName");
        Long id = service.createDsInterfaceParam(vo);
        assertThat(service.getDsInterfaceParam(id).getJsonPath()).isEqualTo("$.data.entName");
    }

    @Test
    void update_paramNotExists_throws() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO upd = newReq(ifId, 1, "x");
        upd.setId(66666L);
        assertServiceException(() -> service.updateDsInterfaceParam(upd), DS_INTERFACE_PARAM_NOT_EXISTS);
    }

    @Test
    void listByInterface_filtersByDirection() {
        Long ifId = insertInterface();
        service.createDsInterfaceParam(newReq(ifId, 1, "in1"));
        service.createDsInterfaceParam(newReq(ifId, 1, "in2"));
        service.createDsInterfaceParam(newReq(ifId, 2, "out1"));
        List<DsInterfaceParamDO> inParams = service.getListByInterface(ifId, 1);
        assertThat(inParams).hasSize(2);
        List<DsInterfaceParamDO> outParams = service.getListByInterface(ifId, 2);
        assertThat(outParams).hasSize(1);
    }

    @Test
    void page_filtersByInterfaceAndDirection() {
        Long ifId = insertInterface();
        service.createDsInterfaceParam(newReq(ifId, 1, "field1"));
        DsInterfaceParamPageReqVO q = new DsInterfaceParamPageReqVO();
        q.setDsInterfaceId(ifId);
        q.setParamDirection(1);
        PageResult<DsInterfaceParamDO> page = service.getDsInterfaceParamPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 4: 最小实现**

`DsInterfaceParamDO.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.dataobject.datasource;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("adapter_ds_interface_param")
@KeySequence("adapter_ds_interface_param_seq")
@Data
@TenantIgnore
public class DsInterfaceParamDO extends BaseDO {
    private Long id;
    private Long dsInterfaceId;
    private Integer paramDirection;
    private String platformField;
    private String providerField;
    private Integer dataType;
    private Boolean required;
    private String transformFn;
    private String defaultValue;
    private String jsonPath;
    private String remark;
}
```

`DsInterfaceParamMapper.java`：

```java
package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsInterfaceParamMapper extends BaseMapperX<DsInterfaceParamDO> {

    default PageResult<DsInterfaceParamDO> selectPage(DsInterfaceParamPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsInterfaceParamDO>()
                .eqIfPresent(DsInterfaceParamDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .eqIfPresent(DsInterfaceParamDO::getParamDirection, reqVO.getParamDirection())
                .likeIfPresent(DsInterfaceParamDO::getPlatformField, reqVO.getPlatformField())
                .orderByDesc(DsInterfaceParamDO::getId));
    }

    default List<DsInterfaceParamDO> selectListByInterface(Long dsInterfaceId, Integer paramDirection) {
        return selectList(new LambdaQueryWrapperX<DsInterfaceParamDO>()
                .eq(DsInterfaceParamDO::getDsInterfaceId, dsInterfaceId)
                .eqIfPresent(DsInterfaceParamDO::getParamDirection, paramDirection)
                .orderByAsc(DsInterfaceParamDO::getId));
    }
}
```

VO 三件：`DsInterfaceParamSaveReqVO`（`id`；`dsInterfaceId` `@NotNull`；`paramDirection` `@NotNull`；`platformField` `@NotEmpty`；`providerField`；`dataType` `@NotNull`；`required`；`transformFn`；`defaultValue`；`jsonPath`；`remark`）；`DsInterfaceParamPageReqVO extends PageParam`（`dsInterfaceId`/`paramDirection`/`platformField`）；`DsInterfaceParamRespVO`（镜像 DO + `createTime`）。写法同前。

`DsInterfaceParamService.java` / `DsInterfaceParamServiceImpl.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsInterfaceParamService {
    Long createDsInterfaceParam(@Valid DsInterfaceParamSaveReqVO reqVO);
    void updateDsInterfaceParam(@Valid DsInterfaceParamSaveReqVO reqVO);
    void deleteDsInterfaceParam(Long id);
    DsInterfaceParamDO getDsInterfaceParam(Long id);
    PageResult<DsInterfaceParamDO> getDsInterfaceParamPage(DsInterfaceParamPageReqVO reqVO);
    List<DsInterfaceParamDO> getListByInterface(Long dsInterfaceId, Integer paramDirection);
}
```

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_PARAM_NOT_EXISTS;

@Service
@Validated
public class DsInterfaceParamServiceImpl implements DsInterfaceParamService {

    @Resource
    private DsInterfaceParamMapper dsInterfaceParamMapper;
    @Resource
    private DsInterfaceMapper dsInterfaceMapper;

    @Override
    public Long createDsInterfaceParam(DsInterfaceParamSaveReqVO reqVO) {
        validateInterfaceExists(reqVO.getDsInterfaceId());
        DsInterfaceParamDO param = BeanUtils.toBean(reqVO, DsInterfaceParamDO.class);
        param.setId(null);
        dsInterfaceParamMapper.insert(param);
        return param.getId();
    }

    @Override
    public void updateDsInterfaceParam(DsInterfaceParamSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateInterfaceExists(reqVO.getDsInterfaceId());
        dsInterfaceParamMapper.updateById(BeanUtils.toBean(reqVO, DsInterfaceParamDO.class));
    }

    @Override
    public void deleteDsInterfaceParam(Long id) {
        validateExists(id);
        dsInterfaceParamMapper.deleteById(id);
    }

    @Override
    public DsInterfaceParamDO getDsInterfaceParam(Long id) {
        return dsInterfaceParamMapper.selectById(id);
    }

    @Override
    public PageResult<DsInterfaceParamDO> getDsInterfaceParamPage(DsInterfaceParamPageReqVO reqVO) {
        return dsInterfaceParamMapper.selectPage(reqVO);
    }

    @Override
    public List<DsInterfaceParamDO> getListByInterface(Long dsInterfaceId, Integer paramDirection) {
        return dsInterfaceParamMapper.selectListByInterface(dsInterfaceId, paramDirection);
    }

    private DsInterfaceParamDO validateExists(Long id) {
        DsInterfaceParamDO param = dsInterfaceParamMapper.selectById(id);
        if (param == null) {
            throw exception(DS_INTERFACE_PARAM_NOT_EXISTS);
        }
        return param;
    }

    private void validateInterfaceExists(Long dsInterfaceId) {
        if (dsInterfaceMapper.selectById(dsInterfaceId) == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
    }
}
```

`DsInterfaceParamController.java`：五端点（写法同前）+ `list-by-interface`：

```java
    @GetMapping("/list-by-interface")
    @Operation(summary = "按接口获得参数列表")
    @Parameter(name = "dsInterfaceId", description = "接口ID", required = true)
    @Parameter(name = "paramDirection", description = "方向：1入参 2出参")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:query')")
    public CommonResult<List<DsInterfaceParamRespVO>> listByInterface(
            @RequestParam("dsInterfaceId") Long dsInterfaceId,
            @RequestParam(value = "paramDirection", required = false) Integer paramDirection) {
        return success(BeanUtils.toBean(
                dsInterfaceParamService.getListByInterface(dsInterfaceId, paramDirection),
                DsInterfaceParamRespVO.class));
    }
```

`@RequestMapping("/adapter/ds-interface-param")`，权限串 `adapter:ds-interface-param:*`。

- [ ] **Step 5: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`DsInterfaceParamServiceImplTest` 全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-adapter/src sql/mysql/ruoyi-vue-pro.sql
git commit -m "feat(adapter): 数据源接口参数 ds_interface_param 映射定义 CRUD（入/出参+转换函数+JSONPath）"
```

---

### Task 6: 数据源应答码批量导入/导出（Excel）

**Files:**
- Modify: `yudao-module-adapter/pom.xml`（若缺 Excel 依赖，加 `yudao-spring-boot-starter-excel` 或基座 EasyExcel 依赖）
- Create: `.../adapter/controller/admin/datasource/vo/DsResponseCodeImportExcelVO.java`
- Create: `.../adapter/controller/admin/datasource/vo/DsResponseCodeImportRespVO.java`
- Modify: `.../adapter/service/datasource/DsResponseCodeService.java`、`DsResponseCodeServiceImpl.java`（加 import/export 方法）
- Modify: `.../adapter/controller/admin/datasource/DsResponseCodeController.java`（加 `/export-excel`、`/import-excel`、`/get-import-template`）
- Test: `.../adapter/service/datasource/DsResponseCodeImportTest.java`

**Interfaces:**
- Consumes: Task 4 的 `DsResponseCodeService`/`Mapper`/`DO`。
- Produces:
  - `class DsResponseCodeImportExcelVO { Long dataSourceId; Long dsInterfaceId; String rawCode; String rawDesc; Boolean success; Boolean charge; Boolean retryable; Boolean triggerSwitch; String platformCode; }`（`@ExcelProperty` 注解）
  - `class DsResponseCodeImportRespVO { List<String> createRawCodes; List<String> updateRawCodes; Map<String,String> failureRawCodes; }`（`@Builder`）
  - `interface` 追加：`DsResponseCodeImportRespVO importResponseCodes(List<DsResponseCodeImportExcelVO> list, boolean updateSupport); List<DsResponseCodeDO> getExportList(DsResponseCodePageReqVO reqVO);`
  - HTTP：`/adapter/ds-response-code/{import-excel,export-excel,get-import-template}`

- [ ] **Step 1: 确认 Excel 工具坐标**

`grep -rn 'yudao-spring-boot-starter-excel\|EasyExcel\|ExcelUtils' yudao-module-system/yudao-module-system-server/pom.xml yudao-module-system/yudao-module-system-server/src/main/java` 找出基座导入导出用的依赖与工具类（yudao 常用 `cn.iocoder.yudao.framework.excel.core.util.ExcelUtils` + EasyExcel 的 `@ExcelProperty`，参照 `system` 模块的用户导入 `UserImportExcelVO`/`UserImportRespVO`）。若 adapter pom 未含该 starter，则追加依赖。

- [ ] **Step 2: 写失败测试（导入服务，纯 DB 单测）**

`DsResponseCodeImportTest.java`：

```java
package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportExcelVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeImportTest extends BaseDbUnitTest {

    @Resource
    private DsResponseCodeServiceImpl service;

    private DsResponseCodeImportExcelVO row(String rawCode, String platformCode) {
        DsResponseCodeImportExcelVO vo = new DsResponseCodeImportExcelVO();
        vo.setDataSourceId(1L);
        vo.setDsInterfaceId(0L);
        vo.setRawCode(rawCode);
        vo.setSuccess("0000".equals(platformCode));
        vo.setCharge(false);
        vo.setRetryable(false);
        vo.setTriggerSwitch(false);
        vo.setPlatformCode(platformCode);
        return vo;
    }

    @Test
    void import_createsNewRows() {
        DsResponseCodeImportRespVO resp = service.importResponseCodes(
                List.of(row("R01", "0000"), row("R02", "3001")), false);
        assertThat(resp.getCreateRawCodes()).containsExactlyInAnyOrder("R01", "R02");
        assertThat(resp.getFailureRawCodes()).isEmpty();
    }

    @Test
    void import_duplicateWithoutUpdate_reportsFailure() {
        service.importResponseCodes(List.of(row("DUP", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row("DUP", "3001")), false);
        assertThat(resp.getCreateRawCodes()).isEmpty();
        assertThat(resp.getFailureRawCodes()).containsKey("DUP");
    }

    @Test
    void import_duplicateWithUpdate_updatesRow() {
        service.importResponseCodes(List.of(row("UPD", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row("UPD", "3001")), true);
        assertThat(resp.getUpdateRawCodes()).contains("UPD");
        List<DsResponseCodeDO> all = service.getExportList(new DsResponseCodePageReqVO());
        assertThat(all).anySatisfy(d -> {
            if ("UPD".equals(d.getRawCode())) {
                assertThat(d.getPlatformCode()).isEqualTo("3001");
            }
        });
    }
}
```

- [ ] **Step 3: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败（import 方法与 VO 不存在）。

- [ ] **Step 4: 最小实现**

`DsResponseCodeImportExcelVO`（EasyExcel `@ExcelProperty("原始应答码")` 等注解，字段见 Interfaces，`@Data`）；`DsResponseCodeImportRespVO`（`@Data @Builder`，三集合，参照 `system` 模块 `UserImportRespVO`）。

`DsResponseCodeService` 接口追加两方法；`DsResponseCodeServiceImpl` 追加：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DsResponseCodeImportRespVO importResponseCodes(List<DsResponseCodeImportExcelVO> list, boolean updateSupport) {
        DsResponseCodeImportRespVO resp = DsResponseCodeImportRespVO.builder()
                .createRawCodes(new ArrayList<>())
                .updateRawCodes(new ArrayList<>())
                .failureRawCodes(new LinkedHashMap<>())
                .build();
        for (DsResponseCodeImportExcelVO row : list) {
            Long ifId = row.getDsInterfaceId() == null ? 0L : row.getDsInterfaceId();
            DsResponseCodeDO exist = dsResponseCodeMapper.selectByScopeAndRawCode(
                    row.getDataSourceId(), ifId, row.getRawCode());
            if (exist == null) {
                DsResponseCodeDO code = BeanUtils.toBean(row, DsResponseCodeDO.class);
                code.setId(null);
                code.setDsInterfaceId(ifId);
                dsResponseCodeMapper.insert(code);
                resp.getCreateRawCodes().add(row.getRawCode());
                continue;
            }
            if (!updateSupport) {
                resp.getFailureRawCodes().put(row.getRawCode(), DS_RESPONSE_CODE_DUPLICATE.getMsg());
                continue;
            }
            DsResponseCodeDO update = BeanUtils.toBean(row, DsResponseCodeDO.class);
            update.setId(exist.getId());
            update.setDsInterfaceId(ifId);
            dsResponseCodeMapper.updateById(update);
            resp.getUpdateRawCodes().add(row.getRawCode());
        }
        return resp;
    }

    @Override
    public List<DsResponseCodeDO> getExportList(DsResponseCodePageReqVO reqVO) {
        return dsResponseCodeMapper.selectList(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eqIfPresent(DsResponseCodeDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsResponseCodeDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .likeIfPresent(DsResponseCodeDO::getRawCode, reqVO.getRawCode())
                .orderByDesc(DsResponseCodeDO::getId));
    }
```

（需在 `DsResponseCodeServiceImpl` 顶部补 `import` ：`java.util.ArrayList`、`java.util.LinkedHashMap`、`org.springframework.transaction.annotation.Transactional`、`LambdaQueryWrapperX`。）

`DsResponseCodeController` 追加三端点，参照 `system` 模块 `UserController` 的 import/export/template：`/import-excel`（`@PostMapping`，`@RequestParam("file") MultipartFile file` + `@RequestParam(value="updateSupport", required=false, defaultValue="false") Boolean updateSupport`，用 `ExcelUtils.read(file, DsResponseCodeImportExcelVO.class)` 解析后调 `importResponseCodes`）、`/export-excel`（`@GetMapping`，`ExcelUtils.write(response, "数据源应答码.xls", "数据", DsResponseCodeRespVO.class, BeanUtils.toBean(getExportList(reqVO), DsResponseCodeRespVO.class))`）、`/get-import-template`（`@GetMapping`，下发空模板）。这三端点走 HTTP/Servlet，不进单测；单测覆盖 `importResponseCodes`/`getExportList` 业务逻辑。

- [ ] **Step 5: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`DsResponseCodeImportTest` 3 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-adapter/pom.xml yudao-module-adapter/src
git commit -m "feat(adapter): 数据源应答码 Excel 批量导入/导出（新增/更新/失败明细）"
```

---

## P1 完成定义（DoD）

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test` 全绿，且不依赖 docker 中间件（H2 内存库）。
2. 四张表（data_source / ds_interface / ds_response_code / ds_interface_param）具备完整增删改查分页 + 归属校验 + 编码自动生成；应答码具四标记、查重、未映射码查询、Excel 批量导入导出。
3. MySQL DDL 已并入 `sql/mysql/ruoyi-vue-pro.sql`，运行库 `apiten_adapter` 可承载这些表（docker exec 应用建库与建表）。
4. 每任务一个 commit，git log 与本计划任务一一对应；扁平模块范式与 P0 一致。
5. 全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续计划（本计划不含）

- **P2 HTTP 适配引擎**：`HttpDataSourceProvider implements DataSourceProvider`，消费本期 ds_interface/ds_interface_param/ds_response_code 配置——请求模板渲染（`${param}` + 转换函数）、真实 HTTP 外调（超时/重试）、出参 JSONPath 抽取、应答码映射（原始码→平台码 + 四标记，未映射归一 3001 告警）、联调测试台、健康检查；`InvokeController` 由单 provider 注入改为按数据源 `protocol_type` 路由的 provider 注册表。
- P4 路由/分流/切换链；P5 计费；P6 流水落库与幂等；P7 限流限额与配额账本；P8 报表；P9 监控。
- 前端页面与 sys_menu 菜单/按钮权限落库（前端里程碑统一处理）。
