package cn.iocoder.yudao.module.route.dal.mysql.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RouteConfigMapper extends BaseMapperX<RouteConfigDO> {

    default PageResult<RouteConfigDO> selectPage(RouteConfigPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RouteConfigDO>()
                .eqIfPresent(RouteConfigDO::getProductCode, reqVO.getProductCode())
                .eqIfPresent(RouteConfigDO::getOrgId, reqVO.getOrgId())
                .eqIfPresent(RouteConfigDO::getTargetType, reqVO.getTargetType())
                .eqIfPresent(RouteConfigDO::getStatus, reqVO.getStatus())
                .orderByDesc(RouteConfigDO::getId));
    }

    default List<RouteConfigDO> selectListByProductCode(String productCode) {
        return selectList(RouteConfigDO::getProductCode, productCode);
    }

    /**
     * 按产品编码 + 机构ID 匹配可用路由配置，供 Task 5 路由解析消费。
     * orgId 为 null：仅匹配产品默认级（org_id IS NULL）；
     * orgId 非空：匹配该机构的机构级 或 产品默认级（org_id = orgId OR org_id IS NULL）。
     */
    default List<RouteConfigDO> selectMatched(String productCode, Long orgId) {
        LambdaQueryWrapperX<RouteConfigDO> w = new LambdaQueryWrapperX<RouteConfigDO>()
                .eq(RouteConfigDO::getProductCode, productCode)
                .eq(RouteConfigDO::getStatus, 0);
        if (orgId == null) {
            w.isNull(RouteConfigDO::getOrgId);
        } else {
            w.and(x -> x.eq(RouteConfigDO::getOrgId, orgId).or().isNull(RouteConfigDO::getOrgId));
        }
        return selectList(w);
    }
}
