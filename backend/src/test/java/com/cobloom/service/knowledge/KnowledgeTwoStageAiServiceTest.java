package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobloom.service.ai.MockAIService;
import com.cobloom.service.ai.MockAiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeTwoStageAiServiceTest {
  @Test
  void mockProviderSupportsStructureThenIdBasedRelations() {
    MockAIService aiService = new MockAIService(new MockAiProvider(), new ObjectMapper());
    KnowledgeStructureResult structure = aiService.extractStructure(
        "[C0] Spring Framework uses dependency injection to manage components.");

    assertFalse(structure.summary().isBlank());
    assertTrue(structure.concepts().size() <= 12);
    assertTrue(structure.entities().size() <= 15);

    List<KnowledgeRelationCandidate> relations = aiService.extractRelations(
        List.of(
            new KnowledgeNodeReference("N1", "Spring Framework", "ENTITY"),
            new KnowledgeNodeReference("N2", "dependency injection", "CONCEPT")
        ),
        List.of(new KnowledgeChunkCandidate(
            0, "", "", "Spring Framework uses dependency injection to manage components.", ""))
    );

    assertFalse(relations.isEmpty());
    assertTrue(relations.getFirst().evidence().startsWith("[C0]"));
  }
}
