package org.example.chatgptmcpserver.config;

import org.example.chatgptmcpserver.tool.DbAdminMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.mcp.db-admin.enabled", havingValue = "true")
public class DbAdminMcpToolConfiguration {

    @Bean
    public ToolCallbackProvider dbAdminToolCallbackProvider(DbAdminMcpTools dbAdminMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dbAdminMcpTools)
                .build();
    }
}

