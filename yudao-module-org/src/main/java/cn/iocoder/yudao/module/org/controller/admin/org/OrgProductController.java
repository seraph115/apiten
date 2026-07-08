package cn.iocoder.yudao.module.org.controller.admin.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductRespVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgProductSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgProductDO;
import cn.iocoder.yudao.module.org.service.org.OrgProductService;
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

@Tag(name = "管理后台 - 机构产品开通")
@RestController
@RequestMapping("/org/product")
@Validated
public class OrgProductController {

    @Resource
    private OrgProductService orgProductService;

    @PostMapping("/create")
    @Operation(summary = "创建机构产品开通")
    @PreAuthorize("@ss.hasPermission('org:product:create')")
    public CommonResult<Long> create(@Valid @RequestBody OrgProductSaveReqVO reqVO) {
        return success(orgProductService.createOrgProduct(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新机构产品开通")
    @PreAuthorize("@ss.hasPermission('org:product:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody OrgProductSaveReqVO reqVO) {
        orgProductService.updateOrgProduct(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除机构产品开通")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:product:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        orgProductService.deleteOrgProduct(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得机构产品开通")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:product:query')")
    public CommonResult<OrgProductRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(orgProductService.getOrgProduct(id), OrgProductRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得机构产品开通分页")
    @PreAuthorize("@ss.hasPermission('org:product:query')")
    public CommonResult<PageResult<OrgProductRespVO>> page(@Valid OrgProductPageReqVO reqVO) {
        PageResult<OrgProductDO> page = orgProductService.getOrgProductPage(reqVO);
        return success(BeanUtils.toBean(page, OrgProductRespVO.class));
    }

    @GetMapping("/list-by-org")
    @Operation(summary = "获得机构下的产品开通列表")
    @Parameter(name = "orgId", description = "所属机构ID", required = true)
    @PreAuthorize("@ss.hasPermission('org:product:query')")
    public CommonResult<List<OrgProductRespVO>> listByOrg(@RequestParam("orgId") Long orgId) {
        return success(BeanUtils.toBean(orgProductService.getListByOrgId(orgId), OrgProductRespVO.class));
    }
}
