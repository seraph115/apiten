package cn.apiten.common.adapter;

import java.util.Map;

public class ProviderRequest {

    private String productCode;
    private Map<String, Object> params;
    private Long dsInterfaceId; // 显式接口调用（HTTP 引擎）；为空走 productCode 兜底(MOCK)

    public ProviderRequest() {
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Long getDsInterfaceId() {
        return dsInterfaceId;
    }

    public void setDsInterfaceId(Long dsInterfaceId) {
        this.dsInterfaceId = dsInterfaceId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
