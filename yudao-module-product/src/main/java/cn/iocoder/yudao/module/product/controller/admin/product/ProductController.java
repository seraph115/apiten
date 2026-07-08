package cn.iocoder.yudao.module.product.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductRespVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.service.product.ProductService;
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

@Tag(name = "管理后台 - 产品信息")
@RestController
@RequestMapping("/product/info")
@Validated
public class ProductController {

    @Resource
    private ProductService productService;

    @PostMapping("/create")
    @Operation(summary = "创建产品")
    @PreAuthorize("@ss.hasPermission('product:info:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductSaveReqVO reqVO) {
        return success(productService.createProduct(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新产品")
    @PreAuthorize("@ss.hasPermission('product:info:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductSaveReqVO reqVO) {
        productService.updateProduct(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:info:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        productService.deleteProduct(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得产品")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:info:query')")
    public CommonResult<ProductRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(productService.getProduct(id), ProductRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得产品分页")
    @PreAuthorize("@ss.hasPermission('product:info:query')")
    public CommonResult<PageResult<ProductRespVO>> page(@Valid ProductPageReqVO reqVO) {
        PageResult<ProductDO> page = productService.getProductPage(reqVO);
        return success(BeanUtils.toBean(page, ProductRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得产品精简列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('product:info:query')")
    public CommonResult<List<ProductRespVO>> simpleList() {
        return success(BeanUtils.toBean(productService.getSimpleList(), ProductRespVO.class));
    }
}
