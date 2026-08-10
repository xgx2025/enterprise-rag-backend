package com.hope.enterpriserag.chat.service;

import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.chat.entity.ChatConversation;
import com.hope.enterpriserag.chat.entity.ChatMessage;
import com.hope.enterpriserag.chat.entity.ChatTrace;
import com.hope.enterpriserag.chat.generation.AnswerStatus;
import com.hope.enterpriserag.chat.generation.GroundedAnswer;
import com.hope.enterpriserag.chat.mapper.ChatCitationMapper;
import com.hope.enterpriserag.chat.mapper.ChatConversationMapper;
import com.hope.enterpriserag.chat.mapper.ChatMessageMapper;
import com.hope.enterpriserag.chat.mapper.ChatTraceMapper;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPersistenceServiceTest {
    private final ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
    private final ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
    private final ChatCitationMapper citationMapper = mock(ChatCitationMapper.class);
    private final ChatTraceMapper traceMapper = mock(ChatTraceMapper.class);
    private final RetrievalAccessContext access = new RetrievalAccessContext(10L, 20L, Set.of("USER"), 1);
    private ChatPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ChatPersistenceService(conversationMapper, messageMapper, citationMapper, traceMapper);
    }

    @Test
    void shouldKeepPreviousAnswerUntilReplacementCompletes() {
        ChatConversation conversation = conversation();
        ChatMessage previous = assistant(30L, "COMPLETED", null);
        previous.setParentMessageId(11L);
        ChatMessage user = userMessage();
        when(messageMapper.selectOne(any())).thenReturn(previous);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(messageMapper.selectById(11L)).thenReturn(user);

        ChatTurnState state = service.beginRegeneration(access, 30L, false);

        assertThat(previous.getStatus()).isEqualTo("COMPLETED");
        verify(messageMapper, never()).updateById(previous);
        ArgumentCaptor<ChatMessage> replacement = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(replacement.capture());
        assertThat(replacement.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(replacement.getValue().getRegeneratedFromId()).isEqualTo(30L);
        assertThat(state.userMessageId()).isEqualTo(11L);
    }

    @Test
    void shouldSupersedePreviousAnswerAfterReplacementIsPersisted() {
        ChatConversation conversation = conversation();
        ChatMessage replacement = assistant(31L, "RUNNING", 30L);
        replacement.setParentMessageId(11L);
        ChatMessage previous = assistant(30L, "COMPLETED", null);
        when(messageMapper.selectOne(any())).thenReturn(replacement);
        when(conversationMapper.selectOne(any())).thenReturn(conversation);
        when(messageMapper.selectById(30L)).thenReturn(previous);
        when(conversationMapper.selectById(100L)).thenReturn(conversation);
        ChatCommand command = new ChatCommand("问题", 100L, List.of(200L), true, true, true, 8, 12_000);
        ChatTurnState state = new ChatTurnState(100L, 11L, 31L, command);

        service.completeTurn(access, state, answer());

        assertThat(replacement.getStatus()).isEqualTo("COMPLETED");
        assertThat(previous.getStatus()).isEqualTo("SUPERSEDED");
        verify(traceMapper).insert(any(ChatTrace.class));
    }

    private ChatConversation conversation() {
        ChatConversation value = new ChatConversation();
        value.setId(100L);
        value.setTenantId(10L);
        value.setUserId(20L);
        value.setTitle("问题");
        value.setKnowledgeBaseIds("[200]");
        value.setRetrievalStrategy("{\"dense\":true,\"sparse\":true,\"rerank\":true}");
        value.setStatus("ACTIVE");
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private ChatMessage userMessage() {
        ChatMessage value = new ChatMessage();
        value.setId(11L);
        value.setTenantId(10L);
        value.setConversationId(100L);
        value.setRole("USER");
        value.setContent("问题");
        value.setStatus("COMPLETED");
        return value;
    }

    private ChatMessage assistant(Long id, String status, Long regeneratedFromId) {
        ChatMessage value = new ChatMessage();
        value.setId(id);
        value.setTenantId(10L);
        value.setConversationId(100L);
        value.setRole("ASSISTANT");
        value.setContent("");
        value.setStatus(status);
        value.setRegeneratedFromId(regeneratedFromId);
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private GroundedAnswer answer() {
        RetrievalStatsResponse stats = new RetrievalStatsResponse(1, 0, 1, 1, 12);
        RetrievalResponse retrieval = new RetrievalResponse("trace-1", "问题", List.of(), List.of(), List.of(),
                List.of(), "[S1] 证据", "CONTEXT_READY", Map.of("total", 12L), List.of(), stats);
        return new GroundedAnswer("回答。[S1]", AnswerStatus.SUPPORTED, List.of(), retrieval, null, "test-model");
    }
}
