package cn.iocoder.yudao.module.org.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** org 模块错误码，占用 1-022-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 机构信息 1-022-001-xxx ==========
    ErrorCode ORG_NOT_EXISTS = new ErrorCode(1_022_001_000, "机构不存在");

    // ========== 机构账号 1-022-002-xxx ==========
    ErrorCode ORG_ACCOUNT_NOT_EXISTS = new ErrorCode(1_022_002_000, "机构账号不存在");

    // ========== 机构产品 1-022-003-xxx ==========
    ErrorCode ORG_PRODUCT_NOT_EXISTS = new ErrorCode(1_022_003_000, "机构产品不存在");
    ErrorCode ORG_PRODUCT_DUPLICATE = new ErrorCode(1_022_003_001, "该机构已开通此产品");
}
