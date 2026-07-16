package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.dto.FeedbackRequest;
import com.cobloom.dto.RecordRequest;
import com.cobloom.entity.CompanionFeedback;
import com.cobloom.service.RecordService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
// 课程需求文档使用 note/notes 表述；当前 MVP 中 record 即 note 的实现名称。
// 同时保留 /api/records 并兼容 /api/notes，避免破坏现有前端与演示数据。
@RequestMapping({"/api/records", "/api/notes"})
public class RecordController {
  private final RecordService recordService;

  public RecordController(RecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(value = "q", required = false) String q) {
    return recordService.list(UserContext.userId(), q);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return recordService.detail(UserContext.userId(), id);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody RecordRequest req) {
    return recordService.create(UserContext.userId(), req);
  }

  @PostMapping("/upload")
  public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
    return recordService.uploadMarkdown(UserContext.userId(), file);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(@PathVariable Long id, @RequestBody RecordRequest req) {
    return recordService.update(UserContext.userId(), id, req);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@PathVariable Long id) {
    recordService.delete(UserContext.userId(), id);
    return Map.of("ok", true);
  }

  @PostMapping("/{id}/ai/summary")
  public Map<String, Object> summary(@PathVariable Long id) {
    return recordService.generateSummary(UserContext.userId(), id);
  }

  @PostMapping("/{id}/summary")
  public Map<String, Object> summaryAlias(@PathVariable Long id) {
    return recordService.generateSummary(UserContext.userId(), id);
  }

  @PostMapping("/{id}/ai/keywords")
  public Map<String, Object> keywords(@PathVariable Long id) {
    return recordService.extractKeywords(UserContext.userId(), id);
  }

  @PostMapping("/{id}/ai/feedback")
  public CompanionFeedback feedback(@PathVariable Long id, @RequestBody FeedbackRequest req) {
    return recordService.feedback(UserContext.userId(), id, req.feedbackType);
  }

  @GetMapping("/{id}/recommendations")
  public List<Map<String, Object>> recommendations(@PathVariable Long id) {
    return recordService.recommendations(UserContext.userId(), id);
  }
}
