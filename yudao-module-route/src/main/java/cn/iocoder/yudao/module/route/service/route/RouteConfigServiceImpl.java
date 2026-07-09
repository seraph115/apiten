package cn.iocoder.yudao.module.route.service.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigSaveReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.dal.mysql.route.RouteConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.route.enums.ErrorCodeConstants.ROUTE_CONFIG_NOT_EXISTS;

@Service
@Validated
public class RouteConfigServiceImpl implements RouteConfigService {

    @Resource
    private RouteConfigMapper routeConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRouteConfig(RouteConfigSaveReqVO reqVO) {
        RouteConfigDO route = BeanUtils.toBean(reqVO, RouteConfigDO.class);
        route.setId(null);
        if (!org.springframework.util.StringUtils.hasText(route.getTargetType())) {
            route.setTargetType("SINGLE");
        }
        // route_code 列 NOT NULL + 唯一，先写入临时唯一占位（32 位 UUID，正好落在 varchar(32) 内），
        // 拿到自增 id 后再据 id 回填最终编码；整个过程在同一事务内，外部只可见最终编码
        route.setRouteCode(UUID.randomUUID().toString().replace("-", ""));
        routeConfigMapper.insert(route);              // id 由 DB 自增分配
        route.setRouteCode(String.format("R%06d", route.getId()));
        routeConfigMapper.updateById(route);          // 回填编码
        return route.getId();
    }

    @Override
    public void updateRouteConfig(RouteConfigSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        RouteConfigDO route = BeanUtils.toBean(reqVO, RouteConfigDO.class);
        route.setRouteCode(null); // 编码不可改
        routeConfigMapper.updateById(route);
    }

    @Override
    public void deleteRouteConfig(Long id) {
        validateExists(id);
        routeConfigMapper.deleteById(id);
    }

    @Override
    public RouteConfigDO getRouteConfig(Long id) {
        return routeConfigMapper.selectById(id);
    }

    @Override
    public PageResult<RouteConfigDO> getRouteConfigPage(RouteConfigPageReqVO reqVO) {
        return routeConfigMapper.selectPage(reqVO);
    }

    @Override
    public List<RouteConfigDO> getListByProductCode(String productCode) {
        return routeConfigMapper.selectListByProductCode(productCode);
    }

    private RouteConfigDO validateExists(Long id) {
        RouteConfigDO route = routeConfigMapper.selectById(id);
        if (route == null) {
            throw exception(ROUTE_CONFIG_NOT_EXISTS);
        }
        return route;
    }
}
