package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeywordRetrieverTest {
  private KnowledgeNodeMapper nodeMapper;
  private KnowledgeRelationMapper relationMapper;
  private GrowthRecordMapper recordMapper;
  private KeywordRetriever retriever;

  @BeforeEach
  void setUp() {
    nodeMapper = mock(KnowledgeNodeMapper.class);
    relationMapper = mock(KnowledgeRelationMapper.class);
    recordMapper = mock(GrowthRecordMapper.class);
    retriever = new KeywordRetriever(nodeMapper, relationMapper, recordMapper);
  }

  @Test
  void emptyQueryReturnsWithoutDatabaseAccess() {
    ExpandedQuery query = new ExpandedQuery("如何", List.of(), List.of(), List.of(), "");

    assertTrue(retriever.retrieve(1L, query).isEmpty());
    verifyNoInteractions(nodeMapper, relationMapper, recordMapper);
  }

  @Test
  void matchingNodeAndRecordBecomeRankedCandidates() {
    KnowledgeNode concept = node(10L, 1L, "ADC", "模数转换器", 2L);
    KnowledgeNode irrelevant = node(11L, 1L, "PWM", "脉宽调制", 2L);
    GrowthRecord record = record(2L, "STM32 ADC", "ADC,模数转换器", "ADC summary");
    KnowledgeNode noteNode = node(20L, 1L, "STM32 ADC", "", 2L);
    when(nodeMapper.selectList(any())).thenReturn(List.of(concept, irrelevant));
    when(recordMapper.selectList(any())).thenReturn(List.of(record));
    when(nodeMapper.selectOne(any())).thenReturn(noteNode);
    ExpandedQuery query = new ExpandedQuery("ADC是什么", List.of("ADC"),
        List.of("模数转换器"), List.of(), "ADC 模数转换器");

    List<RetrievalCandidate> candidates = retriever.retrieve(1L, query);

    assertEquals(2, candidates.size());
    assertTrue(candidates.stream().anyMatch(candidate -> "keyword_node".equals(candidate.source())));
    assertTrue(candidates.stream().anyMatch(candidate -> "keyword_record".equals(candidate.source())));
  }

  @Test
  void sharedNodeCanResolveItsSourceThroughRelation() {
    KnowledgeNode shared = node(30L, 1L, "中断", "中断处理", null);
    KnowledgeRelation relation = new KnowledgeRelation();
    relation.sourceRecordId = 5L;
    when(nodeMapper.selectList(any())).thenReturn(List.of(shared));
    when(recordMapper.selectList(any())).thenReturn(List.of());
    when(relationMapper.selectOne(any())).thenReturn(relation);
    ExpandedQuery query = new ExpandedQuery("中断", List.of("中断"), List.of(), List.of(), "中断");

    List<RetrievalCandidate> candidates = retriever.retrieve(1L, query);

    assertEquals(1, candidates.size());
    assertEquals(5L, candidates.getFirst().recordId());
    assertEquals(30L, candidates.getFirst().knowledgeNodeId());
  }

  @Test
  void matchingRecordWithoutNoteNodeStillKeepsRecordTraceability() {
    when(nodeMapper.selectList(any())).thenReturn(List.of());
    when(recordMapper.selectList(any())).thenReturn(
        List.of(record(8L, "RAG retrieval", null, null)));
    when(nodeMapper.selectOne(any())).thenReturn(null);
    ExpandedQuery query = new ExpandedQuery("RAG", List.of("RAG"), List.of(), List.of(), "RAG");

    RetrievalCandidate candidate = retriever.retrieve(1L, query).getFirst();

    assertEquals(8L, candidate.recordId());
    assertEquals(null, candidate.knowledgeNodeId());
    assertTrue(candidate.content().contains("笔记：RAG retrieval"));
  }

  private KnowledgeNode node(Long id, Long userId, String name, String description, Long recordId) {
    KnowledgeNode node = new KnowledgeNode();
    node.id = id;
    node.userId = userId;
    node.nodeType = "CONCEPT";
    node.name = name;
    node.description = description;
    node.sourceRecordId = recordId;
    return node;
  }

  private GrowthRecord record(Long id, String title, String keywords, String summary) {
    GrowthRecord record = new GrowthRecord();
    record.id = id;
    record.title = title;
    record.keywords = keywords;
    record.summary = summary;
    return record;
  }
}
