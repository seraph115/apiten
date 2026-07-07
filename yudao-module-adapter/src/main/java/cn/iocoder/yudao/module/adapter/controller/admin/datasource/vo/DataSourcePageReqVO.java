package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourcePageReqVO extends PageParam {

    @Schema(description = "数据源名称")
    private String name;

    @Schema(description = "类型")
    private Integer sourceType;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "接入协议")
    private Integer protocolType;
}
