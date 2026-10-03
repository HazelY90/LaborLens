package com.hazely.laborlens.security;

import com.hazely.laborlens.dtos.AuthDtos.Identity;
import com.hazely.laborlens.entities.User;
import com.hazely.laborlens.exceptions.AuthError;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

/** Signed access and refresh JWTs share a key but cannot substitute for one another. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtTokens {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final String issuer;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtTokens(@Value("${app.auth.jwt-secret}") String secret,
            @Value("${app.auth.issuer:laborlens}") String issuer,
            @Value("${app.auth.access-seconds:1800}") long accessSeconds,
            @Value("${app.auth.refresh-seconds:604800}") long refreshSeconds) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32 || issuer.isBlank() || accessSeconds <= 0 || refreshSeconds <= accessSeconds) {
            throw new IllegalStateException("JWT requires a secret of at least 32 UTF-8 bytes and valid lifetimes");
        }
        var signingKey = new SecretKeySpec(key, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
        var jwtDecoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer)));
        decoder = jwtDecoder;
        this.issuer = issuer;
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    public long accessSeconds() {
        return accessSeconds;
    }

    public long refreshSeconds() {
        return refreshSeconds;
    }

    public String issue(User user, boolean isRefresh) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(isRefresh ? refreshSeconds : accessSeconds))
                .id(UUID.randomUUID().toString()).claim("token_type", isRefresh ? "refresh" : "access")
                .claim("token_version", user.getTokenVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
    }

    public Identity read(String token, boolean isRefresh) {
        try {
            if (token == null || token.length() > 8192) throw AuthError.unauthorized();
            Jwt jwt = decoder.decode(token);
            String expected = isRefresh ? "refresh" : "access";
            Object rawVersion = jwt.getClaims().get("token_version");
            if (!(rawVersion instanceof Integer) && !(rawVersion instanceof Long)) throw AuthError.unauthorized();
            long version = ((Number) rawVersion).longValue();
            Instant now = Instant.now();
            if (!expected.equals(jwt.getClaimAsString("token_type"))
                    || version < 0 || version > Integer.MAX_VALUE || jwt.getExpiresAt() == null
                    || jwt.getIssuedAt() == null || !jwt.getExpiresAt().isAfter(now)
                    || jwt.getIssuedAt().isAfter(now) || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                throw AuthError.unauthorized();
            }
            long id = Long.parseLong(jwt.getSubject());
            if (id < 1) throw AuthError.unauthorized();
            return new Identity(id, (int) version);
        } catch (JwtException | IllegalArgumentException error) {
            throw AuthError.unauthorized();
        }
    }
}
