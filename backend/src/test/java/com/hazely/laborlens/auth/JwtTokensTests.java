package com.hazely.laborlens.auth;

import com.hazely.laborlens.entities.User;
import com.hazely.laborlens.exceptions.AuthError;
import com.hazely.laborlens.security.JwtTokens;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** Validate signed claims directly without sleeps or network calls. */
class JwtTokensTests {
    private static final String SECRET = "test-only-signing-secret-at-least-32-bytes";
    private final JwtTokens jwt = new JwtTokens(SECRET, "laborlens", 1800, 604800);
    @Test
    void checksSignatureTypeExpirationAndMandatoryClaims() {
        Instant now = Instant.now();
        assertThrows(AuthError.class, () -> jwt.read(signed("laborlens", "access", now.minusSeconds(120), now.minusSeconds(1), 0), false));
        assertThrows(AuthError.class, () -> jwt.read(signed("another-app", "access", now.minusSeconds(1), now.plusSeconds(60), 0), false));
        assertThrows(AuthError.class, () -> jwt.read(signed("laborlens", "refresh", now.minusSeconds(1), now.plusSeconds(60), 0), false));
        assertThrows(AuthError.class, () -> jwt.read(signed("laborlens", "access", now.plusSeconds(60), now.plusSeconds(120), 0), false));
        assertThrows(AuthError.class, () -> jwt.read(signed("laborlens", "access", now.minusSeconds(1), now.plusSeconds(60), null), false));
        assertThrows(AuthError.class, () -> jwt.read(signed("laborlens", "access", now.minusSeconds(1), now.plusSeconds(60), 1.5), false));
        var other = new JwtTokens("different-test-secret-at-least-32-bytes", "laborlens", 1800, 604800);
        var user = new User("person@example.test", "Name", "hash"); ReflectionTestUtils.setField(user, "id", 1L);
        assertThrows(AuthError.class, () -> jwt.read(other.issue(user, false), false));
        assertThrows(AuthError.class, () -> jwt.read("not-a-jwt", false));
        assertThrows(AuthError.class, () -> jwt.read(null, false));
    }
    @Test
    void issuesIndependentTokensWithIdentityAndVersion() {
        var user = new User("person@example.test", "Name", "hash"); ReflectionTestUtils.setField(user, "id", 42L);
        user.revoke();
        String access = jwt.issue(user, false), refresh = jwt.issue(user, true);
        assertEquals(42, jwt.read(access, false).id());
        assertEquals(1, jwt.read(refresh, true).version());
        assertNotEquals(refresh, jwt.issue(user, true));
        assertThrows(AuthError.class, () -> jwt.read(access, true));
        assertThrows(IllegalStateException.class, () -> new JwtTokens("short", "laborlens", 1800, 604800));
    }
    private String signed(String issuer, String type, Instant issued, Instant expires, Object version) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(issued).expiresAt(expires).claim("token_type", type);
        if (version != null) claims.claim("token_version", version);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
