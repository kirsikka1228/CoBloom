package com.cobloom.service.rag;

public record RagContextItem(
    Long recordId,
    Long chunkId,
    Long knowledgeNodeId,
    String title,
    String content,
    String snippet,
    String sourceType,
    double score
) {}
