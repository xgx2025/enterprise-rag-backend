package com.hope.enterpriserag.chat.generation;

import java.util.Map;

/**
 * 问答内部阶段事件，由 Web 适配层转换为 SSE，不携带密钥、Prompt 或未授权正文。
 */
public record ChatProgressEvent(String type, Map<String, Object> data) {
    public ChatProgressEvent {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
