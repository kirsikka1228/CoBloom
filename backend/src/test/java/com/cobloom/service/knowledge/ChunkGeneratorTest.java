package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkGeneratorTest {
  private final ChunkGenerator generator = new ChunkGenerator(new ContentCleaner());

  @Test
  void emptySectionsProduceOneSafeEmptyChunk() {
    List<KnowledgeChunkCandidate> chunks = generator.generate(List.of());

    assertEquals(1, chunks.size());
    assertEquals(0, chunks.getFirst().chunkIndex());
    assertEquals("", chunks.getFirst().content());
  }

  @Test
  void paragraphsBeyondTargetSizeAreSplitAndReindexed() {
    String paragraph = "a".repeat(600);
    MarkdownSection section = new MarkdownSection("Title", "Title", paragraph + "\n\n" + paragraph);

    List<KnowledgeChunkCandidate> chunks = generator.generate(List.of(section));

    assertEquals(2, chunks.size());
    assertEquals(0, chunks.get(0).chunkIndex());
    assertEquals(1, chunks.get(1).chunkIndex());
    assertTrue(chunks.stream().allMatch(chunk -> chunk.content().startsWith("Title")));
  }

  @Test
  void veryLongParagraphIsSplitAtHardLimit() {
    MarkdownSection section = new MarkdownSection("", "", "x".repeat(2500));

    List<KnowledgeChunkCandidate> chunks = generator.generate(List.of(section));

    assertEquals(3, chunks.size());
    assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 900));
  }

  @Test
  void headingWithoutBodyStillProducesSearchableChunk() {
    List<KnowledgeChunkCandidate> chunks = generator.generate(
        List.of(new MarkdownSection("Only heading", "Root / Only heading", "")));

    assertEquals(1, chunks.size());
    assertEquals("Root / Only heading", chunks.getFirst().content());
  }
}
