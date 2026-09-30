package com.aditi.authservice.infrastructure.persistence;

import com.aditi.authservice.domain.model.Role;
import com.aditi.authservice.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The JPA entity — an infrastructure concern that the domain must never see.
 *
 * <p>Why a separate class instead of annotating the domain {@code User}?
 * Because mapping an aggregate onto a relational table is a storage decision.
 * A future "store users in a document DB" or "split roles into their own
 * table" requirement would otherwise ripple straight into the domain and
 * break every use case. Here, only this file changes.
 *
 * <p>{@code @Enumerated(STRING)} stores {@code "ADMIN"} / {@code "CUSTOMER"},
 * which is byte-for-byte what previous revisions of this service wrote, so
 * upgrading the service does not require a data migration.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected UserEntity() {
        // required by JPA
    }

    public UserEntity(Long id, String username, String password, Role role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    /** Persistence-facing → domain-facing. */
    public static UserEntity from(User user) {
        return new UserEntity(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole());
    }

    /** Domain-facing → persistence-facing. */
    public User toDomain() {
        return new User(id, username, password, role);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
