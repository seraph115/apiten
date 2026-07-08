package cn.iocoder.yudao.module.adapter.engine;

import cn.apiten.common.api.PlatformErrorCode;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsResponseCodeDO;
import cn.iocoder.yudao.module.adapter.dal.mysql.datasource.DsResponseCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

@Component
@Slf4j
public class ResponseCodeResolver {

    @Resource
    private DsResponseCodeMapper responseCodeMapper;

    public CodeResolution resolve(Long dataSourceId, Long dsInterfaceId, String rawCode) {
        DsResponseCodeDO hit = responseCodeMapper.selectByScopeAndRawCode(dataSourceId, dsInterfaceId, rawCode);
        if (hit == null && dsInterfaceId != null && dsInterfaceId != 0L) {
            hit = responseCodeMapper.selectByScopeAndRawCode(dataSourceId, 0L, rawCode); // 回落数据源级
        }
        CodeResolution r = new CodeResolution();
        if (hit == null || StrUtil.isBlank(hit.getPlatformCode())) {
            log.warn("[response-code] 未映射原始码 dsId={} ifId={} raw={}，归一为 {}",
                    dataSourceId, dsInterfaceId, rawCode, PlatformErrorCode.UPSTREAM_ERROR.getCode());
            r.setPlatformCode(PlatformErrorCode.UPSTREAM_ERROR.getCode());
            r.setMapped(false);
            return r;
        }
        r.setPlatformCode(hit.getPlatformCode());
        r.setSuccess(Boolean.TRUE.equals(hit.getSuccess()));
        r.setCharge(Boolean.TRUE.equals(hit.getCharge()));
        r.setRetryable(Boolean.TRUE.equals(hit.getRetryable()));
        r.setTriggerSwitch(Boolean.TRUE.equals(hit.getTriggerSwitch()));
        r.setMapped(true);
        return r;
    }
}
