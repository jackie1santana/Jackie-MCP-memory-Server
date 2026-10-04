package org.example.chatgptmcpserver.config;

import org.example.chatgptmcpserver.tool.MemoryMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.memory.enabled", havingValue = "true", matchIfMissing = true)
public class McpToolConfiguration {

    @Bean
    public ToolCallbackProvider memoryToolCallbackProvider(MemoryMcpTools memoryMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(memoryMcpTools)
                .build();
    }
}

