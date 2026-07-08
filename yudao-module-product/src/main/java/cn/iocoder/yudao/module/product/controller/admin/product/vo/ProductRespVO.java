package cn.iocoder.yudao.module.product.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 产品 Response VO")
@Data
public class ProductRespVO {
    private Long id;
    private String productCode;
    private String name;
    private Integer productType;
    private Integer authType;
    private Integer status;
    private String version;
    private String description;
    private Boolean cacheEnabled;
    private Boolean asyncSupport;
    private Boolean needAuthNo;
    private String remark;
    private LocalDateTime createTime;
}
