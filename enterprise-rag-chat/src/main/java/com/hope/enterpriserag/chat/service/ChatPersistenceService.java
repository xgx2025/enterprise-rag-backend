package com.hope.enterpriserag.chat.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.chat.dto.ChatCitationResponse;
import com.hope.enterpriserag.chat.dto.ChatMessageResponse;
import com.hope.enterpriserag.chat.dto.ChatReasoningStepResponse;
import com.hope.enterpriserag.chat.dto.ConversationResponse;
import com.hope.enterpriserag.chat.entity.ChatCitation;
import com.hope.enterpriserag.chat.entity.ChatConversation;
import com.hope.enterpriserag.chat.entity.ChatMessage;
import com.hope.enterpriserag.chat.entity.ChatRequestClaim;
import com.hope.enterpriserag.chat.entity.ChatReasoningStep;
import com.hope.enterpriserag.chat.entity.ChatTrace;
import com.hope.enterpriserag.chat.generation.ChatTurn;
import com.hope.enterpriserag.chat.generation.GroundedAnswer;
import com.hope.enterpriserag.chat.generation.ReasoningStep;
import com.hope.enterpriserag.chat.mapper.ChatCitationMapper;
import com.hope.enterpriserag.chat.mapper.ChatConversationMapper;
import com.hope.enterpriserag.chat.mapper.ChatMessageMapper;
import com.hope.enterpriserag.chat.mapper.ChatRequestClaimMapper;
import com.hope.enterpriserag.chat.mapper.ChatReasoningStepMapper;
import com.hope.enterpriserag.chat.mapper.ChatTraceMapper;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chat 持久化事务服务，所有查询同时约束 tenantId 与 userId，防止跨租户或跨用户访问。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
public class ChatPersistenceService {
    private static final Type LONG_LIST_TYPE = new TypeToken<List<Long>>() { }.getType();

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatCitationMapper citationMapper;
    private final ChatTraceMapper traceMapper;
    private final ChatRequestClaimMapper requestClaimMapper;
    private final ChatReasoningStepMapper reasoningStepMapper;
    private final Gson gson = new Gson();

    /** 创建空会话，后续首次提问会更新标题、知识库范围和检索策略。 */
    @Transactional
    public ConversationResponse createConversation(RetrievalAccessContext access) {
        ChatConversation conversation = new ChatConversation();
        LocalDateTime now = LocalDateTime.now();
        conversation.setId(IdUtil.getSnowflakeNextId());
        conversation.setTenantId(access.tenantId());
        conversation.setUserId(access.userId());
        conversation.setTitle("新对话");
        conversation.setKnowledgeBaseIds("[]");
        conversation.setRetrievalStrategy(defaultStrategyJson());
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        return response(conversation, List.of());
    }

    /** 查询当前用户的活动会话摘要，按最近更新时间倒序。 */
    public List<ConversationResponse> listConversations(RetrievalAccessContext access) {
        return conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getTenantId, access.tenantId())
                        .eq(ChatConversation::getUserId, access.userId())
                        .eq(ChatConversation::getStatus, "ACTIVE")
                        .orderByDesc(ChatConversation::getUpdatedAt))
                .stream().map(value -> response(value, List.of())).toList();
    }

    /** 查询会话及其未被替代的完整消息和引用。 */
    public ConversationResponse getConversation(RetrievalAccessContext access, Long conversationId) {
        ChatConversation conversation = requireConversation(access, conversationId);
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getTenantId, access.tenantId())
                .eq(ChatMessage::getConversationId, conversationId)
                .ne(ChatMessage::getStatus, "SUPERSEDED")
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId));
        Map<Long, List<ChatCitation>> citations = citations(messages);
        Map<Long, List<ChatReasoningStep>> reasoningSteps = reasoningSteps(messages);
        return response(conversation, messages.stream()
                .map(message -> response(message, citations.getOrDefault(message.getId(), List.of()),
                        reasoningSteps.getOrDefault(message.getId(), List.of())))
                .toList());
    }

    /** 软删除当前用户会话，不物理删除审计数据。 */
    @Transactional
    public void deleteConversation(RetrievalAccessContext access, Long conversationId) {
        ChatConversation conversation = requireConversation(access, conversationId);
        conversation.setStatus("DELETED");
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    /** 创建用户消息与 RUNNING 助手占位消息。 */
    @Transactional
    public ChatTurnState beginTurn(RetrievalAccessContext access, ChatCommand command) {
        claimRequest(access, command.requestId());
        ChatConversation conversation = command.conversationId() == null
                ? newConversation(access) : requireConversationForUpdate(access, command.conversationId());
        ensureNoRunningTurn(access, conversation.getId());
        LocalDateTime now = LocalDateTime.now();
        conversation.setKnowledgeBaseIds(gson.toJson(safeIds(command.knowledgeBaseIds())));
        conversation.setRetrievalStrategy(strategyJson(command));
        if ("新对话".equals(conversation.getTitle())) {
            conversation.setTitle(title(command.query()));
        }
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);

        ChatMessage userMessage = message(access, conversation.getId(), "USER", command.query().trim(),
                "COMPLETED", now);
        messageMapper.insert(userMessage);
        ChatMessage assistantMessage = message(access, conversation.getId(), "ASSISTANT", "", "RUNNING", now);
        assistantMessage.setParentMessageId(userMessage.getId());
        messageMapper.insert(assistantMessage);
        ChatCommand effectiveCommand = new ChatCommand(command.query(), conversation.getId(),
                safeIds(command.knowledgeBaseIds()), command.denseEnabled(), command.sparseEnabled(),
                command.rerankEnabled(), command.resultLimit(), command.contextMaxCharacters(),
                command.requestId());
        return new ChatTurnState(conversation.getId(), userMessage.getId(), assistantMessage.getId(),
                effectiveCommand);
    }

    /** 根据旧助手消息创建重新生成或失败重试占位消息。 */
    @Transactional
    public ChatTurnState beginRegeneration(RetrievalAccessContext access, Long assistantMessageId,
                                           boolean failedOnly) {
        ChatMessage previous = requireAssistantMessage(access, assistantMessageId);
        if ("RUNNING".equals(previous.getStatus())) {
            throw new BusinessException("回答仍在生成，不能重新生成");
        }
        if ("SUPERSEDED".equals(previous.getStatus())) {
            throw new BusinessException("该回答已被新回答替代");
        }
        if (failedOnly && !("FAILED".equals(previous.getStatus()) || "CANCELLED".equals(previous.getStatus()))) {
            throw new BusinessException("只有失败或已取消的回答可以重试");
        }
        ChatConversation conversation = requireConversationForUpdate(access, previous.getConversationId());
        ensureNoRunningTurn(access, conversation.getId());
        ChatMessage userMessage = messageMapper.selectById(previous.getParentMessageId());
        if (userMessage == null || !access.tenantId().equals(userMessage.getTenantId())
                || !conversation.getId().equals(userMessage.getConversationId())) {
            throw new BusinessException(404, "原始问题不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMessage replacement = message(access, conversation.getId(), "ASSISTANT", "", "RUNNING", now);
        replacement.setParentMessageId(userMessage.getId());
        replacement.setRegeneratedFromId(previous.getId());
        messageMapper.insert(replacement);
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);

        Strategy strategy = parseStrategy(conversation.getRetrievalStrategy());
        ChatCommand command = new ChatCommand(userMessage.getContent(), conversation.getId(),
                parseIds(conversation.getKnowledgeBaseIds()), strategy.dense(), strategy.sparse(),
                true, strategy.resultLimit(), strategy.contextMaxCharacters(), null);
        return new ChatTurnState(conversation.getId(), userMessage.getId(), replacement.getId(), command);
    }

    /** 加载当前问题之前的历史，历史只用于理解追问。 */
    public List<ChatTurn> loadHistory(RetrievalAccessContext access, ChatTurnState state) {
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getTenantId, access.tenantId())
                .eq(ChatMessage::getConversationId, state.conversationId())
                .eq(ChatMessage::getStatus, "COMPLETED")
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId));
        List<ChatTurn> history = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (state.userMessageId().equals(message.getId())) {
                break;
            }
            if (message.getContent() != null && !message.getContent().isBlank()) {
                history.add(new ChatTurn(message.getRole(), message.getContent()));
            }
        }
        return List.copyOf(history);
    }

    /** 原子保存回答、引用快照和 Trace。 */
    @Transactional
    public ChatMessageResponse completeTurn(RetrievalAccessContext access, ChatTurnState state,
                                            GroundedAnswer answer) {
        ChatMessage assistant = requireRunningAssistant(access, state.assistantMessageId());
        LocalDateTime now = LocalDateTime.now();
        assistant.setContent(answer.content());
        assistant.setStatus("COMPLETED");
        assistant.setAnswerStatus(answer.status().name());
        assistant.setTraceId(answer.retrieval().traceId());
        assistant.setRetrievalStats(gson.toJson(answer.retrieval().retrievalStats()));
        assistant.setUpdatedAt(now);
        messageMapper.updateById(assistant);
        supersedePrevious(access, assistant, now);

        List<ChatCitation> citations = new ArrayList<>();
        for (RetrievalSourceResponse source : answer.citations()) {
            ChatCitation citation = citation(access, assistant.getId(), source, now);
            citationMapper.insert(citation);
            citations.add(citation);
        }
        traceMapper.insert(trace(access, state, answer, now));
        touchConversation(state.conversationId(), now);
        List<ChatReasoningStep> reasoningSteps = saveReasoningSteps(access, assistant.getId(), answer, now);
        return response(assistant, citations, reasoningSteps);
    }

    /** 将运行中的助手消息标记为失败，错误信息仅保存安全摘要。 */
    @Transactional
    public void failTurn(RetrievalAccessContext access, ChatTurnState state, String code, String message) {
        finishAbnormally(access, state, "FAILED", code, message);
    }

    /** 将运行中的助手消息标记为客户端取消。 */
    @Transactional
    public void cancelTurn(RetrievalAccessContext access, ChatTurnState state) {
        finishAbnormally(access, state, "CANCELLED", "CLIENT_CANCELLED", "回答生成已取消");
    }

    private void finishAbnormally(RetrievalAccessContext access, ChatTurnState state, String status,
                                  String code, String message) {
        ChatMessage assistant = requireRunningAssistant(access, state.assistantMessageId());
        LocalDateTime now = LocalDateTime.now();
        assistant.setStatus(status);
        assistant.setErrorCode(code);
        assistant.setErrorMessage(limit(message, 500));
        assistant.setUpdatedAt(now);
        messageMapper.updateById(assistant);
        touchConversation(state.conversationId(), now);
    }

    private ChatConversation newConversation(RetrievalAccessContext access) {
        ChatConversation value = new ChatConversation();
        LocalDateTime now = LocalDateTime.now();
        value.setId(IdUtil.getSnowflakeNextId());
        value.setTenantId(access.tenantId());
        value.setUserId(access.userId());
        value.setTitle("新对话");
        value.setKnowledgeBaseIds("[]");
        value.setRetrievalStrategy(defaultStrategyJson());
        value.setStatus("ACTIVE");
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        conversationMapper.insert(value);
        return value;
    }

    private ChatConversation requireConversation(RetrievalAccessContext access, Long conversationId) {
        if (conversationId == null) {
            throw new BusinessException(404, "会话不存在");
        }
        ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getTenantId, access.tenantId())
                .eq(ChatConversation::getUserId, access.userId())
                .eq(ChatConversation::getStatus, "ACTIVE"));
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        return conversation;
    }

    private ChatConversation requireConversationForUpdate(RetrievalAccessContext access, Long conversationId) {
        if (conversationId == null) {
            throw new BusinessException(404, "会话不存在");
        }
        ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .eq(ChatConversation::getTenantId, access.tenantId())
                .eq(ChatConversation::getUserId, access.userId())
                .eq(ChatConversation::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        return conversation;
    }

    private void ensureNoRunningTurn(RetrievalAccessContext access, Long conversationId) {
        long running = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getTenantId, access.tenantId())
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getRole, "ASSISTANT")
                .eq(ChatMessage::getStatus, "RUNNING"));
        if (running > 0) {
            throw new BusinessException(409, "当前会话已有回答正在生成");
        }
    }

    private void claimRequest(RetrievalAccessContext access, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        ChatRequestClaim claim = new ChatRequestClaim();
        claim.setId(IdUtil.getSnowflakeNextId());
        claim.setTenantId(access.tenantId());
        claim.setUserId(access.userId());
        claim.setRequestId(requestId);
        claim.setCreatedAt(LocalDateTime.now());
        try {
            requestClaimMapper.insert(claim);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该请求已提交，请刷新会话查看结果");
        }
    }

    private ChatMessage requireAssistantMessage(RetrievalAccessContext access, Long messageId) {
        ChatMessage message = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .eq(ChatMessage::getTenantId, access.tenantId())
                .eq(ChatMessage::getRole, "ASSISTANT"));
        if (message == null) {
            throw new BusinessException(404, "助手消息不存在");
        }
        requireConversation(access, message.getConversationId());
        return message;
    }

    private void supersedePrevious(RetrievalAccessContext access, ChatMessage replacement, LocalDateTime now) {
        Long previousId = replacement.getRegeneratedFromId();
        if (previousId == null) {
            return;
        }
        ChatMessage previous = messageMapper.selectById(previousId);
        if (previous == null || !access.tenantId().equals(previous.getTenantId())
                || !replacement.getConversationId().equals(previous.getConversationId())
                || !"ASSISTANT".equals(previous.getRole())) {
            throw new IllegalStateException("被替代的助手消息不属于当前会话");
        }
        previous.setStatus("SUPERSEDED");
        previous.setUpdatedAt(now);
        messageMapper.updateById(previous);
    }

    private ChatMessage requireRunningAssistant(RetrievalAccessContext access, Long messageId) {
        ChatMessage message = requireAssistantMessage(access, messageId);
        if (!"RUNNING".equals(message.getStatus())) {
            throw new IllegalStateException("助手消息已结束，不能重复写入结果");
        }
        return message;
    }

    private ChatMessage message(RetrievalAccessContext access, Long conversationId, String role,
                                String content, String status, LocalDateTime now) {
        ChatMessage value = new ChatMessage();
        value.setId(IdUtil.getSnowflakeNextId());
        value.setTenantId(access.tenantId());
        value.setConversationId(conversationId);
        value.setRole(role);
        value.setContent(content);
        value.setStatus(status);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    private ChatCitation citation(RetrievalAccessContext access, Long messageId,
                                  RetrievalSourceResponse source, LocalDateTime now) {
        ChatCitation value = new ChatCitation();
        value.setId(IdUtil.getSnowflakeNextId());
        value.setTenantId(access.tenantId());
        value.setMessageId(messageId);
        value.setSourceId(source.sourceId());
        value.setDocumentId(Long.valueOf(source.documentId()));
        value.setTitle(source.title());
        value.setVersion(source.version());
        value.setEffectiveDate(source.effectiveDate());
        value.setSectionPath(source.sectionPath());
        value.setPageNumber(source.pageNumber());
        value.setQuote(source.quote());
        value.setSecurityLevel(source.securityLevel());
        value.setScore(source.score());
        value.setCreatedAt(now);
        return value;
    }

    private ChatTrace trace(RetrievalAccessContext access, ChatTurnState state,
                            GroundedAnswer answer, LocalDateTime now) {
        ChatTrace value = new ChatTrace();
        value.setId(IdUtil.getSnowflakeNextId());
        value.setTraceId(answer.retrieval().traceId());
        value.setTenantId(access.tenantId());
        value.setUserId(access.userId());
        value.setConversationId(state.conversationId());
        value.setUserMessageId(state.userMessageId());
        value.setAssistantMessageId(state.assistantMessageId());
        value.setKnowledgeBaseIds(gson.toJson(safeIds(state.command().knowledgeBaseIds())));
        value.setRetrievalStrategy(strategyJson(state.command()));
        value.setRetrievalStats(gson.toJson(answer.retrieval().retrievalStats()));
        value.setRetrievalTiming(gson.toJson(answer.retrieval().timing()));
        value.setModel(answer.modelName());
        value.setAnswerStatus(answer.status().name());
        if (answer.modelResult() != null) {
            value.setPromptTokens(answer.modelResult().promptTokens());
            value.setCompletionTokens(answer.modelResult().completionTokens());
            value.setTotalTokens(answer.modelResult().totalTokens());
        } else {
            value.setPromptTokens(0);
            value.setCompletionTokens(0);
            value.setTotalTokens(0);
        }
        value.setCreatedAt(now);
        return value;
    }

    private Map<Long, List<ChatCitation>> citations(List<ChatMessage> messages) {
        List<Long> ids = messages.stream().filter(message -> "ASSISTANT".equals(message.getRole()))
                .map(ChatMessage::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return citationMapper.selectList(new LambdaQueryWrapper<ChatCitation>()
                        .in(ChatCitation::getMessageId, ids)
                        .orderByAsc(ChatCitation::getSourceId))
                .stream().collect(Collectors.groupingBy(ChatCitation::getMessageId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, List<ChatReasoningStep>> reasoningSteps(List<ChatMessage> messages) {
        List<Long> ids = messages.stream().filter(message -> "ASSISTANT".equals(message.getRole()))
                .map(ChatMessage::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return reasoningStepMapper.selectList(new LambdaQueryWrapper<ChatReasoningStep>()
                        .in(ChatReasoningStep::getMessageId, ids)
                        .orderByAsc(ChatReasoningStep::getSequenceNumber))
                .stream().collect(Collectors.groupingBy(ChatReasoningStep::getMessageId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private List<ChatReasoningStep> saveReasoningSteps(RetrievalAccessContext access, Long messageId,
                                                       GroundedAnswer answer, LocalDateTime now) {
        List<ChatReasoningStep> saved = new ArrayList<>();
        for (int index = 0; index < answer.reasoningSteps().size(); index++) {
            ReasoningStep source = answer.reasoningSteps().get(index);
            ChatReasoningStep value = new ChatReasoningStep();
            value.setId(IdUtil.getSnowflakeNextId());
            value.setTenantId(access.tenantId());
            value.setMessageId(messageId);
            value.setSequenceNumber(index);
            value.setStepKey(source.id());
            value.setTitle(source.title());
            value.setDetail(source.detail());
            value.setStatus(source.status());
            value.setCreatedAt(now);
            reasoningStepMapper.insert(value);
            saved.add(value);
        }
        return List.copyOf(saved);
    }

    private ConversationResponse response(ChatConversation conversation, List<ChatMessageResponse> messages) {
        return new ConversationResponse(String.valueOf(conversation.getId()), conversation.getTitle(),
                parseIds(conversation.getKnowledgeBaseIds()).stream().map(String::valueOf).toList(),
                strategyName(parseStrategy(conversation.getRetrievalStrategy())), messages,
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private ChatMessageResponse response(ChatMessage message, List<ChatCitation> citations,
                                         List<ChatReasoningStep> reasoningSteps) {
        return new ChatMessageResponse(String.valueOf(message.getId()),
                message.getRole().toLowerCase(Locale.ROOT), message.getContent(),
                citations.stream().map(this::response).toList(),
                reasoningSteps.stream().map(this::response).toList(), message.getAnswerStatus(),
                parseStats(message.getRetrievalStats()), message.getCreatedAt(),
                "RUNNING".equals(message.getStatus()), message.getStatus(), message.getTraceId(),
                message.getErrorCode(), message.getErrorMessage());
    }

    private ChatReasoningStepResponse response(ChatReasoningStep step) {
        return new ChatReasoningStepResponse(step.getStepKey(), step.getTitle(), step.getDetail(), step.getStatus());
    }

    private ChatCitationResponse response(ChatCitation citation) {
        return new ChatCitationResponse(citation.getSourceId(), String.valueOf(citation.getDocumentId()),
                citation.getTitle(), citation.getVersion(), citation.getEffectiveDate(),
                citation.getSectionPath(), citation.getPageNumber(), citation.getQuote(),
                citation.getSecurityLevel(), citation.getScore() == null ? 0.0 : citation.getScore());
    }

    private RetrievalStatsResponse parseStats(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonObject value = JsonParser.parseString(json).getAsJsonObject();
        return new RetrievalStatsResponse(value.get("totalRetrieved").getAsInt(),
                value.get("permissionFiltered").getAsInt(), value.get("fusionCandidates").getAsInt(),
                value.get("rerankKept").getAsInt(), value.get("totalTimeMs").getAsLong());
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Long> ids = gson.fromJson(json, LONG_LIST_TYPE);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    private Strategy parseStrategy(String json) {
        JsonObject value = json == null || json.isBlank()
                ? JsonParser.parseString(defaultStrategyJson()).getAsJsonObject()
                : JsonParser.parseString(json).getAsJsonObject();
        return new Strategy(booleanValue(value, "dense", true), booleanValue(value, "sparse", true),
                booleanValue(value, "rerank", true), integerValue(value, "resultLimit"),
                integerValue(value, "contextMaxCharacters"));
    }

    private boolean booleanValue(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private Integer integerValue(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsInt() : null;
    }

    private String strategyJson(ChatCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("dense", command.denseEnabled());
        value.put("sparse", command.sparseEnabled());
        value.put("rerank", command.rerankEnabled());
        value.put("resultLimit", command.resultLimit());
        value.put("contextMaxCharacters", command.contextMaxCharacters());
        return gson.toJson(value);
    }

    private String defaultStrategyJson() {
        return "{\"dense\":true,\"sparse\":true,\"rerank\":true}";
    }

    private String strategyName(Strategy strategy) {
        if (strategy.dense() && strategy.sparse()) {
            return "hybrid";
        }
        return strategy.dense() ? "dense" : "sparse";
    }

    private List<Long> safeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String title(String query) {
        String value = query.trim();
        return value.length() <= 40 ? value : value.substring(0, 40) + "…";
    }

    private String limit(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }

    private void touchConversation(Long conversationId, LocalDateTime now) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setUpdatedAt(now);
            conversationMapper.updateById(conversation);
        }
    }

    private record Strategy(boolean dense, boolean sparse, boolean rerank,
                            Integer resultLimit, Integer contextMaxCharacters) {
    }
}
