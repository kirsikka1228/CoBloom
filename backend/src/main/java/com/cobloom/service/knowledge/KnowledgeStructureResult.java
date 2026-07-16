package com.cobloom.service.knowledge;

import java.util.List;

public record KnowledgeStructureResult(
    String summary,
    List<KnowledgeConceptCandidate> concepts,
    List<KnowledgeEntityCandidate> entities
) {}
