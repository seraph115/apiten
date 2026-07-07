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
