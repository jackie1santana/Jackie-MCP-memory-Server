package org.example.chatgptmcpserver.security;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.mcp.oauth.enabled", havingValue = "true")
public class McpOAuthMetadataController {
    private final AuthorizationServerSettings settings;
    public McpOAuthMetadataController(AuthorizationServerSettings settings) { this.settings = settings; }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    public Map<String, Object> metadata() {
        return Map.of("resource", settings.getIssuer() + "/mcp",
                "authorization_servers", List.of(settings.getIssuer()),
                "scopes_supported", List.of(McpOAuthConfiguration.SCOPE),
                "bearer_methods_supported", List.of("header"));
    }
}
