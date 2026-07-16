package com.cobloom.service.knowledge;

public record KnowledgeRelationCandidate(
    String source,
    String target,
    String relationType,
    String evidence
) {}
