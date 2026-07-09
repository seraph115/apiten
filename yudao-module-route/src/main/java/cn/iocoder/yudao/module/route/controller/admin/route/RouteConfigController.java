package cn.iocoder.yudao.module.route.controller.admin.route;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigPageReqVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigRespVO;
import cn.iocoder.yudao.module.route.controller.admin.route.vo.RouteConfigSaveReqVO;
import cn.iocoder.yudao.module.route.dal.dataobject.route.RouteConfigDO;
import cn.iocoder.yudao.module.route.service.route.RouteConfigService;
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

@Tag(name = "管理后台 - 路由配置")
@RestController
@RequestMapping("/route/config")
@Validated
public class RouteConfigController {

    @Resource
    private RouteConfigService routeConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建路由配置")
    @PreAuthorize("@ss.hasPermission('route:config:create')")
    public CommonResult<Long> create(@Valid @RequestBody RouteConfigSaveReqVO reqVO) {
        return success(routeConfigService.createRouteConfig(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新路由配置")
    @PreAuthorize("@ss.hasPermission('route:config:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody RouteConfigSaveReqVO reqVO) {
        routeConfigService.updateRouteConfig(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除路由配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('route:config:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        routeConfigService.deleteRouteConfig(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得路由配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('route:config:query')")
    public CommonResult<RouteConfigRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(routeConfigService.getRouteConfig(id), RouteConfigRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得路由配置分页")
    @PreAuthorize("@ss.hasPermission('route:config:query')")
    public CommonResult<PageResult<RouteConfigRespVO>> page(@Valid RouteConfigPageReqVO reqVO) {
        PageResult<RouteConfigDO> page = routeConfigService.getRouteConfigPage(reqVO);
        return success(BeanUtils.toBean(page, RouteConfigRespVO.class));
    }

    @GetMapping("/list-by-product")
    @Operation(summary = "按产品编码获得路由配置列表")
    @Parameter(name = "productCode", description = "产品编码", required = true)
    @PreAuthorize("@ss.hasPermission('route:config:query')")
    public CommonResult<List<RouteConfigRespVO>> listByProduct(@RequestParam("productCode") String productCode) {
        return success(BeanUtils.toBean(routeConfigService.getListByProductCode(productCode), RouteConfigRespVO.class));
    }
}
