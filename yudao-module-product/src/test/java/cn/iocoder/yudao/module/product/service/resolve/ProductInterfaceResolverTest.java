package cn.iocoder.yudao.module.product.service.resolve;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductInterfaceResolver.class)
class ProductInterfaceResolverTest extends BaseDbUnitTest {

    @Resource private ProductInterfaceResolver resolver;
    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    private Long product(String code) {
        ProductDO p = new ProductDO();
        p.setProductCode(code); p.setName("产品"); p.setProductType(1); p.setStatus(0);
        productMapper.insert(p);
        return p.getId();
    }
    private Long func(Long productId, String code) {
        ProductFunctionDO f = new ProductFunctionDO();
        f.setFuncCode(code); f.setName("功能"); f.setProductId(productId); f.setStatus(0);
        functionMapper.insert(f);
        return f.getId();
    }
    private void bind(Long funcId, Long ifId, String ifCode, int priority, boolean isDefault) {
        FuncInterfaceDO b = new FuncInterfaceDO();
        b.setProductFunctionId(funcId); b.setDsInterfaceId(ifId); b.setDsInterfaceCode(ifCode);
        b.setPriority(priority); b.setIsDefault(isDefault); b.setStatus(0);
        bindMapper.insert(b);
    }

    @Test
    void resolve_collectsBoundInterfacesSortedByPriority() {
        Long pid = product("P000001");
        Long fid = func(pid, "F000001");
        bind(fid, 200L, "IF000002", 2, false);
        bind(fid, 100L, "IF000001", 1, true);
        List<ResolvedInterface> list = resolver.resolve("P000001");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getDsInterfaceId()).isEqualTo(100L); // priority 1 在前
    }

    @Test
    void resolveDefault_prefersDefaultFlag() {
        Long pid = product("P000002");
        Long fid = func(pid, "F000002");
        bind(fid, 100L, "IF000001", 1, false);
        bind(fid, 200L, "IF000002", 2, true); // 默认但 priority 更大
        ResolvedInterface d = resolver.resolveDefault("P000002");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L); // isDefault 优先于 priority
    }

    @Test
    void resolve_productNotExists_throws() {
        assertServiceException(() -> resolver.resolve("NOPE"), PRODUCT_NOT_EXISTS);
    }

    @Test
    void resolve_ignoresDisabledFunctionAndBinding() {
        Long pid = product("P000003");
        Long enabledFn = func(pid, "F000003");
        bind(enabledFn, 100L, "IF000001", 1, true);
        ProductFunctionDO disabled = new ProductFunctionDO();
        disabled.setFuncCode("F000004"); disabled.setName("停用"); disabled.setProductId(pid); disabled.setStatus(1);
        functionMapper.insert(disabled);
        bind(disabled.getId(), 300L, "IF000003", 1, true);
        List<ResolvedInterface> list = resolver.resolve("P000003");
        assertThat(list).extracting(ResolvedInterface::getDsInterfaceId).containsExactly(100L);
    }

    @Test
    void resolveDefault_noBinding_returnsNull() {
        product("P000005");
        assertThat(resolver.resolveDefault("P000005")).isNull();
    }
}
