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
