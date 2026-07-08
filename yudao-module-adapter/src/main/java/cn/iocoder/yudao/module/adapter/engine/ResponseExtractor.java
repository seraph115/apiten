package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.adapter.dal.dataobject.datasource.DsInterfaceParamDO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseExtractor {

    private ResponseExtractor() {}

    public static Map<String, Object> extract(String rawJson, List<DsInterfaceParamDO> params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null) {
            return result;
        }
        JSON json;
        try {
            json = JSONUtil.parse(rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("上游响应非法 JSON: " + e.getMessage(), e);
        }
        for (DsInterfaceParamDO p : params) {
            if (p.getParamDirection() == null || p.getParamDirection() != 2) {
                continue; // 只处理出参
            }
            String path = normalizePath(p.getJsonPath());
            Object value = StrUtil.isBlank(path) ? null : json.getByPath(path);
            result.put(p.getPlatformField(), value);
        }
        return result;
    }

    /**
     * Hutool {@code getByPath}（{@code BeanPath}）使用点/方括号语法，不带 JSONPath 的 {@code $.} 前缀；
     * 去掉前导 {@code $.} 或 {@code $} 即可交给 Hutool 解析。
     *
     * 经验证：Hutool 5.8.46 的 {@code BeanPath} 原生支持形如 {@code list[0].nm} 的方括号数组下标语法
     * （无需转换为 {@code list.0.nm}），故此处未做额外的 {@code [n]} → {@code .n} 归一化。
     */
    private static String normalizePath(String jsonPath) {
        if (StrUtil.isBlank(jsonPath)) {
            return jsonPath;
        }
        String p = jsonPath.trim();
        if (p.startsWith("$.")) {
            return p.substring(2);
        }
        if (p.startsWith("$")) {
            return p.substring(1);
        }
        return p;
    }
}
