package cn.iocoder.yudao.module.adapter.controller.admin.datasource.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 数据源应答码 Excel 导入 VO
 */
@Data
public class DsResponseCodeImportExcelVO {

    @ExcelProperty("所属数据源ID")
    private Long dataSourceId;

    @ExcelProperty("所属接口ID")
    private Long dsInterfaceId;

    @ExcelProperty("原始应答码")
    private String rawCode;

    @ExcelProperty("原始描述")
    private String rawDesc;

    @ExcelProperty("是否成功")
    private Boolean success;

    @ExcelProperty("是否计费")
    private Boolean charge;

    @ExcelProperty("是否可重试")
    private Boolean retryable;

    @ExcelProperty("是否触发切换")
    private Boolean triggerSwitch;

    @ExcelProperty("映射平台统一码")
    private String platformCode;

}
