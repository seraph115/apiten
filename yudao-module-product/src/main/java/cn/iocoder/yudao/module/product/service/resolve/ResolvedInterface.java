package cn.iocoder.yudao.module.product.service.resolve;

import lombok.Data;

/**
 * 解析后的「产品功能 → 数据源接口」绑定结果
 */
@Data
public class ResolvedInterface {

    private Long productFunctionId;
    private String funcCode;
    private Long dsInterfaceId;
    private String dsInterfaceCode;
    private Integer priority;
    private boolean isDefault;

}
