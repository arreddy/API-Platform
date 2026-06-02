package com.apiplatform.mockserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class WebConfig {

  @Bean
  WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }
}
