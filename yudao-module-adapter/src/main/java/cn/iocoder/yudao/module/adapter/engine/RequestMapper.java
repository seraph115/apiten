package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestMapper {

    private RequestMapper() {}

    public static Map<String, Object> mapInParams(Map<String, Object> platformParams,
            List<DsInterfaceParamDO> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) {
            return result;
        }
        Map<String, Object> src = platformParams == null ? Map.of() : platformParams;
        for (DsInterfaceParamDO p : params) {
            if (p.getParamDirection() == null || p.getParamDirection() != 1) {
                continue; // 只处理入参
            }
            Object raw = src.get(p.getPlatformField());
            String value = raw == null ? null : String.valueOf(raw);
            if (StrUtil.isEmpty(value) && StrUtil.isNotEmpty(p.getDefaultValue())) {
                value = p.getDefaultValue();
            }
            String transformed = TransformFunctions.apply(p.getTransformFn(), value);
            String targetKey = StrUtil.isNotBlank(p.getProviderField())
                    ? p.getProviderField() : p.getPlatformField();
            result.put(targetKey, transformed);
        }
        return result;
    }
}
