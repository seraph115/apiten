package cn.iocoder.yudao.module.org.controller.admin.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgRespVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgDO;
import cn.iocoder.yudao.module.org.service.org.OrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 机构信息")
@RestController
@RequestMapping("/org/info")
@Validated
public class OrgController {

    @Resource
    private OrgService orgService;

    @PostMapping("/create")
    @Operation(summary = "创建机构")
    @PreAuthorize("@ss.hasPermission('org:info:create')")
    public CommonResult<Long> create(@Valid @RequestBody OrgSaveReqVO reqVO) {
        return success(orgService.createOrg(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新机构")
    @PreAuthorize("@ss.hasPermission('org:info:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody OrgSaveReqVO reqVO) {
        orgService.updateOrg(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除机构")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:info:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        orgService.deleteOrg(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得机构")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:info:query')")
    public CommonResult<OrgRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(orgService.getOrg(id), OrgRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得机构分页")
    @PreAuthorize("@ss.hasPermission('org:info:query')")
    public CommonResult<PageResult<OrgRespVO>> page(@Valid OrgPageReqVO reqVO) {
        PageResult<OrgDO> page = orgService.getOrgPage(reqVO);
        return success(BeanUtils.toBean(page, OrgRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得机构精简列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('org:info:query')")
    public CommonResult<List<OrgRespVO>> simpleList() {
        return success(BeanUtils.toBean(orgService.getSimpleList(), OrgRespVO.class));
    }
}
