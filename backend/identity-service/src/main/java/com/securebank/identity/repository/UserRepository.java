package com.securebank.identity.repository;

import com.securebank.identity.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);
}
