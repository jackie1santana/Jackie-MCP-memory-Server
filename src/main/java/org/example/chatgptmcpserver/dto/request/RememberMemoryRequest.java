package org.example.chatgptmcpserver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RememberMemoryRequest(
        @NotBlank String category,
        String scope,
        String scopeContext,
        @NotBlank String title,
        @NotBlank String content,
        @NotBlank String memoryType,
        @NotNull @Min(1) @Max(10) Integer importance,
        Instant occurredAt,
        Instant validFrom,
        Instant validUntil,
        String source,
        Map<String, Object> metadata,
        List<String> tags
) {
}

