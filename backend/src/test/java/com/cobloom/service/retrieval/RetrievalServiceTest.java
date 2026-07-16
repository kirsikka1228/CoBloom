package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RetrievalServiceTest {
  private VectorRetriever vectorRetriever;
  private KeywordRetriever keywordRetriever;
  private GraphRetriever graphRetriever;
  private QueryUnderstandingService queryUnderstandingService;
  private RetrievalReranker reranker;
  private RetrievalService service;

  @BeforeEach
  void setUp() {
    vectorRetriever = mock(VectorRetriever.class);
    keywordRetriever = mock(KeywordRetriever.class);
    graphRetriever = mock(GraphRetriever.class);
    queryUnderstandingService = mock(QueryUnderstandingService.class);
    reranker = mock(RetrievalReranker.class);
    service = new RetrievalService(vectorRetriever, keywordRetriever, graphRetriever,
        queryUnderstandingService, reranker);
  }

  @Test
  void sameChunkFromVectorAndKeywordIsMergedBeforeReranking() {
    ExpandedQuery query = new ExpandedQuery("ADC", List.of("ADC"), List.of(), List.of(), "ADC");
    RetrievalCandidate vector = candidate("vector", 1L, null, 11L, 0.8, 0.0, 0.7);
    RetrievalCandidate keyword = candidate("keyword", 1L, null, 11L, 0.0, 0.9, 0.75);
    when(queryUnderstandingService.expand("ADC")).thenReturn(query);
    when(vectorRetriever.retrieve(1L, query)).thenReturn(List.of(vector));
    when(keywordRetriever.retrieve(1L, query)).thenReturn(List.of(keyword));
    when(graphRetriever.retrieve(eq(1L), any())).thenReturn(List.of());
    ArgumentCaptor<List<RetrievalCandidate>> captor = ArgumentCaptor.forClass(List.class);
    when(reranker.rerank(eq(query), captor.capture(), eq(8)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    RetrievalResult result = service.retrieve(1L, "ADC");

    assertEquals(1, result.candidates().size());
    RetrievalCandidate merged = captor.getValue().getFirst();
    assertEquals("vector+keyword", merged.source());
    assertEquals(0.8, merged.vectorScore());
    assertEquals(0.9, merged.keywordScore());
    assertTrue(merged.score() > 0.75);
  }

  @Test
  void nodeRecordAndNullCandidatesUseIndependentMergeKeys() {
    ExpandedQuery query = new ExpandedQuery("graph", List.of("graph"), List.of(), List.of(), "graph");
    RetrievalCandidate node = candidate("keyword_node", 2L, 20L, null, 0, 0.8, 0.7);
    RetrievalCandidate record = candidate("keyword_record", 3L, null, null, 0, 0.7, 0.6);
    when(queryUnderstandingService.expand("graph")).thenReturn(query);
    when(vectorRetriever.retrieve(1L, query)).thenReturn(List.of());
    when(keywordRetriever.retrieve(1L, query)).thenReturn(List.of(node, record));
    when(graphRetriever.retrieve(eq(1L), any())).thenReturn(java.util.Collections.singletonList(null));
    when(reranker.rerank(eq(query), any(), eq(8)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    RetrievalResult result = service.retrieve(1L, "graph");

    assertEquals(2, result.candidates().size());
    assertEquals("graph", result.question());
  }

  private RetrievalCandidate candidate(String source, Long recordId, Long nodeId, Long chunkId,
                                       double vectorScore, double keywordScore, double score) {
    return new RetrievalCandidate(source, recordId, nodeId, chunkId, 0, "content", "snippet",
        vectorScore, keywordScore, score);
  }
}
