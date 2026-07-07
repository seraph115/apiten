package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsInterfaceServiceImpl.class)
class DsInterfaceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsInterfaceServiceImpl service;
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

    private DsInterfaceSaveReqVO newReq(String name, Long dsId) {
        DsInterfaceSaveReqVO vo = new DsInterfaceSaveReqVO();
        vo.setName(name);
        vo.setDataSourceId(dsId);
        vo.setMethod("POST");
        vo.setMsgFormat(1);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesIfCode() {
        Long dsId = insertDataSource();
        Long id = service.createDsInterface(newReq("企业工商查询", dsId));
        DsInterfaceDO db = service.getDsInterface(id);
        assertThat(db.getIfCode()).matches("IF\\d{6}");
        assertThat(db.getDataSourceId()).isEqualTo(dsId);
    }

    @Test
    void update_interfaceNotExists_throws() {
        Long dsId = insertDataSource();
        DsInterfaceSaveReqVO upd = newReq("x", dsId);
        upd.setId(88888L);
        assertServiceException(() -> service.updateDsInterface(upd), DS_INTERFACE_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long dsId = insertDataSource();
        Long id = service.createDsInterface(newReq("待删", dsId));
        service.deleteDsInterface(id);
        assertThat(service.getDsInterface(id)).isNull();
    }

    @Test
    void listByDataSourceId_filters() {
        Long ds1 = insertDataSource();
        Long ds2 = insertDataSource();
        service.createDsInterface(newReq("A", ds1));
        service.createDsInterface(newReq("B", ds1));
        service.createDsInterface(newReq("C", ds2));
        List<DsInterfaceDO> list = service.getListByDataSourceId(ds1);
        assertThat(list).hasSize(2);
    }

    @Test
    void page_filtersByName() {
        Long dsId = insertDataSource();
        service.createDsInterface(newReq("工商查询", dsId));
        service.createDsInterface(newReq("司法查询", dsId));
        DsInterfacePageReqVO q = new DsInterfacePageReqVO();
        q.setName("工商");
        PageResult<DsInterfaceDO> page = service.getDsInterfacePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void create_dataSourceNotExists_throws() {
        // 不先造数据源，直接用一个不存在的 dataSourceId
        assertServiceException(() -> service.createDsInterface(newReq("孤儿接口", 999999L)),
                DATA_SOURCE_NOT_EXISTS);
    }

    @Test
    void update_dataSourceNotExists_throws() {
        Long dsId = insertDataSource();
        Long id = service.createDsInterface(newReq("接口", dsId));
        DsInterfaceSaveReqVO upd = newReq("改", 999999L); // 存在的接口 id + 不存在的数据源
        upd.setId(id);
        assertServiceException(() -> service.updateDsInterface(upd),
                DATA_SOURCE_NOT_EXISTS);
    }

    @Test
    void create_afterDelete_noDuplicateIfCode() {
        Long dsId = insertDataSource();
        Long id1 = service.createDsInterface(newReq("接口1", dsId));
        Long id2 = service.createDsInterface(newReq("接口2", dsId));
        String c2 = service.getDsInterface(id2).getIfCode();
        service.deleteDsInterface(id2);
        Long id3 = service.createDsInterface(newReq("接口3", dsId)); // 删除后再建
        String c3 = service.getDsInterface(id3).getIfCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("IF\\d{6}");
    }
}
