package org.example.chatgptmcpserver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record GetMemoryTimelineRequest(
        @NotNull Instant from,
        @NotNull Instant to,
        String scope,
        String scopeContext,
        @Min(1) @Max(50) Integer limit
) {
}

