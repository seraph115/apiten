package cn.iocoder.yudao.module.product.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamRespVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductParamDO;
import cn.iocoder.yudao.module.product.service.product.ProductParamService;
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

@Tag(name = "管理后台 - 产品参数")
@RestController
@RequestMapping("/product/param")
@Validated
public class ProductParamController {

    @Resource
    private ProductParamService productParamService;

    @PostMapping("/create")
    @Operation(summary = "创建产品参数")
    @PreAuthorize("@ss.hasPermission('product:param:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductParamSaveReqVO reqVO) {
        return success(productParamService.createProductParam(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新产品参数")
    @PreAuthorize("@ss.hasPermission('product:param:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductParamSaveReqVO reqVO) {
        productParamService.updateProductParam(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除产品参数")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:param:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        productParamService.deleteProductParam(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得产品参数")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:param:query')")
    public CommonResult<ProductParamRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(productParamService.getProductParam(id), ProductParamRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得产品参数分页")
    @PreAuthorize("@ss.hasPermission('product:param:query')")
    public CommonResult<PageResult<ProductParamRespVO>> page(@Valid ProductParamPageReqVO reqVO) {
        PageResult<ProductParamDO> page = productParamService.getProductParamPage(reqVO);
        return success(BeanUtils.toBean(page, ProductParamRespVO.class));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "获得产品参数列表（按产品+方向）")
    @Parameter(name = "productId", description = "所属产品编号", required = true)
    @Parameter(name = "paramDirection", description = "方向：1入参 2出参")
    @PreAuthorize("@ss.hasPermission('product:param:query')")
    public CommonResult<List<ProductParamRespVO>> listByProduct(@RequestParam("productId") Long productId,
                                                                  @RequestParam(value = "paramDirection", required = false) Integer paramDirection) {
        return success(BeanUtils.toBean(productParamService.getListByProduct(productId, paramDirection), ProductParamRespVO.class));
    }
}
