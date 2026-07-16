package com.cobloom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.dto.RecordRequest;
import com.cobloom.entity.CompanionFeedback;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.RecordChunk;
import com.cobloom.entity.RecordTag;
import com.cobloom.entity.Tag;
import com.cobloom.mapper.CompanionFeedbackMapper;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.mapper.QaReferenceMapper;
import com.cobloom.mapper.RecordChunkMapper;
import com.cobloom.mapper.RecordTagMapper;
import com.cobloom.mapper.TagMapper;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.knowledge.GraphStage;
import com.cobloom.service.knowledge.GraphStatus;
import com.cobloom.service.knowledge.KnowledgeProcessingService;
import com.cobloom.service.knowledge.StructuredKnowledgeService;
import com.cobloom.service.rag.EmbeddingService;
import com.cobloom.service.rag.TextSimilarity;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

  @Mock GrowthRecordMapper recordMapper;
  @Mock TagMapper tagMapper;
  @Mock RecordTagMapper recordTagMapper;
  @Mock RecordChunkMapper chunkMapper;
  @Mock CompanionFeedbackMapper feedbackMapper;
  @Mock QaReferenceMapper qaReferenceMapper;
  @Mock AIService aiService;
  @Mock TextSimilarity similarity;
  @Mock EmbeddingService embeddingService;
  @Mock KnowledgeNodeMapper knowledgeNodeMapper;
  @Mock KnowledgeRelationMapper knowledgeRelationMapper;
  @Mock KnowledgeProcessingService knowledgeProcessingService;
  @Mock StructuredKnowledgeService structuredKnowledgeService;

  private RecordService service;

  @BeforeEach
  void setUp() {
    service = new RecordService(recordMapper, tagMapper, recordTagMapper, chunkMapper,
        feedbackMapper, qaReferenceMapper, aiService, similarity, embeddingService,
        knowledgeNodeMapper, knowledgeRelationMapper, knowledgeProcessingService,
        structuredKnowledgeService);
  }

  @Test
  void createPersistsTagsInitialStatusAndSchedulesKnowledge() {
    GrowthRecord[] stored = new GrowthRecord[1];
    when(recordMapper.insert(any(GrowthRecord.class))).thenAnswer(invocation -> {
      stored[0] = invocation.getArgument(0);
      stored[0].id = 21L;
      return 1;
    });
    when(recordMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> stored[0]);
    when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
      ((Tag) invocation.getArgument(0)).id = 31L;
      return 1;
    });
    RecordRequest request = request("  Title  ", "body", List.of(" java ", "", "java"));

    Map<String, Object> created = service.create(5L, request);

    assertEquals(21L, created.get("id"));
    assertEquals("Title", stored[0].title);
    assertEquals(GraphStatus.WAITING.name(), stored[0].graphStatus);
    assertEquals(GraphStage.QUEUED.name(), stored[0].graphStage);
    verify(recordTagMapper).delete(any(Wrapper.class));
    verify(knowledgeProcessingService).processRecord(21L, 5L);
  }

  @Test
  void createRejectsNullAndBlankTitles() {
    assertThrows(IllegalArgumentException.class, () -> service.create(1L, null));
    assertThrows(IllegalArgumentException.class, () -> service.create(1L, request("  ", "x", null)));
    verify(recordMapper, never()).insert(any(GrowthRecord.class));
  }

  @Test
  void uploadMarkdownCreatesRecordAndDerivesTitle() {
    GrowthRecord[] stored = new GrowthRecord[1];
    when(recordMapper.insert(any(GrowthRecord.class))).thenAnswer(invocation -> {
      stored[0] = invocation.getArgument(0);
      stored[0].id = 22L;
      return 1;
    });
    when(recordMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> stored[0]);
    MockMultipartFile file = new MockMultipartFile("file", " notes.MD ", "text/markdown", "# content".getBytes());

    Map<String, Object> result = service.uploadMarkdown(2L, file);

    assertEquals("notes", result.get("title"));
    assertEquals("# content", result.get("content"));
  }

  @Test
  void uploadMarkdownRejectsAllInvalidFileBoundaries() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L, null));
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L,
        new MockMultipartFile("file", "a.md", "text/plain", new byte[0])));
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L,
        new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes())));
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L,
        new MockMultipartFile("file", ".md", "text/plain", "x".getBytes())));
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L,
        new MockMultipartFile("file", "a.md", "text/plain", "   ".getBytes())));

    MultipartFile tooLarge = org.mockito.Mockito.mock(MultipartFile.class);
    when(tooLarge.isEmpty()).thenReturn(false);
    when(tooLarge.getSize()).thenReturn(2L * 1024 * 1024 + 1);
    assertThrows(IllegalArgumentException.class, () -> service.uploadMarkdown(1L, tooLarge));

    MultipartFile broken = org.mockito.Mockito.mock(MultipartFile.class);
    when(broken.isEmpty()).thenReturn(false);
    when(broken.getSize()).thenReturn(10L);
    when(broken.getOriginalFilename()).thenReturn("a.md");
    when(broken.getBytes()).thenThrow(new IOException("disk error"));
    assertThrows(IllegalStateException.class, () -> service.uploadMarkdown(1L, broken));
  }

  @Test
  void updateSummaryKeywordsAndFeedbackCoverRecordActions() {
    GrowthRecord record = record(8L, 1L, "old", "body");
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record);
    when(aiService.generateSummary("new body")).thenReturn("summary");
    when(aiService.extractKeywords("new body")).thenReturn(List.of("RAG", "Graph"));
    when(aiService.companionFeedback(any(), any())).thenReturn("keep going");
    RecordRequest update = request("new", "new body", null);

    assertEquals("new", service.update(1L, 8L, update).get("title"));
    assertEquals("summary", service.generateSummary(1L, 8L).get("summary"));
    assertTrue(((java.util.Set<?>) service.extractKeywords(1L, 8L).get("keywords")).contains("RAG"));
    CompanionFeedback feedback = service.feedback(1L, 8L, " ");
    assertEquals("gentle", feedback.feedbackType);
    assertEquals("keep going", feedback.content);
  }

  @Test
  void deleteRemovesOwnedRecordAndAllDependentData() {
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record(8L, 1L, "x", "y"));

    service.delete(1L, 8L);

    verify(structuredKnowledgeService).removeRecordKnowledge(1L, 8L);
    verify(chunkMapper).delete(any(Wrapper.class));
    verify(feedbackMapper).delete(any(Wrapper.class));
    verify(qaReferenceMapper).delete(any(Wrapper.class));
    verify(recordTagMapper).delete(any(Wrapper.class));
    verify(recordMapper).deleteById(8L);
  }

  @Test
  void listRecentDetailTimelineAndCountsBuildViews() {
    GrowthRecord one = record(1L, 9L, "one", "body");
    GrowthRecord two = record(2L, 9L, "two", "body");
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(one, two));
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(one);
    when(recordMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
    when(tagMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

    assertEquals(2, service.list(9L).size());
    assertEquals(2, service.list(9L, " one ").size());
    assertEquals(1, service.recent(9L, 1).size());
    assertEquals(1L, service.detail(9L, 1L).get("id"));
    assertEquals(2, service.timeline(9L).size());
    assertEquals(2, service.recordCount(9L));
    assertEquals(3, service.tagCount(9L));
  }

  @Test
  void tagOperationsTrimReuseFilterAndCreateTags() {
    Tag used = tag(1L, 2L, "used");
    Tag unused = tag(2L, 2L, "unused");
    when(tagMapper.selectList(any(Wrapper.class))).thenReturn(List.of(used, unused));
    when(recordTagMapper.selectCount(any(Wrapper.class))).thenReturn(1L, 0L);
    when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(used);

    assertEquals(1, service.tags(2L).size());
    assertEquals(1L, service.createTag(2L, " used ").get("id"));
    assertThrows(IllegalArgumentException.class, () -> service.createTag(2L, "  "));

    when(tagMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
      ((Tag) invocation.getArgument(0)).id = 3L;
      return 1;
    });
    assertEquals(3L, service.createTag(2L, "new").get("id"));
  }

  @Test
  void viewUsesLegacySuccessAndResolvesOnlyExistingTags() {
    GrowthRecord record = record(4L, 1L, "title", "body");
    record.graphStatus = null;
    record.keywords = "RAG, ,Graph";
    RecordTag good = new RecordTag(); good.tagId = 3L;
    RecordTag missing = new RecordTag(); missing.tagId = 4L;
    when(recordTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of(good, missing));
    when(tagMapper.selectById(3L)).thenReturn(tag(3L, 1L, "knowledge"));
    when(tagMapper.selectById(4L)).thenReturn(null);

    Map<String, Object> view = service.view(record);

    assertEquals(GraphStatus.SUCCESS.name(), view.get("graphStatus"));
    assertEquals(List.of("knowledge"), view.get("tags"));
    assertEquals(2, ((java.util.Set<?>) view.get("keywords")).size());
  }

  @Test
  void requireOwnedRejectsMissingRecord() {
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.requireOwned(1L, 99L));
  }

  @Test
  void recentFeedbackReturnsMapperData() {
    CompanionFeedback feedback = new CompanionFeedback();
    when(feedbackMapper.selectList(any(Wrapper.class))).thenReturn(List.of(feedback));
    assertEquals(List.of(feedback), service.recentFeedback(1L));
  }

  @Test
  void recommendationsScoreEmbeddingAndExcludeUnrelatedDomain() {
    GrowthRecord current = record(1L, 1L, "STM32 interrupt", "GPIO DMA");
    current.keywords = "STM32,interrupt";
    GrowthRecord related = record(2L, 1L, "STM32 DMA", "interrupt controller");
    related.keywords = "STM32,interrupt";
    GrowthRecord unrelated = record(3L, 1L, "Vue web", "CSS HTTP");
    unrelated.keywords = "Vue,CSS";
    when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(current);
    when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(related, unrelated));
    when(embeddingService.embed(any())).thenReturn(new double[]{1.0, 0.0});
    when(embeddingService.cosine(any(double[].class), any(double[].class))).thenReturn(0.8);
    when(similarity.score(any(), any())).thenReturn(0.2);

    List<Map<String, Object>> recommendations = service.recommendations(1L, 1L);

    assertEquals(1, recommendations.size());
    assertEquals(2L, recommendations.get(0).get("id"));
    assertNotNull(recommendations.get(0).get("recommendationReason"));
  }

  @Test
  void knowledgeSchedulingPropagatesFailureForObservableFailedRequest() {
    when(recordMapper.insert(any(GrowthRecord.class))).thenAnswer(invocation -> {
      ((GrowthRecord) invocation.getArgument(0)).id = 5L;
      return 1;
    });
    doThrow(new IllegalStateException("executor rejected"))
        .when(knowledgeProcessingService).processRecord(5L, 1L);

    assertThrows(IllegalStateException.class,
        () -> service.create(1L, request("title", "body", List.of())));
  }

  private RecordRequest request(String title, String content, List<String> tags) {
    RecordRequest request = new RecordRequest();
    request.title = title;
    request.content = content;
    request.recordType = null;
    request.mood = "ok";
    request.tags = tags;
    return request;
  }

  private GrowthRecord record(Long id, Long userId, String title, String content) {
    GrowthRecord record = new GrowthRecord();
    record.id = id;
    record.userId = userId;
    record.title = title;
    record.content = content;
    record.recordType = "study";
    record.mood = "ok";
    record.createdAt = LocalDateTime.now();
    record.updatedAt = LocalDateTime.now();
    return record;
  }

  private Tag tag(Long id, Long userId, String name) {
    Tag tag = new Tag();
    tag.id = id;
    tag.userId = userId;
    tag.name = name;
    return tag;
  }
}
