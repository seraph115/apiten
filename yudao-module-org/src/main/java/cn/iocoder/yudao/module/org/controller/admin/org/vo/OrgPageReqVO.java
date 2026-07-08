package cn.iocoder.yudao.module.org.controller.admin.org.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 机构分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class OrgPageReqVO extends PageParam {

    @Schema(description = "机构名称")
    private String name;

    @Schema(description = "状态")
    private Integer status;
}
