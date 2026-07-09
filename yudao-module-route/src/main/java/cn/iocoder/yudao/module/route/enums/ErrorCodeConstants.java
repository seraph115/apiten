package cn.iocoder.yudao.module.route.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** route 模块错误码，占用 1-023-xxx-xxx 段 */
public interface ErrorCodeConstants {
    // ========== 路由配置 1-023-001-xxx ==========
    ErrorCode ROUTE_CONFIG_NOT_EXISTS = new ErrorCode(1_023_001_000, "路由配置不存在");
}
