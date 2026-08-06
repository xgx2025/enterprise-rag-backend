package com.hope.enterpriserag.server;

import com.hope.enterpriserag.security.dto.RegisterRequest;
import com.hope.enterpriserag.security.dto.ResetPasswordRequest;
import com.hope.enterpriserag.system.entity.SysTenant;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.system.mapper.SysTenantMapper;
import com.hope.enterpriserag.system.mapper.UserMapper;
import com.hope.enterpriserag.security.service.EmailService;
import com.hope.enterpriserag.system.service.TenantService;
import com.hope.enterpriserag.system.service.UserService;
import com.hope.enterpriserag.security.service.impl.AuthServiceImpl;
import com.hope.enterpriserag.security.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationFlowComponentsTests {

    @Mock
    private SysTenantMapper tenantMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private EmailService emailService;

    @Test
    void registrationUsesDefaultTenantId() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        SysTenant tenant = new SysTenant();
        tenant.setId(23L);
        when(userService.existsByUsername("new-user")).thenReturn(false);
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        when(emailService.verifyCode("user@example.com", "123456")).thenReturn(true);

        AuthServiceImpl authService = new AuthServiceImpl(
                userService, jwtUtil, redisTemplate, passwordEncoder, emailService, tenantMapper
        );
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setPassword("secret123");
        request.setEmail("user@example.com");
        request.setCode("123456");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).create(userCaptor.capture());
        assertEquals(23L, userCaptor.getValue().getTenantId());
        assertTrue(passwordEncoder.matches("secret123", userCaptor.getValue().getPassword()));
    }

    @Test
    void duplicateUsernameDoesNotConsumeVerificationCode() {
        when(userService.existsByUsername("existing-user")).thenReturn(true);
        AuthServiceImpl authService = new AuthServiceImpl(
                userService, jwtUtil, redisTemplate, new BCryptPasswordEncoder(), emailService, tenantMapper
        );
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing-user");

        assertThrows(BusinessException.class, () -> authService.register(request));

        verify(emailService, never()).verifyCode(any(), any());
    }

    @Test
    void resetPasswordStoresNewBcryptHash() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setUsername("xgx");
        user.setEmail("xgx@example.com");
        when(userService.getByEmail("xgx@example.com")).thenReturn(user);
        when(emailService.verifyCode("xgx@example.com", "123456")).thenReturn(true);
        AuthServiceImpl authService = new AuthServiceImpl(
                userService, jwtUtil, redisTemplate, passwordEncoder, emailService, tenantMapper
        );
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("xgx@example.com");
        request.setCode("123456");
        request.setNewPassword("new-secret");

        authService.resetPassword(request);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).updatePassword(eq(user), passwordCaptor.capture());
        assertTrue(passwordEncoder.matches("new-secret", passwordCaptor.getValue()));
    }

    @Test
    void resetPasswordWithUnregisteredEmailDoesNotConsumeVerificationCode() {
        when(userService.getByEmail("nonexistent@example.com")).thenReturn(null);
        AuthServiceImpl authService = new AuthServiceImpl(
                userService, jwtUtil, redisTemplate, new BCryptPasswordEncoder(), emailService, tenantMapper
        );
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("nonexistent@example.com");
        request.setCode("123456");
        request.setNewPassword("new-secret");

        assertThrows(BusinessException.class, () -> authService.resetPassword(request));

        verify(emailService, never()).verifyCode(any(), any());
    }

    @Test
    void userServiceAssignsUniqueHutoolIds() {
        UserService service = new UserService(userMapper);
        User first = new User();
        User second = new User();

        service.create(first);
        service.create(second);

        assertTrue(first.getId() > 0);
        assertTrue(second.getId() > 0);
        assertTrue(!first.getId().equals(second.getId()));
    }

    @Test
    void tenantServiceAssignsUniqueHutoolIds() {
        TenantService service = new TenantService(tenantMapper);
        SysTenant first = new SysTenant();
        SysTenant second = new SysTenant();

        service.create(first);
        service.create(second);

        assertTrue(first.getId() > 0);
        assertTrue(second.getId() > 0);
        assertTrue(!first.getId().equals(second.getId()));
    }
}
