package cn.iocoder.yudao.module.product.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {

    default PageResult<ProductDO> selectPage(ProductPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductDO>()
                .likeIfPresent(ProductDO::getName, reqVO.getName())
                .eqIfPresent(ProductDO::getProductType, reqVO.getProductType())
                .eqIfPresent(ProductDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProductDO::getId));
    }

    default ProductDO selectByProductCode(String productCode) {
        return selectOne(ProductDO::getProductCode, productCode);
    }
}
