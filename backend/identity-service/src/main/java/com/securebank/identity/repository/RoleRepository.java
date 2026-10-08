package com.securebank.identity.repository;

import com.securebank.common.security.Role;
import com.securebank.identity.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Short> {

    Optional<RoleEntity> findByName(Role name);
}
