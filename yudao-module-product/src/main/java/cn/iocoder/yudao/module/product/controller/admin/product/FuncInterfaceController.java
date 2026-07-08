package cn.iocoder.yudao.module.product.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfacePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfaceRespVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfaceSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.service.product.FuncInterfaceService;
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

@Tag(name = "管理后台 - 功能-数据源接口绑定")
@RestController
@RequestMapping("/product/func-interface")
@Validated
public class FuncInterfaceController {

    @Resource
    private FuncInterfaceService funcInterfaceService;

    @PostMapping("/create")
    @Operation(summary = "创建功能接口绑定")
    @PreAuthorize("@ss.hasPermission('product:func-interface:create')")
    public CommonResult<Long> create(@Valid @RequestBody FuncInterfaceSaveReqVO reqVO) {
        return success(funcInterfaceService.createFuncInterface(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新功能接口绑定")
    @PreAuthorize("@ss.hasPermission('product:func-interface:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody FuncInterfaceSaveReqVO reqVO) {
        funcInterfaceService.updateFuncInterface(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除功能接口绑定")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:func-interface:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        funcInterfaceService.deleteFuncInterface(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得功能接口绑定")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:func-interface:query')")
    public CommonResult<FuncInterfaceRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(funcInterfaceService.getFuncInterface(id), FuncInterfaceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得功能接口绑定分页")
    @PreAuthorize("@ss.hasPermission('product:func-interface:query')")
    public CommonResult<PageResult<FuncInterfaceRespVO>> page(@Valid FuncInterfacePageReqVO reqVO) {
        PageResult<FuncInterfaceDO> page = funcInterfaceService.getFuncInterfacePage(reqVO);
        return success(BeanUtils.toBean(page, FuncInterfaceRespVO.class));
    }

    @GetMapping("/list-by-function")
    @Operation(summary = "获得功能接口绑定列表（按功能）")
    @Parameter(name = "productFunctionId", description = "产品功能编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:func-interface:query')")
    public CommonResult<List<FuncInterfaceRespVO>> listByFunction(@RequestParam("productFunctionId") Long productFunctionId) {
        return success(BeanUtils.toBean(funcInterfaceService.getListByFunction(productFunctionId), FuncInterfaceRespVO.class));
    }
}
