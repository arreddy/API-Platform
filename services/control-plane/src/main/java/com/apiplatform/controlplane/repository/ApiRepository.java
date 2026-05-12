package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.Api;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiRepository extends JpaRepository<Api, String> {

  Page<Api> findByTenantId(String tenantId, Pageable pageable);

  Page<Api> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

  @Query(
      "SELECT a FROM Api a WHERE a.tenantId = :tenantId AND LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))")
  Page<Api> searchByTitle(String tenantId, String search, Pageable pageable);

  Optional<Api> findByTenantIdAndId(String tenantId, String id);

  boolean existsByTenantIdAndName(String tenantId, String name);
}
