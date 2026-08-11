package com.hope.enterpriserag.server.controller.knowledge;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.service.RetrievalService;
import com.hope.enterpriserag.server.dto.knowledge.RetrievalRequest;
import com.hope.enterpriserag.server.support.ChatAccessContextFactory;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


/**
 * 企业检索 REST 适配器。
 * 同时提供正式检索地址和前端检索调试页兼容地址；两个入口执行完全相同的权限规则。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
public class RetrievalController {
    private final RetrievalService retrievalService;
    private final ChatAccessContextFactory accessContextFactory;

    /** 执行检索、过滤、父块回溯、重排和上下文组装。 */
    @PostMapping({"/retrieval/search", "/debug/retrieve"})
    public Result<RetrievalResponse> retrieve(@AuthenticationPrincipal User user,
                                              Authentication authentication,
                                              @Valid @RequestBody RetrievalRequest request) {
        RetrievalAccessContext access = accessContextFactory.create(user, authentication);
        return Result.ok(retrievalService.retrieve(access, request.toCommand()));
    }
}
