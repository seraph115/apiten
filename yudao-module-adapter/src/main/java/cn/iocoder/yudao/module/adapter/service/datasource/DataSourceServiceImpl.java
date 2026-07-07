package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;

@Service
@Validated
public class DataSourceServiceImpl implements DataSourceService {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    public Long createDataSource(DataSourceSaveReqVO reqVO) {
        DataSourceDO ds = BeanUtils.toBean(reqVO, DataSourceDO.class);
        ds.setId(null);
        ds.setDsCode(generateDsCode());
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    @Override
    public void updateDataSource(DataSourceSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        DataSourceDO ds = BeanUtils.toBean(reqVO, DataSourceDO.class);
        ds.setDsCode(null); // 编码不可改
        dataSourceMapper.updateById(ds);
    }

    @Override
    public void deleteDataSource(Long id) {
        validateExists(id);
        dataSourceMapper.deleteById(id);
    }

    @Override
    public DataSourceDO getDataSource(Long id) {
        return dataSourceMapper.selectById(id);
    }

    @Override
    public PageResult<DataSourceDO> getDataSourcePage(DataSourcePageReqVO reqVO) {
        return dataSourceMapper.selectPage(reqVO);
    }

    @Override
    public List<DataSourceDO> getSimpleList() {
        return dataSourceMapper.selectList(new LambdaQueryWrapperX<DataSourceDO>()
                .orderByDesc(DataSourceDO::getId));
    }

    private DataSourceDO validateExists(Long id) {
        DataSourceDO ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw exception(DATA_SOURCE_NOT_EXISTS);
        }
        return ds;
    }

    private String generateDsCode() {
        long next = dataSourceMapper.selectMaxId() + 1;
        return String.format("DS%06d", next);
    }
}
