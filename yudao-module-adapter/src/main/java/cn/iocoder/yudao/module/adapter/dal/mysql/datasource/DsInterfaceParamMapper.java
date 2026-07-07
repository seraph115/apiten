package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsInterfaceParamMapper extends BaseMapperX<DsInterfaceParamDO> {

    default PageResult<DsInterfaceParamDO> selectPage(DsInterfaceParamPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsInterfaceParamDO>()
                .eqIfPresent(DsInterfaceParamDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .eqIfPresent(DsInterfaceParamDO::getParamDirection, reqVO.getParamDirection())
                .likeIfPresent(DsInterfaceParamDO::getPlatformField, reqVO.getPlatformField())
                .orderByDesc(DsInterfaceParamDO::getId));
    }

    default List<DsInterfaceParamDO> selectListByInterface(Long dsInterfaceId, Integer paramDirection) {
        return selectList(new LambdaQueryWrapperX<DsInterfaceParamDO>()
                .eq(DsInterfaceParamDO::getDsInterfaceId, dsInterfaceId)
                .eqIfPresent(DsInterfaceParamDO::getParamDirection, paramDirection)
                .orderByAsc(DsInterfaceParamDO::getId));
    }
}
