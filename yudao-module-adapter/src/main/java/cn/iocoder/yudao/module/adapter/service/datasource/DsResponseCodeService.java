package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportExcelVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import jakarta.validation.Valid;
import java.util.List;

public interface DsResponseCodeService {
    Long createDsResponseCode(@Valid DsResponseCodeSaveReqVO reqVO);
    void updateDsResponseCode(@Valid DsResponseCodeSaveReqVO reqVO);
    void deleteDsResponseCode(Long id);
    DsResponseCodeDO getDsResponseCode(Long id);
    PageResult<DsResponseCodeDO> getDsResponseCodePage(DsResponseCodePageReqVO reqVO);
    List<DsResponseCodeDO> getUnmappedList(Long dataSourceId);
    DsResponseCodeImportRespVO importResponseCodes(List<DsResponseCodeImportExcelVO> list, boolean updateSupport);
    List<DsResponseCodeDO> getExportList(DsResponseCodePageReqVO reqVO);
}
