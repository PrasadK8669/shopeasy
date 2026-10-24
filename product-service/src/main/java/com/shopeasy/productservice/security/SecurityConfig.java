package com.shopeasy.productservice.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final String ISSUER = "shopeasy-auth";

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Checks the expiry time and that the token was issued by our auth-service
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    // Turns the "roles" claim (["ADMIN"]) into Spring authorities ("ROLE_ADMIN")
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityErrorHandler errors,
                                    JwtAuthenticationConverter converter,
                                    @Value("${app.internal.api-key}") String internalKey) throws Exception {
        if (internalKey.length() < 32) {
            throw new IllegalStateException("app.internal.api-key must be at least 32 characters. Set it in application-local.yml.");
        }

        http
                .csrf(csrf -> csrf.disable()) // safe: the API uses bearer tokens, not cookies
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer")))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public: browsing the catalogue and the contact form
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/categories", "/api/products/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        // Stock changes: Order Service (internal key) or an admin
                        .requestMatchers(HttpMethod.PATCH, "/api/products/*/stock", "/api/products/*/stock/restore")
                                .hasAnyRole("SERVICE", "ADMIN")
                        // Catalogue changes: admins only
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("ADMIN")
                        // Deny by default: anything not listed above is blocked
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .addFilterBefore(new InternalApiKeyFilter(internalKey), AuthorizationFilter.class)
                // At most 5 contact messages per 10 minutes from one address
                .addFilterBefore(new RateLimitFilter("POST", "/api/contact", 5, Duration.ofMinutes(10)),
                        AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String origin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static SecretKey secretKey(String secret) {
        String value = secret == null ? "" : secret.trim();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            String start = value.substring(0, Math.min(4, value.length()));
            throw new IllegalStateException("app.jwt.secret is not valid base64. Length read: "
                    + value.length() + " (expected 44). First 4 characters: [" + start
                    + "]. Set it in application-local.yml.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}