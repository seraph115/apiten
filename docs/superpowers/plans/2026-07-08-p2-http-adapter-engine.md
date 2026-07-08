# 征信 API 平台 P2：HTTP 适配引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 yudao-module-adapter 内实现配置驱动的 HTTP 适配引擎——消费 P1 落地的数据源接口/参数映射/应答码配置，完成「入参映射+模板渲染 → 真实 HTTP 外调 → 出参 JSONPath 抽取 → 应答码映射（原始码→平台码+四标记，未映射归一 3001）」全链路，并提供联调测试台端点与按协议类型路由的 Provider 注册表。

**Architecture:** 引擎分解为可独立测试的组件（模板渲染 `TemplateRenderer` + 转换函数库 `TransformFunctions`、入参映射 `RequestMapper`、出参抽取 `ResponseExtractor`、应答码解析 `ResponseCodeResolver`、HTTP 执行 `HttpInvoker`），由 `HttpAdapterEngine` 编排：按 `dsInterfaceId` 加载接口/参数/应答码配置 → 渲染请求 → 外调 → 抽取 → 映射码。入口为**联调测试台**（`POST /adapter/ds-interface/{id}/test`，显式指定接口，返回原始请求/原始响应/映射结果）。`InvokeController` 由单 Provider 注入改为按数据源 `protocol_type` 路由的 `ProviderRegistry`（MOCK→MockProvider，HTTP→HttpDataSourceProvider）。**productCode→接口的路由解析不在本期**（留待路由里程碑）——本期 HTTP 引擎以「显式接口」为入口，productCode 默认路径继续由 MockProvider 兜底。

**Tech Stack:** Java 21 + Spring Boot 3.5.15；Spring `RestClient`（同步、虚拟线程友好）+ `MockRestServiceServer`（离线单测，无需真实端口）；Hutool 5.8.46（`JSONUtil.getByPath` 出参抽取、`SecureUtil` 摘要、`DateUtil` 日期转换）；MyBatis-Plus（复用 P1 的 `DsInterfaceMapper`/`DsInterfaceParamMapper`/`DsResponseCodeMapper`）；H2 `BaseDbUnitTest`；JUnit 5 + AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-07-credit-api-platform-design.md`（v1.3）§4.2.2 配置化适配（入参映射/出参映射/应答码映射/联调测试台）、§4.2.3 超时与重试、§8.4 步骤⑥适配层调用。

## Global Constraints

- JDK 21；Maven 命令统一前缀 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`；测试 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`，必须离线通过（H2 + MockRestServiceServer，不连真实上游/中间件）。
- **扁平模块范式**：全部代码在 `yudao-module-adapter`；引擎包根 `cn.iocoder.yudao.module.adapter.engine`；错误码续用 `cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants`，本期占 `1_020_005_xxx` 段。
- **不新增第三方依赖**：JSONPath 用 Hutool `JSONUtil.getByPath`（基座已含 Hutool），HTTP 用 Spring `RestClient`（Spring Boot 自带），摘要/日期用 Hutool。若确需 jayway json-path 请先报告，不擅自加。
- **平台码对齐** apiten-common `PlatformErrorCode`（字符串码）；未映射原始码归一 `PlatformErrorCode.UPSTREAM_ERROR`（`3001`）并记 warn 日志（本期日志占位，正式告警留待监控里程碑）。
- **入参映射方向** `paramDirection=1`（入参）：平台标准字段 `platformField` → 供应商字段 `providerField`，可经 `transformFn` 转换；**出参** `paramDirection=2`：按 `jsonPath` 从上游响应抽取 → 平台标准字段 `platformField`。
- **转换函数库**本期支持：`MD5`、`SHA256`、`UPPER`、`LOWER`、`TRIM`、`DEFAULT:<值>`、`SUBSTR:<start>,<len>`、`DATE_FMT:<目标pattern>`（输入按常见格式宽松解析）。函数名大小写不敏感；带参用 `FN:arg` 语法；未知函数原样透传并记 warn。AES/SM 等加密转换留待后续（需密钥管理）。
- **超时/重试**取自 `ds_interface.timeoutMs`/`retryCount`；重试仅对「HTTP 连接/读取异常或 5xx」，且不对已成功的业务响应重试（本期不依赖应答码「是否可重试」标记做重试——该标记供后续切换链消费）。
- **联调测试台**（§4.2.2#5）：返回 `rawRequest`（方法/URL/头/体）、`rawResponse`（状态码/体）、`mappedData`（出参映射后）、`platformCode` 及四标记；测试台调用**不计费不入账单**（本期无计费，仅标注）。
- **ProviderRequest 扩展**：新增可选字段 `Long dsInterfaceId`（apiten-common），用于显式接口调用；`productCode` 路径保持兼容（为空 dsInterfaceId 时走 MockProvider 兜底）。
- 每个任务结束必须 `git commit`（仅 add 改动文件，不用 `-A`）。
- **不在本期范围**：productCode→接口路由解析（路由里程碑）、切换链/分流（路由里程碑）、计费（计费里程碑）、健康检查探活与被动指标采集（监控里程碑，本期仅在应答码解析处留 warn 占位）、加密类转换函数、前端页面。

---

### Task 1: 转换函数库 TransformFunctions + 模板渲染 TemplateRenderer

**Files:**
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/engine/TransformFunctions.java`
- Create: `.../engine/TemplateRenderer.java`
- Test: `yudao-module-adapter/src/test/java/cn/iocoder/yudao/module/adapter/engine/TransformFunctionsTest.java`
- Test: `.../engine/TemplateRendererTest.java`
- （下文 `.../engine/...` = `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/engine/...`，测试在对应 `src/test/java`）

**Interfaces:**
- Consumes: 无（纯工具，Hutool `SecureUtil`/`DateUtil`/`StrUtil`）。
- Produces:
  - `class TransformFunctions { static String apply(String fnSpec, String value); }`——`fnSpec` 为 null/空时原样返回 `value`；支持 `MD5/SHA256/UPPER/LOWER/TRIM/DEFAULT:x/SUBSTR:s,l/DATE_FMT:pattern`；未知函数原样返回并记 warn。
  - `class TemplateRenderer { static String render(String template, Map<String,Object> vars); }`——把 `${key}` 占位替换为 `vars` 中对应值的字符串（null 值替换为空串）；无匹配占位保留原样；`template` 为 null 返回 null。

- [ ] **Step 1: 写失败测试**

`TransformFunctionsTest.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransformFunctionsTest {

    @Test
    void nullOrBlankSpec_returnsValueUnchanged() {
        assertThat(TransformFunctions.apply(null, "abc")).isEqualTo("abc");
        assertThat(TransformFunctions.apply("", "abc")).isEqualTo("abc");
    }

    @Test
    void md5_and_sha256() {
        assertThat(TransformFunctions.apply("MD5", "abc"))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        assertThat(TransformFunctions.apply("sha256", "abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void upper_lower_trim() {
        assertThat(TransformFunctions.apply("UPPER", "aB")).isEqualTo("AB");
        assertThat(TransformFunctions.apply("LOWER", "aB")).isEqualTo("ab");
        assertThat(TransformFunctions.apply("TRIM", "  x ")).isEqualTo("x");
    }

    @Test
    void default_whenBlank() {
        assertThat(TransformFunctions.apply("DEFAULT:N/A", "")).isEqualTo("N/A");
        assertThat(TransformFunctions.apply("DEFAULT:N/A", "v")).isEqualTo("v");
    }

    @Test
    void substr_startLen() {
        assertThat(TransformFunctions.apply("SUBSTR:0,6", "1234567890")).isEqualTo("123456");
        assertThat(TransformFunctions.apply("SUBSTR:6,4", "1234567890")).isEqualTo("7890");
    }

    @Test
    void dateFmt_normalizesToPattern() {
        assertThat(TransformFunctions.apply("DATE_FMT:yyyyMMdd", "2026-07-08")).isEqualTo("20260708");
    }

    @Test
    void unknownFn_returnsValueUnchanged() {
        assertThat(TransformFunctions.apply("NO_SUCH_FN", "v")).isEqualTo("v");
    }
}
```

`TemplateRendererTest.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void substitutesPlaceholders() {
        Map<String, Object> vars = Map.of("name", "某某公司", "id", 42);
        assertThat(TemplateRenderer.render("{\"n\":\"${name}\",\"i\":${id}}", vars))
                .isEqualTo("{\"n\":\"某某公司\",\"i\":42}");
    }

    @Test
    void nullValue_becomesEmpty() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", null);
        assertThat(TemplateRenderer.render("a=${x}", vars)).isEqualTo("a=");
    }

    @Test
    void unmatchedPlaceholder_keptAsIs() {
        assertThat(TemplateRenderer.render("a=${missing}", Map.of())).isEqualTo("a=${missing}");
    }

    @Test
    void nullTemplate_returnsNull() {
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败（类不存在）。

- [ ] **Step 3: 最小实现**

`TransformFunctions.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransformFunctions {

    private TransformFunctions() {}

    public static String apply(String fnSpec, String value) {
        if (StrUtil.isBlank(fnSpec)) {
            return value;
        }
        String name;
        String arg = null;
        int idx = fnSpec.indexOf(':');
        if (idx >= 0) {
            name = fnSpec.substring(0, idx).trim();
            arg = fnSpec.substring(idx + 1);
        } else {
            name = fnSpec.trim();
        }
        String v = value == null ? "" : value;
        switch (name.toUpperCase()) {
            case "MD5":      return SecureUtil.md5(v);
            case "SHA256":   return SecureUtil.sha256(v);
            case "UPPER":    return v.toUpperCase();
            case "LOWER":    return v.toLowerCase();
            case "TRIM":     return v.trim();
            case "DEFAULT":  return StrUtil.isEmpty(value) ? (arg == null ? "" : arg) : value;
            case "SUBSTR":   return substr(v, arg);
            case "DATE_FMT": return DateUtil.format(DateUtil.parse(v), arg);
            default:
                log.warn("[transform] 未知转换函数 {}，原样透传", name);
                return value;
        }
    }

    private static String substr(String v, String arg) {
        String[] parts = arg.split(",");
        int start = Integer.parseInt(parts[0].trim());
        int len = Integer.parseInt(parts[1].trim());
        if (start >= v.length()) {
            return "";
        }
        int end = Math.min(start + len, v.length());
        return v.substring(start, end);
    }
}
```

`TemplateRenderer.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, Object> vars) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            if (vars != null && vars.containsKey(key)) {
                Object val = vars.get(key);
                m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : String.valueOf(val)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0))); // 未匹配保留原样
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：TransformFunctions 7 + TemplateRenderer 4 全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎——转换函数库与模板渲染器"
```

---

### Task 2: 入参映射 RequestMapper（平台参数 → 供应商参数）

**Files:**
- Create: `.../engine/RequestMapper.java`
- Test: `.../engine/RequestMapperTest.java`

**Interfaces:**
- Consumes: `TransformFunctions`（Task 1）；P1 的 `DsInterfaceParamDO`（`platformField/providerField/transformFn/defaultValue/paramDirection`）。
- Produces:
  - `class RequestMapper { static Map<String,Object> mapInParams(Map<String,Object> platformParams, List<DsInterfaceParamDO> params); }`——对每个入参定义（`paramDirection==1`）：取 `platformParams.get(platformField)`，为空时用 `defaultValue`，经 `transformFn` 转换，写入结果 map 的 `providerField`（为空则回落 `platformField`）键。返回供应商侧参数 map。

- [ ] **Step 1: 写失败测试**

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RequestMapperTest {

    private DsInterfaceParamDO in(String platformField, String providerField, String transformFn, String def) {
        DsInterfaceParamDO p = new DsInterfaceParamDO();
        p.setParamDirection(1);
        p.setPlatformField(platformField);
        p.setProviderField(providerField);
        p.setTransformFn(transformFn);
        p.setDefaultValue(def);
        return p;
    }

    @Test
    void mapsPlatformFieldToProviderField() {
        Map<String, Object> out = RequestMapper.mapInParams(
                Map.of("idNo", "110101199001011234"),
                List.of(in("idNo", "cert_no", null, null)));
        assertThat(out).containsEntry("cert_no", "110101199001011234");
    }

    @Test
    void appliesTransformFn() {
        Map<String, Object> out = RequestMapper.mapInParams(
                Map.of("idNo", "abc"),
                List.of(in("idNo", "cert_md5", "MD5", null)));
        assertThat(out).containsEntry("cert_md5", "900150983cd24fb0d6963f7d28e17f72");
    }

    @Test
    void usesDefaultWhenMissing() {
        Map<String, Object> out = RequestMapper.mapInParams(
                Map.of(),
                List.of(in("region", "region", null, "110000")));
        assertThat(out).containsEntry("region", "110000");
    }

    @Test
    void providerFieldBlank_fallsBackToPlatformField() {
        Map<String, Object> out = RequestMapper.mapInParams(
                Map.of("name", "张三"),
                List.of(in("name", "", null, null)));
        assertThat(out).containsEntry("name", "张三");
    }

    @Test
    void ignoresOutParams() {
        DsInterfaceParamDO outParam = in("x", "y", null, null);
        outParam.setParamDirection(2);
        Map<String, Object> out = RequestMapper.mapInParams(Map.of("x", "v"), List.of(outParam));
        assertThat(out).isEmpty();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestMapper {

    private RequestMapper() {}

    public static Map<String, Object> mapInParams(Map<String, Object> platformParams,
            List<DsInterfaceParamDO> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) {
            return result;
        }
        Map<String, Object> src = platformParams == null ? Map.of() : platformParams;
        for (DsInterfaceParamDO p : params) {
            if (p.getParamDirection() == null || p.getParamDirection() != 1) {
                continue; // 只处理入参
            }
            Object raw = src.get(p.getPlatformField());
            String value = raw == null ? null : String.valueOf(raw);
            if (StrUtil.isEmpty(value) && StrUtil.isNotEmpty(p.getDefaultValue())) {
                value = p.getDefaultValue();
            }
            String transformed = TransformFunctions.apply(p.getTransformFn(), value);
            String targetKey = StrUtil.isNotBlank(p.getProviderField())
                    ? p.getProviderField() : p.getPlatformField();
            result.put(targetKey, transformed);
        }
        return result;
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`RequestMapperTest` 5 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎——入参映射(平台→供应商+转换+默认值)"
```

---

### Task 3: 出参抽取 ResponseExtractor（JSONPath → 平台字段）

**Files:**
- Create: `.../engine/ResponseExtractor.java`
- Test: `.../engine/ResponseExtractorTest.java`

**Interfaces:**
- Consumes: Hutool `JSONUtil`；P1 的 `DsInterfaceParamDO`（`paramDirection==2` 的 `jsonPath`/`platformField`）。
- Produces:
  - `class ResponseExtractor { static Map<String,Object> extract(String rawJson, List<DsInterfaceParamDO> params); }`——对每个出参定义按 `jsonPath` 从 `rawJson` 抽取值写入结果 map 的 `platformField` 键。`jsonPath` 内部去掉前导 `$.`/`$` 再交 Hutool `getByPath`；抽取不到记 null；`rawJson` 非法 JSON 抛 `IllegalArgumentException`（由上层归一 3001）。

- [ ] **Step 1: 写失败测试**

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ResponseExtractorTest {

    private DsInterfaceParamDO out(String platformField, String jsonPath) {
        DsInterfaceParamDO p = new DsInterfaceParamDO();
        p.setParamDirection(2);
        p.setPlatformField(platformField);
        p.setJsonPath(jsonPath);
        return p;
    }

    @Test
    void extractsNestedField_withDollarPrefix() {
        String json = "{\"data\":{\"entName\":\"某某公司\",\"regCap\":1000}}";
        Map<String, Object> r = ResponseExtractor.extract(json, List.of(
                out("companyName", "$.data.entName"),
                out("registeredCapital", "$.data.regCap")));
        assertThat(r).containsEntry("companyName", "某某公司");
        assertThat(String.valueOf(r.get("registeredCapital"))).isEqualTo("1000");
    }

    @Test
    void extractsArrayElement() {
        String json = "{\"list\":[{\"nm\":\"A\"},{\"nm\":\"B\"}]}";
        Map<String, Object> r = ResponseExtractor.extract(json, List.of(out("first", "list[0].nm")));
        assertThat(r).containsEntry("first", "A");
    }

    @Test
    void missingPath_yieldsNull() {
        String json = "{\"data\":{}}";
        Map<String, Object> r = ResponseExtractor.extract(json, List.of(out("x", "$.data.nope")));
        assertThat(r).containsKey("x");
        assertThat(r.get("x")).isNull();
    }

    @Test
    void ignoresInParams() {
        DsInterfaceParamDO inParam = out("x", "$.a");
        inParam.setParamDirection(1);
        String json = "{\"a\":\"v\"}";
        assertThat(ResponseExtractor.extract(json, List.of(inParam))).isEmpty();
    }

    @Test
    void invalidJson_throws() {
        assertThatThrownBy(() -> ResponseExtractor.extract("not-json", List.of(out("x", "$.a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseExtractor {

    private ResponseExtractor() {}

    public static Map<String, Object> extract(String rawJson, List<DsInterfaceParamDO> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) {
            return result;
        }
        JSON json;
        try {
            json = JSONUtil.parse(rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("上游响应非法 JSON: " + e.getMessage(), e);
        }
        for (DsInterfaceParamDO p : params) {
            if (p.getParamDirection() == null || p.getParamDirection() != 2) {
                continue; // 只处理出参
            }
            String path = normalizePath(p.getJsonPath());
            Object value = StrUtil.isBlank(path) ? null : json.getByPath(path);
            result.put(p.getPlatformField(), value);
        }
        return result;
    }

    /** Hutool getByPath 使用点/方括号语法，不带 $. 前缀；去掉前导 $ 或 $. */
    private static String normalizePath(String jsonPath) {
        if (StrUtil.isBlank(jsonPath)) {
            return jsonPath;
        }
        String p = jsonPath.trim();
        if (p.startsWith("$.")) {
            return p.substring(2);
        }
        if (p.startsWith("$")) {
            return p.substring(1);
        }
        return p;
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`ResponseExtractorTest` 5 用例全过。

> 注：若 `extractsArrayElement`（`list[0].nm`）在 Hutool 5.8.46 的 `getByPath` 下解析异常，按 Hutool `BeanPath` 实际语法调整 `normalizePath`（如把 `[n]` 归一为 `.n`），并在报告记录最终语法。该用例即为守护。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎——出参 JSONPath 抽取"
```

---

### Task 4: 应答码解析 ResponseCodeResolver（原始码→平台码+四标记）

**Files:**
- Create: `.../engine/CodeResolution.java`
- Create: `.../engine/ResponseCodeResolver.java`
- Test: `.../engine/ResponseCodeResolverTest.java`

**Interfaces:**
- Consumes: P1 的 `DsResponseCodeMapper.selectByScopeAndRawCode(dataSourceId, dsInterfaceId, rawCode)`（已存在）；`DsResponseCodeDO`；apiten-common `PlatformErrorCode`。
- Produces:
  - `class CodeResolution { String platformCode; boolean success; boolean charge; boolean retryable; boolean triggerSwitch; boolean mapped; }`（`@Data`）。
  - `@Component class ResponseCodeResolver { CodeResolution resolve(Long dataSourceId, Long dsInterfaceId, String rawCode); }`——先按接口级（`dsInterfaceId`）查，未命中回落数据源级（`dsInterfaceId=0`）；仍未命中或命中但 `platformCode` 空则归一 `UPSTREAM_ERROR`（3001）、`mapped=false`、四标记 false、记 warn；命中则用 DO 的 `platformCode` 与四标记。

- [ ] **Step 1: 写失败测试（H2，真实 mapper）**

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ResponseCodeResolver.class)
class ResponseCodeResolverTest extends BaseDbUnitTest {

    @Resource private ResponseCodeResolver resolver;
    @Resource private DsResponseCodeMapper mapper;

    private void insert(Long dsId, Long ifId, String raw, String platform,
            boolean success, boolean charge, boolean retryable, boolean sw) {
        DsResponseCodeDO d = new DsResponseCodeDO();
        d.setDataSourceId(dsId); d.setDsInterfaceId(ifId); d.setRawCode(raw);
        d.setPlatformCode(platform); d.setSuccess(success); d.setCharge(charge);
        d.setRetryable(retryable); d.setTriggerSwitch(sw);
        mapper.insert(d);
    }

    @Test
    void interfaceLevel_hit_mapsFlags() {
        insert(10L, 100L, "0", "0000", true, true, false, false);
        CodeResolution r = resolver.resolve(10L, 100L, "0");
        assertThat(r.isMapped()).isTrue();
        assertThat(r.getPlatformCode()).isEqualTo("0000");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isCharge()).isTrue();
    }

    @Test
    void fallsBackToDataSourceLevel() {
        insert(10L, 0L, "E99", "3001", false, false, true, true);
        CodeResolution r = resolver.resolve(10L, 100L, "E99");
        assertThat(r.isMapped()).isTrue();
        assertThat(r.getPlatformCode()).isEqualTo("3001");
        assertThat(r.isTriggerSwitch()).isTrue();
    }

    @Test
    void unmapped_normalizesTo3001() {
        CodeResolution r = resolver.resolve(10L, 100L, "UNKNOWN");
        assertThat(r.isMapped()).isFalse();
        assertThat(r.getPlatformCode()).isEqualTo("3001");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void hitButBlankPlatformCode_treatedAsUnmapped() {
        insert(10L, 100L, "X", "", false, false, false, false);
        CodeResolution r = resolver.resolve(10L, 100L, "X");
        assertThat(r.isMapped()).isFalse();
        assertThat(r.getPlatformCode()).isEqualTo("3001");
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

`CodeResolution.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;

@Data
public class CodeResolution {
    private String platformCode;
    private boolean success;
    private boolean charge;
    private boolean retryable;
    private boolean triggerSwitch;
    private boolean mapped;
}
```

`ResponseCodeResolver.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.apiten.common.api.PlatformErrorCode;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

@Component
@Slf4j
public class ResponseCodeResolver {

    @Resource
    private DsResponseCodeMapper responseCodeMapper;

    public CodeResolution resolve(Long dataSourceId, Long dsInterfaceId, String rawCode) {
        DsResponseCodeDO hit = responseCodeMapper.selectByScopeAndRawCode(dataSourceId, dsInterfaceId, rawCode);
        if (hit == null && dsInterfaceId != null && dsInterfaceId != 0L) {
            hit = responseCodeMapper.selectByScopeAndRawCode(dataSourceId, 0L, rawCode); // 回落数据源级
        }
        CodeResolution r = new CodeResolution();
        if (hit == null || StrUtil.isBlank(hit.getPlatformCode())) {
            log.warn("[response-code] 未映射原始码 dsId={} ifId={} raw={}，归一为 {}",
                    dataSourceId, dsInterfaceId, rawCode, PlatformErrorCode.UPSTREAM_ERROR.getCode());
            r.setPlatformCode(PlatformErrorCode.UPSTREAM_ERROR.getCode());
            r.setMapped(false);
            return r;
        }
        r.setPlatformCode(hit.getPlatformCode());
        r.setSuccess(Boolean.TRUE.equals(hit.getSuccess()));
        r.setCharge(Boolean.TRUE.equals(hit.getCharge()));
        r.setRetryable(Boolean.TRUE.equals(hit.getRetryable()));
        r.setTriggerSwitch(Boolean.TRUE.equals(hit.getTriggerSwitch()));
        r.setMapped(true);
        return r;
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`ResponseCodeResolverTest` 4 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎——应答码解析(接口级→数据源级回落，未映射归一3001)"
```

---

### Task 5: HTTP 执行器 HttpInvoker（Spring RestClient，超时+重试）

**Files:**
- Create: `.../engine/HttpCallResult.java`
- Create: `.../engine/HttpInvoker.java`
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/config/AdapterHttpConfig.java`
- Test: `.../engine/HttpInvokerTest.java`

**Interfaces:**
- Consumes: Spring `RestClient.Builder`（config bean）；P1 的 `DsInterfaceDO`（`uri/method/timeoutMs/retryCount`，由上层传入）。
- Produces:
  - `class HttpCallResult { int statusCode; String rawResponseBody; String requestMethod; String requestUrl; Map<String,String> requestHeaders; String requestBody; }`（`@Data`）。
  - `@Component class HttpInvoker { HttpCallResult call(String method, String url, Map<String,String> headers, String body, int timeoutMs, int retryCount); }`——`RestClient` 发请求；4xx/5xx 不抛（交由应答码解析）；对 IO 异常或 5xx 最多重试 `retryCount` 次；填充请求元数据。构造器注入 `RestClient.Builder`。

- [ ] **Step 1: 写失败测试（MockRestServiceServer，离线）**

```java
package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpInvokerTest {

    @Test
    void postReturnsBodyAndStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://upstream/query"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string("{\"cert\":\"x\"}"))
              .andRespond(withSuccess("{\"code\":\"0\",\"data\":{}}", MediaType.APPLICATION_JSON));

        HttpInvoker invoker = new HttpInvoker(builder);
        HttpCallResult r = invoker.call("POST", "http://upstream/query",
                Map.of("Content-Type", "application/json"), "{\"cert\":\"x\"}", 3000, 0);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getRawResponseBody()).contains("\"code\":\"0\"");
        assertThat(r.getRequestUrl()).isEqualTo("http://upstream/query");
        server.verify();
    }

    @Test
    void serverError_returnedNotThrown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://upstream/e"))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        HttpInvoker invoker = new HttpInvoker(builder);
        HttpCallResult r = invoker.call("GET", "http://upstream/e", Map.of(), null, 3000, 0);
        assertThat(r.getStatusCode()).isEqualTo(500);
        assertThat(r.getRawResponseBody()).isEqualTo("boom");
    }
}
```

> 注：`MockRestServiceServer.bindTo(builder)` 要求 `HttpInvoker` 用**注入的 builder** 构造 `RestClient`（`builder.build()`），不能方法内 `RestClient.create()` 另建，否则 mock 不生效。真实网络超时/重试计数无法用 MockRestServiceServer 精确断言；本期两个用例覆盖「成功」「5xx 返回不抛」，重试逻辑靠代码审查保证。

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

`HttpCallResult.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;
import java.util.Map;

@Data
public class HttpCallResult {
    private int statusCode;
    private String rawResponseBody;
    private String requestMethod;
    private String requestUrl;
    private Map<String, String> requestHeaders;
    private String requestBody;
}
```

`AdapterHttpConfig.java`：

```java
package cn.iocoder.yudao.module.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AdapterHttpConfig {

    @Bean
    public RestClient.Builder adapterRestClientBuilder() {
        return RestClient.builder();
    }
}
```

`HttpInvoker.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
@Slf4j
public class HttpInvoker {

    private final RestClient client;

    public HttpInvoker(RestClient.Builder adapterRestClientBuilder) {
        this.client = adapterRestClientBuilder.build();
    }

    public HttpCallResult call(String method, String url, Map<String, String> headers,
            String body, int timeoutMs, int retryCount) {
        HttpCallResult result = new HttpCallResult();
        result.setRequestMethod(method);
        result.setRequestUrl(url);
        result.setRequestHeaders(headers == null ? Map.of() : headers);
        result.setRequestBody(body);

        int attempts = Math.max(0, retryCount) + 1;
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                RestClient.RequestBodySpec spec =
                        client.method(HttpMethod.valueOf(method.toUpperCase())).uri(url);
                if (headers != null) {
                    headers.forEach(spec::header);
                }
                if (body != null) {
                    spec.body(body);
                }
                var resp = spec.retrieve()
                        .onStatus(s -> true, (req, res) -> { }) // 4xx/5xx 不抛
                        .toEntity(String.class);
                result.setStatusCode(resp.getStatusCode().value());
                result.setRawResponseBody(resp.getBody());
                if (resp.getStatusCode().is5xxServerError() && i < attempts - 1) {
                    continue; // 5xx 重试
                }
                return result;
            } catch (RuntimeException e) { // IO/连接异常
                last = e;
                log.warn("[http] 调用失败 attempt {}/{} url={} err={}", i + 1, attempts, url, e.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("HTTP 调用失败: " + url);
    }
}
```

> 超时说明：`SimpleClientHttpRequestFactory` 的连接/读取超时需设在 `RestClient.Builder`（可在 `AdapterHttpConfig` 里对 builder 预置 `requestFactory`，用 `ClientHttpRequestFactorySettings`/`SimpleClientHttpRequestFactory` 设 `timeoutMs`）。**为不破坏 MockRestServiceServer 绑定**，本任务不在 `HttpInvoker` 内 clone/覆盖 requestFactory（那会解绑 mock）。若需要按接口 `timeoutMs` 动态设超时且保持可测，实现者可在报告中标注：本期超时由 config 层统一默认值兜底，按接口动态超时留待集成测试完善——优先保证测试离线通过与生产有默认超时。

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`HttpInvokerTest` 2 用例全过。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎——RestClient 执行器(4xx/5xx不抛，5xx重试)"
```

---

### Task 6: 引擎编排 HttpAdapterEngine + 联调测试台端点

**Files:**
- Create: `.../engine/EngineResult.java`
- Create: `.../engine/HttpAdapterEngine.java`
- Create: `.../controller/admin/datasource/vo/DsInterfaceTestReqVO.java`、`DsInterfaceTestRespVO.java`
- Create: `.../controller/admin/datasource/DsInterfaceTestController.java`
- Modify: `.../enums/ErrorCodeConstants.java`（加 `1_020_005_000`）
- Test: `.../engine/HttpAdapterEngineTest.java`

**Interfaces:**
- Consumes: `RequestMapper`/`TemplateRenderer`/`ResponseExtractor`（静态工具）、`ResponseCodeResolver`/`HttpInvoker`（bean）；P1 的 `DsInterfaceMapper`/`DsInterfaceParamMapper`。
- Produces:
  - `class EngineResult { HttpCallResult rawCall; Map<String,Object> mappedData; String platformCode; boolean success; boolean charge; boolean retryable; boolean triggerSwitch; boolean codeMapped; }`（`@Data`）。
  - `@Component class HttpAdapterEngine { EngineResult invoke(Long dsInterfaceId, Map<String,Object> platformParams); }`。
  - HTTP `POST /adapter/ds-interface/{id}/test`（body=`DsInterfaceTestReqVO{params}`）→ `DsInterfaceTestRespVO`；权限串 `adapter:ds-interface:test`。
  - `ErrorCodeConstants.ADAPTER_ENGINE_INVOKE_FAILED = new ErrorCode(1_020_005_000, "适配调用失败")`。

**原始码取值约定（本期）**：以出参映射中 `platformField == "__rawCode__"` 的定义为原始码抽取项（其 `jsonPath` 指向上游响应里的原始码）；引擎抽取后取该键为 rawCode 交解析器（并从 mappedData 移除该内部键）；若无此约定项则 rawCode 为空 → 必然未映射 → 3001。此约定在测试与报告写明，后续升级为接口级独立字段。

- [ ] **Step 1: ErrorCode + 写失败测试（集成）**

`ErrorCodeConstants` 追加：

```java
    // ========== 适配引擎 1-020-005-xxx ==========
    ErrorCode ADAPTER_ENGINE_INVOKE_FAILED = new ErrorCode(1_020_005_000, "适配调用失败");
```

`HttpAdapterEngineTest.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import jakarta.annotation.Resource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Import({HttpAdapterEngine.class, ResponseCodeResolver.class, HttpInvoker.class,
        HttpAdapterEngineTest.MockHttpConfig.class})
class HttpAdapterEngineTest extends BaseDbUnitTest {

    @Resource private HttpAdapterEngine engine;
    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private DsResponseCodeMapper codeMapper;
    @Resource private MockRestServiceServer mockServer;

    @TestConfiguration
    static class MockHttpConfig {
        private final RestClient.Builder builder = RestClient.builder();
        @Bean RestClient.Builder adapterRestClientBuilder() { return builder; }
        @Bean MockRestServiceServer mockRestServiceServer() { return MockRestServiceServer.bindTo(builder).build(); }
    }

    private Long newInterface(String ifCode, Long dsId, String uri) {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode(ifCode); dif.setName("接口"); dif.setDataSourceId(dsId);
        dif.setUri(uri); dif.setMethod("POST"); dif.setMsgFormat(1);
        dif.setStatus(0); dif.setVersion("v1"); dif.setTimeoutMs(3000); dif.setRetryCount(0);
        interfaceMapper.insert(dif);
        return dif.getId();
    }

    private void outParam(Long ifId, String platformField, String jsonPath) {
        DsInterfaceParamDO p = new DsInterfaceParamDO();
        p.setDsInterfaceId(ifId); p.setParamDirection(2);
        p.setPlatformField(platformField); p.setJsonPath(jsonPath);
        paramMapper.insert(p);
    }

    @Test
    void invoke_rendersCallsExtractsAndMapsCode() {
        Long ifId = newInterface("IF000001", 10L, "http://upstream/company");
        DsInterfaceParamDO in = new DsInterfaceParamDO();
        in.setDsInterfaceId(ifId); in.setParamDirection(1);
        in.setPlatformField("idNo"); in.setProviderField("cert_no");
        paramMapper.insert(in);
        outParam(ifId, "companyName", "$.data.entName");
        outParam(ifId, "__rawCode__", "$.code");
        DsResponseCodeDO rc = new DsResponseCodeDO();
        rc.setDataSourceId(10L); rc.setDsInterfaceId(ifId); rc.setRawCode("0");
        rc.setPlatformCode("0000"); rc.setSuccess(true); rc.setCharge(true);
        rc.setRetryable(false); rc.setTriggerSwitch(false);
        codeMapper.insert(rc);

        mockServer.expect(requestTo("http://upstream/company"))
                .andRespond(withSuccess("{\"code\":\"0\",\"data\":{\"entName\":\"某某公司\"}}",
                        MediaType.APPLICATION_JSON));

        EngineResult r = engine.invoke(ifId, Map.of("idNo", "110101199001011234"));

        assertThat(r.getPlatformCode()).isEqualTo("0000");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isCharge()).isTrue();
        assertThat(r.getMappedData()).containsEntry("companyName", "某某公司");
        assertThat(r.getMappedData()).doesNotContainKey("__rawCode__");
        assertThat(r.getRawCall().getStatusCode()).isEqualTo(200);
        mockServer.verify();
    }

    @Test
    void invoke_unmappedRawCode_yields3001() {
        Long ifId = newInterface("IF000002", 20L, "http://upstream/x");
        outParam(ifId, "__rawCode__", "$.code");
        mockServer.expect(requestTo("http://upstream/x"))
                .andRespond(withSuccess("{\"code\":\"E404\"}", MediaType.APPLICATION_JSON));

        EngineResult r = engine.invoke(ifId, Map.of());
        assertThat(r.getPlatformCode()).isEqualTo("3001");
        assertThat(r.isCodeMapped()).isFalse();
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

`EngineResult.java`：

```java
package cn.iocoder.yudao.module.adapter.engine;

import lombok.Data;
import java.util.Map;

@Data
public class EngineResult {
    private HttpCallResult rawCall;
    private Map<String, Object> mappedData;
    private String platformCode;
    private boolean success;
    private boolean charge;
    private boolean retryable;
    private boolean triggerSwitch;
    private boolean codeMapped;
}
```

`HttpAdapterEngine.java`（注意：`RequestMapper`/`TemplateRenderer`/`ResponseExtractor` 为静态工具类，**不注入**，直接静态调用）：

```java
package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;

@Component
public class HttpAdapterEngine {

    private static final String RAW_CODE_KEY = "__rawCode__";

    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private ResponseCodeResolver codeResolver;
    @Resource private HttpInvoker httpInvoker;

    public EngineResult invoke(Long dsInterfaceId, Map<String, Object> platformParams) {
        DsInterfaceDO dif = interfaceMapper.selectById(dsInterfaceId);
        if (dif == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
        List<DsInterfaceParamDO> params = paramMapper.selectListByInterface(dsInterfaceId, null);

        // 1. 入参映射
        Map<String, Object> providerParams = RequestMapper.mapInParams(platformParams, params);
        // 2. 渲染 URL 模板 + JSON body
        String url = TemplateRenderer.render(dif.getUri(), providerParams);
        String body = JSONUtil.toJsonStr(providerParams);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        // 3. HTTP 外调
        HttpCallResult call = httpInvoker.call(dif.getMethod(), url, headers, body,
                dif.getTimeoutMs() == null ? 3000 : dif.getTimeoutMs(),
                dif.getRetryCount() == null ? 0 : dif.getRetryCount());
        // 4. 出参抽取 + 原始码
        EngineResult result = new EngineResult();
        result.setRawCall(call);
        Map<String, Object> mapped;
        String rawCode = "";
        try {
            mapped = ResponseExtractor.extract(call.getRawResponseBody(), params);
            Object rc = mapped.remove(RAW_CODE_KEY);
            rawCode = rc == null ? "" : String.valueOf(rc);
        } catch (IllegalArgumentException e) {
            mapped = new HashMap<>(); // 响应非法 JSON → 空数据 + rawCode 空 → 3001
        }
        result.setMappedData(mapped);
        // 5. 应答码解析
        CodeResolution cr = codeResolver.resolve(dif.getDataSourceId(), dsInterfaceId, rawCode);
        result.setPlatformCode(cr.getPlatformCode());
        result.setSuccess(cr.isSuccess());
        result.setCharge(cr.isCharge());
        result.setRetryable(cr.isRetryable());
        result.setTriggerSwitch(cr.isTriggerSwitch());
        result.setCodeMapped(cr.isMapped());
        return result;
    }
}
```

`DsInterfaceTestReqVO.java` / `DsInterfaceTestRespVO.java`（`@Data @Schema`：req 含 `Map<String,Object> params`；resp 含 `requestMethod/requestUrl/requestHeaders(Map<String,String>)/requestBody/statusCode(int)/rawResponseBody/mappedData(Map<String,Object>)/platformCode/success/charge/retryable/triggerSwitch/codeMapped`）。

`DsInterfaceTestController.java`：

```java
package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceTestReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceTestRespVO;
import cn.iocoder.yudao.module.adapter.engine.EngineResult;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据源接口联调测试台")
@RestController
@RequestMapping("/adapter/ds-interface")
@Validated
public class DsInterfaceTestController {

    @Resource
    private HttpAdapterEngine engine;

    @PostMapping("/{id}/test")
    @Operation(summary = "联调测试台：按接口发起真实外调并展示映射结果（不计费）")
    @Parameter(name = "id", description = "接口ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:test')")
    public CommonResult<DsInterfaceTestRespVO> test(@PathVariable("id") Long id,
            @Valid @RequestBody DsInterfaceTestReqVO reqVO) {
        EngineResult r = engine.invoke(id, reqVO.getParams());
        DsInterfaceTestRespVO vo = new DsInterfaceTestRespVO();
        vo.setRequestMethod(r.getRawCall().getRequestMethod());
        vo.setRequestUrl(r.getRawCall().getRequestUrl());
        vo.setRequestHeaders(r.getRawCall().getRequestHeaders());
        vo.setRequestBody(r.getRawCall().getRequestBody());
        vo.setStatusCode(r.getRawCall().getStatusCode());
        vo.setRawResponseBody(r.getRawCall().getRawResponseBody());
        vo.setMappedData(r.getMappedData());
        vo.setPlatformCode(r.getPlatformCode());
        vo.setSuccess(r.isSuccess());
        vo.setCharge(r.isCharge());
        vo.setRetryable(r.isRetryable());
        vo.setTriggerSwitch(r.isTriggerSwitch());
        vo.setCodeMapped(r.isCodeMapped());
        return success(vo);
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：`HttpAdapterEngineTest` 2 用例全过（联调测试台 Controller 走 HTTP 不单测，编译通过即可）。

- [ ] **Step 5: Commit**

```bash
git add yudao-module-adapter/src
git commit -m "feat(adapter): HTTP 引擎编排 HttpAdapterEngine + 联调测试台端点"
```

---

### Task 7: Provider 注册表 + InvokeController 按协议类型路由

**Files:**
- Modify: `apiten-common/src/main/java/cn/apiten/common/adapter/ProviderRequest.java`（加 `Long dsInterfaceId`）
- Create: `yudao-module-adapter/src/main/java/cn/iocoder/yudao/module/adapter/provider/ProviderRegistry.java`
- Create: `.../provider/HttpDataSourceProvider.java`
- Modify: `.../controller/InvokeController.java`
- Test: `.../provider/ProviderRegistryTest.java`
- Test: `.../provider/HttpDataSourceProviderTest.java`

**Interfaces:**
- Consumes: `HttpAdapterEngine`（Task 6）；`DataSourceProvider` SPI；`MockProvider`（P0）。
- Produces:
  - `ProviderRequest` 增 `Long dsInterfaceId`（可空）+ getter/setter。
  - `@Component class ProviderRegistry { ProviderRegistry(List<DataSourceProvider>); DataSourceProvider get(String type); }`——按 `type()` 建 map；未知类型回落 MOCK。
  - `@Component class HttpDataSourceProvider implements DataSourceProvider { String type()=="HTTP"; ProviderResponse invoke(ProviderRequest); }`——`dsInterfaceId` 为空抛 `ADAPTER_ENGINE_INVOKE_FAILED`；调 `HttpAdapterEngine.invoke`，装配 `ProviderResponse{rawCode=platformCode, platformCode, data=mappedData}`。
  - `InvokeController.invoke`：`dsInterfaceId` 非空 → `registry.get("HTTP")`；否则 `registry.get("MOCK")`（P0 兼容）。

- [ ] **Step 1: 写失败测试**

`ProviderRegistryTest.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderRegistryTest {

    static class StubProvider implements DataSourceProvider {
        private final String t;
        StubProvider(String t) { this.t = t; }
        public String type() { return t; }
        public ProviderResponse invoke(ProviderRequest r) {
            ProviderResponse resp = new ProviderResponse();
            resp.setPlatformCode(t);
            return resp;
        }
    }

    @Test
    void getByType() {
        ProviderRegistry reg = new ProviderRegistry(List.of(new StubProvider("MOCK"), new StubProvider("HTTP")));
        assertThat(reg.get("HTTP").type()).isEqualTo("HTTP");
        assertThat(reg.get("MOCK").type()).isEqualTo("MOCK");
    }

    @Test
    void unknownType_fallsBackToMock() {
        ProviderRegistry reg = new ProviderRegistry(List.of(new StubProvider("MOCK")));
        assertThat(reg.get("NOPE").type()).isEqualTo("MOCK");
    }
}
```

`HttpDataSourceProviderTest.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import cn.iocoder.yudao.module.adapter.engine.HttpInvoker;
import cn.iocoder.yudao.module.adapter.engine.ResponseCodeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import jakarta.annotation.Resource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Import({HttpDataSourceProvider.class, HttpAdapterEngine.class, ResponseCodeResolver.class,
        HttpInvoker.class, HttpDataSourceProviderTest.MockHttpConfig.class})
class HttpDataSourceProviderTest extends BaseDbUnitTest {

    @Resource private HttpDataSourceProvider provider;
    @Resource private DsInterfaceMapper interfaceMapper;
    @Resource private DsInterfaceParamMapper paramMapper;
    @Resource private DsResponseCodeMapper codeMapper;
    @Resource private MockRestServiceServer mockServer;

    @TestConfiguration
    static class MockHttpConfig {
        private final RestClient.Builder builder = RestClient.builder();
        @Bean RestClient.Builder adapterRestClientBuilder() { return builder; }
        @Bean MockRestServiceServer mockRestServiceServer() { return MockRestServiceServer.bindTo(builder).build(); }
    }

    @Test
    void invoke_viaEngine_returnsMappedProviderResponse() {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode("IF000001"); dif.setName("x"); dif.setDataSourceId(10L);
        dif.setUri("http://up/q"); dif.setMethod("POST"); dif.setMsgFormat(1);
        dif.setStatus(0); dif.setVersion("v1"); dif.setTimeoutMs(3000); dif.setRetryCount(0);
        interfaceMapper.insert(dif);
        Long ifId = dif.getId();
        DsInterfaceParamDO rawCodeMap = new DsInterfaceParamDO();
        rawCodeMap.setDsInterfaceId(ifId); rawCodeMap.setParamDirection(2);
        rawCodeMap.setPlatformField("__rawCode__"); rawCodeMap.setJsonPath("$.code");
        paramMapper.insert(rawCodeMap);
        DsResponseCodeDO rc = new DsResponseCodeDO();
        rc.setDataSourceId(10L); rc.setDsInterfaceId(ifId); rc.setRawCode("0");
        rc.setPlatformCode("0000"); rc.setSuccess(true); rc.setCharge(true);
        rc.setRetryable(false); rc.setTriggerSwitch(false);
        codeMapper.insert(rc);

        mockServer.expect(requestTo("http://up/q"))
                .andRespond(withSuccess("{\"code\":\"0\"}", MediaType.APPLICATION_JSON));

        ProviderRequest req = new ProviderRequest();
        req.setDsInterfaceId(ifId);
        req.setParams(Map.of());
        ProviderResponse resp = provider.invoke(req);

        assertThat(provider.type()).isEqualTo("HTTP");
        assertThat(resp.getPlatformCode()).isEqualTo("0000");
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test -q`
Expected：编译失败。

- [ ] **Step 3: 最小实现**

`ProviderRequest` 加字段（apiten-common，补 getter/setter）：

```java
    private Long dsInterfaceId; // 显式接口调用（HTTP 引擎）；为空走 productCode 兜底(MOCK)
```

`ProviderRegistry.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<String, DataSourceProvider> byType = new HashMap<>();

    public ProviderRegistry(List<DataSourceProvider> providers) {
        for (DataSourceProvider p : providers) {
            byType.put(p.type(), p);
        }
    }

    public DataSourceProvider get(String type) {
        DataSourceProvider p = byType.get(type);
        return p != null ? p : byType.get("MOCK");
    }
}
```

`HttpDataSourceProvider.java`：

```java
package cn.iocoder.yudao.module.adapter.provider;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.engine.EngineResult;
import cn.iocoder.yudao.module.adapter.engine.HttpAdapterEngine;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.ADAPTER_ENGINE_INVOKE_FAILED;

@Component
public class HttpDataSourceProvider implements DataSourceProvider {

    @Resource
    private HttpAdapterEngine engine;

    @Override
    public String type() { return "HTTP"; }

    @Override
    public ProviderResponse invoke(ProviderRequest request) {
        if (request.getDsInterfaceId() == null) {
            throw exception(ADAPTER_ENGINE_INVOKE_FAILED);
        }
        EngineResult r = engine.invoke(request.getDsInterfaceId(), request.getParams());
        ProviderResponse resp = new ProviderResponse();
        resp.setRawCode(r.getPlatformCode());
        resp.setPlatformCode(r.getPlatformCode());
        resp.setData(r.getMappedData());
        return resp;
    }
}
```

`InvokeController.java`：

```java
package cn.iocoder.yudao.module.adapter.controller;

import cn.apiten.common.adapter.ProviderRequest;
import cn.apiten.common.adapter.ProviderResponse;
import cn.iocoder.yudao.module.adapter.provider.ProviderRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/adapter/v1")
public class InvokeController {

    private final ProviderRegistry registry;

    public InvokeController(ProviderRegistry registry) { this.registry = registry; }

    @PostMapping("/invoke")
    public ProviderResponse invoke(@RequestBody ProviderRequest request) {
        String type = request.getDsInterfaceId() != null ? "HTTP" : "MOCK";
        return registry.get(type).invoke(request);
    }
}
```

> 注：MockProvider 与 HttpDataSourceProvider 均 `@Component`，一起注入 `ProviderRegistry`。openapi 的 `QueryOrchestrator` 经 Feign 调 `/adapter/v1/invoke` 时不带 `dsInterfaceId` → 走 MOCK，行为不变；openapi 侧单测仍离线绿（ProviderRequest 加字段兼容）。

- [ ] **Step 4: 运行验证通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter,yudao-module-openapi -am test -q`
Expected：`ProviderRegistryTest`(2) + `HttpDataSourceProviderTest`(1) 全过；openapi 既有测试仍绿；adapter 全模块绿。

- [ ] **Step 5: Commit**

```bash
git add apiten-common yudao-module-adapter
git commit -m "feat(adapter): Provider 注册表按协议类型路由，HttpDataSourceProvider 接入引擎"
```

---

## P2 完成定义（DoD）

1. `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl yudao-module-adapter -am test` 全绿，离线（H2 + MockRestServiceServer，不连真实上游/中间件）。
2. HTTP 适配引擎完成：入参映射（平台→供应商+转换函数+默认值）、模板渲染、真实 HTTP 外调（4xx/5xx 不抛、5xx 重试）、出参 JSONPath 抽取、应答码解析（接口级→数据源级回落，未映射归一 3001）。
3. 联调测试台端点 `POST /adapter/ds-interface/{id}/test` 可按接口发起真实外调并返回原始请求/原始响应/映射结果/平台码+四标记。
4. Provider 注册表按协议类型路由；`dsInterfaceId` 显式接口调用走 HTTP 引擎，productCode 兜底走 MockProvider（P0 e2e 链路行为不变）。
5. 每任务一个 commit，git log 与本计划任务一一对应；扁平模块范式。
6. 全量构建 `mvn -T 1C clean install -DskipTests` BUILD SUCCESS。

## 后续计划（本计划不含）

- **路由里程碑**：productCode→机构产品→数据源接口的路由解析（静态路由匹配 + 动态选源打分 + 切换链兜底）；届时 openapi 传 productCode，路由层解析出 `dsInterfaceId` 交本引擎。
- 加密类转换函数（AES/RSA/SM2/SM4，需密钥管理）与认证方式（Token 获取/刷新、签名、双向 TLS、IP 白名单出口）；按接口 `timeoutMs` 动态超时的完善（当前由 config 默认兜底）。
- 健康检查探活与被动指标采集（成功率/查得率/P95/切换率，供选源策略与监控）。
- 原始码取值升级：由 `__rawCode__` 出参约定升级为接口级独立配置字段。
- 计费、流水落库与幂等、限流限额与配额、报表、监控。
- 前端页面（数据源管理 + 联调测试台 UI）与 sys_menu 菜单/按钮权限落库。
