package cn.iocoder.yudao.module.product.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** product 模块错误码，占用 1-021-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 产品信息 1-021-001-xxx ==========
    ErrorCode PRODUCT_NOT_EXISTS = new ErrorCode(1_021_001_000, "产品不存在");

    // ========== 产品功能 1-021-002-xxx ==========
    ErrorCode PRODUCT_FUNCTION_NOT_EXISTS = new ErrorCode(1_021_002_000, "产品功能不存在");
}
