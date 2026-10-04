package org.example.chatgptmcpserver.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class JdbcMemoryTagRepository implements MemoryTagRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMemoryTagRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replaceTags(UUID memoryId, List<String> tags) {
        String deleteSql = "DELETE FROM memory_tag_map WHERE memory_id = :memoryId";
        jdbcTemplate.update(deleteSql, Map.of("memoryId", memoryId));

        if (tags == null || tags.isEmpty()) {
            return;
        }

        String upsertTagSql = """
                INSERT INTO memory_tag (name)
                VALUES (:name)
                ON CONFLICT (name) DO NOTHING
                """;

        String mapSql = """
                INSERT INTO memory_tag_map (memory_id, tag_id)
                VALUES (:memoryId, (SELECT id FROM memory_tag WHERE name = :name))
                ON CONFLICT (memory_id, tag_id) DO NOTHING
                """;

        for (String rawTag : tags) {
            if (rawTag == null || rawTag.isBlank()) {
                continue;
            }
            String normalizedTag = rawTag.trim().toLowerCase();
            jdbcTemplate.update(upsertTagSql, Collections.singletonMap("name", normalizedTag));

            MapSqlParameterSource mapParams = new MapSqlParameterSource()
                    .addValue("memoryId", memoryId)
                    .addValue("name", normalizedTag);
            jdbcTemplate.update(mapSql, mapParams);
        }
    }

    @Override
    public List<String> findTagNamesByMemoryId(UUID memoryId) {
        String sql = """
                SELECT t.name
                FROM memory_tag_map tm
                JOIN memory_tag t ON t.id = tm.tag_id
                WHERE tm.memory_id = :memoryId
                ORDER BY t.name ASC
                """;

        return jdbcTemplate.queryForList(sql, Map.of("memoryId", memoryId), String.class);
    }
}

