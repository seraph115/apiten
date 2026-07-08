package cn.iocoder.yudao.module.product.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfacePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.product.vo.FuncInterfaceSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.product.FuncInterfaceDO;
import cn.iocoder.yudao.module.product.dal.mysql.product.FuncInterfaceMapper;
import cn.iocoder.yudao.module.product.dal.mysql.product.ProductFunctionMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import jakarta.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.FUNC_INTERFACE_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PRODUCT_FUNCTION_NOT_EXISTS;

@Service
@Validated
public class FuncInterfaceServiceImpl implements FuncInterfaceService {

    @Resource
    private FuncInterfaceMapper funcInterfaceMapper;
    @Resource
    private ProductFunctionMapper productFunctionMapper;

    @Override
    public Long createFuncInterface(FuncInterfaceSaveReqVO reqVO) {
        validateProductFunctionExists(reqVO.getProductFunctionId());
        FuncInterfaceDO funcInterface = BeanUtils.toBean(reqVO, FuncInterfaceDO.class);
        funcInterface.setId(null);
        funcInterfaceMapper.insert(funcInterface);
        return funcInterface.getId();
    }

    @Override
    public void updateFuncInterface(FuncInterfaceSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateProductFunctionExists(reqVO.getProductFunctionId());
        funcInterfaceMapper.updateById(BeanUtils.toBean(reqVO, FuncInterfaceDO.class));
    }

    @Override
    public void deleteFuncInterface(Long id) {
        validateExists(id);
        funcInterfaceMapper.deleteById(id);
    }

    @Override
    public FuncInterfaceDO getFuncInterface(Long id) {
        return funcInterfaceMapper.selectById(id);
    }

    @Override
    public PageResult<FuncInterfaceDO> getFuncInterfacePage(FuncInterfacePageReqVO reqVO) {
        return funcInterfaceMapper.selectPage(reqVO);
    }

    @Override
    public List<FuncInterfaceDO> getListByFunction(Long productFunctionId) {
        return funcInterfaceMapper.selectListByFunction(productFunctionId);
    }

    private FuncInterfaceDO validateExists(Long id) {
        FuncInterfaceDO funcInterface = funcInterfaceMapper.selectById(id);
        if (funcInterface == null) {
            throw exception(FUNC_INTERFACE_NOT_EXISTS);
        }
        return funcInterface;
    }

    private void validateProductFunctionExists(Long productFunctionId) {
        if (productFunctionMapper.selectById(productFunctionId) == null) {
            throw exception(PRODUCT_FUNCTION_NOT_EXISTS);
        }
    }
}
