package com.cobloom.service.knowledge;

import java.util.List;

public record KnowledgeConceptCandidate(
    String name,
    String description,
    List<Integer> evidenceChunkIds
) {}
