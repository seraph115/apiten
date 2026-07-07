package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsInterfaceService {
    Long createDsInterface(@Valid DsInterfaceSaveReqVO reqVO);
    void updateDsInterface(@Valid DsInterfaceSaveReqVO reqVO);
    void deleteDsInterface(Long id);
    DsInterfaceDO getDsInterface(Long id);
    PageResult<DsInterfaceDO> getDsInterfacePage(DsInterfacePageReqVO reqVO);
    List<DsInterfaceDO> getListByDataSourceId(Long dataSourceId);
}
