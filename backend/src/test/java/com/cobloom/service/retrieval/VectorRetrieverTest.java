package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.RecordChunk;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.RecordChunkMapper;
import com.cobloom.service.rag.EmbeddingService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VectorRetrieverTest {
  private RecordChunkMapper chunkMapper;
  private GrowthRecordMapper recordMapper;
  private EmbeddingService embeddingService;
  private VectorRetriever retriever;

  @BeforeEach
  void setUp() {
    chunkMapper = mock(RecordChunkMapper.class);
    recordMapper = mock(GrowthRecordMapper.class);
    embeddingService = mock(EmbeddingService.class);
    retriever = new VectorRetriever(chunkMapper, recordMapper, embeddingService);
    when(embeddingService.embed(any())).thenReturn(new double[] {1.0, 0.0});
  }

  @Test
  void invalidAndLowSimilarityChunksAreSkipped() {
    RecordChunk blank = chunk(1L, 1L, "", "embedding", LocalDateTime.now());
    RecordChunk missingEmbedding = chunk(2L, 1L, "content", "", LocalDateTime.now());
    RecordChunk lowScore = chunk(3L, 1L, "low similarity", "low", LocalDateTime.now());
    when(chunkMapper.selectList(any())).thenReturn(List.of(blank, missingEmbedding, lowScore));
    when(embeddingService.cosine(any(double[].class), eq("low"))).thenReturn(0.1);

    List<RetrievalCandidate> candidates = retriever.retrieve(1L, null);

    assertTrue(candidates.isEmpty());
  }

  @Test
  void validChunksAreScoredSortedAndTruncatedToEight() {
    java.util.ArrayList<RecordChunk> chunks = new java.util.ArrayList<>();
    for (long id = 1; id <= 10; id++) {
      chunks.add(chunk(id, id, "x".repeat(id == 1 ? 250 : 80), "e" + id,
          LocalDateTime.now().minusDays(id)));
      when(embeddingService.cosine(any(double[].class), eq("e" + id))).thenReturn(0.3 + id * 0.05);
      GrowthRecord record = new GrowthRecord();
      record.id = id;
      record.updatedAt = LocalDateTime.now().minusDays(id);
      when(recordMapper.selectById(id)).thenReturn(record);
    }
    when(chunkMapper.selectList(any())).thenReturn(chunks);
    ExpandedQuery query = new ExpandedQuery("ADC", List.of("ADC"), List.of(), List.of(), "ADC expanded");

    List<RetrievalCandidate> candidates = retriever.retrieve(1L, query);

    assertEquals(8, candidates.size());
    assertEquals(10L, candidates.getFirst().chunkId());
    assertTrue(candidates.getFirst().score() >= candidates.getLast().score());
    assertEquals("vector", candidates.getFirst().source());
  }

  @Test
  void missingRecordFallsBackToChunkCreationTimeAndLongSnippetIsLimited() {
    RecordChunk chunk = chunk(50L, 9L, "long ".repeat(200), "valid", LocalDateTime.now().minusDays(30));
    when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
    when(embeddingService.cosine(any(double[].class), eq("valid"))).thenReturn(0.8);
    when(recordMapper.selectById(9L)).thenReturn(null);

    RetrievalCandidate candidate = retriever.retrieve(1L,
        new ExpandedQuery("long", List.of("long"), List.of(), List.of(), "long")).getFirst();

    assertEquals(120, candidate.snippet().length());
    assertTrue(candidate.score() > 0.5);
  }

  private RecordChunk chunk(Long id, Long recordId, String content, String embedding,
                            LocalDateTime createdAt) {
    RecordChunk chunk = new RecordChunk();
    chunk.id = id;
    chunk.userId = 1L;
    chunk.recordId = recordId;
    chunk.chunkIndex = id.intValue();
    chunk.content = content;
    chunk.embedding = embedding;
    chunk.createdAt = createdAt;
    return chunk;
  }
}
