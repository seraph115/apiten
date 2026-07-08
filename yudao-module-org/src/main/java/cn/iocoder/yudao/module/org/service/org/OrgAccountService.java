package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import jakarta.validation.Valid;

import java.util.List;

public interface OrgAccountService {

    Long createAccount(@Valid OrgAccountSaveReqVO reqVO);

    void updateAccount(@Valid OrgAccountSaveReqVO reqVO);

    void deleteAccount(Long id);

    OrgAccountDO getAccount(Long id);

    OrgAccountDO getAccountByAppKey(String appKey);

    String resetSecret(Long id);

    PageResult<OrgAccountDO> getAccountPage(OrgAccountPageReqVO reqVO);

    List<OrgAccountDO> getListByOrgId(Long orgId);
}
