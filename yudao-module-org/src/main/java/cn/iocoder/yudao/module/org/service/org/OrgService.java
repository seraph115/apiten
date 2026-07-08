package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import jakarta.validation.Valid;
import java.util.List;

public interface OrgService {
    Long createOrg(@Valid OrgSaveReqVO reqVO);
    void updateOrg(@Valid OrgSaveReqVO reqVO);
    void deleteOrg(Long id);
    OrgDO getOrg(Long id);
    OrgDO getOrgByCode(String orgCode);
    PageResult<OrgDO> getOrgPage(OrgPageReqVO reqVO);
    List<OrgDO> getSimpleList();
}
