package com.cobloom.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextSimilarityTest {
  private final TextSimilarity similarity = new TextSimilarity();

  @Test
  void emptyTextHasNoSimilarity() {
    assertEquals(0.0, similarity.score(null, "knowledge"));
    assertEquals(0.0, similarity.score("", ""));
  }

  @Test
  void identicalTextHasMaximumSimilarity() {
    assertEquals(1.0, similarity.score("知识图谱 关系", "知识图谱 关系"), 0.000001);
  }

  @Test
  void relatedChineseTextGetsPositiveSimilarity() {
    double score = similarity.score("知识图谱实体关系", "知识图谱关系抽取");

    assertTrue(score > 0);
  }
}
