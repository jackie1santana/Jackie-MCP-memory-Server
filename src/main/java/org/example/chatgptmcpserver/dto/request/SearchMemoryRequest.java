package org.example.chatgptmcpserver.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SearchMemoryRequest(
        String query,
        String scope,
        String scopeContext,
        String category,
        @Min(1) @Max(50) Integer limit
) {
}

