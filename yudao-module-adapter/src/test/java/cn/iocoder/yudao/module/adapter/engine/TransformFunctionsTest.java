package cn.iocoder.yudao.module.adapter.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransformFunctionsTest {

    @Test
    void nullOrBlankSpec_returnsValueUnchanged() {
        assertThat(TransformFunctions.apply(null, "abc")).isEqualTo("abc");
        assertThat(TransformFunctions.apply("", "abc")).isEqualTo("abc");
    }

    @Test
    void md5_and_sha256() {
        assertThat(TransformFunctions.apply("MD5", "abc"))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        assertThat(TransformFunctions.apply("sha256", "abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void upper_lower_trim() {
        assertThat(TransformFunctions.apply("UPPER", "aB")).isEqualTo("AB");
        assertThat(TransformFunctions.apply("LOWER", "aB")).isEqualTo("ab");
        assertThat(TransformFunctions.apply("TRIM", "  x ")).isEqualTo("x");
    }

    @Test
    void default_whenBlank() {
        assertThat(TransformFunctions.apply("DEFAULT:N/A", "")).isEqualTo("N/A");
        assertThat(TransformFunctions.apply("DEFAULT:N/A", "v")).isEqualTo("v");
    }

    @Test
    void substr_startLen() {
        assertThat(TransformFunctions.apply("SUBSTR:0,6", "1234567890")).isEqualTo("123456");
        assertThat(TransformFunctions.apply("SUBSTR:6,4", "1234567890")).isEqualTo("7890");
    }

    @Test
    void dateFmt_normalizesToPattern() {
        assertThat(TransformFunctions.apply("DATE_FMT:yyyyMMdd", "2026-07-08")).isEqualTo("20260708");
    }

    @Test
    void unknownFn_returnsValueUnchanged() {
        assertThat(TransformFunctions.apply("NO_SUCH_FN", "v")).isEqualTo("v");
    }
}
