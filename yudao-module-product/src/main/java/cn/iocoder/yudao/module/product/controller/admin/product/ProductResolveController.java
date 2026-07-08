package cn.iocoder.yudao.module.product.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver;
import cn.iocoder.yudao.module.product.service.resolve.ResolvedInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产品→数据源接口解析")
@RestController
@RequestMapping("/product/resolve")
@Validated
public class ProductResolveController {

    @Resource
    private ProductInterfaceResolver productInterfaceResolver;

    @GetMapping("/interfaces")
    @Operation(summary = "按产品编码解析启用的绑定接口列表（按优先级升序）")
    @Parameter(name = "productCode", description = "产品编码", required = true, example = "P000001")
    @PreAuthorize("@ss.hasPermission('product:info:query')")
    public CommonResult<List<ResolvedInterface>> interfaces(@RequestParam("productCode") String productCode) {
        return success(productInterfaceResolver.resolve(productCode));
    }

    @GetMapping("/default")
    @Operation(summary = "按产品编码解析默认数据源接口（默认标记优先，其次优先级最小）")
    @Parameter(name = "productCode", description = "产品编码", required = true, example = "P000001")
    @PreAuthorize("@ss.hasPermission('product:info:query')")
    public CommonResult<ResolvedInterface> defaultInterface(@RequestParam("productCode") String productCode) {
        return success(productInterfaceResolver.resolveDefault(productCode));
    }

}
