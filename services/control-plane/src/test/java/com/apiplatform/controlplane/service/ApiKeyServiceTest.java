package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.dto.ApiKeyDto;
import com.apiplatform.controlplane.entity.ApiKey;
import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiKeyRepository;
import com.apiplatform.controlplane.repository.ProxyRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

  @Mock ApiKeyRepository apiKeyRepository;
  @Mock ProxyRepository proxyRepository;
  @Mock BCryptPasswordEncoder bcrypt;
  @InjectMocks ApiKeyService service;

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String KEY_ID = UUID.randomUUID().toString();
  private static final String PROXY_ID = UUID.randomUUID().toString();
  private static final String USER_ID = UUID.randomUUID().toString();

  private ApiKey sampleKey() {
    ApiKey k = new ApiKey();
    k.setId(KEY_ID);
    k.setTenantId(TENANT);
    k.setProxyId(PROXY_ID);
    k.setName("test-key");
    k.setKeyPrefix("apk_test1234");
    k.setKeyHash("$2a$12$hashedvalue");
    k.setScopes(new String[] {"read"});
    k.setRateLimit(1000);
    k.setRateLimitWindow("1h");
    k.setStatus("active");
    return k;
  }

  // ---- list ----

  @Test
  void list_noFilters_callsFindByTenantId() {
    when(apiKeyRepository.findByTenantId(eq(TENANT), any())).thenReturn(Page.empty());
    service.list(TENANT, null, null, 1, 20);
    verify(apiKeyRepository).findByTenantId(eq(TENANT), any());
  }

  @Test
  void list_withProxyId_callsFindByProxyId() {
    when(apiKeyRepository.findByTenantIdAndProxyId(eq(TENANT), eq(PROXY_ID), any()))
        .thenReturn(Page.empty());
    service.list(TENANT, PROXY_ID, null, 1, 20);
    verify(apiKeyRepository).findByTenantIdAndProxyId(eq(TENANT), eq(PROXY_ID), any());
  }

  @Test
  void list_withStatus_callsFindByStatus() {
    when(apiKeyRepository.findByTenantIdAndStatus(eq(TENANT), eq("active"), any()))
        .thenReturn(Page.empty());
    service.list(TENANT, null, "active", 1, 20);
    verify(apiKeyRepository).findByTenantIdAndStatus(eq(TENANT), eq("active"), any());
  }

  @Test
  void list_returnsPageDto() {
    when(apiKeyRepository.findByTenantId(eq(TENANT), any()))
        .thenReturn(new PageImpl<>(List.of(sampleKey())));
    var result = service.list(TENANT, null, null, 1, 20);
    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).name()).isEqualTo("test-key");
  }

  // ---- get ----

  @Test
  void get_found_returnsSummary() {
    when(apiKeyRepository.findByTenantIdAndId(TENANT, KEY_ID))
        .thenReturn(Optional.of(sampleKey()));
    var result = service.get(TENANT, KEY_ID);
    assertThat(result.id()).isEqualTo(KEY_ID);
    assertThat(result.name()).isEqualTo("test-key");
  }

  @Test
  void get_notFound_throwsNotFound() {
    when(apiKeyRepository.findByTenantIdAndId(TENANT, KEY_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(TENANT, KEY_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- create ----

  @Test
  void create_success_returnsRawKey() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID))
        .thenReturn(Optional.of(new Proxy()));
    when(bcrypt.encode(any())).thenReturn("$2a$12$hash");
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ApiKeyDto.CreateRequest("my-key", PROXY_ID, List.of("read"), 500, "1h", null);
    var result = service.create(TENANT, USER_ID, req);

    assertThat(result.rawKey()).startsWith("apk_");
    assertThat(result.rawKey()).hasSize(52); // "apk_" (4) + 48 hex chars
    assertThat(result.key().name()).isEqualTo("my-key");
  }

  @Test
  void create_withDefaults_usesDefaultRateLimit() {
    when(bcrypt.encode(any())).thenReturn("$2a$12$hash");
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ApiKeyDto.CreateRequest("key", null, null, null, null, null);
    var result = service.create(TENANT, USER_ID, req);

    assertThat(result.key().rateLimit()).isEqualTo(1000);
    assertThat(result.key().rateLimitWindow()).isEqualTo("1h");
  }

  @Test
  void create_proxyNotFound_throwsNotFound() {
    when(proxyRepository.findByTenantIdAndId(TENANT, PROXY_ID)).thenReturn(Optional.empty());
    var req = new ApiKeyDto.CreateRequest("k", PROXY_ID, null, null, null, null);
    assertThatThrownBy(() -> service.create(TENANT, USER_ID, req))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- revoke ----

  @Test
  void revoke_setsStatusRevoked() {
    ApiKey key = sampleKey();
    when(apiKeyRepository.findByTenantIdAndId(TENANT, KEY_ID)).thenReturn(Optional.of(key));
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.revoke(TENANT, KEY_ID);
    assertThat(key.getStatus()).isEqualTo("revoked");
  }

  @Test
  void revoke_notFound_throwsNotFound() {
    when(apiKeyRepository.findByTenantIdAndId(TENANT, KEY_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.revoke(TENANT, KEY_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- validate ----

  @Test
  void validate_nullKey_returnsInvalid() {
    var result = service.validate(null, PROXY_ID);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_shortKey_returnsInvalid() {
    var result = service.validate("apk_short", PROXY_ID);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_noCandidates_returnsInvalid() {
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of());
    var result = service.validate("apk_123456789012rest", PROXY_ID);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_hashMismatch_returnsInvalid() {
    ApiKey key = sampleKey();
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of(key));
    when(bcrypt.matches(any(), any())).thenReturn(false);

    var result = service.validate("apk_test1234abcdef", PROXY_ID);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_expiredKey_setsStatusExpiredAndReturnsInvalid() {
    ApiKey key = sampleKey();
    key.setExpiresAt(OffsetDateTime.now().minusDays(1));
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of(key));
    when(bcrypt.matches(any(), any())).thenReturn(true);
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.validate("apk_test1234abcdef", PROXY_ID);

    assertThat(result.valid()).isFalse();
    assertThat(key.getStatus()).isEqualTo("expired");
    verify(apiKeyRepository).save(key);
  }

  @Test
  void validate_proxyMismatch_returnsInvalid() {
    ApiKey key = sampleKey();
    key.setProxyId("other-proxy-id");
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of(key));
    when(bcrypt.matches(any(), any())).thenReturn(true);

    var result = service.validate("apk_test1234abcdef", PROXY_ID);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void validate_validKey_returnsValidResult() {
    ApiKey key = sampleKey();
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of(key));
    when(bcrypt.matches(any(), any())).thenReturn(true);
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.validate("apk_test1234abcdef", PROXY_ID);

    assertThat(result.valid()).isTrue();
    assertThat(result.keyId()).isEqualTo(KEY_ID);
    assertThat(result.tenantId()).isEqualTo(TENANT);
    assertThat(result.rateLimit()).isEqualTo(1000);
  }

  @Test
  void validate_validKeyWithNullProxyId_matchesAnyProxy() {
    ApiKey key = sampleKey();
    key.setProxyId(null);
    when(apiKeyRepository.findByKeyPrefixAndStatus(any(), eq("active")))
        .thenReturn(List.of(key));
    when(bcrypt.matches(any(), any())).thenReturn(true);
    when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.validate("apk_test1234abcdef", PROXY_ID);
    assertThat(result.valid()).isTrue();
  }
}
