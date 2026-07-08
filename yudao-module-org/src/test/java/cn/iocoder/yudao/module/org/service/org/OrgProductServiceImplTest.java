package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgProductServiceImpl.class)
class OrgProductServiceImplTest extends BaseDbUnitTest {

    @Resource private OrgProductServiceImpl service;
    @Resource private OrgMapper orgMapper;

    private Long newOrg() {
        OrgDO o = new OrgDO();
        o.setOrgCode("ORG000001"); o.setName("机构"); o.setStatus(0);
        orgMapper.insert(o);
        return o.getId();
    }
    private OrgProductSaveReqVO req(Long orgId, String productCode) {
        OrgProductSaveReqVO vo = new OrgProductSaveReqVO();
        vo.setOrgId(orgId); vo.setProductId(100L); vo.setProductCode(productCode);
        vo.setStatus(0); vo.setUnitPrice(new BigDecimal("1.5000"));
        return vo;
    }

    @Test
    void create_persistsAndReadBack() {
        Long orgId = newOrg();
        Long id = service.createOrgProduct(req(orgId, "P000001"));
        OrgProductDO db = service.getOrgProduct(id);
        assertThat(db.getProductCode()).isEqualTo("P000001");
        assertThat(db.getUnitPrice()).isEqualByComparingTo("1.5000");
    }

    @Test
    void create_parentOrgNotExists_throws() {
        assertServiceException(() -> service.createOrgProduct(req(99999L, "P000001")), ORG_NOT_EXISTS);
    }

    @Test
    void create_duplicate_throws() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000001"));
        assertServiceException(() -> service.createOrgProduct(req(orgId, "P000001")), ORG_PRODUCT_DUPLICATE);
    }

    @Test
    void getByOrgAndProductCode_returns() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000002"));
        assertThat(service.getByOrgAndProductCode(orgId, "P000002")).isNotNull();
        assertThat(service.getByOrgAndProductCode(orgId, "NOPE")).isNull();
    }

    @Test
    void update_notExists_throws() {
        OrgProductSaveReqVO upd = req(1L, "P000001");
        upd.setId(99999L);
        assertServiceException(() -> service.updateOrgProduct(upd), ORG_PRODUCT_NOT_EXISTS);
    }

    @Test
    void page_filtersByOrgId() {
        Long orgId = newOrg();
        service.createOrgProduct(req(orgId, "P000001"));
        service.createOrgProduct(req(orgId, "P000002"));
        OrgProductPageReqVO q = new OrgProductPageReqVO();
        q.setOrgId(orgId);
        PageResult<OrgProductDO> page = service.getOrgProductPage(q);
        assertThat(page.getTotal()).isEqualTo(2);
    }
}
