package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品功能分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductFunctionPageReqVO extends PageParam {

    @Schema(description = "功能名称")
    private String name;

    @Schema(description = "所属产品ID")
    private Long productId;

    @Schema(description = "状态")
    private Integer status;
}
