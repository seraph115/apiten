package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据源接口分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class DsInterfacePageReqVO extends PageParam {

    @Schema(description = "接口名称")
    private String name;

    @Schema(description = "所属数据源ID")
    private Long dataSourceId;

    @Schema(description = "状态")
    private Integer status;
}
