package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsInterfaceMapper extends BaseMapperX<DsInterfaceDO> {

    default PageResult<DsInterfaceDO> selectPage(DsInterfacePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsInterfaceDO>()
                .likeIfPresent(DsInterfaceDO::getName, reqVO.getName())
                .eqIfPresent(DsInterfaceDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsInterfaceDO::getStatus, reqVO.getStatus())
                .orderByDesc(DsInterfaceDO::getId));
    }

    default List<DsInterfaceDO> selectListByDataSourceId(Long dataSourceId) {
        return selectList(DsInterfaceDO::getDataSourceId, dataSourceId);
    }
}
