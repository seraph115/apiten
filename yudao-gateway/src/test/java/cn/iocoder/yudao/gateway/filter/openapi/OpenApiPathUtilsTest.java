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
