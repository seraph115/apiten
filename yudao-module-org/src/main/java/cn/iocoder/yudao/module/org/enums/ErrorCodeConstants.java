package cn.iocoder.yudao.module.org.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** org 模块错误码，占用 1-022-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 机构信息 1-022-001-xxx ==========
    ErrorCode ORG_NOT_EXISTS = new ErrorCode(1_022_001_000, "机构不存在");
}
