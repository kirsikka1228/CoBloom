package com.cobloom.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.config.JwtUtil;
import com.cobloom.entity.User;
import com.cobloom.mapper.UserMapper;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  public Map<String, Object> register(String username, String password, String nickname) {
    if (username == null || username.isBlank() || password == null || password.length() < 6) {
      throw new IllegalArgumentException("用户名不能为空，密码至少 6 位");
    }
    if (userMapper.selectCount(new QueryWrapper<User>().eq("username", username)) > 0) {
      throw new IllegalArgumentException("用户名已存在");
    }
    User user = new User();
    user.username = username;
    user.password = passwordEncoder.encode(password);
    user.nickname = nickname == null || nickname.isBlank() ? username : nickname;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = LocalDateTime.now();
    userMapper.insert(user);
    return tokenPayload(user);
  }

  public Map<String, Object> login(String username, String password) {
    if (username == null || password == null) throw new IllegalArgumentException("用户名或密码错误");
    User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
    boolean ok = user != null && user.password != null && passwordEncoder.matches(password, user.password);
    if (!ok) {
      throw new IllegalArgumentException("用户名或密码错误");
    }
    return tokenPayload(user);
  }

  public Map<String, Object> me(Long userId) {
    User user = userMapper.selectById(userId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", user.id);
    m.put("username", user.username);
    m.put("nickname", user.nickname);
    return m;
  }

  private Map<String, Object> tokenPayload(User user) {
    return Map.of("token", jwtUtil.generate(user.id), "user", me(user.id));
  }
}
