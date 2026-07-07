package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 数据源 Response VO")
@Data
public class DataSourceRespVO {
    private Long id;
    private String dsCode;
    private String name;
    private String supplierName;
    private Integer sourceType;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private Integer envType;
    private String serviceAddr;
    private Integer authType;
    private Integer timeoutMs;
    private Integer maxConcurrency;
    private Integer retryCount;
    private Boolean routable;
    private Integer protocolType;
    private String protocolExtConfig;
    private String remark;
    private LocalDateTime createTime;
}
