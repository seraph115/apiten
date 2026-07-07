package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportExcelVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(DsResponseCodeServiceImpl.class)
class DsResponseCodeImportTest extends BaseDbUnitTest {

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

    private DsResponseCodeImportExcelVO row(Long dsId, String rawCode, String platformCode) {
        DsResponseCodeImportExcelVO vo = new DsResponseCodeImportExcelVO();
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
    void import_createsNewRows() {
        Long dsId = insertDataSource();
        DsResponseCodeImportRespVO resp = service.importResponseCodes(
                List.of(row(dsId, "R01", "0000"), row(dsId, "R02", "3001")), false);
        assertThat(resp.getCreateRawCodes()).containsExactlyInAnyOrder("R01", "R02");
        assertThat(resp.getFailureRawCodes()).isEmpty();
    }

    @Test
    void import_duplicateWithoutUpdate_reportsFailure() {
        Long dsId = insertDataSource();
        service.importResponseCodes(List.of(row(dsId, "DUP", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row(dsId, "DUP", "3001")), false);
        assertThat(resp.getCreateRawCodes()).isEmpty();
        assertThat(resp.getFailureRawCodes()).containsKey("DUP");
    }

    @Test
    void import_duplicateWithUpdate_updatesRow() {
        Long dsId = insertDataSource();
        service.importResponseCodes(List.of(row(dsId, "UPD", "0000")), false);
        DsResponseCodeImportRespVO resp = service.importResponseCodes(List.of(row(dsId, "UPD", "3001")), true);
        assertThat(resp.getUpdateRawCodes()).contains("UPD");
        List<DsResponseCodeDO> all = service.getExportList(new DsResponseCodePageReqVO());
        assertThat(all).anySatisfy(d -> {
            if ("UPD".equals(d.getRawCode())) {
                assertThat(d.getPlatformCode()).isEqualTo("3001");
            }
        });
    }

    @Test
    void import_dataSourceNotExists_reportsFailure() {
        DsResponseCodeImportRespVO resp = service.importResponseCodes(
                List.of(row(999999L, "NODS", "0000")), false);
        assertThat(resp.getCreateRawCodes()).isEmpty();
        assertThat(resp.getFailureRawCodes()).containsKey("NODS");
        assertThat(service.getExportList(new DsResponseCodePageReqVO())).isEmpty();
    }
}
