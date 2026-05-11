package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.ProxyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProxyVersionRepository extends JpaRepository<ProxyVersion, String> {

    List<ProxyVersion> findByProxyIdOrderByVersionDesc(String proxyId);

    Optional<ProxyVersion> findByProxyIdAndVersion(String proxyId, int version);
}
