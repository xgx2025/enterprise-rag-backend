package com.hope.enterpriserag.server.support;

import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.system.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 从服务端认证主体创建 Chat/检索访问边界，客户端不得覆盖安全等级和角色。
 */
@Component
public class ChatAccessContextFactory {
    /** 根据当前用户和 Spring Security 权限创建不可提升的访问上下文。 */
    public RetrievalAccessContext create(User user, Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String role = authority.getAuthority().trim().toUpperCase(Locale.ROOT);
                roles.add(role);
                if (role.startsWith("ROLE_") && role.length() > 5) {
                    roles.add(role.substring(5));
                }
            }
        }
        int maximumSecurityLevel = user.getMaximumSecurityLevel() == null
                ? 1 : Math.max(1, Math.min(3, user.getMaximumSecurityLevel()));
        return new RetrievalAccessContext(user.getTenantId(), user.getId(), Set.copyOf(roles),
                maximumSecurityLevel);
    }

    /** 判断当前访问上下文是否具备知识库治理权限。 */
    public boolean isKnowledgeAdministrator(RetrievalAccessContext access) {
        return access != null && (access.roles().contains("ROLE_KB_ADMIN")
                || access.roles().contains("KB_ADMIN"));
    }
}
