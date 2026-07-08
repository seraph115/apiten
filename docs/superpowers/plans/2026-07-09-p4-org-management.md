# 征信 API 平台 P4：机构域管理 + 网关五重鉴权 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `yudao-module-org` 模块，落地机构域持久化与管理端 CRUD（机构信息 / 机构账号 AK-SK+白名单+签名 / 机构产品开通+单价+限额+有效期），并实现对外 API 网关的五重鉴权（签名 / IP / 账号 / 机构 / 产品有效期）——由薄网关过滤器缓存请求体、计算摘要、调用 org 模块 `verify` RPC，通过则注入上下文头转发 openapi，失败则直接返回统一错误 JSON。

**Architecture:** 采用「薄网关 + org verify RPC」：全部五重校验逻辑在 `yudao-module-org` 的普通 MVC `OrgAuthService` 中实现，靠 H2 离线单测全覆盖，SK 不出 org 模块；网关（reactive/WebFlux）只做请求体缓存→SHA-256 摘要→`WebClient(lb)` 调用 verify→上下文头注入/错误短路，与现有 `TokenAuthenticationFilter` 的 `WebClient+lbFunction+本地缓存` 范式一致。机构域三表沿用 P1/P3 确立的 yudao 标准 CRUD 纵切范式（DO/Mapper/VO/Service/Controller/ErrorCode/双写 DDL/H2 单测）。机构产品对 product 模块的引用为**松耦合**（只存 `product_id` + `product_code` 快照，不依赖 product 模块代码、无跨库 FK）。SK 用 AES-256 加密落库，密钥来自配置。

**Tech Stack:** Java 21 + Spring Boot 3.5.15；MyBatis-Plus（`BaseDO`/`BaseMapperX`/`LambdaQueryWrapperX`）、yudao 框架（`CommonResult`/`PageResult`/`PageParam`/`BeanUtils.toBean`/`ServiceExceptionUtil.exception`）；JDK 内置 `MessageDigest`(SHA-256)/`Mac`(HmacSHA256)/`Cipher`(AES-GCM) 做签名与加密（**不引第三方**）；Spring Cloud Gateway（WebFlux）+ `WebClient` + `ReactorLoadBalancerExchangeFilterFunction`；H2 内存库单测（`BaseDbUnitTest`）；JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§4.1.2 认证鉴权、§4.1.4 错误码体系（1xxx 段）、§6.4 机构管理、§7 数据模型（机构域）、§8.3 机构开通流程、§8.4 步骤①网关五重校验。

## Global Constraints

- JDK 21；Maven 命令前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`；org 模块测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-org -am test -q`，apiten-common 测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test -q`，必须离线通过（H2，不依赖中间件）。
- **扁平模块范式**：新模块 `yudao-module-org`，包根 `cn.iocoder.yudao.module.org`；模块数字错误码 `cn.iocoder.yudao.module.org.enums.ErrorCodeConstants`，本域占 `1_022_xxx_xxx` 段（org_info=`1_022_001_xxx`、org_account=`1_022_002_xxx`、org_product=`1_022_003_xxx`）。
- **平台字符串码**（对外 API 响应 / 五重鉴权结果）统一用 apiten-common `PlatformErrorCode`（四段式字符串码，§4.1.4）；本期新增 1002/1003/1004/1005/1007（1001/1006 已存在）。
- **表命名**：`org_info`、`org_account`、`org_product`；均含 `BaseDO` 五审计列，单租户（extends `BaseDO` + `@TenantIgnore`，无 `tenant_id`）。
- **运行库**：`apiten_org`（MySQL `127.0.0.1:23306` 本地 compose）；单测用 H2。
- **编码规则**（§7）：机构 `ORG`+6 位序号，**服务端由自增 id 派生**（沿用 P1/P3 修复方案：临时 UUID 占位插入 → 据 id 回填 `ORG%06d`，`@Transactional`；杜绝软删除序号复用），H2 DDL 对编码列加唯一约束。**AK/SK 例外**：AK/SK 为**随机生成**的凭证（非序列派生），AK 唯一，SK 明文仅在创建/重置时返回一次、落库为 AES 密文。
- **模块依赖**：org 模块 pom 依赖 `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant,test}`（同 adapter/product，groupId `cn.iocoder.cloud`）+ `apiten-common`。**不依赖** yudao-module-product（机构产品只存 `product_id`/`product_code` 快照）。
- **服务坐标**：`spring.application.name=org-server`，端口 **48094**（现有：openapi 48090 / adapter 48091 / flow 48092 / product 48093）；datasource `master.url` 指向 `apiten_org`。
- **权限串**：`@ss.hasPermission('org:<kebab-domain>:{create|update|delete|query}')`（菜单落库留待前端里程碑）。verify RPC 为内部端点，路径 `/rpc-api/org-auth/verify`，不需要登录态。
- **AES 密钥**：来自配置 `apiten.org.sk-secret`（生产必须经环境变量覆盖，示例默认 `apiten-default-dev-key`）；同一密钥被 `OrgAccountServiceImpl`（加密落库）与 `OrgAuthServiceImpl`（解密验签）共用。
- **DDL 双写**：MySQL DDL 写到 `docker/mysql/init/06-org-schema.sql`（新建，供 apiten_org 运行库）；H2 DDL 到 `yudao-module-org/src/test/resources/sql/create_tables.sql` + `clean.sql`。
- 每任务一个 `git commit`（仅 add 改动文件，不用 `-A`）。
- **不在本期范围**：billing_template / template_tier / charge_code_rule 表（计费里程碑）；billing_account / recharge_record / finance_flow / adjust_record（账务里程碑）；限额/并发的**运行时 enforce**（本期只存字段，限流里程碑执行）；幂等（bizSeqNo）；密钥轮换新旧重叠期（本期支持生成/重置/停用，不做重叠期）；授权书编号校验（1008，依赖 product.need_auth_no 跨模块，产品上下文可用时再做）；机构用户映射（org_user/org_user_mapping）；合并对账；config 快照版本化发布（org verify 本期走 DB + 短 TTL 缓存留待路由/配置里程碑，本期先直查 DB）；前端页面与 sys_menu 菜单/按钮权限落库。

---

### Task 1: yudao-module-org 模块与持久化基建

**Files:**
- Create: `yudao-module-org/pom.xml`
- Create: `yudao-module-org/src/main/java/cn/iocoder/yudao/module/org/OrgServerApplication.java`
- Create: `yudao-module-org/src/main/resources/application.yaml`、`application-local.yaml`、`application-dev.yaml`
- Create: `yudao-module-org/src/test/resources/application-unit-test.yaml`、`logback.xml`、`sql/create_tables.sql`、`sql/clean.sql`
- Create: `yudao-module-org/src/test/java/cn/iocoder/yudao/module/org/DbHarnessSmokeTest.java`
- Create: `docker/mysql/init/06-org-schema.sql`（建库 + 后续任务追加建表）
- Modify: 根 `pom.xml`（`<modules>` 增加 `yudao-module-org`）

**Interfaces:**
- Consumes: 无（新模块）。
- Produces: 可离线运行的 `BaseDbUnitTest` 持久化基座；运行库 `apiten_org`；后续任务的 DO/Mapper/Service 落于此。

- [ ] **Step 1: 建模块 pom + 注册根 pom**

`yudao-module-org/pom.xml`：parent 与依赖照搬 `yudao-module-product/pom.xml` 现状（`sed -n '/<parent>/,/<\/dependencies>/p' yudao-module-product/pom.xml` 抄坐标，仅把 `artifactId` 改为 `yudao-module-org`、`name`/`description` 改为机构模块；含 `apiten-common` + `yudao-spring-boot-starter-{mybatis,web,security,biz-tenant}` + `test` starter）。根 `pom.xml` `<modules>` 在 `yudao-module-product` 之后追加 `<module>yudao-module-org</module>`。

- [ ] **Step 2: 运行库 + 服务配置**

`docker/mysql/init/06-org-schema.sql` 顶部：

```sql
-- apiten 机构域建表（apiten_org 运行库）
CREATE DATABASE IF NOT EXISTS `apiten_org` DEFAULT CHARACTER SET utf8mb4;
USE `apiten_org`;
```

对运行容器立即建库：`docker exec apiten-mysql-1 mysql -uroot -papiten123 -e "CREATE DATABASE IF NOT EXISTS \`apiten_org\` DEFAULT CHARACTER SET utf8mb4;"`

`application.yaml`/`application-local.yaml`/`application-dev.yaml`：照搬 `yudao-module-product` 对应文件，改三处——`spring.application.name: org-server`；`server.port: 48094`；`spring.datasource.dynamic.datasource.master.url` 库名改 `apiten_org`（`jdbc:mysql://127.0.0.1:23306/apiten_org?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true`）。在 `application.yaml` 顶层追加机构鉴权配置段：

```yaml
apiten:
  org:
    sk-secret: ${APITEN_ORG_SK_SECRET:apiten-default-dev-key} # SK 的 AES 加密密钥，生产必须用环境变量覆盖
    timestamp-window-ms: 300000 # 签名时间戳窗口 ±5 分钟
```

`OrgServerApplication.java`：照搬 `ProductServerApplication`，改包名与类名（`@SpringBootApplication`，`main` 调 `SpringApplication.run(OrgServerApplication.class, args)`）。

- [ ] **Step 3: 单测资源（H2 基座）**

照搬 `yudao-module-product/src/test/resources/` 的 `application-unit-test.yaml`（`yudao.info.base-package` 改 `cn.iocoder.yudao.module.org`）、`logback.xml`；`sql/create_tables.sql` 先放一张 smoke 表（结构同 P3 的 `product_db_harness_smoke`，表名 `org_db_harness_smoke`：`"id" bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "name" varchar(64)`）；`sql/clean.sql` 放 `DELETE FROM "org_db_harness_smoke";`。

- [ ] **Step 4: 冒烟测试**

`DbHarnessSmokeTest.java`：照搬 P3 product 的 `DbHarnessSmokeTest`（`extends BaseDbUnitTest`；注意 `BaseDbUnitTest` 不装配 `JdbcTemplateAutoConfiguration`，须 `new JdbcTemplate(dataSource)` 手动构造），插入/计数 `org_db_harness_smoke`：

```java
package cn.iocoder.yudao.module.org;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import jakarta.annotation.Resource;
import static org.assertj.core.api.Assertions.assertThat;

class DbHarnessSmokeTest extends BaseDbUnitTest {

    @Resource
    private DataSource dataSource;

    @Test
    void insertAndCount() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"org_db_harness_smoke\" (\"name\") VALUES (?)", "smoke");
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM \"org_db_harness_smoke\"", Integer.class);
        assertThat(cnt).isEqualTo(1);
    }
}
```

- [ ] **Step 5: 运行验证**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-org -am test -q`
Expected：先编译失败（缺依赖/资源）；补齐后 `Tests run: 1, Failures: 0`，离线。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-org docker/mysql/init/06-org-schema.sql pom.xml
git commit -m "chore(org): 新建 yudao-module-org 模块与 MyBatis-Plus/H2 单测基座，apiten_org 库"
```

---

### Task 2: apiten-common 扩充——加解密/签名工具 + 平台错误码 + 鉴权 DTO

**Files:**
- Create: `apiten-common/src/main/java/cn/apiten/common/crypto/CryptoSignatures.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/crypto/AesCipher.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/org/OrgAuthVerifyReqDTO.java`
- Create: `apiten-common/src/main/java/cn/apiten/common/org/OrgAuthVerifyRespDTO.java`
- Modify: `apiten-common/src/main/java/cn/apiten/common/api/PlatformErrorCode.java`
- Test: `apiten-common/src/test/java/cn/apiten/common/crypto/CryptoSignaturesTest.java`
- Test: `apiten-common/src/test/java/cn/apiten/common/crypto/AesCipherTest.java`

**Interfaces:**
- Consumes: JDK `java.security.MessageDigest`、`javax.crypto.Mac`/`Cipher`/`spec.*`。
- Produces:
  - `class CryptoSignatures { static String sha256Hex(String body); static String hmacSha256Hex(String key, String message); static String buildSignPayload(String appKey, String timestamp, String nonce, String bodyDigest); }`——`sha256Hex(null)` 等价 `sha256Hex("")`；`buildSignPayload` 返回 `appKey + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigest`（**规范签名串，客户端 SDK 与平台必须一致**）。
  - `class AesCipher { static String encrypt(String plaintext, String secretKey); static String decrypt(String cipherBase64, String secretKey); }`——AES-256-GCM，密钥由 `secretKey` 经 SHA-256 派生为 32 字节；随机 12 字节 IV 前置后整体 Base64；`decrypt(encrypt(x))==x`。
  - `PlatformErrorCode` 新增枚举值：`TIMESTAMP_INVALID("1002","时间戳超窗或重放")`、`IP_FORBIDDEN("1003","IP不在白名单")`、`ACCOUNT_DISABLED("1004","账号停用")`、`ORG_DISABLED("1005","机构停用")`、`PRODUCT_EXPIRED("1007","产品已停用或过期")`。
  - `class OrgAuthVerifyReqDTO { String appKey; String timestamp; String nonce; String signature; String bodyDigest; String productCode; String clientIp; String flowNo; + getters/setters }`。
  - `class OrgAuthVerifyRespDTO { boolean pass; String platformCode; String msg; Long orgId; Long accountId; String orgCode; + getters/setters + static OrgAuthVerifyRespDTO pass(Long orgId, Long accountId, String orgCode); + static OrgAuthVerifyRespDTO fail(PlatformErrorCode ec); }`。

- [ ] **Step 1: 写失败测试**

`CryptoSignaturesTest.java`：

```java
package cn.apiten.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CryptoSignaturesTest {

    @Test
    void sha256Hex_knownVector() {
        // echo -n "abc" | sha256sum
        assertThat(CryptoSignatures.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_nullEqualsEmpty() {
        assertThat(CryptoSignatures.sha256Hex(null)).isEqualTo(CryptoSignatures.sha256Hex(""));
    }

    @Test
    void hmacSha256Hex_knownVector() {
        // HMAC-SHA256(key="key", msg="The quick brown fox jumps over the lazy dog")
        assertThat(CryptoSignatures.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void buildSignPayload_isNewlineJoined() {
        assertThat(CryptoSignatures.buildSignPayload("AK1", "1700000000000", "n1", "dig"))
                .isEqualTo("AK1\n1700000000000\nn1\ndig");
    }
}
```

`AesCipherTest.java`：

```java
package cn.apiten.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AesCipherTest {

    @Test
    void roundTrip() {
        String key = "apiten-test-key";
        String plain = "sk_9f8e7d6c5b4a3210";
        String cipher = AesCipher.encrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(AesCipher.decrypt(cipher, key)).isEqualTo(plain);
    }

    @Test
    void randomIv_differentCiphertextsSamePlaintext() {
        String key = "k";
        assertThat(AesCipher.encrypt("same", key)).isNotEqualTo(AesCipher.encrypt("same", key));
    }

    @Test
    void wrongKey_fails() {
        String cipher = AesCipher.encrypt("secret", "key-A");
        assertThatThrownBy(() -> AesCipher.decrypt(cipher, "key-B")).isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test -q`
Expected：编译失败（类不存在）。

- [ ] **Step 3: 最小实现**

`CryptoSignatures.java`：

```java
package cn.apiten.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CryptoSignatures {

    private CryptoSignatures() {}

    public static String sha256Hex(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sha256 失败", e);
        }
    }

    public static String hmacSha256Hex(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmac 失败", e);
        }
    }

    public static String buildSignPayload(String appKey, String timestamp, String nonce, String bodyDigest) {
        return appKey + "\n" + timestamp + "\n" + nonce + "\n" + bodyDigest;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
```

`AesCipher.java`：

```java
package cn.apiten.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class AesCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesCipher() {}

    public static String encrypt(String plaintext, String secretKey) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secretKey), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public static String decrypt(String cipherBase64, String secretKey) {
        try {
            byte[] in = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = Arrays.copyOfRange(in, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(in, IV_LEN, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secretKey), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    private static SecretKeySpec deriveKey(String secretKey) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
```

`PlatformErrorCode.java`：在现有枚举常量中，`SIGN_ERROR` 之后补入五个（保持四段式字符串码）：

```java
    SIGN_ERROR("1001", "签名错误"),
    TIMESTAMP_INVALID("1002", "时间戳超窗或重放"),
    IP_FORBIDDEN("1003", "IP不在白名单"),
    ACCOUNT_DISABLED("1004", "账号停用"),
    ORG_DISABLED("1005", "机构停用"),
    PRODUCT_UNAUTHORIZED("1006", "产品未授权"),
    PRODUCT_EXPIRED("1007", "产品已停用或过期"),
```

`OrgAuthVerifyReqDTO.java`：8 个字段（见 Interfaces），每个 `private` + 标准 getter/setter（无 Lombok 依赖，apiten-common 保持零框架依赖，手写访问器）。

`OrgAuthVerifyRespDTO.java`：

```java
package cn.apiten.common.org;

import cn.apiten.common.api.PlatformErrorCode;

public class OrgAuthVerifyRespDTO {
    private boolean pass;
    private String platformCode;
    private String msg;
    private Long orgId;
    private Long accountId;
    private String orgCode;

    public static OrgAuthVerifyRespDTO pass(Long orgId, Long accountId, String orgCode) {
        OrgAuthVerifyRespDTO r = new OrgAuthVerifyRespDTO();
        r.pass = true;
        r.platformCode = PlatformErrorCode.SUCCESS.getCode();
        r.msg = PlatformErrorCode.SUCCESS.getMsg();
        r.orgId = orgId;
        r.accountId = accountId;
        r.orgCode = orgCode;
        return r;
    }

    public static OrgAuthVerifyRespDTO fail(PlatformErrorCode ec) {
        OrgAuthVerifyRespDTO r = new OrgAuthVerifyRespDTO();
        r.pass = false;
        r.platformCode = ec.getCode();
        r.msg = ec.getMsg();
        return r;
    }

    public boolean isPass() { return pass; }
    public void setPass(boolean pass) { this.pass = pass; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test -q`
Expected：`CryptoSignaturesTest` 4 + `AesCipherTest` 3 全过（含既有 `ApiResponseTest`/`SnowflakeIdGeneratorTest` 保持绿）。

- [ ] **Step 5: Commit**

```bash
git add apiten-common/src
git commit -m "feat(common): AES/HMAC/SHA256 加解密签名工具 + 平台码1002-1007 + 机构鉴权DTO"
```

---

### Task 3: 机构信息 org_info CRUD

**Files:**
- Create: `.../org/dal/dataobject/org/OrgDO.java`
- Create: `.../org/dal/mysql/org/OrgMapper.java`
- Create: `.../org/controller/admin/org/vo/{OrgSaveReqVO,OrgPageReqVO,OrgRespVO}.java`
- Create: `.../org/service/org/{OrgService,OrgServiceImpl}.java`
- Create: `.../org/controller/admin/org/OrgController.java`
- Create: `.../org/enums/ErrorCodeConstants.java`
- Test: `.../org/service/org/OrgServiceImplTest.java`
- Modify: `docker/mysql/init/06-org-schema.sql`、`.../test/resources/sql/create_tables.sql`、`clean.sql`
- （`.../org/...` = `yudao-module-org/src/main/java/cn/iocoder/yudao/module/org/...`）

**Interfaces:**
- Consumes: Task 1 基座。
- Produces:
  - `class OrgDO extends BaseDO { Long id; String orgCode; String name; String unifiedSocialCreditCode; String contactPerson; String contactPhone; Integer status; String businessOwner; String remark; }`
  - `interface OrgService { Long createOrg(OrgSaveReqVO); void updateOrg(OrgSaveReqVO); void deleteOrg(Long id); OrgDO getOrg(Long id); OrgDO getOrgByCode(String orgCode); PageResult<OrgDO> getOrgPage(OrgPageReqVO); List<OrgDO> getSimpleList(); }`
  - HTTP：`/org/info/{create,update,delete,get,page,simple-list}`；权限串 `org:info:*`
  - `ErrorCodeConstants.ORG_NOT_EXISTS = new ErrorCode(1_022_001_000, "机构不存在")`

- [ ] **Step 1: ErrorCode + DDL**

`.../org/enums/ErrorCodeConstants.java`：

```java
package cn.iocoder.yudao.module.org.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** org 模块错误码，占用 1-022-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 机构信息 1-022-001-xxx ==========
    ErrorCode ORG_NOT_EXISTS = new ErrorCode(1_022_001_000, "机构不存在");
}
```

MySQL DDL 追加到 `docker/mysql/init/06-org-schema.sql`：

```sql
CREATE TABLE `org_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_code` varchar(32) NOT NULL COMMENT '机构编码 ORG+序号',
  `name` varchar(128) NOT NULL COMMENT '机构名称',
  `unified_social_credit_code` varchar(32) NULL DEFAULT '' COMMENT '统一社会信用代码',
  `contact_person` varchar(64) NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` varchar(32) NULL DEFAULT '' COMMENT '联系方式',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `business_owner` varchar(64) NULL DEFAULT '' COMMENT '业务归属',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_org_code` (`org_code`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构信息表';
```

H2 DDL 追加到 `create_tables.sql`：

```sql
CREATE TABLE IF NOT EXISTS "org_info" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "org_code" varchar(32) NOT NULL,
    "name" varchar(128) NOT NULL,
    "unified_social_credit_code" varchar(32) DEFAULT '',
    "contact_person" varchar(64) DEFAULT '',
    "contact_phone" varchar(32) DEFAULT '',
    "status" tinyint NOT NULL DEFAULT 0,
    "business_owner" varchar(64) DEFAULT '',
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_org_info_code" UNIQUE ("org_code")
) COMMENT '机构信息表';
```

`clean.sql` 追加：`DELETE FROM "org_info";`

- [ ] **Step 2: 写失败测试**

`OrgServiceImplTest.java`（结构、断言风格、`assertServiceException` 导入照搬 P3 `ProductServiceImplTest`）：

```java
package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgServiceImpl.class)
class OrgServiceImplTest extends BaseDbUnitTest {

    @Resource
    private OrgServiceImpl service;

    private OrgSaveReqVO newReq(String name) {
        OrgSaveReqVO vo = new OrgSaveReqVO();
        vo.setName(name);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesOrgCode() {
        Long id = service.createOrg(newReq("某某银行"));
        OrgDO db = service.getOrg(id);
        assertThat(db.getOrgCode()).matches("ORG\\d{6}");
        assertThat(db.getName()).isEqualTo("某某银行");
    }

    @Test
    void getByCode_returnsOrg() {
        Long id = service.createOrg(newReq("某某小贷"));
        String code = service.getOrg(id).getOrgCode();
        assertThat(service.getOrgByCode(code).getId()).isEqualTo(id);
    }

    @Test
    void update_notExists_throws() {
        OrgSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateOrg(upd), ORG_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createOrg(newReq("待删"));
        service.deleteOrg(id);
        assertThat(service.getOrg(id)).isNull();
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        service.createOrg(newReq("A"));
        Long id2 = service.createOrg(newReq("B"));
        String c2 = service.getOrg(id2).getOrgCode();
        service.deleteOrg(id2);
        Long id3 = service.createOrg(newReq("C"));
        String c3 = service.getOrg(id3).getOrgCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("ORG\\d{6}");
    }

    @Test
    void page_filtersByName() {
        service.createOrg(newReq("工商银行"));
        service.createOrg(newReq("建设银行"));
        OrgPageReqVO q = new OrgPageReqVO();
        q.setName("工商");
        PageResult<OrgDO> page = service.getOrgPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行验证失败** — `mvn -pl yudao-module-org -am test -q` 编译失败。

- [ ] **Step 4: 最小实现**

`OrgDO.java`：`@TableName("org_info")` + `@KeySequence("org_info_seq")` + `@Data` + `@TenantIgnore`，字段见 Interfaces（写法照搬 P3 `ProductDO`）。

`OrgMapper.java`（`@Mapper extends BaseMapperX<OrgDO>`；`selectPage(OrgPageReqVO)` 用 `LambdaQueryWrapperX` 按 `name(like)`/`status` 过滤 + `orderByDesc(OrgDO::getId)`；`selectByOrgCode(String)` = `selectOne(OrgDO::getOrgCode, code)`；**不含** `selectMaxId`——编码 id 派生；写法同 P3 `ProductMapper`）。

VO 三件：`OrgSaveReqVO`（`id`；`name` `@NotEmpty`；`unifiedSocialCreditCode`；`contactPerson`；`contactPhone`；`status` `@NotNull`；`businessOwner`；`remark`）、`OrgPageReqVO extends PageParam`（`@EqualsAndHashCode(callSuper=true)`；`name`/`status`）、`OrgRespVO`（镜像 DO 全字段 + `LocalDateTime createTime`）。类注解与写法与 P3 三个 VO 一致，仅字段替换。

`OrgService`/`OrgServiceImpl`：接口见 Interfaces；Impl 结构照搬 P3 `ProductServiceImpl`——`createOrg` 用 id 派生编码：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createOrg(OrgSaveReqVO reqVO) {
    OrgDO org = BeanUtils.toBean(reqVO, OrgDO.class);
    org.setId(null);
    org.setOrgCode(java.util.UUID.randomUUID().toString().replace("-", "")); // 临时唯一占位(32位)
    orgMapper.insert(org);
    org.setOrgCode(String.format("ORG%06d", org.getId()));
    orgMapper.updateById(org);
    return org.getId();
}
```

`updateOrg` 校验存在（抛 `ORG_NOT_EXISTS`）后 `setOrgCode(null)` 再 `updateById`（编码不可改）；`getOrgByCode` 调 `selectByOrgCode`；`getSimpleList` 全部按 id 倒序。`import org.springframework.transaction.annotation.Transactional;`。

`OrgController.java`：`/org/info` 五端点 + `simple-list`，权限串 `org:info:*`，写法照搬 P3 `ProductController`（`BeanUtils.toBean` 转 RespVO）。

- [ ] **Step 5: 运行验证通过** — `OrgServiceImplTest` 6 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-org docker/mysql/init/06-org-schema.sql
git commit -m "feat(org): 机构信息 org_info CRUD（编码 id 派生+按编码查询）"
```

---

### Task 4: 机构账号 org_account CRUD（AK/SK 生成 + AES 落库 + IP 白名单 + 重置密钥）

**Files:**
- Create: `.../org/dal/dataobject/org/OrgAccountDO.java`
- Create: `.../org/dal/mysql/org/OrgAccountMapper.java`
- Create: `.../org/controller/admin/org/vo/{OrgAccountSaveReqVO,OrgAccountPageReqVO,OrgAccountRespVO}.java`
- Create: `.../org/service/org/{OrgAccountService,OrgAccountServiceImpl}.java`
- Create: `.../org/controller/admin/org/OrgAccountController.java`
- Modify: `.../org/enums/ErrorCodeConstants.java`
- Test: `.../org/service/org/OrgAccountServiceImplTest.java`
- Modify: DDL 三处

**Interfaces:**
- Consumes: Task 3 `OrgMapper`（校验 `orgId` 存在 → `ORG_NOT_EXISTS`）；Task 2 `AesCipher`。
- Produces:
  - `class OrgAccountDO extends BaseDO { Long id; Long orgId; String accountName; String appKey; String secretKeyCipher; Integer keyStatus; String ipWhitelist; String callbackUrl; String signAlgorithm; LocalDateTime expireTime; Integer concurrencyLimit; Integer dailyLimit; Integer monthlyLimit; Integer status; String remark; }`（`secretKeyCipher` 为 AES 密文；`keyStatus`：0有效 1作废；`status`：0启用 1停用）
  - `interface OrgAccountService { Long createAccount(OrgAccountSaveReqVO); void updateAccount(OrgAccountSaveReqVO); void deleteAccount(Long id); OrgAccountDO getAccount(Long id); OrgAccountDO getAccountByAppKey(String appKey); String resetSecret(Long id); PageResult<OrgAccountDO> getAccountPage(OrgAccountPageReqVO); List<OrgAccountDO> getListByOrgId(Long orgId); }`——`createAccount` 返回 id；对外首个明文 SK 由 Controller 紧接着调 `resetSecret(id)` 取得（见实现要点）。
  - HTTP：`/org/account/{create,update,delete,get,page,list-by-org,reset-secret}`；权限串 `org:account:*`
  - `ErrorCodeConstants.ORG_ACCOUNT_NOT_EXISTS = new ErrorCode(1_022_002_000, "机构账号不存在")`

MySQL DDL：

```sql
CREATE TABLE `org_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '所属机构ID',
  `account_name` varchar(128) NULL DEFAULT '' COMMENT '账号名称',
  `app_key` varchar(64) NOT NULL COMMENT '访问密钥 AK',
  `secret_key_cipher` varchar(512) NOT NULL COMMENT 'SK 密文(AES)',
  `key_status` tinyint NOT NULL DEFAULT 0 COMMENT '密钥状态：0有效 1作废',
  `ip_whitelist` varchar(512) NULL DEFAULT '' COMMENT 'IP白名单(逗号分隔,支持CIDR段)',
  `callback_url` varchar(256) NULL DEFAULT '' COMMENT '回调地址',
  `sign_algorithm` varchar(32) NOT NULL DEFAULT 'HMAC-SHA256' COMMENT '签名算法',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '账号有效期(空为长期)',
  `concurrency_limit` int NULL DEFAULT NULL COMMENT '账号级最大并发',
  `daily_limit` int NULL DEFAULT NULL COMMENT '账号级日调用量上限',
  `monthly_limit` int NULL DEFAULT NULL COMMENT '账号级月调用量上限',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_app_key` (`app_key`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构账号表';
```

H2 DDL（同风格：双引号、`GENERATED BY DEFAULT AS IDENTITY`、`bit ... DEFAULT FALSE`、`datetime`/`int` 可空列不加 DEFAULT，加 `CONSTRAINT "uk_org_account_app_key" UNIQUE ("app_key")`）：

```sql
CREATE TABLE IF NOT EXISTS "org_account" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "org_id" bigint NOT NULL,
    "account_name" varchar(128) DEFAULT '',
    "app_key" varchar(64) NOT NULL,
    "secret_key_cipher" varchar(512) NOT NULL,
    "key_status" tinyint NOT NULL DEFAULT 0,
    "ip_whitelist" varchar(512) DEFAULT '',
    "callback_url" varchar(256) DEFAULT '',
    "sign_algorithm" varchar(32) NOT NULL DEFAULT 'HMAC-SHA256',
    "expire_time" datetime DEFAULT NULL,
    "concurrency_limit" int DEFAULT NULL,
    "daily_limit" int DEFAULT NULL,
    "monthly_limit" int DEFAULT NULL,
    "status" tinyint NOT NULL DEFAULT 0,
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id"),
    CONSTRAINT "uk_org_account_app_key" UNIQUE ("app_key")
) COMMENT '机构账号表';
```

`clean.sql` 追加：`DELETE FROM "org_account";`

**AK/SK 生成 + 明文回传约定（写入 `OrgAccountServiceImpl`）：**
- AK = `"AK" + 去横线UUID`（唯一）；SK 明文 = 两段去横线UUID 拼接（64 字符高熵随机串）。
- 落库：`secretKeyCipher = AesCipher.encrypt(skPlain, skSecret)`，`skSecret` 由 `@Value("${apiten.org.sk-secret:apiten-default-dev-key}")` 注入；明文 **不落库**。
- **首个对外明文 SK 的回传**：为不破坏“返回 id”的 CRUD 范式，`createAccount` 落一个随机 SK 密文后返回 id；**Controller create 流程**紧接着 `String sk = service.resetSecret(id)` 生成并返回首个对外明文，装入 `OrgAccountRespVO.secretKey`。`resetSecret(id)`：校验存在→生成新明文→`AesCipher.encrypt`→`updateById`→返回明文。明文仅创建/重置这两个响应携带，其余接口 `secretKey` 为 null。

- [ ] **Step 1: ErrorCode + DDL** — 追加 `ORG_ACCOUNT_NOT_EXISTS = new ErrorCode(1_022_002_000, "机构账号不存在")`；建表 DDL 三处（MySQL + H2 + clean）。

- [ ] **Step 2: 写失败测试**

`OrgAccountServiceImplTest.java`（先经 `OrgMapper` 造真实父机构；`skSecret` 用 `@TestPropertySource` 注入固定值使 `@Value` 生效）：

```java
package cn.iocoder.yudao.module.org.service.org;

import cn.apiten.common.crypto.AesCipher;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_ACCOUNT_NOT_EXISTS;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgAccountServiceImpl.class)
@TestPropertySource(properties = "apiten.org.sk-secret=unit-test-key")
class OrgAccountServiceImplTest extends BaseDbUnitTest {

    @Resource private OrgAccountServiceImpl service;
    @Resource private OrgMapper orgMapper;

    private Long newOrg() {
        OrgDO o = new OrgDO();
        o.setOrgCode("ORG000001"); o.setName("机构"); o.setStatus(0);
        orgMapper.insert(o);
        return o.getId();
    }

    private OrgAccountSaveReqVO req(Long orgId) {
        OrgAccountSaveReqVO vo = new OrgAccountSaveReqVO();
        vo.setOrgId(orgId);
        vo.setAccountName("默认账号");
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesAkAndEncryptsSk() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        OrgAccountDO db = service.getAccount(id);
        assertThat(db.getAppKey()).startsWith("AK").hasSizeGreaterThan(10);
        assertThat(db.getSecretKeyCipher()).isNotBlank();
        assertThat(AesCipher.decrypt(db.getSecretKeyCipher(), "unit-test-key")).isNotBlank();
    }

    @Test
    void create_parentOrgNotExists_throws() {
        assertServiceException(() -> service.createAccount(req(99999L)), ORG_NOT_EXISTS);
    }

    @Test
    void getByAppKey_returnsAccount() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        String ak = service.getAccount(id).getAppKey();
        assertThat(service.getAccountByAppKey(ak).getId()).isEqualTo(id);
    }

    @Test
    void resetSecret_changesCipher_returnsNewPlain() {
        Long orgId = newOrg();
        Long id = service.createAccount(req(orgId));
        String oldCipher = service.getAccount(id).getSecretKeyCipher();
        String newPlain = service.resetSecret(id);
        assertThat(newPlain).isNotBlank();
        String newCipher = service.getAccount(id).getSecretKeyCipher();
        assertThat(newCipher).isNotEqualTo(oldCipher);
        assertThat(AesCipher.decrypt(newCipher, "unit-test-key")).isEqualTo(newPlain);
    }

    @Test
    void resetSecret_notExists_throws() {
        assertServiceException(() -> service.resetSecret(99999L), ORG_ACCOUNT_NOT_EXISTS);
    }

    @Test
    void listByOrgId_filters() {
        Long orgId = newOrg();
        service.createAccount(req(orgId));
        service.createAccount(req(orgId));
        assertThat(service.getListByOrgId(orgId)).hasSize(2);
    }
}
```

- [ ] **Step 3: 运行验证失败** — 编译失败。

- [ ] **Step 4: 最小实现**

`OrgAccountDO.java`：`@TableName("org_account")` + `@KeySequence("org_account_seq")` + `@Data` + `@TenantIgnore`，字段见 Interfaces（`expireTime` 为 `java.time.LocalDateTime`）。

`OrgAccountMapper.java`（`@Mapper extends BaseMapperX<OrgAccountDO>`；`selectPage` 按 `orgId`/`appKey(eq)`/`status` 过滤 + `orderByDesc(id)`；`selectByAppKey(String)` = `selectOne(OrgAccountDO::getAppKey, appKey)`；`selectListByOrgId(Long)` = `selectList(OrgAccountDO::getOrgId, orgId)`）。

VO 三件：
- `OrgAccountSaveReqVO`（`id`；`orgId` `@NotNull`；`accountName`；`ipWhitelist`；`callbackUrl`；`signAlgorithm`；`expireTime`(LocalDateTime)；`concurrencyLimit`；`dailyLimit`；`monthlyLimit`；`status` `@NotNull`；`remark`。**不含** appKey/secretKey——AK/SK 由服务端生成）。
- `OrgAccountPageReqVO extends PageParam`（`orgId`/`appKey`/`status`）。
- `OrgAccountRespVO`（镜像 DO 除 `secretKeyCipher`；**新增** `String secretKey`——仅创建/重置响应填明文，其余接口为 null；`LocalDateTime createTime`）。

`OrgAccountServiceImpl` 关键实现：

```java
@Value("${apiten.org.sk-secret:apiten-default-dev-key}")
private String skSecret;

@Resource private OrgAccountMapper accountMapper;
@Resource private OrgMapper orgMapper;

@Override
public Long createAccount(OrgAccountSaveReqVO reqVO) {
    validateOrgExists(reqVO.getOrgId());
    OrgAccountDO account = BeanUtils.toBean(reqVO, OrgAccountDO.class);
    account.setId(null);
    account.setAppKey(generateAppKey());
    account.setSecretKeyCipher(AesCipher.encrypt(generateSecret(), skSecret));
    account.setKeyStatus(0);
    if (account.getSignAlgorithm() == null) {
        account.setSignAlgorithm("HMAC-SHA256");
    }
    accountMapper.insert(account);
    return account.getId();
}

@Override
public String resetSecret(Long id) {
    if (accountMapper.selectById(id) == null) {
        throw exception(ORG_ACCOUNT_NOT_EXISTS);
    }
    String skPlain = generateSecret();
    OrgAccountDO upd = new OrgAccountDO();
    upd.setId(id);
    upd.setSecretKeyCipher(AesCipher.encrypt(skPlain, skSecret));
    upd.setKeyStatus(0);
    accountMapper.updateById(upd);
    return skPlain;
}

private void validateOrgExists(Long orgId) {
    if (orgMapper.selectById(orgId) == null) {
        throw exception(ORG_NOT_EXISTS);
    }
}
private String generateAppKey() {
    return "AK" + java.util.UUID.randomUUID().toString().replace("-", "");
}
private String generateSecret() {
    return java.util.UUID.randomUUID().toString().replace("-", "")
         + java.util.UUID.randomUUID().toString().replace("-", "");
}
```

`updateAccount`：校验存在（`ORG_ACCOUNT_NOT_EXISTS`）后 `BeanUtils.toBean` 转 DO，显式 `setAppKey(null)`/`setSecretKeyCipher(null)`（AK/SK 不经 update 改），`updateById`。`getAccountByAppKey` 调 `selectByAppKey`；`getListByOrgId` 调 `selectListByOrgId`。`import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;`。

`OrgAccountController.java`：`/org/account`，权限串 `org:account:*`；
- `create`：`Long id = service.createAccount(vo); String sk = service.resetSecret(id); OrgAccountDO db = service.getAccount(id); OrgAccountRespVO resp = BeanUtils.toBean(db, OrgAccountRespVO.class); resp.setSecretKey(sk); return success(resp);`
- `reset-secret`（`@PutMapping("/reset-secret") @RequestParam("id")`）：`String sk = service.resetSecret(id); OrgAccountRespVO r = new OrgAccountRespVO(); r.setId(id); r.setSecretKey(sk); return success(r);`
- `get`/`page`/`list-by-org`：`BeanUtils.toBean` 转 RespVO，`secretKey` 保持 null（不回明文）。
- `update`/`delete`：标准。

- [ ] **Step 5: 运行验证通过** — `OrgAccountServiceImplTest` 6 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-org docker/mysql/init/06-org-schema.sql
git commit -m "feat(org): 机构账号 org_account CRUD（AK随机+SK AES落库+重置密钥+IP白名单）"
```

---

### Task 5: 机构产品 org_product CRUD（松耦合产品引用 + 单价/限额/有效期）

**Files:**
- Create: `.../org/dal/dataobject/org/OrgProductDO.java`
- Create: `.../org/dal/mysql/org/OrgProductMapper.java`
- Create: `.../org/controller/admin/org/vo/{OrgProductSaveReqVO,OrgProductPageReqVO,OrgProductRespVO}.java`
- Create: `.../org/service/org/{OrgProductService,OrgProductServiceImpl}.java`
- Create: `.../org/controller/admin/org/OrgProductController.java`
- Modify: `.../org/enums/ErrorCodeConstants.java`
- Test: `.../org/service/org/OrgProductServiceImplTest.java`
- Modify: DDL 三处

**Interfaces:**
- Consumes: Task 3 `OrgMapper`（校验 `orgId` 存在 → `ORG_NOT_EXISTS`）。
- Produces:
  - `class OrgProductDO extends BaseDO { Long id; Long orgId; Long productId; String productCode; Integer status; LocalDateTime effectiveTime; LocalDateTime expireTime; BigDecimal unitPrice; Long billingTemplateId; Integer dailyLimit; Integer monthlyLimit; Integer concurrencyLimit; String remark; }`（`productId`/`productCode` 为 product 模块的**松耦合快照引用**，本模块不校验其存在性；`billingTemplateId` 松耦合引用计费模板，本期可空、不建模板表；`status`：0开通 1停用）
  - `interface OrgProductService { Long createOrgProduct(OrgProductSaveReqVO); void updateOrgProduct(OrgProductSaveReqVO); void deleteOrgProduct(Long id); OrgProductDO getOrgProduct(Long id); OrgProductDO getByOrgAndProductCode(Long orgId, String productCode); PageResult<OrgProductDO> getOrgProductPage(OrgProductPageReqVO); List<OrgProductDO> getListByOrgId(Long orgId); }`
  - HTTP：`/org/product/{create,update,delete,get,page,list-by-org}`；权限串 `org:product:*`
  - `ErrorCodeConstants.ORG_PRODUCT_NOT_EXISTS = new ErrorCode(1_022_003_000, "机构产品不存在")`；`ORG_PRODUCT_DUPLICATE = new ErrorCode(1_022_003_001, "该机构已开通此产品")`

MySQL DDL：

```sql
CREATE TABLE `org_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '所属机构ID',
  `product_id` bigint NOT NULL COMMENT '产品ID（松耦合引用 product）',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码快照',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0开通 1停用',
  `effective_time` datetime NULL DEFAULT NULL COMMENT '生效时间(空为立即)',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '失效时间(空为长期)',
  `unit_price` decimal(12,4) NULL DEFAULT NULL COMMENT '开通单价',
  `billing_template_id` bigint NULL DEFAULT NULL COMMENT '计费模板ID(松耦合,计费里程碑)',
  `daily_limit` int NULL DEFAULT NULL COMMENT '日调用量上限',
  `monthly_limit` int NULL DEFAULT NULL COMMENT '月调用量上限',
  `concurrency_limit` int NULL DEFAULT NULL COMMENT '最大并发',
  `remark` varchar(512) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_org_product` (`org_id`, `product_code`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构产品开通表';
```

> 注：`uk_org_product(org_id, product_code)` 与软删除列 `deleted` 共存时，MyBatis-Plus 逻辑删除后同 (org,product) 再开通会与旧软删行冲突。为契合“开通-停用-重开通”语义，采用**唯一约束 + 服务层重复校验**：create 前先 `getByOrgAndProductCode` 查**未删除**记录，命中则抛 `ORG_PRODUCT_DUPLICATE`（软删除记录被 MyBatis-Plus 自动 `deleted=0` 过滤）。H2 DDL **不加**该唯一约束（H2 逻辑删除与唯一约束交互与 MySQL 不一致），重复防护由服务层单测覆盖。

H2 DDL（同风格，`decimal(12,4)`；**不建**唯一约束）：

```sql
CREATE TABLE IF NOT EXISTS "org_product" (
    "id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "org_id" bigint NOT NULL,
    "product_id" bigint NOT NULL,
    "product_code" varchar(32) NOT NULL,
    "status" tinyint NOT NULL DEFAULT 0,
    "effective_time" datetime DEFAULT NULL,
    "expire_time" datetime DEFAULT NULL,
    "unit_price" decimal(12,4) DEFAULT NULL,
    "billing_template_id" bigint DEFAULT NULL,
    "daily_limit" int DEFAULT NULL,
    "monthly_limit" int DEFAULT NULL,
    "concurrency_limit" int DEFAULT NULL,
    "remark" varchar(512) DEFAULT '',
    "creator" varchar(64) DEFAULT '',
    "create_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater" varchar(64) DEFAULT '',
    "update_time" datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted" bit NOT NULL DEFAULT FALSE
) COMMENT '机构产品开通表';
```

`clean.sql` 追加：`DELETE FROM "org_product";`

- [ ] **Step 1: ErrorCode + DDL** — 追加 `ORG_PRODUCT_NOT_EXISTS`/`ORG_PRODUCT_DUPLICATE`；建表 DDL 三处。

- [ ] **Step 2: 写失败测试**

`OrgProductServiceImplTest.java`（先经 `OrgMapper` 造真实父机构）：

```java
package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgProductServiceImpl.class)
class OrgProductServiceImplTest extends BaseDbUnitTest {

    @Resource private OrgProductServiceImpl service;
    @Resource private OrgMapper orgMapper;

    private Long newOrg() {
        OrgDO o = new OrgDO();
        o.setOrgCode("ORG000001"); o.setName("机构"); o.setStatus(0);
        orgMapper.insert(o);
        return o.getId();
    }
    private OrgProductSaveReqVO req(Long orgId, String productCode) {
        OrgProductSaveReqVO vo = new OrgProductSaveReqVO();
        vo.setOrgId(orgId); vo.setProductId(100L); vo.setProductCode(productCode);
        vo.setStatus(0); vo.setUnitPrice(new BigDecimal("1.5000"));
        return vo;
    }

    @Test
    void create_persistsAndReadBack() {
        Long orgId = newOrg();
        Long id = service.createOrgProduct(req(orgId, "P000001"));
        OrgProductDO db = service.getOrgProduct(id);
        assertThat(db.getProductCode()).isEqualTo("P000001");
        assertThat(db.getUnitPrice()).isEqualByComparingTo("1.5000");
    }

    @Test
    void create_parentOrgNotExists_throws() {
        assertServiceException(() -> service.createOrgProduct(req(99999L, "P000001")), ORG_NOT_EXISTS);
    }

    @Test
    void create_duplicate_throws() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000001"));
        assertServiceException(() -> service.createOrgProduct(req(orgId, "P000001")), ORG_PRODUCT_DUPLICATE);
    }

    @Test
    void getByOrgAndProductCode_returns() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000002"));
        assertThat(service.getByOrgAndProductCode(orgId, "P000002")).isNotNull();
        assertThat(service.getByOrgAndProductCode(orgId, "NOPE")).isNull();
    }

    @Test
    void update_notExists_throws() {
        OrgProductSaveReqVO upd = req(1L, "P000001");
        upd.setId(99999L);
        assertServiceException(() -> service.updateOrgProduct(upd), ORG_PRODUCT_NOT_EXISTS);
    }

    @Test
    void page_filtersByOrgId() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000001"));
        service.createOrgProduct(req(orgId, "P000002"));
        OrgProductPageReqVO q = new OrgProductPageReqVO();
        q.setOrgId(orgId);
        PageResult<OrgProductDO> page = service.getOrgProductPage(q);
        assertThat(page.getTotal()).isEqualTo(2);
    }
}
```

- [ ] **Step 3: 运行验证失败** — 编译失败。

- [ ] **Step 4: 最小实现**

`OrgProductDO.java`：`@TableName("org_product")` + `@KeySequence("org_product_seq")` + `@Data` + `@TenantIgnore`，字段见 Interfaces（`unitPrice` 为 `java.math.BigDecimal`，`effectiveTime`/`expireTime` 为 `LocalDateTime`）。

`OrgProductMapper.java`（`selectPage` 按 `orgId`/`productCode(eq)`/`status` 过滤 + `orderByDesc(id)`；`selectByOrgAndProductCode(Long orgId, String productCode)` = `selectOne(new LambdaQueryWrapperX<OrgProductDO>().eq(OrgProductDO::getOrgId, orgId).eq(OrgProductDO::getProductCode, productCode))`；`selectListByOrgId(Long)` = `selectList(OrgProductDO::getOrgId, orgId)`）。

VO 三件：`OrgProductSaveReqVO`（`id`；`orgId` `@NotNull`；`productId` `@NotNull`；`productCode` `@NotEmpty`；`status` `@NotNull`；`effectiveTime`；`expireTime`；`unitPrice`；`billingTemplateId`；`dailyLimit`；`monthlyLimit`；`concurrencyLimit`；`remark`）、`OrgProductPageReqVO extends PageParam`（`orgId`/`productCode`/`status`）、`OrgProductRespVO`（镜像 DO + createTime）。

`OrgProductServiceImpl`：

```java
@Resource private OrgProductMapper orgProductMapper;
@Resource private OrgMapper orgMapper;

@Override
public Long createOrgProduct(OrgProductSaveReqVO reqVO) {
    if (orgMapper.selectById(reqVO.getOrgId()) == null) {
        throw exception(ORG_NOT_EXISTS);
    }
    if (orgProductMapper.selectByOrgAndProductCode(reqVO.getOrgId(), reqVO.getProductCode()) != null) {
        throw exception(ORG_PRODUCT_DUPLICATE);
    }
    OrgProductDO entity = BeanUtils.toBean(reqVO, OrgProductDO.class);
    entity.setId(null);
    orgProductMapper.insert(entity);
    return entity.getId();
}
```

`updateOrgProduct`：校验存在（`ORG_PRODUCT_NOT_EXISTS`）后 `BeanUtils.toBean` 转 DO，`setOrgId(null)`/`setProductId(null)`/`setProductCode(null)`（归属不经 update 改，仅调价/限额/有效期/状态）后 `updateById`；`getByOrgAndProductCode` 调 mapper；`getListByOrgId` 调 mapper。`import static ...ServiceExceptionUtil.exception;`。

`OrgProductController.java`：`/org/product` 六端点（含 `list-by-org`），权限串 `org:product:*`，标准写法（`BeanUtils.toBean` 转 RespVO）。

- [ ] **Step 5: 运行验证通过** — `OrgProductServiceImplTest` 6 用例全过。

- [ ] **Step 6: Commit**

```bash
git add yudao-module-org docker/mysql/init/06-org-schema.sql
git commit -m "feat(org): 机构产品 org_product CRUD（松耦合产品引用+单价/限额/有效期+重复开通校验）"
```

---

### Task 6: 五重鉴权服务 OrgAuthService + verify RPC 端点

**Files:**
- Create: `.../org/service/auth/NonceStore.java`
- Create: `.../org/service/auth/InMemoryNonceStore.java`
- Create: `.../org/service/auth/OrgAuthService.java`
- Create: `.../org/service/auth/OrgAuthServiceImpl.java`
- Create: `.../org/controller/rpc/OrgAuthRpcController.java`
- Test: `.../org/service/auth/OrgAuthServiceImplTest.java`

**Interfaces:**
- Consumes: Task 2 `CryptoSignatures`/`AesCipher`/`PlatformErrorCode`/`OrgAuthVerifyReqDTO`/`OrgAuthVerifyRespDTO`；Task 4 `OrgAccountMapper`；Task 3 `OrgMapper`；Task 5 `OrgProductMapper`。
- Produces:
  - `interface NonceStore { boolean tryAcquire(String key, long ttlMs); }`——首次出现返回 `true` 并记录；窗口内重复返回 `false`。
  - `@Component class InMemoryNonceStore implements NonceStore`（`ConcurrentHashMap<String,Long>` 存到期时间戳，每次惰性清理过期项；**多实例部署需换 Redis 实现，本期单实例/测试用内存**）。
  - `interface OrgAuthService { OrgAuthVerifyRespDTO verify(OrgAuthVerifyReqDTO req); }`。
  - `@Component class OrgAuthServiceImpl`：按顺序执行五重校验（见下），任一不过即 `fail(对应码)`，全过 `pass(orgId, accountId, orgCode)`。
  - HTTP `POST /rpc-api/org-auth/verify`（body=`OrgAuthVerifyReqDTO`）→ `OrgAuthVerifyRespDTO`（内部端点，`@PermitAll`，`@TenantIgnore`）。

**五重校验顺序（`OrgAuthServiceImpl.verify`）：**
1. **账号**：`accountMapper.selectByAppKey(appKey)`；null → `ACCOUNT_DISABLED(1004)`。
2. **时间戳**：`timestamp` 非数字或 `|now - ts| > timestampWindowMs` → `TIMESTAMP_INVALID(1002)`。
3. **签名**：`sk = AesCipher.decrypt(cipher, skSecret)`；`expect = hmacSha256Hex(sk, buildSignPayload(appKey, timestamp, nonce, bodyDigest))`；`!expect.equalsIgnoreCase(signature)` → `SIGN_ERROR(1001)`。
4. **nonce 去重**：`!nonceStore.tryAcquire(appKey + ":" + nonce, timestampWindowMs)` → `TIMESTAMP_INVALID(1002)`（重放）。
5. **IP 白名单**：`ipWhitelist` 非空且 `clientIp` 不匹配任一条目（精确 IP 或 CIDR 段）→ `IP_FORBIDDEN(1003)`；空白名单放行。
6. **账号状态**：`status!=0 || keyStatus!=0 || (expireTime!=null && expireTime<now)` → `ACCOUNT_DISABLED(1004)`。
7. **机构**：`orgMapper.selectById(orgId)`；null 或 `status!=0` → `ORG_DISABLED(1005)`。
8. **产品有效期**：`orgProductMapper.selectByOrgAndProductCode(orgId, productCode)`；null 或 `status!=0` → `PRODUCT_UNAUTHORIZED(1006)`；`expireTime<now` → `PRODUCT_EXPIRED(1007)`；`effectiveTime>now`（未生效）→ `PRODUCT_UNAUTHORIZED(1006)`。

> 签名放在 nonce 之前，避免未过签名的请求污染 nonce 存储。授权书编号（1008）依赖 product.need_auth_no（跨模块），本期不做。

- [ ] **Step 1: 写失败测试（H2，真实 mapper + 内存 NonceStore）**

`OrgAuthServiceImplTest.java`：

```java
package cn.iocoder.yudao.module.org.service.auth;

import cn.apiten.common.crypto.AesCipher;
import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgAccountMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import({OrgAuthServiceImpl.class, InMemoryNonceStore.class})
@TestPropertySource(properties = {"apiten.org.sk-secret=unit-test-key", "apiten.org.timestamp-window-ms=300000"})
class OrgAuthServiceImplTest extends BaseDbUnitTest {

    private static final String SK = "sk-plaintext-secret";

    @Resource private OrgAuthServiceImpl service;
    @Resource private OrgMapper orgMapper;
    @Resource private OrgAccountMapper accountMapper;
    @Resource private OrgProductMapper orgProductMapper;

    /** 造：启用机构 + 启用账号(SK 已知) + 已开通产品；返回 appKey */
    private String seedHappyPath(String appKey, String ip) {
        OrgDO org = new OrgDO();
        org.setOrgCode("ORG000001"); org.setName("机构"); org.setStatus(0);
        orgMapper.insert(org);
        OrgAccountDO acc = new OrgAccountDO();
        acc.setOrgId(org.getId()); acc.setAppKey(appKey);
        acc.setSecretKeyCipher(AesCipher.encrypt(SK, "unit-test-key"));
        acc.setKeyStatus(0); acc.setStatus(0); acc.setSignAlgorithm("HMAC-SHA256");
        acc.setIpWhitelist(ip == null ? "" : ip);
        accountMapper.insert(acc);
        OrgProductDO op = new OrgProductDO();
        op.setOrgId(org.getId()); op.setProductId(100L); op.setProductCode("P000001"); op.setStatus(0);
        orgProductMapper.insert(op);
        return appKey;
    }

    private OrgAuthVerifyReqDTO signedReq(String appKey, String nonce, String ip) {
        OrgAuthVerifyReqDTO r = new OrgAuthVerifyReqDTO();
        r.setAppKey(appKey);
        r.setTimestamp(Long.toString(System.currentTimeMillis()));
        r.setNonce(nonce);
        r.setBodyDigest(CryptoSignatures.sha256Hex("{}"));
        r.setProductCode("P000001");
        r.setClientIp(ip);
        String payload = CryptoSignatures.buildSignPayload(appKey, r.getTimestamp(), nonce, r.getBodyDigest());
        r.setSignature(CryptoSignatures.hmacSha256Hex(SK, payload));
        return r;
    }

    @Test
    void allChecksPass() {
        seedHappyPath("AKok", "1.2.3.4");
        OrgAuthVerifyRespDTO resp = service.verify(signedReq("AKok", "n1", "1.2.3.4"));
        assertThat(resp.isPass()).isTrue();
        assertThat(resp.getOrgCode()).isEqualTo("ORG000001");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
    }

    @Test
    void unknownAppKey_1004() {
        assertThat(service.verify(signedReq("AKnope", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1004");
    }

    @Test
    void badSignature_1001() {
        seedHappyPath("AKsig", "1.2.3.4");
        OrgAuthVerifyReqDTO r = signedReq("AKsig", "n1", "1.2.3.4");
        r.setSignature("deadbeef");
        assertThat(service.verify(r).getPlatformCode()).isEqualTo("1001");
    }

    @Test
    void staleTimestamp_1002() {
        seedHappyPath("AKts", "1.2.3.4");
        OrgAuthVerifyReqDTO r = signedReq("AKts", "n1", "1.2.3.4");
        r.setTimestamp(Long.toString(System.currentTimeMillis() - 600_000)); // 10 分钟前
        assertThat(service.verify(r).getPlatformCode()).isEqualTo("1002");
    }

    @Test
    void replayNonce_1002() {
        seedHappyPath("AKrp", "1.2.3.4");
        assertThat(service.verify(signedReq("AKrp", "dup", "1.2.3.4")).isPass()).isTrue();
        assertThat(service.verify(signedReq("AKrp", "dup", "1.2.3.4")).getPlatformCode()).isEqualTo("1002");
    }

    @Test
    void ipNotInWhitelist_1003() {
        seedHappyPath("AKip", "10.0.0.0/8");
        assertThat(service.verify(signedReq("AKip", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1003");
        assertThat(service.verify(signedReq("AKip", "n2", "10.1.2.3")).isPass()).isTrue();
    }

    @Test
    void orgDisabled_1005() {
        seedHappyPath("AKorg", "1.2.3.4");
        OrgDO org = orgMapper.selectList().get(0);
        org.setStatus(1);
        orgMapper.updateById(org);
        assertThat(service.verify(signedReq("AKorg", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1005");
    }

    @Test
    void productNotOpened_1006() {
        OrgDO org = new OrgDO();
        org.setOrgCode("ORG000002"); org.setName("机构"); org.setStatus(0);
        orgMapper.insert(org);
        OrgAccountDO acc = new OrgAccountDO();
        acc.setOrgId(org.getId()); acc.setAppKey("AKnoprod");
        acc.setSecretKeyCipher(AesCipher.encrypt(SK, "unit-test-key"));
        acc.setKeyStatus(0); acc.setStatus(0); acc.setIpWhitelist("");
        accountMapper.insert(acc);
        assertThat(service.verify(signedReq("AKnoprod", "n1", "9.9.9.9")).getPlatformCode()).isEqualTo("1006");
    }

    @Test
    void productExpired_1007() {
        seedHappyPath("AKexp", "1.2.3.4");
        OrgProductDO op = orgProductMapper.selectList().get(0);
        op.setExpireTime(LocalDateTime.now().minusDays(1));
        orgProductMapper.updateById(op);
        assertThat(service.verify(signedReq("AKexp", "n1", "1.2.3.4")).getPlatformCode()).isEqualTo("1007");
    }
}
```

> `replayNonce_1002` 依赖同 `nonce` 两次；`signedReq` 每次用当前时间戳重签，两次时间戳/签名均有效，仅 nonce 复用触发去重。`InMemoryNonceStore` 键必须为 `appKey:nonce`。

- [ ] **Step 2: 运行验证失败** — 编译失败。

- [ ] **Step 3: 最小实现**

`NonceStore.java`：

```java
package cn.iocoder.yudao.module.org.service.auth;

public interface NonceStore {
    /** 首次出现返回 true 并记录；窗口内重复返回 false */
    boolean tryAcquire(String key, long ttlMs);
}
```

`InMemoryNonceStore.java`：

```java
package cn.iocoder.yudao.module.org.service.auth;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryNonceStore implements NonceStore {

    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long ttlMs) {
        long now = System.currentTimeMillis();
        seen.entrySet().removeIf(e -> e.getValue() < now); // 惰性清理过期
        return seen.putIfAbsent(key, now + ttlMs) == null;
    }
}
```

`OrgAuthService.java`：接口见 Interfaces。

`OrgAuthServiceImpl.java`：

```java
package cn.iocoder.yudao.module.org.service.auth;

import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.crypto.AesCipher;
import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgAccountMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgProductMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Component
public class OrgAuthServiceImpl implements OrgAuthService {

    @Value("${apiten.org.sk-secret:apiten-default-dev-key}")
    private String skSecret;
    @Value("${apiten.org.timestamp-window-ms:300000}")
    private long timestampWindowMs;

    @Resource private OrgAccountMapper accountMapper;
    @Resource private OrgMapper orgMapper;
    @Resource private OrgProductMapper orgProductMapper;
    @Resource private NonceStore nonceStore;

    @Override
    public OrgAuthVerifyRespDTO verify(OrgAuthVerifyReqDTO req) {
        // 1. 账号
        OrgAccountDO account = accountMapper.selectByAppKey(req.getAppKey());
        if (account == null) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ACCOUNT_DISABLED);
        }
        // 2. 时间戳窗口
        long ts;
        try {
            ts = Long.parseLong(req.getTimestamp());
        } catch (Exception e) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        if (Math.abs(System.currentTimeMillis() - ts) > timestampWindowMs) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        // 3. 签名
        String sk = AesCipher.decrypt(account.getSecretKeyCipher(), skSecret);
        String expect = CryptoSignatures.hmacSha256Hex(sk,
                CryptoSignatures.buildSignPayload(req.getAppKey(), req.getTimestamp(), req.getNonce(), req.getBodyDigest()));
        if (!expect.equalsIgnoreCase(req.getSignature())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.SIGN_ERROR);
        }
        // 4. nonce 去重（重放）
        if (!nonceStore.tryAcquire(req.getAppKey() + ":" + req.getNonce(), timestampWindowMs)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.TIMESTAMP_INVALID);
        }
        // 5. IP 白名单
        if (!ipAllowed(account.getIpWhitelist(), req.getClientIp())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.IP_FORBIDDEN);
        }
        // 6. 账号状态
        LocalDateTime now = LocalDateTime.now();
        boolean accountBad = !isZero(account.getStatus()) || !isZero(account.getKeyStatus())
                || (account.getExpireTime() != null && account.getExpireTime().isBefore(now));
        if (accountBad) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ACCOUNT_DISABLED);
        }
        // 7. 机构
        OrgDO org = orgMapper.selectById(account.getOrgId());
        if (org == null || !isZero(org.getStatus())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.ORG_DISABLED);
        }
        // 8. 产品有效期
        OrgProductDO op = orgProductMapper.selectByOrgAndProductCode(account.getOrgId(), req.getProductCode());
        if (op == null || !isZero(op.getStatus())) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_UNAUTHORIZED);
        }
        if (op.getExpireTime() != null && op.getExpireTime().isBefore(now)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_EXPIRED);
        }
        if (op.getEffectiveTime() != null && op.getEffectiveTime().isAfter(now)) {
            return OrgAuthVerifyRespDTO.fail(PlatformErrorCode.PRODUCT_UNAUTHORIZED);
        }
        return OrgAuthVerifyRespDTO.pass(org.getId(), account.getId(), org.getOrgCode());
    }

    private boolean isZero(Integer v) { return v != null && v == 0; }

    /** 空白名单放行；否则逐条精确/CIDR 匹配 */
    private boolean ipAllowed(String whitelist, String clientIp) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        for (String entry : whitelist.split(",")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.contains("/") ? cidrMatch(e, clientIp) : e.equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    private boolean cidrMatch(String cidr, String ip) {
        try {
            String[] parts = cidr.split("/");
            long net = ipv4ToLong(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            long mask = prefix == 0 ? 0 : (-1L << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipv4ToLong(ip) & mask) == (net & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipv4ToLong(String ip) {
        String[] o = ip.split("\\.");
        return (Long.parseLong(o[0]) << 24) | (Long.parseLong(o[1]) << 16)
                | (Long.parseLong(o[2]) << 8) | Long.parseLong(o[3]);
    }
}
```

`OrgAuthRpcController.java`：

```java
package cn.iocoder.yudao.module.org.controller.rpc;

import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import cn.iocoder.yudao.framework.security.core.annotations.PermitAll;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.org.service.auth.OrgAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/rpc-api/org-auth")
public class OrgAuthRpcController {

    @Resource
    private OrgAuthService orgAuthService;

    @PostMapping("/verify")
    @PermitAll
    @TenantIgnore
    public OrgAuthVerifyRespDTO verify(@RequestBody OrgAuthVerifyReqDTO req) {
        return orgAuthService.verify(req);
    }
}
```

> `@PermitAll` 的导入路径以本仓库 yudao 版本为准（若 `cn.iocoder.yudao.framework.security.core.annotations.PermitAll` 不存在，改用等价注解或在 `application.yaml` 的 `yudao.security.permit-all-urls` 追加 `/rpc-api/org-auth/verify`）。实现者在报告记录最终方式。

- [ ] **Step 4: 运行验证通过** — `OrgAuthServiceImplTest` 9 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-org
git commit -m "feat(org): 五重鉴权服务(签名/时间戳/nonce/IP/账号/机构/产品) + verify RPC 端点"
```

---

### Task 7: 网关五重鉴权过滤器 + flowNo 注入 + openapi 读头

**Files:**
- Create: `yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/openapi/OpenApiAuthFilter.java`
- Create: `yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/openapi/OpenApiPathUtils.java`
- Modify: `yudao-gateway/pom.xml`（加 `apiten-common` 依赖）、`yudao-gateway/src/main/resources/application.yaml`（加 `apiten.gateway.worker-id`）
- Modify: `yudao-module-openapi/.../controller/QueryController.java`、`.../service/QueryOrchestrator.java`（读 `X-Flow-No` 头）
- Test: `yudao-gateway/src/test/java/cn/iocoder/yudao/gateway/filter/openapi/OpenApiPathUtilsTest.java`
- Test: `yudao-module-openapi/.../controller/QueryControllerTest.java`（补 flowNo 头用例）

**Interfaces:**
- Consumes: Task 2 `CryptoSignatures.sha256Hex`、`OrgAuthVerifyReqDTO`/`OrgAuthVerifyRespDTO`、`ApiResponse`、`PlatformErrorCode`、`SnowflakeIdGenerator`；Task 6 `/rpc-api/org-auth/verify`（经 `lb://org-server`）。
- Produces:
  - `class OpenApiPathUtils { static boolean isOpenApiPath(String path); static String extractProductCode(String path); }`——`isOpenApiPath`：以 `/api/v1/` 开头；`extractProductCode("/api/v1/P000001/query")` → `"P000001"`，无匹配 → `null`。
  - `@Component class OpenApiAuthFilter implements GlobalFilter, Ordered`（`getOrder()` 返回 `-50`，晚于 `TokenAuthenticationFilter(-100)`）：仅处理 `/api/v1/**`；缓存请求体→`sha256Hex`→生成 flowNo→`WebClient(lb)` POST verify→pass 注入 `X-Org-Id`/`X-Account-Id`/`X-Org-Code`/`X-Product-Code`/`X-Flow-No` 头并转发；fail 直接写回统一 `ApiResponse` 错误 JSON（HTTP 200）。

- [ ] **Step 1: 写失败测试（纯工具，离线）**

`OpenApiPathUtilsTest.java`：

```java
package cn.iocoder.yudao.gateway.filter.openapi;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenApiPathUtilsTest {

    @Test
    void isOpenApiPath() {
        assertThat(OpenApiPathUtils.isOpenApiPath("/api/v1/P000001/query")).isTrue();
        assertThat(OpenApiPathUtils.isOpenApiPath("/admin-api/system/user/page")).isFalse();
        assertThat(OpenApiPathUtils.isOpenApiPath("/api/v1/")).isTrue();
        assertThat(OpenApiPathUtils.isOpenApiPath(null)).isFalse();
    }

    @Test
    void extractProductCode() {
        assertThat(OpenApiPathUtils.extractProductCode("/api/v1/P000001/query")).isEqualTo("P000001");
        assertThat(OpenApiPathUtils.extractProductCode("/api/v1/P000001/apply")).isEqualTo("P000001");
        assertThat(OpenApiPathUtils.extractProductCode("/api/v1/")).isNull();
        assertThat(OpenApiPathUtils.extractProductCode("/other")).isNull();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-gateway -am test -q`
Expected：编译失败（类不存在）。

- [ ] **Step 3: 最小实现（工具 + pom 依赖）**

`yudao-gateway/pom.xml`：在 `<dependencies>` 内追加 apiten-common（groupId/version 照抄 `yudao-module-product/pom.xml` 里引 apiten-common 的那一段，勿臆造）：

```xml
        <dependency>
            <artifactId>apiten-common</artifactId>
            <groupId>cn.iocoder.cloud</groupId>
            <version>${revision}</version>
        </dependency>
```

`OpenApiPathUtils.java`：

```java
package cn.iocoder.yudao.gateway.filter.openapi;

public final class OpenApiPathUtils {

    private static final String PREFIX = "/api/v1/";

    private OpenApiPathUtils() {}

    public static boolean isOpenApiPath(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    public static String extractProductCode(String path) {
        if (!isOpenApiPath(path)) {
            return null;
        }
        String rest = path.substring(PREFIX.length()); // e.g. "P000001/query"
        int slash = rest.indexOf('/');
        String code = slash >= 0 ? rest.substring(0, slash) : rest;
        return code.isEmpty() ? null : code;
    }
}
```

- [ ] **Step 4: 运行验证通过（工具）**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-gateway -am test -q`
Expected：`OpenApiPathUtilsTest` 2 用例全过。

- [ ] **Step 5: 实现网关过滤器**

`OpenApiAuthFilter.java`（reactive；请求体缓存用 `DataBufferUtils.join` + 请求装饰器；WebClient+lb 参考现有 `TokenAuthenticationFilter`）：

```java
package cn.iocoder.yudao.gateway.filter.openapi;

import cn.apiten.common.crypto.CryptoSignatures;
import cn.apiten.common.id.SnowflakeIdGenerator;
import cn.apiten.common.api.PlatformErrorCode;
import cn.apiten.common.org.OrgAuthVerifyReqDTO;
import cn.apiten.common.org.OrgAuthVerifyRespDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenApiAuthFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnowflakeIdGenerator idGen;

    public OpenApiAuthFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction,
            @Value("${apiten.gateway.worker-id:1}") long workerId) {
        this.webClient = WebClient.builder().filter(lbFunction).build();
        this.idGen = new SnowflakeIdGenerator(workerId);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!OpenApiPathUtils.isOpenApiPath(path)) {
            return chain.filter(exchange); // 非对外 API，放行
        }
        final String productCode = OpenApiPathUtils.extractProductCode(path);
        final String flowNo = idGen.nextIdStr();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        final String appKey = headers.getFirst("X-App-Key");
        final String timestamp = headers.getFirst("X-Timestamp");
        final String nonce = headers.getFirst("X-Nonce");
        final String signature = headers.getFirst("X-Signature");
        String ip = exchange.getRequest().getRemoteAddress() == null ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            ip = xff.split(",")[0].trim();
        }
        final String clientIp = ip;

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String bodyDigest = CryptoSignatures.sha256Hex(new String(bytes, StandardCharsets.UTF_8));

                    OrgAuthVerifyReqDTO req = new OrgAuthVerifyReqDTO();
                    req.setAppKey(appKey);
                    req.setTimestamp(timestamp);
                    req.setNonce(nonce);
                    req.setSignature(signature);
                    req.setBodyDigest(bodyDigest);
                    req.setProductCode(productCode);
                    req.setClientIp(clientIp);
                    req.setFlowNo(flowNo);

                    return webClient.post()
                            .uri("lb://org-server/rpc-api/org-auth/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(req)
                            .retrieve()
                            .bodyToMono(OrgAuthVerifyRespDTO.class)
                            .onErrorResume(e -> Mono.just(OrgAuthVerifyRespDTO.fail(PlatformErrorCode.SYSTEM_ERROR)))
                            .flatMap(resp -> {
                                if (!resp.isPass()) {
                                    return writeError(exchange, flowNo, productCode, resp);
                                }
                                ServerHttpRequest mutated = new CachedBodyRequestDecorator(
                                        exchange.getRequest(), bytes, b -> {
                                            b.set("X-Org-Id", String.valueOf(resp.getOrgId()));
                                            b.set("X-Account-Id", String.valueOf(resp.getAccountId()));
                                            b.set("X-Org-Code", resp.getOrgCode());
                                            b.set("X-Product-Code", productCode);
                                            b.set("X-Flow-No", flowNo);
                                        });
                                return chain.filter(exchange.mutate().request(mutated).build());
                            });
                });
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String flowNo, String productCode,
            OrgAuthVerifyRespDTO resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowNo", flowNo);
        body.put("productCode", productCode);
        body.put("code", resp.getPlatformCode());
        body.put("msg", resp.getMsg());
        body.put("charged", false);
        body.put("costTime", 0);
        body.put("data", null);
        byte[] out;
        try {
            out = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            out = "{\"code\":\"3999\",\"msg\":\"系统异常\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = exchange.getResponse().bufferFactory().wrap(out);
        return exchange.getResponse().writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -50;
    }

    /** 用已读取字节重放请求体 + 覆写请求头 */
    static class CachedBodyRequestDecorator extends ServerHttpRequestDecorator {
        private final byte[] body;
        private final HttpHeaders headers;

        CachedBodyRequestDecorator(ServerHttpRequest delegate, byte[] body,
                java.util.function.Consumer<HttpHeaders> headerCustomizer) {
            super(delegate);
            this.body = body;
            HttpHeaders h = new HttpHeaders();
            h.addAll(delegate.getHeaders());
            headerCustomizer.accept(h);
            h.setContentLength(body.length);
            this.headers = h;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.defer(() -> Mono.just(new DefaultDataBufferFactory().wrap(body)));
        }
    }
}
```

`yudao-gateway/src/main/resources/application.yaml`：在文件末尾 `yudao:` 段前追加：

```yaml
apiten:
  gateway:
    worker-id: ${APITEN_GATEWAY_WORKER_ID:1} # 雪花 ID 工作节点号，多实例需各不相同
```

> **可测试性说明（诚实标注）**：`OpenApiAuthFilter` 的请求体缓存 + `lb://` WebClient 调用属 reactive/远程集成，无法离线单测精确断言；本任务离线覆盖纯工具 `OpenApiPathUtils` 与已在 Task 2/6 全覆盖的签名/五重逻辑，过滤器端到端由 DoD 的 curl 冒烟验证。`CachedBodyRequestDecorator` 字节重放为 Spring Cloud Gateway 通行写法；若本 Spring 版本 `getBody()` 装饰不生效，改用 `ServerWebExchangeUtils.cacheRequestBodyAndRequest` 并在报告记录。

- [ ] **Step 6: openapi 读 X-Flow-No 头**

`QueryController.java`：

```java
    @PostMapping("/{productCode}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String productCode,
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "X-Flow-No", required = false) String flowNo) {
        return orchestrator.query(productCode, params, flowNo);
    }
```

`QueryOrchestrator.java`：`query` 增加 `String flowNo` 形参，方法体首行改为 `String usedFlowNo = (flowNo != null && !flowNo.isBlank()) ? flowNo : idGen.nextIdStr();`，随后两处 `ApiResponse.of(...)` 的第一参数由 `idGen.nextIdStr()` 改为 `usedFlowNo`。保留旧签名重载 `query(productCode, params)` 委托 `query(productCode, params, null)`，避免破坏既有 `QueryControllerTest`。

- [ ] **Step 7: openapi 测试补 flowNo 用例**

`QueryControllerTest.java` 追加两个用例：
- 传入固定 flowNo：`assertThat(orchestrator.query("P000001", Map.of(), "1943026717538291712").getFlowNo()).isEqualTo("1943026717538291712");`
- 不传（null）：`assertThat(orchestrator.query("P000001", Map.of(), null).getFlowNo()).isNotBlank();`

- [ ] **Step 8: 运行验证通过**

Run:
```
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-gateway -am test -q
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-openapi -am test -q
```
Expected：`OpenApiPathUtilsTest` + openapi flowNo 用例全过。

- [ ] **Step 9: Commit**

```bash
git add yudao-gateway/src yudao-gateway/pom.xml yudao-module-openapi/src
git commit -m "feat(gateway): 对外API五重鉴权过滤器(体摘要+verify RPC+上下文头) + flowNo 注入"
```

---

## P4 完成定义（DoD）

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apiten-common -am test`、`-pl yudao-module-org -am test`、`-pl yudao-gateway -am test`、`-pl yudao-module-openapi -am test` 全绿，离线（H2，不连中间件）。
2. 机构域三表（org_info / org_account / org_product）具备完整 CRUD + 归属校验 + 机构码 id 派生 + AK/SK 随机生成 + SK AES 加密落库 + 明文仅创建/重置返回一次。
3. `OrgAuthService.verify` 覆盖五重（签名 / 时间戳+nonce / IP / 账号 / 机构 / 产品有效期），各错误路径返回正确平台码（1001-1007），H2 单测 9 用例全过。
4. 网关 `/api/v1/**` 过滤器：缓存体→SHA-256 摘要→`lb://org-server` verify→pass 注入上下文头转发、fail 返回统一错误 JSON；flowNo 由网关生成经 `X-Flow-No` 传入 openapi。
5. **端到端 curl 冒烟**（本地起 gateway+org-server+openapi+mysql 后手动验证，记录到执行报告）：
   - 管理端 `POST /admin-api/org/info/create`、`/org/account/create`（拿 AK + 一次性 SK）、`/org/product/create` 开通 `P000001`；
   - 用 AK/SK 按规范签名串 `hmacSha256Hex(sk, appKey\ntimestamp\nnonce\nsha256Hex(body))` 请求 `POST /api/v1/P000001/query`，头带 `X-App-Key/X-Timestamp/X-Nonce/X-Signature`，预期 `code=0000` 且响应含网关生成的 `flowNo`；
   - 篡改签名 → `1001`；改早时间戳 → `1002`；换未授权产品 → `1006`；停用机构 → `1005`。
6. MySQL DDL 并入 `docker/mysql/init/06-org-schema.sql`，运行库 `apiten_org` 可承载。
7. 每任务一个 commit；扁平模块范式；全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续计划（本计划不含）

- **计费里程碑**：billing_template / template_tier / charge_code_rule（org_product.billing_template_id 届时指向真实模板）；计费判定、余额扣减、计费流水、账户/充值/财务流水/账务调整、成本与毛利。
- **路由里程碑**：route_config 三级静态路由（机构产品 > 机构 > 产品默认）消费 P3 `ProductInterfaceResolver`（扩展含机构维度）+ 本域 org_product；动态选源打分、切换链兜底；配置快照版本化发布——届时 org verify 的机构配置改由本地快照缓存供给（替换本期直查 DB），呼应 §3.2「纯缓存」。
- **限流里程碑**：org_account/org_product 的 concurrency/daily/monthly_limit 运行时 enforce（并发信号量 + 调用量账本 + 2102/2103/2104）。
- **鉴权增强**：授权书编号（1008，依赖 product.need_auth_no）、密钥轮换新旧重叠期、nonce 去重换 Redis（多实例共享）、报文级加解密。
- **流水里程碑**：机构流水/数据源流水落库、全链路视图、幂等（bizSeqNo）。
- 机构用户映射（org_user/org_user_mapping）、合并对账、前端页面（机构管理 + 开通向导 7 步）与 sys_menu 菜单/按钮权限落库。
