package cn.iocoder.yudao.module.route.service.resolve;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.apiten.common.route.RouteResolveRespDTO;
import cn.iocoder.yudao.module.route.client.ProductResolveClient;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.dal.mysql.route.RouteConfigMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;

@Component
public class RouteResolver {

    @Resource private RouteConfigMapper routeConfigMapper;
    @Resource private ProductResolveClient productResolveClient;

    public RouteResolveRespDTO resolve(String productCode, Long orgId) {
        List<RouteConfigDO> matched = routeConfigMapper.selectMatched(productCode, orgId);
        RouteConfigDO best = matched.stream()
                .filter(r -> "SINGLE".equals(r.getTargetType()) && r.getTargetDsInterfaceId() != null)
                // 机构行(orgId!=null)优先于产品默认级(orgId==null)；同层 priority 升序
                .min(Comparator
                        .comparing((RouteConfigDO r) -> r.getOrgId() == null) // false(机构行) 排前
                        .thenComparingInt(r -> r.getPriority() == null ? 0 : r.getPriority()))
                .orElse(null);
        if (best != null) {
            return RouteResolveRespDTO.of(best.getTargetDsInterfaceId(), "ROUTE_CONFIG");
        }
        ProductDefaultRespDTO def = productResolveClient.resolveDefault(productCode);
        if (def != null && def.getDsInterfaceId() != null) {
            return RouteResolveRespDTO.of(def.getDsInterfaceId(), "PRODUCT_DEFAULT");
        }
        return RouteResolveRespDTO.noTarget();
    }
}
