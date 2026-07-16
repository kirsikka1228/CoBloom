package com.cobloom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.config.JwtUtil;
import com.cobloom.entity.User;
import com.cobloom.mapper.UserMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceBoundaryTest {

  @Mock UserMapper userMapper;
  @Mock PasswordEncoder passwordEncoder;
  @Mock JwtUtil jwtUtil;
  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(userMapper, passwordEncoder, jwtUtil);
  }

  @Test
  void registerRejectsBlankUsernameAndWeakPasswordBeforeDatabaseAccess() {
    assertThrows(IllegalArgumentException.class, () -> service.register(" ", "123456", null));
    assertThrows(IllegalArgumentException.class, () -> service.register("user", "12345", null));
    assertThrows(IllegalArgumentException.class, () -> service.register("user", null, null));
    verify(userMapper, never()).insert(any(User.class));
  }

  @Test
  void registerRejectsDuplicateUsername() {
    when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
    assertThrows(IllegalArgumentException.class, () -> service.register("existing", "123456", null));
  }

  @Test
  void registerEncodesPasswordUsesNicknameFallbackAndReturnsToken() {
    when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    when(passwordEncoder.encode("123456")).thenReturn("encoded");
    when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
      ((User) invocation.getArgument(0)).id = 7L;
      return 1;
    });
    when(userMapper.selectById(7L)).thenAnswer(invocation -> {
      User user = new User();
      user.id = 7L;
      user.username = "new-user";
      user.nickname = "new-user";
      return user;
    });
    when(jwtUtil.generate(7L)).thenReturn("token");

    Map<String, Object> payload = service.register("new-user", "123456", " ");

    assertEquals("token", payload.get("token"));
    assertEquals("new-user", ((Map<?, ?>) payload.get("user")).get("nickname"));
    verify(passwordEncoder).encode("123456");
  }

  @Test
  void loginRejectsNullMissingPasswordAndWrongPassword() {
    assertThrows(IllegalArgumentException.class, () -> service.login(null, "password"));
    assertThrows(IllegalArgumentException.class, () -> service.login("user", null));

    when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> service.login("missing", "password"));

    User noPassword = new User();
    noPassword.password = null;
    when(userMapper.selectOne(any(Wrapper.class))).thenReturn(noPassword);
    assertThrows(IllegalArgumentException.class, () -> service.login("user", "password"));

    User user = new User();
    user.password = "encoded";
    when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
    when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> service.login("user", "wrong"));
  }
}
