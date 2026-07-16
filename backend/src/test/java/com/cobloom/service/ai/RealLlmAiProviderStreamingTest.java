package com.cobloom.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RealLlmAiProviderStreamingTest {

  private final RealLlmAiProvider provider = new RealLlmAiProvider(
      new ObjectMapper(), "https://example.invalid", "test-key", "test-model", "extraction-model", 1, 1);

  @Test
  void readsSseWithLeadingBomWhitespaceAndReasoningChunks() throws Exception {
    String body = "\uFEFF  data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}\n\n"
        + " data: {\"choices\":[{\"delta\":{\"content\":\"{\\\"summary\\\":\"}}]}\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"\\\"ok\\\"}\"}}]}\n"
        + "data: [DONE]\n";
    MockClientHttpResponse response = new MockClientHttpResponse(
        body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);

    String result = provider.readStreamingResponse(
        response, "extraction-model", "knowledge-structure", System.nanoTime());

    assertEquals("{\"summary\":\"ok\"}", result);
  }

  @Test
  void readsNonStreamingJsonResponse() throws Exception {
    String body = "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}";
    MockClientHttpResponse response = new MockClientHttpResponse(
        body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);

    String result = provider.readStreamingResponse(response, "test-model", "chat", System.nanoTime());

    assertEquals("answer", result);
  }

  @Test
  void rejectsHttpErrorAndEmptyBody() throws Exception {
    MockClientHttpResponse error = new MockClientHttpResponse(new byte[0], HttpStatus.BAD_GATEWAY);
    assertThrows(IllegalStateException.class,
        () -> provider.readStreamingResponse(error, "model", "task", System.nanoTime()));

    MockClientHttpResponse empty = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    assertEquals("", provider.readStreamingResponse(empty, "model", "task", System.nanoTime()));
  }

  @Test
  void recoversJsonThatProviderPlacedOnlyInReasoningContent() throws Exception {
    String body = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"```json\\n{\\\"ok\\\":true}\\n```\"},\"finish_reason\":\"stop\"}]}\n"
        + "data: [DONE]\n";
    MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
    assertEquals("{\"ok\":true}",
        provider.readStreamingResponse(response, "model", "knowledge-structure", System.nanoTime()));
  }

  @Test
  void reasoningWithoutAnswerIsReportedAsObservableFailure() {
    String body = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking only\"},\"finish_reason\":\"length\"}]}\n"
        + "data: [DONE]\n";
    MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> provider.readStreamingResponse(response, "model", "task", System.nanoTime()));
    assertTrue(error.getMessage().contains("reasoningLength"));
  }

  @Test
  void completeFailsFastWhenApiKeyIsMissing() {
    RealLlmAiProvider missingKey = new RealLlmAiProvider(
        new ObjectMapper(), "https://example.invalid", " ", "chat", "extract", 1, 1);
    assertEquals("real", missingKey.name());
    assertThrows(IllegalStateException.class,
        () -> missingKey.complete("system", "TASK:knowledge-structure\ncontent"));
  }
}
