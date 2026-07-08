package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 产品参数 Response VO")
@Data
public class ProductParamRespVO {
    private Long id;
    private Long productId;
    private Integer paramDirection;
    private String fieldName;
    private Integer dataType;
    private Boolean required;
    private String validationRule;
    private String desensitizeRule;
    private String description;
    private Integer sort;
    private LocalDateTime createTime;
}
