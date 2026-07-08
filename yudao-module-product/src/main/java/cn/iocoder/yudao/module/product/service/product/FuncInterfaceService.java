package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfacePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfaceSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import jakarta.validation.Valid;
import java.util.List;

public interface FuncInterfaceService {
    Long createFuncInterface(@Valid FuncInterfaceSaveReqVO reqVO);
    void updateFuncInterface(@Valid FuncInterfaceSaveReqVO reqVO);
    void deleteFuncInterface(Long id);
    FuncInterfaceDO getFuncInterface(Long id);
    PageResult<FuncInterfaceDO> getFuncInterfacePage(FuncInterfacePageReqVO reqVO);
    List<FuncInterfaceDO> getListByFunction(Long productFunctionId);
}
