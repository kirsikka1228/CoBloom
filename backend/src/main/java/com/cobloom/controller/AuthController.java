package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public Map<String, Object> register(@RequestBody Map<String, String> req) {
    return authService.register(req.get("username"), req.get("password"), req.get("nickname"));
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody Map<String, String> req) {
    return authService.login(req.get("username"), req.get("password"));
  }

  @PostMapping("/logout")
  public Map<String, Boolean> logout() {
    // 当前课程 MVP 使用无状态 JWT，退出主要由前端删除本地 token 完成；
    // 后端接口用于满足接口完整性，并统一前后端退出语义。
    return Map.of("ok", true);
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return authService.me(UserContext.userId());
  }
}
