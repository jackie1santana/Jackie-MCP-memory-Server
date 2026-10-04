package org.example.chatgptmcpserver.security;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;

@Configuration
@ConditionalOnProperty(name = "app.mcp.oauth.enabled", havingValue = "true")
public class McpOAuthConfiguration {
    static final String SCOPE = "memory";

    static String required(Environment env, String key) {
        String value = env.getRequiredProperty(key).trim();
        if (value.isEmpty()) throw new IllegalStateException(key + " must be configured");
        return value;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(Environment env) {
        String issuer = required(env, "app.mcp.public-base-url");
        if (!issuer.startsWith("https://") || issuer.endsWith("/")) {
            throw new IllegalStateException("app.mcp.public-base-url must be HTTPS without a trailing slash");
        }
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(Environment env) {
        RegisteredClient client = RegisteredClient.withId("chatgpt-memory")
                .clientId(required(env, "app.mcp.oauth.client-id"))
                .clientSecret("{noop}" + required(env, "app.mcp.oauth.client-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(required(env, "app.mcp.oauth.redirect-uri"))
                .scope(SCOPE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(7)).reuseRefreshTokens(false).build())
                .build();
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(AuthorizationServerSettings settings) {
        OAuth2AccessTokenGenerator access = new OAuth2AccessTokenGenerator();
        access.setAccessTokenCustomizer(context -> context.getClaims()
                .claim("aud", List.of(settings.getIssuer() + "/mcp"))
                .claim("iss", settings.getIssuer()));
        return new DelegatingOAuth2TokenGenerator(access, new OAuth2RefreshTokenGenerator());
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(Environment env, AuthorizationServerSettings settings) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("github")
                .clientId(required(env, "app.mcp.oauth.github-client-id"))
                .clientSecret(required(env, "app.mcp.oauth.github-client-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(settings.getIssuer() + "/login/oauth2/code/github")
                .scope("read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id").clientName("GitHub").build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationSecurity(HttpSecurity http, AuthorizationServerSettings settings) throws Exception {
        http.securityMatcher("/oauth2/authorize", "/oauth2/token", "/oauth2/introspect",
                "/oauth2/revoke", "/.well-known/oauth-authorization-server");
        http.oauth2AuthorizationServer(server -> {});
        http.addFilterBefore(new McpOAuthResourceFilter(settings.getIssuer() + "/mcp"),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/github"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    OpaqueTokenIntrospector memoryTokenIntrospector(OAuth2AuthorizationService service,
                                                   AuthorizationServerSettings settings) {
        return token -> {
            OAuth2Authorization authorization = service.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null || authorization.getAccessToken() == null
                    || !authorization.getAccessToken().isActive()
                    || !authorization.getAuthorizedScopes().contains(SCOPE)) {
                throw new BadOpaqueTokenException("Invalid or expired memory access token");
            }
            Map<String, Object> claims = authorization.getAccessToken().getClaims();
            if (claims == null || !settings.getIssuer().equals(String.valueOf(claims.get("iss")))
                    || !List.of(settings.getIssuer() + "/mcp").equals(claims.get("aud"))) {
                throw new BadOpaqueTokenException("Invalid memory token audience");
            }
            return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(), claims,
                    Set.of(new SimpleGrantedAuthority("SCOPE_" + SCOPE)));
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain memorySecurity(HttpSecurity http, OpaqueTokenIntrospector introspector,
                                      AuthorizationServerSettings settings) throws Exception {
        return http.securityMatcher("/mcp", "/mcp/**", "/internal/**")
                .csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("SCOPE_" + SCOPE))
                .oauth2ResourceServer(resource -> resource.opaqueToken(opaque -> opaque.introspector(introspector))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\""
                                    + settings.getIssuer() + "/.well-known/oauth-protected-resource\", scope=\"memory\"");
                        })).build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain loginSecurity(HttpSecurity http, Environment env) throws Exception {
        String owner = required(env, "app.mcp.oauth.github-owner");
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/**", "/actuator/health", "/error", "/oauth2/authorization/**", "/login/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2Login(login -> login.userInfoEndpoint(info -> info.userService(request -> {
                    var user = delegate.loadUser(request);
                    if (!owner.equalsIgnoreCase(user.getAttribute("login"))) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "This GitHub account is not allowed");
                    }
                    return new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_OWNER")), user.getAttributes(), "id");
                }))).build();
    }
}
