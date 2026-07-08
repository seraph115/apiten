package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void substitutesPlaceholders() {
        Map<String, Object> vars = Map.of("name", "某某公司", "id", 42);
        assertThat(TemplateRenderer.render("{\"n\":\"${name}\",\"i\":${id}}", vars))
                .isEqualTo("{\"n\":\"某某公司\",\"i\":42}");
    }

    @Test
    void nullValue_becomesEmpty() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", null);
        assertThat(TemplateRenderer.render("a=${x}", vars)).isEqualTo("a=");
    }

    @Test
    void unmatchedPlaceholder_keptAsIs() {
        assertThat(TemplateRenderer.render("a=${missing}", Map.of())).isEqualTo("a=${missing}");
    }

    @Test
    void nullTemplate_returnsNull() {
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }
}
