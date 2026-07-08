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
