package cn.apiten.common.api;

public enum PlatformErrorCode {
    SUCCESS("0000", "成功"),
    NO_DATA("0001", "查无数据"),
    SIGN_ERROR("1001", "签名错误"),
    PRODUCT_UNAUTHORIZED("1006", "产品未授权"),
    PARAM_MISSING("2001", "参数缺失"),
    BALANCE_INSUFFICIENT("2101", "余额不足"),
    UPSTREAM_ERROR("3001", "数据源异常"),
    CHAIN_EXHAUSTED("3003", "切换链耗尽"),
    SYSTEM_ERROR("3999", "系统异常");

    private final String code;
    private final String msg;

    PlatformErrorCode(String code, String msg) { this.code = code; this.msg = msg; }
    public String getCode() { return code; }
    public String getMsg() { return msg; }
}
