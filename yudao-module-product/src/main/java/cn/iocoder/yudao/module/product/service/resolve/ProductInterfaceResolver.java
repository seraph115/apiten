package cn.iocoder.yudao.module.product.service.resolve;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;

/**
 * 产品 → 数据源接口 解析服务：按 productCode 汇总启用功能的启用绑定接口，
 * 按默认/优先级排序，供路由里程碑消费。
 */
@Component
public class ProductInterfaceResolver {

    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    public List<ResolvedInterface> resolve(String productCode) {
        ProductDO product = productMapper.selectByProductCode(productCode);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        List<ProductFunctionDO> functions = functionMapper.selectList(new LambdaQueryWrapperX<ProductFunctionDO>()
                .eq(ProductFunctionDO::getProductId, product.getId())
                .eq(ProductFunctionDO::getStatus, 0)); // 仅启用功能
        List<ResolvedInterface> result = new ArrayList<>();
        for (ProductFunctionDO fn : functions) {
            List<FuncInterfaceDO> binds = bindMapper.selectList(new LambdaQueryWrapperX<FuncInterfaceDO>()
                    .eq(FuncInterfaceDO::getProductFunctionId, fn.getId())
                    .eq(FuncInterfaceDO::getStatus, 0)); // 仅启用绑定
            for (FuncInterfaceDO b : binds) {
                ResolvedInterface ri = new ResolvedInterface();
                ri.setProductFunctionId(fn.getId());
                ri.setFuncCode(fn.getFuncCode());
                ri.setDsInterfaceId(b.getDsInterfaceId());
                ri.setDsInterfaceCode(b.getDsInterfaceCode());
                ri.setPriority(b.getPriority() == null ? 0 : b.getPriority());
                ri.setDefault(Boolean.TRUE.equals(b.getIsDefault()));
                result.add(ri);
            }
        }
        result.sort(Comparator.comparingInt(ResolvedInterface::getPriority));
        return result;
    }

    public ResolvedInterface resolveDefault(String productCode) {
        List<ResolvedInterface> all = resolve(productCode);
        return all.stream()
                .min(Comparator.comparing((ResolvedInterface r) -> !r.isDefault()) // isDefault=true 优先
                        .thenComparingInt(ResolvedInterface::getPriority))
                .orElse(null);
    }
}
