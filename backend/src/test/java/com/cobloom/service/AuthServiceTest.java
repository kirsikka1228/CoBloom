package com.cobloom.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cobloom.config.JwtUtil;
import com.cobloom.entity.User;
import com.cobloom.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务单元测试")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @Spy
    @InjectMocks 
    private AuthService authService;

    @Test
    @DisplayName("✅ 登录成功测试")
    void shouldReturnTokenWhenCredentialsAreValid() throws Exception {
        // Arrange
        String username = "test";
        String password = "123";
        String expectedToken = "mock.token";
  
        User user = new User();
        user.id = 1L;
        user.username = username;
        user.password = "encoded_password";

        // Mock 数据库查询
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        // Mock 密码匹配
        when(passwordEncoder.matches(eq(password), anyString())).thenReturn(true);
        // Mock Token 生成
        when(jwtUtil.generate(user.id)).thenReturn(expectedToken);

        // 💡 关键修复：向 me() 方法传入 user.id，并且为 me 方法提供一个模拟的返回 Map
        Map<String, Object> mockMeResult = Map.of("id", user.id, "username", username);
        doReturn(mockMeResult).when(authService).me(user.id);

        // Act
        Map<String, Object> result = authService.login(username, password);

        // Assert
        assertNotNull(result);
        assertEquals(expectedToken, result.get("token"));
        assertEquals(mockMeResult, result.get("user"));
    }
}