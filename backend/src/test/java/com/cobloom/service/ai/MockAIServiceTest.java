package com.cobloom.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.service.knowledge.KnowledgeChunkCandidate;
import com.cobloom.service.knowledge.KnowledgeNodeReference;
import com.cobloom.service.knowledge.KnowledgeRelationCandidate;
import com.cobloom.service.knowledge.KnowledgeStructureResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockAIServiceTest {

  @Mock AiProvider provider;
  private MockAIService service;

  @BeforeEach
  void setUp() {
    service = new MockAIService(provider, new ObjectMapper());
  }

  @Test
  void summaryParsesFenceFiltersBlankListsAndFormatsMarkdown() {
    when(provider.complete(anyString(), anyString())).thenReturn("""
        ```json
        {"coreTopic":"RAG","mainConclusions":["Uses retrieval",""],
         "technicalKeywords":["vector",null],"relatedKnowledge":["graph"]}
        ```
        """);

    String summary = service.generateSummary("content");

    assertTrue(summary.contains("RAG"));
    assertTrue(summary.contains("Uses retrieval"));
    assertTrue(summary.contains("vector"));
    assertFalse(summary.contains("null"));
  }

  @Test
  void invalidSummaryFallsBackInsteadOfBreakingRecordFlow() {
    when(provider.complete(anyString(), anyString())).thenReturn("not-json");
    assertFalse(service.generateSummary("content").isBlank());
  }

  @Test
  void keywordsDeduplicateIgnoreBlankLimitAndFallbackOnInvalidJson() {
    when(provider.complete(anyString(), anyString())).thenReturn(
        "{\"keywords\":[\"RAG\",\"RAG\",\" \",\"Graph\",\"A\",\"B\",\"C\",\"D\",\"E\",\"F\",\"G\"]}",
        "invalid");

    List<String> keywords = service.extractKeywords("content");
    assertEquals(8, keywords.size());
    assertEquals("RAG", keywords.getFirst());
    assertEquals(1, service.extractKeywords("content").size());
  }

  @Test
  void structureFiltersBlankNamesDeduplicatesEvidenceAndCapsLists() {
    StringBuilder concepts = new StringBuilder();
    StringBuilder entities = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      if (i > 0) { concepts.append(','); entities.append(','); }
      concepts.append("{\"name\":\"C").append(i)
          .append("\",\"description\":\"d\",\"evidenceChunkIds\":[0,0,-1,1,2,3]}");
      entities.append("{\"name\":\"E").append(i)
          .append("\",\"type\":\"TECH\",\"description\":\"d\",\"evidenceChunkIds\":[0]}");
    }
    when(provider.complete(anyString(), anyString())).thenReturn(
        "{\"summary\":\"s\",\"concepts\":[" + concepts +
            ", {\"name\":\" \",\"evidenceChunkIds\":[]}],\"entities\":[" + entities + "]}");

    KnowledgeStructureResult result = service.extractStructure("[C0] text");

    assertEquals(12, result.concepts().size());
    assertEquals(14, result.entities().size());
    assertEquals(List.of(0, 1, 2), result.concepts().getFirst().evidenceChunkIds());
  }

  @Test
  void invalidStructureJsonIsObservableFailure() {
    when(provider.complete(anyString(), anyString())).thenReturn("[]");
    assertThrows(IllegalStateException.class, () -> service.extractStructure("text"));
  }

  @Test
  void relationsValidateIdsEvidenceSelfLinksAndLimit() {
    when(provider.complete(anyString(), anyString())).thenReturn("""
        {"relations":[
          {"sourceId":"N1","targetId":"N2","relationType":"related_to","evidenceChunkId":0,"evidenceQuote":"evidence"},
          {"sourceId":"N9","targetId":"N2","relationType":"related_to","evidenceChunkId":0,"evidenceQuote":"bad source"},
          {"sourceId":"N1","targetId":"N1","relationType":"related_to","evidenceChunkId":0,"evidenceQuote":"self"},
          {"sourceId":"N1","targetId":"N2","relationType":"related_to","evidenceChunkId":9,"evidenceQuote":"bad chunk"},
          {"sourceId":"N1","targetId":"N2","relationType":"related_to","evidenceChunkId":0,"evidenceQuote":" "}
        ]}
        """);
    List<KnowledgeNodeReference> nodes = List.of(
        new KnowledgeNodeReference("N1", "Spring", "ENTITY"),
        new KnowledgeNodeReference("N2", "DI", "CONCEPT"),
        new KnowledgeNodeReference(null, "ignored", "CONCEPT"));
    List<KnowledgeChunkCandidate> chunks = List.of(
        new KnowledgeChunkCandidate(0, "", "", "evidence", ""),
        new KnowledgeChunkCandidate(null, "", "", "ignored", ""));

    List<KnowledgeRelationCandidate> result = service.extractRelations(nodes, chunks);

    assertEquals(1, result.size());
    assertEquals("Spring", result.getFirst().source());
    assertTrue(result.getFirst().evidence().startsWith("[C0]"));
  }

  @Test
  void relationsSkipProviderWhenCandidatesCannotFormRelation() {
    assertTrue(service.extractRelations(null, null).isEmpty());
    assertTrue(service.extractRelations(List.of(new KnowledgeNodeReference("N1", "one", "C")), List.of()).isEmpty());
  }

  @Test
  void answerHandlesEmptyContextSanitizesTemplateAndFallbacksOnBlank() {
    assertFalse(service.answer("q", null).isBlank());
    when(provider.complete(anyString(), anyString())).thenReturn(
        "TASK:qa-answer {chunks} {question} useful answer", "   ");

    assertEquals("useful answer", service.answer("q", List.of("context")));
    assertFalse(service.answer("q", List.of("context")).isBlank());
  }

  @Test
  void feedbackSupportsAllStylesAndNullRecord() {
    GrowthRecord record = new GrowthRecord();
    record.title = "Sprint";
    assertFalse(service.companionFeedback(record, "rational").isBlank());
    assertFalse(service.companionFeedback(record, "creative").isBlank());
    assertFalse(service.companionFeedback(record, "gentle").isBlank());
    assertFalse(service.companionFeedback(null, null).isBlank());
  }
}
