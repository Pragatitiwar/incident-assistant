package com.demo.incidentAssistance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] userKey;
    private final byte[] adminKey;
    private final RateLimiter userLimiter;

    public ApiKeyFilter(@Value("${assistant.security.api-key}") String userKey,
                        @Value("${assistant.security.admin-key}") String adminKey,
                        @Value("${assistant.security.rate-limit-per-minute:10}") int perMinute) {
        if (userKey.length() < 16 || adminKey.length() < 16) {
            throw new IllegalStateException("API keys must be at least 16 characters");
        }
        if (userKey.equals(adminKey)) {
            throw new IllegalStateException("The user key and admin key must differ");
        }
        this.userKey = userKey.getBytes(StandardCharsets.UTF_8);
        this.adminKey = adminKey.getBytes(StandardCharsets.UTF_8);
        this.userLimiter = new RateLimiter(perMinute);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader("X-API-Key");
        boolean isAdmin = matches(provided, adminKey);
        boolean isUser = matches(provided, userKey);

        if (!isAdmin && !isUser) {
            reject(response, 401, "Unauthorized", "Missing or invalid API key", null);
            return;
        }
        if (isAdminPath(request.getRequestURI()) && !isAdmin) {
            reject(response, 403, "Forbidden", "This endpoint requires the admin key", null);
            return;
        }
        if (!isAdmin && !userLimiter.tryAcquire()) {
            reject(response, 429, "Too Many Requests", "Rate limit exceeded",
                    userLimiter.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isPublic(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && "/actuator/health".equals(request.getRequestURI());
    }

    private boolean isAdminPath(String path) {
        return path.startsWith("/eval") || path.startsWith("/ingest")
                || path.startsWith("/actuator");
    }

    private boolean matches(String provided, byte[] key) {
        return provided != null
                && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), key);
    }

    private void reject(HttpServletResponse response, int status, String title,
                        String detail, Long retryAfter) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}