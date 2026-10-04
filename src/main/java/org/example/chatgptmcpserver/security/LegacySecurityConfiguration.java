package org.example.chatgptmcpserver.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "app.mcp.oauth.enabled", havingValue = "false", matchIfMissing = true)
public class LegacySecurityConfiguration {
    @Bean
    SecurityFilterChain legacySecurity(HttpSecurity http) throws Exception {
        // Existing McpAuthFilter continues to enforce the configured API key.
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
