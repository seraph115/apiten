package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源接口 Response VO")
@Data
public class DsInterfaceRespVO {
    private Long id;
    private String ifCode;
    private String name;
    private Long dataSourceId;
    private String uri;
    private String method;
    private Integer msgFormat;
    private Integer signType;
    private Integer encryptType;
    private String authParams;
    private String version;
    private Integer status;
    private Integer timeoutMs;
    private Integer retryCount;
    private Boolean cacheEnabled;
    private Integer cacheTtl;
    private String cacheKey;
    private String remark;
    private LocalDateTime createTime;
}
