package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.dto.ApiDto;
import com.apiplatform.controlplane.entity.Api;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiRepository;
import java.util.List;
import java.util.Map;
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

@ExtendWith(MockitoExtension.class)
class ApiRegistryServiceTest {

  @Mock ApiRepository apiRepository;
  @Mock OasValidatorService oasValidator;
  @InjectMocks ApiRegistryService service;

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String API_ID = UUID.randomUUID().toString();
  private static final String USER_ID = UUID.randomUUID().toString();

  private Api sampleApi() {
    Api api = new Api();
    api.setId(API_ID);
    api.setTenantId(TENANT);
    api.setName("pet-api");
    api.setTitle("Pet API");
    api.setVersion("1.0.0");
    api.setOasVersion("3.0");
    api.setStatus("active");
    api.setTags(new String[] {});
    api.setServers(List.of());
    api.setEndpoints(List.of());
    api.setSecuritySchemes(Map.of());
    api.setOasDocument(Map.of("openapi", "3.0.0"));
    return api;
  }

  private OasValidatorService.ParsedOas sampleParsed() {
    return new OasValidatorService.ParsedOas(
        "3.0",
        "Pet API",
        "1.0.0",
        "A test",
        "/v1",
        List.of(Map.of("url", "https://api.example.com/v1")),
        List.of(Map.of("path", "/pets", "method", "GET")),
        Map.of(),
        List.of("pets"),
        Map.of("openapi", "3.0.0"));
  }

  // ---- list ----

  @Test
  void list_noFilters_callsFindByTenantId() {
    when(apiRepository.findByTenantId(eq(TENANT), any())).thenReturn(Page.empty());
    service.list(TENANT, null, null, 1, 20);
    verify(apiRepository).findByTenantId(eq(TENANT), any());
  }

  @Test
  void list_withSearch_callsSearchByTitle() {
    when(apiRepository.searchByTitle(eq(TENANT), eq("pet"), any())).thenReturn(Page.empty());
    service.list(TENANT, null, "pet", 1, 20);
    verify(apiRepository).searchByTitle(eq(TENANT), eq("pet"), any());
  }

  @Test
  void list_withStatus_callsFindByStatus() {
    when(apiRepository.findByTenantIdAndStatus(eq(TENANT), eq("active"), any()))
        .thenReturn(Page.empty());
    service.list(TENANT, "active", null, 1, 20);
    verify(apiRepository).findByTenantIdAndStatus(eq(TENANT), eq("active"), any());
  }

  @Test
  void list_returnsPageDto() {
    Api api = sampleApi();
    when(apiRepository.findByTenantId(eq(TENANT), any()))
        .thenReturn(new PageImpl<>(List.of(api)));
    var page = service.list(TENANT, null, null, 1, 20);
    assertThat(page.data()).hasSize(1);
    assertThat(page.data().get(0).name()).isEqualTo("pet-api");
  }

  // ---- get ----

  @Test
  void get_found_returnsFullDto() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(sampleApi()));
    var result = service.get(TENANT, API_ID);
    assertThat(result.id()).isEqualTo(API_ID);
    assertThat(result.name()).isEqualTo("pet-api");
    assertThat(result.title()).isEqualTo("Pet API");
  }

  @Test
  void get_notFound_throwsNotFound() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(TENANT, API_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- getOasDocument ----

  @Test
  void getOasDocument_found_returnsMap() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(sampleApi()));
    Map<String, Object> doc = service.getOasDocument(TENANT, API_ID);
    assertThat(doc).containsKey("openapi");
  }

  @Test
  void getOasDocument_notFound_throwsNotFound() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getOasDocument(TENANT, API_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- register ----

  @Test
  void register_success_savesAndReturnsDto() {
    when(oasValidator.parseAndValidate(any())).thenReturn(sampleParsed());
    when(apiRepository.existsByTenantIdAndName(TENANT, "pet-api")).thenReturn(false);
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.register(TENANT, USER_ID, "dummy oas", null);

    assertThat(result.api().name()).isEqualTo("pet-api");
    assertThat(result.suggested()).containsKey("targetUrl");
    verify(apiRepository).save(any());
  }

  @Test
  void register_withNameOverride_usesOverride() {
    when(oasValidator.parseAndValidate(any())).thenReturn(sampleParsed());
    when(apiRepository.existsByTenantIdAndName(TENANT, "custom-name")).thenReturn(false);
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.register(TENANT, USER_ID, "dummy oas", "custom-name");
    assertThat(result.api().name()).isEqualTo("custom-name");
  }

  @Test
  void register_duplicateName_throwsConflict() {
    when(oasValidator.parseAndValidate(any())).thenReturn(sampleParsed());
    when(apiRepository.existsByTenantIdAndName(TENANT, "pet-api")).thenReturn(true);

    assertThatThrownBy(() -> service.register(TENANT, USER_ID, "dummy", null))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void register_titleWithSpecialChars_slugified() {
    var parsed =
        new OasValidatorService.ParsedOas(
            "3.0", "My Cool API!!!",
            "1.0.0", null, null, List.of(), List.of(), Map.of(), List.of(), Map.of());
    when(oasValidator.parseAndValidate(any())).thenReturn(parsed);
    when(apiRepository.existsByTenantIdAndName(eq(TENANT), any())).thenReturn(false);
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.register(TENANT, USER_ID, "dummy", null);
    assertThat(result.api().name()).doesNotContain("!").doesNotContain(" ");
  }

  // ---- update ----

  @Test
  void update_description_updatesField() {
    Api api = sampleApi();
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(api));
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ApiDto.UpdateRequest(null, "New description", null, null);
    var result = service.update(TENANT, API_ID, req);

    assertThat(result.description()).isEqualTo("New description");
  }

  @Test
  void update_status_updatesField() {
    Api api = sampleApi();
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(api));
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ApiDto.UpdateRequest(null, null, "deprecated", null);
    var result = service.update(TENANT, API_ID, req);

    assertThat(result.status()).isEqualTo("deprecated");
  }

  @Test
  void update_withOasContent_reparsesOas() {
    Api api = sampleApi();
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(api));
    when(oasValidator.parseAndValidate(any())).thenReturn(sampleParsed());
    when(apiRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var req = new ApiDto.UpdateRequest(null, null, null, "new oas content");
    service.update(TENANT, API_ID, req);

    verify(oasValidator).parseAndValidate("new oas content");
  }

  @Test
  void update_notFound_throwsNotFound() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(TENANT, API_ID, new ApiDto.UpdateRequest(null, null, null, null)))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- delete ----

  @Test
  void delete_found_callsRepositoryDelete() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.of(sampleApi()));
    service.delete(TENANT, API_ID);
    verify(apiRepository).delete(any(Api.class));
  }

  @Test
  void delete_notFound_throwsNotFound() {
    when(apiRepository.findByTenantIdAndId(TENANT, API_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(TENANT, API_ID))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ---- validate ----

  @Test
  void validate_delegatesToOasValidator() {
    when(oasValidator.parseAndValidate("content")).thenReturn(sampleParsed());
    var result = service.validate("content");
    assertThat(result.title()).isEqualTo("Pet API");
  }
}
