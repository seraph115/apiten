package cn.iocoder.yudao.module.org.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountPageReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrgAccountMapper extends BaseMapperX<OrgAccountDO> {

    default PageResult<OrgAccountDO> selectPage(OrgAccountPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OrgAccountDO>()
                .eqIfPresent(OrgAccountDO::getOrgId, reqVO.getOrgId())
                .eqIfPresent(OrgAccountDO::getAppKey, reqVO.getAppKey())
                .eqIfPresent(OrgAccountDO::getStatus, reqVO.getStatus())
                .orderByDesc(OrgAccountDO::getId));
    }

    default OrgAccountDO selectByAppKey(String appKey) {
        return selectOne(OrgAccountDO::getAppKey, appKey);
    }

    default List<OrgAccountDO> selectListByOrgId(Long orgId) {
        return selectList(OrgAccountDO::getOrgId, orgId);
    }
}
