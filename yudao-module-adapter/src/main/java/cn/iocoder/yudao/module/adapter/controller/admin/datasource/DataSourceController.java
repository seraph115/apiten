package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourcePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DataSourceSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DataSourceDO;
import cn.iocoder.yudao.module.adapter.service.datasource.DataSourceService;
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

@Tag(name = "管理后台 - 数据源")
@RestController
@RequestMapping("/adapter/data-source")
@Validated
public class DataSourceController {

    @Resource
    private DataSourceService dataSourceService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:create')")
    public CommonResult<Long> create(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        return success(dataSourceService.createDataSource(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DataSourceSaveReqVO reqVO) {
        dataSourceService.updateDataSource(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:data-source:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        dataSourceService.deleteDataSource(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<DataSourceRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dataSourceService.getDataSource(id), DataSourceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源分页")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<PageResult<DataSourceRespVO>> page(@Valid DataSourcePageReqVO reqVO) {
        PageResult<DataSourceDO> page = dataSourceService.getDataSourcePage(reqVO);
        return success(BeanUtils.toBean(page, DataSourceRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得数据源精简列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('adapter:data-source:query')")
    public CommonResult<List<DataSourceRespVO>> simpleList() {
        return success(BeanUtils.toBean(dataSourceService.getSimpleList(), DataSourceRespVO.class));
    }
}
