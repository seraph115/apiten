package cn.iocoder.yudao.module.adapter.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/** adapter 模块错误码，占用 1-020-xxx-xxx 段 */
public interface ErrorCodeConstants {

    // ========== 数据源 1-020-001-xxx ==========
    ErrorCode DATA_SOURCE_NOT_EXISTS = new ErrorCode(1_020_001_000, "数据源不存在");
    ErrorCode DATA_SOURCE_CODE_DUPLICATE = new ErrorCode(1_020_001_001, "数据源编码已存在");

    // ========== 数据源接口 1-020-002-xxx ==========
    ErrorCode DS_INTERFACE_NOT_EXISTS = new ErrorCode(1_020_002_000, "数据源接口不存在");

    // ========== 数据源应答码 1-020-003-xxx ==========
    ErrorCode DS_RESPONSE_CODE_NOT_EXISTS = new ErrorCode(1_020_003_000, "数据源应答码不存在");
    ErrorCode DS_RESPONSE_CODE_DUPLICATE = new ErrorCode(1_020_003_001, "同接口下原始应答码已存在");

    // ========== 数据源接口参数 1-020-004-xxx ==========
    ErrorCode DS_INTERFACE_PARAM_NOT_EXISTS = new ErrorCode(1_020_004_000, "数据源接口参数不存在");

    // ========== 适配引擎 1-020-005-xxx ==========
    ErrorCode ADAPTER_ENGINE_INVOKE_FAILED = new ErrorCode(1_020_005_000, "适配调用失败");
}
