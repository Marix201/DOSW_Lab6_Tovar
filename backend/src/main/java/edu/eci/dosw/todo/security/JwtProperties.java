package edu.eci.dosw.todo.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings. The signing secret comes from the environment (JWT_SECRET) and is never committed.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {
}
