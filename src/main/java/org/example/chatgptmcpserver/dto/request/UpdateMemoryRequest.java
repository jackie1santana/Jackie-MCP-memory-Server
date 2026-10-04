package org.example.chatgptmcpserver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateMemoryRequest(
        @NotNull UUID id,
        String category,
        String scope,
        String title,
        String content,
        String memoryType,
        @Min(1) @Max(10) Integer importance,
        Instant occurredAt,
        Instant validFrom,
        Instant validUntil,
        String source,
        Map<String, Object> metadata,
        List<String> tags
) {
}

