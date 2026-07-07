package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DataSourceMapper;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsInterfaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DATA_SOURCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_INTERFACE_NOT_EXISTS;

@Service
@Validated
public class DsInterfaceServiceImpl implements DsInterfaceService {

    @Resource
    private DsInterfaceMapper dsInterfaceMapper;
    @Resource
    private DataSourceMapper dataSourceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDsInterface(DsInterfaceSaveReqVO reqVO) {
        validateDataSourceExists(reqVO.getDataSourceId());
        DsInterfaceDO dif = BeanUtils.toBean(reqVO, DsInterfaceDO.class);
        dif.setId(null);
        dif.setIfCode(java.util.UUID.randomUUID().toString().replace("-", "")); // 临时唯一占位(32位,落在 varchar(32))
        dsInterfaceMapper.insert(dif);                          // id 由 DB 自增分配
        dif.setIfCode(String.format("IF%06d", dif.getId()));   // 据 id 回填最终编码
        dsInterfaceMapper.updateById(dif);
        return dif.getId();
    }

    @Override
    public void updateDsInterface(DsInterfaceSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateDataSourceExists(reqVO.getDataSourceId());
        DsInterfaceDO dif = BeanUtils.toBean(reqVO, DsInterfaceDO.class);
        dif.setIfCode(null);
        dsInterfaceMapper.updateById(dif);
    }

    @Override
    public void deleteDsInterface(Long id) {
        validateExists(id);
        dsInterfaceMapper.deleteById(id);
    }

    @Override
    public DsInterfaceDO getDsInterface(Long id) {
        return dsInterfaceMapper.selectById(id);
    }

    @Override
    public PageResult<DsInterfaceDO> getDsInterfacePage(DsInterfacePageReqVO reqVO) {
        return dsInterfaceMapper.selectPage(reqVO);
    }

    @Override
    public List<DsInterfaceDO> getListByDataSourceId(Long dataSourceId) {
        return dsInterfaceMapper.selectListByDataSourceId(dataSourceId);
    }

    private DsInterfaceDO validateExists(Long id) {
        DsInterfaceDO dif = dsInterfaceMapper.selectById(id);
        if (dif == null) {
            throw exception(DS_INTERFACE_NOT_EXISTS);
        }
        return dif;
    }

    private void validateDataSourceExists(Long dataSourceId) {
        if (dataSourceMapper.selectById(dataSourceId) == null) {
            throw exception(DATA_SOURCE_NOT_EXISTS);
        }
    }
}
