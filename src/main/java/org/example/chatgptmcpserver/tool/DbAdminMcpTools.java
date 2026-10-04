package org.example.chatgptmcpserver.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.example.chatgptmcpserver.dto.response.DbInfoResponse;
import org.example.chatgptmcpserver.dto.response.DbQueryResultResponse;
import org.example.chatgptmcpserver.dto.response.DbTableColumnsResponse;
import org.example.chatgptmcpserver.dto.response.OperationResultResponse;
import org.example.chatgptmcpserver.exception.MemoryValidationException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Validated
@ConditionalOnProperty(name = "app.mcp.db-admin.enabled", havingValue = "true")
public class DbAdminMcpTools {

    private static final Pattern READ_QUERY_START = Pattern.compile("^(?is)\\s*(select|with|show|explain)\\b");
    private static final Pattern CREATE_TABLE_START = Pattern.compile("^(?is)\\s*create\\s+table\\b");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Environment environment;

    public DbAdminMcpTools(NamedParameterJdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Tool(description = "DB admin read action. List all non-template PostgreSQL databases visible to this connection.")
    public List<String> db_list_databases() {
        String sql = """
                SELECT datname
                FROM pg_database
                WHERE datistemplate = FALSE
                ORDER BY datname ASC
                """;
        return jdbcTemplate.queryForList(sql, Map.of(), String.class);
    }

    @Tool(description = "DB admin read action. Return database server and session information such as current database, user, and PostgreSQL version.")
    public DbInfoResponse db_get_info() {
        String sql = """
                SELECT current_database() AS current_database,
                       current_user AS current_user,
                       version() AS server_version,
                       inet_server_addr()::text AS server_address,
                       inet_server_port() AS server_port
                """;
        Map<String, Object> details = jdbcTemplate.queryForMap(sql, Map.of());
        return new DbInfoResponse(details);
    }

    @Tool(description = "DB admin read action. List tables from all schemas (or one schema if schema is provided).")
    public DbQueryResultResponse db_list_tables(String schema) {
        String baseSql = """
                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_type = 'BASE TABLE'
                """;

        String sql;
        Map<String, Object> params;
        if (schema != null && !schema.isBlank()) {
            sql = baseSql + " AND table_schema = :schema ORDER BY table_schema, table_name";
            params = Map.of("schema", schema.trim());
        } else {
            sql = baseSql + " AND table_schema NOT IN ('pg_catalog', 'information_schema') ORDER BY table_schema, table_name";
            params = Map.of();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        return new DbQueryResultResponse(rows, rows.size(), false);
    }

    @Tool(description = "DB admin read action. Describe table columns, data types, and nullability.")
    public DbTableColumnsResponse db_describe_table(@NotBlank String schema, @NotBlank String table) {
        String sql = """
                SELECT ordinal_position,
                       column_name,
                       data_type,
                       is_nullable,
                       column_default
                FROM information_schema.columns
                WHERE table_schema = :schema
                  AND table_name = :table
                ORDER BY ordinal_position ASC
                """;

        Map<String, Object> params = Map.of(
                "schema", schema.trim(),
                "table", table.trim()
        );

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, params);
        return new DbTableColumnsResponse(schema.trim(), table.trim(), columns);
    }

    @Tool(description = "DB admin read action. Run a SELECT/WITH/SHOW/EXPLAIN query for inspection. Use limit to constrain results.")
    public DbQueryResultResponse db_read_query(
            @NotBlank String sql,
            @Min(1) @Max(2000) Integer limit
    ) {
        String normalizedSql = sql.trim();
        if (!READ_QUERY_START.matcher(normalizedSql).find()) {
            throw new MemoryValidationException("Only read queries are allowed in db_read_query");
        }

        int requested = limit == null ? defaultQueryLimit() : limit;
        int max = maxQueryLimit();
        int effectiveLimit = Math.min(requested, max);

        String limitedSql = "SELECT * FROM (" + normalizedSql + ") AS q LIMIT " + effectiveLimit;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(limitedSql, Map.of());
        return new DbQueryResultResponse(rows, rows.size(), rows.size() >= effectiveLimit);
    }

    @Tool(description = "DB admin write action. Create a new table using CREATE TABLE SQL. Requires app.mcp.db-admin.allow-ddl=true.")
    public OperationResultResponse db_create_table(@NotBlank String createTableSql) {
        boolean allowDdl = environment.getProperty("app.mcp.db-admin.allow-ddl", Boolean.class, false);
        if (!allowDdl) {
            throw new MemoryValidationException("DDL is disabled. Set app.mcp.db-admin.allow-ddl=true to allow table creation.");
        }

        String normalizedSql = createTableSql.trim();
        if (!CREATE_TABLE_START.matcher(normalizedSql).find()) {
            throw new MemoryValidationException("Only CREATE TABLE statements are allowed in db_create_table");
        }
        if (normalizedSql.contains(";")) {
            throw new MemoryValidationException("Only a single CREATE TABLE statement is allowed");
        }

        jdbcTemplate.update(normalizedSql, Map.of());
        return new OperationResultResponse(true, "Table created");
    }

    private int defaultQueryLimit() {
        return environment.getProperty("app.mcp.db-admin.default-query-limit", Integer.class, 200);
    }

    private int maxQueryLimit() {
        return environment.getProperty("app.mcp.db-admin.max-query-limit", Integer.class, 1000);
    }
}


