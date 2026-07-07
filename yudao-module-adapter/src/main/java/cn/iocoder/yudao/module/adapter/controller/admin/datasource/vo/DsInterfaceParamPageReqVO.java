package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源接口参数分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DsInterfaceParamPageReqVO extends PageParam {

    @Schema(description = "所属接口ID")
    private Long dsInterfaceId;

    @Schema(description = "方向：1入参 2出参")
    private Integer paramDirection;

    @Schema(description = "平台标准字段")
    private String platformField;
}
