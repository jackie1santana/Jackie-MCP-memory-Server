package org.example.chatgptmcpserver.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** This single-resource server never issues tokens for another resource. */
final class McpOAuthResourceFilter extends OncePerRequestFilter {
    private final String resource;
    McpOAuthResourceFilter(String resource) { this.resource = resource; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String[] requested = request.getParameterValues("resource");
        boolean initialAuthorization = "GET".equals(request.getMethod()) && "/oauth2/authorize".equals(request.getRequestURI());
        if ((initialAuthorization && requested == null)
                || (requested != null && (requested.length != 1 || !resource.equals(requested[0])))) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_target\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
