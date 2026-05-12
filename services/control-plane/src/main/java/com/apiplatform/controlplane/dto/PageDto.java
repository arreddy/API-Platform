package com.apiplatform.controlplane.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageDto<T>(List<T> data, long total, int page, int limit) {
  public static <T> PageDto<T> of(Page<T> page) {
    return new PageDto<>(
        page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
  }
}
