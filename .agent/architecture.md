# CoBloom 系统架构设计说明 (Architecture)

## 1. 业务愿景与定位
CoBloom 是一个 AI 陪伴式个人记录与成长反馈平台。核心理念是“记录被看见、成长被回应”。系统不仅作为高效的个人知识库（通过时间线、知识图谱和 RAG 问答进行复盘），更作为一个具备情感共鸣的陪伴平台，通过 AI 自动分析提供长期的成长反馈与心理陪伴。

## 2. 总体技术架构
平台采用前后端分离架构，核心技术栈如下：
- **前端 (Frontend)**: Vue 3 + Vite + Pinia + Vue Router + Axios，提供响应式和沉浸式的用户体验。
- **后端 (Backend)**: Java 17 + Spring Boot + MyBatis，保障核心业务逻辑的稳健与扩展性。
- **数据存储 (Storage)**: 
  - 关系型/内存数据库 (H2/MySQL): 用于存储用户、成长记录、标签关系、问答历史等结构化数据。
  - 向量计算组件 (内嵌 TextSimilarity): 负责处理文本块的相似度匹配，为 RAG 注入上下文。

## 3. 核心数据流与功能模块
### 3.1 记录与双轨分析流
1. 用户在前端提交一篇包含情绪或生活事件的随笔。
2. 后端接收并持久化 `GrowthRecord`。
3. **分析流 A (情感陪伴)**：调用 `AIService` 进行异步/同步分析，提取情绪标签、文本摘要、核心关键词，并生成一段充满共情和洞察的“成长反馈”(`CompanionFeedback`)。
4. **分析流 B (知识固化)**：通过 `RecordService` 对文本进行智能切片（`RecordChunk`），并建立标签图谱关联（`RecordTag`）。

### 3.2 智能问答与回顾流 (RAG Loop)
1. 用户通过 `QaAsk.vue` 提问（例如：“我上个月面对焦虑时是怎么调节的？”）。
2. `QaService` 调用 `EmbeddingService` 将问题向量化。
3. 通过 `TextSimilarity` 比对数据库中该用户的所有 `RecordChunk`，筛选出最相关的 Top-K 切片（`ScoredChunk`）。
4. 将切片作为背景上下文（Context）拼接至 Prompt，透传给 `AIService`。
5. AI 生成融合了用户历史记忆的陪伴式回答，同时系统记录引用来源（`QaReference`）和问答对（`QaRecord`）。

### 3.3 多维回顾可视化
- **时间线 (Timeline)**：基于 `GrowthRecord` 的时间戳进行流式渲染。
- **关系图谱 (Graph)**：基于 `Tag` 和 `RecordTag` 的多对多映射，在前端绘制用户的“生活事件/认知图谱”。