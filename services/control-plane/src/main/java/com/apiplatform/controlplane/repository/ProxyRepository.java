package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.Proxy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProxyRepository extends JpaRepository<Proxy, String> {

    Page<Proxy> findByTenantId(String tenantId, Pageable pageable);

    Page<Proxy> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Page<Proxy> findByTenantIdAndApiId(String tenantId, String apiId, Pageable pageable);

    Optional<Proxy> findByTenantIdAndId(String tenantId, String id);

    boolean existsByTenantIdAndPathPrefixAndStatusAndIdNot(
            String tenantId, String pathPrefix, String status, String id);

    boolean existsByTenantIdAndPathPrefixAndStatus(
            String tenantId, String pathPrefix, String status);

    List<Proxy> findByStatus(String status);
}
