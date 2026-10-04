package org.example.chatgptmcpserver.dto.response;

import java.util.List;
import java.util.Map;

public record DbTableColumnsResponse(
        String schema,
        String table,
        List<Map<String, Object>> columns
) {
}

