package org.example.chatgptmcpserver.dto.response;

import java.util.List;

public record SearchMemoryResponse(
        List<MemoryResponse> memories,
        int count,
        String activeScope
) {
}

