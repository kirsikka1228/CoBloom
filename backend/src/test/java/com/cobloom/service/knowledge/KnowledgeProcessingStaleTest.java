package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.entity.RecordChunk;
import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.dto.KnowledgeNodeDetailDTO;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.mapper.RecordChunkMapper;
import com.cobloom.service.RecordService;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.rag.EmbeddingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-processing-stale-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.sql.init.mode=always",
    "cobloom.jwt-secret=test-only-jwt-secret-with-at-least-32-bytes"
})
class KnowledgeProcessingStaleTest {
  @Autowired
  private RecordService recordService;
  @Autowired
  private GrowthRecordMapper recordMapper;
  @Autowired
  private RecordChunkMapper chunkMapper;
  @Autowired
  private KnowledgeNodeMapper nodeMapper;
  @Autowired
  private KnowledgeRelationMapper relationMapper;
  @Autowired
  private KnowledgeGraphQueryService graphQueryService;
  @Autowired
  private StructuredKnowledgeService structuredKnowledgeService;

  @MockBean
  private AIService aiService;
  @MockBean
  private EmbeddingService embeddingService;

  @Test
  void markdownUploadPersistsChunksAndGraphWhenOnlySummaryUpdatesDuringCompile() throws Exception {
    Long userId = 1L;
    String title = "phase4-stale-test-" + System.nanoTime();
    String markdown = """
        # STM32 中断

        ADC 用于模拟量转换。
        GPIO 支持输入输出模式。
        """;

    when(embeddingService.embedToString(anyString())).thenReturn("[0.1,0.2,0.3]");
    when(aiService.extractStructure(anyString())).thenAnswer(invocation -> {
      GrowthRecord record = recordMapper.selectOne(new QueryWrapper<GrowthRecord>()
          .eq("user_id", userId)
          .eq("title", title));
      if (record != null) {
        record.summary = "manual summary during compile";
        record.updatedAt = LocalDateTime.now().plusSeconds(5);
        recordMapper.updateById(record);
      }
      return new KnowledgeStructureResult(
          "compiled summary",
          List.of(
              new KnowledgeConceptCandidate("ADC", "analog to digital converter", List.of(0)),
              new KnowledgeConceptCandidate("GPIO", "general purpose input output", List.of(0)),
              new KnowledgeConceptCandidate("中断", "interrupt mechanism", List.of(0))
          ),
          List.of(new KnowledgeEntityCandidate("STM32", "MCU", "microcontroller", List.of(0)))
      );
    });
    when(aiService.extractRelations(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of(
            new KnowledgeRelationCandidate("STM32", "ADC", "contains", "[C0] ADC 用于模拟量转换"),
            new KnowledgeRelationCandidate("STM32", "GPIO", "contains", "[C0] GPIO 支持输入输出模式")
        ));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        title + ".md",
        "text/markdown",
        markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    Map<String, Object> created = recordService.uploadMarkdown(userId, file);
    Long recordId = ((Number) created.get("id")).longValue();

    waitUntilStatus(recordId, GraphStatus.SUCCESS.name());

    Long chunkCount = chunkMapper.selectCount(new QueryWrapper<RecordChunk>().eq("record_id", recordId));
    Long nodeCount = nodeMapper.selectCount(new QueryWrapper<KnowledgeNode>().eq("source_record_id", recordId));
    Long relationCount = relationMapper.selectCount(new QueryWrapper<KnowledgeRelation>().eq("source_record_id", recordId));
    GrowthRecord latest = recordMapper.selectById(recordId);

    assertEquals("compiled summary", latest.summary, "compiled result should persist after summary-only updatedAt change");
    assertTrue(chunkCount > 0, "record_chunk should be generated");
    assertTrue(nodeCount > 0, "knowledge_node should be generated");
    assertTrue(relationCount > 0, "knowledge_relation should be generated");
    assertEquals(GraphStatus.SUCCESS.name(), latest.graphStatus, "graph status should become SUCCESS");
    assertEquals(null, latest.graphError, "successful graph generation should clear the error");
    assertTrue(latest.graphUpdatedAt != null, "successful graph generation should record completion time");
    verify(aiService, never()).extractKeywords(anyString());
    verify(aiService, never()).generateSummary(anyString());
    verify(aiService, times(1)).extractStructure(anyString());
    verify(aiService, times(1)).extractRelations(
        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void markdownUploadRecordsFailedStatusWhenKnowledgeCompilationFails() throws Exception {
    Long userId = 1L;
    String title = "phase4-failure-test-" + System.nanoTime();
    when(aiService.extractStructure(anyString())).thenThrow(new IllegalStateException("LLM timeout"));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        title + ".md",
        "text/markdown",
        "# Failure test\n\nThis note triggers a compiler failure."
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    Map<String, Object> created = recordService.uploadMarkdown(userId, file);
    Long recordId = ((Number) created.get("id")).longValue();

    waitUntilStatus(recordId, GraphStatus.FAILED.name());
    GrowthRecord failed = recordMapper.selectById(recordId);

    assertEquals(GraphStatus.FAILED.name(), failed.graphStatus);
    assertTrue(failed.graphError.contains("LLM timeout"));
    assertTrue(failed.graphUpdatedAt != null);
  }

  @Test
  void globalGraphHidesAndCleanupRemovesOrphanSharedNodes() {
    KnowledgeNode orphan = new KnowledgeNode();
    orphan.userId = 1L;
    orphan.nodeType = StructuredKnowledgeService.NODE_CONCEPT;
    orphan.name = "orphan-" + System.nanoTime();
    orphan.normalizedName = orphan.name;
    orphan.description = "unreferenced test node";
    orphan.createdAt = LocalDateTime.now();
    orphan.updatedAt = orphan.createdAt;
    nodeMapper.insert(orphan);

    assertTrue(graphQueryService.graph(1L).nodes.stream()
        .noneMatch(node -> orphan.name.equals(node.name)));

    structuredKnowledgeService.removeRecordKnowledge(1L, Long.MAX_VALUE);

    assertEquals(null, nodeMapper.selectById(orphan.id));
  }

  @Test
  void conceptMatchingAnotherNoteTitleIsCollapsedIntoDirectNoteLink() {
    Long userId = 1L;
    long suffix = System.nanoTime();
    String sourceTitle = "STM-" + suffix;
    String targetTitle = "interrupt-" + suffix;

    GrowthRecord source = graphRecord(900_000L + suffix, userId, sourceTitle);
    recordMapper.insert(source);
    structuredKnowledgeService.persist(source, new KnowledgeCompileResult(
        sourceTitle + " mentions " + targetTitle,
        "source summary",
        List.of(targetTitle),
        List.of(new KnowledgeConceptCandidate(targetTitle, "interrupt concept", List.of(0))),
        List.of(),
        List.of(),
        List.of()));

    GrowthRecord target = graphRecord(source.id + 1, userId, targetTitle);
    recordMapper.insert(target);
    structuredKnowledgeService.persist(target, new KnowledgeCompileResult(
        targetTitle,
        "target summary",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of()));

    KnowledgeNode sourceNote = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId).eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .eq("source_record_id", source.id));
    KnowledgeNode targetNote = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId).eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .eq("source_record_id", target.id));

    KnowledgeGraphDTO graph = graphQueryService.graph(userId);
    assertTrue(graph.nodes.stream().noneMatch(node -> targetTitle.equals(node.name)
            && StructuredKnowledgeService.NODE_CONCEPT.equals(node.nodeType)),
        "title-matched concept should not be displayed");
    var displayedTarget = graph.nodes.stream().filter(node -> targetTitle.equals(node.name)
            && StructuredKnowledgeService.NODE_NOTE.equals(node.nodeType))
        .findFirst().orElseThrow();
    assertTrue(displayedTarget.relationCount > 0,
        "folded note should count projected relations");
    assertEquals(2L, displayedTarget.sourceCount,
        "folded note should include its own record and the mentioning record as sources");
    assertTrue(graph.nodes.stream().anyMatch(node -> targetTitle.equals(node.name)
            && StructuredKnowledgeService.NODE_NOTE.equals(node.nodeType)),
        "the matching note should be displayed instead");
    assertTrue(graph.edges.stream().anyMatch(edge ->
            ("knowledge-" + sourceNote.id).equals(edge.source)
                && ("knowledge-" + targetNote.id).equals(edge.target)
                && RelationType.RELATED_TO.value().equals(edge.relationType)),
        "source note should link directly to the note named like its concept");

    KnowledgeNodeDetailDTO detail = graphQueryService.nodeDetail(userId, targetNote.id);
    assertEquals(targetNote.id, detail.node.knowledgeNodeId);
    assertEquals(2, detail.sources.size());
    assertTrue(detail.node.relationCount > 0);
  }

  private GrowthRecord graphRecord(Long id, Long userId, String title) {
    GrowthRecord record = new GrowthRecord();
    record.id = id;
    record.userId = userId;
    record.title = title;
    record.content = title;
    record.recordType = "note";
    record.createdAt = LocalDateTime.now();
    record.updatedAt = record.createdAt;
    return record;
  }

  private void waitUntilStatus(Long recordId, String expectedStatus) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      GrowthRecord record = recordMapper.selectById(recordId);
      if (record != null && expectedStatus.equals(record.graphStatus)) {
        return;
      }
      Thread.sleep(100);
    }
    fail("Timed out waiting for graph status " + expectedStatus + " for record " + recordId);
  }
}
