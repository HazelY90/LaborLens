package com.hazely.laborlens.config;

import com.hazely.laborlens.security.*;
import com.hazely.laborlens.services.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;

/** Stateless access JWT authentication with separately guarded refresh-cookie endpoints. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public SecurityFilterChain security(HttpSecurity http, AuthService auth, RefreshCookie cookies) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(cookies.origins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return http.sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // APIs use bearer headers; refresh/logout additionally enforce an explicit Origin allowlist.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(source))
                .authorizeHttpRequests(c -> c
                        .requestMatchers("/", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(c -> c.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Valid authentication is required.\",\"field\":null}");
                }))
                // Instantiate inside the chain so the filter is not also registered as a servlet filter.
                .addFilterBefore(new JwtFilter(auth), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
