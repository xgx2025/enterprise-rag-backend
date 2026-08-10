package com.hope.enterpriserag.chat.generation;

/** 客户端断开或主动取消流式回答时抛出的业务中断异常。 */
public class ChatCancelledException extends RuntimeException {
    public ChatCancelledException(String message) {
        super(message);
    }
}
