package com.securebank.identity.domain;

import com.securebank.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference row of the {@code roles} table (seeded by Flyway, never created at runtime). */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private Role name;

    protected RoleEntity() {
    }

    public Short getId() {
        return id;
    }

    public Role getName() {
        return name;
    }
}
