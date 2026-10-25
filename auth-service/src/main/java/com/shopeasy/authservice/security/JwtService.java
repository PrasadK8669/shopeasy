package com.shopeasy.authservice.security;

import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shopeasy.authservice.entity.User;
import com.shopeasy.authservice.exception.InvalidChallengeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    // Tokens that other services accept as a login
    public static final String ISSUER = "shopeasy-auth";

    // A different issuer, so the other services reject a challenge token as a login token
    private static final String CHALLENGE_ISSUER = "shopeasy-auth-mfa";
    private static final String CHALLENGE_PURPOSE = "admin-mfa";
    private static final long CHALLENGE_MINUTES = 5;

    private final JwtEncoder encoder;
    private final JwtDecoder challengeDecoder;
    private final long expiryMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-minutes}") long expiryMinutes) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret must be a base64 string. Set it in application-local.yml.");
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes.");
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(CHALLENGE_ISSUER));
        this.challengeDecoder = decoder;

        this.expiryMinutes = expiryMinutes;
    }

    public String generate(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        return sign(claims);
    }

    // Proof that the password step passed. It carries no roles and is useless to every other service.
    public String generateChallenge(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(CHALLENGE_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(CHALLENGE_MINUTES, ChronoUnit.MINUTES))
                .subject(String.valueOf(user.getId()))
                .claim("purpose", CHALLENGE_PURPOSE)
                .build();
        return sign(claims);
    }

    // Returns the user id, or throws if the token is missing, tampered with, expired or the wrong kind
    public Long parseChallenge(String token) {
        try {
            Jwt jwt = challengeDecoder.decode(token);
            if (!CHALLENGE_PURPOSE.equals(jwt.getClaimAsString("purpose"))) {
                throw new InvalidChallengeException();
            }
            return Long.valueOf(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            throw new InvalidChallengeException();
        }
    }

    public long expirySeconds() {
        return expiryMinutes * 60;
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}