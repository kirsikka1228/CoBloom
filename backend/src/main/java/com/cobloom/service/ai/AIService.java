package com.cobloom.service.ai;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.service.knowledge.KnowledgeChunkCandidate;
import com.cobloom.service.knowledge.KnowledgeNodeReference;
import com.cobloom.service.knowledge.KnowledgeRelationCandidate;
import com.cobloom.service.knowledge.KnowledgeStructureResult;
import java.util.List;

public interface AIService {
  String generateSummary(String content);
  List<String> extractKeywords(String content);
  KnowledgeStructureResult extractStructure(String chunkedContent);
  List<KnowledgeRelationCandidate> extractRelations(
      List<KnowledgeNodeReference> nodes,
      List<KnowledgeChunkCandidate> evidenceChunks
  );
  String companionFeedback(GrowthRecord record, String feedbackType);
  String answer(String question, List<String> contexts);
}
