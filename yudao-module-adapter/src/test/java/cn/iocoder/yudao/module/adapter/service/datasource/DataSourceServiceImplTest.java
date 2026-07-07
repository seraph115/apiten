package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DataSourceServiceImpl.class)
class DataSourceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DataSourceServiceImpl service;

    private DataSourceSaveReqVO newReq(String name) {
        DataSourceSaveReqVO vo = new DataSourceSaveReqVO();
        vo.setName(name);
        vo.setSourceType(1);
        vo.setStatus(0);
        vo.setEnvType(1);
        vo.setProtocolType(1);
        return vo;
    }

    @Test
    void create_generatesDsCode_andPersists() {
        Long id = service.createDataSource(newReq("百行征信"));
        DataSourceDO db = service.getDataSource(id);
        assertThat(db).isNotNull();
        assertThat(db.getName()).isEqualTo("百行征信");
        assertThat(db.getDsCode()).matches("DS\\d{6}");
    }

    @Test
    void update_thenGet_reflectsChange() {
        Long id = service.createDataSource(newReq("源A"));
        DataSourceSaveReqVO upd = newReq("源A改名");
        upd.setId(id);
        service.updateDataSource(upd);
        assertThat(service.getDataSource(id).getName()).isEqualTo("源A改名");
    }

    @Test
    void update_notExists_throws() {
        DataSourceSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateDataSource(upd), DATA_SOURCE_NOT_EXISTS);
    }

    @Test
    void delete_thenGet_null() {
        Long id = service.createDataSource(newReq("待删"));
        service.deleteDataSource(id);
        assertThat(service.getDataSource(id)).isNull();
    }

    @Test
    void page_filtersByName() {
        service.createDataSource(newReq("阿里数据"));
        service.createDataSource(newReq("腾讯数据"));
        DataSourcePageReqVO q = new DataSourcePageReqVO();
        q.setName("阿里");
        PageResult<DataSourceDO> page = service.getDataSourcePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).getName()).isEqualTo("阿里数据");
    }

    @Test
    void create_sameName_dsCodeStillUnique() {
        Long id1 = service.createDataSource(newReq("同名"));
        Long id2 = service.createDataSource(newReq("同名"));
        assertThat(service.getDataSource(id1).getDsCode())
                .isNotEqualTo(service.getDataSource(id2).getDsCode());
    }

    @Test
    void create_afterDelete_noDuplicateDsCode() {
        Long id1 = service.createDataSource(newReq("源1"));
        Long id2 = service.createDataSource(newReq("源2"));
        service.deleteDataSource(id2);
        Long id3 = service.createDataSource(newReq("源3")); // 删除后再建
        String c1 = service.getDataSource(id1).getDsCode();
        String c3 = service.getDataSource(id3).getDsCode();
        assertThat(c3).isNotEqualTo(c1);
        assertThat(c3).matches("DS\\d{6}");
    }
}
