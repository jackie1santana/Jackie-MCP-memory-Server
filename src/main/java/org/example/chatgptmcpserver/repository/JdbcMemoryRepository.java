package org.example.chatgptmcpserver.repository;

import org.example.chatgptmcpserver.domain.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class JdbcMemoryRepository implements MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Memory save(Memory memory) {
        String sql = """
                INSERT INTO memory (
                    id, category, scope, title, content, memory_type, importance,
                    occurred_at, valid_from, valid_until, source, metadata, active,
                    created_at, updated_at
                )
                VALUES (
                    :id, :category, :scope, :title, :content, :memoryType, :importance,
                    :occurredAt, :validFrom, :validUntil, :source, CAST(:metadata AS jsonb), :active,
                    :createdAt, :updatedAt
                )
                """;

        MapSqlParameterSource params = mapMemoryParams(memory);
        jdbcTemplate.update(sql, params);
        log.info("event=memory_saved id={} scope={} category={} active={}",
                memory.id(), memory.scope(), memory.category(), memory.active());
        return memory;
    }

    @Override
    public Optional<Memory> findById(UUID id) {
        String sql = """
                SELECT id, category, scope, title, content, memory_type, importance,
                       occurred_at, valid_from, valid_until, source, metadata, active,
                       created_at, updated_at
                FROM memory
                WHERE id = :id
                """;

        try {
            Memory memory = jdbcTemplate.queryForObject(sql, Map.of("id", id), memoryRowMapper());
            return Optional.ofNullable(memory);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Memory update(Memory memory) {
        String sql = """
                UPDATE memory
                SET category = :category,
                    scope = :scope,
                    title = :title,
                    content = :content,
                    memory_type = :memoryType,
                    importance = :importance,
                    occurred_at = :occurredAt,
                    valid_from = :validFrom,
                    valid_until = :validUntil,
                    source = :source,
                    metadata = CAST(:metadata AS jsonb),
                    active = :active,
                    updated_at = :updatedAt
                WHERE id = :id
                """;

        MapSqlParameterSource params = mapMemoryParams(memory);
        jdbcTemplate.update(sql, params);
        log.info("event=memory_updated id={} scope={} category={} active={}",
                memory.id(), memory.scope(), memory.category(), memory.active());
        return memory;
    }

    @Override
    public boolean deactivate(UUID id) {
        String sql = """
                UPDATE memory
                SET active = FALSE,
                    updated_at = :updatedAt
                WHERE id = :id
                """;

        int updated = jdbcTemplate.update(sql, Map.of("id", id, "updatedAt", Timestamp.from(Instant.now())));
        if (updated > 0) {
            log.info("event=memory_deactivated id={}", id);
        }
        return updated > 0;
    }

    @Override
    public List<Memory> search(String query, String scope, String category, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, category, scope, title, content, memory_type, importance,
                       occurred_at, valid_from, valid_until, source, metadata, active,
                       created_at, updated_at
                FROM memory
                WHERE active = TRUE
                  AND (valid_from IS NULL OR valid_from <= NOW())
                  AND (valid_until IS NULL OR valid_until >= NOW())
                """);

        Map<String, Object> params = new HashMap<>();
        if (scope != null) {
            sql.append(" AND scope = :scope");
            params.put("scope", scope);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = :category");
            params.put("category", category);
        }
        if (query != null && !query.isBlank()) {
            List<String> tokens = Arrays.stream(query.trim().split("\\s+"))
                    .filter(token -> !token.isBlank())
                    .limit(8)
                    .toList();

            if (!tokens.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < tokens.size(); i++) {
                    if (i > 0) {
                        sql.append(" AND ");
                    }
                    sql.append("""
                            (
                                title ILIKE :q%d
                                OR content ILIKE :q%d
                                OR source ILIKE :q%d
                                OR category ILIKE :q%d
                                OR memory_type ILIKE :q%d
                                OR EXISTS (
                                    SELECT 1
                                    FROM memory_tag_map mtm
                                    JOIN memory_tag mt ON mt.id = mtm.tag_id
                                    WHERE mtm.memory_id = memory.id
                                      AND mt.active = TRUE
                                      AND mt.name ILIKE :q%d
                                )
                            )
                            """.formatted(i, i, i, i, i, i));
                    params.put("q" + i, "%" + tokens.get(i) + "%");
                }
                sql.append(")");
            }
        }

        sql.append("""
                 ORDER BY importance DESC,
                          COALESCE(occurred_at, created_at) DESC,
                          created_at DESC
                 LIMIT :limit
                """);
        params.put("limit", limit);

        return jdbcTemplate.query(sql.toString(), params, memoryRowMapper());
    }

    @Override
    public List<Memory> findByScope(String scope, int limit) {
        String sql = """
                SELECT id, category, scope, title, content, memory_type, importance,
                       occurred_at, valid_from, valid_until, source, metadata, active,
                       created_at, updated_at
                FROM memory
                WHERE active = TRUE
                  AND scope = :scope
                  AND (valid_from IS NULL OR valid_from <= NOW())
                  AND (valid_until IS NULL OR valid_until >= NOW())
                ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC, created_at DESC
                LIMIT :limit
                """;

        return jdbcTemplate.query(sql, Map.of("scope", scope, "limit", limit), memoryRowMapper());
    }

    @Override
    public List<Memory> findByCategory(String category, String scope, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, category, scope, title, content, memory_type, importance,
                       occurred_at, valid_from, valid_until, source, metadata, active,
                       created_at, updated_at
                FROM memory
                WHERE active = TRUE
                  AND category = :category
                  AND (valid_from IS NULL OR valid_from <= NOW())
                  AND (valid_until IS NULL OR valid_until >= NOW())
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        if (scope != null) {
            sql.append(" AND scope = :scope");
            params.put("scope", scope);
        }
        sql.append(" ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC, created_at DESC LIMIT :limit");
        params.put("limit", limit);

        return jdbcTemplate.query(sql.toString(), params, memoryRowMapper());
    }

    @Override
    public List<Memory> findTimeline(Instant from, Instant to, String scope, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, category, scope, title, content, memory_type, importance,
                       occurred_at, valid_from, valid_until, source, metadata, active,
                       created_at, updated_at
                FROM memory
                WHERE active = TRUE
                  AND occurred_at IS NOT NULL
                  AND occurred_at BETWEEN :from AND :to
                  AND (valid_from IS NULL OR valid_from <= NOW())
                  AND (valid_until IS NULL OR valid_until >= NOW())
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("from", Timestamp.from(from));
        params.put("to", Timestamp.from(to));
        if (scope != null) {
            sql.append(" AND scope = :scope");
            params.put("scope", scope);
        }
        sql.append(" ORDER BY occurred_at ASC LIMIT :limit");
        params.put("limit", limit);

        return jdbcTemplate.query(sql.toString(), params, memoryRowMapper());
    }

    @Override
    public List<String> findCategories() {
        String sql = """
                SELECT name
                FROM memory_category
                WHERE active = TRUE
                ORDER BY name ASC
                """;

        return jdbcTemplate.queryForList(sql, Collections.emptyMap(), String.class);
    }

    private RowMapper<Memory> memoryRowMapper() {
        return (rs, rowNum) -> new Memory(
                UUID.fromString(rs.getString("id")),
                rs.getString("category"),
                rs.getString("scope"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("memory_type"),
                rs.getInt("importance"),
                toInstant(rs, "occurred_at"),
                toInstant(rs, "valid_from"),
                toInstant(rs, "valid_until"),
                rs.getString("source"),
                parseMetadata(rs.getString("metadata")),
                rs.getBoolean("active"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at"),
                List.of()
        );
    }

    private MapSqlParameterSource mapMemoryParams(Memory memory) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", memory.id());
        params.addValue("category", memory.category());
        params.addValue("scope", memory.scope());
        params.addValue("title", memory.title());
        params.addValue("content", memory.content());
        params.addValue("memoryType", memory.memoryType());
        params.addValue("importance", memory.importance());
        params.addValue("occurredAt", toTimestamp(memory.occurredAt()));
        params.addValue("validFrom", toTimestamp(memory.validFrom()));
        params.addValue("validUntil", toTimestamp(memory.validUntil()));
        params.addValue("source", memory.source());
        params.addValue("metadata", toJson(memory.metadata()));
        params.addValue("active", memory.active());
        params.addValue("createdAt", toTimestamp(memory.createdAt()));
        params.addValue("updatedAt", toTimestamp(memory.updatedAt()));
        return params;
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Cannot serialize metadata", ex);
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {
            });
        } catch (RuntimeException ex) {
            log.warn("event=metadata_parse_failed reason={}", ex.getMessage());
            return Map.of();
        }
    }
}

