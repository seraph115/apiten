# 征信 API 平台 P3：产品域管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `yudao-module-product` 模块，落地产品域的持久化与管理端 CRUD：产品信息、产品功能、功能-数据源接口绑定、产品入/出参定义，并提供「产品 → 数据源接口」解析服务，作为后续路由里程碑的入口种子（productCode → 接口）。

**Architecture:** 沿用 P1 数据源管理确立的 yudao 标准 CRUD 纵切范式（DO/Mapper/VO/Service/Controller/ErrorCode/DDL/Test）。产品域独立成新模块 `yudao-module-product`（与 P0/P1 单模块扁平结构一致），持久化基建（MyBatis-Plus + H2 `BaseDbUnitTest`）如 P1 Task 1。功能-接口绑定通过**松耦合**引用 adapter 的 `ds_interface`（只存 `ds_interface_id` + 编码快照，不依赖 adapter 模块代码、无跨库 FK）——存在性校验留待路由实际调用时。

**Tech Stack:** Java 21 + Spring Boot 3.5.15；MyBatis-Plus（`BaseDO`/`BaseMapperX`/`LambdaQueryWrapperX`）、yudao 框架（`CommonResult`/`PageResult`/`PageParam`/`BeanUtils.toBean`/`ServiceExceptionUtil.exception`）、H2 内存库单测（`BaseDbUnitTest`）、JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§6.3 产品信息管理、§7 数据模型（产品域）、§8.2 产品上架流程、§8.4 步骤⑤路由决策。

## Global Constraints

- JDK 21；Maven 命令前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`；测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-product -am test -q`，必须离线通过（H2，不依赖中间件）。
- **扁平模块范式**：新模块 `yudao-module-product`，包根 `cn.iocoder.yudao.module.product`；错误码 `cn.iocoder.yudao.module.product.enums.ErrorCodeConstants`，本域占 `1_021_xxx_xxx` 段（product_info=`1_021_001_xxx`、product_function=`1_021_002_xxx`、func_interface=`1_021_003_xxx`、product_param=`1_021_004_xxx`）。
- **表命名**：`product_info`、`product_function`、`product_func_interface`、`product_param`；均含 `BaseDO` 五审计列，单租户（extends `BaseDO` + `@TenantIgnore`，无 `tenant_id`）。
- **运行库**：`apiten_product`（MySQL `127.0.0.1:23306` 本地 compose）；单测用 H2。
- **编码规则**（§7）：产品 `P`+6 位序号、功能 `F`+6 位序号；**服务端由自增 id 派生**（沿用 P1 Task 2 修复方案：临时 UUID 占位插入 → 据 id 回填 `P%06d`/`F%06d`，`@Transactional`；杜绝软删除序号复用），H2 DDL 对编码列加唯一约束。
- **模块依赖**：product 模块 pom 依赖 `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant,test}`（同 adapter，groupId `cn.iocoder.cloud`）+ `apiten-common`。**不依赖** yudao-module-adapter（功能-接口绑定只存接口 id/编码快照）。
- **权限串**：`@ss.hasPermission('product:<kebab-domain>:{create|update|delete|query}')`（菜单落库留待前端里程碑）。
- **DDL 双写**：MySQL DDL 追加到 `docker/mysql/init/05-product-schema.sql`（新建，供 apiten_product 运行库）；H2 DDL 到 `yudao-module-product/src/test/resources/sql/create_tables.sql` + `clean.sql`。
- 每任务一个 `git commit`（仅 add 改动文件，不用 `-A`）。
- **不在本期范围**：组合产品（combo_product，后续里程碑）、产品成本（product_cost，计费里程碑）、机构产品绑定（org_product，机构里程碑）、路由/分流/切换链（路由里程碑）、脱敏规则运行时执行（本期只存规则定义）、前端页面与菜单。

---

### Task 1: yudao-module-product 模块与持久化基建

**Files:**
- Create: `yudao-module-product/pom.xml`
- Create: `yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/ProductServerApplication.java`
- Create: `yudao-module-product/src/main/resources/application.yaml`、`application-local.yaml`、`application-dev.yaml`
- Create: `yudao-module-product/src/test/resources/application-unit-test.yaml`、`logback.xml`、`sql/create_tables.sql`、`sql/clean.sql`
- Create: `yudao-module-product/src/test/java/cn/iocoder/yudao/module/product/DbHarnessSmokeTest.java`
- Create: `docker/mysql/init/05-product-schema.sql`（先空占位 + 建库，后续任务追加建表）
- Modify: 根 `pom.xml`（`<modules>` 增加 `yudao-module-product`）

**Interfaces:**
- Consumes: 无（新模块）。
- Produces: 可离线运行的 `BaseDbUnitTest` 持久化基座；运行库 `apiten_product`；后续任务的 DO/Mapper/Service 落于此。

- [ ] **Step 1: 建模块 pom + 注册根 pom**

`yudao-module-product/pom.xml`：parent 与依赖照搬 `yudao-module-adapter/pom.xml` 现状（`sed -n '/<parent>/,/<\/dependencies>/p' yudao-module-adapter/pom.xml` 抄坐标，仅把 artifactId 改为 `yudao-module-product`；含 apiten-common + yudao-spring-boot-starter-{mybatis,web,security,biz-tenant} + test starter）。根 `pom.xml` `<modules>` 追加 `<module>yudao-module-product</module>`。

- [ ] **Step 2: 运行库 + 服务配置**

`docker/mysql/init/05-product-schema.sql` 顶部：

```sql
-- apiten 产品域建表（apiten_product 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_product` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_product`;
```

对运行容器立即建库：`docker exec apiten-mysql-1 mysql -uroot -papiten123 -e "CREATE DATABASE IF NOT EXISTS \`apiten_product\` DEFAULT CHARACTER SET utf8mb4;"`

`application.yaml`/`application-local.yaml`/`application-dev.yaml`：照搬 `yudao-module-adapter` 对应文件（`spring.application.name: product-server`，端口 48093，`spring.datasource.dynamic.datasource.master.url` 指向 `apiten_product`，Nacos/profile 同 adapter 约定）。

- [ ] **Step 3: 单测资源（H2 基座）**

照搬 `yudao-module-adapter/src/test/resources/` 的 `application-unit-test.yaml`（`yudao.info.base-package` 改 `cn.iocoder.yudao.module.product`）、`logback.xml`；`sql/create_tables.sql` 先放一张 smoke 表（结构同 P1 的 `adapter_db_harness_smoke`，表名 `product_db_harness_smoke`）；`sql/clean.sql` 对应 `DELETE`。

- [ ] **Step 4: 冒烟测试**

`DbHarnessSmokeTest.java`：照搬 P1 adapter 的 `DbHarnessSmokeTest`（`extends BaseDbUnitTest`；注意 `BaseDbUnitTest` 不装配 JdbcTemplateAutoConfiguration，须 `new JdbcTemplate(dataSource)` 手动构造，见 P1 Task 1 报告），插入/计数 `product_db_harness_smoke`。

- [ ] **Step 5: 运行验证**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-product -am test -q`
Expected：先编译失败（缺依赖/资源）；补齐后 `Tests run: 1, Failures: 0`，离线。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-product docker/mysql/init/05-product-schema.sql pom.xml
git commit -m "chore(product): 新建 yudao-module-product 模块与 MyBatis-Plus/H2 单测基座，apiten_product 库"
```

---

### Task 2: 产品信息 product_info CRUD

**Files:**
- Create: `.../product/dal/dataobject/product/ProductDO.java`
- Create: `.../product/dal/mysql/product/ProductMapper.java`
- Create: `.../product/controller/admin/product/vo/{ProductSaveReqVO,ProductPageReqVO,ProductRespVO}.java`
- Create: `.../product/service/product/{ProductService,ProductServiceImpl}.java`
- Create: `.../product/controller/admin/product/ProductController.java`
- Create: `.../product/enums/ErrorCodeConstants.java`
- Test: `.../product/service/product/ProductServiceImplTest.java`
- Modify: `docker/mysql/init/05-product-schema.sql`、`.../test/resources/sql/create_tables.sql`、`clean.sql`
- （`.../product/...` = `yudao-module-product/src/main/java/cn/iocoder/yudao/module/product/...`）

**Interfaces:**
- Consumes: Task 1 基座。
- Produces:
  - `class ProductDO extends BaseDO { Long id; String productCode; String name; Integer productType; Integer authType; Integer status; String version; String description; Boolean cacheEnabled; Boolean asyncSupport; Boolean needAuthNo; String remark; }`
  - `interface ProductService { Long createProduct(ProductSaveReqVO); void updateProduct(ProductSaveReqVO); void deleteProduct(Long id); ProductDO getProduct(Long id); ProductDO getProductByCode(String productCode); PageResult<ProductDO> getProductPage(ProductPageReqVO); List<ProductDO> getSimpleList(); }`
  - HTTP：`/product/info/{create,update,delete,get,page,simple-list}`
  - `ErrorCodeConstants.PRODUCT_NOT_EXISTS = new ErrorCode(1_021_001_000, "产品不存在")`

- [ ] **Step 1: ErrorCode + DDL**

`.../product/enums/ErrorCodeConstants.java`：

```java
package cn.iocoder.yudao.module.product.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** product 模块错误码，占用 1-021-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 产品信息 1-021-001-xxx ==========
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_021_001_000, "产品不存在");
}
```

MySQL DDL 追加到 `docker/mysql/init/05-product-schema.sql`：

```sql
CREATE TABLE `product_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码 P+序号',
  `name` varchar(128) NOT NULL COMMENT '产品名称',
  `product_type` tinyint NOT NULL DEFAULT 1 COMMENT '产品类型：1企业 2个人 3核验 4司法 5经营风险 6知识产权 7报告 8组合',
  `auth_type` tinyint NOT NULL DEFAULT 0 COMMENT '认证类型',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `version` varchar(16) NOT NULL DEFAULT 'v1' COMMENT '版本',
  `description` varchar(512) NULL DEFAULT '' COMMENT '说明',
  `cache_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否缓存',
  `async_support` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否支持异步',
  `need_auth_no` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要授权书编号',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品信息表';
```

H2 DDL 追加到 `create_tables.sql`：

```sql
CREATE TABLE IF NOT EXISTS "product_info" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "product_code" varchar(32) NOT NULL,
    "name" varchar(128) NOT NULL,
    "product_type" tinyint NOT NULL DEFAULT 1,
    "auth_type" tinyint NOT NULL DEFAULT 0,
    "status" tinyint NOT NULL DEFAULT 0,
    "version" varchar(16) NOT NULL DEFAULT 'v1',
    "description" varchar(512) DEFAULT '',
    "cache_enabled" bit NOT NULL DEFAULT FALSE,
    "async_support" bit NOT NULL DEFAULT FALSE,
    "need_auth_no" bit NOT NULL DEFAULT FALSE,
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_product_info_code" UNIQUE ("product_code")
) COMMENT '产品信息表';
```

`clean.sql` 追加：`DELETE FROM "product_info";`

- [ ] **Step 2: 写失败测试**

`ProductServiceImplTest.java`（结构、断言风格、`assertServiceException` 导入照搬 P1 `DataSourceServiceImplTest`；`assertServiceException` = `cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException`）：

```java
package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductServiceImpl.class)
class ProductServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductServiceImpl service;

    private ProductSaveReqVO newReq(String name) {
        ProductSaveReqVO vo = new ProductSaveReqVO();
        vo.setName(name);
        vo.setProductType(1);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesProductCode() {
        Long id = service.createProduct(newReq("企业工商信息"));
        ProductDO db = service.getProduct(id);
        assertThat(db.getProductCode()).matches("P\\d{6}");
        assertThat(db.getName()).isEqualTo("企业工商信息");
    }

    @Test
    void getByCode_returnsProduct() {
        Long id = service.createProduct(newReq("个人核验"));
        String code = service.getProduct(id).getProductCode();
        assertThat(service.getProductByCode(code).getId()).isEqualTo(id);
    }

    @Test
    void update_notExists_throws() {
        ProductSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateProduct(upd), PRODUCT_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createProduct(newReq("待删"));
        service.deleteProduct(id);
        assertThat(service.getProduct(id)).isNull();
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        Long id1 = service.createProduct(newReq("A"));
        Long id2 = service.createProduct(newReq("B"));
        String c2 = service.getProduct(id2).getProductCode();
        service.deleteProduct(id2);
        Long id3 = service.createProduct(newReq("C"));
        String c3 = service.getProduct(id3).getProductCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("P\\d{6}");
    }

    @Test
    void page_filtersByName() {
        service.createProduct(newReq("工商信息"));
        service.createProduct(newReq("司法涉诉"));
        ProductPageReqVO q = new ProductPageReqVO();
        q.setName("工商");
        PageResult<ProductDO> page = service.getProductPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败** — `mvn -pl yudao-module-product -am test -q` 编译失败。

- [ ] **Step 4: 最小实现**

`ProductDO.java`：

```java
package cn.iocoder.yudao.module.product.dal.dataobject.product;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("product_info")
@KeySequence("product_info_seq")
@Data
@TenantIgnore
public class ProductDO extends BaseDO {
    private Long id;
    private String productCode;
    private String name;
    private Integer productType;
    private Integer authType;
    private Integer status;
    private String version;
    private String description;
    private Boolean cacheEnabled;
    private Boolean asyncSupport;
    private Boolean needAuthNo;
    private String remark;
}
```

`ProductMapper.java`（`@Mapper extends BaseMapperX<ProductDO>`；`selectPage(ProductPageReqVO)` 用 `LambdaQueryWrapperX` 按 name(like)/productType/status 过滤 + `orderByDesc(id)`；`selectByProductCode(String)` = `selectOne(ProductDO::getProductCode, code)`；**不含** `selectMaxId`——编码 id 派生。写法同 P1 `DataSourceMapper`）。

VO 三件：`ProductSaveReqVO`（`id`；`name` `@NotEmpty`；`productType` `@NotNull`；`authType`；`status` `@NotNull`；`version`；`description`；`cacheEnabled`；`asyncSupport`；`needAuthNo`；`remark`）、`ProductPageReqVO extends PageParam`（`@EqualsAndHashCode(callSuper=true)`；`name`/`productType`/`status`）、`ProductRespVO`（镜像 DO 全字段 + `LocalDateTime createTime`）。类注解与写法与 P1 三个 VO 完全一致，仅字段替换。

`ProductService`/`ProductServiceImpl`：接口见 Interfaces；Impl 结构照搬 P1 `DataSourceServiceImpl` **已修复版**——`createProduct` 用 id 派生编码：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createProduct(ProductSaveReqVO reqVO) {
    ProductDO product = BeanUtils.toBean(reqVO, ProductDO.class);
    product.setId(null);
    product.setProductCode(java.util.UUID.randomUUID().toString().replace("-", "")); // 临时唯一占位(32位)
    productMapper.insert(product);
    product.setProductCode(String.format("P%06d", product.getId()));
    productMapper.updateById(product);
    return product.getId();
}
```

`updateProduct` 校验存在（抛 `PRODUCT_NOT_EXISTS`）后 `setProductCode(null)` 再 `updateById`（编码不可改）；`getProductByCode` 调 `selectByProductCode`；`getSimpleList` 全部按 id 倒序。`import org.springframework.transaction.annotation.Transactional;`。

`ProductController.java`：`/product/info` 五端点 + `simple-list`，权限串 `product:info:*`，写法照搬 P1 `DataSourceController`（`BeanUtils.toBean` 转 RespVO）。

- [ ] **Step 5: 运行验证通过** — `ProductServiceImplTest` 6 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-product docker/mysql/init/05-product-schema.sql
git commit -m "feat(product): 产品信息 product_info CRUD（编码 id 派生+按编码查询）"
```

---

### Task 3: 产品功能 product_function CRUD

**Files:**
- Create: `.../product/dal/dataobject/product/ProductFunctionDO.java`
- Create: `.../product/dal/mysql/product/ProductFunctionMapper.java`
- Create: `.../product/controller/admin/product/vo/{ProductFunctionSaveReqVO,ProductFunctionPageReqVO,ProductFunctionRespVO}.java`
- Create: `.../product/service/product/{ProductFunctionService,ProductFunctionServiceImpl}.java`
- Create: `.../product/controller/admin/product/ProductFunctionController.java`
- Modify: `.../product/enums/ErrorCodeConstants.java`
- Test: `.../product/service/product/ProductFunctionServiceImplTest.java`
- Modify: DDL 三处

**Interfaces:**
- Consumes: Task 2 的 `ProductMapper`（校验 `productId` 存在 → `PRODUCT_NOT_EXISTS`）。
- Produces:
  - `class ProductFunctionDO extends BaseDO { Long id; String funcCode; String name; Long productId; Integer sort; Boolean required; Boolean charge; Integer status; String remark; }`
  - `interface ProductFunctionService { Long createProductFunction(ProductFunctionSaveReqVO); void updateProductFunction(ProductFunctionSaveReqVO); void deleteProductFunction(Long id); ProductFunctionDO getProductFunction(Long id); PageResult<ProductFunctionDO> getProductFunctionPage(ProductFunctionPageReqVO); List<ProductFunctionDO> getListByProductId(Long productId); }`
  - HTTP：`/product/function/{create,update,delete,get,page,list-by-product}`；权限串 `product:function:*`
  - `ErrorCodeConstants.PRODUCT_FUNCTION_NOT_EXISTS = new ErrorCode(1_021_002_000, "产品功能不存在")`

**实现要点（结构照搬 P1 Task 3 `ds_interface`，仅字段替换）：** `funcCode` 用 id 派生（临时 UUID 占位 → 回填 `F%06d`，`@Transactional`）；H2 DDL 对 `func_code` 加唯一约束；create/update 校验 `productId` 存在（注入 `ProductMapper`，不存在抛 `PRODUCT_NOT_EXISTS`）；`getListByProductId` 用 `selectList(ProductFunctionDO::getProductId, productId)`。**测试覆盖**（先经 `ProductMapper` 造真实父产品拿 id，参考 P1 `DsInterfaceServiceImplTest` 的 `insertDataSource` 辅助法）：编码生成、父产品不存在→`PRODUCT_NOT_EXISTS`、更新功能不存在→`PRODUCT_FUNCTION_NOT_EXISTS`、按产品列表、编码删后重建回归、分页过滤。

MySQL DDL（`docker/mysql/init/05-product-schema.sql`）：

```sql
CREATE TABLE `product_function` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `func_code` varchar(32) NOT NULL COMMENT '功能编码 F+序号',
  `name` varchar(128) NOT NULL COMMENT '功能名称',
  `product_id` bigint NOT NULL COMMENT '所属产品ID',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必选',
  `charge` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否计费',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_func_code` (`func_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品功能表';
```

H2 DDL（同风格：双引号、`GENERATED BY DEFAULT AS IDENTITY`、`bit ... DEFAULT FALSE`，加 `CONSTRAINT "uk_product_function_code" UNIQUE ("func_code")`）；`clean.sql` 加 `DELETE FROM "product_function";`。

DO 全字段见 Interfaces（`@TableName("product_function")` + `@KeySequence("product_function_seq")` + `@Data` + `@TenantIgnore`）。VO 三件字段：Save（`id`；`name` `@NotEmpty`；`productId` `@NotNull`；`sort`；`required`；`charge`；`status` `@NotNull`；`remark`），Page（`name`/`productId`/`status`），Resp（镜像 DO + createTime）。Mapper `selectPage` 按 name(like)/productId/status 过滤 + `selectListByProductId`；无 `selectMaxId`。

- [ ] **Step 1: ErrorCode + DDL** — 追加 `PRODUCT_FUNCTION_NOT_EXISTS = new ErrorCode(1_021_002_000, "产品功能不存在")`；建表 DDL 三处。
- [ ] **Step 2: 写失败测试**（6 用例，见实现要点）。
- [ ] **Step 3: 运行验证失败**。
- [ ] **Step 4: 最小实现**（DO/Mapper/3VO/Service/Impl/Controller，id 派生 funcCode + 父产品校验）。
- [ ] **Step 5: 运行验证通过**。
- [ ] **Step 6: Commit** — `feat(product): 产品功能 product_function CRUD（归属产品校验+编码 id 派生）`。

---

### Task 4: 功能-接口绑定 product_func_interface CRUD

**Files:**
- Create: `.../product/dal/dataobject/product/FuncInterfaceDO.java`
- Create: `.../product/dal/mysql/product/FuncInterfaceMapper.java`
- Create: `.../product/controller/admin/product/vo/{FuncInterfaceSaveReqVO,FuncInterfacePageReqVO,FuncInterfaceRespVO}.java`
- Create: `.../product/service/product/{FuncInterfaceService,FuncInterfaceServiceImpl}.java`
- Create: `.../product/controller/admin/product/FuncInterfaceController.java`
- Modify: `.../product/enums/ErrorCodeConstants.java`
- Test: `.../product/service/product/FuncInterfaceServiceImplTest.java`
- Modify: DDL 三处

**Interfaces:**
- Consumes: Task 3 的 `ProductFunctionMapper`（校验 `productFunctionId` 存在 → `PRODUCT_FUNCTION_NOT_EXISTS`）。
- Produces:
  - `class FuncInterfaceDO extends BaseDO { Long id; Long productFunctionId; Long dsInterfaceId; String dsInterfaceCode; Integer priority; Boolean isDefault; Integer status; }`（`ds_interface_id`/`ds_interface_code` 为 adapter 数据源接口的**松耦合快照引用**，product 模块不校验其存在性——留待路由调用时）
  - `interface FuncInterfaceService { Long createFuncInterface(FuncInterfaceSaveReqVO); void updateFuncInterface(FuncInterfaceSaveReqVO); void deleteFuncInterface(Long id); FuncInterfaceDO getFuncInterface(Long id); PageResult<FuncInterfaceDO> getFuncInterfacePage(FuncInterfacePageReqVO); List<FuncInterfaceDO> getListByFunction(Long productFunctionId); }`
  - HTTP：`/product/func-interface/{create,update,delete,get,page,list-by-function}`；权限串 `product:func-interface:*`
  - `ErrorCodeConstants.FUNC_INTERFACE_NOT_EXISTS = new ErrorCode(1_021_003_000, "功能接口绑定不存在")`

MySQL DDL：

```sql
CREATE TABLE `product_func_interface` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_function_id` bigint NOT NULL COMMENT '产品功能ID',
  `ds_interface_id` bigint NOT NULL COMMENT '数据源接口ID（松耦合引用 adapter）',
  `ds_interface_code` varchar(32) NULL DEFAULT '' COMMENT '数据源接口编码快照',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（越小越优先）',
  `is_default` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认数据源',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_func_id` (`product_function_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品功能-数据源接口绑定表';
```

H2 DDL（同风格，**无**唯一约束——一个功能可绑多接口）；`clean.sql` 加 `DELETE FROM "product_func_interface";`。

**实现要点（结构照搬 P1 `ds_interface_param` CRUD）：** `FuncInterfaceDO` 无业务编码（无 id 派生）；create/update 校验 `productFunctionId` 存在（注入 `ProductFunctionMapper`，不存在抛 `PRODUCT_FUNCTION_NOT_EXISTS`）；`getListByFunction` 用 `selectList` where `productFunctionId` 并 `orderByAsc(priority)`；`selectPage` 按 `productFunctionId`/`dsInterfaceId`/`status` 过滤。DO（`@TableName("product_func_interface")`）与三 VO（Save：`id`；`productFunctionId` `@NotNull`；`dsInterfaceId` `@NotNull`；`dsInterfaceCode`；`priority`；`isDefault`；`status` `@NotNull`。Page：`productFunctionId`/`dsInterfaceId`/`status`。Resp：镜像 DO + createTime）写法同前。

**测试**（先经 `ProductMapper` + `ProductFunctionMapper` 造真实产品与功能拿 id）：create 绑定持久化四字段、`productFunctionId` 不存在→`PRODUCT_FUNCTION_NOT_EXISTS`、update 绑定不存在→`FUNC_INTERFACE_NOT_EXISTS`、`getListByFunction` 按功能过滤且 priority 升序、分页过滤。

- [ ] **Step 1: ErrorCode + DDL**。
- [ ] **Step 2: 写失败测试**（5 用例）。
- [ ] **Step 3: 运行验证失败**。
- [ ] **Step 4: 最小实现**。
- [ ] **Step 5: 运行验证通过**。
- [ ] **Step 6: Commit** — `feat(product): 功能-数据源接口绑定 product_func_interface CRUD（松耦合引用+优先级）`。

---

### Task 5: 产品参数 product_param CRUD

**Files:**
- Create: `.../product/dal/dataobject/product/ProductParamDO.java`
- Create: `.../product/dal/mysql/product/ProductParamMapper.java`
- Create: `.../product/controller/admin/product/vo/{ProductParamSaveReqVO,ProductParamPageReqVO,ProductParamRespVO}.java`
- Create: `.../product/service/product/{ProductParamService,ProductParamServiceImpl}.java`
- Create: `.../product/controller/admin/product/ProductParamController.java`
- Modify: `.../product/enums/ErrorCodeConstants.java`
- Test: `.../product/service/product/ProductParamServiceImplTest.java`
- Modify: DDL 三处

**Interfaces:**
- Consumes: Task 2 `ProductMapper`（校验 `productId` 存在）。
- Produces:
  - `class ProductParamDO extends BaseDO { Long id; Long productId; Integer paramDirection; String fieldName; Integer dataType; Boolean required; String validationRule; String desensitizeRule; String description; Integer sort; }`（`paramDirection`：1入参 2出参；`validationRule` 入参校验规则、`desensitizeRule` 出参脱敏规则——本期只存定义，运行时执行留待后续）
  - `interface ProductParamService { Long createProductParam(ProductParamSaveReqVO); void updateProductParam(ProductParamSaveReqVO); void deleteProductParam(Long id); ProductParamDO getProductParam(Long id); PageResult<ProductParamDO> getProductParamPage(ProductParamPageReqVO); List<ProductParamDO> getListByProduct(Long productId, Integer paramDirection); }`
  - HTTP：`/product/param/{create,update,delete,get,page,list-by-product}`；权限串 `product:param:*`
  - `ErrorCodeConstants.PRODUCT_PARAM_NOT_EXISTS = new ErrorCode(1_021_004_000, "产品参数不存在")`

MySQL DDL：

```sql
CREATE TABLE `product_param` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `product_id` bigint NOT NULL COMMENT '所属产品ID',
  `param_direction` tinyint NOT NULL COMMENT '方向：1入参 2出参',
  `field_name` varchar(128) NOT NULL COMMENT '字段名',
  `data_type` tinyint NOT NULL DEFAULT 1 COMMENT '数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
  `validation_rule` varchar(256) NULL DEFAULT '' COMMENT '入参校验规则',
  `desensitize_rule` varchar(128) NULL DEFAULT '' COMMENT '出参脱敏规则',
  `description` varchar(256) NULL DEFAULT '' COMMENT '说明',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_product_direction` (`product_id`, `param_direction`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品参数定义表';
```

H2 DDL（同风格）；`clean.sql` 加 `DELETE FROM "product_param";`。

**实现要点（结构照搬 P1 `ds_interface_param`）：** 无业务编码；create/update 校验 `productId` 存在（`PRODUCT_NOT_EXISTS`）；`getListByProduct(productId, paramDirection)` 用 `eq(productId) + eqIfPresent(paramDirection)` 升序；`selectPage` 按 `productId`/`paramDirection`/`fieldName(like)` 过滤。DO（`@TableName("product_param")`）与三 VO（Save：`id`；`productId` `@NotNull`；`paramDirection` `@NotNull`；`fieldName` `@NotEmpty`；`dataType` `@NotNull`；`required`；`validationRule`；`desensitizeRule`；`description`；`sort`。Page：`productId`/`paramDirection`/`fieldName`。Resp：镜像 DO + createTime）。**测试**（先造真实父产品）：入参持久化、出参含 desensitizeRule、父产品不存在→`PRODUCT_NOT_EXISTS`、更新参数不存在→`PRODUCT_PARAM_NOT_EXISTS`、按产品+方向列表、分页过滤。

- [ ] **Step 1–6**：同前六步（ErrorCode+DDL → 失败测试 → 验证失败 → 实现 → 验证通过 → commit `feat(product): 产品参数 product_param 定义 CRUD（入/出参+校验/脱敏规则）`）。

---

### Task 6: 产品 → 数据源接口 解析服务（路由里程碑入口种子）

**Files:**
- Create: `.../product/service/resolve/ResolvedInterface.java`
- Create: `.../product/service/resolve/ProductInterfaceResolver.java`
- Create: `.../product/controller/admin/product/ProductResolveController.java`
- Test: `.../product/service/resolve/ProductInterfaceResolverTest.java`

**Interfaces:**
- Consumes: Task 2/3/4 的 `ProductMapper`/`ProductFunctionMapper`/`FuncInterfaceMapper`。
- Produces:
  - `class ResolvedInterface { Long productFunctionId; String funcCode; Long dsInterfaceId; String dsInterfaceCode; Integer priority; boolean isDefault; }`（`@Data`）
  - `@Component class ProductInterfaceResolver { List<ResolvedInterface> resolve(String productCode); ResolvedInterface resolveDefault(String productCode); }`——`resolve`：按 productCode 找产品（不存在抛 `PRODUCT_NOT_EXISTS`）→ 取启用功能 → 每功能取启用绑定接口 → 汇总为 `ResolvedInterface` 列表按 `priority` 升序；`resolveDefault`：`isDefault==true` 优先、再按 priority 最小，空则 null。这是路由里程碑「静态路由匹配」的最简种子（尚不含机构维度/分流/切换链）。
  - HTTP `GET /product/resolve/interfaces?productCode=P000001` → `List<ResolvedInterface>`；`GET /product/resolve/default?productCode=...` → `ResolvedInterface`；权限串 `product:info:query`。

- [ ] **Step 1: 写失败测试（H2，多表造数）**

`ProductInterfaceResolverTest.java`：

```java
package cn.iocoder.yudao.module.product.service.resolve;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductInterfaceResolver.class)
class ProductInterfaceResolverTest extends BaseDbUnitTest {

    @Resource private ProductInterfaceResolver resolver;
    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    private Long product(String code) {
        ProductDO p = new ProductDO();
        p.setProductCode(code); p.setName("产品"); p.setProductType(1); p.setStatus(0);
        productMapper.insert(p);
        return p.getId();
    }
    private Long func(Long productId, String code) {
        ProductFunctionDO f = new ProductFunctionDO();
        f.setFuncCode(code); f.setName("功能"); f.setProductId(productId); f.setStatus(0);
        functionMapper.insert(f);
        return f.getId();
    }
    private void bind(Long funcId, Long ifId, String ifCode, int priority, boolean isDefault) {
        FuncInterfaceDO b = new FuncInterfaceDO();
        b.setProductFunctionId(funcId); b.setDsInterfaceId(ifId); b.setDsInterfaceCode(ifCode);
        b.setPriority(priority); b.setIsDefault(isDefault); b.setStatus(0);
        bindMapper.insert(b);
    }

    @Test
    void resolve_collectsBoundInterfacesSortedByPriority() {
        Long pid = product("P000001");
        Long fid = func(pid, "F000001");
        bind(fid, 200L, "IF000002", 2, false);
        bind(fid, 100L, "IF000001", 1, true);
        List<ResolvedInterface> list = resolver.resolve("P000001");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getDsInterfaceId()).isEqualTo(100L); // priority 1 在前
    }

    @Test
    void resolveDefault_prefersDefaultFlag() {
        Long pid = product("P000002");
        Long fid = func(pid, "F000002");
        bind(fid, 100L, "IF000001", 1, false);
        bind(fid, 200L, "IF000002", 2, true); // 默认但 priority 更大
        ResolvedInterface d = resolver.resolveDefault("P000002");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L); // isDefault 优先于 priority
    }

    @Test
    void resolve_productNotExists_throws() {
        assertServiceException(() -> resolver.resolve("NOPE"), PRODUCT_NOT_EXISTS);
    }

    @Test
    void resolve_ignoresDisabledFunctionAndBinding() {
        Long pid = product("P000003");
        Long enabledFn = func(pid, "F000003");
        bind(enabledFn, 100L, "IF000001", 1, true);
        ProductFunctionDO disabled = new ProductFunctionDO();
        disabled.setFuncCode("F000004"); disabled.setName("停用"); disabled.setProductId(pid); disabled.setStatus(1);
        functionMapper.insert(disabled);
        bind(disabled.getId(), 300L, "IF000003", 1, true);
        List<ResolvedInterface> list = resolver.resolve("P000003");
        assertThat(list).extracting(ResolvedInterface::getDsInterfaceId).containsExactly(100L);
    }

    @Test
    void resolveDefault_noBinding_returnsNull() {
        product("P000005");
        assertThat(resolver.resolveDefault("P000005")).isNull();
    }
}
```

- [ ] **Step 2: 运行验证失败** — 编译失败。

- [ ] **Step 3: 最小实现**

`ResolvedInterface.java`：`@Data` POJO，字段见 Interfaces。

`ProductInterfaceResolver.java`：

```java
package cn.iocoder.yudao.module.product.service.resolve;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;

@Component
public class ProductInterfaceResolver {

    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    public List<ResolvedInterface> resolve(String productCode) {
        ProductDO product = productMapper.selectByProductCode(productCode);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        List<ProductFunctionDO> functions = functionMapper.selectList(new LambdaQueryWrapperX<ProductFunctionDO>()
                .eq(ProductFunctionDO::getProductId, product.getId())
                .eq(ProductFunctionDO::getStatus, 0)); // 仅启用功能
        List<ResolvedInterface> result = new ArrayList<>();
        for (ProductFunctionDO fn : functions) {
            List<FuncInterfaceDO> binds = bindMapper.selectList(new LambdaQueryWrapperX<FuncInterfaceDO>()
                    .eq(FuncInterfaceDO::getProductFunctionId, fn.getId())
                    .eq(FuncInterfaceDO::getStatus, 0)); // 仅启用绑定
            for (FuncInterfaceDO b : binds) {
                ResolvedInterface ri = new ResolvedInterface();
                ri.setProductFunctionId(fn.getId());
                ri.setFuncCode(fn.getFuncCode());
                ri.setDsInterfaceId(b.getDsInterfaceId());
                ri.setDsInterfaceCode(b.getDsInterfaceCode());
                ri.setPriority(b.getPriority() == null ? 0 : b.getPriority());
                ri.setDefault(Boolean.TRUE.equals(b.getIsDefault()));
                result.add(ri);
            }
        }
        result.sort(Comparator.comparingInt(ResolvedInterface::getPriority));
        return result;
    }

    public ResolvedInterface resolveDefault(String productCode) {
        List<ResolvedInterface> all = resolve(productCode);
        return all.stream()
                .min(Comparator.comparing((ResolvedInterface r) -> !r.isDefault()) // isDefault=true 优先
                        .thenComparingInt(ResolvedInterface::getPriority))
                .orElse(null);
    }
}
```

`ProductResolveController.java`：`@RestController @RequestMapping("/product/resolve")`，两个 `@GetMapping`（`/interfaces`、`/default`，`@RequestParam("productCode")`），权限串 `product:info:query`，`success(...)` 包装。

- [ ] **Step 4: 运行验证通过** — `ProductInterfaceResolverTest` 5 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-product
git commit -m "feat(product): 产品→数据源接口解析服务（按功能汇总绑定接口，默认/优先级排序）"
```

---

## P3 完成定义（DoD）

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-product -am test` 全绿，离线（H2）。
2. 产品域四表（product_info / product_function / product_func_interface / product_param）具备完整 CRUD + 归属校验 + 编码 id 派生（P/F 码）+ H2 唯一约束防软删复用。
3. `ProductInterfaceResolver` 能按 productCode 汇总启用功能的启用绑定接口，按默认/优先级排序，供路由里程碑消费。
4. MySQL DDL 并入 `docker/mysql/init/05-product-schema.sql`，运行库 `apiten_product` 可承载。
5. 每任务一个 commit；扁平模块范式；全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续计划（本计划不含）

- **机构里程碑**：org / org_account（AK/SK+白名单+签名）/ org_product（机构产品开通+单价+模板+限额）；机构鉴权（网关五重校验）。
- **路由里程碑**：route_config 三级静态路由（机构产品 > 机构 > 产品默认）→ 动态选源打分 → 切换链兜底；届时消费本域的 `ProductInterfaceResolver`（并扩展为含机构维度）解析出 `dsInterfaceId` 交 P2 的 HTTP 引擎；openapi 的 productCode 路径经路由层解析后设 `ProviderRequest.dsInterfaceId` 走 HTTP。
- 组合产品（combo_product）、产品成本（product_cost，计费里程碑）、脱敏规则运行时执行。
- 计费、流水落库与幂等、限流限额、报表、监控。
- 前端页面（产品管理）与 sys_menu 菜单/按钮权限落库。
