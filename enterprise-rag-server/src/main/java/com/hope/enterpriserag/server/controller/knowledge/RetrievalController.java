package com.hope.enterpriserag.server.controller.knowledge;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.service.RetrievalService;
import com.hope.enterpriserag.server.dto.knowledge.RetrievalRequest;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 企业检索 REST 适配器。
 * 同时提供正式检索地址和前端检索调试页兼容地址；两个入口执行完全相同的权限规则。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
public class RetrievalController {
    /**
     * 当前用户表尚未持久化安全许可等级，因此普通认证用户只授予公开级别 1。
     * 后续扩展身份模型时应从服务端用户资料读取，不能改为请求参数。
     */
    private static final int DEFAULT_USER_SECURITY_LEVEL = 1;

    private final RetrievalService retrievalService;

    /** 执行检索、过滤、父块回溯、重排和上下文组装。 */
    @PostMapping({"/retrieval/search", "/debug/retrieve"})
    public Result<RetrievalResponse> retrieve(@AuthenticationPrincipal User user,
                                              Authentication authentication,
                                              @Valid @RequestBody RetrievalRequest request) {
        RetrievalAccessContext access = new RetrievalAccessContext(user.getTenantId(), user.getId(),
                roles(authentication), DEFAULT_USER_SECURITY_LEVEL);
        return Result.ok(retrievalService.retrieve(access, request.toCommand()));
    }

    private Set<String> roles(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication == null) {
            return Set.of();
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority().trim().toUpperCase(Locale.ROOT);
            roles.add(role);
            if (role.startsWith("ROLE_") && role.length() > 5) {
                roles.add(role.substring(5));
            }
        }
        return Set.copyOf(roles);
    }
}
