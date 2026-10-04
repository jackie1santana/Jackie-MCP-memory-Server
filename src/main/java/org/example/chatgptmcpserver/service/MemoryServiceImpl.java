package org.example.chatgptmcpserver.service;

import org.example.chatgptmcpserver.domain.Memory;
import org.example.chatgptmcpserver.domain.MemoryScope;
import org.example.chatgptmcpserver.dto.request.GetMemoryTimelineRequest;
import org.example.chatgptmcpserver.dto.request.RememberMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SearchMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SetMemoryScopeRequest;
import org.example.chatgptmcpserver.dto.request.UpdateMemoryRequest;
import org.example.chatgptmcpserver.dto.response.MemoryResponse;
import org.example.chatgptmcpserver.exception.InvalidScopeException;
import org.example.chatgptmcpserver.exception.MemoryNotFoundException;
import org.example.chatgptmcpserver.exception.MemoryValidationException;
import org.example.chatgptmcpserver.repository.MemoryRepository;
import org.example.chatgptmcpserver.repository.MemorySettingsRepository;
import org.example.chatgptmcpserver.repository.MemoryTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Validated
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryServiceImpl implements MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 50;
    private static final String DEFAULT_SCOPE_CONTEXT = "default";

    private final MemoryRepository memoryRepository;
    private final MemoryTagRepository memoryTagRepository;
    private final MemorySettingsRepository memorySettingsRepository;

    public MemoryServiceImpl(
            MemoryRepository memoryRepository,
            MemoryTagRepository memoryTagRepository,
            MemorySettingsRepository memorySettingsRepository
    ) {
        this.memoryRepository = memoryRepository;
        this.memoryTagRepository = memoryTagRepository;
        this.memorySettingsRepository = memorySettingsRepository;
    }

    @Override
    @Transactional
    public MemoryResponse saveMemory(RememberMemoryRequest request) {
        MemoryScope scope = request.scope() == null || request.scope().isBlank()
                ? normalizeScope(memorySettingsRepository.getActiveScope(normalizeScopeContext(request.scopeContext())))
                : normalizeScope(request.scope());
        if (scope == MemoryScope.ALL || scope == MemoryScope.OFF) {
            throw new MemoryValidationException(
                    "Stored memories cannot use scope 'all' or 'off'. Set a concrete scope first or pass request.scope."
            );
        }
        validateDateRange(request.validFrom(), request.validUntil());

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        Memory memory = new Memory(
                id,
                request.category().trim(),
                scope.value(),
                request.title().trim(),
                request.content().trim(),
                request.memoryType().trim(),
                request.importance(),
                request.occurredAt(),
                request.validFrom(),
                request.validUntil(),
                request.source(),
                request.metadata() == null ? Map.of() : request.metadata(),
                true,
                now,
                now,
                List.of()
        );

        Memory saved = memoryRepository.save(memory);
        memoryTagRepository.replaceTags(saved.id(), request.tags());
        List<String> tags = memoryTagRepository.findTagNamesByMemoryId(saved.id());

        log.info("event=memory_saved id={} scope={} category={} importance={}",
                saved.id(), saved.scope(), saved.category(), saved.importance());
        return toResponse(saved, tags);
    }

    @Override
    public List<MemoryResponse> searchMemories(SearchMemoryRequest request) {
        ScopeResolution scopeResolution = resolveScope(request.scope(), request.scopeContext());
        if (scopeResolution.mode() == ScopeMode.NONE) {
            return List.of();
        }

        int limit = normalizeLimit(request.limit());
        List<Memory> memories = memoryRepository.search(
                request.query(),
                scopeResolution.scopeFilter(),
                request.category(),
                limit
        );

        return memories.stream()
                .map(memory -> toResponse(memory, memoryTagRepository.findTagNamesByMemoryId(memory.id())))
                .toList();
    }

    @Override
    public MemoryResponse getMemory(UUID id, String explicitScope, String scopeContext) {
        Memory memory = memoryRepository.findById(id)
                .orElseThrow(() -> new MemoryNotFoundException("Memory not found for id: " + id));

        if (!memory.active()) {
            throw new MemoryNotFoundException("Memory not found for id: " + id);
        }

        ScopeResolution scopeResolution = resolveScope(explicitScope, scopeContext);
        if (scopeResolution.mode() == ScopeMode.NONE) {
            throw new MemoryNotFoundException("Memory not available for current scope settings");
        }

        if (scopeResolution.mode() != ScopeMode.ALL && !memory.scope().equals(scopeResolution.scopeFilter())) {
            throw new MemoryNotFoundException("Memory not available for current scope settings");
        }

        return toResponse(memory, memoryTagRepository.findTagNamesByMemoryId(memory.id()));
    }

    @Override
    @Transactional
    public MemoryResponse updateMemory(UpdateMemoryRequest request) {
        Memory current = memoryRepository.findById(request.id())
                .orElseThrow(() -> new MemoryNotFoundException("Memory not found for id: " + request.id()));

        String nextScope = current.scope();
        if (request.scope() != null && !request.scope().isBlank()) {
            MemoryScope scope = normalizeScope(request.scope());
            if (scope == MemoryScope.ALL || scope == MemoryScope.OFF) {
                throw new MemoryValidationException("Stored memories cannot use scope 'all' or 'off'.");
            }
            nextScope = scope.value();
        }

        Instant validFrom = request.validFrom() != null ? request.validFrom() : current.validFrom();
        Instant validUntil = request.validUntil() != null ? request.validUntil() : current.validUntil();
        validateDateRange(validFrom, validUntil);

        Memory updated = new Memory(
                current.id(),
                request.category() != null ? request.category().trim() : current.category(),
                nextScope,
                request.title() != null ? request.title().trim() : current.title(),
                request.content() != null ? request.content().trim() : current.content(),
                request.memoryType() != null ? request.memoryType().trim() : current.memoryType(),
                request.importance() != null ? request.importance() : current.importance(),
                request.occurredAt() != null ? request.occurredAt() : current.occurredAt(),
                validFrom,
                validUntil,
                request.source() != null ? request.source() : current.source(),
                request.metadata() != null ? request.metadata() : current.metadata(),
                current.active(),
                current.createdAt(),
                Instant.now(),
                List.of()
        );

        Memory persisted = memoryRepository.update(updated);
        if (request.tags() != null) {
            memoryTagRepository.replaceTags(persisted.id(), request.tags());
        }

        return toResponse(persisted, memoryTagRepository.findTagNamesByMemoryId(persisted.id()));
    }

    @Override
    public boolean deactivateMemory(UUID id) {
        boolean changed = memoryRepository.deactivate(id);
        if (!changed) {
            throw new MemoryNotFoundException("Memory not found for id: " + id);
        }
        return true;
    }

    @Override
    public List<MemoryResponse> getMemoriesByScope(String scope, Integer limit) {
        ScopeResolution scopeResolution = resolveScope(scope, null);
        if (scopeResolution.mode() == ScopeMode.NONE) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);
        List<Memory> memories = scopeResolution.mode() == ScopeMode.ALL
                ? memoryRepository.search(null, null, null, effectiveLimit)
                : memoryRepository.findByScope(scopeResolution.scopeFilter(), effectiveLimit);

        return memories.stream()
                .map(memory -> toResponse(memory, memoryTagRepository.findTagNamesByMemoryId(memory.id())))
                .toList();
    }

    @Override
    public List<MemoryResponse> getMemoriesByCategory(String category, String scope, Integer limit) {
        ScopeResolution scopeResolution = resolveScope(scope, null);
        if (scopeResolution.mode() == ScopeMode.NONE) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);
        List<Memory> memories = memoryRepository.findByCategory(
                category,
                scopeResolution.mode() == ScopeMode.ALL ? null : scopeResolution.scopeFilter(),
                effectiveLimit
        );

        return memories.stream()
                .map(memory -> toResponse(memory, memoryTagRepository.findTagNamesByMemoryId(memory.id())))
                .toList();
    }

    @Override
    public String setActiveMemoryScope(SetMemoryScopeRequest request) {
        MemoryScope scope = normalizeScope(request.scope());
        memorySettingsRepository.setActiveScope(scope.value(), normalizeScopeContext(request.scopeContext()));
        return scope.value();
    }

    @Override
    public String getActiveMemoryScope() {
        return getActiveMemoryScope(DEFAULT_SCOPE_CONTEXT);
    }

    @Override
    public String getActiveMemoryScope(String scopeContext) {
        return normalizeScope(memorySettingsRepository.getActiveScope(normalizeScopeContext(scopeContext))).value();
    }

    @Override
    public List<MemoryResponse> getTimelineMemories(GetMemoryTimelineRequest request) {
        if (request.to().isBefore(request.from())) {
            throw new MemoryValidationException("Timeline end date must be on or after the start date.");
        }

        ScopeResolution scopeResolution = resolveScope(request.scope(), request.scopeContext());
        if (scopeResolution.mode() == ScopeMode.NONE) {
            return List.of();
        }

        List<Memory> timeline = memoryRepository.findTimeline(
                request.from(),
                request.to(),
                scopeResolution.mode() == ScopeMode.ALL ? null : scopeResolution.scopeFilter(),
                normalizeLimit(request.limit())
        );

        return timeline.stream()
                .map(memory -> toResponse(memory, memoryTagRepository.findTagNamesByMemoryId(memory.id())))
                .toList();
    }

    @Override
    public List<String> getCategories() {
        return memoryRepository.findCategories();
    }

    private MemoryScope normalizeScope(String scope) {
        try {
            return MemoryScope.fromValue(scope);
        } catch (IllegalArgumentException ex) {
            throw new InvalidScopeException(ex.getMessage());
        }
    }

    private void validateDateRange(Instant validFrom, Instant validUntil) {
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new MemoryValidationException("validUntil must be on or after validFrom");
        }
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private ScopeResolution resolveScope(String explicitScope, String scopeContext) {
        if (explicitScope != null && !explicitScope.isBlank()) {
            MemoryScope scope = normalizeScope(explicitScope);
            return fromScope(scope);
        }

        MemoryScope activeScope = normalizeScope(memorySettingsRepository.getActiveScope(normalizeScopeContext(scopeContext)));
        return fromScope(activeScope);
    }

    private String normalizeScopeContext(String scopeContext) {
        if (scopeContext == null || scopeContext.isBlank()) {
            return DEFAULT_SCOPE_CONTEXT;
        }
        return scopeContext;
    }

    private ScopeResolution fromScope(MemoryScope scope) {
        if (scope == MemoryScope.OFF) {
            return new ScopeResolution(ScopeMode.NONE, null);
        }
        if (scope == MemoryScope.ALL) {
            return new ScopeResolution(ScopeMode.ALL, null);
        }
        return new ScopeResolution(ScopeMode.ONE, scope.value());
    }

    private MemoryResponse toResponse(Memory memory, List<String> tags) {
        return new MemoryResponse(
                memory.id(),
                memory.category(),
                memory.scope(),
                memory.title(),
                memory.content(),
                memory.memoryType(),
                memory.importance(),
                memory.occurredAt(),
                memory.validFrom(),
                memory.validUntil(),
                memory.source(),
                memory.metadata(),
                memory.active(),
                memory.createdAt(),
                memory.updatedAt(),
                tags
        );
    }

    private enum ScopeMode {
        NONE,
        ONE,
        ALL
    }

    private record ScopeResolution(ScopeMode mode, String scopeFilter) {
    }
}

