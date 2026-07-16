package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.service.RecordService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {
  private final RecordService recordService;

  public TimelineController(RecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping
  public List<Map<String, Object>> timeline() {
    return recordService.timeline(UserContext.userId());
  }
}
