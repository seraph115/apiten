package cn.apiten.common.org;

import cn.apiten.common.api.PlatformErrorCode;

public class OrgAuthVerifyRespDTO {
    private boolean pass;
    private String platformCode;
    private String msg;
    private Long orgId;
    private Long accountId;
    private String orgCode;

    public static OrgAuthVerifyRespDTO pass(Long orgId, Long accountId, String orgCode) {
        OrgAuthVerifyRespDTO r = new OrgAuthVerifyRespDTO();
        r.pass = true;
        r.platformCode = PlatformErrorCode.SUCCESS.getCode();
        r.msg = PlatformErrorCode.SUCCESS.getMsg();
        r.orgId = orgId;
        r.accountId = accountId;
        r.orgCode = orgCode;
        return r;
    }

    public static OrgAuthVerifyRespDTO fail(PlatformErrorCode ec) {
        OrgAuthVerifyRespDTO r = new OrgAuthVerifyRespDTO();
        r.pass = false;
        r.platformCode = ec.getCode();
        r.msg = ec.getMsg();
        return r;
    }

    public boolean isPass() { return pass; }
    public void setPass(boolean pass) { this.pass = pass; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
}
