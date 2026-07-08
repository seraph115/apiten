package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductServiceImpl.class)
class ProductServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductServiceImpl service;

    private ProductSaveReqVO newReq(String name) {
        ProductSaveReqVO vo = new ProductSaveReqVO();
        vo.setName(name);
        vo.setProductType(1);
        vo.setStatus(0);
        return vo;
    }

    @Test
    void create_generatesProductCode() {
        Long id = service.createProduct(newReq("企业工商信息"));
        ProductDO db = service.getProduct(id);
        assertThat(db.getProductCode()).matches("P\\d{6}");
        assertThat(db.getName()).isEqualTo("企业工商信息");
    }

    @Test
    void getByCode_returnsProduct() {
        Long id = service.createProduct(newReq("个人核验"));
        String code = service.getProduct(id).getProductCode();
        assertThat(service.getProductByCode(code).getId()).isEqualTo(id);
    }

    @Test
    void update_notExists_throws() {
        ProductSaveReqVO upd = newReq("x");
        upd.setId(99999L);
        assertServiceException(() -> service.updateProduct(upd), PRODUCT_NOT_EXISTS);
    }

    @Test
    void delete_thenNull() {
        Long id = service.createProduct(newReq("待删"));
        service.deleteProduct(id);
        assertThat(service.getProduct(id)).isNull();
    }

    @Test
    void create_afterDelete_noDuplicateCode() {
        Long id1 = service.createProduct(newReq("A"));
        Long id2 = service.createProduct(newReq("B"));
        String c2 = service.getProduct(id2).getProductCode();
        service.deleteProduct(id2);
        Long id3 = service.createProduct(newReq("C"));
        String c3 = service.getProduct(id3).getProductCode();
        assertThat(c3).isNotEqualTo(c2);
        assertThat(c3).matches("P\\d{6}");
    }

    @Test
    void page_filtersByName() {
        service.createProduct(newReq("工商信息"));
        service.createProduct(newReq("司法涉诉"));
        ProductPageReqVO q = new ProductPageReqVO();
        q.setName("工商");
        PageResult<ProductDO> page = service.getProductPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
