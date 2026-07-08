package cn.iocoder.yudao.module.product.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.ProductFunctionPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.ProductFunctionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProductFunctionMapper extends BaseMapperX<ProductFunctionDO> {

    default PageResult<ProductFunctionDO> selectPage(ProductFunctionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductFunctionDO>()
                .likeIfPresent(ProductFunctionDO::getName, reqVO.getName())
                .eqIfPresent(ProductFunctionDO::getProductId, reqVO.getProductId())
                .eqIfPresent(ProductFunctionDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProductFunctionDO::getId));
    }

    default List<ProductFunctionDO> selectListByProductId(Long productId) {
        return selectList(ProductFunctionDO::getProductId, productId);
    }
}
