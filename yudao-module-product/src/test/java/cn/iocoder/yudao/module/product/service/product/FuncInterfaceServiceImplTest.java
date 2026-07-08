package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfacePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfaceSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FUNC_INTERFACE_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_FUNCTION_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(FuncInterfaceServiceImpl.class)
class FuncInterfaceServiceImplTest extends BaseDbUnitTest {

    @Resource
    private FuncInterfaceServiceImpl service;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private ProductFunctionMapper productFunctionMapper;

    private int seq = 0;

    private Long insertProduct() {
        ProductDO product = new ProductDO();
        product.setProductCode(String.format("P%06d", ++seq));
        product.setName("测试产品");
        product.setProductType(1);
        product.setStatus(0);
        productMapper.insert(product);
        return product.getId();
    }

    private Long insertProductFunction(Long productId) {
        ProductFunctionDO function = new ProductFunctionDO();
        function.setFuncCode(String.format("F%06d", ++seq));
        function.setName("测试功能");
        function.setProductId(productId);
        function.setSort(0);
        function.setRequired(false);
        function.setCharge(false);
        function.setStatus(0);
        productFunctionMapper.insert(function);
        return function.getId();
    }

    private FuncInterfaceSaveReqVO newReq(Long productFunctionId, Long dsInterfaceId, String dsInterfaceCode, int priority) {
        FuncInterfaceSaveReqVO vo = new FuncInterfaceSaveReqVO();
        vo.setProductFunctionId(productFunctionId);
        vo.setDsInterfaceId(dsInterfaceId);
        vo.setDsInterfaceCode(dsInterfaceCode);
        vo.setPriority(priority);
        vo.setIsDefault(false);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_persistsFourFields() {
        Long productId = insertProduct();
        Long functionId = insertProductFunction(productId);
        FuncInterfaceSaveReqVO vo = newReq(functionId, 100L, "IF000100", 5);
        Long id = service.createFuncInterface(vo);
        FuncInterfaceDO db = service.getFuncInterface(id);
        assertThat(db.getProductFunctionId()).isEqualTo(functionId);
        assertThat(db.getDsInterfaceId()).isEqualTo(100L);
        assertThat(db.getDsInterfaceCode()).isEqualTo("IF000100");
        assertThat(db.getPriority()).isEqualTo(5);
    }

    @Test
    void create_productFunctionNotExists_throws() {
        assertServiceException(() -> service.createFuncInterface(newReq(999999L, 100L, "IF000100", 0)),
                PRODUCT_FUNCTION_NOT_EXISTS);
    }

    @Test
    void update_bindingNotExists_throws() {
        Long productId = insertProduct();
        Long functionId = insertProductFunction(productId);
        FuncInterfaceSaveReqVO upd = newReq(functionId, 100L, "IF000100", 0);
        upd.setId(88888L);
        assertServiceException(() -> service.updateFuncInterface(upd), FUNC_INTERFACE_NOT_EXISTS);
    }

    @Test
    void getListByFunction_filtersAndOrdersByPriority() {
        Long productId = insertProduct();
        Long f1 = insertProductFunction(productId);
        Long f2 = insertProductFunction(productId);
        service.createFuncInterface(newReq(f1, 101L, "IF000101", 10));
        service.createFuncInterface(newReq(f1, 102L, "IF000102", 2));
        service.createFuncInterface(newReq(f1, 103L, "IF000103", 5));
        service.createFuncInterface(newReq(f2, 104L, "IF000104", 0));

        List<FuncInterfaceDO> list = service.getListByFunction(f1);
        assertThat(list).hasSize(3);
        assertThat(list).extracting(FuncInterfaceDO::getPriority).containsExactly(2, 5, 10);
        assertThat(list).allMatch(item -> item.getProductFunctionId().equals(f1));
    }

    @Test
    void page_filtersByProductFunctionId() {
        Long productId = insertProduct();
        Long f1 = insertProductFunction(productId);
        Long f2 = insertProductFunction(productId);
        service.createFuncInterface(newReq(f1, 101L, "IF000101", 0));
        service.createFuncInterface(newReq(f2, 102L, "IF000102", 0));

        FuncInterfacePageReqVO q = new FuncInterfacePageReqVO();
        q.setProductFunctionId(f1);
        PageResult<FuncInterfaceDO> page = service.getFuncInterfacePage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
