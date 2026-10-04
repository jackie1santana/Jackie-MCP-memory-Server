package org.example.chatgptmcpserver.integration;

import org.example.chatgptmcpserver.dto.request.RememberMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SearchMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SetMemoryScopeRequest;
import org.example.chatgptmcpserver.dto.response.MemoryResponse;
import org.example.chatgptmcpserver.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MemoryScopeIntegrationTest {

    private static final String SCOPE_CONTEXT = "integration-chat-1";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db");
    }

    @Autowired
    private MemoryService memoryService;

    @Test
    void memoryScopeFlowWorksAsExpected() {
        memoryService.setActiveMemoryScope(new SetMemoryScopeRequest("fidelity", SCOPE_CONTEXT));

        MemoryResponse saved = memoryService.saveMemory(new RememberMemoryRequest(
                "fidelity",
                null,
                SCOPE_CONTEXT,
                "Fidelity production runbook",
                "Remember the Fidelity job rerun steps and on-call ownership",
                "fact",
                9,
                Instant.now(),
                null,
                null,
                "integration-test",
                Map.of("suite", "MemoryScopeIntegrationTest"),
                List.of("fidelity", "runbook")
        ));

        MemoryResponse fetched = memoryService.getMemory(saved.id(), null, SCOPE_CONTEXT);
        assertThat(fetched.id()).isEqualTo(saved.id());
        assertThat(fetched.scope()).isEqualTo("fidelity");

        memoryService.setActiveMemoryScope(new SetMemoryScopeRequest("custody", SCOPE_CONTEXT));
        List<MemoryResponse> custodyResults = memoryService.searchMemories(
                new SearchMemoryRequest("fidelity", null, SCOPE_CONTEXT, null, null)
        );
        assertThat(custodyResults).isEmpty();

        memoryService.setActiveMemoryScope(new SetMemoryScopeRequest("all", SCOPE_CONTEXT));
        List<MemoryResponse> allResults = memoryService.searchMemories(
                new SearchMemoryRequest("fidelity", null, SCOPE_CONTEXT, null, null)
        );
        assertThat(allResults).extracting(MemoryResponse::id).contains(saved.id());

        memoryService.setActiveMemoryScope(new SetMemoryScopeRequest("off", SCOPE_CONTEXT));
        List<MemoryResponse> offResults = memoryService.searchMemories(
                new SearchMemoryRequest("fidelity", null, SCOPE_CONTEXT, null, null)
        );
        assertThat(offResults).isEmpty();
    }
}

