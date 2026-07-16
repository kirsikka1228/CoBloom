package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.dto.AskRequest;
import com.cobloom.service.QaService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaController {
  private final QaService qaService;

  public QaController(QaService qaService) {
    this.qaService = qaService;
  }

  @PostMapping("/ask")
  public Map<String, Object> ask(@RequestBody AskRequest req) {
    return qaService.ask(UserContext.userId(), req.question);
  }

  @GetMapping("/history")
  public List<Map<String, Object>> history() {
    return qaService.history(UserContext.userId());
  }

  @GetMapping("/history/{id}")
  public Map<String, Object> detail(@PathVariable Long id) {
    return qaService.detail(UserContext.userId(), id);
  }

  @DeleteMapping("/history/{id}")
  public Map<String, Object> deleteHistory(@PathVariable Long id) {
    qaService.deleteHistory(UserContext.userId(), id);
    return Map.of("ok", true, "message", "删除成功", "id", id);
  }
}
