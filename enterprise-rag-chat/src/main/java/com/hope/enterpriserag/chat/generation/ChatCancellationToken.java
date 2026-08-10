package com.hope.enterpriserag.chat.generation;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次流式回答的协作式取消令牌，同时识别执行线程中断。
 */
public final class ChatCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** 标记客户端已取消本次生成。 */
    public void cancel() {
        cancelled.set(true);
    }

    /** 在阶段边界检查取消状态。 */
    public void throwIfCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new ChatCancelledException("客户端已取消回答生成");
        }
    }
}
