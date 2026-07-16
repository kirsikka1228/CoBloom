package com.cobloom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.dto.QAReferenceDTO;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.QaRecord;
import com.cobloom.entity.QaReference;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.QaRecordMapper;
import com.cobloom.mapper.QaReferenceMapper;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.rag.RagContextBuilder;
import com.cobloom.service.rag.RagContextItem;
import com.cobloom.service.retrieval.RetrievalResult;
import com.cobloom.service.retrieval.RetrievalService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QaServiceTest {

  @Mock GrowthRecordMapper recordMapper;
  @Mock QaRecordMapper qaRecordMapper;
  @Mock QaReferenceMapper referenceMapper;
  @Mock AIService aiService;
  @Mock RetrievalService retrievalService;
  @Mock RagContextBuilder ragContextBuilder;

  private QaService service;

  @BeforeEach
  void setUp() {
    service = new QaService(recordMapper, qaRecordMapper, referenceMapper, aiService,
        retrievalService, ragContextBuilder);
  }

  @Test
  void askUsesRetrievedContextAndPersistsTraceableReference() {
    RagContextItem item = new RagContextItem(7L, 8L, 9L, "RAG", "context", "snippet", "chunk", 0.87654);
    when(retrievalService.retrieve(1L, "question")).thenReturn(new RetrievalResult("question", List.of()));
    when(ragContextBuilder.build(any())).thenReturn(List.of(item));
    when(aiService.answer("question", List.of("context"))).thenReturn("answer");
    when(qaRecordMapper.insert(any(QaRecord.class))).thenAnswer(invocation -> {
      QaRecord qa = invocation.getArgument(0);
      qa.id = 10L;
      return 1;
    });
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
      QaRecord qa = new QaRecord();
      qa.id = 10L;
      qa.userId = 1L;
      qa.question = "question";
      qa.answer = "answer";
      qa.createdAt = LocalDateTime.now();
      return qa;
    });
    QaReference stored = new QaReference();
    stored.recordId = 7L;
    stored.chunkId = 8L;
    stored.snippet = "snippet";
    stored.similarity = 0.877;
    when(referenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
    GrowthRecord source = new GrowthRecord();
    source.id = 7L;
    source.title = "Source note";
    when(recordMapper.selectById(7L)).thenReturn(source);

    Map<String, Object> result = service.ask(1L, "question");

    assertEquals("answer", result.get("answer"));
    List<?> references = (List<?>) result.get("references");
    assertEquals(1, references.size());
    assertEquals("Source note", ((QAReferenceDTO) references.get(0)).title);
    verify(referenceMapper).insert(any(QaReference.class));
  }

  @Test
  void askWithoutContextUsesFallbackAndSkipsLlmAndReferences() {
    when(retrievalService.retrieve(1L, "unknown")).thenReturn(new RetrievalResult("unknown", List.of()));
    when(ragContextBuilder.build(any())).thenReturn(List.of());
    when(qaRecordMapper.insert(any(QaRecord.class))).thenAnswer(invocation -> {
      QaRecord qa = invocation.getArgument(0);
      qa.id = 11L;
      return 1;
    });
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
      QaRecord qa = new QaRecord();
      qa.id = 11L;
      qa.userId = 1L;
      qa.question = "unknown";
      qa.answer = "fallback";
      return qa;
    });
    when(referenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

    Map<String, Object> result = service.ask(1L, "unknown");

    assertTrue(result.containsKey("references"));
    verify(aiService, never()).answer(any(), any());
    verify(referenceMapper, never()).insert(any(QaReference.class));
  }

  @Test
  void detailKeepsReferenceWhenSourceRecordWasDeleted() {
    QaRecord qa = qa(3L, 1L);
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenReturn(qa);
    QaReference ref = new QaReference();
    ref.recordId = 99L;
    ref.snippet = "old evidence";
    ref.similarity = null;
    when(referenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ref));
    when(recordMapper.selectById(99L)).thenReturn(null);

    Map<String, Object> detail = service.detail(1L, 3L);

    QAReferenceDTO dto = (QAReferenceDTO) ((List<?>) detail.get("references")).get(0);
    assertEquals(0.0, dto.similarity);
  }

  @Test
  void detailRejectsMissingOrForeignHistory() {
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.detail(2L, 3L));
  }

  @Test
  void historyAndCountReturnMapperResults() {
    when(qaRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(qa(1L, 5L), qa(2L, 5L)));
    when(qaRecordMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

    assertEquals(2, service.history(5L).size());
    assertEquals(2, service.qaCount(5L));
  }

  @Test
  void deleteHistoryRemovesReferencesThenQuestion() {
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenReturn(qa(4L, 1L));
    when(qaRecordMapper.delete(any(Wrapper.class))).thenReturn(1);

    service.deleteHistory(1L, 4L);

    verify(referenceMapper).delete(any(Wrapper.class));
    verify(qaRecordMapper).delete(any(Wrapper.class));
  }

  @Test
  void deleteHistoryRejectsMissingAndFailedDelete() {
    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.deleteHistory(1L, 4L));

    when(qaRecordMapper.selectOne(any(Wrapper.class))).thenReturn(qa(4L, 1L));
    when(qaRecordMapper.delete(any(Wrapper.class))).thenReturn(0);
    assertThrows(IllegalStateException.class, () -> service.deleteHistory(1L, 4L));
  }

  private QaRecord qa(Long id, Long userId) {
    QaRecord qa = new QaRecord();
    qa.id = id;
    qa.userId = userId;
    qa.question = "q" + id;
    qa.answer = "a" + id;
    qa.createdAt = LocalDateTime.now();
    return qa;
  }
}
