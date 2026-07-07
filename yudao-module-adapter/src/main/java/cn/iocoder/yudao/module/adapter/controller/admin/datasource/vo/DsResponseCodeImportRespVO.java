package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据源应答码导入 Response VO")
@Data
@Builder
public class DsResponseCodeImportRespVO {

    @Schema(description = "创建成功的原始应答码数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> createRawCodes;

    @Schema(description = "更新成功的原始应答码数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> updateRawCodes;

    @Schema(description = "导入失败的原始应答码集合，key 为原始应答码，value 为失败原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, String> failureRawCodes;

}
