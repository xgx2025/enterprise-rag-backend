package com.hope.enterpriserag.chat.service;

import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.chat.config.ChatProperties;
import com.hope.enterpriserag.chat.dto.ChatMessageResponse;
import com.hope.enterpriserag.chat.dto.ConversationResponse;
import com.hope.enterpriserag.chat.generation.ChatCancellationToken;
import com.hope.enterpriserag.chat.generation.ChatCancelledException;
import com.hope.enterpriserag.chat.generation.ChatProgressEvent;
import com.hope.enterpriserag.chat.generation.ChatProgressListener;
import com.hope.enterpriserag.chat.generation.GroundedAnswer;
import com.hope.enterpriserag.chat.generation.GroundedAnswerService;
import com.hope.enterpriserag.chat.model.ChatModelResult;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat 应用服务，统一编排消息占位、可信回答、SSE 增量与最终持久化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
public class ChatApplicationService {
    private final ChatPersistenceService persistenceService;
    private final GroundedAnswerService groundedAnswerService;
    private final ChatProperties properties;

    /** 创建当前用户的新会话。 */
    public ConversationResponse createConversation(RetrievalAccessContext access) {
        ConversationResponse response = persistenceService.createConversation(access);
        log.info("问答会话创建成功: tenantId={}, userId={}, conversationId={}",
                access.tenantId(), access.userId(), response.id());
        return response;
    }

    /** 查询当前用户会话摘要。 */
    public List<ConversationResponse> listConversations(RetrievalAccessContext access) {
        return persistenceService.listConversations(access);
    }

    /** 查询当前用户会话详情。 */
    public ConversationResponse getConversation(RetrievalAccessContext access, Long conversationId) {
        return persistenceService.getConversation(access, conversationId);
    }

    /** 软删除当前用户会话。 */
    public void deleteConversation(RetrievalAccessContext access, Long conversationId) {
        persistenceService.deleteConversation(access, conversationId);
        log.info("问答会话删除成功: tenantId={}, userId={}, conversationId={}",
                access.tenantId(), access.userId(), conversationId);
    }

    /** 执行非流式完整回答。 */
    public ChatMessageResponse send(RetrievalAccessContext access, ChatCommand command) {
        validateNewTurn(command);
        return execute(access, persistenceService.beginTurn(access, command),
                ChatProgressListener.NOOP, new ChatCancellationToken(), false);
    }

    /** 执行流式回答，answer.delta 由后端在引用校验通过后发送。 */
    public ChatMessageResponse stream(RetrievalAccessContext access, ChatCommand command,
                                      ChatProgressListener listener, ChatCancellationToken token) {
        validateNewTurn(command);
        return execute(access, persistenceService.beginTurn(access, command), listener, token, true);
    }

    /** 重新生成任意已结束的助手回答。 */
    public ChatMessageResponse regenerate(RetrievalAccessContext access, Long assistantMessageId,
                                          ChatProgressListener listener, ChatCancellationToken token,
                                          boolean stream) {
        return execute(access, persistenceService.beginRegeneration(access, assistantMessageId, false),
                listener, token, stream);
    }

    /** 仅重试失败或已取消的助手回答。 */
    public ChatMessageResponse retry(RetrievalAccessContext access, Long assistantMessageId,
                                     ChatProgressListener listener, ChatCancellationToken token,
                                     boolean stream) {
        return execute(access, persistenceService.beginRegeneration(access, assistantMessageId, true),
                listener, token, stream);
    }

    private ChatMessageResponse execute(RetrievalAccessContext access, ChatTurnState state,
                                        ChatProgressListener listener, ChatCancellationToken token,
                                        boolean stream) {
        ChatProgressListener effectiveListener = listener == null ? ChatProgressListener.NOOP : listener;
        ChatCancellationToken effectiveToken = token == null ? new ChatCancellationToken() : token;
        boolean persisted = false;
        try {
            emit(effectiveListener, "message.start", Map.of(
                    "messageId", String.valueOf(state.assistantMessageId()),
                    "conversationId", String.valueOf(state.conversationId())));
            GroundedAnswer answer = groundedAnswerService.answer(access, state.command(),
                    persistenceService.loadHistory(access, state), effectiveListener, effectiveToken);
            effectiveToken.throwIfCancelled();
            if (stream) {
                emitAnswerDeltas(answer.content(), effectiveListener, effectiveToken);
                emitAnswerMetadata(answer, effectiveListener);
            }
            ChatMessageResponse response = persistenceService.completeTurn(access, state, answer);
            persisted = true;
            safeEmit(effectiveListener, "message.done", Map.of(
                    "messageId", response.id(),
                    "conversationId", String.valueOf(state.conversationId()),
                    "traceId", answer.retrieval().traceId()));
            log.info("可信问答完成: tenantId={}, userId={}, conversationId={}, messageId={}, traceId={}, answerStatus={}",
                    access.tenantId(), access.userId(), state.conversationId(), state.assistantMessageId(),
                    answer.retrieval().traceId(), answer.status());
            return response;
        } catch (ChatCancelledException e) {
            if (!persisted) {
                persistenceService.cancelTurn(access, state);
            }
            safeEmit(effectiveListener, "message.cancelled", Map.of(
                    "messageId", String.valueOf(state.assistantMessageId()),
                    "conversationId", String.valueOf(state.conversationId())));
            log.info("可信问答已取消: tenantId={}, userId={}, conversationId={}, messageId={}",
                    access.tenantId(), access.userId(), state.conversationId(), state.assistantMessageId());
            throw e;
        } catch (RuntimeException e) {
            if (!persisted) {
                persistenceService.failTurn(access, state, "CHAT_GENERATION_FAILED", "回答生成失败，请稍后重试");
            }
            safeEmit(effectiveListener, "message.error", Map.of(
                    "code", "CHAT_GENERATION_FAILED",
                    "message", "回答生成失败，请稍后重试",
                    "messageId", String.valueOf(state.assistantMessageId())));
            log.error("可信问答失败: tenantId={}, userId={}, conversationId={}, messageId={}",
                    access.tenantId(), access.userId(), state.conversationId(), state.assistantMessageId(), e);
            throw e;
        }
    }

    private void emitAnswerDeltas(String content, ChatProgressListener listener,
                                  ChatCancellationToken token) {
        int maximumCodePoints = Math.max(1, properties.getDeltaChunkSize());
        int offset = 0;
        while (offset < content.length()) {
            token.throwIfCancelled();
            int remainingCodePoints = content.codePointCount(offset, content.length());
            int count = Math.min(maximumCodePoints, remainingCodePoints);
            int end = content.offsetByCodePoints(offset, count);
            emit(listener, "answer.delta", Map.of("content", content.substring(offset, end)));
            offset = end;
        }
    }

    private void emitAnswerMetadata(GroundedAnswer answer, ChatProgressListener listener) {
        for (RetrievalSourceResponse result : answer.retrieval().sources()) {
            emit(listener, "retrieval.result", sourceData(result));
        }
        for (RetrievalSourceResponse citation : answer.citations()) {
            emit(listener, "citation.add", sourceData(citation));
        }
        RetrievalStatsResponse stats = answer.retrieval().retrievalStats();
        emit(listener, "retrieval.summary", Map.of(
                "totalRetrieved", stats.totalRetrieved(),
                "permissionFiltered", stats.permissionFiltered(),
                "fusionCandidates", stats.fusionCandidates(),
                "rerankKept", stats.rerankKept(),
                "totalTimeMs", stats.totalTimeMs()));
        emit(listener, "answer.status", Map.of("status", answer.status().name()));
        ChatModelResult usage = answer.modelResult();
        emit(listener, "usage", Map.of(
                "promptTokens", usage == null ? 0 : usage.promptTokens(),
                "completionTokens", usage == null ? 0 : usage.completionTokens(),
                "totalTokens", usage == null ? 0 : usage.totalTokens()));
    }

    private Map<String, Object> sourceData(RetrievalSourceResponse source) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceId", source.sourceId());
        data.put("documentId", source.documentId());
        data.put("title", value(source.title()));
        data.put("version", value(source.version()));
        data.put("effectiveDate", source.effectiveDate() == null ? "" : source.effectiveDate().toString());
        data.put("sectionPath", value(source.sectionPath()));
        data.put("pageNumber", source.pageNumber() == null ? 0 : source.pageNumber());
        data.put("quote", value(source.quote()));
        data.put("securityLevel", source.securityLevel() == null ? 1 : source.securityLevel());
        data.put("score", source.score());
        return data;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void emit(ChatProgressListener listener, String type, Map<String, Object> data) {
        listener.onEvent(new ChatProgressEvent(type, data));
    }

    private void safeEmit(ChatProgressListener listener, String type, Map<String, Object> data) {
        try {
            emit(listener, type, data);
        } catch (RuntimeException ignored) {
            // 客户端连接可能已关闭，状态已由持久化分支记录。
        }
    }

    private void validateNewTurn(ChatCommand command) {
        if (command == null || command.query() == null || command.query().isBlank()) {
            throw new BusinessException("问题不能为空");
        }
        if (command.query().trim().length() > properties.getMaxQuestionCharacters()) {
            throw new BusinessException("问题长度超过限制");
        }
        if (!command.denseEnabled() && !command.sparseEnabled()) {
            throw new BusinessException("Dense 和 Sparse 检索不能同时关闭");
        }
        if (!command.rerankEnabled()) {
            throw new BusinessException("可信问答必须启用重排");
        }
    }
}
