package com.shopeasy.productservice.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Allows at most "maxRequests" calls per client address in each time window
public class RateLimitFilter extends OncePerRequestFilter {

    private record Window(long start, int count) {
    }

    private final String method;
    private final String pathPrefix;
    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(String method, String pathPrefix, int maxRequests, Duration window) {
        this.method = method;
        this.pathPrefix = pathPrefix;
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !method.equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith(pathPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) { // keep memory bounded
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= windowMillis);
        }

        // getRemoteAddr is the real connecting address. X-Forwarded-For can be faked, so it is ignored.
        String key = request.getRemoteAddr();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= windowMillis
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));

        if (w.count() > maxRequests) {
            long retry = Math.max(1, (w.start() + windowMillis - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Too many requests. Try again in " + retry + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}