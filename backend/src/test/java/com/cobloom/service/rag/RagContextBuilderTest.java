package com.cobloom.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.service.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagContextBuilderTest {
  private GrowthRecordMapper recordMapper;
  private KnowledgeNodeMapper nodeMapper;
  private RagContextBuilder builder;

  @BeforeEach
  void setUp() {
    recordMapper = mock(GrowthRecordMapper.class);
    nodeMapper = mock(KnowledgeNodeMapper.class);
    builder = new RagContextBuilder(recordMapper, nodeMapper);
  }

  @Test
  void nullAndEmptyCandidatesProduceNoContexts() {
    assertTrue(builder.build(null).isEmpty());
    assertTrue(builder.build(List.of()).isEmpty());
  }

  @Test
  void chunkCandidateKeepsTraceableRecordAndChunkIds() {
    when(recordMapper.selectById(1L)).thenReturn(record(1L, "ADC Note", "summary"));
    RetrievalCandidate candidate = candidate("vector", 1L, null, 10L, "ADC\n conversion", 0.82);

    RagContextItem context = builder.build(List.of(candidate)).getFirst();

    assertEquals(1L, context.recordId());
    assertEquals(10L, context.chunkId());
    assertEquals("ADC Note", context.title());
    assertEquals("ADC conversion", context.content());
  }

  @Test
  void nodeCandidateUsesNodeDescriptionAndItsSourceRecord() {
    KnowledgeNode node = new KnowledgeNode();
    node.id = 7L;
    node.sourceRecordId = 2L;
    node.description = "Interrupt handling concept";
    when(nodeMapper.selectById(7L)).thenReturn(node);
    when(recordMapper.selectById(2L)).thenReturn(record(2L, "STM32", "summary"));
    RetrievalCandidate candidate = candidate("graph", null, 7L, null, "", 0.66);

    RagContextItem context = builder.build(List.of(candidate)).getFirst();

    assertEquals(2L, context.recordId());
    assertEquals(7L, context.knowledgeNodeId());
    assertTrue(context.content().contains("Note title: STM32"));
    assertTrue(context.content().contains("Knowledge description: Interrupt handling concept"));
  }

  @Test
  void recordCandidateUsesTitleAndSummary() {
    when(recordMapper.selectById(3L)).thenReturn(record(3L, "RAG", "Hybrid retrieval summary"));
    RetrievalCandidate candidate = candidate("keyword", 3L, null, null, "", 0.71);

    RagContextItem context = builder.build(List.of(candidate)).getFirst();

    assertEquals("RAG", context.title());
    assertTrue(context.content().contains("Summary: Hybrid retrieval summary"));
  }

  @Test
  void invalidCandidatesAreSkippedAndContextCountIsLimited() {
    List<RetrievalCandidate> candidates = new ArrayList<>();
    candidates.add(null);
    candidates.add(candidate("vector", null, null, 99L, "missing record", 0.9));
    for (long id = 1; id <= 10; id++) {
      when(recordMapper.selectById(id)).thenReturn(record(id, "Note " + id, "Summary " + id));
      candidates.add(candidate("keyword", id, null, null, "", 0.5));
    }

    List<RagContextItem> contexts = builder.build(candidates);

    assertEquals(8, contexts.size());
    assertEquals(1L, contexts.getFirst().recordId());
    assertEquals(8L, contexts.getLast().recordId());
  }

  private GrowthRecord record(Long id, String title, String summary) {
    GrowthRecord record = new GrowthRecord();
    record.id = id;
    record.title = title;
    record.summary = summary;
    return record;
  }

  private RetrievalCandidate candidate(String source, Long recordId, Long nodeId, Long chunkId,
                                       String content, double score) {
    return new RetrievalCandidate(source, recordId, nodeId, chunkId, 0, content, content,
        score, score, score);
  }
}
