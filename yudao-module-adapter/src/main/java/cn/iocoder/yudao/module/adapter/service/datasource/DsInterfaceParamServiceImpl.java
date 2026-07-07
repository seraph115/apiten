package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_PARAM_NOT_EXISTS;

@Service
@Validated
public class DsInterfaceParamServiceImpl implements DsInterfaceParamService {

    @Resource
    private DsInterfaceParamMapper dsInterfaceParamMapper;
    @Resource
    private DsInterfaceMapper dsInterfaceMapper;

    @Override
    public Long createDsInterfaceParam(DsInterfaceParamSaveReqVO reqVO) {
        validateInterfaceExists(reqVO.getDsInterfaceId());
        DsInterfaceParamDO param = BeanUtils.toBean(reqVO, DsInterfaceParamDO.class);
        param.setId(null);
        dsInterfaceParamMapper.insert(param);
        return param.getId();
    }

    @Override
    public void updateDsInterfaceParam(DsInterfaceParamSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateInterfaceExists(reqVO.getDsInterfaceId());
        dsInterfaceParamMapper.updateById(BeanUtils.toBean(reqVO, DsInterfaceParamDO.class));
    }

    @Override
    public void deleteDsInterfaceParam(Long id) {
        validateExists(id);
        dsInterfaceParamMapper.deleteById(id);
    }

    @Override
    public DsInterfaceParamDO getDsInterfaceParam(Long id) {
        return dsInterfaceParamMapper.selectById(id);
    }

    @Override
    public PageResult<DsInterfaceParamDO> getDsInterfaceParamPage(DsInterfaceParamPageReqVO reqVO) {
        return dsInterfaceParamMapper.selectPage(reqVO);
    }

    @Override
    public List<DsInterfaceParamDO> getListByInterface(Long dsInterfaceId, Integer paramDirection) {
        return dsInterfaceParamMapper.selectListByInterface(dsInterfaceId, paramDirection);
    }

    private DsInterfaceParamDO validateExists(Long id) {
        DsInterfaceParamDO param = dsInterfaceParamMapper.selectById(id);
        if (param == null) {
            throw exception(DS_INTERFACE_PARAM_NOT_EXISTS);
        }
        return param;
    }

    private void validateInterfaceExists(Long dsInterfaceId) {
        if (dsInterfaceMapper.selectById(dsInterfaceId) == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
    }
}
