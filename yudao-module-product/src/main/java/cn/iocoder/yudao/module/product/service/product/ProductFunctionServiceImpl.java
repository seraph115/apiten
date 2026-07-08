package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_FUNCTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;

@Service
@Validated
public class ProductFunctionServiceImpl implements ProductFunctionService {

    @Resource
    private ProductFunctionMapper productFunctionMapper;
    @Resource
    private ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProductFunction(ProductFunctionSaveReqVO reqVO) {
        validateProductExists(reqVO.getProductId());
        ProductFunctionDO function = BeanUtils.toBean(reqVO, ProductFunctionDO.class);
        function.setId(null);
        // func_code 列 NOT NULL + 唯一，先写入临时唯一占位（32 位 UUID，正好落在 varchar(32) 内），
        // 拿到自增 id 后再据 id 回填最终编码；整个过程在同一事务内，外部只可见最终编码
        function.setFuncCode(UUID.randomUUID().toString().replace("-", ""));
        productFunctionMapper.insert(function);        // id 由 DB 自增分配
        function.setFuncCode(String.format("F%06d", function.getId()));
        productFunctionMapper.updateById(function);     // 回填编码
        return function.getId();
    }

    @Override
    public void updateProductFunction(ProductFunctionSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateProductExists(reqVO.getProductId());
        ProductFunctionDO function = BeanUtils.toBean(reqVO, ProductFunctionDO.class);
        function.setFuncCode(null); // 编码不可改
        productFunctionMapper.updateById(function);
    }

    @Override
    public void deleteProductFunction(Long id) {
        validateExists(id);
        productFunctionMapper.deleteById(id);
    }

    @Override
    public ProductFunctionDO getProductFunction(Long id) {
        return productFunctionMapper.selectById(id);
    }

    @Override
    public PageResult<ProductFunctionDO> getProductFunctionPage(ProductFunctionPageReqVO reqVO) {
        return productFunctionMapper.selectPage(reqVO);
    }

    @Override
    public List<ProductFunctionDO> getListByProductId(Long productId) {
        return productFunctionMapper.selectListByProductId(productId);
    }

    private ProductFunctionDO validateExists(Long id) {
        ProductFunctionDO function = productFunctionMapper.selectById(id);
        if (function == null) {
            throw exception(PRODUCT_FUNCTION_NOT_EXISTS);
        }
        return function;
    }

    private void validateProductExists(Long productId) {
        if (productMapper.selectById(productId) == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
    }
}
