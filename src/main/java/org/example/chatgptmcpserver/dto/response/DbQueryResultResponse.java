package org.example.chatgptmcpserver.dto.response;

import java.util.List;
import java.util.Map;

public record DbQueryResultResponse(
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated
) {
}

