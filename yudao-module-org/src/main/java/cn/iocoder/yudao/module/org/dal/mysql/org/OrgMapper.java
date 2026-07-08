package cn.iocoder.yudao.module.org.dal.mysql.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgMapper extends BaseMapperX<OrgDO> {

    default PageResult<OrgDO> selectPage(OrgPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<OrgDO>()
                .likeIfPresent(OrgDO::getName, reqVO.getName())
                .eqIfPresent(OrgDO::getStatus, reqVO.getStatus())
                .orderByDesc(OrgDO::getId));
    }

    default OrgDO selectByOrgCode(String orgCode) {
        return selectOne(OrgDO::getOrgCode, orgCode);
    }
}
