# 代码评审记录

### 本部分对 `com.cobloom.config` 文件夹下的文件进行review。

## 1. 异步任务配置 (`AsyncConfig.java`)

*   **现状描述**：成功定义了名为 `knowledgeTaskExecutor` 的线程池，设定了核心池大小为 1，最大池大小为 2，以及 100 的队列容量。
*   **改进建议**：
    *   **性能评估**：当前 `corePoolSize` 设置较小，若后续高并发业务增多，建议根据服务器 CPU 负载动态评估并调整线程池参数。
    *   **异常处理**：使用了 `AbortPolicy`，在任务超出队列容量时会直接抛出异常。建议确认业务层是否已实现对应的重试或补偿机制。

## 2. JWT 认证与实现 (`JwtAuthFilter.java`, `JwtUtil.java`)

*   **现状描述**：
    *   `JwtUtil` 采用了 HMAC-SHA 算法构建密钥，并支持过期时间配置化，符合标准实践。
    *   `JwtAuthFilter` 继承了 `OncePerRequestFilter`，确保认证逻辑在请求周期内仅执行一次。
*   **改进建议**：
    *   **异常监控**：`JwtAuthFilter` 中对解析异常使用了 `catch (Exception ignored)`。建议将其细化为捕获特定的 `JwtException`，并添加日志记录，以便于排查恶意请求或 Token 过期问题。
    *   **密钥安全**：确保 `cobloom.jwt-secret` 在部署环境下通过环境变量或配置中心管理，严禁硬编码。

## 3. 安全配置 (`SecurityConfig.java`)

*   **现状描述**：采用了无状态 `STATELESS` 会话管理，禁用了 CSRF，并集成了 `BCryptPasswordEncoder`，是目前主流的安全配置方案。
*   **改进建议**：
    *   **CORS 策略**：当前 CORS 配置 `addAllowedOriginPattern("*")` 过于宽松。生产环境建议显式指定允许的 Origin 域名，遵循最小权限原则。
    *   **开发环境隔离**：明确了对 `h2-console` 等路径的开放。请确保此类配置在生产环境（Profile）中进行严格过滤或关闭。

## 4. 用户上下文 (`UserContext.java`)

*   **现状描述**：有效利用 `SecurityContextHolder` 封装了 `userId()` 方法，实现了全局获取用户信息。
*   **改进建议**：
    *   **异常处理建议**：当前在无认证信息时直接抛出 `IllegalStateException`:。建议在项目中明确划分“必须认证”与“可选认证”的接口，以避免在允许匿名访问的接口中误抛出该异常。

---


### 本部分对 `com.cobloom.controller` 文件夹下的代码文件进行review。

## 1. 异常处理与统一响应 (`ApiExceptionHandler.java`)

*   **现状描述**：使用 `@RestControllerAdvice` 集中处理了 `IllegalArgumentException` 和 `MaxUploadSizeExceededException`，能够有效防止业务层异常直接透传至前端。
*   **改进建议**：
    *   **异常覆盖面**：建议增加对 `Throwable` 或 `Exception` 的兜底处理，返回 500 错误码，防止非预期异常泄露系统栈信息。
    *   **响应标准化**：目前返回的响应体格式为 `Map<String, String>`。建议定义统一的 `Result<T>` 封装类（包含 code, message, data 字段），以规范化 API 响应结构，方便前端统一解析。

## 2. 控制层逻辑与设计 (`AuthController`, `RecordController` 等)

*   **现状描述**：
    *   接口设计符合 RESTful 规范，逻辑层与控制层职责分工明确，通过 `UserContext.userId()` 获取当前用户，代码简洁。
    *   `RecordController` 处理了复杂的路径兼容性（同时支持 `/api/records` 与 `/api/notes`），体现了对现有系统兼容性的考量。
*   **改进建议**：
    *   **参数校验**：大量接口（如 `Auth` 注册、登录、分类创建）缺乏对请求体（`@RequestBody`）的非空或格式校验。建议引入 `spring-boot-starter-validation`，通过在 DTO 上添加 `@NotBlank`, `@Email` 等注解进行声明式校验。
    *   **接口职责**：`MetaController` 中的 `categories` 接口直接返回了硬编码的 List 数据。如果该分类体系业务需求稳定，建议移至常量类或数据库配置管理，而非直接写在控制层。
    *   **退出逻辑**：`AuthController` 中的 `logout` 接口目前仅作为占位。若未来业务包含 Token 黑名单或服务器端会话清理逻辑，需在此处增加对应 Service 调用。

## 3. 异步监控接口 (`KnowledgeExecutorController.java`)

*   **现状描述**：提供了一个内部 API 用于实时观测异步线程池状态，这对于排查任务积压问题非常有价值。
*   **改进建议**：
    *   **安全性**：该接口路径为 `/api/internal/...`，但未见明确的权限控制。建议在 `SecurityConfig` 中将其限制为仅 `ADMIN` 角色或仅限内网访问。

## 4. 其它接口 (`QaController`, `GraphController`, `TimelineController`)

*   **设计评价**：整体结构合理，利用 `UserContext` 简化了业务参数。`GraphController` 通过 `@PathVariable` 和 `@RequestParam` 灵活处理了知识图谱的查询请求，扩展性较好。
*   **小贴士**：对于 `QaController` 的删除接口，建议对删除操作进行日志审计，记录操作时间与用户 ID，以备后续溯源。

---



### 本部分对 `com.cobloom.service.ai` 文件夹下的核心 AI 交互层代码进行了review

## 1. 架构抽象与接口设计 (`AiProvider.java`, `AIService.java`)

*   **现状描述**：
    *   `AiProvider` 对底层 HTTP 调用逻辑进行了抽象，保持了请求协议的简洁性。
    *   `AIService` 聚焦于业务领域逻辑，如知识库摘要、关系提取和问答，实现了业务与 AI 模型的解耦。
*   **改进建议**：
    *   **接口封装**：`AIService` 中的 `extractRelations` 方法参数列表较长，若后续新增过滤条件或控制参数，建议封装为 `DTO` 对象，以提升代码的扩展性和阅读体验。

## 2. 模拟环境实现 (`MockAiProvider.java`, `MockAIService.java`)

*   **现状描述**：
    *   `MockAiProvider` 通过正则解析 `TASK` 标记，利用硬编码逻辑生成 JSON 响应，极大提高了开发阶段的迭代速度。
    *   `MockAIService` 标记为 `@Primary`，确保在没有配置真实 LLM 的情况下系统能够以“零配置”模式启动，设计非常贴心。
*   **改进建议**：
    *   **规则外置**：`MockAiProvider` 内部包含大量的关键词过滤逻辑（如 `isKnowledgeTerm`），建议将这些术语词库移至配置文件或外部字典，避免业务词汇更新时频繁修改 Java 代码。

## 3. 生产环境 LLM 集成 (`RealLlmAiProvider.java`)

*   **现状描述**：
    *   实现了基于 `RestClient` 的 OpenAI 兼容接口，支持流式传输（SSE），并根据不同任务类型智能选择模型。
    *   配置了完善的超时控制机制，考虑了连接与读取超时的生产安全需求。
*   **改进建议**：
    *   **容错与熔断**：虽然增加了超时设置，但在高并发场景下，建议引入熔断器（如 Resilience4j），在高延迟或外部接口异常时保护应用系统。
    *   **启动校验**：建议在 Spring Bean 初始化阶段增加 API Key 的格式校验，实现“Fail-fast”机制，避免运行过程中才发现配置缺失。
    *   **监控度量**：生产环境调用 LLM 往往成本高昂，强烈建议在此层引入 Micrometer，监控 Token 消耗、接口延迟及异常分布，以便后续进行成本审计和优化。

## 4. 数据清理与健壮性

*   **现状描述**：`RealLlmAiProvider` 对大模型可能产生的 markdown 代码块封装 (`stripJsonCodeFence`) 做了处理，提升了解析的稳定性。
*   **改进建议**：
    *   **解析重试**：大模型偶尔会输出非标准 JSON，建议增加一层基于 `Jackson` 的重试逻辑或格式修复器（Repairer），增强在复杂场景下的响应处理能力。

---

### 本部分对 `com.cobloom.service.` 核心服务层代码进行了review


## 1. 业务逻辑层抽象与封装 (`AuthService.java`, `GraphService.java`)

*   **现状描述**：
    *   `AuthService` 实现了用户注册、登录与状态获取逻辑，利用 `JwtUtil` 和 `PasswordEncoder` 保证了认证过程的安全度。
    *   `GraphService` 作为中介层，将前端请求透明地转发至 `KnowledgeGraphQueryService`，保持了当前 API 接口的简洁性。
*   **改进建议**：
    *   **参数校验**：`AuthService.register` 中对 `nickname` 的校验仅限于空值检查，缺乏对昵称长度限制和特殊字符过滤，建议引入标准的 `Validation` 注解或统一校验逻辑以增强安全性。
    *   **职责单一化**：`GraphService` 目前仅作简单的接口透传。若后续图谱查询业务逻辑增多，应注意避免此类“哑服务”过度臃肿，并保持接口层与服务层的界限。

## 2. 问答业务处理逻辑 (`QaService.java`)

*   **现状描述**：
    *   通过 `RetrievalService` 获取候选内容，并由 `AIService` 生成回答，流程清晰地串联了 RAG（检索增强生成）的全链路。
    *   代码中对事务管理（`@Transactional`）应用到位，确保了问答记录与引用表更新的原子性。
*   **改进建议**：
    *   **N+1 查询问题**：在 `detail` 方法中，遍历引用列表时循环调用了 `recordMapper.selectById` 循环查询 `GrowthRecord`，在数据量增大时会导致性能瓶颈。建议改用 `IN` 语句批量获取相关记录。
    *   **防御性编程**：当前针对“知识库未找到足够相关内容”的处理相对单一且直接硬编码。建议将其抽离至配置层，以便支持根据不同业务场景提供更具引导性的兜底话术。

## 3. 核心记录服务与复杂算法 (`RecordService.java`)

*   **现状描述**：
    *   集成了文档上传、知识处理、推荐评分算法及反馈机制，是当前系统中最复杂的业务单元。
    *   使用了 `TransactionSynchronizationManager` 精巧地处理异步任务，确保在主事务提交后触发知识库更新，保障了数据一致性。
*   **改进建议**：
    *   **代码解耦**：当前类承担了标签管理、推荐评分、内容提取等过多职责，违反了单一职责原则。建议将其拆分为 `RecordCoreService`、`RecommendationService` 等独立组件。
    *   **硬编码优化**：`domainTags` 中硬编码了大量领域关键词（如 "stm32", "web" 等），且 `GENERIC_RECOMMENDATION_TERMS` 也是硬编码集合，建议移至配置文件或数据库动态配置，提升维护灵活性。
    *   **性能优化**：`list` 方法直接返回了包含完整 `content` 字段的记录大对象，在列表查看场景中极易导致网络传输和内存压力，建议剥离该大字段至详情查询接口。

## 4. 事务与异步任务的健壮性 (`RecordService.java`)

*   **现状描述**：
    *   在 `RecordService` 中针对不同环境（事务激活/未激活）的异步处理逻辑做了兼容，确保了任务处理的及时性。
*   **改进建议**：
    *   **异步解耦**：目前知识库处理逻辑在 `afterCommit` 中直接同步调用 `knowledgeProcessingService.processRecord`。如果该逻辑执行耗时较长，在高并发下可能会阻塞后续的连接释放，甚至导致线程池吃紧。建议引入消息队列进行真正的异步化，通过持久化队列保障任务重试和高可用。
    *   **日志完善**：虽然关键路径已添加了详细的 `log.info`，但在异常处理链路（如异步任务执行失败时）中，建议增加更详细的上下文信息（如失败的 `recordId`、`userId` 等），以便在生产环境中快速进行问题排查和数据修复。

---

### 本部分对 `com.cobloom.service.retrieval` 文件夹下的代码文件进行review。


## 1. 业务逻辑层抽象与封装 (`AuthService.java`, `GraphService.java`)

*   **现状描述**：
    *   `AuthService` 实现了用户注册、登录与状态获取逻辑，利用 `JwtUtil` 和 `PasswordEncoder` 保证了认证过程的安全度。
    *   `GraphService` 作为中介层，将前端请求透明地转发至 `KnowledgeGraphQueryService`，保持了当前 API 接口的简洁性。
*   **改进建议**：
    *   **参数校验**：`AuthService.register` 中对 `nickname` 的校验仅限于空值检查，缺乏对昵称长度限制和特殊字符过滤，建议引入标准的 `Validation` 注解或统一校验逻辑以增强安全性。
    *   **职责单一化**：`GraphService` 目前仅作简单的接口透传。若后续图谱查询业务逻辑增多，应注意避免此类“哑服务”过度臃肿，并保持接口层与服务层的界限。

## 2. 问答业务处理逻辑 (`QaService.java`)

*   **现状描述**：
    *   通过 `RetrievalService` 获取候选内容，并由 `AIService` 生成回答，流程清晰地串联了 RAG（检索增强生成）的全链路。
    *   代码中对事务管理（`@Transactional`）应用到位，确保了问答记录与引用表更新的原子性。
*   **改进建议**：
    *   **N+1 查询问题**：在 `detail` 方法中，遍历引用列表时循环调用了 `recordMapper.selectById` 循环查询 `GrowthRecord`，在数据量增大时会导致性能瓶颈。建议改用 `IN` 语句批量获取相关记录。
    *   **防御性编程**：当前针对“知识库未找到足够相关内容”的处理相对单一且直接硬编码。建议将其抽离至配置层，以便支持根据不同业务场景提供更具引导性的兜底话术。

## 3. 核心记录服务与复杂算法 (`RecordService.java`)

*   **现状描述**：
    *   集成了文档上传、知识处理、推荐评分算法及反馈机制，是当前系统中最复杂的业务单元。
    *   使用了 `TransactionSynchronizationManager` 精巧地处理异步任务，确保在主事务提交后触发知识库更新，保障了数据一致性。
*   **改进建议**：
    *   **代码解耦**：当前类承担了标签管理、推荐评分、内容提取等过多职责，违反了单一职责原则。建议将其拆分为 `RecordCoreService`、`RecommendationService` 等独立组件。
    *   **硬编码优化**：`domainTags` 中硬编码了大量领域关键词（如 "stm32", "web" 等），且 `GENERIC_RECOMMENDATION_TERMS` 也是硬编码集合，建议移至配置文件或数据库动态配置，提升维护灵活性。
    *   **性能优化**：`list` 方法直接返回了包含完整 `content` 字段的记录大对象，在列表查看场景中极易导致网络传输和内存压力，建议剥离该大字段至详情查询接口。

## 4. 事务与异步任务的健壮性

*   **现状描述**：
    *   在 `RecordService` 中针对不同环境（事务激活/未激活）的异步处理逻辑做了兼容，确保了任务处理的及时性。
*   **改进建议**：
    *   **异步解耦**：目前知识库处理逻辑在 `afterCommit` 中直接同步调用 `knowledgeProcessingService.processRecord`。如果该逻辑执行耗时较长，在高并发下可能会阻塞后续的连接释放，甚至导致线程池吃紧。建议引入消息队列进行真正的异步化，通过持久化队列保障任务重试和高可用。
    *   **日志完善**：虽然关键路径已添加了详细的 `log.info`，但在异常处理链路中，建议增加更详细的上下文信息（如失败的 `recordId`、`userId` 等），以便在生产环境中快速进行问题排查和数据修复。


## 5. RAG 检索核心入口与召回融合 (`RetrievalService.java`, `RetrievalResult.java`, `RetrievalCandidate.java`)

*   **现状描述**：
    *   `RetrievalService` 采用多路召回（混合检索）架构，将向量检索（Vector）、关键字检索（Keyword）以及图谱关系网络（Graph）的结果在内存中进行去重合并，最终交由重排器处理。
    *   `merge` 算法针对多路重复节点通过合并 `source` 标志位、选取最大分数，并加入叠加增益分（`keywordScore * 0.15`），实现多维度证据的融合。
*   **改进建议**：
    *   **内存去重优化**：在 `merge` 方法中，使用字符串拼接作为 `Map` 的 Key（如 `"record:" + recordId + ":" + content`）。若内容过大，会频繁生成大字符串 Key 并占用不必要的内存。建议为 `GrowthRecord` 生成稳定的哈希值或将大字段转换为指纹来优化去重性能。
    *   **硬编码模型融合**：融合加分系数（`0.15`）以及重排阈值均为硬编码，建议随着测试反馈的微调，将这些检索超参数抽取到全局配置，避免硬编码。

## 6. 查询理解与多路召回层 (`QueryUnderstandingService.java`, `KeywordRetriever.java`, `VectorRetriever.java`)

*   **现状描述**：
    *   `QueryUnderstandingService` 提供了轻量级的分词与英文缩写识别（ACRONYM 匹配），并引入了针对嵌入式硬件术语的特定词库及别名扩充映射，使语义理解更贴合垂直业务。
    *   `VectorRetriever` 在相似度计算的基础上，通过分块质量（`chunkQuality`）和文档时间衰减因数（`recency`）综合计算相关性分数，更倾向于召回近期的高质量内容。
*   **改进建议**：
    *   **检索全表扫描问题 (高风险)**：`KeywordRetriever.retrieve` 与 `VectorRetriever.retrieve` 在执行检索时，直接在内存中遍历**全用户表数据**（例如对当前 `userId` 下的所有 `RecordChunk` 或 `KnowledgeNode` 执行全量 `selectList`）。当单个用户的笔记积累到成千上万条时，这将引发极其严重的性能崩溃。
    *   **修复方案**：
        1. 关键字检索：应在数据库层级实现全文索引，通过 SQL 完成模糊匹配和范围限制，绝对不要全量读到 JVM 内存里做匹配过滤。
        2. 向量检索：引入专门的向量数据库（如 Milvus, PgVector）执行 ANN（近似最近邻）搜索，而非在 JVM 内循环解析向量 String 并进行 Cosine 计算。
    *   **缩写逻辑健壮性**：`ACRONYM` 使用正则匹配英文词。当用户输入包含 "is"、"or"、"my" 这种两个字符的虚词时，可能会由于首字母大写转换和硬编码别名表的不匹配，带来错误的检索词噪声。

## 7. 知识图谱召回扩展 (`GraphRetriever.java`)

*   **现状描述**：
    *   `GraphRetriever` 基于高频 Seed 节点（种子候选），利用图谱关系网执行一度邻居扩散（`ALLOWED_RELATIONS` 限制），并通过证据文本（`evidenceText`）的存在性来进行过滤和质量约束。
    *   扩展的分数通过节点权重和特定关系类型（如 `PREREQUISITE` 前置关系）的额外 Boost 综合计算，从而将存在特定逻辑链条的间接证据召回进来。
*   **改进建议**：
    *   **N+1 循环查询与全表查询风险**：
        1. `relationsFor` 方法内，是在外层循环中针对每个 `seedNodeId` 频繁向数据库执行查询关系表的逻辑，存在典型的图节点膨胀引发的 N+1 问题。
        2. 对邻居节点详情的加载在内层循环调用 `nodeMapper.selectById`，建议改用批量加载（Batch Get）或在数据库端关联查询。
    *   **硬编码硬限**：在扩展计算中 `0.78` 属于人为规定的硬边界得分，随着后续重排规则微调，建议引入配置以保持可调整弹性。

## 8. 检索重排序机制 (`RetrievalReranker.java`)

*   **现状描述**：
    *   `RetrievalReranker` 提供了非常契合业务的精细化排序规则。通过标题匹配（`titleMatch`）、关键字匹配（`keywordMatch`）和英文缩写精确契合等特征多路提分。
    *   特别设计了 **主题失配惩罚（Topic Mismatch Penalty）**，针对 "STM32" 与 "ESP32" 的检索歧义以及未匹配核心词和别名的场景给予相应减分扣罚（惩罚因子如 `0.35` / `0.20`），显著降低了 RAG 中知识混淆和“答非所问”的现象。
*   **改进建议**：
    *   **主题失配逻辑硬编码**：STM32 与 ESP32 的负面纠正逻辑硬编码在 `topicMismatchPenaltyDetails` 逻辑内。若后续产品引入更多芯片，代码极易失去维护控制。建议将其抽象为“排他词字典映射”（Mutual Exclusion Dictionary Config）。
    *   **重复调用数据库**：在 Rerank 流式处理阶段，对每个候选节点都会单独去执行数据库查询。作为原本应该注重高效响应的 Rerank 阶段，这将造成极其恐怖的连接开销和响应时延。应在 Rerank 前做前置批量缓存。

   
---

### 本部分对 `com.cobloom.service.knowledge` 代码进行了review

## 1. 知识编译与结构提取 (`KnowledgeCompilerService.java`)

*   **现状描述**：
    *   `KnowledgeCompilerService` 利用 `MarkdownParser` 和 `ChunkGenerator` 将原始笔记切片，并基于 `AIService` 提取概念、实体和关系，实现了精细的清洗、格式化与标准化流程[cite: 14]。
    *   引入了证据匹配机制，在保存关系时强制校验大模型提取的原文引用是否真实存在于原始分块中（通过 `normalizeEvidence` 消除标点差异后进行 `contains` 匹配），提供了极强的质量保障[cite: 14]。
    *   定义了硬编码截断和容量上限（如 `MAX_CONCEPTS = 12`、`MAX_ENTITIES = 15`、`MAX_RELATIONS = 20` 等），防止 RAG 注入过多噪声[cite: 14]。
*   **改进建议**：
    *   **同步向量化开销**：在 `compile` 流程中，`embedChunks` 是在循环中同步调用 `embeddingService.embedToString` 的[cite: 14]。如果分块数量较多，由于大模型接口延迟高，阻塞式的向量化计算会导致该主线程长时间挂起。建议引入线程池或 CompletableFuture 进行并发向量化处理，以显著缩短编译耗时。
    *   **硬限参数化**：当前的容量限制（如 `MAX_CONCEPTS`）均硬编码在代码中[cite: 14]。为了方便不同体量笔记的灵活抽取，建议将这些限制参数化并抽取至全局配置文件（YML）中。

## 2. 图谱查询与关系投影 (`KnowledgeGraphQueryService.java`)

*   **现状描述**：
    *   支持整图、单笔记图、节点详情以及邻居扩散查询，并支持通过 `titleReplacements` 自动将概念节点向更具体的实体笔记节点进行投影和合并展示，使知识图谱的连接更加紧密[cite: 15]。
    *   在扩散查询中实现了最大深度控制（`MAX_DEPTH = 3`）以及关系类型规范化校验，保证了前端图谱渲染的性能[cite: 15]。
*   **改进建议**：
    *   **全量加载风险（严重）**：`titleReplacements` 方法在每次进行图谱投影时，都会通过 `nodeMapper.selectList` 全量加载当前用户下的所有 `NOTE` 节点到内存中进行比对[cite: 15]。若用户的历史笔记数量达到成千上万条，此操作会导致极高的 CPU 和内存负载。建议优化为增量查询，或者针对当前图谱中已经召回的节点进行精准的 `IN` 查询。
    *   **ID 解析健壮性**：在 `parseGraphNodeId` 方法中，对前端 ID 的解析直接使用了 `substring("knowledge-".length())` 这种硬编码长度方式[cite: 15]。如果前缀格式在未来版本中发生变更，极易引发越界异常（IndexOutOfBoundsException），建议采用正则匹配或更安全的字符串分割工具类。

## 3. 异步任务编排与状态一致性 (`KnowledgeProcessingService.java`)

*   **现状描述**：
    *   采用 `@Async("knowledgeTaskExecutor")` 将耗时较长的知识编译与持久化流程移至独立线程池异步执行，有效避免了主业务线程的阻塞[cite: 16]。
    *   设计了巧妙的双重校验机制（Stale Check）：在事务内（`TransactionTemplate`）提交持久化结果之前，通过 `isKnowledgeSourceChanged` 检查笔记的 `title` 和 `content` 是否在编译期间被用户再次修改，从而避免由于异步时差导致历史脏数据覆盖最新内容的情况[cite: 16]。
*   **改进建议**：
    *   **异常回滚与状态滞留**：当异步任务内部抛出未捕获异常时，外层会捕获并标记状态为 `FAILED` 并记录异常信息[cite: 16]。但由于该逻辑属于异步执行，若前端没有轮询或基于 WebSocket 的主动推送机制，用户可能会在界面上长期处于等待（WAITING）或未知报错状态。建议引入系统通知或消息推送机制，确保状态变更能实时反馈给前端。

## 4. 结构化知识持久化与孤立节点清理 (`StructuredKnowledgeService.java`)

*   **现状描述**：
    *   提供了精细的知识点持久化逻辑。在 `persist` 流程中首先进行 `removeRecordKnowledge` 清理历史知识，再建立新的笔记节点、概念节点与实体节点，最后通过 `RelationType.CONTAINS` 绑定隶属关系，保证了图谱结构的最新性[cite: 17]。
    *   设计了自动垃圾回收机制 `removeOrphanSharedNodes`，在关联数据被清除时，自动删除失去所有关系纽带的孤立概念和实体节点，防止无用垃圾数据在数据库中无限膨胀[cite: 17]。
*   **改进建议**：
    *   **N+1 循环查询 (严重风险)**：`removeOrphanSharedNodes` 方法中，首先加载了当前用户下所有的共享节点[cite: 17]。接着，在 `for` 循环中对每一个共享节点，分别调用 `relationMapper.selectCount` 查询其是否有残留关系[cite: 17]。这在用户知识点增多时是极其致命的 N+1 查询性能杀手。
    *   **修复方案**：建议使用 `LEFT JOIN` 或 `GROUP BY` 进行单条 SQL 聚合查询，一次性筛选出所有关系计数为 0 的孤立节点 ID 列表，并在循环外执行批量删除（`deleteBatchIds`），将数据库交互次数降到最低。