package com.hope.enterpriserag.chat.generation;

/**
 * 用于理解追问的历史消息摘要；历史回答不得被当作事实证据。
 */
public record ChatTurn(String role, String content) {
}
