package com.bupt.charging.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DemoSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE user_account ADD COLUMN IF NOT EXISTS role VARCHAR(20)");
        jdbcTemplate.update("UPDATE user_account SET role = 'OWNER' WHERE role IS NULL");
    }
}
