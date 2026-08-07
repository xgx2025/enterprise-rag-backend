package com.hope.enterpriserag.security.config;

import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.system.service.UserService;
import com.hope.enterpriserag.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器，在每个请求中从 Authorization 头提取 Bearer Token，
 * 校验通过后将用户信息写入 Spring Security 上下文。
 * <p>
 * 继承 {@link OncePerRequestFilter} 确保每个请求只执行一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && jwtUtil.validateAccessToken(token)) {
            try {
                Claims claims = jwtUtil.parseAccessToken(token);
                Long userId = jwtUtil.getUserIdFromToken(claims);
                User user = userService.getById(userId);

                if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT认证成功: userId={}, requestUri={}", userId, request.getRequestURI());
                } else {
                    log.debug("JWT认证跳过-用户不可用: userId={}", userId);
                }
            } catch (Exception e) {
                log.warn("JWT认证处理异常: requestUri={}, error={}",
                        request.getRequestURI(), e.getMessage(), e);
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 请求头提取 Bearer Token */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
