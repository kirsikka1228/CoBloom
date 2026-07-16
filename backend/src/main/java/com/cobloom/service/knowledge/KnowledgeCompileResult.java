package com.cobloom.service.knowledge;

import java.util.List;

public record KnowledgeCompileResult(
    String cleanedContent,
    String summary,
    List<String> keywords,
    List<KnowledgeConceptCandidate> concepts,
    List<KnowledgeEntityCandidate> entities,
    List<KnowledgeRelationCandidate> relations,
    List<KnowledgeChunkCandidate> chunks
) {}
