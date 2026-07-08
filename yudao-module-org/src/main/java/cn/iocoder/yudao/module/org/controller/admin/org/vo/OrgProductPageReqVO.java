package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 机构产品开通分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class OrgProductPageReqVO extends PageParam {

    @Schema(description = "所属机构ID")
    private Long orgId;

    @Schema(description = "产品编码快照")
    private String productCode;

    @Schema(description = "状态")
    private Integer status;
}
