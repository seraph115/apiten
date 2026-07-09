package cn.iocoder.yudao.module.route.service.resolve;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.route.client.ProductResolveClient;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.dal.mysql.route.RouteConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@Import({RouteResolver.class, RouteResolverTest.StubConfig.class})
class RouteResolverTest extends BaseDbUnitTest {

    @Resource private RouteResolver resolver;
    @Resource private RouteConfigMapper mapper;

    /** 手写 stub：对任意 productCode 返回 dsInterfaceId=stubDefault（模拟产品公底） */
    static Long stubDefault = 900L;

    @TestConfiguration
    static class StubConfig {
        @Bean ProductResolveClient productResolveClient() {
            return productCode -> {
                ProductDefaultRespDTO d = new ProductDefaultRespDTO();
                d.setDsInterfaceId(stubDefault);
                return d;
            };
        }
    }

    private void route(String productCode, Long orgId, String type, Long dsIfId, int priority, int status) {
        RouteConfigDO r = new RouteConfigDO();
        r.setRouteCode("R" + (System.nanoTime() % 1000000));
        r.setName("r"); r.setProductCode(productCode); r.setOrgId(orgId);
        r.setTargetType(type); r.setTargetDsInterfaceId(dsIfId); r.setPriority(priority); r.setStatus(status);
        mapper.insert(r);
    }

    @Test
    void orgSpecificWins_overProductDefaultLevel() {
        route("P000001", null, "SINGLE", 100L, 0, 0); // 产品默认级
        route("P000001", 5L, "SINGLE", 200L, 9, 0);   // 机构产品级(priority 更大但 org 命中优先)
        RouteResolveRespDTO r = resolver.resolve("P000001", 5L);
        assertThat(r.getDsInterfaceId()).isEqualTo(200L);
        assertThat(r.getSource()).isEqualTo("ROUTE_CONFIG");
    }

    @Test
    void productLevelUsed_whenNoOrgMatch() {
        route("P000001", null, "SINGLE", 100L, 0, 0);
        route("P000001", 5L, "SINGLE", 200L, 0, 0);
        RouteResolveRespDTO r = resolver.resolve("P000001", 999L); // orgId 不匹配任何机构行
        assertThat(r.getDsInterfaceId()).isEqualTo(100L); // 回落 orgId=null 产品默认级行
    }

    @Test
    void priorityBreaksTie_withinSameLevel() {
        route("P000001", null, "SINGLE", 100L, 5, 0);
        route("P000001", null, "SINGLE", 300L, 1, 0); // priority 更小
        RouteResolveRespDTO r = resolver.resolve("P000001", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(300L);
    }

    @Test
    void disabledRoute_ignored_fallsBackToProductDefault() {
        route("P000001", null, "SINGLE", 100L, 0, 1); // 停用
        RouteResolveRespDTO r = resolver.resolve("P000001", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(900L); // 无启用 route_config → 产品公底
        assertThat(r.getSource()).isEqualTo("PRODUCT_DEFAULT");
    }

    @Test
    void fallsBackToProductDefault_whenNoRouteConfig() {
        RouteResolveRespDTO r = resolver.resolve("P999999", null);
        assertThat(r.getDsInterfaceId()).isEqualTo(900L);
        assertThat(r.getSource()).isEqualTo("PRODUCT_DEFAULT");
    }

    @Test
    void noTarget_whenNoRouteAndNoProductDefault() {
        stubDefault = null; // 产品也无默认绑定
        try {
            RouteResolveRespDTO r = resolver.resolve("P888888", null);
            assertThat(r.getDsInterfaceId()).isNull();
            assertThat(r.getSource()).isEqualTo("NONE");
            assertThat(r.getPlatformCode()).isEqualTo("3005");
        } finally {
            stubDefault = 900L; // 复原，避免污染其它用例
        }
    }
}
