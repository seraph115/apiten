package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 功能接口绑定 Response VO")
@Data
public class FuncInterfaceRespVO {
    private Long id;
    private Long productFunctionId;
    private Long dsInterfaceId;
    private String dsInterfaceCode;
    private Integer priority;
    private Boolean isDefault;
    private Integer status;
    private LocalDateTime createTime;
}
