package org.example.chatgptmcpserver.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class JdbcMemorySettingsRepository implements MemorySettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemorySettingsRepository.class);
    private static final String ACTIVE_SCOPE_KEY_PREFIX = "active_scope::";
    private static final String DEFAULT_CONTEXT = "default";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMemorySettingsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void setActiveScope(String scope, String scopeContext) {
        String sql = """
                INSERT INTO memory_settings (setting_key, setting_value, updated_at)
                VALUES (:key, :value, :updatedAt)
                ON CONFLICT (setting_key)
                DO UPDATE SET setting_value = EXCLUDED.setting_value,
                              updated_at = EXCLUDED.updated_at
                """;

        String key = activeScopeKey(scopeContext);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", scope)
                .addValue("updatedAt", Timestamp.from(Instant.now()));

        jdbcTemplate.update(sql, params);
        log.info("event=active_scope_set scope={} context={}", scope, normalizeContext(scopeContext));
    }

    @Override
    public String getActiveScope(String scopeContext) {
        String sql = """
                SELECT setting_value
                FROM memory_settings
                WHERE setting_key = :key
                """;

        return jdbcTemplate.query(sql, Map.of("key", activeScopeKey(scopeContext)), rs -> {
            if (rs.next()) {
                return rs.getString("setting_value");
            }
            return "off";
        });
    }

    private String activeScopeKey(String scopeContext) {
        return ACTIVE_SCOPE_KEY_PREFIX + normalizeContext(scopeContext);
    }

    private String normalizeContext(String scopeContext) {
        if (scopeContext == null || scopeContext.isBlank()) {
            return DEFAULT_CONTEXT;
        }
        String normalized = scopeContext.trim().toLowerCase();
        return normalized.replaceAll("[^a-z0-9._:-]", "_");
    }
}

