package cn.iocoder.yudao.module.adapter.engine;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransformFunctions {

    private TransformFunctions() {}

    public static String apply(String fnSpec, String value) {
        if (StrUtil.isBlank(fnSpec)) {
            return value;
        }
        String name;
        String arg = null;
        int idx = fnSpec.indexOf(':');
        if (idx >= 0) {
            name = fnSpec.substring(0, idx).trim();
            arg = fnSpec.substring(idx + 1);
        } else {
            name = fnSpec.trim();
        }
        String v = value == null ? "" : value;
        switch (name.toUpperCase()) {
            case "MD5":      return SecureUtil.md5(v);
            case "SHA256":   return SecureUtil.sha256(v);
            case "UPPER":    return v.toUpperCase();
            case "LOWER":    return v.toLowerCase();
            case "TRIM":     return v.trim();
            case "DEFAULT":  return StrUtil.isEmpty(value) ? (arg == null ? "" : arg) : value;
            case "SUBSTR":   return substr(v, arg);
            case "DATE_FMT": return DateUtil.format(DateUtil.parse(v), arg);
            default:
                log.warn("[transform] 未知转换函数 {}，原样透传", name);
                return value;
        }
    }

    private static String substr(String v, String arg) {
        String[] parts = arg.split(",");
        int start = Integer.parseInt(parts[0].trim());
        int len = Integer.parseInt(parts[1].trim());
        if (start >= v.length()) {
            return "";
        }
        int end = Math.min(start + len, v.length());
        return v.substring(start, end);
    }
}
