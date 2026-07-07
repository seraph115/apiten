package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSourceMapper extends BaseMapperX<DataSourceDO> {

    default PageResult<DataSourceDO> selectPage(DataSourcePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DataSourceDO>()
                .likeIfPresent(DataSourceDO::getName, reqVO.getName())
                .eqIfPresent(DataSourceDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(DataSourceDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DataSourceDO::getProtocolType, reqVO.getProtocolType())
                .orderByDesc(DataSourceDO::getId));
    }

    default DataSourceDO selectByDsCode(String dsCode) {
        return selectOne(DataSourceDO::getDsCode, dsCode);
    }
}
