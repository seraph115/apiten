package cn.iocoder.yudao.module.org.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrgProductMapper extends BaseMapperX<OrgProductDO> {

    default PageResult<OrgProductDO> selectPage(OrgProductPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OrgProductDO>()
                .eqIfPresent(OrgProductDO::getOrgId, reqVO.getOrgId())
                .eqIfPresent(OrgProductDO::getProductCode, reqVO.getProductCode())
                .eqIfPresent(OrgProductDO::getStatus, reqVO.getStatus())
                .orderByDesc(OrgProductDO::getId));
    }

    default OrgProductDO selectByOrgAndProductCode(Long orgId, String productCode) {
        return selectOne(new LambdaQueryWrapperX<OrgProductDO>()
                .eq(OrgProductDO::getOrgId, orgId)
                .eq(OrgProductDO::getProductCode, productCode));
    }

    default List<OrgProductDO> selectListByOrgId(Long orgId) {
        return selectList(OrgProductDO::getOrgId, orgId);
    }
}
