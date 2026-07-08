package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_NOT_EXISTS;

@Service
@Validated
public class ProductServiceImpl implements ProductService {

    @Resource
    private ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductSaveReqVO reqVO) {
        ProductDO product = BeanUtils.toBean(reqVO, ProductDO.class);
        product.setId(null);
        // product_code 列 NOT NULL + 唯一，先写入临时唯一占位（32 位 UUID，正好落在 varchar(32) 内），
        // 拿到自增 id 后再据 id 回填最终编码；整个过程在同一事务内，外部只可见最终编码
        product.setProductCode(UUID.randomUUID().toString().replace("-", ""));
        productMapper.insert(product);            // id 由 DB 自增分配
        product.setProductCode(String.format("P%06d", product.getId()));
        productMapper.updateById(product);         // 回填编码
        return product.getId();
    }

    @Override
    public void updateProduct(ProductSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        ProductDO product = BeanUtils.toBean(reqVO, ProductDO.class);
        product.setProductCode(null); // 编码不可改
        productMapper.updateById(product);
    }

    @Override
    public void deleteProduct(Long id) {
        validateExists(id);
        productMapper.deleteById(id);
    }

    @Override
    public ProductDO getProduct(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public ProductDO getProductByCode(String productCode) {
        return productMapper.selectByProductCode(productCode);
    }

    @Override
    public PageResult<ProductDO> getProductPage(ProductPageReqVO reqVO) {
        return productMapper.selectPage(reqVO);
    }

    @Override
    public List<ProductDO> getSimpleList() {
        return productMapper.selectList(new LambdaQueryWrapperX<ProductDO>()
                .orderByDesc(ProductDO::getId));
    }

    private ProductDO validateExists(Long id) {
        ProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        return product;
    }
}
