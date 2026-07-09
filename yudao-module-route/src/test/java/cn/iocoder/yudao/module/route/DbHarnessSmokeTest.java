package cn.iocoder.yudao.module.route;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import jakarta.annotation.Resource;
import static org.assertj.core.api.Assertions.assertThat;

class DbHarnessSmokeTest extends BaseDbUnitTest {

    @Resource
    private DataSource dataSource;

    @Test
    void insertAndCount() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"route_db_harness_smoke\" (\"name\") VALUES (?)", "smoke");
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM \"route_db_harness_smoke\"", Integer.class);
        assertThat(cnt).isEqualTo(1);
    }
}
