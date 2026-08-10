package com.hope.enterpriserag.server.controller.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hope.enterpriserag.chat.generation.ChatCancellationToken;
import com.hope.enterpriserag.chat.generation.ChatCancelledException;
import com.hope.enterpriserag.chat.generation.ChatProgressEvent;
import com.hope.enterpriserag.chat.generation.ChatProgressListener;
import com.hope.enterpriserag.chat.service.ChatApplicationService;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.server.dto.chat.ChatRequest;
import com.hope.enterpriserag.server.support.ChatAccessContextFactory;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可信问答 SSE 适配器，负责事件序列化、异步执行和客户端断开取消。
 */
@RestController
@RequestMapping("/chat")
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
public class ChatStreamController {
    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final ChatApplicationService chatService;
    private final ChatAccessContextFactory accessFactory;
    private final AsyncTaskExecutor taskExecutor;
    private final Gson gson = new Gson();

    /**
     * 创建流式控制器并显式绑定 Chat 专用执行器，避免与框架默认执行器产生注入歧义。
     */
    public ChatStreamController(ChatApplicationService chatService,
                                ChatAccessContextFactory accessFactory,
                                @Qualifier("chatTaskExecutor") AsyncTaskExecutor taskExecutor) {
        this.chatService = chatService;
        this.accessFactory = accessFactory;
        this.taskExecutor = taskExecutor;
    }

    /** 流式执行新问题。 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal User user,
                             Authentication authentication,
                             @Valid @RequestBody ChatRequest request) {
        RetrievalAccessContext access = accessFactory.create(user, authentication);
        return start((listener, token) -> chatService.stream(access, request.toCommand(), listener, token));
    }

    /** 流式重新生成指定助手消息。 */
    @PostMapping(value = "/messages/{messageId}/regenerate/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@AuthenticationPrincipal User user,
                                 Authentication authentication,
                                 @PathVariable String messageId) {
        RetrievalAccessContext access = accessFactory.create(user, authentication);
        Long id = parseId(messageId);
        return start((listener, token) -> chatService.regenerate(access, id, listener, token, true));
    }

    /** 流式重试失败或已取消的助手消息。 */
    @PostMapping(value = "/messages/{messageId}/retry/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retry(@AuthenticationPrincipal User user,
                            Authentication authentication,
                            @PathVariable String messageId) {
        RetrievalAccessContext access = accessFactory.create(user, authentication);
        Long id = parseId(messageId);
        return start((listener, token) -> chatService.retry(access, id, listener, token, true));
    }

    private SseEmitter start(StreamingOperation operation) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        ChatCancellationToken token = new ChatCancellationToken();
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicReference<Future<?>> futureReference = new AtomicReference<>();
        ChatProgressListener listener = event -> send(emitter, token, event);

        Future<?> future = taskExecutor.submit(() -> {
            try {
                operation.execute(listener, token);
            } catch (ChatCancelledException ignored) {
                // 业务层已持久化 CANCELLED；连接通常已经关闭。
            } catch (RuntimeException e) {
                safeSend(emitter, new ChatProgressEvent("message.error", Map.of(
                        "code", "CHAT_STREAM_FAILED",
                        "message", "回答生成失败，请稍后重试")));
            } finally {
                terminal.set(true);
                emitter.complete();
            }
        });
        futureReference.set(future);

        Runnable cancel = () -> {
            if (!terminal.get()) {
                token.cancel();
                Future<?> running = futureReference.get();
                if (running != null) {
                    running.cancel(true);
                }
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());
        return emitter;
    }

    private void send(SseEmitter emitter, ChatCancellationToken token, ChatProgressEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(json(event)));
        } catch (IOException | IllegalStateException e) {
            token.cancel();
            throw new ChatCancelledException("SSE 客户端连接已关闭");
        }
    }

    private void safeSend(SseEmitter emitter, ChatProgressEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(json(event)));
        } catch (IOException | IllegalStateException ignored) {
            // 连接已关闭时无需继续写事件。
        }
    }

    private String json(ChatProgressEvent event) {
        JsonObject root = new JsonObject();
        root.addProperty("type", event.type());
        root.add("data", gson.toJsonTree(event.data()));
        return gson.toJson(root);
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

    @FunctionalInterface
    private interface StreamingOperation {
        void execute(ChatProgressListener listener, ChatCancellationToken token);
    }
}
