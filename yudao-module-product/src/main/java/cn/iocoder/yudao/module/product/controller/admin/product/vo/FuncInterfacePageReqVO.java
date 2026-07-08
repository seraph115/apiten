package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 功能接口绑定分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FuncInterfacePageReqVO extends PageParam {

    @Schema(description = "所属产品功能ID")
    private Long productFunctionId;

    @Schema(description = "数据源接口ID")
    private Long dsInterfaceId;

    @Schema(description = "状态")
    private Integer status;
}
