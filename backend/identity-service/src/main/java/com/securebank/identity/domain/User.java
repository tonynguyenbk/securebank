package com.securebank.identity.domain;

import com.securebank.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A SecureBank login identity. Never exposed directly through the API (see {@code UserResponse}). */
@Entity
@Table(name = "users")
public class User implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    /** Assigned IDs: tells Spring Data to persist instead of merge (avoids a select before insert). */
    @Transient
    private boolean isNew = true;

    protected User() {
    }

    public User(UUID id, String username, String passwordHash, String fullName, String email, String phone,
                Set<RoleEntity> roles, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.fullName = Objects.requireNonNull(fullName);
        this.email = Objects.requireNonNull(email);
        this.phone = phone;
        this.enabled = true;
        this.roles = new HashSet<>(roles);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> roleNames() {
        Set<Role> names = EnumSet.noneOf(Role.class);
        roles.forEach(r -> names.add(r.getName()));
        return names;
    }

    /** Highest-privilege role, used as "actorRole" in audit events. */
    public Role primaryRole() {
        Set<Role> names = roleNames();
        for (Role r : new Role[]{Role.ADMIN, Role.AUDITOR, Role.BANK_STAFF, Role.CUSTOMER}) {
            if (names.contains(r)) {
                return r;
            }
        }
        return null;
    }
}
