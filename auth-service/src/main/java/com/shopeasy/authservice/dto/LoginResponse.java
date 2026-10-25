package com.shopeasy.authservice.dto;

public record LoginResponse(
        boolean mfaRequired,
        String token,
        String tokenType,
        Long expiresInSeconds,
        UserResponse user,
        String challengeToken,
        String maskedEmail) {

    public static LoginResponse signedIn(AuthResponse auth) {
        return new LoginResponse(false, auth.token(), auth.tokenType(), auth.expiresInSeconds(), auth.user(), null, null);
    }

    public static LoginResponse challenge(String challengeToken, String maskedEmail) {
        return new LoginResponse(true, null, null, null, null, challengeToken, maskedEmail);
    }
}