package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(OrgServiceImpl.class)
class OrgServiceImplTest extends BaseDbUnitTest {

    @Resource
    private OrgServiceImpl service;

    private OrgSaveReqVO newReq(String name) {
        OrgSaveReqVO vo = new OrgSaveReqVO();
        vo.setName(name);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesOrgCode() {
        Long id = service.createOrg(newReq("某某银行"));
        OrgDO db = service.getOrg(id);
        assertThat(db.getOrgCode()).matches("ORG\\d{6}");
        assertThat(db.getName()).isEqualTo("某某银行");
    }

    @Test
    void getByCode_returnsOrg() {
        Long id = service.createOrg(newReq("某某小贷"));
        String code = service.getOrg(id).getOrgCode();
        assertThat(service.getOrgByCode(code).getId()).isEqualTo(id);
    }

    @Test
    void update_notExists_throws() {
        OrgSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateOrg(upd), ORG_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createOrg(newReq("待删"));
        service.deleteOrg(id);
        assertThat(service.getOrg(id)).isNull();
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        service.createOrg(newReq("A"));
        Long id2 = service.createOrg(newReq("B"));
        String c2 = service.getOrg(id2).getOrgCode();
        service.deleteOrg(id2);
        Long id3 = service.createOrg(newReq("C"));
        String c3 = service.getOrg(id3).getOrgCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("ORG\\d{6}");
    }

    @Test
    void page_filtersByName() {
        service.createOrg(newReq("工商银行"));
        service.createOrg(newReq("建设银行"));
        OrgPageReqVO q = new OrgPageReqVO();
        q.setName("工商");
        PageResult<OrgDO> page = service.getOrgPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
