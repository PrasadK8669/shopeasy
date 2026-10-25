package com.shopeasy.authservice.security;

import jakarta.servlet.http.HttpServletRequest;

// What we know about the caller of a sign-in request
public record ClientInfo(String deviceId, String userAgent, String ip) {

    public static ClientInfo from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > 300) {
            agent = agent.substring(0, 300);
        }
        // getRemoteAddr is the real connecting address. Headers like X-Forwarded-For can be faked.
        return new ClientInfo(request.getHeader("X-Device-Id"), agent, request.getRemoteAddr());
    }
}