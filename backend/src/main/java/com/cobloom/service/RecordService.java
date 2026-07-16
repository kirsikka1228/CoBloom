package com.cobloom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.dto.RecordRequest;
import com.cobloom.entity.CompanionFeedback;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.entity.QaReference;
import com.cobloom.entity.RecordChunk;
import com.cobloom.entity.RecordTag;
import com.cobloom.entity.Tag;
import com.cobloom.mapper.CompanionFeedbackMapper;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.mapper.QaReferenceMapper;
import com.cobloom.mapper.RecordChunkMapper;
import com.cobloom.mapper.RecordTagMapper;
import com.cobloom.mapper.TagMapper;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.knowledge.KnowledgeProcessingService;
import com.cobloom.service.knowledge.GraphStatus;
import com.cobloom.service.knowledge.GraphStage;
import com.cobloom.service.knowledge.StructuredKnowledgeService;
import com.cobloom.service.rag.EmbeddingService;
import com.cobloom.service.rag.TextSimilarity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RecordService {
  private static final Logger log = LoggerFactory.getLogger(RecordService.class);
  private static final long MAX_MARKDOWN_SIZE = 2 * 1024 * 1024;
  private static final Set<String> GENERIC_RECOMMENDATION_TERMS = Set.of(
      "ai", "api", "web", "代码", "结构", "项目", "系统", "学习", "笔记", "知识", "整理", "技术", "功能", "模块", "方法",
      "service", "controller", "mapper", "spring", "vue", "http", "https"
  );

  private final GrowthRecordMapper recordMapper;
  private final TagMapper tagMapper;
  private final RecordTagMapper recordTagMapper;
  private final RecordChunkMapper chunkMapper;
  private final KnowledgeNodeMapper knowledgeNodeMapper;
  private final KnowledgeRelationMapper knowledgeRelationMapper;
  private final CompanionFeedbackMapper feedbackMapper;
  private final QaReferenceMapper qaReferenceMapper;
  private final AIService aiService;
  private final TextSimilarity similarity;
  private final EmbeddingService embeddingService;
  private final KnowledgeProcessingService knowledgeProcessingService;
  private final StructuredKnowledgeService structuredKnowledgeService;

  public RecordService(GrowthRecordMapper recordMapper, TagMapper tagMapper, RecordTagMapper recordTagMapper,
                       RecordChunkMapper chunkMapper, CompanionFeedbackMapper feedbackMapper,
                       QaReferenceMapper qaReferenceMapper, AIService aiService, TextSimilarity similarity,
                       EmbeddingService embeddingService, KnowledgeNodeMapper knowledgeNodeMapper,
                       KnowledgeRelationMapper knowledgeRelationMapper, KnowledgeProcessingService knowledgeProcessingService,
                       StructuredKnowledgeService structuredKnowledgeService) {
    this.recordMapper = recordMapper;
    this.tagMapper = tagMapper;
    this.recordTagMapper = recordTagMapper;
    this.chunkMapper = chunkMapper;
    this.knowledgeNodeMapper = knowledgeNodeMapper;
    this.knowledgeRelationMapper = knowledgeRelationMapper;
    this.feedbackMapper = feedbackMapper;
    this.qaReferenceMapper = qaReferenceMapper;
    this.aiService = aiService;
    this.similarity = similarity;
    this.embeddingService = embeddingService;
    this.knowledgeProcessingService = knowledgeProcessingService;
    this.structuredKnowledgeService = structuredKnowledgeService;
  }

  public List<Map<String, Object>> list(Long userId) {
    return list(userId, null);
  }

  public List<Map<String, Object>> list(Long userId, String q) {
    QueryWrapper<GrowthRecord> wrapper = new QueryWrapper<GrowthRecord>().eq("user_id", userId);
    String keyword = q == null ? "" : q.trim();
    if (!keyword.isBlank()) {
      wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
    }
    wrapper.orderByDesc("created_at");
    return recordMapper.selectList(wrapper)
        .stream().map(this::view).toList();
  }

  public List<Map<String, Object>> recent(Long userId, int limit) {
    return recordMapper.selectList(new QueryWrapper<GrowthRecord>().eq("user_id", userId).orderByDesc("created_at"))
        .stream().limit(limit).map(this::view).toList();
  }

  public Map<String, Object> detail(Long userId, Long id) {
    return view(requireOwned(userId, id));
  }

  @Transactional
  public Map<String, Object> create(Long userId, RecordRequest req) {
    log.info("RecordService.create enter userId={}, title={}, contentLength={}",
        userId,
        req == null ? null : req.title,
        req == null || req.content == null ? null : req.content.length());
    validate(req);
    GrowthRecord r = new GrowthRecord();
    r.userId = userId;
    apply(r, req);
    markGraphWaiting(r);
    r.createdAt = LocalDateTime.now();
    r.updatedAt = LocalDateTime.now();
    recordMapper.insert(r);
    log.info("RecordService.create inserted recordId={}, userId={}, title={}", r.id, userId, r.title);
    syncTags(userId, r.id, req.tags);
    scheduleKnowledgeProcessing(r.id, userId);
    Map<String, Object> detail = detail(userId, r.id);
    log.info("RecordService.create exit recordId={}, userId={}", r.id, userId);
    return detail;
  }

  @Transactional
  public Map<String, Object> uploadMarkdown(Long userId, MultipartFile file) {
    log.info("RecordService.uploadMarkdown enter userId={}, filename={}, size={}",
        userId,
        file == null ? null : file.getOriginalFilename(),
        file == null ? null : file.getSize());
    if (file == null || file.isEmpty()) {
      log.info("RecordService.uploadMarkdown return reason=empty_file userId={}", userId);
      throw new IllegalArgumentException("Markdown 文件不能为空");
    }
    if (file.getSize() > MAX_MARKDOWN_SIZE) {
      log.info("RecordService.uploadMarkdown return reason=file_too_large userId={}, filename={}, size={}",
          userId, file.getOriginalFilename(), file.getSize());
      throw new IllegalArgumentException("Markdown 文件大小不能超过 2MB");
    }

    String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    if (!originalName.toLowerCase().endsWith(".md")) {
      log.info("RecordService.uploadMarkdown return reason=invalid_extension userId={}, filename={}", userId, originalName);
      throw new IllegalArgumentException("只支持上传 .md 文件");
    }

    String title = originalName.substring(0, originalName.length() - 3).trim();
    if (title.isBlank()) {
      log.info("RecordService.uploadMarkdown return reason=blank_title userId={}, filename={}", userId, originalName);
      throw new IllegalArgumentException("标题不能为空");
    }

    String content;
    try {
      content = new String(file.getBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("RecordService.uploadMarkdown exception reason=read_failed userId={}, filename={}", userId, originalName, e);
      throw new IllegalStateException("读取 Markdown 文件失败");
    }
    if (content.isBlank()) {
      log.info("RecordService.uploadMarkdown return reason=blank_content userId={}, filename={}", userId, originalName);
      throw new IllegalArgumentException("Markdown 文件不能为空");
    }

    RecordRequest req = new RecordRequest();
    req.title = title;
    req.content = content;
    req.recordType = "学习";
    req.tags = List.of();
    Map<String, Object> created = create(userId, req);
    log.debug("Markdown upload accepted userId={}, filename={}, recordId={}, title={}, contentLength={}",
        userId,
        originalName,
        created.get("id"),
        title,
        content.length());
    log.info("RecordService.uploadMarkdown exit userId={}, filename={}, recordId={}",
        userId, originalName, created.get("id"));
    return created;
  }

  @Transactional
  public Map<String, Object> update(Long userId, Long id, RecordRequest req) {
    validate(req);
    GrowthRecord r = requireOwned(userId, id);
    apply(r, req);
    markGraphWaiting(r);
    r.updatedAt = LocalDateTime.now();
    recordMapper.updateById(r);
    syncTags(userId, r.id, req.tags);
    scheduleKnowledgeProcessing(r.id, userId);
    return detail(userId, id);
  }

  @Transactional
  public void delete(Long userId, Long id) {
    requireOwned(userId, id);
    structuredKnowledgeService.removeRecordKnowledge(userId, id);
    chunkMapper.delete(new QueryWrapper<RecordChunk>().eq("record_id", id));
    feedbackMapper.delete(new QueryWrapper<CompanionFeedback>().eq("record_id", id));
    qaReferenceMapper.delete(new QueryWrapper<QaReference>().eq("record_id", id));
    recordTagMapper.delete(new QueryWrapper<RecordTag>().eq("record_id", id));
    recordMapper.deleteById(id);
  }

  @Transactional
  public Map<String, Object> generateSummary(Long userId, Long id) {
    GrowthRecord r = requireOwned(userId, id);
    r.summary = aiService.generateSummary(r.content);
    r.updatedAt = LocalDateTime.now();
    recordMapper.updateById(r);
    return detail(userId, id);
  }

  @Transactional
  public Map<String, Object> extractKeywords(Long userId, Long id) {
    GrowthRecord r = requireOwned(userId, id);
    r.keywords = String.join(",", aiService.extractKeywords(r.content));
    r.updatedAt = LocalDateTime.now();
    recordMapper.updateById(r);
    return detail(userId, id);
  }

  @Transactional
  public CompanionFeedback feedback(Long userId, Long id, String type) {
    GrowthRecord r = requireOwned(userId, id);
    CompanionFeedback f = new CompanionFeedback();
    f.userId = userId;
    f.recordId = id;
    f.feedbackType = type == null || type.isBlank() ? "gentle" : type;
    f.content = aiService.companionFeedback(r, f.feedbackType);
    f.createdAt = LocalDateTime.now();
    feedbackMapper.insert(f);
    return f;
  }

  public List<Map<String, Object>> recommendations(Long userId, Long id) {
    GrowthRecord current = requireOwned(userId, id);
    RecommendationProfile currentProfile = recommendationProfile(userId, current);
    return recordMapper.selectList(new QueryWrapper<GrowthRecord>().eq("user_id", userId).ne("id", id))
        .stream()
        .map(r -> {
          RecommendationProfile candidateProfile = recommendationProfile(userId, r);
          RecommendationScore recommendationScore = recommendationScore(currentProfile, candidateProfile);
          Map<String, Object> row = view(r);
          row.put("score", Math.round(recommendationScore.score() * 100.0) / 100.0);
          row.put("recommendationReason", recommendationScore.reason());
          return row;
        })
        .filter(row -> ((Double) row.get("score")) > 0)
        .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
        .limit(5)
        .toList();
  }

  private RecommendationProfile recommendationProfile(Long userId, GrowthRecord record) {
    Set<String> terms = recommendationTerms(record);
    Set<String> knowledgeNodeIds = recordKnowledgeNodeIds(userId, record.id);
    Set<String> knowledgeTerms = recordKnowledgeTerms(userId, knowledgeNodeIds);
    terms.addAll(knowledgeTerms);
    return new RecommendationProfile(
        record,
        terms,
        knowledgeNodeIds,
        domainTags(record, terms),
        chunksFor(record.id)
    );
  }

  private RecommendationScore recommendationScore(RecommendationProfile current, RecommendationProfile candidate) {
    double embeddingScore = embeddingSimilarity(current.chunks(), candidate.chunks(), current.record(), candidate.record());
    double keywordScore = overlapScore(current.terms(), candidate.terms());
    double graphScore = graphRelationScore(current, candidate);
    double textFallback = similarity.score(current.record().content, candidate.record().content);

    double score = embeddingScore * 4.0
        + keywordScore * 3.0
        + graphScore * 6.0
        + Math.min(0.3, textFallback) * 1.0;

    DomainPenalty domainPenalty = domainPenalty(current.domains(), candidate.domains(), keywordScore, graphScore);
    score *= domainPenalty.multiplier();
    if (domainPenalty.excluded()) score = 0;

    String reason = "embedding=" + round(embeddingScore)
        + ", keywordConcept=" + round(keywordScore)
        + ", graph=" + round(graphScore)
        + ", domain=" + domainPenalty.reason();
    return new RecommendationScore(score, reason);
  }

  private Set<String> recommendationTerms(GrowthRecord record) {
    Set<String> terms = new LinkedHashSet<>();
    terms.addAll(filteredTerms(split(record.keywords)));
    terms.addAll(filteredTerms(tokenize(record.title)));
    terms.addAll(filteredTerms(tokenize(record.summary)));
    return terms;
  }

  private Set<String> recordKnowledgeNodeIds(Long userId, Long recordId) {
    Set<String> ids = new LinkedHashSet<>();
    KnowledgeNode note = knowledgeNodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .eq("source_record_id", recordId));
    if (note != null) ids.add(String.valueOf(note.id));

    for (KnowledgeRelation relation : knowledgeRelationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .eq("source_record_id", recordId))) {
      KnowledgeNode source = knowledgeNodeMapper.selectById(relation.sourceNodeId);
      KnowledgeNode target = knowledgeNodeMapper.selectById(relation.targetNodeId);
      if (source != null && !StructuredKnowledgeService.NODE_NOTE.equals(source.nodeType)) ids.add(String.valueOf(source.id));
      if (target != null && !StructuredKnowledgeService.NODE_NOTE.equals(target.nodeType)) ids.add(String.valueOf(target.id));
    }
    return ids;
  }

  private Set<String> recordKnowledgeTerms(Long userId, Set<String> nodeIds) {
    Set<String> terms = new LinkedHashSet<>();
    for (String rawId : nodeIds) {
      Long id = Long.valueOf(rawId);
      KnowledgeNode node = knowledgeNodeMapper.selectById(id);
      if (node != null && userId.equals(node.userId)) {
        terms.addAll(filteredTerms(tokenize(node.name)));
      }
    }
    return terms;
  }

  private List<RecordChunk> chunksFor(Long recordId) {
    return chunkMapper.selectList(new QueryWrapper<RecordChunk>().eq("record_id", recordId).orderByAsc("chunk_index"));
  }

  private double embeddingSimilarity(List<RecordChunk> currentChunks, List<RecordChunk> candidateChunks,
                                     GrowthRecord current, GrowthRecord candidate) {
    double best = 0;
    for (RecordChunk currentChunk : currentChunks) {
      if (currentChunk.embedding == null || currentChunk.embedding.isBlank()) continue;
      for (RecordChunk candidateChunk : candidateChunks) {
        if (candidateChunk.embedding == null || candidateChunk.embedding.isBlank()) continue;
        best = Math.max(best, embeddingService.cosine(currentChunk.embedding, candidateChunk.embedding));
      }
    }
    if (best > 0) return best;
    return embeddingService.cosine(
        embeddingService.embed(current.title + " " + current.content),
        embeddingService.embed(candidate.title + " " + candidate.content)
    );
  }

  private double graphRelationScore(RecommendationProfile current, RecommendationProfile candidate) {
    Set<String> sharedNodes = intersection(current.knowledgeNodeIds(), candidate.knowledgeNodeIds());
    double sharedScore = sharedNodes.isEmpty()
        ? 0
        : Math.min(1.0, sharedNodes.size() / (double) Math.max(2, Math.min(current.knowledgeNodeIds().size(), candidate.knowledgeNodeIds().size())));

    double relationScore = hasGraphRelation(current.knowledgeNodeIds(), candidate.knowledgeNodeIds()) ? 0.4 : 0;
    return Math.min(1.0, sharedScore + relationScore);
  }

  private boolean hasGraphRelation(Set<String> currentNodeIds, Set<String> candidateNodeIds) {
    if (currentNodeIds.isEmpty() || candidateNodeIds.isEmpty()) return false;
    Set<Long> current = currentNodeIds.stream().map(Long::valueOf).collect(Collectors.toSet());
    Set<Long> candidate = candidateNodeIds.stream().map(Long::valueOf).collect(Collectors.toSet());
    for (KnowledgeRelation relation : knowledgeRelationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .in("source_node_id", current)
        .or()
        .in("target_node_id", current))) {
      if (candidate.contains(relation.sourceNodeId) || candidate.contains(relation.targetNodeId)) return true;
    }
    return false;
  }

  private double overlapScore(Set<String> currentTerms, Set<String> candidateTerms) {
    Set<String> overlap = intersection(currentTerms, candidateTerms);
    if (overlap.isEmpty()) return 0;
    int base = Math.max(2, Math.min(currentTerms.size(), candidateTerms.size()));
    return Math.min(1.0, overlap.size() / (double) base);
  }

  private DomainPenalty domainPenalty(Set<String> currentDomains, Set<String> candidateDomains,
                                      double keywordScore, double graphScore) {
    if (currentDomains.isEmpty() || candidateDomains.isEmpty()) return new DomainPenalty(1.0, false, "neutral");
    Set<String> overlap = intersection(currentDomains, candidateDomains);
    if (!overlap.isEmpty()) return new DomainPenalty(1.0, false, "same:" + overlap);
    if (graphScore >= 0.5 || keywordScore >= 0.5) return new DomainPenalty(0.65, false, "cross_domain_with_strong_evidence");
    return new DomainPenalty(0.15, true, "cross_domain_excluded");
  }

  private Set<String> domainTags(GrowthRecord record, Set<String> terms) {
    Set<String> domains = new LinkedHashSet<>();
    String text = normalize(record.title + " " + record.content + " " + record.summary + " " + String.join(" ", terms));
    if (containsAny(text, "stm32", "esp32", "adc", "gpio", "mcu", "uart", "spi", "i2c", "pwm", "dma", "dac", "tim", "单片机", "嵌入式")) {
      domains.add("embedded");
    }
    if (containsAny(text, "asp.net", "web", "html", "css", "javascript", "vue", "spring", "controller", "http", "api", "前端", "后端")) {
      domains.add("web");
    }
    if (containsAny(text, "操作系统", "磁盘", "调度", "进程", "线程", "内存", "文件系统", "死锁", "页表")) {
      domains.add("os");
    }
    return domains;
  }

  private boolean containsAny(String text, String... terms) {
    for (String term : terms) {
      if (text.contains(term)) return true;
    }
    return false;
  }

  private Set<String> filteredTerms(Set<String> rawTerms) {
    return rawTerms.stream()
        .map(this::normalize)
        .filter(term -> term.length() >= 2)
        .filter(term -> !GENERIC_RECOMMENDATION_TERMS.contains(term))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> tokenize(String text) {
    if (text == null || text.isBlank()) return Set.of();
    return Arrays.stream(text.split("[\\s,，;；、。！？：:（）()【】\\[\\]<>《》]+"))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private <T> Set<T> intersection(Set<T> left, Set<T> right) {
    Set<T> result = new LinkedHashSet<>(left);
    result.retainAll(right);
    return result;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  public List<Map<String, Object>> timeline(Long userId) {
    return recordMapper.selectList(new QueryWrapper<GrowthRecord>().eq("user_id", userId).orderByDesc("created_at"))
        .stream().map(r -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", r.id);
          m.put("time", r.createdAt);
          m.put("title", r.title);
          m.put("recordType", r.recordType);
          m.put("mood", r.mood);
          m.put("summary", r.summary);
          m.put("tags", tagsFor(r.id));
          return m;
        }).toList();
  }

  public List<CompanionFeedback> recentFeedback(Long userId) {
    return feedbackMapper.selectList(new QueryWrapper<CompanionFeedback>().eq("user_id", userId).orderByDesc("created_at").last("limit 5"));
  }

  public long tagCount(Long userId) {
    return tagMapper.selectCount(new QueryWrapper<Tag>().eq("user_id", userId));
  }

  public List<Map<String, Object>> tags(Long userId) {
    List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>().eq("user_id", userId).orderByAsc("name"));
    return tags.stream()
        .filter(tag -> recordTagMapper.selectCount(new QueryWrapper<RecordTag>().eq("tag_id", tag.id)) > 0)
        .map(tag -> Map.<String, Object>of("id", tag.id, "name", tag.name))
        .toList();
  }

  @Transactional
  public Map<String, Object> createTag(Long userId, String rawName) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException("标签名称不能为空");
    }
    Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("user_id", userId).eq("name", name));
    if (tag == null) {
      tag = new Tag();
      tag.userId = userId;
      tag.name = name;
      tagMapper.insert(tag);
    }
    return Map.of("id", tag.id, "name", tag.name);
  }

  public long recordCount(Long userId) {
    return recordMapper.selectCount(new QueryWrapper<GrowthRecord>().eq("user_id", userId));
  }

  public GrowthRecord requireOwned(Long userId, Long id) {
    GrowthRecord r = recordMapper.selectOne(new QueryWrapper<GrowthRecord>().eq("id", id).eq("user_id", userId));
    if (r == null) throw new IllegalArgumentException("记录不存在或无权访问");
    return r;
  }

  public Map<String, Object> view(GrowthRecord r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.id);
    m.put("userId", r.userId);
    m.put("title", r.title);
    m.put("content", r.content);
    m.put("recordType", r.recordType);
    m.put("mood", r.mood);
    m.put("summary", r.summary);
    m.put("keywords", split(r.keywords));
    m.put("graphStatus", r.graphStatus == null ? GraphStatus.SUCCESS.name() : r.graphStatus);
    m.put("graphStage", r.graphStage);
    m.put("graphError", r.graphError);
    m.put("graphUpdatedAt", r.graphUpdatedAt);
    m.put("tags", tagsFor(r.id));
    m.put("feedback", feedbackMapper.selectList(new QueryWrapper<CompanionFeedback>().eq("record_id", r.id).orderByDesc("created_at")));
    m.put("createdAt", r.createdAt);
    m.put("updatedAt", r.updatedAt);
    return m;
  }

  private void apply(GrowthRecord r, RecordRequest req) {
    r.title = req.title.trim();
    r.content = req.content == null ? "" : req.content;
    r.recordType = req.recordType == null ? "生活" : req.recordType;
    r.mood = req.mood;
  }

  private void markGraphWaiting(GrowthRecord record) {
    record.graphStatus = GraphStatus.WAITING.name();
    record.graphStage = GraphStage.QUEUED.name();
    record.graphError = null;
    record.graphUpdatedAt = null;
  }

  private void validate(RecordRequest req) {
    if (req == null || req.title == null || req.title.trim().isBlank()) {
      throw new IllegalArgumentException("标题不能为空");
    }
  }

  private void syncTags(Long userId, Long recordId, List<String> names) {
    recordTagMapper.delete(new QueryWrapper<RecordTag>().eq("record_id", recordId));
    if (names == null) return;
    for (String raw : names) {
      String name = raw == null ? "" : raw.trim();
      if (name.isBlank()) continue;
      Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("user_id", userId).eq("name", name));
      if (tag == null) {
        tag = new Tag();
        tag.userId = userId;
        tag.name = name;
        tagMapper.insert(tag);
      }
      RecordTag rt = new RecordTag();
      rt.recordId = recordId;
      rt.tagId = tag.id;
      recordTagMapper.insert(rt);
    }
  }

  private void scheduleKnowledgeProcessing(Long recordId, Long userId) {
    boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
    log.info("RecordService.scheduleKnowledgeProcessing enter recordId={}, userId={}, synchronizationActive={}, actualTransactionActive={}",
        recordId,
        userId,
        synchronizationActive,
        TransactionSynchronizationManager.isActualTransactionActive());
    if (synchronizationActive) {
      log.info("RecordService.scheduleKnowledgeProcessing register afterCommit recordId={}, userId={}", recordId, userId);
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          log.info("RecordService.scheduleKnowledgeProcessing afterCommit enter recordId={}, userId={}", recordId, userId);
          try {
            knowledgeProcessingService.processRecord(recordId, userId);
            log.info("RecordService.scheduleKnowledgeProcessing afterCommit exit recordId={}, userId={}", recordId, userId);
          } catch (Exception e) {
            log.error("RecordService.scheduleKnowledgeProcessing afterCommit exception recordId={}, userId={}",
                recordId, userId, e);
            throw e;
          }
        }

        @Override
        public void afterCompletion(int status) {
          log.info("RecordService.scheduleKnowledgeProcessing afterCompletion recordId={}, userId={}, status={}",
              recordId, userId, status);
        }
      });
    } else {
      log.info("RecordService.scheduleKnowledgeProcessing no synchronization, invoke directly recordId={}, userId={}", recordId, userId);
      try {
        knowledgeProcessingService.processRecord(recordId, userId);
        log.info("RecordService.scheduleKnowledgeProcessing direct invoke exit recordId={}, userId={}", recordId, userId);
      } catch (Exception e) {
        log.error("RecordService.scheduleKnowledgeProcessing direct invoke exception recordId={}, userId={}",
            recordId, userId, e);
        throw e;
      }
    }
    log.info("RecordService.scheduleKnowledgeProcessing exit recordId={}, userId={}", recordId, userId);
  }

  private List<String> tagsFor(Long recordId) {
    List<RecordTag> links = recordTagMapper.selectList(new QueryWrapper<RecordTag>().eq("record_id", recordId));
    return links.stream()
        .map(link -> tagMapper.selectById(link.tagId))
        .filter(t -> t != null)
        .map(t -> t.name)
        .toList();
  }

  private Set<String> split(String csv) {
    if (csv == null || csv.isBlank()) return Set.of();
    return Arrays.stream(csv.split("[,，]")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
  }
  private record RecommendationProfile(
      GrowthRecord record,
      Set<String> terms,
      Set<String> knowledgeNodeIds,
      Set<String> domains,
      List<RecordChunk> chunks
  ) {}

  private record RecommendationScore(double score, String reason) {}

  private record DomainPenalty(double multiplier, boolean excluded, String reason) {}
}
