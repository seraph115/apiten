package cn.iocoder.yudao.module.route.service.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigSaveReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.route.enums.ErrorCodeConstants.ROUTE_CONFIG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(RouteConfigServiceImpl.class)
class RouteConfigServiceImplTest extends BaseDbUnitTest {

    @Resource private RouteConfigServiceImpl service;

    private RouteConfigSaveReqVO req(String productCode, Long orgId, Long dsIfId, int priority) {
        RouteConfigSaveReqVO vo = new RouteConfigSaveReqVO();
        vo.setName("路由");
        vo.setProductCode(productCode);
        vo.setOrgId(orgId);
        vo.setTargetType("SINGLE");
        vo.setTargetDsInterfaceId(dsIfId);
        vo.setPriority(priority);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesRouteCode() {
        Long id = service.createRouteConfig(req("P000001", null, 100L, 0));
        RouteConfigDO db = service.getRouteConfig(id);
        assertThat(db.getRouteCode()).matches("R\\d{6}");
        assertThat(db.getTargetDsInterfaceId()).isEqualTo(100L);
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        Long id2 = service.createRouteConfig(req("P000001", 5L, 200L, 1));
        String c2 = service.getRouteConfig(id2).getRouteCode();
        service.deleteRouteConfig(id2);
        Long id3 = service.createRouteConfig(req("P000002", null, 300L, 0));
        assertThat(service.getRouteConfig(id3).getRouteCode()).isNotEqualTo(c2).matches("R\\d{6}");
    }

    @Test
    void update_notExists_throws() {
        RouteConfigSaveReqVO upd = req("P000001", null, 100L, 0);
        upd.setId(99999L);
        assertServiceException(() -> service.updateRouteConfig(upd), ROUTE_CONFIG_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createRouteConfig(req("P000001", null, 100L, 0));
        service.deleteRouteConfig(id);
        assertThat(service.getRouteConfig(id)).isNull();
    }

    @Test
    void listByProductCode_filters() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        service.createRouteConfig(req("P000001", 5L, 200L, 1));
        service.createRouteConfig(req("P000002", null, 300L, 0));
        assertThat(service.getListByProductCode("P000001")).hasSize(2);
    }

    @Test
    void page_filtersByProductCode() {
        service.createRouteConfig(req("P000001", null, 100L, 0));
        service.createRouteConfig(req("P000002", null, 300L, 0));
        RouteConfigPageReqVO q = new RouteConfigPageReqVO();
        q.setProductCode("P000001");
        PageResult<RouteConfigDO> page = service.getRouteConfigPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
