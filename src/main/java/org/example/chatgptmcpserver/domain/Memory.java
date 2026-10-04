package org.example.chatgptmcpserver.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Memory(
        UUID id,
        String category,
        String scope,
        String title,
        String content,
        String memoryType,
        Integer importance,
        Instant occurredAt,
        Instant validFrom,
        Instant validUntil,
        String source,
        Map<String, Object> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<String> tags
) {
}

