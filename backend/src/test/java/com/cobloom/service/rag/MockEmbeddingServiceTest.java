package com.cobloom.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MockEmbeddingServiceTest {
  private final MockEmbeddingService service = new MockEmbeddingService();

  @Test
  void emptyTextProducesZeroVector() {
    double[] vector = service.embed(null);

    assertEquals(128, vector.length);
    assertTrue(java.util.Arrays.stream(vector).allMatch(value -> value == 0));
  }

  @Test
  void identicalTextHasMaximumCosineSimilarity() {
    double[] vector = service.embed("Spring dependency injection");

    assertEquals(1.0, service.cosine(vector, vector), 0.000001);
  }

  @Test
  void serializedEmbeddingCanBeReadBack() {
    String embedding = service.embedToString("STM32 ADC interrupt");

    assertTrue(embedding.startsWith("["));
    assertEquals(1.0, service.cosine(embedding, embedding), 0.00001);
  }

  @Test
  void invalidSerializedEmbeddingDoesNotCrash() {
    assertEquals(0.0, service.cosine(new double[] {1.0}, "not-a-vector"));
  }
}
