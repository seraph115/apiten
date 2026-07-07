package cn.iocoder.yudao.module.adapter.dal.mysql.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DsResponseCodeMapper extends BaseMapperX<DsResponseCodeDO> {

    default PageResult<DsResponseCodeDO> selectPage(DsResponseCodePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eqIfPresent(DsResponseCodeDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsResponseCodeDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .likeIfPresent(DsResponseCodeDO::getRawCode, reqVO.getRawCode())
                .eqIfPresent(DsResponseCodeDO::getPlatformCode, reqVO.getPlatformCode())
                .orderByDesc(DsResponseCodeDO::getId));
    }

    default DsResponseCodeDO selectByScopeAndRawCode(Long dataSourceId, Long dsInterfaceId, String rawCode) {
        return selectOne(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eq(DsResponseCodeDO::getDataSourceId, dataSourceId)
                .eq(DsResponseCodeDO::getDsInterfaceId, dsInterfaceId)
                .eq(DsResponseCodeDO::getRawCode, rawCode));
    }

    default List<DsResponseCodeDO> selectUnmapped(Long dataSourceId) {
        return selectList(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eq(DsResponseCodeDO::getDataSourceId, dataSourceId)
                .and(w -> w.isNull(DsResponseCodeDO::getPlatformCode)
                        .or().eq(DsResponseCodeDO::getPlatformCode, ""))
                .orderByDesc(DsResponseCodeDO::getId));
    }
}
