package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import jakarta.validation.Valid;
import java.util.List;

public interface ProductFunctionService {
    Long createProductFunction(@Valid ProductFunctionSaveReqVO reqVO);
    void updateProductFunction(@Valid ProductFunctionSaveReqVO reqVO);
    void deleteProductFunction(Long id);
    ProductFunctionDO getProductFunction(Long id);
    PageResult<ProductFunctionDO> getProductFunctionPage(ProductFunctionPageReqVO reqVO);
    List<ProductFunctionDO> getListByProductId(Long productId);
}
