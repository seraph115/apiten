package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsResponseCodeServiceImpl service;
    @Resource
    private DataSourceMapper dataSourceMapper;

    private int dsSeq = 0;

    private Long insertDataSource() {
        DataSourceDO ds = new DataSourceDO();
        ds.setDsCode(String.format("DS%06d", ++dsSeq));
        ds.setName("测试源");
        ds.setSourceType(1);
        ds.setStatus(0);
        ds.setEnvType(1);
        ds.setProtocolType(1);
        dataSourceMapper.insert(ds);
        return ds.getId();
    }

    private DsResponseCodeSaveReqVO newReq(Long dsId, String rawCode, String platformCode) {
        DsResponseCodeSaveReqVO vo = new DsResponseCodeSaveReqVO();
        vo.setDataSourceId(dsId);
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
        Long dsId = insertDataSource();
        DsResponseCodeSaveReqVO vo = newReq(dsId, "A00", "0000");
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
        Long dsId = insertDataSource();
        service.createDsResponseCode(newReq(dsId, "DUP", "0000"));
        assertServiceException(() -> service.createDsResponseCode(newReq(dsId, "DUP", "0001")),
                DS_RESPONSE_CODE_DUPLICATE);
    }

    @Test
    void create_dataSourceNotExists_throws() {
        assertServiceException(() -> service.createDsResponseCode(newReq(999999L, "A00", "0000")),
                DATA_SOURCE_NOT_EXISTS);
    }

    @Test
    void update_notExists_throws() {
        Long dsId = insertDataSource();
        DsResponseCodeSaveReqVO upd = newReq(dsId, "X", "0000");
        upd.setId(77777L);
        assertServiceException(() -> service.updateDsResponseCode(upd), DS_RESPONSE_CODE_NOT_EXISTS);
    }

    @Test
    void unmappedList_returnsOnlyBlankPlatformCode() {
        Long dsId = insertDataSource();
        service.createDsResponseCode(newReq(dsId, "MAPPED", "3001"));
        service.createDsResponseCode(newReq(dsId, "UNMAPPED1", ""));
        service.createDsResponseCode(newReq(dsId, "UNMAPPED2", null));
        List<DsResponseCodeDO> unmapped = service.getUnmappedList(dsId);
        assertThat(unmapped).extracting(DsResponseCodeDO::getRawCode)
                .containsExactlyInAnyOrder("UNMAPPED1", "UNMAPPED2");
    }

    @Test
    void page_filtersByRawCode() {
        Long dsId = insertDataSource();
        service.createDsResponseCode(newReq(dsId, "ERR_TIMEOUT", "3001"));
        service.createDsResponseCode(newReq(dsId, "OK", "0000"));
        DsResponseCodePageReqVO q = new DsResponseCodePageReqVO();
        q.setRawCode("ERR");
        PageResult<DsResponseCodeDO> page = service.getDsResponseCodePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
