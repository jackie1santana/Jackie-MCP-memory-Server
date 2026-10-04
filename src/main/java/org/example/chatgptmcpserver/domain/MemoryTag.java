package org.example.chatgptmcpserver.domain;

import java.time.Instant;

public record MemoryTag(
        Long id,
        String name,
        boolean active,
        Instant createdAt
) {
}

