package com.hope.enterpriserag.chat.generation;

/**
 * 回答可信状态：完全有据、部分有据或证据不足拒答。
 */
public enum AnswerStatus {
    SUPPORTED,
    PARTIAL,
    INSUFFICIENT
}
