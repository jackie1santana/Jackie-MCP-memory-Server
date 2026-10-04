package org.example.chatgptmcpserver.ops;

import org.example.chatgptmcpserver.tool.MemoryMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
public class LocalRuntimeStatusController {

    private final Environment environment;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<MemoryMcpTools> memoryMcpToolsProvider;

    public LocalRuntimeStatusController(
            Environment environment,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<MemoryMcpTools> memoryMcpToolsProvider
    ) {
        this.environment = environment;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.memoryMcpToolsProvider = memoryMcpToolsProvider;
    }

    @GetMapping("/internal/status")
    public RuntimeStatusResponse status() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean memoryEnabled = environment.getProperty("app.memory.enabled", Boolean.class, true);

        boolean postgresReachable = false;
        String databaseMessage = "PostgreSQL check skipped";

        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                Integer ping = jdbcTemplate.queryForObject("SELECT 1", Map.of(), Integer.class);
                postgresReachable = Integer.valueOf(1).equals(ping);
                databaseMessage = postgresReachable ? "PostgreSQL reachable" : "Unexpected ping result";
            } catch (RuntimeException ex) {
                databaseMessage = "PostgreSQL not reachable: " + ex.getClass().getSimpleName();
            }
        }

        List<String> toolNames = List.of();
        MemoryMcpTools toolBean = memoryMcpToolsProvider.getIfAvailable();
        if (toolBean != null) {
            toolNames = Arrays.stream(toolBean.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Tool.class))
                    .map(method -> method.getName().toLowerCase())
                    .sorted()
                    .toList();
        }

        return new RuntimeStatusResponse(
                true,
                memoryEnabled,
                postgresReachable,
                databaseMessage,
                !toolNames.isEmpty(),
                toolNames,
                profiles,
                "Local-first mode: remote MCP access only works while this PC is powered on, awake, online, and running PostgreSQL + Spring Boot + tunnel."
        );
    }

    public record RuntimeStatusResponse(
            boolean springBootRunning,
            boolean memoryEnabled,
            boolean postgresReachable,
            String databaseMessage,
            boolean mcpToolsRegistered,
            List<String> registeredMcpTools,
            List<String> activeProfiles,
            String note
    ) {
    }
}

