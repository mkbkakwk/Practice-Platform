package com.oj.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serializes transactions that can change the administrator set. PostgreSQL
 * transaction advisory locks are released automatically on commit or rollback.
 */
@Component
public class AdministratorLock {

    private static final long LOCK_KEY = 728491305021L;
    private final JdbcTemplate jdbcTemplate;

    public AdministratorLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire() {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + LOCK_KEY + ")");
    }
}
