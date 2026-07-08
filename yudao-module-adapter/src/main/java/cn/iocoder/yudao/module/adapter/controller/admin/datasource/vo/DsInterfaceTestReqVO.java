package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Schema(description = "管理后台 - 数据源接口联调测试台 Request VO")
@Data
public class DsInterfaceTestReqVO {

    @Schema(description = "平台参数（平台字段名 -> 值）")
    private Map<String, Object> params;
}
