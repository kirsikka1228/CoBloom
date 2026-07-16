package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.service.RecordService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetaController {
  private final RecordService recordService;

  public MetaController(RecordService recordService) {
    this.recordService = recordService;
  }

  @GetMapping("/api/tags")
  public List<Map<String, Object>> tags() {
    return recordService.tags(UserContext.userId());
  }

  @PostMapping("/api/tags")
  public Map<String, Object> createTag(@RequestBody Map<String, String> body) {
    return recordService.createTag(UserContext.userId(), body == null ? null : body.get("name"));
  }

  @GetMapping("/api/categories")
  public List<Map<String, Object>> categories() {
    return List.of(
        Map.of("id", "study", "name", "学习"),
        Map.of("id", "project", "name", "项目"),
        Map.of("id", "creation", "name", "创作"),
        Map.of("id", "emotion", "name", "情绪"),
        Map.of("id", "life", "name", "生活"),
        Map.of("id", "achievement", "name", "成就"));
  }

  @PostMapping("/api/categories")
  public Map<String, Object> createCategory(@RequestBody Map<String, String> body) {
    String name = body == null ? "" : String.valueOf(body.getOrDefault("name", "")).trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException("分类名称不能为空");
    }
    return Map.of("id", name, "name", name);
  }
}
