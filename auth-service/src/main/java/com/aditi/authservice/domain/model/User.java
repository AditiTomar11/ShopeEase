package com.aditi.authservice.domain.model;

import java.util.Objects;

/**
 * The User aggregate — the heart of the auth domain.
 *
 * <p>Deliberately a <b>plain Java object</b>: no {@code @Entity}, no Lombok,
 * no setters-only bean contract. The domain decides how a user is created and
 * how its password is stored; the database decides how it is persisted. That
 * separation is the whole point of Onion Architecture.
 *
 * <p>{@code password} always holds a <b>one-way hash</b> (BCrypt by default),
 * never a plaintext password. See {@link com.aditi.authservice.domain.port.PasswordHasher}.
 */
public class User {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Role role;

    public User(Long id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username is required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required");
        this.role = Objects.requireNonNull(role, "role is required");
    }

    /** Used for a not-yet-persisted user; the repository assigns the id. */
    public static User newUser(String username, String passwordHash, Role role) {
        return new User(null, username, passwordHash, role);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    /** @return the BCrypt hash, never the plaintext. */
    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return Objects.equals(username, other.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        // Never interpolate the hash into logs.
        return "User{id=" + id + ", username='" + username + "', role=" + role + '}';
    }
}
