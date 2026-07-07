package cn.iocoder.yudao.module.adapter.controller.admin.datasource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodePageReqVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeRespVO;
import cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo.DsResponseCodeSaveReqVO;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.service.datasource.DsResponseCodeService;
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

@Tag(name = "管理后台 - 数据源应答码")
@RestController
@RequestMapping("/adapter/ds-response-code")
@Validated
public class DsResponseCodeController {

    @Resource
    private DsResponseCodeService dsResponseCodeService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源应答码")
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:create')")
    public CommonResult<Long> create(@Valid @RequestBody DsResponseCodeSaveReqVO reqVO) {
        return success(dsResponseCodeService.createDsResponseCode(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源应答码")
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody DsResponseCodeSaveReqVO reqVO) {
        dsResponseCodeService.updateDsResponseCode(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源应答码")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        dsResponseCodeService.deleteDsResponseCode(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源应答码")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:query')")
    public CommonResult<DsResponseCodeRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dsResponseCodeService.getDsResponseCode(id), DsResponseCodeRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据源应答码分页")
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:query')")
    public CommonResult<PageResult<DsResponseCodeRespVO>> page(@Valid DsResponseCodePageReqVO reqVO) {
        PageResult<DsResponseCodeDO> page = dsResponseCodeService.getDsResponseCodePage(reqVO);
        return success(BeanUtils.toBean(page, DsResponseCodeRespVO.class));
    }

    @GetMapping("/unmapped-list")
    @Operation(summary = "获得未映射平台码的原始应答码列表")
    @Parameter(name = "dataSourceId", description = "数据源ID", required = true)
    @PreAuthorize("@ss.hasPermission('adapter:ds-response-code:query')")
    public CommonResult<List<DsResponseCodeRespVO>> unmappedList(@RequestParam("dataSourceId") Long dataSourceId) {
        return success(BeanUtils.toBean(dsResponseCodeService.getUnmappedList(dataSourceId), DsResponseCodeRespVO.class));
    }
}
