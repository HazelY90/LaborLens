package com.hazely.laborlens.services;

import com.hazely.laborlens.dtos.AuthDtos.*;
import com.hazely.laborlens.entities.User;
import com.hazely.laborlens.exceptions.AuthError;
import com.hazely.laborlens.repositories.UserRepository;
import com.hazely.laborlens.security.JwtTokens;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** User row locks serialize token issuance, password changes and global logout. */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Transactional
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokens jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtTokens jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public Profile register(Register input) {
        String email = email(input.email());
        checkPassword(input.password(), "password");
        if (users.existsByEmail(email)) throw duplicate();
        try {
            User user = users.saveAndFlush(new User(email, input.username().strip(), passwords.encode(input.password())));
            return profile(user);
        } catch (DataIntegrityViolationException error) {
            // The unique database constraint also handles concurrent registrations.
            throw duplicate();
        }
    }

    public Tokens login(Login input) {
        checkPassword(input.password(), "password");
        User user = users.lockEmail(email(input.email())).orElse(null);
        boolean isValid = passwords.matches(input.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !isValid) {
            throw new AuthError(401, "INVALID_CREDENTIALS", "Email or password is incorrect.",
                    null);
        }
        return tokens(user);
    }

    public Tokens refresh(String token) {
        return tokens(locked(jwt.read(token, true)));
    }

    public void logout(String token) {
        locked(jwt.read(token, true)).revoke();
    }

    @Transactional(readOnly = true)
    public Identity authenticate(String token) {
        Identity identity = jwt.read(token, false);
        checked(users.findById(identity.id()).orElseThrow(AuthError::unauthorized), identity);
        return identity;
    }

    @Transactional(readOnly = true)
    public Profile me(Identity identity) {
        return profile(checked(users.findById(identity.id()).orElseThrow(AuthError::unauthorized),
                identity));
    }

    public Profile rename(Identity identity, Rename input) {
        User user = locked(identity);
        user.rename(input.username().strip());
        return profile(user);
    }

    public void password(Identity identity, PasswordChange input) {
        User user = locked(identity);
        currentPassword(user, input.currentPassword());
        checkPassword(input.newPassword(), "newPassword");
        user.changePassword(passwords.encode(input.newPassword()));
    }

    public void delete(Identity identity, Delete input) {
        User user = locked(identity);
        currentPassword(user, input.currentPassword());
        users.delete(user);
        users.flush();
    }

    private User locked(Identity identity) {
        return checked(users.lockId(identity.id()).orElseThrow(AuthError::unauthorized), identity);
    }

    private User checked(User user, Identity identity) {
        if (user.getTokenVersion() != identity.version()) throw AuthError.unauthorized();
        return user;
    }

    private Tokens tokens(User user) {
        return new Tokens(new Access(jwt.issue(user, false), "Bearer", jwt.accessSeconds(), profile(user)),
                jwt.issue(user, true));
    }

    private Profile profile(User user) {
        return new Profile(user.getId(), user.getEmail(), user.getUsername());
    }

    private void currentPassword(User user, String password) {
        checkPassword(password, "currentPassword");
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new AuthError(400, "INVALID_PASSWORD", "Current password is incorrect.",
                    "currentPassword");
        }
    }

    private void checkPassword(String password, String field) {
        // BCrypt accepts at most 72 bytes, not 72 Unicode characters.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthError(400, "INVALID_REQUEST", "Password must not exceed 72 UTF-8 bytes.",
                    field);
        }
    }

    private String email(String email) {
        if (!email.matches("[\\x21-\\x7E]+")) {
            throw new AuthError(400, "INVALID_REQUEST", "Use an ASCII email address without spaces.",
                    "email");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    private AuthError duplicate() {
        return new AuthError(409, "EMAIL_EXISTS", "Email is already registered.", "email");
    }
}
