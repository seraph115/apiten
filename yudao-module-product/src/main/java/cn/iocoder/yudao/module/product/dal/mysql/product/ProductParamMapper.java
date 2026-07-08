package cn.iocoder.yudao.module.product.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductParamPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductParamDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProductParamMapper extends BaseMapperX<ProductParamDO> {

    default PageResult<ProductParamDO> selectPage(ProductParamPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductParamDO>()
                .eqIfPresent(ProductParamDO::getProductId, reqVO.getProductId())
                .eqIfPresent(ProductParamDO::getParamDirection, reqVO.getParamDirection())
                .likeIfPresent(ProductParamDO::getFieldName, reqVO.getFieldName())
                .orderByDesc(ProductParamDO::getId));
    }

    default List<ProductParamDO> selectListByProduct(Long productId, Integer paramDirection) {
        return selectList(new LambdaQueryWrapperX<ProductParamDO>()
                .eq(ProductParamDO::getProductId, productId)
                .eqIfPresent(ProductParamDO::getParamDirection, paramDirection)
                .orderByAsc(ProductParamDO::getSort));
    }
}
