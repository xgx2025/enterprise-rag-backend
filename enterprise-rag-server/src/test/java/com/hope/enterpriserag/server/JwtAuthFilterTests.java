package com.hope.enterpriserag.server;

import com.hope.enterpriserag.security.config.JwtAuthFilter;
import com.hope.enterpriserag.security.util.JwtUtil;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.system.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** JWT 请求认证过滤器的回归测试。 */
class JwtAuthFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 验证有效 JWT 会创建可信认证对象并写入安全上下文。 */
    @Test
    void validAccessTokenShouldPopulateAuthenticatedSecurityContext() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserService userService = mock(UserService.class);
        Claims claims = mock(Claims.class);
        User user = new User();
        user.setId(100L);
        user.setTenantId(10L);
        user.setStatus(1);

        when(jwtUtil.validateAccessToken("valid-token")).thenReturn(true);
        when(jwtUtil.parseAccessToken("valid-token")).thenReturn(claims);
        when(jwtUtil.getUserIdFromToken(claims)).thenReturn(100L);
        when(userService.getById(100L)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/knowledge-bases");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthFilter(jwtUtil, userService)
                .doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertSame(user, authentication.getPrincipal());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }
}
