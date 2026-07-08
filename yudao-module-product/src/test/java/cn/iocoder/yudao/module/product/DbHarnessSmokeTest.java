package cn.iocoder.yudao.module.product;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.Resource;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

class DbHarnessSmokeTest extends BaseDbUnitTest {

    // BaseDbUnitTest 装配的是 DataSourceAutoConfiguration/DruidDataSourceAutoConfigure（DataSource Bean），
    // 但没有装配 Spring Boot 的 JdbcTemplateAutoConfiguration，所以容器里没有现成的 JdbcTemplate Bean。
    // 直接用注入的 DataSource 手工构造 JdbcTemplate，避免为了这一个冒烟测试去改动共享的 BaseDbUnitTest。
    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void h2Schema_isLoaded_andWritable() {
        jdbcTemplate.update("INSERT INTO \"product_db_harness_smoke\" (\"name\") VALUES (?)", "ok");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"product_db_harness_smoke\"", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
