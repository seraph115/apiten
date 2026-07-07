package cn.apiten.common.api;

public class ApiResponse<T> {
    private String flowNo;
    private String productCode;
    private String code;
    private String msg;
    private boolean charged;
    private long costTime;
    private T data;

    public static <T> ApiResponse<T> of(String flowNo, String productCode,
            PlatformErrorCode ec, boolean charged, long costTime, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.flowNo = flowNo;
        r.productCode = productCode;
        r.code = ec.getCode();
        r.msg = ec.getMsg();
        r.charged = charged;
        r.costTime = costTime;
        r.data = data;
        return r;
    }

    public String getFlowNo() { return flowNo; }
    public String getProductCode() { return productCode; }
    public String getCode() { return code; }
    public String getMsg() { return msg; }
    public boolean isCharged() { return charged; }
    public long getCostTime() { return costTime; }
    public T getData() { return data; }
}
