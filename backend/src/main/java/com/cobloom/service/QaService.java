package com.cobloom.service;

//用户问题->RetrievalService 检索相关候选->
// RagContextBuilder 把候选转成 RAG 上下文->AIService.answer 基于上下文生成答案
// ->qa_record 保存问答->qa_reference 保存本次回答使用过的来源
// ->detail 返回答案+references
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.dto.QAReferenceDTO;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.QaRecord;
import com.cobloom.entity.QaReference;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.QaRecordMapper;
import com.cobloom.mapper.QaReferenceMapper;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.rag.RagContextBuilder;
import com.cobloom.service.rag.RagContextItem;
import com.cobloom.service.retrieval.RetrievalResult;
import com.cobloom.service.retrieval.RetrievalService;//调 retrievalService.retrieve 得到候选
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QaService {
  // 如果没有上下文，返回“知识库中未找到足够相关内容”一类兜底回答
  private static final String NOT_ENOUGH_RELEVANT = "知识库中未找到足够相关内容";

  private final GrowthRecordMapper recordMapper;
  private final QaRecordMapper qaRecordMapper;
  private final QaReferenceMapper referenceMapper;
  private final AIService aiService;
  private final RetrievalService retrievalService;
  private final RagContextBuilder ragContextBuilder;

  public QaService(GrowthRecordMapper recordMapper, QaRecordMapper qaRecordMapper,
      QaReferenceMapper referenceMapper, AIService aiService, RetrievalService retrievalService,
      RagContextBuilder ragContextBuilder) {
    this.recordMapper = recordMapper;
    this.qaRecordMapper = qaRecordMapper;
    this.referenceMapper = referenceMapper;
    this.aiService = aiService;
    this.retrievalService = retrievalService;
    this.ragContextBuilder = ragContextBuilder;
  }

  @Transactional
  public Map<String, Object> ask(Long userId, String question) {
    RetrievalResult retrieval = retrievalService.retrieve(userId, question);
    List<RagContextItem> contextItems = ragContextBuilder.build(retrieval.candidates());
    List<String> contexts = contextItems.stream().map(RagContextItem::content).toList();
    String answer = contexts.isEmpty() ? NOT_ENOUGH_RELEVANT : aiService.answer(question, contexts);

    QaRecord qa = new QaRecord();
    qa.userId = userId;
    qa.question = question;
    qa.answer = answer;
    qa.createdAt = LocalDateTime.now();
    qaRecordMapper.insert(qa);

    for (RagContextItem item : contextItems) {
      // 对每个上下文保存一条 qa_reference。
      QaReference ref = new QaReference();
      ref.qaRecordId = qa.id;
      ref.recordId = item.recordId();
      ref.chunkId = item.chunkId();
      ref.snippet = item.snippet();
      ref.similarity = Math.round(item.score() * 1000.0) / 1000.0;
      referenceMapper.insert(ref);
    }
    return detail(userId, qa.id);
  }

  public List<Map<String, Object>> history(Long userId) {
    return qaRecordMapper.selectList(new QueryWrapper<QaRecord>().eq("user_id", userId).orderByDesc("created_at"))
        .stream().map(this::view).toList();
  }

  // 返回引用时，detail 会根据 record_id 查原始记录标题，组装
  public Map<String, Object> detail(Long userId, Long id) {
    QaRecord qa = qaRecordMapper.selectOne(new QueryWrapper<QaRecord>().eq("id", id).eq("user_id", userId));
    if (qa == null)
      throw new IllegalArgumentException("问答记录不存在或无权访问");
    Map<String, Object> m = view(qa);
    List<QAReferenceDTO> refs = referenceMapper.selectList(new QueryWrapper<QaReference>().eq("qa_record_id", id))
        .stream()
        .map(ref -> {
          GrowthRecord record = recordMapper.selectById(ref.recordId);
          return new QAReferenceDTO(
              ref.recordId,
              record == null ? "已删除记录" : record.title,
              ref.snippet,
              ref.similarity == null ? 0 : ref.similarity);
        })
        .toList();
    m.put("references", refs);
    return m;
  }

  @Transactional
  public void deleteHistory(Long userId, Long id) {
    QaRecord qa = qaRecordMapper.selectOne(new QueryWrapper<QaRecord>().eq("id", id).eq("user_id", userId));
    if (qa == null)
      throw new IllegalArgumentException("问答记录不存在或无权删除");
    referenceMapper.delete(new QueryWrapper<QaReference>().eq("qa_record_id", qa.id));
    int deleted = qaRecordMapper.delete(new QueryWrapper<QaRecord>().eq("id", qa.id).eq("user_id", userId));
    if (deleted <= 0)
      throw new IllegalStateException("删除问答记录失败");
  }

  public long qaCount(Long userId) {
    return qaRecordMapper.selectCount(new QueryWrapper<QaRecord>().eq("user_id", userId));
  }

  private Map<String, Object> view(QaRecord qa) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", qa.id);
    m.put("question", qa.question);
    m.put("answer", qa.answer);
    m.put("createdAt", qa.createdAt);
    return m;
  }
}
