package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import jakarta.validation.Valid;
import java.util.List;

public interface ProductService {
    Long createProduct(@Valid ProductSaveReqVO reqVO);
    void updateProduct(@Valid ProductSaveReqVO reqVO);
    void deleteProduct(Long id);
    ProductDO getProduct(Long id);
    ProductDO getProductByCode(String productCode);
    PageResult<ProductDO> getProductPage(ProductPageReqVO reqVO);
    List<ProductDO> getSimpleList();
}
