package com.cobloom.service.knowledge;

public record KnowledgeChunkCandidate(
    Integer chunkIndex,
    String heading,
    String sectionPath,
    String content,
    String embedding
) {}
