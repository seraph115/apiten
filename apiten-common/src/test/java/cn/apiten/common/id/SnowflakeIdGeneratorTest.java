package cn.apiten.common.id;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void nextId_uniqueAndIncreasing() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        long prev = 0;
        for (int i = 0; i < 100_000; i++) {
            long id = g.nextId();
            assertThat(ids.add(id)).isTrue();
            assertThat(id).isGreaterThan(prev);
            prev = id;
        }
    }

    @Test
    void workerId_outOfRange_throws() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextIdStr_returnsDecimalString() {
        String s = new SnowflakeIdGenerator(2).nextIdStr();
        assertThat(s).matches("\\d{15,20}");
    }
}
