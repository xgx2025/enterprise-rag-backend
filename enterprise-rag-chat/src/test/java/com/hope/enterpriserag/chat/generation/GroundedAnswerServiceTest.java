package com.hope.enterpriserag.chat.generation;

import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.chat.config.ChatProperties;
import com.hope.enterpriserag.chat.model.ChatModel;
import com.hope.enterpriserag.chat.model.ChatModelPrompt;
import com.hope.enterpriserag.chat.model.ChatModelResult;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalCommand;
import com.hope.enterpriserag.knowledge.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroundedAnswerServiceTest {
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final ChatProperties properties = new ChatProperties();
    private final RetrievalAccessContext access = new RetrievalAccessContext(1L, 2L, Set.of("USER"), 1);
    private final ChatCommand command = new ChatCommand(
            "深圳住宿标准是多少？", null, List.of(10L), true, true, true, 8, 12_000);
    private GroundedAnswerService service;

    @BeforeEach
    void setUp() {
        properties.setRefusalText("证据不足，无法回答。");
        when(chatModel.modelName()).thenReturn("test-chat-model");
        service = new GroundedAnswerService(retrievalService, chatModel, new CitationValidator(), properties);
    }

    @Test
    void shouldGenerateSupportedAnswerAndEmitAllGroundingStages() {
        when(retrievalService.retrieve(any(), any())).thenReturn(retrievalWith(source("S1", 0.92)));
        when(chatModel.generate(any())).thenReturn(new ChatModelResult("深圳住宿上限为 500 元。[S1]", 30, 12, 42));
        List<String> stages = new ArrayList<>();

        GroundedAnswer answer = service.answer(access, command, List.of(),
                event -> stages.add(event.type()), new ChatCancellationToken());

        assertThat(answer.status()).isEqualTo(AnswerStatus.SUPPORTED);
        assertThat(answer.citations()).extracting(RetrievalSourceResponse::sourceId).containsExactly("S1");
        assertThat(stages).containsExactly("retrieval.started", "retrieval.completed", "rerank.completed",
                "generation.started", "citation.completed");
        ArgumentCaptor<ChatModelPrompt> prompt = ArgumentCaptor.forClass(ChatModelPrompt.class);
        verify(chatModel).generate(prompt.capture());
        assertThat(prompt.getValue().userPrompt()).contains("受控证据", "[S1]", "深圳住宿上限为 500 元");
    }

    @Test
    void shouldRefuseBeforeCallingModelWhenEvidenceIsMissing() {
        when(retrievalService.retrieve(any(), any())).thenReturn(retrievalWith());

        GroundedAnswer answer = service.answer(access, command, List.of(), null, null);

        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT);
        assertThat(answer.content()).isEqualTo("证据不足，无法回答。");
        verify(chatModel, never()).generate(any());
    }

    @Test
    void shouldReplaceModelAnswerWhenCitationIsForged() {
        when(retrievalService.retrieve(any(), any())).thenReturn(retrievalWith(source("S1", 0.92)));
        when(chatModel.generate(any())).thenReturn(new ChatModelResult("深圳住宿上限为 500 元。[S9]", 30, 12, 42));

        GroundedAnswer answer = service.answer(access, command, List.of(), null, null);

        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT);
        assertThat(answer.content()).isEqualTo("证据不足，无法回答。");
        assertThat(answer.citations()).isEmpty();
    }

    @Test
    void shouldRewriteReferentialFollowUpForRetrievalOnly() {
        ChatCommand followUp = new ChatCommand("那上海呢？", null, List.of(10L),
                true, true, true, 8, 12_000);
        when(retrievalService.retrieve(any(), any())).thenReturn(retrievalWith(source("S1", 0.92)));
        when(chatModel.generate(any())).thenReturn(new ChatModelResult("深圳住宿上限为 500 元。[S1]", 30, 12, 42));
        ArgumentCaptor<RetrievalCommand> retrievalCommand = ArgumentCaptor.forClass(RetrievalCommand.class);

        service.answer(access, followUp, List.of(new ChatTurn("USER", "深圳住宿标准是多少？")),
                null, null);

        verify(retrievalService).retrieve(any(), retrievalCommand.capture());
        assertThat(retrievalCommand.getValue().query()).contains("深圳住宿标准是多少", "那上海呢");
    }

    private RetrievalResponse retrievalWith(RetrievalSourceResponse... sources) {
        List<RetrievalSourceResponse> sourceList = List.of(sources);
        String context = sourceList.isEmpty() ? "" : "[S1] 深圳住宿上限为 500 元。";
        return new RetrievalResponse("trace-1", command.query(), List.of(), List.of(), List.of(), List.of(),
                context, "CONTEXT_READY", Map.of("total", 15L), sourceList,
                new RetrievalStatsResponse(sourceList.size(), 0, sourceList.size(), sourceList.size(), 15));
    }

    private RetrievalSourceResponse source(String sourceId, double score) {
        return new RetrievalSourceResponse(sourceId, "101", "差旅制度", "V2", LocalDate.of(2026, 1, 1),
                "住宿标准", 3, "深圳住宿上限为 500 元。", 1, score);
    }
}
