package cn.iocoder.yudao.module.route.controller.admin.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 路由配置创建/修改 Request VO")
@Data
public class RouteConfigSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "路由名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "路由名称不能为空")
    private String name;

    @Schema(description = "产品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "产品编码不能为空")
    private String productCode;

    @Schema(description = "机构ID（空=产品默认级，非空=机构产品级）")
    private Long orgId;

    @Schema(description = "目标类型：SINGLE(本期)/SPLIT/CHAIN/DYNAMIC(预留)")
    private String targetType;

    @Schema(description = "SINGLE 目标数据源接口ID（松耦合引用）")
    private Long targetDsInterfaceId;

    @Schema(description = "优先级（越小越优先）")
    private Integer priority;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
