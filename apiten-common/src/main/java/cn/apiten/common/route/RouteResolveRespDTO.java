package cn.apiten.common.route;

import cn.apiten.common.api.PlatformErrorCode;

public class RouteResolveRespDTO {
    private Long dsInterfaceId;
    private String source;
    private String platformCode;

    public static RouteResolveRespDTO of(Long dsInterfaceId, String source) {
        RouteResolveRespDTO r = new RouteResolveRespDTO();
        r.dsInterfaceId = dsInterfaceId;
        r.source = source;
        return r;
    }

    public static RouteResolveRespDTO noTarget() {
        RouteResolveRespDTO r = new RouteResolveRespDTO();
        r.source = "NONE";
        r.platformCode = PlatformErrorCode.ROUTE_NO_TARGET.getCode();
        return r;
    }

    public Long getDsInterfaceId() { return dsInterfaceId; }
    public void setDsInterfaceId(Long dsInterfaceId) { this.dsInterfaceId = dsInterfaceId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPlatformCode() { return platformCode; }
    public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }
}
