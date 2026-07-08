package cn.iocoder.yudao.module.adapter.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, Object> vars) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            if (vars != null && vars.containsKey(key)) {
                Object val = vars.get(key);
                m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : String.valueOf(val)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0))); // 未匹配保留原样
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
