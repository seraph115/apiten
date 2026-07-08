package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 机构账号 Response VO")
@Data
public class OrgAccountRespVO {
    private Long id;
    private Long orgId;
    private String accountName;
    private String appKey;

    @Schema(description = "SK 明文，仅创建/重置密钥响应携带，其余接口为 null")
    private String secretKey;

    private Integer keyStatus;
    private String ipWhitelist;
    private String callbackUrl;
    private String signAlgorithm;
    private LocalDateTime expireTime;
    private Integer concurrencyLimit;
    private Integer dailyLimit;
    private Integer monthlyLimit;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
