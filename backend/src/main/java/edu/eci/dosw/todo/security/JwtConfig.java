package edu.eci.dosw.todo.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Single place where the cryptographic configuration lives, so the encoder (login) and the decoder
 * (every protected request) always use the same key and algorithm (HS256).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(properties.issuer()),
                requiredClaimsValidator()));
        return decoder;
    }

    /** exp, iat and a numeric subject (the user id) are mandatory; the library does not enforce them. */
    static OAuth2TokenValidator<Jwt> requiredClaimsValidator() {
        return jwt -> {
            String subject = jwt.getSubject();
            boolean valid = jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null
                    && subject != null
                    && subject.matches("\\d+");
            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing or invalid required claims", null));
        };
    }

    static SecretKey secretKey(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and contain at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
