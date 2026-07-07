package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DataSourceService {
    Long createDataSource(@Valid DataSourceSaveReqVO reqVO);
    void updateDataSource(@Valid DataSourceSaveReqVO reqVO);
    void deleteDataSource(Long id);
    DataSourceDO getDataSource(Long id);
    PageResult<DataSourceDO> getDataSourcePage(DataSourcePageReqVO reqVO);
    List<DataSourceDO> getSimpleList();
}
