package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源应答码 Response VO")
@Data
public class DsResponseCodeRespVO {
    private Long id;
    private Long dataSourceId;
    private Long dsInterfaceId;
    private String rawCode;
    private String rawDesc;
    private Boolean success;
    private Boolean charge;
    private Boolean retryable;
    private Boolean triggerSwitch;
    private String platformCode;
    private LocalDateTime createTime;
}
