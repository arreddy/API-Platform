package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.ProxyVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProxyVersionRepository extends JpaRepository<ProxyVersion, String> {

  List<ProxyVersion> findByProxyIdOrderByVersionDesc(String proxyId);

  Optional<ProxyVersion> findByProxyIdAndVersion(String proxyId, int version);
}
