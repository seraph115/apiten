package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 机构账号创建/修改 Request VO")
@Data
public class OrgAccountSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属机构ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属机构不能为空")
    private Long orgId;

    @Schema(description = "账号名称")
    private String accountName;

    @Schema(description = "IP白名单(逗号分隔,支持CIDR段)")
    private String ipWhitelist;

    @Schema(description = "回调地址")
    private String callbackUrl;

    @Schema(description = "签名算法")
    private String signAlgorithm;

    @Schema(description = "账号有效期(空为长期)")
    private LocalDateTime expireTime;

    @Schema(description = "账号级最大并发")
    private Integer concurrencyLimit;

    @Schema(description = "账号级日调用量上限")
    private Integer dailyLimit;

    @Schema(description = "账号级月调用量上限")
    private Integer monthlyLimit;

    @Schema(description = "状态：0启用 1停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
