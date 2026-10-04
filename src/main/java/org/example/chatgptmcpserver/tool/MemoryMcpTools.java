package org.example.chatgptmcpserver.tool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.chatgptmcpserver.dto.request.GetMemoryTimelineRequest;
import org.example.chatgptmcpserver.dto.request.RememberMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SearchMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SetMemoryScopeRequest;
import org.example.chatgptmcpserver.dto.request.UpdateMemoryRequest;
import org.example.chatgptmcpserver.dto.response.CategoryListResponse;
import org.example.chatgptmcpserver.dto.response.MemoryResponse;
import org.example.chatgptmcpserver.dto.response.MemoryScopeResponse;
import org.example.chatgptmcpserver.dto.response.OperationResultResponse;
import org.example.chatgptmcpserver.dto.response.SearchMemoryResponse;
import org.example.chatgptmcpserver.exception.MemoryValidationException;
import org.example.chatgptmcpserver.service.MemoryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Component
@Validated
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryMcpTools {

    private final MemoryService memoryService;

    public MemoryMcpTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Tool(description = "Write action. Use when the user explicitly asks to remember, save, store, record, or keep information for future sessions. If request.scope is omitted, active scope is used for request.scopeContext.")
    public MemoryResponse remember_memory(@Valid @NotNull RememberMemoryRequest request) {
        return memoryService.saveMemory(request);
    }

    @Tool(description = "Retrieval action. Use when prior stored personal context would materially improve the current answer. Respects active memory scope unless request.scope is provided.")
    public SearchMemoryResponse search_memory(@Valid @NotNull SearchMemoryRequest request) {
        String activeScope = memoryService.getActiveMemoryScope(request.scopeContext());
        List<MemoryResponse> memories = memoryService.searchMemories(request);
        return new SearchMemoryResponse(memories, memories.size(), activeScope);
    }

    @Tool(description = "Get a specific memory by id. Returns a not found error if the memory is outside the active scope and no explicit scope is provided.")
    public MemoryResponse get_memory(
            @NotBlank String memoryId,
            String scope,
            String scopeContext
    ) {
        UUID id = parseUuid(memoryId);
        return memoryService.getMemory(id, scope, scopeContext);
    }

    @Tool(description = "Update an existing memory by id. Use for correcting details, changing dates, scope, tags, or metadata.")
    public MemoryResponse update_memory(@Valid @NotNull UpdateMemoryRequest request) {
        return memoryService.updateMemory(request);
    }

    @Tool(description = "Soft delete action. Marks a memory inactive so it is no longer used in contextual retrieval.")
    public OperationResultResponse forget_memory(@NotBlank String memoryId) {
        UUID id = parseUuid(memoryId);
        boolean success = memoryService.deactivateMemory(id);
        return new OperationResultResponse(success, success ? "Memory deactivated" : "No change");
    }

    @Tool(description = "Set active memory scope (for example: fidelity, custody, personal, all, off). Controls default contextual retrieval behavior.")
    public MemoryScopeResponse set_memory_scope(@Valid @NotNull SetMemoryScopeRequest request) {
        return new MemoryScopeResponse(memoryService.setActiveMemoryScope(request));
    }

    @Tool(description = "Read current active memory scope.")
    public MemoryScopeResponse get_memory_scope(String scopeContext) {
        return new MemoryScopeResponse(memoryService.getActiveMemoryScope(scopeContext));
    }

    @Tool(description = "List available memory categories for organizing and filtering memories.")
    public CategoryListResponse list_memory_categories() {
        return new CategoryListResponse(memoryService.getCategories());
    }

    @Tool(description = "Get timeline memories that occurred between two dates. Respects active scope unless request.scope is explicitly provided.")
    public SearchMemoryResponse get_memory_timeline(@Valid @NotNull GetMemoryTimelineRequest request) {
        List<MemoryResponse> timeline = memoryService.getTimelineMemories(request);
        return new SearchMemoryResponse(timeline, timeline.size(), memoryService.getActiveMemoryScope(request.scopeContext()));
    }

    private UUID parseUuid(String memoryId) {
        try {
            return UUID.fromString(memoryId);
        } catch (IllegalArgumentException ex) {
            throw new MemoryValidationException("Invalid memoryId format: " + memoryId);
        }
    }
}

