package cn.apiten.common.adapter;

import java.util.Map;

public class ProviderRequest {

    private String productCode;
    private Map<String, Object> params;

    public ProviderRequest() {
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
