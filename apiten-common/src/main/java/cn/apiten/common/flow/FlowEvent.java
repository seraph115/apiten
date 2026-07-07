package cn.apiten.common.flow;

/**
 * 流水事件（P0 骨架）：openapi 侧组装响应后尽力而为地发送到 Kafka（topic apiten.org-flow），
 * flow-server 消费并暂存内存（P6 改为落库并引入幂等）。
 */
public class FlowEvent {

    private String flowNo;
    private String productCode;
    private String platformCode;
    private boolean charged;
    private long costTimeMs;
    private long requestTimeEpochMs;

    public FlowEvent() {
    }

    public String getFlowNo() {
        return flowNo;
    }

    public void setFlowNo(String flowNo) {
        this.flowNo = flowNo;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getPlatformCode() {
        return platformCode;
    }

    public void setPlatformCode(String platformCode) {
        this.platformCode = platformCode;
    }

    public boolean isCharged() {
        return charged;
    }

    public void setCharged(boolean charged) {
        this.charged = charged;
    }

    public long getCostTimeMs() {
        return costTimeMs;
    }

    public void setCostTimeMs(long costTimeMs) {
        this.costTimeMs = costTimeMs;
    }

    public long getRequestTimeEpochMs() {
        return requestTimeEpochMs;
    }

    public void setRequestTimeEpochMs(long requestTimeEpochMs) {
        this.requestTimeEpochMs = requestTimeEpochMs;
    }
}
