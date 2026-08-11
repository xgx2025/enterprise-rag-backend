package com.hope.enterpriserag.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可信问答业务配置，控制证据门槛、历史窗口和 SSE 增量大小。
 */
@Data
@ConfigurationProperties(prefix = "rag.chat")
public class ChatProperties {
    /** 用户问题最大字符数。 */
    private int maxQuestionCharacters = 2_000;
    /** 至少一条来源需达到的已校准重排分数，范围为 0 至 1。 */
    private double minimumEvidenceScore = 0.35;
    /** 进入 Prompt 的最近历史消息数，历史仅用于理解追问，不作为事实证据。 */
    private int maxHistoryMessages = 6;
    /** SSE 单个 answer.delta 的最大字符数。 */
    private int deltaChunkSize = 24;
    /** SSE 进度流超时时间，单位毫秒。 */
    private long streamTimeoutMillis = 300_000L;
    /** SSE 心跳间隔，单位毫秒，用于避免代理在模型生成期间关闭空闲连接。 */
    private long heartbeatIntervalMillis = 15_000L;
    /** 无证据或引用校验失败时返回的统一拒答文本。 */
    private String refusalText = "当前知识库中没有足够的可靠依据回答该问题，请补充信息或联系相关负责人确认。";
}
