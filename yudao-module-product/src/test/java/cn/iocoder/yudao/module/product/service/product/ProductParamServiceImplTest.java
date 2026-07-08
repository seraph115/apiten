package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductParamDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_PARAM_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductParamServiceImpl.class)
class ProductParamServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductParamServiceImpl service;
    @Resource
    private ProductMapper productMapper;

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

    private ProductParamSaveReqVO newReq(Long productId, Integer paramDirection, String fieldName, int sort) {
        ProductParamSaveReqVO vo = new ProductParamSaveReqVO();
        vo.setProductId(productId);
        vo.setParamDirection(paramDirection);
        vo.setFieldName(fieldName);
        vo.setDataType(1);
        vo.setRequired(false);
        vo.setSort(sort);
        return vo;
    }

    @Test
    void create_inputParam_persisted() {
        Long productId = insertProduct();
        ProductParamSaveReqVO vo = newReq(productId, 1, "idCard", 0);
        vo.setValidationRule("regex:^[0-9]{18}$");
        Long id = service.createProductParam(vo);

        ProductParamDO db = service.getProductParam(id);
        assertThat(db.getProductId()).isEqualTo(productId);
        assertThat(db.getParamDirection()).isEqualTo(1);
        assertThat(db.getFieldName()).isEqualTo("idCard");
        assertThat(db.getValidationRule()).isEqualTo("regex:^[0-9]{18}$");
    }

    @Test
    void create_outputParam_carriesDesensitizeRule() {
        Long productId = insertProduct();
        ProductParamSaveReqVO vo = newReq(productId, 2, "phone", 0);
        vo.setDesensitizeRule("mobile:3,4");
        Long id = service.createProductParam(vo);

        ProductParamDO db = service.getProductParam(id);
        assertThat(db.getParamDirection()).isEqualTo(2);
        assertThat(db.getDesensitizeRule()).isEqualTo("mobile:3,4");
    }

    @Test
    void create_productNotExists_throws() {
        assertServiceException(() -> service.createProductParam(newReq(999999L, 1, "field", 0)),
                PRODUCT_NOT_EXISTS);
    }

    @Test
    void update_paramNotExists_throws() {
        Long productId = insertProduct();
        ProductParamSaveReqVO upd = newReq(productId, 1, "field", 0);
        upd.setId(88888L);
        assertServiceException(() -> service.updateProductParam(upd), PRODUCT_PARAM_NOT_EXISTS);
    }

    @Test
    void getListByProduct_filtersByProductAndDirection() {
        Long p1 = insertProduct();
        Long p2 = insertProduct();
        service.createProductParam(newReq(p1, 1, "in1", 10));
        service.createProductParam(newReq(p1, 1, "in2", 2));
        service.createProductParam(newReq(p1, 2, "out1", 5));
        service.createProductParam(newReq(p2, 1, "other", 0));

        List<ProductParamDO> list = service.getListByProduct(p1, 1);
        assertThat(list).hasSize(2);
        assertThat(list).extracting(ProductParamDO::getSort).containsExactly(2, 10);
        assertThat(list).allMatch(item -> item.getProductId().equals(p1) && item.getParamDirection().equals(1));
    }

    @Test
    void page_filtersByProductId() {
        Long p1 = insertProduct();
        Long p2 = insertProduct();
        service.createProductParam(newReq(p1, 1, "field1", 0));
        service.createProductParam(newReq(p2, 1, "field2", 0));

        ProductParamPageReqVO q = new ProductParamPageReqVO();
        q.setProductId(p1);
        PageResult<ProductParamDO> page = service.getProductParamPage(q);
        assertThat(page.getTotal()).isEqualTo(1);
    }
}
