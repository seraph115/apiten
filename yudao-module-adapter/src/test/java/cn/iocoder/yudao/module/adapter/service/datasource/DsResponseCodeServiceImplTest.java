package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsResponseCodeServiceImpl service;

    private DsResponseCodeSaveReqVO newReq(String rawCode, String platformCode) {
        DsResponseCodeSaveReqVO vo = new DsResponseCodeSaveReqVO();
        vo.setDataSourceId(1L);
        vo.setDsInterfaceId(0L);
        vo.setRawCode(rawCode);
        vo.setSuccess("0000".equals(platformCode));
        vo.setCharge(false);
        vo.setRetryable(false);
        vo.setTriggerSwitch(false);
        vo.setPlatformCode(platformCode);
        return vo;
    }

    @Test
    void create_persistsFourFlags() {
        DsResponseCodeSaveReqVO vo = newReq("A00", "0000");
        vo.setCharge(true);
        vo.setTriggerSwitch(true);
        Long id = service.createDsResponseCode(vo);
        DsResponseCodeDO db = service.getDsResponseCode(id);
        assertThat(db.getSuccess()).isTrue();
        assertThat(db.getCharge()).isTrue();
        assertThat(db.getRetryable()).isFalse();
        assertThat(db.getTriggerSwitch()).isTrue();
        assertThat(db.getPlatformCode()).isEqualTo("0000");
    }

    @Test
    void create_duplicateRawCodeSameScope_throws() {
        service.createDsResponseCode(newReq("DUP", "0000"));
        assertServiceException(() -> service.createDsResponseCode(newReq("DUP", "0001")),
                DS_RESPONSE_CODE_DUPLICATE);
    }

    @Test
    void update_notExists_throws() {
        DsResponseCodeSaveReqVO upd = newReq("X", "0000");
        upd.setId(77777L);
        assertServiceException(() -> service.updateDsResponseCode(upd), DS_RESPONSE_CODE_NOT_EXISTS);
    }

    @Test
    void unmappedList_returnsOnlyBlankPlatformCode() {
        service.createDsResponseCode(newReq("MAPPED", "3001"));
        service.createDsResponseCode(newReq("UNMAPPED1", ""));
        service.createDsResponseCode(newReq("UNMAPPED2", null));
        List<DsResponseCodeDO> unmapped = service.getUnmappedList(1L);
        assertThat(unmapped).extracting(DsResponseCodeDO::getRawCode)
                .containsExactlyInAnyOrder("UNMAPPED1", "UNMAPPED2");
    }

    @Test
    void page_filtersByRawCode() {
        service.createDsResponseCode(newReq("ERR_TIMEOUT", "3001"));
        service.createDsResponseCode(newReq("OK", "0000"));
        DsResponseCodePageReqVO q = new DsResponseCodePageReqVO();
        q.setRawCode("ERR");
        PageResult<DsResponseCodeDO> page = service.getDsResponseCodePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
