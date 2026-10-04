package org.example.chatgptmcpserver.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.example.chatgptmcpserver.ChatGptMcpServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

class McpOAuthSecurityTest {
    @Test
    void publishesDiscoveryAndRejectsUnauthenticatedAndWrongResourceRequests() throws Exception {
        try (var context = new SpringApplicationBuilder(ChatGptMcpServerApplication.class)
                .profiles("nodb")
                .run("--server.port=0", "--spring.datasource.password=unused",
                        "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                        "--app.mcp.oauth.enabled=true", "--app.mcp.auth.enabled=true",
                        "--app.mcp.public-base-url=https://memory.example.com",
                        "--app.mcp.oauth.client-id=chatgpt-test", "--app.mcp.oauth.client-secret=test-only-secret",
                        "--app.mcp.oauth.redirect-uri=https://chatgpt.com/connector_platform_oauth_redirect",
                        "--app.mcp.oauth.github-client-id=test-github", "--app.mcp.oauth.github-client-secret=test-only-github-secret",
                        "--app.mcp.oauth.github-owner=jackie1santana")) {
            String base = "http://localhost:" + ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpClient client = HttpClient.newHttpClient();
            var metadata = get(client, base + "/.well-known/oauth-protected-resource", null);
            assertThat(metadata.statusCode()).isEqualTo(200);
            assertThat(metadata.body()).contains("https://memory.example.com/mcp", "memory");
            var discovery = get(client, base + "/.well-known/oauth-authorization-server", null);
            assertThat(discovery.statusCode()).isEqualTo(200);
            assertThat(discovery.body()).contains("https://memory.example.com/oauth2/token", "S256");
            var denied = get(client, base + "/mcp", null);
            assertThat(denied.statusCode()).isEqualTo(401);
            assertThat(denied.headers().firstValue("WWW-Authenticate").orElseThrow()).contains("resource_metadata");
            assertThat(get(client, base + "/mcp", "Bearer invalid-token").statusCode()).isEqualTo(401);
            assertThat(get(client, base + "/oauth2/authorize?resource=https://wrong.example.com", null).statusCode()).isEqualTo(400);
            assertThat(get(client, base + "/actuator/health", null).statusCode()).isEqualTo(200);
        }
    }

    private HttpResponse<String> get(HttpClient client, String url, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
