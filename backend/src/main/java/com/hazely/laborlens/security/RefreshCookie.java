package com.hazely.laborlens.security;

import com.hazely.laborlens.exceptions.AuthError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

/** A host-only cookie and an explicit origin allowlist protect refresh-based mutations. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RefreshCookie {
    public static final String NAME = "refresh_token";
    private final boolean isSecure;
    private final List<String> origins;
    private final JwtTokens jwt;
    public RefreshCookie(@Value("${app.auth.cookie-secure:true}") boolean isSecure,
                         @Value("${app.auth.allowed-origins:http://localhost:5173,http://localhost:8080}") String origins,
                         JwtTokens jwt) {
        this.isSecure = isSecure;
        this.origins = Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (this.origins.isEmpty() || this.origins.stream().anyMatch(s -> s.contains("*") || s.equals("null"))) {
            throw new IllegalStateException("Configure explicit authentication origins");
        }
        this.jwt = jwt;
    }
    public List<String> origins() { return origins; }
    public String issue(String token) { return cookie(token, jwt.refreshSeconds()); }
    public String clear() { return cookie("", 0); }
    private String cookie(String token, long seconds) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(isSecure).sameSite("Lax")
                .path("/api/auth").maxAge(Duration.ofSeconds(seconds)).build().toString();
    }
    public String read(HttpServletRequest request) {
        // Browsers send Origin on these POST requests; non-browser clients must supply it too.
        if (!origins.contains(request.getHeader("Origin"))) {
            throw new AuthError(403, "INVALID_ORIGIN", "Request origin is not allowed.", null);
        }
        String token = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (NAME.equals(cookie.getName())) {
                    if (token != null) throw AuthError.unauthorized();
                    token = cookie.getValue();
                }
            }
        }
        if (token == null || token.isBlank()) throw AuthError.unauthorized();
        return token;
    }
}
