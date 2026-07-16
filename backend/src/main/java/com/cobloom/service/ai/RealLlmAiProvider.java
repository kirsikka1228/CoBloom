package com.cobloom.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "cobloom.ai-provider", havingValue = "real")
public class RealLlmAiProvider implements AiProvider {
  private static final Logger log = LoggerFactory.getLogger(RealLlmAiProvider.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;
  private final String extractionModel;
  private final int readTimeoutSeconds;

  public RealLlmAiProvider(ObjectMapper objectMapper,
                           @Value("${cobloom.llm.base-url:https://api.openai.com/v1}") String baseUrl,
                           @Value("${cobloom.llm.api-key:}") String apiKey,
                           @Value("${cobloom.llm.model:gpt-4o-mini}") String model,
                           @Value("${cobloom.llm.extraction-model:${cobloom.llm.model:gpt-4o-mini}}") String extractionModel,
                           @Value("${cobloom.llm.connect-timeout-seconds:10}") int connectTimeoutSeconds,
                           @Value("${cobloom.llm.read-timeout-seconds:120}") int readTimeoutSeconds) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
    this.extractionModel = extractionModel;
    this.readTimeoutSeconds = Math.max(1, readTimeoutSeconds);
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)));
    requestFactory.setReadTimeout(Duration.ofSeconds(this.readTimeoutSeconds));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  @Override
  public String name() {
    return "real";
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    String taskName = extractTaskName(userPrompt);
    String requestModel = modelForTask(taskName);
    long startedAt = System.nanoTime();
    log.info("AI request started provider={}, model={}, task={}", name(), requestModel, taskName);
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("AI request failed provider={}, model={}, task={}, elapsedMs={}, reason=missing_api_key",
          name(), requestModel, taskName, elapsedMillis(startedAt));
      throw new IllegalStateException("真实 LLM Provider 未配置 cobloom.llm.api-key");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", requestModel);
      body.put("temperature", 0.1);
      body.put("max_tokens", maxTokens(taskName));
      body.put("stream", true);
      if (isKnowledgeExtractionTask(taskName)) body.put("enable_thinking", false);
      body.put("messages", List.of(
          Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
          Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
      ));
      String result = restClient.post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + apiKey)
          .body(body)
          .exchange((request, response) -> readStreamingResponse(response, requestModel, taskName, startedAt));
      if (result == null || result.isBlank()) {
        throw new IllegalStateException("LLM provider returned an empty response");
      }
      log.info("AI request finished provider={}, model={}, task={}, elapsedMs={}, responseLength={}",
          name(), requestModel, taskName, elapsedMillis(startedAt), result.length());
      return result;
    } catch (Exception e) {
      Throwable rootCause = rootCause(e);
      boolean timedOut = rootCause instanceof SocketTimeoutException;
      log.warn("AI request failed provider={}, model={}, task={}, elapsedMs={}, reason={}, rootCauseType={}, message={}",
          name(), requestModel, taskName, elapsedMillis(startedAt), timedOut ? "read_timeout" : "provider_error",
          rootCause.getClass().getSimpleName(), safeMessage(rootCause));
      if (timedOut) {
        throw new IllegalStateException(
            "真实 LLM Provider 请求超过 " + readTimeoutSeconds + " 秒仍未返回", e);
      }
      throw new IllegalStateException("真实 LLM Provider 调用失败: " + e.getMessage(), e);
    }
  }

  private String extractTaskName(String userPrompt) {
    if (userPrompt == null || userPrompt.isBlank()) return "unknown";
    for (String line : userPrompt.lines().toList()) {
      String trimmed = line.trim();
      if (trimmed.startsWith("TASK:")) {
        String taskName = trimmed.substring("TASK:".length()).trim();
        return taskName.isBlank() ? "unknown" : taskName;
      }
    }
    return "unknown";
  }

  private long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private String safeMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) return "no_message";
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
  }

  private Throwable rootCause(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) current = current.getCause();
    return current;
  }

  private int maxTokens(String taskName) {
    return switch (taskName) {
      case "knowledge-structure" -> 2500;
      case "knowledge-relations-by-id" -> 1800;
      default -> 2000;
    };
  }

  private String modelForTask(String taskName) {
    return isKnowledgeExtractionTask(taskName) ? extractionModel : model;
  }

  private boolean isKnowledgeExtractionTask(String taskName) {
    return taskName != null && taskName.startsWith("knowledge-");
  }

  String readStreamingResponse(org.springframework.http.client.ClientHttpResponse response,
                               String requestModel, String taskName, long startedAt) throws java.io.IOException {
    if (response.getStatusCode().isError()) {
      throw new IllegalStateException(
          "LLM provider returned HTTP " + response.getStatusCode().value());
    }
    StringBuilder content = new StringBuilder();
    StringBuilder reasoningContent = new StringBuilder();
    StringBuilder rawResponse = new StringBuilder();
    boolean firstTokenLogged = false;
    boolean sawSseEvent = false;
    String finishReason = "unknown";
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        rawResponse.append(line).append('\n');
        String normalizedLine = line.stripLeading();
        if (normalizedLine.startsWith("\uFEFF")) {
          normalizedLine = normalizedLine.substring(1).stripLeading();
        }
        if (!normalizedLine.startsWith("data:")) continue;
        sawSseEvent = true;
        String data = normalizedLine.substring("data:".length()).trim();
        if (data.isBlank()) continue;
        if ("[DONE]".equals(data)) break;
        JsonNode event = objectMapper.readTree(data);
        JsonNode choice = event.path("choices").path(0);
        if (!choice.path("finish_reason").asText("").isBlank()) {
          finishReason = choice.path("finish_reason").asText();
        }
        String delta = firstNonBlank(
            choice.path("delta").path("content").asText(""),
            choice.path("message").path("content").asText(""),
            choice.path("text").asText(""),
            event.path("output_text").asText(""));
        String reasoningDelta = firstNonBlank(
            choice.path("delta").path("reasoning_content").asText(""),
            choice.path("message").path("reasoning_content").asText(""));
        if (!reasoningDelta.isEmpty()) reasoningContent.append(reasoningDelta);
        if (!delta.isEmpty()) {
          if (!firstTokenLogged) {
            log.info("AI response streaming provider={}, model={}, task={}, firstTokenMs={}",
                name(), requestModel, taskName, elapsedMillis(startedAt));
            firstTokenLogged = true;
          }
          content.append(delta);
        }
      }
    }
    if (!content.isEmpty()) {
      log.info("AI SSE response completed provider={}, model={}, task={}, finishReason={}, contentLength={}, reasoningLength={}",
          name(), requestModel, taskName, finishReason, content.length(), reasoningContent.length());
      return content.toString();
    }

    String raw = rawResponse.toString().trim();
    if (raw.isBlank()) return "";
    if (sawSseEvent) {
      String reasoning = reasoningContent.toString().trim();
      if (looksLikeJson(reasoning)) {
        log.warn("AI SSE response contained JSON only in reasoning_content provider={}, model={}, task={}",
            name(), requestModel, taskName);
        return stripJsonCodeFence(reasoning);
      }
      throw new IllegalStateException(
          "LLM SSE stream completed without answer content; finishReason=" + finishReason
              + ", reasoningLength=" + reasoning.length());
    }
    JsonNode root = objectMapper.readTree(raw);
    JsonNode choice = root.path("choices").path(0);
    return firstNonBlank(
        choice.path("message").path("content").asText(""),
        choice.path("delta").path("content").asText(""),
        choice.path("text").asText(""),
        root.path("output_text").asText(""));
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private boolean looksLikeJson(String value) {
    if (value == null || value.isBlank()) return false;
    String normalized = stripJsonCodeFence(value);
    return normalized.startsWith("{") || normalized.startsWith("[");
  }

  private String stripJsonCodeFence(String value) {
    String normalized = value.trim();
    if (normalized.startsWith("```json")) normalized = normalized.substring(7).trim();
    if (normalized.startsWith("```")) normalized = normalized.substring(3).trim();
    if (normalized.endsWith("```")) normalized = normalized.substring(0, normalized.length() - 3).trim();
    return normalized;
  }
}
