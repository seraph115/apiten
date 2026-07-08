package cn.iocoder.yudao.module.org.controller.admin.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountPageReqVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountRespVO;
import cn.iocoder.yudao.module.org.controller.admin.org.vo.OrgAccountSaveReqVO;
import cn.iocoder.yudao.module.org.dal.dataobject.org.OrgAccountDO;
import cn.iocoder.yudao.module.org.service.org.OrgAccountService;
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

@Tag(name = "管理后台 - 机构账号")
@RestController
@RequestMapping("/org/account")
@Validated
public class OrgAccountController {

    @Resource
    private OrgAccountService accountService;

    @PostMapping("/create")
    @Operation(summary = "创建机构账号")
    @PreAuthorize("@ss.hasPermission('org:account:create')")
    public CommonResult<OrgAccountRespVO> create(@Valid @RequestBody OrgAccountSaveReqVO reqVO) {
        Long id = accountService.createAccount(reqVO);
        String sk = accountService.resetSecret(id);
        OrgAccountDO db = accountService.getAccount(id);
        OrgAccountRespVO resp = BeanUtils.toBean(db, OrgAccountRespVO.class);
        resp.setSecretKey(sk);
        return success(resp);
    }

    @PutMapping("/update")
    @Operation(summary = "更新机构账号")
    @PreAuthorize("@ss.hasPermission('org:account:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody OrgAccountSaveReqVO reqVO) {
        accountService.updateAccount(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除机构账号")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:account:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        accountService.deleteAccount(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得机构账号")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:account:query')")
    public CommonResult<OrgAccountRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(accountService.getAccount(id), OrgAccountRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得机构账号分页")
    @PreAuthorize("@ss.hasPermission('org:account:query')")
    public CommonResult<PageResult<OrgAccountRespVO>> page(@Valid OrgAccountPageReqVO reqVO) {
        PageResult<OrgAccountDO> page = accountService.getAccountPage(reqVO);
        return success(BeanUtils.toBean(page, OrgAccountRespVO.class));
    }

    @GetMapping("/list-by-org")
    @Operation(summary = "获得机构下的账号列表")
    @Parameter(name = "orgId", description = "所属机构ID", required = true)
    @PreAuthorize("@ss.hasPermission('org:account:query')")
    public CommonResult<List<OrgAccountRespVO>> listByOrg(@RequestParam("orgId") Long orgId) {
        return success(BeanUtils.toBean(accountService.getListByOrgId(orgId), OrgAccountRespVO.class));
    }

    @PutMapping("/reset-secret")
    @Operation(summary = "重置机构账号密钥")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('org:account:update')")
    public CommonResult<OrgAccountRespVO> resetSecret(@RequestParam("id") Long id) {
        String sk = accountService.resetSecret(id);
        OrgAccountRespVO resp = new OrgAccountRespVO();
        resp.setId(id);
        resp.setSecretKey(sk);
        return success(resp);
    }
}
