package cn.iocoder.yudao.module.route.controller.admin.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 路由配置 Response VO")
@Data
public class RouteConfigRespVO {
    private Long id;
    private String routeCode;
    private String name;
    private String productCode;
    private Long orgId;
    private String targetType;
    private Long targetDsInterfaceId;
    private Integer priority;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
