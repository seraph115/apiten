# 征信 API 平台 P0：工程骨架与基础设施 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 yudao-cloud 微服务基座与业务模块脚手架，跑通「网关 → openapi → mock 适配 → 统一 JSON 响应 + Kafka 流水事件」空管道。

**Architecture:** 以 yudao-cloud（Spring Cloud Alibaba）为基座，剔除无关业务模块，新建 apiten-common（统一响应/错误码/雪花ID）与 openapi/adapter/flow 三个业务模块脚手架；数据面调用链 gateway→openapi→adapter 同步，流水经 Kafka 异步到 flow。

**Tech Stack:** Java 21 + Spring Boot 3.x + Spring Cloud Alibaba（Nacos/Sentinel/OpenFeign/Gateway）、MyBatis-Plus、Redis、Kafka、MySQL 8、JUnit 5 + AssertJ + spring-kafka-test。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）

## Global Constraints

- JDK 21（虚拟线程），Spring Boot 3.x（跟随 yudao-cloud JDK17/21 分支版本）。
- 基座：yudao-cloud（https://github.com/YunaiV/yudao-cloud），使用 JDK17/21 + Spring Boot 3 分支（`master-jdk17`，若已合入默认分支则用默认分支）。
- MQ 一律 Kafka（经 yudao MQ 抽象层或 spring-kafka 直用）。
- 单租户模式（`yudao.tenant.enable: false`），商城/CRM/ERP/BPM/支付/会员/公众号等模块不引入。
- 统一响应结构与四段式错误码严格按 spec §4.1.3/§4.1.4；`flowNo` 一律字符串。
- 报表任务模块后续命名为 `yudao-module-apireport`（避免与基座自带 `yudao-module-report` 冲突）——P0 不创建，此处仅锁定命名。
- 新建业务模块包名：`cn.iocoder.yudao.module.{openapi|adapter|flow}`；公共组件包名 `cn.apiten.common`。
- 每个任务结束必须 `git commit`；测试命令统一 `mvn -pl <module> -am test`。

---

### Task 1: 基座导入与瘦身

**Files:**
- Create: 仓库根目录导入 yudao-cloud 全部源码（保留 `yudao-gateway`、`yudao-module-system`、`yudao-module-infra`、`yudao-framework`、`yudao-dependencies` 等基础设施，按基座实际结构）
- Delete: `yudao-module-mall`、`yudao-module-crm`、`yudao-module-erp`、`yudao-module-bpm`、`yudao-module-pay`、`yudao-module-member`、`yudao-module-mp`、`yudao-module-report`、`yudao-module-ai` 等无关模块目录（以基座实际存在者为准）
- Modify: 根 `pom.xml`（`<modules>` 移除被删模块）

**Interfaces:**
- Consumes: 无（首任务）
- Produces: 可编译的基座工程；后续任务在根 `pom.xml` 注册新模块

- [ ] **Step 1: 克隆基座并导入当前仓库**

```bash
cd /Users/seraph/claude-workspace/apiten
git clone --depth 1 -b master-jdk17 https://github.com/YunaiV/yudao-cloud /tmp/yudao-cloud \
  || git clone --depth 1 https://github.com/YunaiV/yudao-cloud /tmp/yudao-cloud
rsync -a --exclude .git /tmp/yudao-cloud/ ./
```

- [ ] **Step 2: 删除无关业务模块并同步根 pom**

```bash
for m in mall crm erp bpm pay member mp report ai; do rm -rf yudao-module-$m; done
```

编辑根 `pom.xml`：从 `<modules>` 中删除上述已删目录对应的 `<module>` 行（仅删除文件中实际存在的行）。

- [ ] **Step 3: 验证整体可编译**

Run: `mvn -T 1C clean install -DskipTests -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 关闭多租户（单租户模式）**

编辑各保留服务的 `application.yaml`（`yudao-module-system-server`、`yudao-module-infra-server`、`yudao-gateway` 中存在该配置者）：

```yaml
yudao:
  tenant:
    enable: false
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: 导入 yudao-cloud 基座并剔除无关业务模块（单租户）"
```

---

### Task 2: 本地中间件 docker-compose

**Files:**
- Create: `docker/docker-compose.yml`
- Create: `docker/mysql/init/01-create-databases.sql`

**Interfaces:**
- Consumes: 无
- Produces: 本地 MySQL(3306)/Redis(6379)/Nacos(8848)/Kafka(9092)；数据库 `apiten_system`、`apiten_infra`、`apiten_openapi`、`apiten_flow`

- [ ] **Step 1: 编写 docker-compose.yml**

```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: apiten123
    volumes:
      - ./mysql/init:/docker-entrypoint-initdb.d
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
  nacos:
    image: nacos/nacos-server:v2.3.2
    ports: ["8848:8848", "9848:9848"]
    environment:
      MODE: standalone
  kafka:
    image: bitnami/kafka:3.7
    ports: ["9092:9092"]
    environment:
      KAFKA_CFG_NODE_ID: 0
      KAFKA_CFG_PROCESS_ROLES: controller,broker
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093
```

- [ ] **Step 2: 编写建库脚本 01-create-databases.sql**

```sql
CREATE DATABASE IF NOT EXISTS `apiten_system` DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS `apiten_infra` DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS `apiten_openapi` DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS `apiten_flow` DEFAULT CHARACTER SET utf8mb4;
```

- [ ] **Step 3: 启动并验证**

Run: `cd docker && docker compose up -d && sleep 30 && docker compose ps`
Expected: 4 个容器均 `running`；`mysql -h127.0.0.1 -uroot -papiten123 -e "show databases"` 能看到 4 个 apiten_* 库；`curl -s http://127.0.0.1:8848/nacos/ | head -1` 返回 HTML。

随后导入基座自带 system/infra 初始化 SQL（位于基座 `sql/mysql/` 目录）到 apiten_system/apiten_infra 库，并将对应服务配置中的数据库名改为 apiten_*。

- [ ] **Step 4: Commit**

```bash
git add docker/
git commit -m "chore: 本地中间件 docker-compose（MySQL/Redis/Nacos/Kafka）"
```

---

### Task 3: apiten-common —— 统一错误码与响应结构

**Files:**
- Create: `apiten-common/pom.xml`
- Create: `apiten-common/src/main/java/cn/apiten/common/api/PlatformErrorCode.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/api/ApiResponse.java`
- Test: `apiten-common/src/test/java/cn/apiten/common/api/ApiResponseTest.java`
- Modify: 根 `pom.xml`（`<modules>` 增加 `apiten-common`）

**Interfaces:**
- Consumes: 无
- Produces:
  - `enum PlatformErrorCode { SUCCESS("0000","成功"), NO_DATA("0001","查无数据"), SIGN_ERROR("1001","签名错误"), PRODUCT_UNAUTHORIZED("1006","产品未授权"), PARAM_MISSING("2001","参数缺失"), BALANCE_INSUFFICIENT("2101","余额不足"), UPSTREAM_ERROR("3001","数据源异常"), CHAIN_EXHAUSTED("3003","切换链耗尽"), SYSTEM_ERROR("3999","系统异常"); String getCode(); String getMsg(); }`
  - `class ApiResponse<T> { String flowNo; String productCode; String code; String msg; boolean charged; long costTime; T data; static <T> ApiResponse<T> of(String flowNo, String productCode, PlatformErrorCode ec, boolean charged, long costTime, T data); }`

- [ ] **Step 1: 创建模块 pom 并注册到根 pom**

`apiten-common/pom.xml`（parent 的 groupId/artifactId/version 以基座根 pom 实际值为准）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>apiten-common</artifactId>
  <dependencies>
    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: 写失败测试**

```java
package cn.apiten.common.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void of_shouldFillAllFields() {
        ApiResponse<String> r = ApiResponse.of("123", "P1001001",
                PlatformErrorCode.SUCCESS, true, 312L, "ok");
        assertThat(r.getFlowNo()).isEqualTo("123");
        assertThat(r.getProductCode()).isEqualTo("P1001001");
        assertThat(r.getCode()).isEqualTo("0000");
        assertThat(r.getMsg()).isEqualTo("成功");
        assertThat(r.isCharged()).isTrue();
        assertThat(r.getCostTime()).isEqualTo(312L);
        assertThat(r.getData()).isEqualTo("ok");
    }

    @Test
    void errorCode_segments() {
        assertThat(PlatformErrorCode.BALANCE_INSUFFICIENT.getCode()).isEqualTo("2101");
        assertThat(PlatformErrorCode.SYSTEM_ERROR.getCode()).isEqualTo("3999");
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -pl apiten-common -am test -q`
Expected: 编译错误（类不存在）。

- [ ] **Step 4: 最小实现**

`PlatformErrorCode.java`：

```java
package cn.apiten.common.api;

public enum PlatformErrorCode {
    SUCCESS("0000", "成功"),
    NO_DATA("0001", "查无数据"),
    SIGN_ERROR("1001", "签名错误"),
    PRODUCT_UNAUTHORIZED("1006", "产品未授权"),
    PARAM_MISSING("2001", "参数缺失"),
    BALANCE_INSUFFICIENT("2101", "余额不足"),
    UPSTREAM_ERROR("3001", "数据源异常"),
    CHAIN_EXHAUSTED("3003", "切换链耗尽"),
    SYSTEM_ERROR("3999", "系统异常");

    private final String code;
    private final String msg;

    PlatformErrorCode(String code, String msg) { this.code = code; this.msg = msg; }
    public String getCode() { return code; }
    public String getMsg() { return msg; }
}
```

`ApiResponse.java`：

```java
package cn.apiten.common.api;

public class ApiResponse<T> {
    private String flowNo;
    private String productCode;
    private String code;
    private String msg;
    private boolean charged;
    private long costTime;
    private T data;

    public static <T> ApiResponse<T> of(String flowNo, String productCode,
            PlatformErrorCode ec, boolean charged, long costTime, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.flowNo = flowNo;
        r.productCode = productCode;
        r.code = ec.getCode();
        r.msg = ec.getMsg();
        r.charged = charged;
        r.costTime = costTime;
        r.data = data;
        return r;
    }

    public String getFlowNo() { return flowNo; }
    public String getProductCode() { return productCode; }
    public String getCode() { return code; }
    public String getMsg() { return msg; }
    public boolean isCharged() { return charged; }
    public long getCostTime() { return costTime; }
    public T getData() { return data; }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl apiten-common -am test -q`
Expected: `Tests run: 2, Failures: 0` / `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add apiten-common pom.xml
git commit -m "feat: apiten-common 统一响应结构与四段式错误码"
```

---

### Task 4: apiten-common —— 雪花 ID 生成器（含时钟回拨保护）

**Files:**
- Create: `apiten-common/src/main/java/cn/apiten/common/id/SnowflakeIdGenerator.java`
- Test: `apiten-common/src/test/java/cn/apiten/common/id/SnowflakeIdGeneratorTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `class SnowflakeIdGenerator { SnowflakeIdGenerator(long workerId /*0-1023*/); long nextId(); String nextIdStr(); }`；时钟回拨 ≤10ms 自旋等待，>10ms 抛 `IllegalStateException`。

- [ ] **Step 1: 写失败测试**

```java
package cn.apiten.common.id;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void nextId_uniqueAndIncreasing() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        long prev = 0;
        for (int i = 0; i < 100_000; i++) {
            long id = g.nextId();
            assertThat(ids.add(id)).isTrue();
            assertThat(id).isGreaterThan(prev);
            prev = id;
        }
    }

    @Test
    void workerId_outOfRange_throws() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextIdStr_returnsDecimalString() {
        String s = new SnowflakeIdGenerator(2).nextIdStr();
        assertThat(s).matches("\\d{15,20}");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl apiten-common -am test -q`
Expected: 编译错误（类不存在）。

- [ ] **Step 3: 最小实现**

```java
package cn.apiten.common.id;

public class SnowflakeIdGenerator {
    private static final long EPOCH = 1735689600000L; // 2025-01-01
    private static final long WORKER_BITS = 10L;
    private static final long SEQ_BITS = 12L;
    private static final long MAX_WORKER = ~(-1L << WORKER_BITS); // 1023
    private static final long SEQ_MASK = ~(-1L << SEQ_BITS);      // 4095
    private static final long BACKWARD_TOLERANCE_MS = 10L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER) {
            throw new IllegalArgumentException("workerId must be 0-" + MAX_WORKER);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            long offset = lastTimestamp - ts;
            if (offset > BACKWARD_TOLERANCE_MS) {
                throw new IllegalStateException("clock moved backwards " + offset + "ms");
            }
            while ((ts = System.currentTimeMillis()) < lastTimestamp) {
                Thread.onSpinWait();
            }
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & SEQ_MASK;
            if (sequence == 0) {
                while ((ts = System.currentTimeMillis()) <= lastTimestamp) {
                    Thread.onSpinWait();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << (WORKER_BITS + SEQ_BITS)) | (workerId << SEQ_BITS) | sequence;
    }

    public String nextIdStr() { return Long.toString(nextId()); }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl apiten-common -am test -q`
Expected: `Tests run: 5, Failures: 0`（含 Task 3 的 2 个）

- [ ] **Step 5: Commit**

```bash
git add apiten-common
git commit -m "feat: 雪花 ID 生成器（时钟回拨保护+节点位校验）"
```

---

### Task 5: yudao-module-openapi 脚手架 —— 同步查询端点（mock 数据）

**Files:**
- Create: `yudao-module-openapi/pom.xml`（如基座为 api/server 双子模块结构，本模块简化为单模块）
- Create: `yudao-module-openapi/src/main/java/cn/iocoder/yudao/module/openapi/OpenApiServerApplication.java`
- Create: `yudao-module-openapi/src/main/java/cn/iocoder/yudao/module/openapi/controller/QueryController.java`
- Create: `yudao-module-openapi/src/main/java/cn/iocoder/yudao/module/openapi/service/QueryOrchestrator.java`
- Create: `yudao-module-openapi/src/main/resources/application.yaml`
- Test: `yudao-module-openapi/src/test/java/cn/iocoder/yudao/module/openapi/controller/QueryControllerTest.java`
- Modify: 根 `pom.xml`（注册模块）

**Interfaces:**
- Consumes: `ApiResponse.of(...)`、`PlatformErrorCode`、`SnowflakeIdGenerator`（Task 3/4）
- Produces:
  - HTTP `POST /openapi/v1/{productCode}/query`，请求体 `Map<String,Object>`，响应 `ApiResponse<Map<String,Object>>`（网关将 `/api/v1/**` 重写为 `/openapi/v1/**`，见 Task 7）
  - `class QueryOrchestrator { ApiResponse<Map<String,Object>> query(String productCode, Map<String,Object> params); }`（Task 6 改为调用 adapter；本任务返回 mock）

- [ ] **Step 1: 创建模块 pom 并注册根 pom**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>yudao-module-openapi</artifactId>
  <dependencies>
    <dependency><groupId>cn.iocoder.cloud</groupId><artifactId>apiten-common</artifactId><version>${revision}</version></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: 写失败测试（MockMvc standalone，不依赖中间件）**

```java
package cn.iocoder.yudao.module.openapi.controller;

import cn.iocoder.yudao.module.openapi.service.QueryOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueryControllerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new QueryController(new QueryOrchestrator())).build();

    @Test
    void query_returnsUnifiedResponseWithFlowNo() throws Exception {
        mvc.perform(post("/openapi/v1/P1001001/query")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"某某公司\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.productCode").value("P1001001"))
                .andExpect(jsonPath("$.flowNo").isString())
                .andExpect(jsonPath("$.data.mock").value(true));
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -pl yudao-module-openapi -am test -q`
Expected: 编译错误（Controller/Orchestrator 不存在）。

- [ ] **Step 4: 最小实现**

`QueryOrchestrator.java`：

```java
package cn.iocoder.yudao.module.openapi.service;

import cn.apiten.common.api.ApiResponse;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.id.SnowflakeIdGenerator;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class QueryOrchestrator {

    private final SnowflakeIdGenerator idGen = new SnowflakeIdGenerator(1);

    public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = Map.of("mock", true, "echo", params);
        return ApiResponse.of(idGen.nextIdStr(), productCode,
                PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
    }
}
```

`QueryController.java`：

```java
package cn.iocoder.yudao.module.openapi.controller;

import cn.apiten.common.api.ApiResponse;
import cn.iocoder.yudao.module.openapi.service.QueryOrchestrator;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1")
public class QueryController {

    private final QueryOrchestrator orchestrator;

    public QueryController(QueryOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping("/{productCode}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String productCode,
            @RequestBody Map<String, Object> params) {
        return orchestrator.query(productCode, params);
    }
}
```

`OpenApiServerApplication.java`：

```java
package cn.iocoder.yudao.module.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OpenApiServerApplication {
    public static void main(String[] args) { SpringApplication.run(OpenApiServerApplication.class, args); }
}
```

`application.yaml`：

```yaml
server:
  port: 48090
spring:
  application:
    name: openapi-server
  threads:
    virtual:
      enabled: true
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl yudao-module-openapi -am test -q`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add yudao-module-openapi pom.xml
git commit -m "feat: openapi 模块脚手架——同步查询端点返回统一响应(mock)"
```

---

### Task 6: yudao-module-adapter 脚手架 —— Provider SPI + openapi 经 Feign 调用

**Files:**
- Create: `yudao-module-adapter/pom.xml`（同 Task 5 结构，依赖 apiten-common/web/nacos-discovery/test）
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/AdapterServerApplication.java`（端口 48091，`spring.application.name: adapter-server`，配置文件同 Task 5 风格）
- Create: `apiten-common/src/main/java/cn/apiten/common/adapter/ProviderRequest.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/adapter/ProviderResponse.java`
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/provider/DataSourceProvider.java`
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/provider/MockProvider.java`
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/controller/InvokeController.java`
- Test: `yudao-module-adapter/src/test/java/cn/iocoder/yudao/module/adapter/provider/MockProviderTest.java`
- Modify: `yudao-module-openapi/pom.xml`（加 `spring-cloud-starter-openfeign` 与 `spring-cloud-starter-loadbalancer`）
- Create: `yudao-module-openapi/src/main/java/cn/iocoder/yudao/module/openapi/client/AdapterClient.java`
- Modify: `yudao-module-openapi/.../service/QueryOrchestrator.java` 与 `QueryControllerTest.java`（见 Step 5）
- Modify: 根 `pom.xml`（注册 yudao-module-adapter）

**Interfaces:**
- Consumes: `PlatformErrorCode`（Task 3）
- Produces:
  - `class ProviderRequest { String productCode; Map<String,Object> params; }`（POJO：getter/setter、无参构造，位于 `cn.apiten.common.adapter`，openapi/adapter 共用）
  - `class ProviderResponse { String rawCode; String platformCode; Map<String,Object> data; }`（同上）
  - `interface DataSourceProvider { String type(); ProviderResponse invoke(ProviderRequest request); }`
  - HTTP `POST /adapter/v1/invoke`（body=ProviderRequest，返回 ProviderResponse）
  - `@FeignClient(name="adapter-server", path="/adapter/v1") interface AdapterClient { @PostMapping("/invoke") ProviderResponse invoke(@RequestBody ProviderRequest request); }`

- [ ] **Step 1: 创建 DTO 与失败测试**

`ProviderRequest.java` / `ProviderResponse.java`：按 Interfaces 定义的普通 POJO。

`MockProviderTest.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MockProviderTest {

    @Test
    void invoke_echoesParamsWithSuccessCode() {
        MockProvider p = new MockProvider();
        ProviderRequest req = new ProviderRequest();
        req.setProductCode("P1001001");
        req.setParams(Map.of("name", "某某公司"));
        ProviderResponse resp = p.invoke(req);
        assertThat(p.type()).isEqualTo("MOCK");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
        assertThat(resp.getRawCode()).isEqualTo("MOCK_OK");
        assertThat(resp.getData()).containsEntry("name", "某某公司");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl yudao-module-adapter -am test -q`
Expected: 编译错误。

- [ ] **Step 3: 最小实现（SPI + MockProvider + InvokeController）**

`DataSourceProvider.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;

public interface DataSourceProvider {
    String type();
    ProviderResponse invoke(ProviderRequest request);
}
```

`MockProvider.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class MockProvider implements DataSourceProvider {

    @Override
    public String type() { return "MOCK"; }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        ProviderResponse resp = new ProviderResponse();
        resp.setRawCode("MOCK_OK");
        resp.setPlatformCode("0000");
        resp.setData(new HashMap<>(request.getParams() == null ? Map.of() : request.getParams()));
        return resp;
    }
}
```

`InvokeController.java`：

```java
package cn.iocoder.yudao.module.adapter.controller;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.provider.DataSourceProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/adapter/v1")
public class InvokeController {

    private final DataSourceProvider provider; // P0 仅 MockProvider；P1 起按数据源类型路由

    public InvokeController(DataSourceProvider provider) { this.provider = provider; }

    @PostMapping("/invoke")
    public ProviderResponse invoke(@RequestBody ProviderRequest request) {
        return provider.invoke(request);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl yudao-module-adapter -am test -q`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: openapi 侧接入 Feign（保持单测离线可跑）**

`AdapterClient.java` 按 Interfaces 定义；`OpenApiServerApplication` 加 `@EnableFeignClients`；`QueryOrchestrator` 改为构造器注入 `org.springframework.beans.factory.ObjectProvider<AdapterClient>`：

```java
public ApiResponse<Map<String, Object>> query(String productCode, Map<String, Object> params) {
    long start = System.currentTimeMillis();
    AdapterClient client = adapterClientProvider.getIfAvailable();
    if (client == null) { // 单测/降级分支
        Map<String, Object> data = Map.of("mock", true, "echo", params);
        return ApiResponse.of(idGen.nextIdStr(), productCode,
                PlatformErrorCode.SUCCESS, true, System.currentTimeMillis() - start, data);
    }
    ProviderRequest req = new ProviderRequest();
    req.setProductCode(productCode);
    req.setParams(params);
    ProviderResponse resp = client.invoke(req);
    PlatformErrorCode ec = "0000".equals(resp.getPlatformCode())
            ? PlatformErrorCode.SUCCESS : PlatformErrorCode.UPSTREAM_ERROR;
    return ApiResponse.of(idGen.nextIdStr(), productCode, ec,
            ec == PlatformErrorCode.SUCCESS, System.currentTimeMillis() - start, resp.getData());
}
```

同步更新 `QueryControllerTest`：`new QueryOrchestrator(emptyObjectProvider())`，其中：

```java
private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyObjectProvider() {
    return new org.springframework.beans.factory.ObjectProvider<T>() {
        @Override public T getObject(Object... args) { throw new UnsupportedOperationException(); }
        @Override public T getIfAvailable() { return null; }
        @Override public T getIfUnique() { return null; }
        @Override public T getObject() { throw new UnsupportedOperationException(); }
    };
}
```

- [ ] **Step 6: 全量测试通过后 Commit**

Run: `mvn -pl yudao-module-openapi,yudao-module-adapter -am test -q`
Expected: 全部 PASS

```bash
git add apiten-common yudao-module-adapter yudao-module-openapi pom.xml
git commit -m "feat: adapter 模块 Provider SPI 与 Mock 实现，openapi 经 Feign 调用"
```

---

### Task 7: 网关路由 —— 端到端空管道验证

**Files:**
- Modify: `yudao-gateway/src/main/resources/application.yaml`（新增路由）

**Interfaces:**
- Consumes: openapi 的 `POST /openapi/v1/{productCode}/query`（Task 5/6）
- Produces: 对外 `POST http://127.0.0.1:48080/api/v1/{productCode}/query`（网关端口以基座实际配置为准）

- [ ] **Step 1: 新增网关路由规则**

在 `spring.cloud.gateway.routes` 中追加：

```yaml
- id: apiten-openapi
  uri: lb://openapi-server
  predicates:
    - Path=/api/v1/**
  filters:
    - RewritePath=/api/v1/(?<segment>.*), /openapi/v1/$\{segment}
```

- [ ] **Step 2: 启动链路并端到端验证（需 Task 2 中间件在跑）**

```bash
mvn -pl yudao-gateway spring-boot:run &
mvn -pl yudao-module-openapi spring-boot:run &
mvn -pl yudao-module-adapter spring-boot:run &
sleep 40
curl -s -X POST http://127.0.0.1:48080/api/v1/P1001001/query \
  -H 'Content-Type: application/json' -d '{"name":"某某公司"}'
```

Expected: 返回 JSON 含 `"code":"0000"`、`"productCode":"P1001001"`、字符串 `flowNo`、`data.name == "某某公司"`（经 adapter MockProvider 回显，而非 `mock:true` 降级分支）。

- [ ] **Step 3: Commit**

```bash
git add yudao-gateway
git commit -m "feat: 网关路由 /api/v1/** 接入 openapi，空管道端到端跑通"
```

---

### Task 8: Kafka 流水事件骨架 —— openapi 发送、flow 消费

**Files:**
- Create: `apiten-common/src/main/java/cn/apiten/common/flow/FlowEvent.java`
- Modify: `yudao-module-openapi/pom.xml`（加 `spring-kafka`）、`QueryOrchestrator.java`（发送事件）
- Create: `yudao-module-flow/pom.xml`（依赖 apiten-common/web/nacos-discovery/spring-kafka；test 依赖 spring-kafka-test、awaitility）
- Create: `yudao-module-flow/src/main/java/cn/iocoder/yudao/module/flow/FlowServerApplication.java`（端口 48092，`spring.application.name: flow-server`）
- Create: `yudao-module-flow/src/main/java/cn/iocoder/yudao/module/flow/consumer/FlowEventConsumer.java`
- Test: `yudao-module-flow/src/test/java/cn/iocoder/yudao/module/flow/consumer/FlowEventConsumerTest.java`
- Modify: 根 `pom.xml`（注册 yudao-module-flow）

**Interfaces:**
- Consumes: `ApiResponse`（Task 3）
- Produces:
  - `class FlowEvent { String flowNo; String productCode; String platformCode; boolean charged; long costTimeMs; long requestTimeEpochMs; }`（POJO：getter/setter、无参构造，JSON 序列化）
  - Kafka topic：`apiten.org-flow`（openapi 生产，flow 消费，group `flow-server`）
  - `class FlowEventConsumer { void onEvent(String json); List<FlowEvent> received(); }`（P0 落内存列表；P6 改落库）

- [ ] **Step 1: 写失败测试（EmbeddedKafka）**

```java
package cn.iocoder.yudao.module.flow.consumer;

import cn.apiten.common.flow.FlowEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "apiten.org-flow",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class FlowEventConsumerTest {

    @Autowired KafkaTemplate<String, String> template;
    @Autowired FlowEventConsumer consumer;

    @Test
    void onEvent_deserializesAndStores() throws Exception {
        FlowEvent e = new FlowEvent();
        e.setFlowNo("123");
        e.setProductCode("P1001001");
        e.setPlatformCode("0000");
        e.setCharged(true);
        e.setCostTimeMs(50);
        e.setRequestTimeEpochMs(System.currentTimeMillis());
        template.send("apiten.org-flow", new ObjectMapper().writeValueAsString(e));

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(consumer.received()).extracting(FlowEvent::getFlowNo).contains("123"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl yudao-module-flow -am test -q`
Expected: 编译错误。

- [ ] **Step 3: 最小实现**

`FlowEvent.java`（apiten-common，POJO 按 Interfaces 定义）。

`FlowEventConsumer.java`：

```java
package cn.iocoder.yudao.module.flow.consumer;

import cn.apiten.common.flow.FlowEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FlowEventConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FlowEvent> received = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = "apiten.org-flow", groupId = "flow-server")
    public void onEvent(String json) throws Exception {
        received.add(mapper.readValue(json, FlowEvent.class));
    }

    public List<FlowEvent> received() { return received; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl yudao-module-flow -am test -q`
Expected: `Tests run: 1, Failures: 0`

- [ ] **Step 5: openapi 侧发送事件（尽力而为，不阻塞响应）**

`QueryOrchestrator` 增加注入 `ObjectProvider<KafkaTemplate<String,String>>` 与 `ObjectMapper mapper = new ObjectMapper()`，在组装 `ApiResponse resp` 之后、返回之前：

```java
KafkaTemplate<String, String> kafka = kafkaProvider.getIfAvailable();
if (kafka != null) {
    FlowEvent e = new FlowEvent();
    e.setFlowNo(resp.getFlowNo());
    e.setProductCode(productCode);
    e.setPlatformCode(resp.getCode());
    e.setCharged(resp.isCharged());
    e.setCostTimeMs(resp.getCostTime());
    e.setRequestTimeEpochMs(start);
    try {
        kafka.send("apiten.org-flow", mapper.writeValueAsString(e));
    } catch (Exception ignore) {
        // P0 尽力而为；P6 引入本地兜底与重试
    }
}
return resp;
```

（`QueryControllerTest` 相应传入 `emptyObjectProvider()`，单测仍离线通过。）

Run: `mvn -pl yudao-module-openapi,yudao-module-adapter,yudao-module-flow -am test -q`
Expected: 全部 PASS。

- [ ] **Step 6: 端到端验证（可选，需 Task 2 中间件在跑）**

启动 gateway/openapi/adapter/flow 四服务，重复 Task 7 的 curl；flow-server 日志中确认收到对应 flowNo 的事件（可临时在 onEvent 加 log 打印）。

- [ ] **Step 7: Commit**

```bash
git add apiten-common yudao-module-openapi yudao-module-flow pom.xml
git commit -m "feat: 流水事件骨架——openapi 发 Kafka，flow 消费(内存暂存)"
```

---

## P0 完成定义（DoD）

1. `mvn -T 1C clean install -DskipTests` 全工程 BUILD SUCCESS。
2. `mvn test` 各新模块单测全绿，且不依赖 docker 中间件。
3. docker compose 四中间件启动后，四服务可启动，`curl POST /api/v1/P1001001/query` 经网关返回统一响应（code=0000、字符串 flowNo、data 回显）。
4. flow-server 能消费到对应 flowNo 的流水事件。
5. 每任务一个 commit，git log 与本计划任务一一对应。

## 后续计划（本计划不含）

P1 数据源管理+HTTP 适配引擎 → P2 产品 → P3 机构+鉴权 → P4 路由引擎 → P5 计费 → P6 流水与幂等 → P7 限流限额 → P8 报表（模块名 yudao-module-apireport）→ P9 监控收尾。各计划在启动前基于当时代码现状编写。
