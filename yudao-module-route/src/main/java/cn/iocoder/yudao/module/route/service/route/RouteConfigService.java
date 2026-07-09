package cn.iocoder.yudao.module.route.service.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigSaveReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import jakarta.validation.Valid;
import java.util.List;

public interface RouteConfigService {
    Long createRouteConfig(@Valid RouteConfigSaveReqVO reqVO);
    void updateRouteConfig(@Valid RouteConfigSaveReqVO reqVO);
    void deleteRouteConfig(Long id);
    RouteConfigDO getRouteConfig(Long id);
    PageResult<RouteConfigDO> getRouteConfigPage(RouteConfigPageReqVO reqVO);
    List<RouteConfigDO> getListByProductCode(String productCode);
}
