package cn.iocoder.yudao.module.product.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionRespVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.service.product.ProductFunctionService;
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

@Tag(name = "管理后台 - 产品功能")
@RestController
@RequestMapping("/product/function")
@Validated
public class ProductFunctionController {

    @Resource
    private ProductFunctionService productFunctionService;

    @PostMapping("/create")
    @Operation(summary = "创建产品功能")
    @PreAuthorize("@ss.hasPermission('product:function:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductFunctionSaveReqVO reqVO) {
        return success(productFunctionService.createProductFunction(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新产品功能")
    @PreAuthorize("@ss.hasPermission('product:function:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductFunctionSaveReqVO reqVO) {
        productFunctionService.updateProductFunction(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品功能")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:function:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        productFunctionService.deleteProductFunction(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得产品功能")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:function:query')")
    public CommonResult<ProductFunctionRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(productFunctionService.getProductFunction(id), ProductFunctionRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得产品功能分页")
    @PreAuthorize("@ss.hasPermission('product:function:query')")
    public CommonResult<PageResult<ProductFunctionRespVO>> page(@Valid ProductFunctionPageReqVO reqVO) {
        PageResult<ProductFunctionDO> page = productFunctionService.getProductFunctionPage(reqVO);
        return success(BeanUtils.toBean(page, ProductFunctionRespVO.class));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品功能列表（按产品）")
    @Parameter(name = "productId", description = "产品编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:function:query')")
    public CommonResult<List<ProductFunctionRespVO>> listByProduct(@RequestParam("productId") Long productId) {
        return success(BeanUtils.toBean(productFunctionService.getListByProductId(productId), ProductFunctionRespVO.class));
    }
}
