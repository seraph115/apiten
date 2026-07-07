package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportExcelVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeImportTest extends BaseDbUnitTest {

    @Resource
    private DsResponseCodeServiceImpl service;

    private DsResponseCodeImportExcelVO row(String rawCode, String platformCode) {
        DsResponseCodeImportExcelVO vo = new DsResponseCodeImportExcelVO();
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
    void import_createsNewRows() {
        DsResponseCodeImportRespVO resp = service.importResponseCodes(
                List.of(row("R01", "0000"), row("R02", "3001")), false);
        assertThat(resp.getCreateRawCodes()).containsExactlyInAnyOrder("R01", "R02");
        assertThat(resp.getFailureRawCodes()).isEmpty();
    }

    @Test
    void import_duplicateWithoutUpdate_reportsFailure() {
        service.importResponseCodes(List.of(row("DUP", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row("DUP", "3001")), false);
        assertThat(resp.getCreateRawCodes()).isEmpty();
        assertThat(resp.getFailureRawCodes()).containsKey("DUP");
    }

    @Test
    void import_duplicateWithUpdate_updatesRow() {
        service.importResponseCodes(List.of(row("UPD", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row("UPD", "3001")), true);
        assertThat(resp.getUpdateRawCodes()).contains("UPD");
        List<DsResponseCodeDO> all = service.getExportList(new DsResponseCodePageReqVO());
        assertThat(all).anySatisfy(d -> {
            if ("UPD".equals(d.getRawCode())) {
                assertThat(d.getPlatformCode()).isEqualTo("3001");
            }
        });
    }
}
