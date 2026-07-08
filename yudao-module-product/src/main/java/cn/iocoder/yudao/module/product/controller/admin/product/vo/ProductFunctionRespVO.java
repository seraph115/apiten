package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 产品功能 Response VO")
@Data
public class ProductFunctionRespVO {
    private Long id;
    private String funcCode;
    private String name;
    private Long productId;
    private Integer sort;
    private Boolean required;
    private Boolean charge;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
