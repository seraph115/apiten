package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_FUNCTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductFunctionServiceImpl.class)
class ProductFunctionServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductFunctionServiceImpl service;
    @Resource
    private ProductMapper productMapper;

    private int productSeq = 0;

    private Long insertProduct() {
        ProductDO product = new ProductDO();
        product.setProductCode(String.format("P%06d", ++productSeq));
        product.setName("测试产品");
        product.setProductType(1);
        product.setStatus(0);
        productMapper.insert(product);
        return product.getId();
    }

    private ProductFunctionSaveReqVO newReq(String name, Long productId) {
        ProductFunctionSaveReqVO vo = new ProductFunctionSaveReqVO();
        vo.setName(name);
        vo.setProductId(productId);
        vo.setSort(0);
        vo.setRequired(false);
        vo.setCharge(false);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesFuncCode() {
        Long productId = insertProduct();
        Long id = service.createProductFunction(newReq("工商信息查询", productId));
        ProductFunctionDO db = service.getProductFunction(id);
        assertThat(db.getFuncCode()).matches("F\\d{6}");
        assertThat(db.getProductId()).isEqualTo(productId);
    }

    @Test
    void create_productNotExists_throws() {
        assertServiceException(() -> service.createProductFunction(newReq("孤儿功能", 999999L)),
                PRODUCT_NOT_EXISTS);
    }

    @Test
    void update_functionNotExists_throws() {
        Long productId = insertProduct();
        ProductFunctionSaveReqVO upd = newReq("x", productId);
        upd.setId(88888L);
        assertServiceException(() -> service.updateProductFunction(upd), PRODUCT_FUNCTION_NOT_EXISTS);
    }

    @Test
    void update_productNotExists_throws() {
        Long productId = insertProduct();
        Long id = service.createProductFunction(newReq("功能", productId));
        ProductFunctionSaveReqVO upd = newReq("改", 999999L); // 存在的功能 id + 不存在的产品
        upd.setId(id);
        assertServiceException(() -> service.updateProductFunction(upd), PRODUCT_NOT_EXISTS);
    }

    @Test
    void listByProductId_filters() {
        Long p1 = insertProduct();
        Long p2 = insertProduct();
        service.createProductFunction(newReq("A", p1));
        service.createProductFunction(newReq("B", p1));
        service.createProductFunction(newReq("C", p2));
        List<ProductFunctionDO> list = service.getListByProductId(p1);
        assertThat(list).hasSize(2);
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        Long productId = insertProduct();
        Long id1 = service.createProductFunction(newReq("功能1", productId));
        Long id2 = service.createProductFunction(newReq("功能2", productId));
        String c2 = service.getProductFunction(id2).getFuncCode();
        service.deleteProductFunction(id2);
        Long id3 = service.createProductFunction(newReq("功能3", productId)); // 删除后再建
        String c3 = service.getProductFunction(id3).getFuncCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("F\\d{6}");
    }

    @Test
    void page_filtersByName() {
        Long productId = insertProduct();
        service.createProductFunction(newReq("工商查询", productId));
        service.createProductFunction(newReq("司法查询", productId));
        ProductFunctionPageReqVO q = new ProductFunctionPageReqVO();
        q.setName("工商");
        PageResult<ProductFunctionDO> page = service.getProductFunctionPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
