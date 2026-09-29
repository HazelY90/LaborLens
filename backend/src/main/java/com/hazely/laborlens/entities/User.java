package com.hazely.laborlens.entities;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Credentials and a revocation counter shared by all of the user's devices. */
@Getter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, length = 50)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {}
    public User(String email, String username, String passwordHash) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public void rename(String username) { this.username = username; }
    public void revoke() { this.tokenVersion = Math.incrementExact(this.tokenVersion); }
    public void changePassword(String hash) {
        this.passwordHash = hash;
        revoke();
    }
}
