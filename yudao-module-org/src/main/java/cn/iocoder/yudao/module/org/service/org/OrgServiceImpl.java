package cn.iocoder.yudao.module.org.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.dal.mysql.org.OrgMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.org.enums.ErrorCodeConstants.ORG_NOT_EXISTS;

@Service
@Validated
public class OrgServiceImpl implements OrgService {

    @Resource
    private OrgMapper orgMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(OrgSaveReqVO reqVO) {
        OrgDO org = BeanUtils.toBean(reqVO, OrgDO.class);
        org.setId(null);
        // org_code 列 NOT NULL + 唯一，先写入临时唯一占位（32 位 UUID，正好落在 varchar(32) 内），
        // 拿到自增 id 后再据 id 回填最终编码；整个过程在同一事务内，外部只可见最终编码
        org.setOrgCode(UUID.randomUUID().toString().replace("-", ""));
        orgMapper.insert(org);              // id 由 DB 自增分配
        org.setOrgCode(String.format("ORG%06d", org.getId()));
        orgMapper.updateById(org);          // 回填编码
        return org.getId();
    }

    @Override
    public void updateOrg(OrgSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        OrgDO org = BeanUtils.toBean(reqVO, OrgDO.class);
        org.setOrgCode(null); // 编码不可改
        orgMapper.updateById(org);
    }

    @Override
    public void deleteOrg(Long id) {
        validateExists(id);
        orgMapper.deleteById(id);
    }

    @Override
    public OrgDO getOrg(Long id) {
        return orgMapper.selectById(id);
    }

    @Override
    public OrgDO getOrgByCode(String orgCode) {
        return orgMapper.selectByOrgCode(orgCode);
    }

    @Override
    public PageResult<OrgDO> getOrgPage(OrgPageReqVO reqVO) {
        return orgMapper.selectPage(reqVO);
    }

    @Override
    public List<OrgDO> getSimpleList() {
        return orgMapper.selectList(new LambdaQueryWrapperX<OrgDO>()
                .orderByDesc(OrgDO::getId));
    }

    private OrgDO validateExists(Long id) {
        OrgDO org = orgMapper.selectById(id);
        if (org == null) {
            throw exception(ORG_NOT_EXISTS);
        }
        return org;
    }
}
