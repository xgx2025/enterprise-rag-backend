package com.hope.enterpriserag.server.controller.chat;

import com.hope.enterpriserag.chat.dto.ChatMessageResponse;
import com.hope.enterpriserag.chat.dto.ConversationResponse;
import com.hope.enterpriserag.chat.generation.ChatCancellationToken;
import com.hope.enterpriserag.chat.generation.ChatProgressListener;
import com.hope.enterpriserag.chat.service.ChatApplicationService;
import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.server.dto.chat.ChatRequest;
import com.hope.enterpriserag.server.support.ChatAccessContextFactory;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可信问答 REST 适配器，提供非流式回答、会话历史、重新生成和失败重试。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
public class ChatController {
    private final ChatApplicationService chatService;
    private final ChatAccessContextFactory accessFactory;

    /** 查询当前用户的会话摘要。 */
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> conversations(@AuthenticationPrincipal User user,
                                                             Authentication authentication) {
        return Result.ok(chatService.listConversations(accessFactory.create(user, authentication)));
    }

    /** 创建空会话。 */
    @PostMapping("/conversations")
    public Result<ConversationResponse> createConversation(@AuthenticationPrincipal User user,
                                                            Authentication authentication) {
        return Result.ok(chatService.createConversation(accessFactory.create(user, authentication)));
    }

    /** 查询会话、消息和引用详情。 */
    @GetMapping("/conversations/{conversationId}")
    public Result<ConversationResponse> conversation(@AuthenticationPrincipal User user,
                                                      Authentication authentication,
                                                      @PathVariable String conversationId) {
        return Result.ok(chatService.getConversation(accessFactory.create(user, authentication),
                parseId(conversationId)));
    }

    /** 软删除当前用户会话。 */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@AuthenticationPrincipal User user,
                                           Authentication authentication,
                                           @PathVariable String conversationId) {
        chatService.deleteConversation(accessFactory.create(user, authentication), parseId(conversationId));
        return Result.ok();
    }

    /** 执行单轮非流式可信回答。 */
    @PostMapping("/send")
    public Result<ChatMessageResponse> send(@AuthenticationPrincipal User user,
                                            Authentication authentication,
                                            @Valid @RequestBody ChatRequest request) {
        RetrievalAccessContext access = accessFactory.create(user, authentication);
        return Result.ok(chatService.send(access, request.toCommand()));
    }

    /** 基于原用户问题重新生成助手回答。 */
    @PostMapping("/messages/{messageId}/regenerate")
    public Result<ChatMessageResponse> regenerate(@AuthenticationPrincipal User user,
                                                  Authentication authentication,
                                                  @PathVariable String messageId) {
        return Result.ok(chatService.regenerate(accessFactory.create(user, authentication), parseId(messageId),
                ChatProgressListener.NOOP, new ChatCancellationToken(), false));
    }

    /** 重试失败或已取消的助手回答。 */
    @PostMapping("/messages/{messageId}/retry")
    public Result<ChatMessageResponse> retry(@AuthenticationPrincipal User user,
                                             Authentication authentication,
                                             @PathVariable String messageId) {
        return Result.ok(chatService.retry(accessFactory.create(user, authentication), parseId(messageId),
                ChatProgressListener.NOOP, new ChatCancellationToken(), false));
    }

    private Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException("ID 格式无效");
        }
    }
}
