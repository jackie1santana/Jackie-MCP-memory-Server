package org.example.chatgptmcpserver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.mcp.auth.enabled", havingValue = "true")
public class McpAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    private final Environment environment;

    public McpAuthFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = environment.getProperty("app.mcp.auth.token", "");
        String headerName = environment.getProperty("app.mcp.auth.header-name", "X-MCP-API-KEY");

        if (token.isBlank()) {
            log.error("event=mcp_auth_misconfigured reason=blank_token path={}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP auth token is not configured");
            return;
        }

        String providedToken = request.getHeader(headerName);
        if ((providedToken == null || providedToken.isBlank()) && request.getHeader("Authorization") != null) {
            providedToken = parseBearerToken(request.getHeader("Authorization"));
        }

        if (!token.equals(providedToken)) {
            log.warn("event=mcp_auth_denied path={} remote_addr={}", request.getRequestURI(), request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized MCP request");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/mcp") || path.startsWith("/internal/status"));
    }

    private String parseBearerToken(String authorization) {
        String prefix = "Bearer ";
        if (authorization != null && authorization.startsWith(prefix) && authorization.length() > prefix.length()) {
            return authorization.substring(prefix.length());
        }
        return "";
    }
}

