package edu.eci.dosw.todo.security;

import edu.eci.dosw.todo.dto.TokenResponse;
import edu.eci.dosw.todo.entity.Role;
import edu.eci.dosw.todo.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests (no Spring context): encoder and decoder share the same cryptographic setup. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-secret-0123456789-abcdef";
    private static final String ISSUER = "todo-api";

    private JwtProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private JwtService jwtService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, ISSUER, Duration.ofMinutes(30));
        JwtConfig config = new JwtConfig();
        encoder = config.jwtEncoder(properties);
        decoder = config.jwtDecoder(properties);
        jwtService = new JwtService(encoder, properties);

        user = new UserEntity();
        user.setId(42L);
        user.setEmail("ana@example.com");
        user.setRole(Role.USER);
    }

    private String tokenWith(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void issueToken_shouldContainIdentityIssuerAndThirtyMinuteExpiry() {
        TokenResponse response = jwtService.issueToken(user);

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void decode_shouldRejectTokenWithModifiedPayload() {
        String token = jwtService.issueToken(user).accessToken();
        String[] parts = token.split("\\.");
        // Flip a character in the middle of the payload: the signature no longer matches.
        char[] payload = parts[1].toCharArray();
        int middle = payload.length / 2;
        payload[middle] = payload[middle] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectTokenSignedWithAnotherKey() {
        JwtProperties otherProperties = new JwtProperties("another-secret-another-secret-0123456789", ISSUER, Duration.ofMinutes(30));
        String foreign = new JwtService(new JwtConfig().jwtEncoder(otherProperties), otherProperties)
                .issueToken(user).accessToken();

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectExpiredToken() {
        Instant now = Instant.now();
        String expired = tokenWith(JwtClaimsSet.builder()
                .issuer(ISSUER).subject("42")
                .issuedAt(now.minusSeconds(3600)).expiresAt(now.minusSeconds(1800))
                .claim("roles", List.of("USER")).build());

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectUnexpectedIssuer() {
        Instant now = Instant.now();
        String token = tokenWith(JwtClaimsSet.builder()
                .issuer("someone-else").subject("42")
                .issuedAt(now).expiresAt(now.plusSeconds(600)).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectTokenWithoutExpiration() {
        String token = tokenWith(JwtClaimsSet.builder()
                .issuer(ISSUER).subject("42").issuedAt(Instant.now()).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectNonNumericSubject() {
        Instant now = Instant.now();
        String token = tokenWith(JwtClaimsSet.builder()
                .issuer(ISSUER).subject("not-a-user-id")
                .issuedAt(now).expiresAt(now.plusSeconds(600)).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_shouldRejectGarbage() {
        assertThatThrownBy(() -> decoder.decode("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void secretKey_shouldRefuseShortSecrets() {
        JwtProperties weak = new JwtProperties("too-short", ISSUER, Duration.ofMinutes(30));

        assertThatThrownBy(() -> new JwtConfig().jwtEncoder(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void secretKey_shouldRefuseMissingSecret() {
        JwtProperties missing = new JwtProperties(null, ISSUER, Duration.ofMinutes(30));

        assertThatThrownBy(() -> new JwtConfig().jwtDecoder(missing)).isInstanceOf(IllegalStateException.class);
    }
}
