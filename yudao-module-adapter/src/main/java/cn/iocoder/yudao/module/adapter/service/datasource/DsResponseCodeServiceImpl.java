package cn.iocoder.yudao.module.adapter.service.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportExcelVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeImportRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.adapter.enums.ErrorCodeConstants.DS_RESPONSE_CODE_NOT_EXISTS;

@Service
@Validated
public class DsResponseCodeServiceImpl implements DsResponseCodeService {

    @Resource
    private DsResponseCodeMapper dsResponseCodeMapper;

    @Override
    public Long createDsResponseCode(DsResponseCodeSaveReqVO reqVO) {
        normalizeInterfaceId(reqVO);
        validateDuplicate(reqVO.getDataSourceId(), reqVO.getDsInterfaceId(), reqVO.getRawCode(), null);
        DsResponseCodeDO code = BeanUtils.toBean(reqVO, DsResponseCodeDO.class);
        code.setId(null);
        dsResponseCodeMapper.insert(code);
        return code.getId();
    }

    @Override
    public void updateDsResponseCode(DsResponseCodeSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        normalizeInterfaceId(reqVO);
        validateDuplicate(reqVO.getDataSourceId(), reqVO.getDsInterfaceId(), reqVO.getRawCode(), reqVO.getId());
        dsResponseCodeMapper.updateById(BeanUtils.toBean(reqVO, DsResponseCodeDO.class));
    }

    @Override
    public void deleteDsResponseCode(Long id) {
        validateExists(id);
        dsResponseCodeMapper.deleteById(id);
    }

    @Override
    public DsResponseCodeDO getDsResponseCode(Long id) {
        return dsResponseCodeMapper.selectById(id);
    }

    @Override
    public PageResult<DsResponseCodeDO> getDsResponseCodePage(DsResponseCodePageReqVO reqVO) {
        return dsResponseCodeMapper.selectPage(reqVO);
    }

    @Override
    public List<DsResponseCodeDO> getUnmappedList(Long dataSourceId) {
        return dsResponseCodeMapper.selectUnmapped(dataSourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DsResponseCodeImportRespVO importResponseCodes(List<DsResponseCodeImportExcelVO> list, boolean updateSupport) {
        DsResponseCodeImportRespVO resp = DsResponseCodeImportRespVO.builder()
                .createRawCodes(new ArrayList<>())
                .updateRawCodes(new ArrayList<>())
                .failureRawCodes(new LinkedHashMap<>())
                .build();
        for (DsResponseCodeImportExcelVO row : list) {
            Long ifId = row.getDsInterfaceId() == null ? 0L : row.getDsInterfaceId();
            DsResponseCodeDO exist = dsResponseCodeMapper.selectByScopeAndRawCode(
                    row.getDataSourceId(), ifId, row.getRawCode());
            if (exist == null) {
                DsResponseCodeDO code = BeanUtils.toBean(row, DsResponseCodeDO.class);
                code.setId(null);
                code.setDsInterfaceId(ifId);
                dsResponseCodeMapper.insert(code);
                resp.getCreateRawCodes().add(row.getRawCode());
                continue;
            }
            if (!updateSupport) {
                resp.getFailureRawCodes().put(row.getRawCode(), DS_RESPONSE_CODE_DUPLICATE.getMsg());
                continue;
            }
            DsResponseCodeDO update = BeanUtils.toBean(row, DsResponseCodeDO.class);
            update.setId(exist.getId());
            update.setDsInterfaceId(ifId);
            dsResponseCodeMapper.updateById(update);
            resp.getUpdateRawCodes().add(row.getRawCode());
        }
        return resp;
    }

    @Override
    public List<DsResponseCodeDO> getExportList(DsResponseCodePageReqVO reqVO) {
        return dsResponseCodeMapper.selectList(new LambdaQueryWrapperX<DsResponseCodeDO>()
                .eqIfPresent(DsResponseCodeDO::getDataSourceId, reqVO.getDataSourceId())
                .eqIfPresent(DsResponseCodeDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .likeIfPresent(DsResponseCodeDO::getRawCode, reqVO.getRawCode())
                .orderByDesc(DsResponseCodeDO::getId));
    }

    private DsResponseCodeDO validateExists(Long id) {
        DsResponseCodeDO code = dsResponseCodeMapper.selectById(id);
        if (code == null) {
            throw exception(DS_RESPONSE_CODE_NOT_EXISTS);
        }
        return code;
    }

    private void normalizeInterfaceId(DsResponseCodeSaveReqVO reqVO) {
        if (reqVO.getDsInterfaceId() == null) {
            reqVO.setDsInterfaceId(0L);
        }
    }

    private void validateDuplicate(Long dsId, Long ifId, String rawCode, Long selfId) {
        DsResponseCodeDO exist = dsResponseCodeMapper.selectByScopeAndRawCode(dsId, ifId, rawCode);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw exception(DS_RESPONSE_CODE_DUPLICATE);
        }
    }
}
