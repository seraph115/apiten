package cn.apiten.common.route;

import cn.apiten.common.api.PlatformErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RouteDtoTest {

    @Test
    void of_setsFields() {
        RouteResolveRespDTO r = RouteResolveRespDTO.of(100L, "ROUTE_CONFIG");
        assertThat(r.getDsInterfaceId()).isEqualTo(100L);
        assertThat(r.getSource()).isEqualTo("ROUTE_CONFIG");
        assertThat(r.getPlatformCode()).isNull();
    }

    @Test
    void noTarget_carriesRouteNoTargetCode() {
        RouteResolveRespDTO r = RouteResolveRespDTO.noTarget();
        assertThat(r.getDsInterfaceId()).isNull();
        assertThat(r.getSource()).isEqualTo("NONE");
        assertThat(r.getPlatformCode()).isEqualTo("3005");
    }

    @Test
    void routeNoTargetCode_registered() {
        assertThat(PlatformErrorCode.ROUTE_NO_TARGET.getCode()).isEqualTo("3005");
    }

    @Test
    void productDefault_holdsRef() {
        ProductDefaultRespDTO d = new ProductDefaultRespDTO();
        d.setDsInterfaceId(200L);
        d.setDsInterfaceCode("IF000002");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L);
        assertThat(d.getDsInterfaceCode()).isEqualTo("IF000002");
    }
}
