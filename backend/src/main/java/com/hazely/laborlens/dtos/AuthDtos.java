package com.hazely.laborlens.dtos;

import jakarta.validation.constraints.*;
import java.security.Principal;

/** Auth payloads redact credentials from diagnostic string representations. */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record Register(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 50) @Pattern(regexp = "[^\\p{Cntrl}]+") String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
        @Override
        public String toString() {
            return "Register[redacted]";
        }
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record Rename(@NotBlank @Size(max = 50) @Pattern(regexp = "[^\\p{Cntrl}]+") String username) {
    }

    public record PasswordChange(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
        @Override
        public String toString() {
            return "PasswordChange[redacted]";
        }
    }

    public record Delete(@NotBlank @Size(max = 72) String currentPassword) {
        @Override
        public String toString() {
            return "Delete[redacted]";
        }
    }

    public record Profile(Long id, String email, String username) {
    }

    public record Access(String accessToken, String tokenType, long expiresIn, Profile user) {
        @Override
        public String toString() {
            return "Access[redacted]";
        }
    }

    public record Tokens(Access access, String refreshToken) {
        @Override
        public String toString() {
            return "Tokens[redacted]";
        }
    }

    public record Identity(long id, int version) implements Principal {
        @Override
        public String getName() {
            return Long.toString(id);
        }
    }
}
