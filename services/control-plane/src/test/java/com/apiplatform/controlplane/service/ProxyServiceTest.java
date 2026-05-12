package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.dto.ProxyDto;
import com.apiplatform.controlplane.entity.Api;
import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.entity.ProxyVersion;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiRepository;
import com.apiplatform.controlplane.repository.ProxyRepository;
import com.apiplatform.controlplane.repository.ProxyVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

  @Mock ProxyRepository proxyRepository;
  @Mock ProxyVersionRepository versionRepository;
  @Mock ApiRepository apiRepository;
  // Real ObjectMapper with JavaTimeModule — @Spy on third-party classes fails on Java 25
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private ProxyService service;

  @BeforeEach
  void setUp() {
    service = new ProxyService(proxyRepository, versionRepository, apiRepository, objectMapper);
  }

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String PROXY_ID = UUID.randomUUID().toString();
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String API_ID = UUID.randomUUID().toString();

  private Proxy sampleProxy() {
    Proxy p = new Proxy();
    p.setId(PROXY_ID);
    p.setTenantId(TENANT);
    p.setName("petstore");
    p.setTargetUrl("https://petstore3.swagger.io/api/v3");
    p.setPathPrefix("/petstore");
    p.setStripPrefix(true);
    p.setVersion(1);
    p.setStatus("active");
    p.setPolicies(Map.of());
    p.setRoutes(List.of());
    p.setHeaders(Map.of());
    return p;
  }

  // ---- list ----

  @Test
  void list_noFilters_callsFindByTenantId() {
    when(proxyRepository.findByTenantId(eq(TENANT), any())).thenReturn(Page.empty());
    service.list(TENANT, null, null, 1, 20);
    verify(proxyRepository).findByTenantId(eq(TENANT), any());
  }

  @Test
  void list_withStatus_callsFindByStatus() {
    when(proxyRepository.findByTenantIdAndStatus(eq(TENANT), eq("active"), any()))
        .thenReturn(Page.empty());
    service.list(TENANT, "active", null, 1, 20);
    verify(proxyRepository).findByTenantIdAndStatus(eq(TENANT), eq("active"), any());
  }

  @Test
  void list_withApiId_callsFindByApiId() {
    when(proxyRepository.findByTenantIdAndApiId(eq(TENANT), eq(API_ID), any()))
        .thenReturn(Page.empty());
    service.list(TENANT, null, API_ID, 1, 20);
    verify(proxyRepository).findByTenantIdAndApiId(eq(TENANT), eq(API_ID), any());
  }

  @Test
  void list_returnsPageDto() {
    when(proxyRepository.findByTenantId(eq(TENANT), any()))
        .thenReturn(new PageImpl<>(List.of(sampleProxy())));
    var result = service.list(TENANT, null, null, 1, 20);
    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).name()).isEqualTo("petstore");
  }

  // ---- get ----

  @Test
  void get_found_returnsFullDto() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID))
        .thenReturn(Optional.of(sampleProxy()));
    var result = service.get(TENANT, PROXY_ID);
    assertThat(result.id()).isEqualTo(PROXY_ID);
    assertThat(result.pathPrefix()).isEqualTo("/petstore");
  }

  @Test
  void get_notFound_throwsNotFound() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(TENANT, PROXY_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- getAllActive ----

  @Test
  void getAllActive_returnsActiveList() {
    when(proxyRepository.findByStatus("active")).thenReturn(List.of(sampleProxy()));
    var result = service.getAllActive();
    assertThat(result).hasSize(1);
  }

  // ---- create ----

  @Test
  void create_success_savesProxyAndVersion() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(new Api()));
    when(proxyRepository.existsByTenantIdAndPathPrefixAndStatus(TENANT, "/petstore", "active"))
        .thenReturn(false);
    when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req =
        new ProxyDto.CreateRequest(
            "petstore", "desc", "https://example.com", "/petstore", true, API_ID,
            null, null, null);
    var result = service.create(TENANT, USER_ID, req);

    assertThat(result.name()).isEqualTo("petstore");
    assertThat(result.version()).isEqualTo(1);
    verify(versionRepository).save(any(ProxyVersion.class));
  }

  @Test
  void create_withNoApiId_skipsApiLookup() {
    when(proxyRepository.existsByTenantIdAndPathPrefixAndStatus(TENANT, "/petstore", "active"))
        .thenReturn(false);
    when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req =
        new ProxyDto.CreateRequest(
            "petstore", null, "https://example.com", "/petstore", null, null,
            null, null, null);
    service.create(TENANT, USER_ID, req);
    verify(apiRepository, never()).findByTenantIdAndId(any(), any());
  }

  @Test
  void create_duplicatePathPrefix_throwsConflict() {
    when(proxyRepository.existsByTenantIdAndPathPrefixAndStatus(TENANT, "/petstore", "active"))
        .thenReturn(true);
    var req =
        new ProxyDto.CreateRequest(
            "petstore", null, "https://example.com", "/petstore", true, null,
            null, null, null);
    assertThatThrownBy(() -> service.create(TENANT, USER_ID, req))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void create_apiNotFound_throwsNotFound() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.empty());
    var req =
        new ProxyDto.CreateRequest(
            "x", null, "https://x.com", "/x", true, API_ID, null, null, null);
    assertThatThrownBy(() -> service.create(TENANT, USER_ID, req))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- update ----

  @Test
  void update_name_updatesProxy() {
    Proxy proxy = sampleProxy();
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.of(proxy));
    when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ProxyDto.UpdateRequest("new-name", null, null, null, null, null, null, null, null, null);
    var result = service.update(TENANT, PROXY_ID, USER_ID, req);

    assertThat(result.name()).isEqualTo("new-name");
    assertThat(result.version()).isEqualTo(2);
  }

  @Test
  void update_pathPrefix_checksConflict() {
    Proxy proxy = sampleProxy();
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.of(proxy));
    when(proxyRepository.existsByTenantIdAndPathPrefixAndStatusAndIdNot(
            TENANT, "/new-prefix", "active", PROXY_ID))
        .thenReturn(true);

    var req =
        new ProxyDto.UpdateRequest(null, null, null, "/new-prefix", null, null, null, null, null, null);
    assertThatThrownBy(() -> service.update(TENANT, PROXY_ID, USER_ID, req))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void update_notFound_throwsNotFound() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                service.update(
                    TENANT,
                    PROXY_ID,
                    USER_ID,
                    new ProxyDto.UpdateRequest(null, null, null, null, null, null, null, null, null, null)))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- delete ----

  @Test
  void delete_setsStatusInactive() {
    Proxy proxy = sampleProxy();
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.of(proxy));
    when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.delete(TENANT, PROXY_ID);

    assertThat(proxy.getStatus()).isEqualTo("inactive");
    verify(proxyRepository).save(proxy);
  }

  // ---- getVersions ----

  @Test
  void getVersions_found_returnsVersionList() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID))
        .thenReturn(Optional.of(sampleProxy()));
    ProxyVersion v = new ProxyVersion();
    v.setId(UUID.randomUUID().toString());
    v.setProxyId(PROXY_ID);
    v.setVersion(1);
    v.setChangeNote("Initial");
    when(versionRepository.findByProxyIdOrderByVersionDesc(PROXY_ID)).thenReturn(List.of(v));

    var result = service.getVersions(TENANT, PROXY_ID);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).version()).isEqualTo(1);
  }

  // ---- rollback ----

  @Test
  void rollback_success_restoresSnapshot() {
    Proxy proxy = sampleProxy();
    proxy.setVersion(3);
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.of(proxy));

    ProxyVersion pv = new ProxyVersion();
    pv.setVersion(1);
    pv.setSnapshot(
        Map.of(
            "targetUrl", "https://old.example.com",
            "pathPrefix", "/old",
            "stripPrefix", true,
            "policies", Map.of(),
            "routes", List.of(),
            "headers", Map.of()));
    when(versionRepository.findByProxyIdAndVersion(PROXY_ID, 1)).thenReturn(Optional.of(pv));
    when(proxyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.rollback(TENANT, PROXY_ID, USER_ID, 1);

    assertThat(result.targetUrl()).isEqualTo("https://old.example.com");
    assertThat(result.pathPrefix()).isEqualTo("/old");
    assertThat(result.version()).isEqualTo(4);
  }

  @Test
  void rollback_versionNotFound_throwsNotFound() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID))
        .thenReturn(Optional.of(sampleProxy()));
    when(versionRepository.findByProxyIdAndVersion(PROXY_ID, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rollback(TENANT, PROXY_ID, USER_ID, 99))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
