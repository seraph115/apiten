package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import jakarta.validation.Valid;

import java.util.List;

public interface OrgProductService {

    Long createOrgProduct(@Valid OrgProductSaveReqVO reqVO);

    void updateOrgProduct(@Valid OrgProductSaveReqVO reqVO);

    void deleteOrgProduct(Long id);

    OrgProductDO getOrgProduct(Long id);

    OrgProductDO getByOrgAndProductCode(Long orgId, String productCode);

    PageResult<OrgProductDO> getOrgProductPage(OrgProductPageReqVO reqVO);

    List<OrgProductDO> getListByOrgId(Long orgId);
}
