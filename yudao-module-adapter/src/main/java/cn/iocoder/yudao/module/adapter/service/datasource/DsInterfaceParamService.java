package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsInterfaceParamService {

    Long createDsInterfaceParam(@Valid DsInterfaceParamSaveReqVO reqVO);

    void updateDsInterfaceParam(@Valid DsInterfaceParamSaveReqVO reqVO);

    void deleteDsInterfaceParam(Long id);

    DsInterfaceParamDO getDsInterfaceParam(Long id);

    PageResult<DsInterfaceParamDO> getDsInterfaceParamPage(DsInterfaceParamPageReqVO reqVO);

    List<DsInterfaceParamDO> getListByInterface(Long dsInterfaceId, Integer paramDirection);
}
