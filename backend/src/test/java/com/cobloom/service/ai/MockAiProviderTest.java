package com.cobloom.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {

  private final MockAiProvider provider = new MockAiProvider();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void exposesProviderNameAndSafeUnknownTask() throws Exception {
    assertEquals("mock", provider.name());
    assertTrue(mapper.readTree(provider.complete(null, "no task")).isObject());
  }

  @Test
  void producesStructuredSummaryKeywordsAndNodes() throws Exception {
    String content = "# Spring Framework\nSpring Framework uses RetrievalService and embedding for knowledge retrieval.";
    JsonNode summary = mapper.readTree(provider.complete("", "TASK:knowledge-summary\n" + content));
    JsonNode keywords = mapper.readTree(provider.complete("", "TASK:knowledge-keywords\n" + content));
    JsonNode structure = mapper.readTree(provider.complete("", "TASK:knowledge-structure\n[C3] " + content));

    assertFalse(summary.path("coreTopic").asText().isBlank());
    assertTrue(keywords.path("keywords").isArray());
    assertTrue(structure.path("concepts").isArray());
    assertTrue(structure.path("entities").isArray());
  }

  @Test
  void relationGenerationRequiresTwoIdsAndEvidenceChunk() throws Exception {
    String valid = "TASK:knowledge-relations-by-id\nN1 | ENTITY | Spring\nN2 | CONCEPT | DI\n\n[C2] Spring uses DI";
    assertEquals(1, mapper.readTree(provider.complete("", valid)).path("relations").size());
    assertEquals(0, mapper.readTree(provider.complete("", "TASK:knowledge-relations-by-id\nN1 | E | one"))
        .path("relations").size());
  }

  @Test
  void qaReturnsEvidenceAndHandlesBlankPrompt() {
    assertFalse(provider.complete("", "TASK:qa-answer\nuse this evidence").isBlank());
    assertFalse(provider.complete("", "TASK:qa-answer\n").isBlank());
  }
}
