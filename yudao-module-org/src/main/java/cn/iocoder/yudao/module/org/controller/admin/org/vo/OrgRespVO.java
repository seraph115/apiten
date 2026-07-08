package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 机构 Response VO")
@Data
public class OrgRespVO {
    private Long id;
    private String orgCode;
    private String name;
    private String unifiedSocialCreditCode;
    private String contactPerson;
    private String contactPhone;
    private Integer status;
    private String businessOwner;
    private String remark;
    private LocalDateTime createTime;
}
