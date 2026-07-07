package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_PARAM_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(DsInterfaceParamServiceImpl.class)
class DsInterfaceParamServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DsInterfaceParamServiceImpl service;
    @Resource
    private DsInterfaceMapper dsInterfaceMapper;

    private int ifSeq = 0;

    private Long insertInterface() {
        DsInterfaceDO dif = new DsInterfaceDO();
        dif.setIfCode(String.format("IF%06d", ++ifSeq));
        dif.setName("接口");
        dif.setDataSourceId(1L);
        dif.setMethod("POST");
        dif.setMsgFormat(1);
        dif.setStatus(0);
        dif.setVersion("v1");
        dsInterfaceMapper.insert(dif);
        return dif.getId();
    }

    private DsInterfaceParamSaveReqVO newReq(Long ifId, int direction, String platformField) {
        DsInterfaceParamSaveReqVO vo = new DsInterfaceParamSaveReqVO();
        vo.setDsInterfaceId(ifId);
        vo.setParamDirection(direction);
        vo.setPlatformField(platformField);
        vo.setDataType(1);
        vo.setRequired(true);
        return vo;
    }

    @Test
    void create_inParam_persists() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO vo = newReq(ifId, 1, "idNo");
        vo.setProviderField("cert_no");
        vo.setTransformFn("MD5");
        Long id = service.createDsInterfaceParam(vo);
        DsInterfaceParamDO db = service.getDsInterfaceParam(id);
        assertThat(db.getParamDirection()).isEqualTo(1);
        assertThat(db.getProviderField()).isEqualTo("cert_no");
        assertThat(db.getTransformFn()).isEqualTo("MD5");
    }

    @Test
    void create_outParam_withJsonPath() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO vo = newReq(ifId, 2, "companyName");
        vo.setJsonPath("$.data.entName");
        Long id = service.createDsInterfaceParam(vo);
        assertThat(service.getDsInterfaceParam(id).getJsonPath()).isEqualTo("$.data.entName");
    }

    @Test
    void update_paramNotExists_throws() {
        Long ifId = insertInterface();
        DsInterfaceParamSaveReqVO upd = newReq(ifId, 1, "x");
        upd.setId(66666L);
        assertServiceException(() -> service.updateDsInterfaceParam(upd), DS_INTERFACE_PARAM_NOT_EXISTS);
    }

    @Test
    void create_interfaceNotExists_throws() {
        assertServiceException(() -> service.createDsInterfaceParam(newReq(999999L, 1, "orphan")),
                cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS);
    }

    @Test
    void listByInterface_filtersByDirection() {
        Long ifId = insertInterface();
        service.createDsInterfaceParam(newReq(ifId, 1, "in1"));
        service.createDsInterfaceParam(newReq(ifId, 1, "in2"));
        service.createDsInterfaceParam(newReq(ifId, 2, "out1"));
        List<DsInterfaceParamDO> inParams = service.getListByInterface(ifId, 1);
        assertThat(inParams).hasSize(2);
        List<DsInterfaceParamDO> outParams = service.getListByInterface(ifId, 2);
        assertThat(outParams).hasSize(1);
    }

    @Test
    void page_filtersByInterfaceAndDirection() {
        Long ifId = insertInterface();
        service.createDsInterfaceParam(newReq(ifId, 1, "field1"));
        DsInterfaceParamPageReqVO q = new DsInterfaceParamPageReqVO();
        q.setDsInterfaceId(ifId);
        q.setParamDirection(1);
        PageResult<DsInterfaceParamDO> page = service.getDsInterfaceParamPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
