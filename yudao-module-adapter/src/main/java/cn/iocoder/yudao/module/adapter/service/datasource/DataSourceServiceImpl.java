package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;

@Service
@Validated
public class DataSourceServiceImpl implements DataSourceService {

    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDataSource(DataSourceSaveReqVO reqVO) {
        DataSourceDO ds = BeanUtils.toBean(reqVO, DataSourceDO.class);
        ds.setId(null);
        // ds_code 列 NOT NULL + 唯一，先写入临时唯一占位（32 位 UUID，正好落在 varchar(32) 内），
        // 拿到自增 id 后再据 id 回填最终编码；整个过程在同一事务内，外部只可见最终编码
        ds.setDsCode(UUID.randomUUID().toString().replace("-", ""));
        dataSourceMapper.insert(ds);            // id 由 DB 自增分配
        ds.setDsCode(String.format("DS%06d", ds.getId()));
        dataSourceMapper.updateById(ds);        // 回填编码
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
}
