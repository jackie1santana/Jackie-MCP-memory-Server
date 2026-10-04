package org.example.chatgptmcpserver.domain;

import java.time.Instant;

public record MemoryScopeSetting(
        String settingKey,
        String settingValue,
        Instant updatedAt
) {
}

