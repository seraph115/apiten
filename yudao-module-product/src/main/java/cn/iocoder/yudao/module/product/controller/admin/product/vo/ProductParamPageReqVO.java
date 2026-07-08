package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品参数分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductParamPageReqVO extends PageParam {

    @Schema(description = "所属产品ID")
    private Long productId;

    @Schema(description = "方向：1入参 2出参")
    private Integer paramDirection;

    @Schema(description = "字段名")
    private String fieldName;
}
