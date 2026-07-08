package cn.iocoder.yudao.module.product.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfacePageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FuncInterfaceMapper extends BaseMapperX<FuncInterfaceDO> {

    default PageResult<FuncInterfaceDO> selectPage(FuncInterfacePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FuncInterfaceDO>()
                .eqIfPresent(FuncInterfaceDO::getProductFunctionId, reqVO.getProductFunctionId())
                .eqIfPresent(FuncInterfaceDO::getDsInterfaceId, reqVO.getDsInterfaceId())
                .eqIfPresent(FuncInterfaceDO::getStatus, reqVO.getStatus())
                .orderByDesc(FuncInterfaceDO::getId));
    }

    default List<FuncInterfaceDO> selectListByFunction(Long productFunctionId) {
        return selectList(new LambdaQueryWrapperX<FuncInterfaceDO>()
                .eq(FuncInterfaceDO::getProductFunctionId, productFunctionId)
                .orderByAsc(FuncInterfaceDO::getPriority));
    }
}
