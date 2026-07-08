package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductParamDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_PARAM_NOT_EXISTS;

@Service
@Validated
public class ProductParamServiceImpl implements ProductParamService {

    @Resource
    private ProductParamMapper productParamMapper;
    @Resource
    private ProductMapper productMapper;

    @Override
    public Long createProductParam(ProductParamSaveReqVO reqVO) {
        validateProductExists(reqVO.getProductId());
        ProductParamDO param = BeanUtils.toBean(reqVO, ProductParamDO.class);
        param.setId(null);
        productParamMapper.insert(param);
        return param.getId();
    }

    @Override
    public void updateProductParam(ProductParamSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateProductExists(reqVO.getProductId());
        productParamMapper.updateById(BeanUtils.toBean(reqVO, ProductParamDO.class));
    }

    @Override
    public void deleteProductParam(Long id) {
        validateExists(id);
        productParamMapper.deleteById(id);
    }

    @Override
    public ProductParamDO getProductParam(Long id) {
        return productParamMapper.selectById(id);
    }

    @Override
    public PageResult<ProductParamDO> getProductParamPage(ProductParamPageReqVO reqVO) {
        return productParamMapper.selectPage(reqVO);
    }

    @Override
    public List<ProductParamDO> getListByProduct(Long productId, Integer paramDirection) {
        return productParamMapper.selectListByProduct(productId, paramDirection);
    }

    private ProductParamDO validateExists(Long id) {
        ProductParamDO param = productParamMapper.selectById(id);
        if (param == null) {
            throw exception(PRODUCT_PARAM_NOT_EXISTS);
        }
        return param;
    }

    private void validateProductExists(Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
    }
}
