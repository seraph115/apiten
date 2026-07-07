package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 数据源接口参数创建/修改 Request VO")
@Data
public class DsInterfaceParamSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "所属接口ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属接口不能为空")
    private Long dsInterfaceId;

    @Schema(description = "方向：1入参 2出参", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "方向不能为空")
    private Integer paramDirection;

    @Schema(description = "平台标准字段", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "平台标准字段不能为空")
    private String platformField;

    @Schema(description = "供应商字段")
    private String providerField;

    @Schema(description = "数据类型：1字符串 2数字 3布尔 4日期 5对象 6数组", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据类型不能为空")
    private Integer dataType;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "转换函数名")
    private String transformFn;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "出参JSONPath抽取路径")
    private String jsonPath;

    @Schema(description = "备注")
    private String remark;
}
