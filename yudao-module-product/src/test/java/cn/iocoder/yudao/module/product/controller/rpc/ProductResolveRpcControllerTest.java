package cn.iocoder.yudao.module.product.controller.rpc;

import cn.apiten.common.route.ProductDefaultRespDTO;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.product.service.resolve.ProductInterfaceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@Import({ProductResolveRpcController.class, ProductInterfaceResolver.class})
class ProductResolveRpcControllerTest extends BaseDbUnitTest {

    @Resource private ProductResolveRpcController controller;
    @Resource private ProductMapper productMapper;
    @Resource private ProductFunctionMapper functionMapper;
    @Resource private FuncInterfaceMapper bindMapper;

    private Long product(String code) {
        ProductDO p = new ProductDO();
        p.setProductCode(code); p.setName("产品"); p.setProductType(1); p.setStatus(0);
        productMapper.insert(p); return p.getId();
    }
    private Long func(Long productId, String code) {
        ProductFunctionDO f = new ProductFunctionDO();
        f.setFuncCode(code); f.setName("功能"); f.setProductId(productId); f.setStatus(0);
        functionMapper.insert(f); return f.getId();
    }
    private void bind(Long funcId, Long ifId, String ifCode, int priority, boolean isDefault) {
        FuncInterfaceDO b = new FuncInterfaceDO();
        b.setProductFunctionId(funcId); b.setDsInterfaceId(ifId); b.setDsInterfaceCode(ifCode);
        b.setPriority(priority); b.setIsDefault(isDefault); b.setStatus(0);
        bindMapper.insert(b);
    }

    @Test
    void resolveDefault_returnsDefaultBinding() {
        Long pid = product("P000001");
        Long fid = func(pid, "F000001");
        bind(fid, 100L, "IF000001", 1, false);
        bind(fid, 200L, "IF000002", 2, true); // 默认优先
        ProductDefaultRespDTO d = controller.resolveDefault("P000001");
        assertThat(d.getDsInterfaceId()).isEqualTo(200L);
        assertThat(d.getDsInterfaceCode()).isEqualTo("IF000002");
    }

    @Test
    void resolveDefault_noBinding_returnsEmpty() {
        product("P000002");
        ProductDefaultRespDTO d = controller.resolveDefault("P000002");
        assertThat(d.getDsInterfaceId()).isNull();
    }

    @Test
    void resolveDefault_productNotExists_returnsEmpty_notThrow() {
        ProductDefaultRespDTO d = controller.resolveDefault("NOPE");
        assertThat(d.getDsInterfaceId()).isNull();
    }
}
