package com.hope.enterpriserag.server;

import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.server.dto.chat.ChatRequest;
import com.hope.enterpriserag.server.dto.knowledge.RetrievalStrategyRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRequestTests {
    @Test
    void shouldConvertWebIdsAndStrategyToBusinessCommand() {
        ChatRequest request = new ChatRequest("  深圳住宿标准？  ", "100", List.of("20", "20", "21"),
                new RetrievalStrategyRequest(true, false, true), 6, 8_000);

        ChatCommand command = request.toCommand();

        assertThat(command.query()).isEqualTo("深圳住宿标准？");
        assertThat(command.conversationId()).isEqualTo(100L);
        assertThat(command.knowledgeBaseIds()).containsExactly(20L, 21L);
        assertThat(command.denseEnabled()).isTrue();
        assertThat(command.sparseEnabled()).isFalse();
        assertThat(command.rerankEnabled()).isTrue();
    }

    @Test
    void shouldRejectInvalidClientSuppliedId() {
        ChatRequest request = new ChatRequest("问题", "not-an-id", List.of(), null, null, null);

        assertThatThrownBy(request::toCommand).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectTurningOffAllRetrievalChannels() {
        ChatRequest request = new ChatRequest("问题", null, List.of(),
                new RetrievalStrategyRequest(false, false, true), null, null);

        assertThatThrownBy(request::toCommand).isInstanceOf(BusinessException.class);
    }
}
