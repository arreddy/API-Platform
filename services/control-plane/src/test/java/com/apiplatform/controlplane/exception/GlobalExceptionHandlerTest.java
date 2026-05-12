package com.apiplatform.controlplane.exception;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleApp_returnsCorrectStatus() {
    AppException ex = new AppException(HttpStatus.NOT_FOUND, "API not found");
    ResponseEntity<Map<String, String>> response = handler.handleApp(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("error", "API not found");
  }

  @Test
  void handleApp_conflict_returns409() {
    AppException ex = new AppException(HttpStatus.CONFLICT, "Already exists");
    ResponseEntity<Map<String, String>> response = handler.handleApp(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void handleApp_unauthorized_returns401() {
    AppException ex = new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    ResponseEntity<Map<String, String>> response = handler.handleApp(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("error", "Invalid credentials");
  }

  @Test
  void handleUploadSize_returns413() {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);
    ResponseEntity<Map<String, String>> response = handler.handleUploadSize(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(response.getBody()).containsKey("error");
  }

  @Test
  void handleGeneric_returns500() {
    Exception ex = new RuntimeException("something exploded");
    ResponseEntity<Map<String, String>> response = handler.handleGeneric(ex);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("error", "Internal server error");
  }

  @Test
  void appException_storesStatus() {
    AppException ex = new AppException(HttpStatus.FORBIDDEN, "No access");
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ex.getMessage()).isEqualTo("No access");
  }
}
