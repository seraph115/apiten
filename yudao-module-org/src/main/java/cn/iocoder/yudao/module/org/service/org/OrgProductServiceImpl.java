package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_PRODUCT_DUPLICATE;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_PRODUCT_NOT_EXISTS;

@Service
@Validated
public class OrgProductServiceImpl implements OrgProductService {

    @Resource
    private OrgProductMapper orgProductMapper;
    @Resource
    private OrgMapper orgMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrgProduct(OrgProductSaveReqVO reqVO) {
        if (orgMapper.selectById(reqVO.getOrgId()) == null) {
            throw exception(ORG_NOT_EXISTS);
        }
        if (orgProductMapper.selectByOrgAndProductCode(reqVO.getOrgId(), reqVO.getProductCode()) != null) {
            throw exception(ORG_PRODUCT_DUPLICATE);
        }
        OrgProductDO entity = BeanUtils.toBean(reqVO, OrgProductDO.class);
        entity.setId(null);
        orgProductMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateOrgProduct(OrgProductSaveReqVO reqVO) {
        validateOrgProductExists(reqVO.getId());
        OrgProductDO entity = BeanUtils.toBean(reqVO, OrgProductDO.class);
        entity.setOrgId(null);
        entity.setProductId(null);
        entity.setProductCode(null);
        orgProductMapper.updateById(entity);
    }

    @Override
    public void deleteOrgProduct(Long id) {
        validateOrgProductExists(id);
        orgProductMapper.deleteById(id);
    }

    @Override
    public OrgProductDO getOrgProduct(Long id) {
        return orgProductMapper.selectById(id);
    }

    @Override
    public OrgProductDO getByOrgAndProductCode(Long orgId, String productCode) {
        return orgProductMapper.selectByOrgAndProductCode(orgId, productCode);
    }

    @Override
    public PageResult<OrgProductDO> getOrgProductPage(OrgProductPageReqVO reqVO) {
        return orgProductMapper.selectPage(reqVO);
    }

    @Override
    public List<OrgProductDO> getListByOrgId(Long orgId) {
        return orgProductMapper.selectListByOrgId(orgId);
    }

    private void validateOrgProductExists(Long id) {
        if (orgProductMapper.selectById(id) == null) {
            throw exception(ORG_PRODUCT_NOT_EXISTS);
        }
    }
}
