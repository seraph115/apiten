package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源应答码分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DsResponseCodePageReqVO extends PageParam {

    @Schema(description = "所属数据源ID")
    private Long dataSourceId;

    @Schema(description = "所属接口ID")
    private Long dsInterfaceId;

    @Schema(description = "原始应答码")
    private String rawCode;

    @Schema(description = "映射平台统一码")
    private String platformCode;
}
