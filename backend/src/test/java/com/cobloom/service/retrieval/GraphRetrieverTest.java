package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.service.knowledge.RelationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphRetrieverTest {
  @Mock
  private KnowledgeNodeMapper nodeMapper;
  @Mock
  private KnowledgeRelationMapper relationMapper;

  @Test
  void validatedRelationIsNotFilteredByLegacyConfidenceValue() {
    Long userId = 1L;
    KnowledgeNode seed = node(10L, userId, "NOTE", "STM32", 100L);
    KnowledgeNode neighbor = node(20L, userId, "CONCEPT", "中断", 200L);
    KnowledgeRelation relation = relation(seed.id, neighbor.id, 100L,
        RelationType.RELATED_TO.value(), 0.7, 0.5, "[C3] STM32通过中断响应外设事件");

    when(relationMapper.selectList(any())).thenReturn(List.of(relation));
    when(nodeMapper.selectById(seed.id)).thenReturn(seed);
    when(nodeMapper.selectById(neighbor.id)).thenReturn(neighbor);

    GraphRetriever retriever = new GraphRetriever(nodeMapper, relationMapper);
    List<RetrievalCandidate> result = retriever.retrieve(userId, List.of(
        new RetrievalCandidate("keyword", 100L, seed.id, null, null,
            "STM32", "STM32", 0, 1, 1)));

    assertEquals(1, result.size());
    assertEquals(neighbor.id, result.getFirst().knowledgeNodeId());
    assertTrue(result.getFirst().score() > 0);
  }

  @Test
  void relationWithoutEvidenceIsStillRejected() {
    Long userId = 1L;
    KnowledgeNode seed = node(11L, userId, "NOTE", "STM32", 101L);
    KnowledgeNode neighbor = node(21L, userId, "CONCEPT", "中断", 201L);
    KnowledgeRelation relation = relation(seed.id, neighbor.id, 101L,
        RelationType.RELATED_TO.value(), 0.7, 0.9, "");

    when(relationMapper.selectList(any())).thenReturn(List.of(relation));
    when(nodeMapper.selectById(seed.id)).thenReturn(seed);

    GraphRetriever retriever = new GraphRetriever(nodeMapper, relationMapper);
    List<RetrievalCandidate> result = retriever.retrieve(userId, List.of(
        new RetrievalCandidate("keyword", 101L, seed.id, null, null,
            "STM32", "STM32", 0, 1, 1)));

    assertTrue(result.isEmpty());
  }

  private KnowledgeNode node(Long id, Long userId, String type, String name, Long recordId) {
    KnowledgeNode node = new KnowledgeNode();
    node.id = id;
    node.userId = userId;
    node.nodeType = type;
    node.name = name;
    node.description = name;
    node.sourceRecordId = recordId;
    return node;
  }

  private KnowledgeRelation relation(Long sourceId, Long targetId, Long recordId, String type,
                                     Double weight, Double confidence, String evidence) {
    KnowledgeRelation relation = new KnowledgeRelation();
    relation.id = sourceId + targetId;
    relation.userId = 1L;
    relation.sourceNodeId = sourceId;
    relation.targetNodeId = targetId;
    relation.sourceRecordId = recordId;
    relation.relationType = type;
    relation.weight = weight;
    relation.confidence = confidence;
    relation.evidenceText = evidence;
    return relation;
  }
}
