package cn.iocoder.yudao.module.route.controller.admin.route.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 路由配置分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RouteConfigPageReqVO extends PageParam {

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "机构ID")
    private Long orgId;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "状态")
    private Integer status;
}
