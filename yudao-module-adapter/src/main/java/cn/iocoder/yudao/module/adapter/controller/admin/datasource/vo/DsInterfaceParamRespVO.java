package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源接口参数 Response VO")
@Data
public class DsInterfaceParamRespVO {
    private Long id;
    private Long dsInterfaceId;
    private Integer paramDirection;
    private String platformField;
    private String providerField;
    private Integer dataType;
    private Boolean required;
    private String transformFn;
    private String defaultValue;
    private String jsonPath;
    private String remark;
    private LocalDateTime createTime;
}
