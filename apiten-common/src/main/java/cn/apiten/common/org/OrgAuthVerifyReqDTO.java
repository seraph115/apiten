package cn.apiten.common.org;

public class OrgAuthVerifyReqDTO {
    private String appKey;
    private String timestamp;
    private String nonce;
    private String signature;
    private String bodyDigest;
    private String productCode;
    private String clientIp;
    private String flowNo;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getBodyDigest() { return bodyDigest; }
    public void setBodyDigest(String bodyDigest) { this.bodyDigest = bodyDigest; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getFlowNo() { return flowNo; }
    public void setFlowNo(String flowNo) { this.flowNo = flowNo; }
}
