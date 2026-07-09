package cn.iocoder.yudao.module.route.dal.dataobject.route;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("route_config")
@KeySequence("route_config_seq")
@Data
@TenantIgnore
public class RouteConfigDO extends BaseDO {
    private Long id;
    private String routeCode;
    private String name;
    private String productCode;
    private Long orgId;
    private String targetType;
    private Long targetDsInterfaceId;
    private Integer priority;
    private Integer status;
    private String remark;
}
