package com.securebank.audit.repository;

import com.securebank.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only repository: no save/delete methods are exposed (writes go through {@link AuditLogWriter}). */
public interface AuditLogRepository extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findById(UUID id);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();

    @Query("select distinct a.action from AuditLog a where a.resourceType in :types order by a.action")
    List<String> findDistinctActionsForResourceTypes(@Param("types") Collection<String> types);
}
