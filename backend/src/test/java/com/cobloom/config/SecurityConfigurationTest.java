package com.cobloom.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigurationTest {

  @Test
  void corsAllowsOnlyConfiguredOriginsAndMethods() {
    SecurityConfig security = new SecurityConfig("http://localhost:5173, https://app.example.com");
    UrlBasedCorsConfigurationSource source =
        (UrlBasedCorsConfigurationSource) security.corsConfigurationSource();
    CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/records"));

    assertEquals(List.of("http://localhost:5173", "https://app.example.com"), cors.getAllowedOrigins());
    assertEquals(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"), cors.getAllowedMethods());
    assertFalse(cors.getAllowedOrigins().contains("*"));
    assertFalse(cors.getAllowedMethods().contains("*"));
  }

  @Test
  void corsRejectsWildcardOrEmptyConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> new SecurityConfig("*"));
    assertThrows(IllegalArgumentException.class, () -> new SecurityConfig(" , "));
  }

  @Test
  void unauthenticatedRequestsReceiveStructured401Response() throws Exception {
    SecurityConfig security = new SecurityConfig("http://localhost:5173");
    MockHttpServletResponse response = new MockHttpServletResponse();

    security.restAuthenticationEntryPoint().commence(
        new MockHttpServletRequest("GET", "/api/records"),
        response,
        new AuthenticationCredentialsNotFoundException("missing"));

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentType().startsWith("application/json"));
    assertTrue(response.getContentAsString().contains("invalid"));
  }

  @Test
  void applicationConfigurationContainsNoCommittedSecretsAndDisablesH2Console() throws IOException {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8)
        .replace("\r\n", "\n");
    Path seedSql = Path.of("src/main/resources/data.sql");

    assertTrue(yaml.contains("jwt-secret: ${COBLOOM_JWT_SECRET}"));
    assertTrue(yaml.contains("api-key: ${COBLOOM_LLM_API_KEY:}"));
    assertTrue(yaml.contains("console:\n      enabled: false"));
    assertFalse(yaml.contains("api-key: sk-"));
    assertFalse(yaml.contains("demo-secret-change-me"));
    assertFalse(Files.exists(seedSql),
        "A shared account or password hash must not be committed in data.sql");
  }
}
