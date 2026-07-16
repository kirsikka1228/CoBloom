package com.cobloom.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.RecordChunk;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.RecordChunkMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KnowledgeProcessingService {
  private static final Logger log = LoggerFactory.getLogger(KnowledgeProcessingService.class);

  private final GrowthRecordMapper recordMapper;
  private final RecordChunkMapper chunkMapper;
  private final KnowledgeNodeMapper nodeMapper;
  private final KnowledgeCompilerService knowledgeCompilerService;
  private final StructuredKnowledgeService structuredKnowledgeService;
  private final TransactionTemplate transactionTemplate;

  public KnowledgeProcessingService(GrowthRecordMapper recordMapper, RecordChunkMapper chunkMapper,
                                    KnowledgeNodeMapper nodeMapper,
                                    KnowledgeCompilerService knowledgeCompilerService,
                                    StructuredKnowledgeService structuredKnowledgeService,
                                    PlatformTransactionManager transactionManager) {
    this.recordMapper = recordMapper;
    this.chunkMapper = chunkMapper;
    this.nodeMapper = nodeMapper;
    this.knowledgeCompilerService = knowledgeCompilerService;
    this.structuredKnowledgeService = structuredKnowledgeService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Async("knowledgeTaskExecutor")
  public void processRecord(Long recordId, Long userId) {
    log.info("Knowledge processing processRecord start recordId={}, userId={}, thread={}",
        recordId, userId, Thread.currentThread().getName());
    GrowthRecord source = findOwned(recordId, userId);
    if (source == null) {
      log.info("Knowledge processing skipped reason=record_not_found recordId={}, userId={}", recordId, userId);
      return;
    }
    String sourceTitle = source.title;
    String sourceContent = source.content;
    markStatusIfSourceMatches(recordId, userId, sourceTitle, sourceContent, GraphStatus.PROCESSING, null);
    try {
      processRecordInTransaction(recordId, userId);
      log.info("Knowledge processing processRecord finished recordId={}, userId={}, thread={}",
          recordId, userId, Thread.currentThread().getName());
    } catch (Exception e) {
      markStatusIfSourceMatches(recordId, userId, sourceTitle, sourceContent, GraphStatus.FAILED, safeError(e));
      log.error("Knowledge processing failed recordId={}, userId={}, thread={}",
          recordId, userId, Thread.currentThread().getName(), e);
    }
  }

  public ProcessingOutcome processRecordInTransaction(Long recordId, Long userId) {
    log.info("Knowledge processing processRecordInTransaction enter recordId={}, userId={}, thread={}",
        recordId, userId, Thread.currentThread().getName());
    GrowthRecord record = recordMapper.selectOne(new QueryWrapper<GrowthRecord>()
        .eq("id", recordId)
        .eq("user_id", userId));
    if (record == null) {
      log.info("Knowledge processing processRecordInTransaction return reason=record_not_found recordId={}, userId={}", recordId, userId);
      return ProcessingOutcome.MISSING;
    }

    LocalDateTime sourceUpdatedAt = record.updatedAt;
    String sourceTitle = record.title;
    String sourceContent = record.content;
    log.info("Knowledge processing compile start recordId={}, userId={}, title={}, sourceUpdatedAt={}",
        record.id, record.userId, record.title, sourceUpdatedAt);
    KnowledgeCompileResult result = knowledgeCompilerService.compile(record,
        stage -> markStageIfSourceMatches(recordId, userId, sourceTitle, sourceContent, stage));
    log.info("Knowledge processing compile finished recordId={}, userId={}, chunkCount={}, conceptCount={}, entityCount={}, relationCount={}",
        record.id,
        record.userId,
        result == null || result.chunks() == null ? 0 : result.chunks().size(),
        result == null || result.concepts() == null ? 0 : result.concepts().size(),
        result == null || result.entities() == null ? 0 : result.entities().size(),
        result == null || result.relations() == null ? 0 : result.relations().size());
    logIngestDebug("compiled", record, result);

    markStageIfSourceMatches(recordId, userId, sourceTitle, sourceContent, GraphStage.PERSISTING);
    ProcessingOutcome outcome = transactionTemplate.execute(status -> persistCompiledResult(
        recordId, userId, sourceUpdatedAt, sourceTitle, sourceContent, result));
    return outcome == null ? ProcessingOutcome.MISSING : outcome;
  }

  private ProcessingOutcome persistCompiledResult(Long recordId, Long userId, LocalDateTime sourceUpdatedAt,
                                                    String sourceTitle, String sourceContent,
                                                    KnowledgeCompileResult result) {
    GrowthRecord latest = recordMapper.selectOne(new QueryWrapper<GrowthRecord>()
        .eq("id", recordId)
        .eq("user_id", userId));
    if (latest == null) {
      log.info("Knowledge processing processRecordInTransaction return reason=latest_record_not_found_before_persist recordId={}, userId={}", recordId, userId);
      return ProcessingOutcome.MISSING;
    }
    log.info("Knowledge processing stale check recordId={}, userId={}, sourceUpdatedAt={}, latestUpdatedAt={}",
        recordId, userId, sourceUpdatedAt, latest.updatedAt);
    if (isKnowledgeSourceChanged(sourceTitle, sourceContent, latest)) {
      log.info("Skip stale knowledge processing recordId={}, userId={}, reason=knowledge_source_changed_during_compile, sourceUpdatedAt={}, latestUpdatedAt={}, titleChanged={}, contentChanged={}",
          recordId,
          userId,
          sourceUpdatedAt,
          latest.updatedAt,
          !Objects.equals(sourceTitle, latest.title),
          !Objects.equals(sourceContent, latest.content));
      log.info("Knowledge processing processRecordInTransaction return reason=knowledge_source_changed_during_compile recordId={}, userId={}",
          recordId, userId);
      return ProcessingOutcome.STALE;
    }
    log.info("Knowledge processing stale check passed recordId={}, userId={}", recordId, userId);

    latest.summary = result.summary();
    latest.keywords = String.join(",", result.keywords());
    recordMapper.updateById(latest);

    rebuildChunks(latest, result.chunks());
    log.info("Knowledge processing structured persist start recordId={}, userId={}", latest.id, latest.userId);
    structuredKnowledgeService.persist(latest, result);
    log.info("Knowledge processing structured persist finished recordId={}, userId={}", latest.id, latest.userId);
    logPersistedDebug(latest, result);
    latest.graphStatus = GraphStatus.SUCCESS.name();
    latest.graphStage = GraphStage.COMPLETE.name();
    latest.graphError = null;
    latest.graphUpdatedAt = LocalDateTime.now();
    recordMapper.updateById(latest);
    log.info("Knowledge processing processRecordInTransaction exit recordId={}, userId={}", recordId, userId);
    return ProcessingOutcome.SUCCESS;
  }

  private GrowthRecord findOwned(Long recordId, Long userId) {
    return recordMapper.selectOne(new QueryWrapper<GrowthRecord>()
        .eq("id", recordId)
        .eq("user_id", userId));
  }

  private void markStatusIfSourceMatches(Long recordId, Long userId, String sourceTitle, String sourceContent,
                                         GraphStatus status, String error) {
    GrowthRecord latest = findOwned(recordId, userId);
    if (latest == null
        || !Objects.equals(sourceTitle, latest.title)
        || !Objects.equals(sourceContent, latest.content)) {
      log.info("Skip graph status update recordId={}, userId={}, status={}, reason=source_changed_or_missing",
          recordId, userId, status);
      return;
    }
    latest.graphStatus = status.name();
    latest.graphStage = switch (status) {
      case WAITING -> GraphStage.QUEUED.name();
      case PROCESSING -> latest.graphStage == null ? GraphStage.EXTRACTING_STRUCTURE.name() : latest.graphStage;
      case SUCCESS -> GraphStage.COMPLETE.name();
      case FAILED -> GraphStage.FAILED.name();
    };
    latest.graphError = error;
    latest.graphUpdatedAt = status == GraphStatus.SUCCESS || status == GraphStatus.FAILED
        ? LocalDateTime.now()
        : null;
    recordMapper.updateById(latest);
  }

  private void markStageIfSourceMatches(Long recordId, Long userId, String sourceTitle, String sourceContent,
                                        GraphStage stage) {
    GrowthRecord latest = findOwned(recordId, userId);
    if (latest == null || !Objects.equals(sourceTitle, latest.title)
        || !Objects.equals(sourceContent, latest.content)) return;
    latest.graphStage = stage.name();
    recordMapper.updateById(latest);
  }

  private String safeError(Exception error) {
    String message = error == null || error.getMessage() == null
        ? "知识图谱生成失败"
        : error.getMessage().replaceAll("\\s+", " ").trim();
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  public enum ProcessingOutcome {
    SUCCESS,
    STALE,
    MISSING
  }

  private void rebuildChunks(GrowthRecord record, List<KnowledgeChunkCandidate> chunks) {
    int chunkCount = chunks == null ? 0 : chunks.size();
    log.info("Knowledge processing rebuildChunks enter recordId={}, userId={}, chunkCount={}",
        record == null ? null : record.id,
        record == null ? null : record.userId,
        chunkCount);
    chunkMapper.delete(new QueryWrapper<RecordChunk>().eq("record_id", record.id));
    for (int i = 0; i < chunks.size(); i++) {
      KnowledgeChunkCandidate chunk = chunks.get(i);
      RecordChunk c = new RecordChunk();
      c.userId = record.userId;
      c.recordId = record.id;
      c.chunkIndex = chunk.chunkIndex() == null ? i : chunk.chunkIndex();
      c.content = chunk.content() == null ? "" : chunk.content();
      c.embedding = chunk.embedding() == null ? "" : chunk.embedding();
      c.createdAt = LocalDateTime.now();
      chunkMapper.insert(c);
    }
    log.info("Knowledge processing rebuildChunks exit recordId={}, userId={}, insertedChunkCount={}",
        record.id, record.userId, chunkCount);
  }

  private boolean isKnowledgeSourceChanged(String sourceTitle, String sourceContent, GrowthRecord latest) {
    return latest == null
        || !Objects.equals(sourceTitle, latest.title)
        || !Objects.equals(sourceContent, latest.content);
  }

  private void logIngestDebug(String stage, GrowthRecord record, KnowledgeCompileResult result) {
    if (!log.isDebugEnabled()) return;
    List<KnowledgeChunkCandidate> chunks = result == null || result.chunks() == null ? List.of() : result.chunks();
    log.debug("RAG ingest {} recordId={}, userId={}, title={}, chunkCount={}, conceptCount={}, entityCount={}",
        stage,
        record == null ? null : record.id,
        record == null ? null : record.userId,
        record == null ? null : record.title,
        chunks.size(),
        result == null || result.concepts() == null ? 0 : result.concepts().size(),
        result == null || result.entities() == null ? 0 : result.entities().size());
    for (KnowledgeChunkCandidate chunk : chunks) {
      String content = chunk.content() == null ? "" : chunk.content();
      String embedding = chunk.embedding() == null ? "" : chunk.embedding();
      log.debug("RAG ingest chunk recordId={}, chunkIndex={}, first100={}, embeddingLength={}",
          record == null ? null : record.id,
          chunk.chunkIndex(),
          preview(content),
          embedding.length());
    }
  }

  private void logPersistedDebug(GrowthRecord record, KnowledgeCompileResult result) {
    if (!log.isDebugEnabled()) return;
    Long recordId = record == null ? null : record.id;
    Long userId = record == null ? null : record.userId;
    Long chunkCount = recordId == null ? 0L : chunkMapper.selectCount(new QueryWrapper<RecordChunk>().eq("record_id", recordId));
    Long noteNodeCount = recordId == null || userId == null ? 0L : nodeMapper.selectCount(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .eq("source_record_id", recordId));
    int compiledNodeCandidateCount = 1
        + (result == null || result.concepts() == null ? 0 : result.concepts().size())
        + (result == null || result.entities() == null ? 0 : result.entities().size());
    log.debug("RAG ingest persisted recordId={}, userId={}, dbChunkCount={}, noteKnowledgeNodeCount={}, compiledKnowledgeNodeCandidateCount={}",
        recordId,
        userId,
        chunkCount,
        noteNodeCount,
        compiledNodeCandidateCount);
  }

  private String preview(String content) {
    if (content == null || content.isBlank()) return "";
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
  }
}
