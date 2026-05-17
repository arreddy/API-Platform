package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.OasInsight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OasInsightRepository extends JpaRepository<OasInsight, String> {
  Optional<OasInsight> findByApiId(String apiId);
  Optional<OasInsight> findByApiIdAndTenantId(String apiId, String tenantId);
}
