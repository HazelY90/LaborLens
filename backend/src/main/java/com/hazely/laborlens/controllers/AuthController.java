package com.hazely.laborlens.controllers;

import com.hazely.laborlens.dtos.AuthDtos.*;
import com.hazely.laborlens.security.RefreshCookie;
import com.hazely.laborlens.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Refresh credentials leave the server only through an HttpOnly cookie. */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthService auth;
    private final RefreshCookie cookies;

    public AuthController(AuthService auth, RefreshCookie cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<Profile> register(@Valid @RequestBody Register input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(auth.register(input));
    }

    @PostMapping("/login")
    public ResponseEntity<Access> login(@Valid @RequestBody Login input) {
        return credentials(auth.login(input));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Access> refresh(HttpServletRequest request) {
        return credentials(auth.refresh(cookies.read(request)));
    }

    @GetMapping("/me")
    public Profile me(@AuthenticationPrincipal Identity identity) {
        return auth.me(identity);
    }

    @PatchMapping("/me/username")
    public Profile rename(@AuthenticationPrincipal Identity identity, @Valid @RequestBody Rename input) {
        return auth.rename(identity, input);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Identity identity, @Valid @RequestBody PasswordChange input) {
        auth.password(identity, input);
        return cleared();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Identity identity, @Valid @RequestBody Delete input) {
        auth.delete(identity, input);
        return cleared();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(cookies.read(request));
        return cleared();
    }

    private ResponseEntity<Access> credentials(Tokens tokens) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.refreshToken())).body(tokens.access());
    }

    private ResponseEntity<Void> cleared() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }
}
