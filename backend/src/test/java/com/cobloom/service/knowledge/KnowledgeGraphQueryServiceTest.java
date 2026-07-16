package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.dto.KnowledgeNodeDetailDTO;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphQueryServiceTest {

  @Mock KnowledgeNodeMapper nodeMapper;
  @Mock KnowledgeRelationMapper relationMapper;
  @Mock GrowthRecordMapper recordMapper;
  private KnowledgeGraphQueryService service;

  @BeforeEach
  void setUp() {
    service = new KnowledgeGraphQueryService(nodeMapper, relationMapper, recordMapper);
  }

  @Test
  void globalGraphHidesOrphansFoldsTitleConceptMergesEvidenceAndCountsSources() {
    KnowledgeNode source = node(1L, "NOTE", "STM", "stm", 10L);
    KnowledgeNode concept = node(2L, "CONCEPT", " Interrupt ", "interrupt", 10L);
    KnowledgeNode matchingNote = node(3L, "NOTE", "Interrupt", "interrupt", 20L);
    KnowledgeNode entity = node(4L, null, "NVIC", null, 10L);
    KnowledgeNode orphan = node(5L, "CONCEPT", "orphan", "orphan", 10L);
    matchingNote.updatedAt = LocalDateTime.now();
    List<KnowledgeRelation> relations = List.of(
        relation(11L, 1L, 2L, "contains", "first", 10L),
        relation(12L, 1L, 2L, "contains", "second", 10L),
        relation(13L, 2L, 4L, "related_to", "link", 10L),
        relation(14L, 1L, 99L, "related_to", "missing", 10L));
    when(relationMapper.selectList(any(Wrapper.class))).thenReturn(relations);
    when(nodeMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(source, concept, matchingNote, entity, orphan), List.of(source, matchingNote));
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record(10L, "STM"), record(20L, "Interrupt")));

    KnowledgeGraphDTO graph = service.graph(1L);

    assertEquals(3, graph.nodes.size());
    assertEquals(2, graph.edges.size());
    assertTrue(graph.nodes.stream().noneMatch(n -> Long.valueOf(2L).equals(n.knowledgeNodeId)));
    assertTrue(graph.nodes.stream().noneMatch(n -> "orphan".equals(n.name)));
    assertTrue(graph.edges.stream().anyMatch(e -> e.evidence.contains("first\n---\nsecond")));
    assertTrue(graph.nodes.stream().filter(n -> Long.valueOf(3L).equals(n.knowledgeNodeId))
        .allMatch(n -> n.relationCount == 2 && n.sourceCount == 2));
    assertTrue(graph.nodes.stream().filter(n -> Long.valueOf(4L).equals(n.knowledgeNodeId))
        .allMatch(n -> "concept".equals(n.type)));
  }

  @Test
  void noteGraphRejectsForeignNoteAndHandlesMissingKnowledgeNode() {
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.noteGraph(1L, 10L));

    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record(10L, "note"));
    when(nodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    KnowledgeGraphDTO empty = service.noteGraph(1L, 10L);
    assertTrue(empty.nodes.isEmpty());
    assertTrue(empty.edges.isEmpty());
  }

  @Test
  void noteGraphLoadsProjectionMembersAndConnectedNodes() {
    KnowledgeNode note = node(1L, "NOTE", "note", "note", 10L);
    KnowledgeNode target = node(2L, "ENTITY", "target", "target", 10L);
    KnowledgeRelation edge = relation(1L, 1L, 2L, "related_to", "e", 10L);
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record(10L, "note"));
    when(nodeMapper.selectOne(any(Wrapper.class))).thenReturn(note);
    when(nodeMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(note), List.of(note, target), List.of(note));
    when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(edge));
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record(10L, "note")));

    KnowledgeGraphDTO graph = service.noteGraph(1L, 10L);

    assertEquals(2, graph.nodes.size());
    assertEquals(1, graph.edges.size());
  }

  @Test
  void neighborsNormalizesDepthFiltersRelationsAndStopsAtLoadedFrontier() {
    KnowledgeNode root = node(1L, "CONCEPT", "root", "root", 10L);
    KnowledgeNode target = node(2L, "ENTITY", "target", "target", 11L);
    KnowledgeRelation allowed = relation(1L, 1L, 2L, "related_to", "e", 10L);
    KnowledgeRelation filtered = relation(2L, 1L, 3L, "contains", "x", 10L);
    when(nodeMapper.selectOne(any(Wrapper.class))).thenReturn(root);
    when(nodeMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(), List.of(target), List.of());
    when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(allowed, filtered));
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record(10L, "a"), record(11L, "b")));

    KnowledgeGraphDTO graph = service.neighbors(1L, 1L, 0, " related_to, ");

    assertEquals(2, graph.nodes.size());
    assertEquals(1, graph.edges.size());
  }

  @Test
  void nodeDetailPrefersNewestMatchingNoteAndBuildsSourceSnippet() {
    KnowledgeNode concept = node(1L, "CONCEPT", "Java", "java", 10L);
    KnowledgeNode oldNote = node(2L, "NOTE", "Java", "java", 20L);
    KnowledgeNode newNote = node(3L, "NOTE", "Java", "java", 30L);
    oldNote.updatedAt = LocalDateTime.now().minusDays(1);
    newNote.updatedAt = LocalDateTime.now();
    KnowledgeNode neighbor = node(4L, "ENTITY", "JVM", "jvm", 30L);
    KnowledgeRelation relation = relation(1L, 3L, 4L, "related_to", null, 30L);
    when(nodeMapper.selectOne(any(Wrapper.class))).thenReturn(concept);
    when(nodeMapper.selectList(any(Wrapper.class)))
        .thenReturn(List.of(concept, oldNote, newNote), List.of(newNote, neighbor), List.of(newNote));
    when(relationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation));
    GrowthRecord source = record(30L, "Java");
    source.summary = " ";
    source.content = "x".repeat(160);
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(source));

    KnowledgeNodeDetailDTO detail = service.nodeDetail(1L, 1L);

    assertEquals(3L, detail.node.knowledgeNodeId);
    assertEquals(1, detail.sources.size());
    assertEquals(140, detail.sources.getFirst().snippet.length());
    assertTrue(detail.relatedQuestions.isEmpty());
  }

  @Test
  void neighborsAndDetailRejectMissingNode() {
    when(nodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.neighbors(1L, 9L, null, null));
    assertThrows(IllegalArgumentException.class, () -> service.nodeDetail(1L, 9L));
  }

  private KnowledgeNode node(Long id, String type, String name, String normalized, Long recordId) {
    KnowledgeNode node = new KnowledgeNode();
    node.id = id;
    node.userId = 1L;
    node.nodeType = type;
    node.name = name;
    node.normalizedName = normalized;
    node.description = name + " description";
    node.sourceRecordId = recordId;
    node.updatedAt = LocalDateTime.now();
    return node;
  }

  private KnowledgeRelation relation(Long id, Long source, Long target, String type, String evidence, Long recordId) {
    KnowledgeRelation relation = new KnowledgeRelation();
    relation.id = id;
    relation.userId = 1L;
    relation.sourceNodeId = source;
    relation.targetNodeId = target;
    relation.relationType = type;
    relation.evidenceText = evidence;
    relation.weight = 1.0;
    relation.confidence = 0.9;
    relation.sourceRecordId = recordId;
    return relation;
  }

  private GrowthRecord record(Long id, String title) {
    GrowthRecord record = new GrowthRecord();
    record.id = id;
    record.userId = 1L;
    record.title = title;
    record.summary = title + " summary";
    record.content = title + " content";
    record.createdAt = LocalDateTime.now();
    return record;
  }
}
