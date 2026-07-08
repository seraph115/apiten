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
