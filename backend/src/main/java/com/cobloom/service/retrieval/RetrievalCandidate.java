package com.cobloom.service.retrieval;

public record RetrievalCandidate(
        String source, // 召回来源
        Long recordId, // 原始笔记记录 ID
        Long knowledgeNodeId, // 知识图谱节点 ID
        Long chunkId, // 笔记内容块 ID
        Integer chunkIndex, // 笔记内容块索引
        String content, // 用于后续构造 RAG 上下文的正文
        String snippet, // 短摘要，后续用于引用展示
        double vectorScore,
        double keywordScore,
        double score// 当前候选综合分，后续会被 reranker 重新计算
) {
}
