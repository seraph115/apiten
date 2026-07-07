package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamPageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceParamSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import cn.iocoder.yudao.module.adapter.service.datasource.DsInterfaceParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据源接口参数")
@RestController
@RequestMapping("/adapter/ds-interface-param")
@Validated
public class DsInterfaceParamController {

    @Resource
    private DsInterfaceParamService dsInterfaceParamService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源接口参数")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:create')")
    public CommonResult<Long> create(@Valid @RequestBody DsInterfaceParamSaveReqVO reqVO) {
        return success(dsInterfaceParamService.createDsInterfaceParam(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源接口参数")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DsInterfaceParamSaveReqVO reqVO) {
        dsInterfaceParamService.updateDsInterfaceParam(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源接口参数")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        dsInterfaceParamService.deleteDsInterfaceParam(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源接口参数")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:query')")
    public CommonResult<DsInterfaceParamRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dsInterfaceParamService.getDsInterfaceParam(id), DsInterfaceParamRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源接口参数分页")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:query')")
    public CommonResult<PageResult<DsInterfaceParamRespVO>> page(@Valid DsInterfaceParamPageReqVO reqVO) {
        PageResult<DsInterfaceParamDO> page = dsInterfaceParamService.getDsInterfaceParamPage(reqVO);
        return success(BeanUtils.toBean(page, DsInterfaceParamRespVO.class));
    }

    @GetMapping("/list-by-interface")
    @Operation(summary = "按接口获得参数列表")
    @Parameter(name = "dsInterfaceId", description = "接口ID", required = true)
    @Parameter(name = "paramDirection", description = "方向：1入参 2出参")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface-param:query')")
    public CommonResult<List<DsInterfaceParamRespVO>> listByInterface(
            @RequestParam("dsInterfaceId") Long dsInterfaceId,
            @RequestParam(value = "paramDirection", required = false) Integer paramDirection) {
        return success(BeanUtils.toBean(
                dsInterfaceParamService.getListByInterface(dsInterfaceId, paramDirection),
                DsInterfaceParamRespVO.class));
    }
}
