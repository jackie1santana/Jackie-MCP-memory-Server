package org.example.chatgptmcpserver.service;

import org.example.chatgptmcpserver.domain.Memory;
import org.example.chatgptmcpserver.dto.request.RememberMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SearchMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SetMemoryScopeRequest;
import org.example.chatgptmcpserver.dto.response.MemoryResponse;
import org.example.chatgptmcpserver.repository.MemoryRepository;
import org.example.chatgptmcpserver.repository.MemorySettingsRepository;
import org.example.chatgptmcpserver.repository.MemoryTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryServiceImplTest {

    private static final String TEST_SCOPE_CONTEXT = "unit-chat-1";

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private MemoryTagRepository memoryTagRepository;

    @Mock
    private MemorySettingsRepository memorySettingsRepository;

    @InjectMocks
    private MemoryServiceImpl memoryService;

    @Test
    void searchMemoriesReturnsEmptyWhenActiveScopeIsOffAndNoExplicitScope() {
        when(memorySettingsRepository.getActiveScope(TEST_SCOPE_CONTEXT)).thenReturn("off");

        List<MemoryResponse> result = memoryService.searchMemories(
                new SearchMemoryRequest("fidelity", null, TEST_SCOPE_CONTEXT, null, null)
        );

        assertThat(result).isEmpty();
        verify(memoryRepository, never()).search(any(), any(), any(), any(Integer.class));
    }

    @Test
    void searchMemoriesUsesExplicitScopeWhenActiveScopeIsOff() {
        when(memorySettingsRepository.getActiveScope(TEST_SCOPE_CONTEXT)).thenReturn("off");
        when(memoryTagRepository.findTagNamesByMemoryId(any())).thenReturn(List.of("tag-a"));
        Memory memory = sampleMemory("fidelity");
        when(memoryRepository.search(anyString(), anyString(), isNull(), anyInt()))
                .thenReturn(List.of(memory));

        List<MemoryResponse> result = memoryService.searchMemories(
                new SearchMemoryRequest("fidelity", "fidelity", TEST_SCOPE_CONTEXT, null, null)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scope()).isEqualTo("fidelity");
    }

    @Test
    void saveMemoryPersistsAndMapsTags() {
        when(memorySettingsRepository.getActiveScope(TEST_SCOPE_CONTEXT)).thenReturn("fidelity");
        when(memoryTagRepository.findTagNamesByMemoryId(any())).thenReturn(List.of("tag-a"));
        RememberMemoryRequest request = new RememberMemoryRequest(
                "fidelity",
                null,
                TEST_SCOPE_CONTEXT,
                "Fidelity deployment note",
                "Store this Fidelity deployment note",
                "fact",
                8,
                Instant.now(),
                null,
                null,
                "user",
                Map.of("channel", "chat"),
                List.of("agreement")
        );

        when(memoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MemoryResponse response = memoryService.saveMemory(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.scope()).isEqualTo("fidelity");
        assertThat(response.tags()).contains("tag-a");
    }

    @Test
    void getMemoryChecksScopeAccess() {
        UUID id = UUID.randomUUID();
        when(memorySettingsRepository.getActiveScope(TEST_SCOPE_CONTEXT)).thenReturn("all");
        when(memoryTagRepository.findTagNamesByMemoryId(any())).thenReturn(List.of("tag-a"));
        when(memoryRepository.findById(id)).thenReturn(Optional.of(sampleMemory("custody", id)));

        MemoryResponse response = memoryService.getMemory(id, null, TEST_SCOPE_CONTEXT);

        assertThat(response.scope()).isEqualTo("custody");
    }

    @Test
    void setAndGetScopeRoundtrip() {
        when(memorySettingsRepository.getActiveScope(TEST_SCOPE_CONTEXT)).thenReturn("custody");

        String setScope = memoryService.setActiveMemoryScope(new SetMemoryScopeRequest("custody", TEST_SCOPE_CONTEXT));
        String getScope = memoryService.getActiveMemoryScope(TEST_SCOPE_CONTEXT);

        assertThat(setScope).isEqualTo("custody");
        assertThat(getScope).isEqualTo("custody");
        verify(memorySettingsRepository).setActiveScope("custody", TEST_SCOPE_CONTEXT);
    }

    private Memory sampleMemory(String scope) {
        return sampleMemory(scope, UUID.randomUUID());
    }

    private Memory sampleMemory(String scope, UUID id) {
        Instant now = Instant.now();
        return new Memory(
                id,
                "fidelity",
                scope,
                "title",
                "content",
                "fact",
                7,
                now,
                null,
                null,
                "user",
                Map.of(),
                true,
                now,
                now,
                List.of()
        );
    }
}

