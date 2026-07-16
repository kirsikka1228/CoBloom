package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.service.QaService;
import com.cobloom.service.RecordService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final RecordService recordService;
  private final QaService qaService;

  public DashboardController(RecordService recordService, QaService qaService) {
    this.recordService = recordService;
    this.qaService = qaService;
  }

  @GetMapping
  public Map<String, Object> dashboard() {
    Long userId = UserContext.userId();
    return Map.of(
        "recordCount", recordService.recordCount(userId),
        "tagCount", recordService.tagCount(userId),
        "qaCount", qaService.qaCount(userId),
        "recentRecords", recordService.recent(userId, 5),
        "recentFeedback", recordService.recentFeedback(userId));
  }

  @GetMapping("/summary")
  public Map<String, Object> summary() {
    Long userId = UserContext.userId();
    return Map.of(
        "recordCount", recordService.recordCount(userId),
        "noteCount", recordService.recordCount(userId),
        "tagCount", recordService.tagCount(userId),
        "qaCount", qaService.qaCount(userId));
  }

  @GetMapping("/recent-notes")
  public java.util.List<Map<String, Object>> recentNotes() {
    return recordService.recent(UserContext.userId(), 5);
  }
}
