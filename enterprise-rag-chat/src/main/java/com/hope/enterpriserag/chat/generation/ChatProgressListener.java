package com.hope.enterpriserag.chat.generation;

/** 接收检索、重排、生成与引用校验阶段事件。 */
@FunctionalInterface
public interface ChatProgressListener {
    ChatProgressListener NOOP = event -> { };

    /** 在阶段完成时接收事件。 */
    void onEvent(ChatProgressEvent event);
}
