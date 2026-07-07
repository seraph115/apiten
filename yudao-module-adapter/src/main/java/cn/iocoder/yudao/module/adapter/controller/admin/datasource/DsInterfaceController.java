package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfacePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsInterfaceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceDO;
import cn.iocoder.yudao.module.adapter.service.datasource.DsInterfaceService;
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

@Tag(name = "管理后台 - 数据源接口")
@RestController
@RequestMapping("/adapter/ds-interface")
@Validated
public class DsInterfaceController {

    @Resource
    private DsInterfaceService dsInterfaceService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源接口")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:create')")
    public CommonResult<Long> create(@Valid @RequestBody DsInterfaceSaveReqVO reqVO) {
        return success(dsInterfaceService.createDsInterface(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源接口")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DsInterfaceSaveReqVO reqVO) {
        dsInterfaceService.updateDsInterface(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源接口")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        dsInterfaceService.deleteDsInterface(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源接口")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:query')")
    public CommonResult<DsInterfaceRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dsInterfaceService.getDsInterface(id), DsInterfaceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源接口分页")
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:query')")
    public CommonResult<PageResult<DsInterfaceRespVO>> page(@Valid DsInterfacePageReqVO reqVO) {
        PageResult<DsInterfaceDO> page = dsInterfaceService.getDsInterfacePage(reqVO);
        return success(BeanUtils.toBean(page, DsInterfaceRespVO.class));
    }

    @GetMapping("/list-by-data-source")
    @Operation(summary = "按数据源获得接口列表")
    @Parameter(name = "dataSourceId", description = "数据源ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-interface:query')")
    public CommonResult<List<DsInterfaceRespVO>> listByDataSource(@RequestParam("dataSourceId") Long dataSourceId) {
        return success(BeanUtils.toBean(dsInterfaceService.getListByDataSourceId(dataSourceId), DsInterfaceRespVO.class));
    }
}
