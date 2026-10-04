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
                .sources(TestTools.class)
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
            var mock = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .webAppContextSetup((org.springframework.web.context.WebApplicationContext) context)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            var consent = context.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService.class);
            consent.save(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
                    .withId("chatgpt-memory", "42").scope("memory").build());
            String verifier = "test-verifier-long-enough-for-pkce-01234567890123456789";
            String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var authorize = mock.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/oauth2/authorize")
                    .queryParam("response_type", "code").queryParam("client_id", "chatgpt-test")
                    .queryParam("redirect_uri", "https://chatgpt.com/connector_platform_oauth_redirect")
                    .queryParam("scope", "memory").queryParam("state", "test-state")
                    .queryParam("resource", "https://memory.example.com/mcp")
                    .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login()
                            .oauth2User(new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                                    java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER")),
                                    java.util.Map.of("id", "42"), "id"))))
                    .andReturn().getResponse();
            assertThat(authorize.getStatus()).as("body=%s error=%s redirect=%s", authorize.getContentAsString(), authorize.getErrorMessage(), authorize.getRedirectedUrl()).isEqualTo(302);
            String redirect = authorize.getRedirectedUrl();
            assertThat(redirect).contains("code=", "state=test-state");
            String code = org.springframework.web.util.UriComponentsBuilder.fromUriString(redirect)
                    .build().getQueryParams().getFirst("code");
            var exchange = mock.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/oauth2/token").contentType("application/x-www-form-urlencoded")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("chatgpt-test", "test-only-secret"))
                    .param("grant_type", "authorization_code").param("code", code)
                    .param("redirect_uri", "https://chatgpt.com/connector_platform_oauth_redirect")
                    .param("code_verifier", verifier).param("resource", "https://memory.example.com/mcp"))
                    .andReturn().getResponse();
            assertThat(exchange.getStatus()).as(exchange.getContentAsString()).isEqualTo(200);
            var json = new tools.jackson.databind.ObjectMapper().readTree(exchange.getContentAsString());
            String accessToken = json.get("access_token").asText();
            var introspector = context.getBean(org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector.class);
            assertThat(introspector.introspect(accessToken).getName()).isEqualTo("42");
            var initialize = rpc(client, base, accessToken, null,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"security-test\",\"version\":\"1\"}}}");
            assertThat(initialize.statusCode()).as(initialize.body()).isEqualTo(200);
            assertThat(initialize.body()).contains("protocolVersion", "tools");
            String session = initialize.headers().firstValue("Mcp-Session-Id").orElseThrow();
            var initialized = rpc(client, base, accessToken, session,
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            assertThat(initialized.statusCode()).isEqualTo(202);
            var tools = rpc(client, base, accessToken, session,
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            assertThat(tools.statusCode()).as(tools.body()).isEqualTo(200);
            assertThat(tools.body()).contains("list_memory_categories", "remember_memory", "inputSchema");
            var categories = rpc(client, base, accessToken, session,
                    "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_memory_categories\",\"arguments\":{}}}");
            assertThat(categories.statusCode()).as(categories.body()).isEqualTo(200);
            assertThat(categories.body()).contains("categories").doesNotContain("\"isError\":true");
            mock.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/oauth2/revoke").contentType("application/x-www-form-urlencoded")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("chatgpt-test", "test-only-secret"))
                    .param("token", accessToken)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> introspector.introspect(accessToken))
                    .isInstanceOf(org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException.class);
        }
    }

    private HttpResponse<String> get(HttpClient client, String url, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rpc(HttpClient client, String base, String token, String session, String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("MCP-Protocol-Version", "2025-03-26")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (session != null) request.header("Mcp-Session-Id", session);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class TestTools {
        @org.springframework.context.annotation.Bean
        org.springframework.ai.tool.ToolCallbackProvider testMemoryTools() {
            var service = org.mockito.Mockito.mock(org.example.chatgptmcpserver.service.MemoryService.class);
            org.mockito.Mockito.when(service.getCategories()).thenReturn(java.util.List.of("fidelity"));
            return org.springframework.ai.tool.method.MethodToolCallbackProvider.builder()
                    .toolObjects(new org.example.chatgptmcpserver.tool.MemoryMcpTools(service)).build();
        }
    }
}
