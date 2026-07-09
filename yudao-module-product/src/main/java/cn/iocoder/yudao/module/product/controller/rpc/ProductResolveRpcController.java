package cn.iocoder.yudao.module.product.controller.rpc;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver;
import cn.iocoder.yudao.module.product.service.resolve.ResolvedInterface;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;

/**
 * 内部 RPC 端点：暴露产品 → 数据源接口 默认绑定解析，供路由里程碑消费。
 * 免登录（@PermitAll），忽略租户过滤（@TenantIgnore）。产品不存在或无默认绑定
 * 均返回空 DTO（dsInterfaceId=null），不向调用方抛异常——路由层据 null 走 no-target。
 */
@RestController
@RequestMapping("/rpc-api/product")
public class ProductResolveRpcController {

    @Resource
    private ProductInterfaceResolver resolver;

    @GetMapping("/resolve-default")
    @PermitAll
    @TenantIgnore
    public ProductDefaultRespDTO resolveDefault(@RequestParam("productCode") String productCode) {
        ProductDefaultRespDTO dto = new ProductDefaultRespDTO();
        try {
            ResolvedInterface ri = resolver.resolveDefault(productCode);
            if (ri != null) {
                dto.setDsInterfaceId(ri.getDsInterfaceId());
                dto.setDsInterfaceCode(ri.getDsInterfaceCode());
            }
        } catch (ServiceException ignore) {
            // 产品不存在 → 返回空 DTO，交路由层据 null 处理，不抛给调用方
        }
        return dto;
    }
}
