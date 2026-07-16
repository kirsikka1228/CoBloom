package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalRerankerTest {
  private GrowthRecordMapper recordMapper;
  private KnowledgeNodeMapper nodeMapper;
  private RetrievalReranker reranker;

  @BeforeEach
  void setUp() {
    recordMapper = mock(GrowthRecordMapper.class);
    nodeMapper = mock(KnowledgeNodeMapper.class);
    reranker = new RetrievalReranker(recordMapper, nodeMapper);
  }

  @Test
  void nullInputsAndEmptyCandidatesAreSafe() {
    assertTrue(reranker.rerank(null, List.of(), 8).isEmpty());
    assertTrue(reranker.rerank(query("ADC", "ADC"), null, 8).isEmpty());
    assertTrue(reranker.rerank(query("ADC", "ADC"), List.of(), 8).isEmpty());
  }

  @Test
  void exactAcronymAndTitleMatchPromoteRelevantCandidate() {
    when(recordMapper.selectById(1L)).thenReturn(record("STM32 ADC", "ADC 模数转换器", "conversion"));
    KnowledgeNode node = new KnowledgeNode();
    node.name = "ADC";
    node.normalizedName = "adc";
    node.description = "Analog Digital Converter";
    when(nodeMapper.selectById(10L)).thenReturn(node);
    RetrievalCandidate candidate = candidate("vector+keyword", 1L, 10L, 0.8, 0.8, 0.7);

    List<RetrievalCandidate> result = reranker.rerank(query("STM32 ADC", "STM32", "ADC"),
        List.of(candidate), 8);

    assertEquals(1, result.size());
    assertTrue(result.getFirst().score() >= 0.7);
  }

  @Test
  void topicMismatchAndMissingCoreTermRemoveIrrelevantCandidate() {
    when(recordMapper.selectById(2L)).thenReturn(record("ESP32 WiFi", "ESP32 network", "wireless"));
    RetrievalCandidate candidate = candidate("vector", 2L, null, 0.6, 0.1, 0.6);

    List<RetrievalCandidate> result = reranker.rerank(query("STM32 ADC", "STM32", "ADC"),
        List.of(candidate), 8);

    assertTrue(result.isEmpty());
  }

  @Test
  void resultIsSortedAndLimited() {
    when(recordMapper.selectById(1L)).thenReturn(record("ADC basics", "ADC", "ADC content"));
    when(recordMapper.selectById(2L)).thenReturn(record("ADC advanced", "ADC", "ADC content"));
    RetrievalCandidate lower = candidate("keyword", 1L, null, 0.4, 0.5, 0.5);
    RetrievalCandidate higher = candidate("keyword", 2L, null, 0.9, 0.9, 0.9);

    List<RetrievalCandidate> result = reranker.rerank(query("ADC", "ADC"),
        List.of(lower, higher), 1);

    assertEquals(1, result.size());
    assertEquals(2L, result.getFirst().recordId());
  }

  @Test
  void graphCandidateMustPassHigherThreshold() {
    when(recordMapper.selectById(3L)).thenReturn(record("general note", "", "关系"));
    RetrievalCandidate candidate = candidate("graph", 3L, null, 0.0, 0.0, 0.8);

    List<RetrievalCandidate> result = reranker.rerank(query("关系", "关系"), List.of(candidate), 8);

    assertTrue(result.isEmpty());
  }

  private ExpandedQuery query(String original, String... terms) {
    return new ExpandedQuery(original, List.of(terms), List.of("模数转换器"), List.of(), original);
  }

  private GrowthRecord record(String title, String keywords, String summary) {
    GrowthRecord record = new GrowthRecord();
    record.title = title;
    record.keywords = keywords;
    record.summary = summary;
    return record;
  }

  private RetrievalCandidate candidate(String source, Long recordId, Long nodeId,
                                       double vectorScore, double keywordScore, double score) {
    return new RetrievalCandidate(source, recordId, nodeId, null, null, "ADC content", "ADC snippet",
        vectorScore, keywordScore, score);
  }
}
