package com.hazely.laborlens.security;

import com.hazely.laborlens.exceptions.AuthError;
import com.hazely.laborlens.services.AuthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Authenticates access JWTs only; refresh cookies are handled by auth endpoints. */
public class JwtFilter extends OncePerRequestFilter {
    private final AuthService auth;
    public JwtFilter(AuthService auth) { this.auth = auth; }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return request.getMethod().equals("OPTIONS") || (request.getMethod().equals("POST")
                && List.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").contains(path));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || header.substring(7).isBlank()) throw AuthError.unauthorized();
                var identity = auth.authenticate(header.substring(7));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (AuthError error) {
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Valid authentication is required.\",\"field\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
