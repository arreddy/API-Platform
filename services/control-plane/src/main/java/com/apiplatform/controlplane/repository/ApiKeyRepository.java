package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.ApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

  Page<ApiKey> findByTenantId(String tenantId, Pageable pageable);

  Page<ApiKey> findByTenantIdAndProxyId(String tenantId, String proxyId, Pageable pageable);

  Page<ApiKey> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

  Optional<ApiKey> findByTenantIdAndId(String tenantId, String id);

  // For gateway key validation — look up by prefix first to narrow candidates
  List<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, String status);
}
