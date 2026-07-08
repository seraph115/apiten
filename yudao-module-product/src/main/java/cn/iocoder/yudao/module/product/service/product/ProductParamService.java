package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductParamDO;
import jakarta.validation.Valid;
import java.util.List;

public interface ProductParamService {
    Long createProductParam(@Valid ProductParamSaveReqVO reqVO);
    void updateProductParam(@Valid ProductParamSaveReqVO reqVO);
    void deleteProductParam(Long id);
    ProductParamDO getProductParam(Long id);
    PageResult<ProductParamDO> getProductParamPage(ProductParamPageReqVO reqVO);
    List<ProductParamDO> getListByProduct(Long productId, Integer paramDirection);
}
